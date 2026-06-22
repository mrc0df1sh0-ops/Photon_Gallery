package com.inferno.gallery.data

/**
 * Centralized bucket name constants used throughout the app.
 * Eliminates magic strings and prevents typo-related bugs.
 */
object BucketNames {
    const val ALL = "All"
    const val CAMERA = "Camera"
    const val VIDEOS = "Videos"
    const val FAVORITES = "Favorites"
    const val TRASH = "Trash"
    const val SCREENSHOTS = "Screenshots"
    const val SCREENRECORDINGS = "Screenrecordings"
    const val SCREEN_RECORDS = "Screen records"
    const val SCREEN_RECORDS_NO_SPACE = "Screenrecords"
    const val SCREEN_RECORD = "ScreenRecord"
    const val SCREENSHOT = "Screenshot"

    // Virtual buckets (not actual MediaStore bucket names)
    const val TELEGRAM_CLOUD = "telegram_cloud"
    const val SEARCH_TEXT = "search_text"
    const val SEARCH_SMART = "search_smart"
}
