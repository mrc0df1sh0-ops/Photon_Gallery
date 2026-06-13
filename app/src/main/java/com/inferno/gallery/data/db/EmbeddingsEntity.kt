package com.inferno.gallery.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "media_embeddings")
data class MediaEmbeddingEntity(
    @PrimaryKey val mediaId: Long,
    val embedding: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MediaEmbeddingEntity
        if (mediaId != other.mediaId) return false
        if (!embedding.contentEquals(other.embedding)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = mediaId.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}

class EmbeddingConverter {
    @TypeConverter
    fun toByteArray(array: FloatArray?): ByteArray? {
        if (array == null) return null
        val buffer = ByteBuffer.allocate(array.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        array.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    @TypeConverter
    fun toFloatArray(bytes: ByteArray?): FloatArray? {
        if (bytes == null) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val array = FloatArray(bytes.size / 4)
        buffer.asFloatBuffer().get(array)
        return array
    }
}

@Dao
interface EmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbedding(embedding: MediaEmbeddingEntity)

    @Query("SELECT * FROM media_embeddings WHERE mediaId = :mediaId LIMIT 1")
    suspend fun getEmbedding(mediaId: Long): MediaEmbeddingEntity?

    @Query("SELECT * FROM media_embeddings")
    suspend fun getAllEmbeddings(): List<MediaEmbeddingEntity>

    @Query("SELECT id FROM core_media WHERE isVideo = 0 AND id NOT IN (SELECT mediaId FROM media_embeddings)")
    suspend fun getUnindexedMediaIds(): List<Long>

    @Query("SELECT COUNT(id) FROM core_media WHERE isVideo = 0 AND id NOT IN (SELECT mediaId FROM media_embeddings)")
    fun observeUnindexedCount(): Flow<Int>

    @Query("DELETE FROM media_embeddings WHERE mediaId = :mediaId")
    suspend fun deleteEmbedding(mediaId: Long)

    @Query("DELETE FROM media_embeddings")
    suspend fun clearAllEmbeddings()
}
