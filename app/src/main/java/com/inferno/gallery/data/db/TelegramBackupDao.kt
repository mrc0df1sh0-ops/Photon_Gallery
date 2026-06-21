package com.inferno.gallery.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TelegramBackupDao {
    @Query("SELECT * FROM telegram_backups WHERE mediaId = :mediaId")
    suspend fun getBackupForMedia(mediaId: Long): TelegramBackupEntity?

    @Query("SELECT * FROM telegram_backups WHERE backupStatus = 'PENDING'")
    suspend fun getPendingBackups(): List<TelegramBackupEntity>

    @Query("SELECT * FROM telegram_backups WHERE backupStatus = 'FAILED'")
    suspend fun getFailedBackups(): List<TelegramBackupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(backup: TelegramBackupEntity)

    @Query("DELETE FROM telegram_backups WHERE mediaId = :mediaId")
    suspend fun deleteBackup(mediaId: Long)

    @Query("SELECT mediaId FROM telegram_backups WHERE backupStatus = 'SUCCESS'")
    suspend fun getSuccessfulBackupIds(): List<Long>

    @Query("""
        SELECT cm.*, tb.telegramFileId, tb.telegramThumbFileId, tb.backupStatus 
        FROM core_media cm 
        INNER JOIN telegram_backups tb ON cm.id = tb.mediaId 
        WHERE tb.backupStatus = 'SUCCESS' 
        ORDER BY cm.dateAdded DESC
    """)
    fun observeCloudMedia(): Flow<List<CloudMediaItem>>

    @Query("SELECT COUNT(mediaId) FROM telegram_backups WHERE backupStatus = 'PENDING'")
    fun observePendingBackupsCount(): Flow<Int>

    @Query("SELECT * FROM telegram_backups")
    fun observeAllBackups(): Flow<List<TelegramBackupEntity>>

    @Query("SELECT * FROM telegram_backups WHERE mediaId IN (:mediaIds)")
    suspend fun getBackupsForMediaIds(mediaIds: List<Long>): List<TelegramBackupEntity>

    @Query("""
        SELECT cm.*, tb.telegramFileId, tb.telegramThumbFileId, tb.backupStatus 
        FROM core_media cm 
        INNER JOIN telegram_backups tb ON cm.id = tb.mediaId 
        WHERE tb.backupStatus = 'FAILED' 
        ORDER BY tb.backupTimestamp DESC
    """)
    fun observeFailedBackups(): Flow<List<CloudMediaItem>>

    @Query("UPDATE telegram_backups SET backupStatus = 'PENDING' WHERE backupStatus = 'FAILED'")
    suspend fun retryAllFailedBackups()
}

data class CloudMediaItem(
    val id: Long,
    val uriString: String,
    val filePath: String,
    val bucketName: String,
    val dateAdded: Long,
    val dateModified: Long,
    val size: Long,
    val name: String,
    val isVideo: Boolean,
    val durationMs: Long?,
    val telegramFileId: String?,
    val telegramThumbFileId: String?,
    val backupStatus: String
)
