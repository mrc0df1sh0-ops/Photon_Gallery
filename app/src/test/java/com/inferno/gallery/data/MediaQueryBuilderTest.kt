package com.inferno.gallery.data

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [MediaQueryBuilder].
 *
 * These run on the JVM (no Android device needed) and verify the SQL
 * query building logic that powers all media list screens.
 */
class MediaQueryBuilderTest {

    // ── buildMediaConditions ──

    @Test
    fun `All bucket excludes Trash`() {
        val qc = MediaQueryBuilder.buildMediaConditions(bucket = "All", filterIndex = 0)
        assertEquals(1, qc.conditions.size)
        assertTrue(qc.conditions[0].contains("Trash"))
        assertTrue(qc.args.isEmpty())
    }

    @Test
    fun `null bucket and filterIndex 0 excludes Trash`() {
        val qc = MediaQueryBuilder.buildMediaConditions(bucket = null, filterIndex = 0)
        assertEquals(1, qc.conditions.size)
        assertEquals("cm.bucketName != 'Trash'", qc.conditions[0])
    }

    @Test
    fun `Camera filter uses parameterized query`() {
        val qc = MediaQueryBuilder.buildMediaConditions(bucket = null, filterIndex = 1)
        assertTrue(qc.conditions.any { it.contains("cm.bucketName = ?") })
        assertEquals("Camera", qc.args[0])
    }

    @Test
    fun `Screenshots filter uses parameterized query`() {
        val qc = MediaQueryBuilder.buildMediaConditions(bucket = null, filterIndex = 2)
        assertTrue(qc.conditions.any { it.contains("cm.bucketName = ?") })
        assertEquals("Screenshots", qc.args[0])
    }

    @Test
    fun `Videos bucket filters isVideo and excludes Trash`() {
        val qc = MediaQueryBuilder.buildMediaConditions(bucket = "Videos", filterIndex = 0)
        assertEquals(2, qc.conditions.size)
        assertTrue(qc.conditions.any { it.contains("cm.isVideo = 1") })
        assertTrue(qc.conditions.any { it.contains("Trash") })
    }

    @Test
    fun `Favorites with IDs produces IN clause and excludes Trash`() {
        val qc = MediaQueryBuilder.buildMediaConditions(
            bucket = "Favorites",
            filterIndex = 0,
            favIds = setOf("1", "2", "3")
        )
        assertEquals(2, qc.conditions.size)
        assertTrue(qc.conditions[0].contains("cm.id IN (1,2,3)"))
        assertTrue(qc.conditions[1].contains("Trash"))
    }

    @Test
    fun `Favorites with empty IDs produces impossible condition`() {
        val qc = MediaQueryBuilder.buildMediaConditions(
            bucket = "Favorites",
            filterIndex = 0,
            favIds = emptySet()
        )
        assertEquals(1, qc.conditions.size)
        assertEquals("cm.id IN (0)", qc.conditions[0])
    }

    @Test
    fun `Favorites ignores non-numeric IDs`() {
        val qc = MediaQueryBuilder.buildMediaConditions(
            bucket = "Favorites",
            filterIndex = 0,
            favIds = setOf("abc", "def")
        )
        assertEquals(1, qc.conditions.size)
        assertEquals("cm.id IN (0)", qc.conditions[0])
    }

    @Test
    fun `search_text with FTS IDs produces IN clause`() {
        val qc = MediaQueryBuilder.buildMediaConditions(
            bucket = "search_text",
            filterIndex = 0,
            ftsIds = listOf("10", "20", "30")
        )
        assertEquals(2, qc.conditions.size)
        assertTrue(qc.conditions[0].contains("cm.id IN (10,20,30)"))
        assertTrue(qc.conditions[1].contains("Trash"))
    }

    @Test
    fun `search_text with empty FTS IDs produces impossible condition`() {
        val qc = MediaQueryBuilder.buildMediaConditions(
            bucket = "search_text",
            filterIndex = 0,
            ftsIds = emptyList()
        )
        assertEquals(1, qc.conditions.size)
        assertEquals("cm.id IN (0)", qc.conditions[0])
    }

    @Test
    fun `search_smart with IDs produces IN clause`() {
        val qc = MediaQueryBuilder.buildMediaConditions(
            bucket = "search_smart",
            filterIndex = 0,
            smartIds = listOf("100", "200")
        )
        assertEquals(2, qc.conditions.size)
        assertTrue(qc.conditions[0].contains("cm.id IN (100,200)"))
    }

    @Test
    fun `telegram_cloud bucket checks backup status`() {
        val qc = MediaQueryBuilder.buildMediaConditions(bucket = "telegram_cloud", filterIndex = 0)
        assertEquals(1, qc.conditions.size)
        assertEquals("tb.backupStatus = 'SUCCESS'", qc.conditions[0])
    }

    @Test
    fun `specific bucket uses parameterized query`() {
        val qc = MediaQueryBuilder.buildMediaConditions(bucket = "DCIM", filterIndex = 0)
        assertEquals(1, qc.conditions.size)
        assertEquals("cm.bucketName = ?", qc.conditions[0])
        assertEquals("DCIM", qc.args[0])
    }

    // ── Excluded Folders ──

    @Test
    fun `excluded folders applied for null bucket`() {
        val qc = MediaQueryBuilder.buildMediaConditions(
            bucket = null,
            filterIndex = 0,
            excluded = setOf("Telegram", "WhatsApp")
        )
        assertTrue(qc.conditions.any { it.contains("NOT IN") })
        assertTrue(qc.args.contains("Telegram"))
        assertTrue(qc.args.contains("WhatsApp"))
    }

    @Test
    fun `excluded folders applied for All bucket`() {
        val qc = MediaQueryBuilder.buildMediaConditions(
            bucket = "All",
            filterIndex = 0,
            excluded = setOf("Downloads")
        )
        assertTrue(qc.conditions.any { it.contains("NOT IN") })
        assertEquals("Downloads", qc.args[0])
    }

    @Test
    fun `excluded folders applied for Videos bucket`() {
        val qc = MediaQueryBuilder.buildMediaConditions(
            bucket = "Videos",
            filterIndex = 0,
            excluded = setOf("TikTok")
        )
        assertTrue(qc.conditions.any { it.contains("NOT IN") })
    }

    @Test
    fun `excluded folders NOT applied for specific bucket`() {
        val qc = MediaQueryBuilder.buildMediaConditions(
            bucket = "DCIM",
            filterIndex = 0,
            excluded = setOf("Telegram")
        )
        assertFalse(qc.conditions.any { it.contains("NOT IN") })
    }

    @Test
    fun `excluded folders NOT applied for Favorites`() {
        val qc = MediaQueryBuilder.buildMediaConditions(
            bucket = "Favorites",
            filterIndex = 0,
            excluded = setOf("Telegram"),
            favIds = setOf("1")
        )
        assertFalse(qc.conditions.any { it.contains("NOT IN") })
    }

    @Test
    fun `excluded folders use parameterized placeholders`() {
        val qc = MediaQueryBuilder.buildMediaConditions(
            bucket = null,
            filterIndex = 0,
            excluded = setOf("A", "B", "C")
        )
        val notInCondition = qc.conditions.first { it.contains("NOT IN") }
        // Should have 3 ? placeholders
        assertEquals(3, notInCondition.count { it == '?' })
        assertEquals(listOf("A", "B", "C"), qc.args.drop(0).takeLast(3))
    }

    // ── buildWhereClause ──

    @Test
    fun `buildWhereClause joins conditions with AND`() {
        val qc = MediaQueryBuilder.QueryConditions(
            conditions = listOf("cm.isVideo = 1", "cm.bucketName != 'Trash'"),
            args = emptyList()
        )
        val where = MediaQueryBuilder.buildWhereClause(qc)
        assertEquals("WHERE cm.isVideo = 1 AND cm.bucketName != 'Trash' ", where)
    }

    @Test
    fun `buildWhereClause returns empty for no conditions`() {
        val qc = MediaQueryBuilder.QueryConditions(conditions = emptyList(), args = emptyList())
        val where = MediaQueryBuilder.buildWhereClause(qc)
        assertEquals("", where)
    }

    // ── buildOrderClause ──

    @Test
    fun `NewToOld order`() {
        assertEquals("ORDER BY cm.dateAdded DESC", MediaQueryBuilder.buildOrderClause("NewToOld"))
    }

    @Test
    fun `OldToNew order`() {
        assertEquals("ORDER BY cm.dateAdded ASC", MediaQueryBuilder.buildOrderClause("OldToNew"))
    }

    @Test
    fun `SmallToBig order`() {
        assertEquals("ORDER BY cm.size ASC", MediaQueryBuilder.buildOrderClause("SmallToBig"))
    }

    @Test
    fun `BigToSmall order`() {
        assertEquals("ORDER BY cm.size DESC", MediaQueryBuilder.buildOrderClause("BigToSmall"))
    }

    @Test
    fun `NameAsc order`() {
        assertEquals("ORDER BY cm.name ASC", MediaQueryBuilder.buildOrderClause("NameAsc"))
    }

    @Test
    fun `unknown order defaults to NewToOld`() {
        assertEquals("ORDER BY cm.dateAdded DESC", MediaQueryBuilder.buildOrderClause("InvalidOrder"))
    }
}
