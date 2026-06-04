package com.inferno.gallery.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM core_media ORDER BY dateAdded DESC")
    fun observeAllMedia(): Flow<List<CoreMediaEntity>>

    @Query("SELECT * FROM core_media")
    suspend fun getAllMedia(): List<CoreMediaEntity>

    @Query("SELECT id FROM core_media")
    suspend fun getAllMediaIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(media: List<CoreMediaEntity>)

    @Query("DELETE FROM core_media WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
    
    @Query("UPDATE core_media SET is_indexed_ocr = :isIndexed WHERE id = :id")
    suspend fun updateOcrIndexStatus(id: Long, isIndexed: Boolean)

    @Query("UPDATE core_media SET is_indexed_clip = :isIndexed WHERE id = :id")
    suspend fun updateClipIndexStatus(id: Long, isIndexed: Boolean)

    @Query("SELECT * FROM core_media WHERE is_indexed_ocr = 0 OR is_indexed_clip = 0 LIMIT 100")
    suspend fun getUnindexedMedia(): List<CoreMediaEntity>

    // PERF OPT-6: Removed LIMIT 100 — the three-stage pipeline in AIIndexWorker now
    // processes the entire unindexed set in one run; no retry loop needed.
    @Query("SELECT * FROM core_media WHERE is_indexed_clip = 0 AND isVideo = 0")
    suspend fun getUnindexedClipMedia(): List<CoreMediaEntity>

    @Query("SELECT COUNT(id) FROM core_media WHERE isVideo = 0")
    suspend fun getTotalImageCount(): Int

    @Query("SELECT COUNT(id) FROM core_media WHERE isVideo = 0 AND (is_indexed_ocr = 0 OR is_indexed_clip = 0)")
    suspend fun getUnindexedImageCount(): Int

    @Query("SELECT COUNT(id) FROM core_media WHERE isVideo = 0 AND is_indexed_clip = 0")
    suspend fun getUnindexedClipImageCount(): Int

    @Query("SELECT COUNT(id) FROM core_media WHERE isVideo = 0")
    fun observeTotalImageCount(): Flow<Int>

    @Query("SELECT COUNT(id) FROM core_media WHERE isVideo = 0 AND is_indexed_clip = 0")
    fun observeUnindexedClipImageCount(): Flow<Int>
}

@Dao
interface SearchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVector(vector: MediaVectorEntity)

    // PERF OPT-4: Batch insert — avoids per-row transaction overhead for the pipeline.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVectors(vectors: List<MediaVectorEntity>)

    @Query("SELECT * FROM media_vectors")
    suspend fun getAllVectors(): List<MediaVectorEntity>

    @Query("SELECT * FROM media_vectors WHERE mediaId = :mediaId")
    suspend fun getVectorForMedia(mediaId: Long): MediaVectorEntity?

    @Query("DELETE FROM media_vectors")
    suspend fun clearAllVectors()

    // NOTE: FTS5 insert + search are handled via DatabaseProvider.insertFtsRow() /
    // DatabaseProvider.searchFts() using openHelper.writableDatabase directly,
    // because Room's compile-time @Query validator cannot see tables created via Callback.
}

@Database(
    entities = [
        CoreMediaEntity::class,
        MediaVectorEntity::class
        // ImageFtsEntity is intentionally excluded: @Fts5 has a KSP 2.2.x incompatibility.
        // The FTS5 virtual table is instead created manually in GalleryDatabase.Companion.getDatabase()
        // via a RoomDatabase.Callback onCreate hook using raw CREATE VIRTUAL TABLE SQL.
    ],
    version = 2,
    exportSchema = false
)
abstract class GalleryDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun searchDao(): SearchDao
}
