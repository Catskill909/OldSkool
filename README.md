# Old Skool Sessions Android App

A native Android application for Old Skool Sessions, featuring enhanced audio playback and content management.

## Implementation Status

### Completed Features

#### Content Management
- ✅ Infinite scrolling list powered by WordPress API
- ✅ Rich media display with images and titles
- ✅ Type-safe navigation with Safe Args
- ✅ Responsive list-to-detail transitions

#### WebView Integration
- ✅ Secure WebView configuration
- ✅ JavaScript enabled with proper security measures
- ✅ Mixed content blocking
- ✅ Cache management

### In Progress

#### Detail View Implementation
- ✅ Full-screen cover art display
- ✅ Title and artist information with Oswald typography
- ✅ Play/Pause button implementation
- ✅ Audio progress bar with seek functionality

#### Audio System Integration
- ✅ Native MediaPlayer implementation
- ✅ Basic playback controls (play/pause)
- ✅ Progress tracking and seeking
- 🔄 Lock screen controls and metadata
- 🔄 Audio focus and interruption handling

### Planned Features

#### Audio Service Components
- ExoPlayer integration for reliable playback
- Foreground service for background audio
- Buffering and streaming optimization
- Error handling and retry logic

#### Media Session Features
- Lock screen media controls (play/pause, skip)
- Real-time metadata updates (title, artist, artwork)
- Audio focus management
- Phone call interruption handling
- Notification controls with artwork

## Architecture

### Tech Stack
- Kotlin
- AndroidX components
- Material Design 3
- ViewModel & LiveData
- Navigation component with Safe Args
- Glide for image loading
- Retrofit for API calls

### Key Components
- `WordPressPost`: Data model for posts (Parcelable)
- `PostsAdapter`: RecyclerView adapter for infinite scrolling
- `ArchivesViewModel`: Manages post data and pagination
- `LiveFragment`: Handles WebView audio playback
- `OSSMediaManager`: Controls native audio playback
- `PlayerDetailFragment`: Handles player UI and controls

## URLs

- Live & Archives: https://oldskoolsessions.com/OSS/
- Info/Soundboard: https://oldskoolsessions.com/soundboard/
- Contact: https://starkey.digital/contact-oss/

## Development Setup

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle files
4. Run on device or emulator (min SDK 24)

## Contributing

1. Create a feature branch
2. Make your changes
3. Submit a pull request

Please follow the existing code style and add appropriate tests.

### Audio Implementation

#### Browser Audio Control
- Smart navigation system that prevents unwanted WebView reloads
- Automatic audio stopping and cleanup when switching tabs
- Disabled active tab reselection to maintain audio state
- Uses JavaScript integration to control media elements
- Complete audio player state reset on navigation
- Handles WebView lifecycle properly

#### Native Audio Player (In Progress)
- Will handle remote MP3 playback
- Integrated with bottom navigation for audio control
- Shared audio management between browser and native player

## Building

1. Clone the repository
2. Open in Android Studio
3. Build and run on your device or emulator

## License

All rights reserved. Contact Old Skool Sessions for usage permissions.
