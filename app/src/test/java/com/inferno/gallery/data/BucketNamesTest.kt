package com.inferno.gallery.data

import org.junit.Assert.*
import org.junit.Test

/**
 * Sanity tests for [BucketNames] constants.
 * Guards against accidental renames that would break SQL queries and navigation.
 */
class BucketNamesTest {

    @Test
    fun `system bucket names match MediaStore conventions`() {
        assertEquals("Camera", BucketNames.CAMERA)
        assertEquals("Screenshots", BucketNames.SCREENSHOTS)
        assertEquals("Videos", BucketNames.VIDEOS)
        assertEquals("Trash", BucketNames.TRASH)
        assertEquals("All", BucketNames.ALL)
        assertEquals("Favorites", BucketNames.FAVORITES)
    }

    @Test
    fun `virtual bucket names are stable`() {
        assertEquals("telegram_cloud", BucketNames.TELEGRAM_CLOUD)
        assertEquals("search_text", BucketNames.SEARCH_TEXT)
        assertEquals("search_smart", BucketNames.SEARCH_SMART)
    }

    @Test
    fun `screen recording variants are defined`() {
        assertEquals("Screenrecordings", BucketNames.SCREENRECORDINGS)
        assertEquals("Screen records", BucketNames.SCREEN_RECORDS)
        assertEquals("Screenrecords", BucketNames.SCREEN_RECORDS_NO_SPACE)
        assertEquals("ScreenRecord", BucketNames.SCREEN_RECORD)
        assertEquals("Screenshot", BucketNames.SCREENSHOT)
    }
}
