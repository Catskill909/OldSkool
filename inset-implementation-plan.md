# System Insets Implementation Plan

## Current Issues
- The app uses immersive mode which hides system bars
- Layout doesn't account for window insets
- Content may be hidden under the status bar when it appears

## Implementation Plan

### 1. Update MainActivity.kt
- Remove immersive mode implementation
- Add proper window insets handling:
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    window.setDecorFitsSystemWindows(false)
} else {
    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
}

ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
    val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
    view.setPadding(0, insets.top, 0, insets.bottom)
    windowInsets
}
```

### 2. Update themes.xml
```xml
<style name="Theme.OldSkool" parent="Theme.MaterialComponents.NoActionBar">
    <!-- Make status bar transparent -->
    <item name="android:statusBarColor">@android:color/transparent</item>
    <!-- Light status bar icons for dark status bar background -->
    <item name="android:windowLightStatusBar">false</item>
</style>
```

### 3. Update activity_main.xml
```xml
<!-- Add android:fitsSystemWindows="false" to root layout -->
<androidx.constraintlayout.widget.ConstraintLayout
    android:fitsSystemWindows="false"
    ... >

    <!-- Update header image constraints -->
    <ImageView
        android:id="@+id/header_image"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:adjustViewBounds="true"
        android:scaleType="fitCenter"
        android:src="@drawable/header"
        app:layout_constraintTop_toTopOf="parent" />
</androidx.constraintlayout.widget.ConstraintLayout>
```

## Expected Results
- Content will be visible edge-to-edge
- System bars will remain visible with a transparent status bar
- Layout will automatically adjust when system bars appear/disappear
- All content will remain visible and properly spaced

## Testing Plan
1. Verify header image is visible and not obscured by status bar
2. Check that content adjusts properly when system bars appear/disappear
3. Test on different Android versions (API 29+)
4. Verify behavior in both portrait and landscape orientations