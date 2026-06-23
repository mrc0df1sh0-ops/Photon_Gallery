package com.inferno.gallery.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class DockStyle { PILL, FULL_WIDTH }

val Context.dataStore by preferencesDataStore(name = "user_settings")

class SettingsRepository private constructor(private val context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        val THEME_MODE = stringPreferencesKey("theme_mode")
        val VIEW_MODE = stringPreferencesKey("view_mode")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val ALBUM_SORT_ORDER = stringPreferencesKey("album_sort_order")
        val DOCK_STYLE = stringPreferencesKey("dock_style")
        val USE_MATERIAL_YOU = booleanPreferencesKey("use_material_you")
        val USE_AMOLED_BLACK = booleanPreferencesKey("use_amoled_black")
        val GRID_AUTO_PLAY = booleanPreferencesKey("grid_auto_play")
        val SELECTED_FILTER_INDEX = androidx.datastore.preferences.core.intPreferencesKey("selected_filter_index")
        val GRID_CELLS_COUNT = androidx.datastore.preferences.core.intPreferencesKey("grid_cells_count")
        val THUMBNAIL_CORNER_RADIUS = floatPreferencesKey("thumbnail_corner_radius")
        val USE_FULL_SCREEN = booleanPreferencesKey("use_full_screen")
        val OCR_INDEXING_ENABLED = booleanPreferencesKey("ocr_indexing_enabled")
        val TELEGRAM_BOT_TOKEN = stringPreferencesKey("telegram_bot_token")
        val TELEGRAM_BOT_TOKENS = stringPreferencesKey("telegram_bot_tokens")
        val TELEGRAM_CHAT_ID = stringPreferencesKey("telegram_chat_id")
        val TELEGRAM_BACKUP_ENABLED = booleanPreferencesKey("telegram_backup_enabled")
        val TELEGRAM_STRIP_LOCATION = booleanPreferencesKey("telegram_strip_location")
        val TELEGRAM_AUTO_BACKUP_FOLDERS = stringPreferencesKey("telegram_auto_backup_folders")
        val TELEGRAM_AUTO_BACKUP_WIFI_ONLY = booleanPreferencesKey("telegram_auto_backup_wifi_only")
        val TELEGRAM_AUTO_BACKUP_BATTERY_LOW_PAUSE = booleanPreferencesKey("telegram_auto_backup_battery_low_pause")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val STRIP_METADATA_ON_SHARE = booleanPreferencesKey("strip_metadata_on_share")
        val SMART_SEARCH_AUTO_INDEX = booleanPreferencesKey("smart_search_auto_index")
        val SMART_SEARCH_THRESHOLD = floatPreferencesKey("smart_search_threshold")
        val CONFIRM_DELETE_ENABLED = booleanPreferencesKey("confirm_delete_enabled")
        val AUTOPLAY_WITH_SOUND_ENABLED = booleanPreferencesKey("autoplay_with_sound_enabled")
        val AUTO_CLEAN_TRASH_ENABLED = booleanPreferencesKey("auto_clean_trash_enabled")
        val AUTO_CLEAN_TRASH_DAYS = androidx.datastore.preferences.core.intPreferencesKey("auto_clean_trash_days")
        val CACHE_THUMBNAILS_ENABLED = booleanPreferencesKey("cache_thumbnails_enabled")
        val MAX_BRIGHTNESS_ENABLED = booleanPreferencesKey("max_brightness_enabled")
        val EXCLUDED_FOLDERS = stringPreferencesKey("excluded_folders")
        val HDR_DISPLAY_ENABLED = booleanPreferencesKey("hdr_display_enabled")
        val PINNED_FOLDERS = stringPreferencesKey("pinned_folders")
        val SHOW_HIDDEN_ALBUMS = booleanPreferencesKey("show_hidden_albums")
    }

    val themeModeFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[THEME_MODE] ?: "SYSTEM"
        }

    val viewModeFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[VIEW_MODE] ?: "Grouped"
        }

    val sortOrderFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[SORT_ORDER] ?: "NewToOld"
        }

    val albumSortOrderFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[ALBUM_SORT_ORDER] ?: "NameAsc"
        }

    val dockStyleFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[DOCK_STYLE] ?: DockStyle.PILL.name
        }

    val useMaterialYouFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[USE_MATERIAL_YOU] ?: true
        }

    val useAmoledBlackFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[USE_AMOLED_BLACK] ?: true
        }

    val smartSearchAutoIndexFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[SMART_SEARCH_AUTO_INDEX] ?: false
        }

    val smartSearchThresholdFlow: Flow<Float> = context.dataStore.data
        .map { preferences ->
            preferences[SMART_SEARCH_THRESHOLD] ?: 0.23f
        }

    val confirmDeleteEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[CONFIRM_DELETE_ENABLED] ?: true
        }

    val autoplayWithSoundEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[AUTOPLAY_WITH_SOUND_ENABLED] ?: false
        }

    val autoCleanTrashEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[AUTO_CLEAN_TRASH_ENABLED] ?: false
        }

    val autoCleanTrashDaysFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[AUTO_CLEAN_TRASH_DAYS] ?: 30
        }

    val cacheThumbnailsEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[CACHE_THUMBNAILS_ENABLED] ?: true
        }

    val maxBrightnessEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[MAX_BRIGHTNESS_ENABLED] ?: false
        }

    suspend fun updateThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    suspend fun updateViewMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[VIEW_MODE] = mode
        }
    }

    suspend fun updateSortOrder(order: String) {
        context.dataStore.edit { preferences ->
            preferences[SORT_ORDER] = order
        }
    }

    suspend fun updateAlbumSortOrder(order: String) {
        context.dataStore.edit { preferences ->
            preferences[ALBUM_SORT_ORDER] = order
        }
    }

    suspend fun updateDockStyle(style: DockStyle) {
        context.dataStore.edit { preferences ->
            preferences[DOCK_STYLE] = style.name
        }
    }

    suspend fun updateUseMaterialYou(use: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_MATERIAL_YOU] = use
        }
    }

    suspend fun updateUseAmoledBlack(use: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_AMOLED_BLACK] = use
        }
    }

    val gridAutoPlayFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[GRID_AUTO_PLAY] ?: true
        }

    suspend fun toggleGridAutoPlay(current: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[GRID_AUTO_PLAY] = !current
        }
    }



    val selectedFilterIndexFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[SELECTED_FILTER_INDEX] ?: 1
        }

    suspend fun updateSelectedFilterIndex(index: Int) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_FILTER_INDEX] = index
        }
    }

    val gridCellsCountFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[GRID_CELLS_COUNT] ?: 4
        }

    suspend fun updateGridCellsCount(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[GRID_CELLS_COUNT] = count
        }
    }

    val thumbnailCornerRadiusFlow: Flow<Float> = context.dataStore.data
        .map { preferences ->
            preferences[THUMBNAIL_CORNER_RADIUS] ?: 0f
        }

    suspend fun updateThumbnailCornerRadius(radius: Float) {
        context.dataStore.edit { preferences ->
            preferences[THUMBNAIL_CORNER_RADIUS] = radius
        }
    }

    val useFullScreenFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[USE_FULL_SCREEN] ?: false
        }

    suspend fun updateUseFullScreen(use: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_FULL_SCREEN] = use
        }
    }

    val ocrIndexingEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[OCR_INDEXING_ENABLED] ?: true
        }

    suspend fun updateOcrIndexingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[OCR_INDEXING_ENABLED] = enabled
        }
    }

    val telegramBotTokenFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[TELEGRAM_BOT_TOKEN] ?: ""
        }

    val telegramChatIdFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[TELEGRAM_CHAT_ID] ?: ""
        }

    val telegramBackupEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[TELEGRAM_BACKUP_ENABLED] ?: false
        }

    val telegramBotTokensFlow: Flow<List<String>> = context.dataStore.data
        .map { preferences ->
            val tokensStr = preferences[TELEGRAM_BOT_TOKENS] ?: ""
            if (tokensStr.isNotBlank()) {
                tokensStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                val singleToken = preferences[TELEGRAM_BOT_TOKEN] ?: ""
                if (singleToken.isNotBlank()) listOf(singleToken) else emptyList()
            }
        }

    val telegramStripLocationFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[TELEGRAM_STRIP_LOCATION] ?: true
        }

    val telegramAutoBackupFoldersFlow: Flow<Set<String>> = context.dataStore.data
        .map { preferences ->
            val foldersStr = preferences[TELEGRAM_AUTO_BACKUP_FOLDERS] ?: ""
            if (foldersStr.isBlank()) emptySet() else foldersStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }

    val telegramAutoBackupWifiOnlyFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[TELEGRAM_AUTO_BACKUP_WIFI_ONLY] ?: true
        }

    val telegramAutoBackupBatteryLowPauseFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[TELEGRAM_AUTO_BACKUP_BATTERY_LOW_PAUSE] ?: true
        }

    suspend fun updateTelegramBotToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[TELEGRAM_BOT_TOKEN] = token
        }
    }

    suspend fun updateTelegramBotTokens(tokens: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[TELEGRAM_BOT_TOKENS] = tokens.joinToString(",")
        }
    }

    suspend fun updateTelegramChatId(chatId: String) {
        context.dataStore.edit { preferences ->
            preferences[TELEGRAM_CHAT_ID] = chatId
        }
    }

    suspend fun updateTelegramBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[TELEGRAM_BACKUP_ENABLED] = enabled
        }
    }

    suspend fun updateTelegramStripLocation(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[TELEGRAM_STRIP_LOCATION] = enabled
        }
    }

    suspend fun updateTelegramAutoBackupFolders(folders: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[TELEGRAM_AUTO_BACKUP_FOLDERS] = folders.joinToString(",")
        }
    }

    suspend fun updateTelegramAutoBackupWifiOnly(wifiOnly: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[TELEGRAM_AUTO_BACKUP_WIFI_ONLY] = wifiOnly
        }
    }

    suspend fun updateTelegramAutoBackupBatteryLowPause(pause: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[TELEGRAM_AUTO_BACKUP_BATTERY_LOW_PAUSE] = pause
        }
    }

    val onboardingCompletedFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[ONBOARDING_COMPLETED] ?: false
        }

    suspend fun updateOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED] = completed
        }
    }

    val stripMetadataOnShareFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[STRIP_METADATA_ON_SHARE] ?: true
        }

    suspend fun updateStripMetadataOnShare(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[STRIP_METADATA_ON_SHARE] = enabled
        }
    }

    suspend fun updateSmartSearchAutoIndex(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SMART_SEARCH_AUTO_INDEX] = enabled
        }
    }

    suspend fun updateSmartSearchThreshold(threshold: Float) {
        context.dataStore.edit { preferences ->
            preferences[SMART_SEARCH_THRESHOLD] = threshold
        }
    }

    suspend fun updateConfirmDeleteEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[CONFIRM_DELETE_ENABLED] = enabled
        }
    }

    suspend fun updateAutoplayWithSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTOPLAY_WITH_SOUND_ENABLED] = enabled
        }
    }

    suspend fun updateAutoCleanTrashEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_CLEAN_TRASH_ENABLED] = enabled
        }
    }

    suspend fun updateAutoCleanTrashDays(days: Int) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_CLEAN_TRASH_DAYS] = days
        }
    }

    suspend fun updateCacheThumbnailsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[CACHE_THUMBNAILS_ENABLED] = enabled
        }
    }

    suspend fun updateMaxBrightnessEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MAX_BRIGHTNESS_ENABLED] = enabled
        }
    }

    val excludedFoldersFlow: Flow<Set<String>> = context.dataStore.data
        .map { preferences ->
            val foldersStr = preferences[EXCLUDED_FOLDERS] ?: ""
            if (foldersStr.isBlank()) emptySet() else foldersStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }

    suspend fun updateExcludedFolders(folders: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[EXCLUDED_FOLDERS] = folders.joinToString(",")
        }
    }

    val pinnedFoldersFlow: Flow<Set<String>> = context.dataStore.data
        .map { preferences ->
            val str = preferences[PINNED_FOLDERS] ?: ""
            if (str.isBlank()) emptySet() else str.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }

    suspend fun togglePinnedFolder(folder: String) {
        context.dataStore.edit { preferences ->
            val current = (preferences[PINNED_FOLDERS] ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
            if (current.contains(folder)) {
                current.remove(folder)
            } else {
                current.add(folder)
            }
            preferences[PINNED_FOLDERS] = current.joinToString(",")
        }
    }

    val showHiddenAlbumsFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[SHOW_HIDDEN_ALBUMS] ?: false
        }

    suspend fun updateShowHiddenAlbums(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_HIDDEN_ALBUMS] = show
        }
    }
}
