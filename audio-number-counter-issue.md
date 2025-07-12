# Audio Number Counter Issue: Current Time Not Updating During Playback

## Problem Statement
The current time number display (under the scrubber on the left side) does not update/progress when audio is playing. It only updates when the user manually moves the scrubber.

## Failure Points

### Architecture Overview
The app uses a multi-layer architecture for audio playback:

1. **UI Layer**:
   - `PlayerDetailFragment.kt` - Uses `OSSMediaManager` (older implementation)
   - `PlayerDetailFragmentMedia3.kt` - Uses `OSSPlayerController` (newer Media3 implementation)

2. **Controller Layer**:
   - `OSSMediaManager` - Singleton that manages MediaPlayer
   - `OSSPlayerController` - Singleton that manages Media3 MediaController

3. **Service Layer**:
   - `OSSMediaService` - Older MediaPlayer service
   - `OSSPlayerService` - Media3 Player service

### Data Flow for Current Time Updates

#### For PlayerDetailFragmentMedia3.kt:
1. `OSSPlayerService` updates `_currentPosition.value` in a coroutine via `startPositionTracking()`
2. `OSSPlayerController` exposes this as `currentPosition` LiveData
3. Fragment observes `currentPosition` via:
   ```kotlin
   playerController.currentPosition.observe(viewLifecycleOwner) { position ->
       updateProgress(position.toLong(), duration, isPlaying)
   }
   ```
4. `updateProgress()` should update UI via:
   ```kotlin
   currentTime.text = formatTime(position)
   ```
5. Additionally, there's a separate progress update job in the fragment:
   ```kotlin
   progressJob = lifecycleScope.launch {
       while (isActive) {
           if (isPlaying) {
               val newProgress = binding.progressBar.progress + 1
               binding.progressBar.progress = newProgress
               binding.currentTime.text = formatTime(newProgress.toLong())
           }
           delay(200)
       }
   }
   ```

#### For PlayerDetailFragment.kt:
1. `OSSMediaManager` updates `_currentPosition.value` in `updateProgress()`
2. Fragment collects this via:
   ```kotlin
   lifecycleScope.launch {
       mediaManager.currentPosition.collect { position ->
           updateProgress(position, mediaManager.duration.value, mediaManager.isPlaying.value)
       }
   }
   ```
3. `updateProgress()` updates the UI via:
   ```kotlin
   currentTime.text = formatTime(position)
   ```

## Potential Failure Points

1. **Position Tracking Not Running**:
   - The coroutine in `OSSPlayerService.startPositionTracking()` or `OSSMediaManager.updateProgress()` is not running or is stopped prematurely

2. **Flow/LiveData Disconnection**:
   - The `currentPosition` Flow/LiveData is not being updated or is disconnected from the position tracking

3. **Observer Not Active**:
   - The fragment's observer on `currentPosition` is inactive, not registering properly, or being canceled

4. **Multiple Controller Instances**:
   - Despite singleton pattern, multiple instances of controllers might exist, with UI observing a different instance than what controls playback

5. **Fragment Progress Job Issue**:
   - The separate progress update job in `PlayerDetailFragmentMedia3` may not be running or updating the TextView correctly

6. **Thread/Lifecycle Issues**:
   - Position updates may be happening off the main thread without proper synchronization
   - Fragment lifecycle may be detaching observers during critical update points

## Verification Points

1. **Player Service Tracking**:
   - Is `startPositionTracking()` coroutine actually running?
   - Is `_currentPosition.value` being updated every 500ms?

2. **Controller Connection**:
   - Is the Controller's `currentPosition` LiveData/Flow updated from the service?
   - Is the same controller instance being used for updates and observations?

3. **Fragment Observer**:
   - Is the observer active and properly set up?
   - Is `updateProgress()` being called when position changes?

4. **UI Update Thread**:
   - Are TextView updates happening on the main thread?
   - Is there any timing or race condition issue?

5. **Progress Job**:
   - Is the manual progress update job running alongside the observer?
   - Could there be conflicts between multiple UI update mechanisms?

## Potential Solutions

### 1. Correct Service Position Tracking
Ensure the position tracking coroutine in `OSSPlayerService` is properly started and not stopped prematurely:

```kotlin
private fun startPositionTracking() {
    positionTrackingJob?.cancel()
    positionTrackingJob = coroutineScope.launch {
        while (isActive) {
            if (_isPlaying.value == true) {
                _currentPosition.value = player.currentPosition
                Log.d("OSSPlayerService", "Position updated: ${player.currentPosition}")
            }
            delay(500)
        }
    }
}
```

### 2. Direct UI Update Override
Ensure the fragment's progress update job directly updates the TextView:

```kotlin
progressJob = lifecycleScope.launch {
    while (isActive) {
        if (isPlaying) {
            val position = playerController.player?.currentPosition ?: 0
            binding.progressBar.progress = position.toInt()
            binding.currentTime.text = formatTime(position)
        }
        delay(200)
    }
}
```

### 3. Fix LiveData/Flow Collection
Ensure proper LiveData observation or Flow collection in the fragment:

```kotlin
// For LiveData
playerController.currentPosition.observe(viewLifecycleOwner) { position ->
    Log.d("PlayerFragment", "Position observed: $position")
    updateProgress(position.toLong(), duration, isPlaying)
}

// For Flow
lifecycleScope.launch {
    playerController.currentPosition.collect { position ->
        Log.d("PlayerFragment", "Position collected: $position")
        updateProgress(position, playerController.duration.value, playerController.isPlaying.value)
    }
}
```

### 4. Singleton Pattern Fix
Ensure the singleton is properly implemented and always returns the same instance:

```kotlin
companion object {
    @Volatile
    private var INSTANCE: OSSPlayerController? = null

    fun getInstance(context: Context): OSSPlayerController {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: OSSPlayerController(context.applicationContext).also { INSTANCE = it }
        }
    }
}
```

## Next Steps

1. Add detailed logging to track the flow of position updates:
   - Log position values in service's tracking coroutine
   - Log when observers/collectors receive updates
   - Log when UI is updated with new values

2. Verify singleton implementation:
   - Add instance identification to logs
   - Ensure proper context usage

3. Synchronize UI update mechanisms:
   - Decide on a single source of truth for UI updates
   - Remove redundant update paths if necessary

4. Consider simplifying the architecture:
   - Direct service-to-UI updates without intermediate controllers
   - Use a dedicated ViewModel for UI state
