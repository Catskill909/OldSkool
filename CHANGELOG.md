# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Changed
- Simplified bottom navigation implementation
  - Removed custom spacing overrides
  - Reverted to Material Design defaults
  - Documented spacing control challenges
  - Prepared for future custom navigation implementation

### Added
- Added custom splash screen with app branding
  - Implemented proper image scaling for all device densities
  - Added smooth transition to main content
- Enhanced media notification system
  - Fixed notification click behavior to maintain playback state
  - Improved navigation handling for better UX
  - Updated notification styling and controls

### Fixed
- Resolved notification click issues causing audio interruption
- Fixed fragment recreation on notification clicks
- Improved navigation state management
- Added proper error handling for media session

### Previous Changes

### Added
- Implemented infinite scrolling list with WordPress API integration
  - Added WordPressPost data model with Parcelable support
  - Created PostsAdapter for RecyclerView
  - Implemented ArchivesViewModel for data management
  - Added Safe Args for type-safe navigation
- Integrated Google's Oswald font using downloadable fonts system
- Enhanced bottom navigation with larger icons (36dp) and text (16sp)
- Added proper spacing and padding in bottom navigation bar (80dp height)
- Implemented native audio playback functionality
  - Added OSSMediaManager for MediaPlayer control
  - Integrated play/pause functionality with UI state management
  - Added progress bar with seek capability
  - Implemented proper lifecycle management for audio playback

### Changed
- Optimized list item layout with proper text sizing and spacing
- Removed uppercase styling from navigation and list items
- Improved text readability with adjusted letter spacing
- Updated bottom navigation layout to prevent icon/text overlap

### Fixed
- Resolved WebView security warnings with proper documentation
- Removed unused WebKit imports
- Cleaned up build artifacts from git tracking
- Updated .gitignore and added .gitattributes for better source control

### In Progress
- Lock screen metadata integration
  - Media session controls
  - Album art display
  - Track information

### Planned
- Background audio playback optimization
- Call handling and audio interruption management
- Enhanced error handling and retry logic
- Buffering improvements for smoother playback
