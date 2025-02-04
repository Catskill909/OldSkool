# Media Style Notification Implementation Plan

## Overview
Implement media controls and metadata display in the notification tray and lock screen using Android's MediaSession API. This will provide users with standard media controls and track information without needing to open the app.

## Components Required

### 1. MediaSession
- Create a `MediaSessionCompat` instance to manage playback state
- Handle media button events from notification, lock screen, and hardware buttons
- Update metadata in real-time as tracks change

### 2. MediaStyle Notification
- Create a foreground service notification with media controls
- Display album art, title, and artist information
- Implement standard media controls:
  - Play/Pause
  - Previous/Next (if applicable)
  - Seek bar (optional in notification)

## Implementation Steps

### 1. Update Android Manifest
```xml
<!-- Add required permissions -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

<!-- Declare the media service -->
<service
    android:name=".media.OSSMediaService"
    android:exported="false"
    android:foregroundServiceType="mediaPlayback" />
```

### 2. Create MediaService Class
- Extend `MediaBrowserServiceCompat`
- Initialize `MediaSessionCompat`
- Handle media button events
- Manage notification lifecycle

### 3. Implement MediaSession Callbacks
```kotlin
private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
    override fun onPlay() {
        // Handle play command
    }
    
    override fun onPause() {
        // Handle pause command
    }
    
    override fun onSeekTo(pos: Long) {
        // Handle seeking
    }
}
```

### 4. Create Media Style Notification
```kotlin
private fun createNotification(): Notification {
    // Create notification channel (for Android O and above)
    // Build media style notification with controls
    // Set metadata and artwork
    // Add action buttons (play/pause, etc.)
}
```

### 5. Update MediaSession Metadata
```kotlin
private fun updateMetadata(title: String, artist: String, artwork: Bitmap?) {
    mediaSession.setMetadata(
        MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork)
            .build()
    )
}
```

### 6. Handle Playback State Updates
```kotlin
private fun updatePlaybackState(state: Int) {
    mediaSession.setPlaybackState(
        PlaybackStateCompat.Builder()
            .setState(state, currentPosition, 1.0f)
            .setActions(PlaybackStateCompat.ACTION_PLAY or 
                       PlaybackStateCompat.ACTION_PAUSE or
                       PlaybackStateCompat.ACTION_SEEK_TO)
            .build()
    )
}
```

## Known Failure Cases and Error Handling

### 1. Small Play Icon in Notification
Problem: The small play icon appears in the notification even when trying to hide it.
Solution:
- Remove all notification actions

### 2. Critical: Media Player State Management Issues

#### 2.1 Notification Click Crash (FIXED)
Problem: App crashes when clicking notification to open player.

Root Cause Analysis:
1. Fragment Argument Requirements
   - PlayerDetailFragment requires non-null 'title' argument
   - Navigation to fragment failing due to missing arguments
   - No default values set for required arguments

2. Activity Launch & Task Stack Issues
   - PendingIntent flags not handling task stack properly
   - Activity launch mode conflicts with navigation
   - Back stack not preserved when launching from notification

Fix Implemented:
- Added proper argument handling in PlayerDetailFragment
- Set default values for required arguments
- Improved error handling and recovery

#### 2.2 Critical: Notification Navigation State Loss (CURRENT ISSUE)
Problem: Clicking notification tray (except play/pause) stops audio and creates new empty player view.

Deep Analysis:
1. Fundamental Navigation Design Flaw
   - We're treating notification click as a "new player request"
   - Creating new fragment instance instead of showing existing one
   - Not distinguishing between play/pause and view navigation
   - Losing media session state due to improper navigation

2. Media Session State Management
   - Current approach:
     * Creates new player fragment on notification click
     * Resets media session when navigating
     * Loses connection to active playback session
   - Correct approach:
     * Should maintain single media session
     * Navigation should only change visibility
     * Playback control separate from view navigation

3. Android Navigation Component Usage
   - Current implementation:
     * Uses standard navigation
     * Creates new fragment instance
     * Doesn't preserve media state
   - Required approach:
     * Use single-top navigation mode
     * Reuse existing fragment if present
     * Preserve media session across navigation

4. Notification Intent Handling
   - Current implementation:
     * Same intent handling for all notification actions
     * No distinction between view and control actions
     * Recreates player state on every navigation
   - Required approach:
     * Separate intents for view vs control actions
     * View actions should only focus existing player
     * Control actions should only affect playback

Required Fix:
1. Notification Intent Design
   ```kotlin
   // Different actions for view vs control
   const val ACTION_VIEW_PLAYER = "...action.VIEW_PLAYER"
   const val ACTION_CONTROL_PLAYBACK = "...action.CONTROL_PLAYBACK"
   ```

2. Navigation Pattern
   ```kotlin
   // In MainActivity
   private fun handleIntent(intent: Intent?) {
       when (intent?.action) {
           ACTION_VIEW_PLAYER -> showExistingPlayer()
           ACTION_CONTROL_PLAYBACK -> handlePlaybackControl()
       }
   }
   ```

3. Fragment Management
   - Use FragmentManager.findFragmentByTag()
   - Show/hide fragments instead of recreating
   - Maintain single instance of player fragment

4. Media Session State
   - Keep MediaSession active independent of UI
   - UI should observe MediaSession state
   - Navigation should not affect playback

Implementation Priority:
1. Fix notification intent handling to separate view/control
2. Implement proper fragment management
3. Ensure MediaSession persistence
4. Add proper state observation in UI

#### 2.3 Resolution (FIXED)
The issue has been resolved by implementing proper navigation and state management:

1. Navigation Improvements:
   - Used NavOptions with launchSingleTop and restoreState
   - Removed fragment recreation on notification click
   - Properly handled activity reordering

2. Intent Handling:
   - Simplified notification intent flags
   - Removed unnecessary sourceFragmentId
   - Used REORDER_TO_FRONT for activity focus

3. State Management:
   - Maintained single player instance
   - Preserved media session state
   - Kept playback uninterrupted

Key Code Changes:
```kotlin
// MainActivity.kt - Proper navigation
val navOptions = NavOptions.Builder()
    .setLaunchSingleTop(true)
    .setRestoreState(true)
    .build()
navController.navigate(R.id.navigation_player_detail, null, navOptions)

// OSSMediaService.kt - Simplified intent
val contentIntent = Intent(this, MainActivity::class.java).apply {
    action = MainActivity.ACTION_OPEN_PLAYER
    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
}
```

Result:
- Notification tray click only shows existing player
- Play/pause controls only affect playback
- No audio interruption on navigation
- UI state properly maintained

Previous Failed Attempts:
1. Simple Navigation
   - Direct navigation to player (NavController not ready)
   - Crashes due to Fragment transaction exceptions

2. State-Based Navigation
   - Complex state tracking led to race conditions
   - State lost during process recreation

3. Delayed Navigation
   - Timer-based delays unreliable
   - Activity lifecycle state unclear

4. Lifecycle-Aware Navigation
   - onResume-based navigation still too early
   - Fragment transaction still unsafe

Required Comprehensive Fix:
1. Proper Activity Launch Mode
   - Add android:launchMode="singleTop"
   - Handle onNewIntent properly
   - Preserve task/back stack

2. PendingIntent Configuration
   - Use FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE
   - Add FLAG_ACTIVITY_NEW_TASK for proper task stack
   - Consider FLAG_ACTIVITY_CLEAR_TOP for clean navigation

3. Safe Navigation Pattern
   - Use Navigation component's deep linking
   - Implement proper backstack handling
   - Add fragment transaction error handling

4. State Management
   - Save navigation request in saved instance state
   - Handle process death and recreation
   - Implement proper state restoration

5. Error Tracking
   - Add comprehensive error logging
   - Track all lifecycle states
   - Monitor fragment transaction failures
- Use app icon (`R.mipmap.ic_launcher`) as small icon
- Set minimal MediaStyle without compact view actions
- Avoid setting any transport controls in notification

### 2. Play Button Crashes
Problem: App crashes when tapping play button in app or notification.
Solution:
- Add try/catch around all media control operations
- Disable play button if error occurs
- Log errors for debugging
- Handle null MediaPlayer cases

### 3. Seekbar Crashes
Problem: App crashes when using seekbar in notification.
Solution:
- Add try/catch around seek operations
- Validate seek position (>= 0)
- Reset seeking state on error
- Disable seekbar if setup fails
- Add error logging

### 4. MediaSession Error Handling
Problem: Unhandled errors in MediaSession callbacks.
Solution:
- Add try/catch in all MediaSession callbacks
- Validate parameters before use
- Log errors with stack traces
- Update UI state on errors

### 5. Service Connection Issues
Problem: Service connection failures or disconnects.
Solution:
- Handle null service cases
- Add reconnection logic
- Log connection state changes
- Update UI when service disconnects

### 6. Notification Updates
Problem: Notification updates can fail or crash.
Solution:
- Handle notification channel creation errors
- Check for null metadata
- Use safe defaults for missing data
- Log notification update failures

## Integration Points

### 1. OSSMediaManager Integration
- Update `OSSMediaManager` to work with `MediaSession`
- Forward playback controls to `MediaSession`
- Sync playback state between app and notification
- Add comprehensive error handling

### 2. PlayerDetailFragment Integration
- Connect to `MediaSession` for state updates
- Update UI based on `MediaSession` state
- Forward user actions to `MediaSession`

## Testing Checklist

1. **Basic Functionality**
   - [ ] Notification appears when playback starts
   - [ ] Notification updates with track changes
   - [ ] Controls work from notification
   - [ ] Controls work from lock screen

2. **Edge Cases**
   - [ ] Handle audio focus changes
   - [ ] Handle headphone unplugging
   - [ ] Handle incoming calls
   - [ ] Service cleanup on app close

3. **UI/UX**
   - [ ] Artwork loads correctly
   - [ ] Text doesn't get truncated
   - [ ] Controls are responsive
   - [ ] Notification priority is appropriate

## Resources

- [Android MediaSession Documentation](https://developer.android.com/guide/topics/media-apps/working-with-a-media-session)
- [Media Style Notifications Guide](https://developer.android.com/develop/ui/views/notifications/custom-notification)
- [MediaStyle Sample Code](https://github.com/android/uamp)

## Next Steps

1. Implement `OSSMediaService` with basic `MediaSession` support
2. Add notification creation and management
3. Integrate with existing `OSSMediaManager`
4. Test on different Android versions and devices
5. Add support for additional media controls if needed
