package com.inferno.gallery.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "core_media")
data class CoreMediaEntity(
    @PrimaryKey val id: Long,
    val uriString: String,
    val filePath: String,
    val bucketName: String,
    val dateAdded: Long,
    val dateModified: Long,
    val size: Long,
    val name: String,
    val mimeType: String?,
    val isVideo: Boolean,
    val durationMs: Long?,
    @ColumnInfo(name = "is_indexed_clip") val isIndexedClip: Boolean = false,
    @ColumnInfo(name = "is_indexed_ocr") val isIndexedOcr: Boolean = false
)

@Entity(tableName = "media_vectors")
data class MediaVectorEntity(
    @PrimaryKey val mediaId: Long,
    // Stored as raw bytes (FloatArray serialized via ByteBuffer).
    // Isolated into its own table to prevent CursorWindow bloat when reading core_media.
    val clipVector: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MediaVectorEntity
        if (mediaId != other.mediaId) return false
        if (!clipVector.contentEquals(other.clipVector)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = mediaId.hashCode()
        result = 31 * result + clipVector.contentHashCode()
        return result
    }
}

// NOTE: ImageFtsEntity / @Fts5 are intentionally absent.
// The image_fts FTS5 virtual table is created via raw SQL in DatabaseProvider's
// RoomDatabase.Callback.onCreate() hook, bypassing Room's KSP annotation processor
// which has a KSP 2.2.x incompatibility with @Fts5. All FTS reads/writes go through
// DatabaseProvider.insertFtsRow() and DatabaseProvider.searchFts() using openHelper directly.
