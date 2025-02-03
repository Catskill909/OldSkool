# Old Skool Sessions Android App

A native Android application for Old Skool Sessions, featuring enhanced audio playback and content management.

## Core Features

### Audio Service
- Browser-based audio playback
- Native in-app audio player for remote URLs
- Background audio playback support
- Intelligent audio handling (call interruptions, system events)
- Optimized streaming for both on-demand and live content

### Metadata Integration
- Lock screen media controls
- Real-time metadata updates
- Azuracast API integration

### Content Management
- Infinite scrolling content list powered by WordPress API
- Rich media display (images, artist info, titles)
- Detailed view with integrated audio player
- Responsive list-to-detail navigation

### Original Features
- Live stream access
- Archives of past sessions
- Information page
- Contact form

## Features

- Dark mode design
- WebView integration for seamless content display
- Material Design bottom navigation
- Custom header image
- Responsive layout

## URLs

- Live & Archives: https://oldskoolsessions.com/OSS/
- Info/Soundboard: https://oldskoolsessions.com/soundboard/
- Contact: https://starkey.digital/contact-oss/

## Development

Built with:
- Kotlin
- AndroidX components
- Material Design components
- WebView for content display

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
