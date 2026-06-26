package com.inferno.gallery.data

/**
 * Pure-function SQL query builder for media queries.
 * Extracted from GalleryViewModel for testability.
 *
 * All methods are stateless and produce SQL fragments + bind args
 * that can be verified without Android framework dependencies.
 */
object MediaQueryBuilder {

    data class QueryConditions(
        val conditions: List<String>,
        val args: List<Any>
    )

    /**
     * Builds SQL WHERE conditions and bind args for media queries.
     *
     * @param bucket       The target bucket name (e.g. "Camera", "Favorites", "search_text")
     * @param filterIndex  The dock filter index (0=All, 1=Camera, 2=Screenshots)
     * @param excluded     Set of folder names to exclude
     * @param favIds       Set of favorite media ID strings
     * @param ftsIds       List of media ID strings from FTS text search
     * @param smartIds     List of media ID strings from smart/semantic search
     */
    fun buildMediaConditions(
        bucket: String?,
        filterIndex: Int,
        excluded: Set<String> = emptySet(),
        favIds: Set<String> = emptySet(),
        ftsIds: List<String> = emptyList(),
        smartIds: List<String> = emptyList()
    ): QueryConditions {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<Any>()

        val folderName = when (filterIndex) {
            1 -> BucketNames.CAMERA
            2 -> BucketNames.SCREENSHOTS
            else -> null
        }

        when {
            bucket == BucketNames.SEARCH_TEXT -> {
                if (ftsIds.isEmpty()) {
                    conditions.add("cm.id IN (0)")
                } else {
                    conditions.add("cm.id IN (${ftsIds.joinToString(",")})")
                    conditions.add("cm.bucketName != 'Trash'")
                }
            }
            bucket == BucketNames.SEARCH_SMART -> {
                if (smartIds.isEmpty()) {
                    conditions.add("cm.id IN (0)")
                } else {
                    conditions.add("cm.id IN (${smartIds.joinToString(",")})")
                    conditions.add("cm.bucketName != 'Trash'")
                }
            }
            bucket == BucketNames.FAVORITES -> {
                val numericIds = favIds.mapNotNull { it.toLongOrNull() }
                if (numericIds.isEmpty()) {
                    conditions.add("cm.id IN (0)")
                } else {
                    conditions.add("cm.id IN (${numericIds.joinToString(",")})")
                    conditions.add("cm.bucketName != 'Trash'")
                }
            }
            bucket == BucketNames.ALL || (bucket == null && folderName == null) -> {
                conditions.add("cm.bucketName != 'Trash'")
            }
            bucket == BucketNames.TELEGRAM_CLOUD -> {
                conditions.add("tb.backupStatus = 'SUCCESS'")
            }
            bucket == BucketNames.VIDEOS -> {
                conditions.add("cm.isVideo = 1")
                conditions.add("cm.bucketName != 'Trash'")
            }
            bucket != null -> {
                conditions.add("cm.bucketName = ?")
                args.add(bucket)
            }
            folderName != null -> {
                conditions.add("cm.bucketName = ?")
                args.add(folderName)
            }
            else -> {
                conditions.add("cm.bucketName != 'Trash'")
            }
        }

        // Apply excluded folders filter for main views
        val shouldApplyExclusion = bucket == null || bucket == BucketNames.ALL || bucket == BucketNames.VIDEOS
        if (shouldApplyExclusion && excluded.isNotEmpty()) {
            val placeholders = excluded.joinToString(",") { "?" }
            conditions.add("cm.bucketName NOT IN ($placeholders)")
            args.addAll(excluded)
        }

        return QueryConditions(conditions, args)
    }

    fun buildWhereClause(qc: QueryConditions): String {
        return if (qc.conditions.isNotEmpty()) "WHERE ${qc.conditions.joinToString(" AND ")} " else ""
    }

    fun buildOrderClause(order: String): String = when (order) {
        "NewToOld" -> "ORDER BY cm.dateAdded DESC"
        "OldToNew" -> "ORDER BY cm.dateAdded ASC"
        "SmallToBig" -> "ORDER BY cm.size ASC"
        "BigToSmall" -> "ORDER BY cm.size DESC"
        "NameAsc" -> "ORDER BY cm.name ASC"
        else -> "ORDER BY cm.dateAdded DESC"
    }

    fun buildOrderClause(order: String, bucket: String?, smartIds: List<String>): String {
        if (bucket == BucketNames.SEARCH_SMART) {
            val numericIds = smartIds.mapNotNull { it.toLongOrNull() }
            if (numericIds.isNotEmpty()) {
                val cases = numericIds.mapIndexed { index, id -> "WHEN $id THEN $index" }
                    .joinToString(" ")
                return "ORDER BY CASE cm.id $cases ELSE ${numericIds.size} END"
            }
        }
        return buildOrderClause(order)
    }
}
