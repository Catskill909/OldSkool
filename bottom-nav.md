# Bottom Navigation Audit and Controls

## Available Controls for BottomNavigationView

### Layout Attributes
- `android:layout_height` - Controls overall height of the navigation bar
- `android:minHeight` - Minimum height the navigation bar can be
- `android:layout_gravity` - Position of the navigation bar
- `app:labelVisibilityMode` - How labels are displayed (labeled, selected, unlabeled, auto)
- `app:itemHorizontalTranslationEnabled` - Whether items shift horizontally

### Item Styling
- `itemIconSize` - Size of the navigation icons
- `itemIconPadding` - Space between icon and text label
- `itemIconTint` - Color of the icons
- `itemTextColor` - Color of the text labels
- `itemTextAppearance` - Text style for labels
- `itemTextAppearanceActive` - Text style for active item
- `itemTextAppearanceInactive` - Text style for inactive items
- `itemPaddingTop` - Padding above items
- `itemPaddingBottom` - Padding below items
- `itemBackground` - Background for each item
- `itemRippleColor` - Ripple effect color

### Spacing Controls
- `android:padding` - Overall padding of the navigation bar
- `android:paddingTop` - Top padding
- `android:paddingBottom` - Bottom padding
- `itemSpacing` - Space between items

## Previous Attempts and Issues

### Attempt 1: Basic Dimension Changes
```xml
<dimen name="nav_icon_size">56dp</dimen>
<dimen name="nav_min_height">120dp</dimen>
<dimen name="nav_text_size">20sp</dimen>
```
Issue: Icons and text too large, excessive spacing

### Attempt 2: Reduced Spacing
```xml
<dimen name="nav_item_spacing">8dp</dimen>
<dimen name="nav_item_padding_vertical">8dp</dimen>
```
Issue: Still too much space between icons and text

### Attempt 3: Minimal Spacing
```xml
<dimen name="nav_item_spacing">2dp</dimen>
<dimen name="nav_item_padding_vertical">4dp</dimen>
```
Issue: Text and icons started touching

### Attempt 4: Direct Style Control
```xml
<style name="BottomNavigation">
    <item name="itemIconSize">48dp</item>
    <item name="itemIconPadding">6dp</item>
</style>
```
Issue: Icons and text overlapping

### Attempt 5: Direct Padding Control
```xml
<style name="BottomNavigation">
    <item name="android:background">@color/black</item>
    <item name="itemIconTint">@color/bottom_nav_colors</item>
    <item name="itemTextColor">@color/bottom_nav_colors</item>
    <item name="labelVisibilityMode">labeled</item>
    <item name="itemPaddingTop">2dp</item>
    <item name="itemPaddingBottom">2dp</item>
    <item name="itemIconSize">24dp</item>
</style>
```
Issue: Icons touching top of nav bar, spacing between icon and text still excessive

### Attempt 6: Material Design Standards
```xml
<style name="BottomNavigation">
    <item name="android:background">@color/black</item>
    <item name="itemIconTint">@color/bottom_nav_colors</item>
    <item name="itemTextColor">@color/bottom_nav_colors</item>
    <item name="labelVisibilityMode">labeled</item>
    <item name="android:paddingTop">8dp</item>
    <item name="android:paddingBottom">8dp</item>
    <item name="itemIconSize">24dp</item>
    <item name="itemIconPadding">2dp</item>
</style>
```
With layout height set to 56dp (Material standard)
Issue: Icons and text overlapping each other, completely broken layout

### Analysis of Repeated Failures
1. Material Design's BottomNavigationView seems to have internal layout logic that fights against custom spacing
2. Attempts to follow Material Design standards exactly aren't producing expected results
3. The component appears to handle spacing differently than documented
4. Multiple spacing mechanisms (padding, margins, item spacing) conflict with each other
5. The relationship between fixed height and internal spacing is unclear

### Root Problem
The fundamental issue appears to be that BottomNavigationView's internal implementation handles spacing in ways that aren't well-documented and don't respond predictably to standard Android layout attributes. What should be a simple layout task has become complex due to the opaque nature of the component's internal spacing logic.
```xml
<style name="BottomNavigation">
    <item name="android:background">@color/black</item>
    <item name="itemIconTint">@color/bottom_nav_colors</item>
    <item name="itemTextColor">@color/bottom_nav_colors</item>
    <item name="labelVisibilityMode">labeled</item>
    <item name="itemPaddingTop">2dp</item>
    <item name="itemPaddingBottom">2dp</item>
    <item name="itemIconSize">24dp</item>
</style>
```
Issue: Icons touching top of nav bar, spacing between icon and text still excessive

### Final Solution: Minimal Icon Padding
```xml
<style name="BottomNavigation">
    <item name="android:background">@color/black</item>
    <item name="itemIconTint">@color/bottom_nav_colors</item>
    <item name="itemTextColor">@color/bottom_nav_colors</item>
    <item name="labelVisibilityMode">labeled</item>
    <item name="itemIconPadding">4dp</item>
    <item name="itemIconSize">@dimen/icon_size_medium</item>
</style>
```
Success: Just enough space to prevent touching without excessive gaps

## Root Causes of Issues
1. Mixing multiple padding/spacing controls that conflict
2. Not considering the relationship between height and content
3. Overriding some attributes while leaving others at default
4. Not accounting for Material Design's built-in spacing

## Correct Approach for Tablet Layout

The bottom navigation needs:
1. Proper overall height that accommodates both icon and text
2. Consistent spacing between elements
3. Proper scaling for different device sizes

### Base Layout (phones)
```xml
<BottomNavigationView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:minHeight="64dp"
    app:itemIconSize="24dp"
    app:itemIconPadding="4dp" />
```

### Tablet Layout (sw600dp)
```xml
<BottomNavigationView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:minHeight="80dp"
    app:itemIconSize="32dp"
    app:itemIconPadding="6dp" />
```

## Next Steps
1. Reset all custom styles and start with Material Design defaults
2. Add only necessary overrides for size and spacing
3. Test on multiple device sizes
4. Ensure consistent spacing across all items
5. Validate height calculations based on icon size + padding + text size

## Material Design Guidelines
- Bottom navigation height: 56dp (mobile), 80dp (tablet)
- Icon size: 24dp (mobile), 32dp (tablet)
- Label padding: 4dp (mobile), 6dp (tablet)
- Text size: 12sp (mobile), 14sp (tablet)
