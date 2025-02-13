# Media Playback State Audit

## Latest Architectural Analysis (2025-02-13)

### Core State Management Issues
- First media item loads and plays correctly
- Subsequent items fail to update metadata
- Lock screen controls become unresponsive
- Notification state desyncs from actual playback

### Architectural Pain Points
1. **Component State Triangle**
   - MediaSession (playback state)
   - MediaController (UI state)
   - NotificationManager (system UI state)
   - Each component has independent lifecycle
   - Synchronization failures common

2. **State Ownership Conflicts**
   - Multiple components trying to manage state
   - Race conditions during updates
   - Lifecycle events causing state loss

3. **System Constraints**
   - Android aggressive service management
   - Lock screen security boundaries
   - IPC complexity
   - Version-specific behavior

## Strategic Recommendations

### 1. Single Source of Truth
- MediaService as primary state owner
- All other components observe only
- Implement unidirectional data flow
- Use StateFlow for state management

### 2. Lifecycle-Aware Design
- Proper foreground service implementation
- Handle all lifecycle transitions
- Maintain token validity
- Implement automatic reconnection

### 3. State Validation Protocol
- Add validation checkpoints
- Implement comprehensive logging
- Create state recovery mechanisms
- Monitor state transitions

## Implementation Roadmap

### Phase 1: Stabilization
- [ ] Implement robust service binding
- [ ] Add comprehensive state logging
- [ ] Create error recovery mechanisms

### Phase 2: Architecture Evolution
- [ ] Move to unidirectional data flow
- [ ] Implement state machine
- [ ] Add validation checkpoints

### Phase 3: Monitoring
- [ ] Add telemetry
- [ ] Implement automatic recovery
- [ ] Create self-healing mechanisms


## OSSMediaManager State
- `mediaPlayer`: MediaPlayer instance
  - Initialized: When preparing audio
  - Released: In `release()` method
  - Null: After release or initialization

- `mediaService`: OSSMediaService instance
  - Initialized: On service connection
  - Null: On service disconnection
  - Reconnection: Attempted when null and needed

- `sourceFragmentId`: Int
  - Set: When preparing audio
  - Used: For callback identification

## Failure Timeline
1. **Initial Crash** (2025-02-13 12:53:48)
   - Symptom: Null MediaManager in Fragment cleanup
   - Root Cause: Lifecycle race between service binding and fragment destruction
   - Fix: Added initialization check

2. **Playback Hang** (2025-02-13 12:40:01)
   - Symptom: First track plays, subsequent failures
   - Root Cause: Service connection not re-established
   - Fix: Added exponential backoff retry logic

3. **State Desync** (2025-02-13 12:39:57)
   - Symptom: UI shows playing while audio stopped
   - Root Cause: Missing error state propagation
   - Fix: Added error channel in StateFlow

## Regression Analysis
- All failures stem from lifecycle management complexity
- Service binding timing impacts multiple components
- Error recovery paths need hardening

## StateFlow Properties
- `isPlaying`: Boolean
  - Updated: On play/pause toggle and errors
  - Initial: false

- `currentPosition`: Long
  - Updated: During playback progress
  - Initial: 0L

- `duration`: Long
  - Updated: When media is prepared
  - Initial: 0L

- `currentTitle`: String?
  - Updated: When preparing new audio
  - Initial: null

- `currentArtwork`: String?
  - Updated: When preparing new audio
  - Initial: null

## Pending Operations
- `pendingOperation`: (() -> Unit)?
  - Set: When operation requested but service not connected
  - Executed: When service connects
  - Cleared: After execution

## Critical Sections
1. Service Connection
   - Service binding initiated in init
   - Reconnection attempted when service null
   - Metadata restored on reconnection

2. Media Preparation
   - Previous player released
   - New player created and prepared
   - Error handling for preparation failures

3. Playback Control
   - Play/Pause state management
   - Error handling for playback operations
   - Service state synchronization

4. Resource Cleanup
   - Player release
   - Service unbinding
   - State reset

## Error States
1. Service Disconnection
   - Detected through null checks
   - Reconnection attempted
   - Operations queued if needed

2. Player Errors
   - Logged with details
   - State reset
   - UI notified through StateFlow

3. Preparation Failures
   - Exception handling
   - Resource cleanup
   - Error state propagation

## State Recovery
1. Service Recovery
   - Automatic reconnection
   - Metadata restoration
   - Pending operation execution

2. Player Recovery
   - Resource cleanup
   - New player creation
   - State restoration

## Validation Points
1. Service Connection
   - Check before operations
   - Queue if disconnected
   - Restore on reconnect

2. Player State
   - Null checks before use
   - Error state handling
   - Resource cleanup

3. Metadata
   - Title validation
   - Artwork loading
   - Service updates

## Testing Notes
1. Service Tests
   - Connection/Disconnection
   - State restoration
   - Operation queueing

2. Player Tests
   - Preparation sequence
   - Error handling
   - Resource cleanup

3. State Flow Tests
   - Value updates
   - Error propagation
   - UI synchronization
