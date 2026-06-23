package com.inferno.gallery.ui

import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.ui.draw.alpha
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.HelpOutline
import com.inferno.gallery.ui.ConnectionTestResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.SwitchDefaults
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.work.WorkInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import com.inferno.gallery.ui.DetectedChatsResult
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import com.inferno.gallery.ui.theme.ShapeLarge
import com.inferno.gallery.ui.theme.ShapeLargeIncreased3
import com.inferno.gallery.ui.theme.ShapeExtraLarge
import com.inferno.gallery.ui.theme.photonContainer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inferno.gallery.ui.ThemeMode
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.path

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: SettingsViewModel = viewModel(),
    galleryViewModel: GalleryViewModel = viewModel(),
    onBackClick: () -> Unit = {},
    onNavigateToVault: () -> Unit = {},
    activeSection: String? = null,
    onActiveSectionChange: (String?) -> Unit = {}
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val useMaterialYou by viewModel.useMaterialYou.collectAsState()
    val useAmoledBlack by viewModel.useAmoledBlack.collectAsState()
    val useFullScreen by viewModel.useFullScreen.collectAsState()
    val showHiddenAlbums by viewModel.showHiddenAlbums.collectAsState()
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dockStyle by viewModel.dockStyle.collectAsState()
    val gridCellsCount by galleryViewModel.gridCellsCount.collectAsState()
    val thumbnailCornerRadius by viewModel.thumbnailCornerRadius.collectAsState()
    val gridAutoPlay by galleryViewModel.gridAutoPlay.collectAsState()
    val ocrProgressState by viewModel.ocrProgress.collectAsState()
    val clipProgressState by viewModel.clipProgress.collectAsState()
    val ocrIndexWorkInfo by viewModel.ocrIndexWorkInfo.collectAsState(initial = null)
    val totalImagesCount by viewModel.totalImagesCount.collectAsState()
    val unindexedOcrImagesCount by viewModel.unindexedOcrImagesCount.collectAsState()
    val stripMetadataOnShare by viewModel.stripMetadataOnShare.collectAsState()
    val cacheThumbnailsEnabled by viewModel.cacheThumbnailsEnabled.collectAsState()
    val maxBrightnessEnabled by viewModel.maxBrightnessEnabled.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val savedTokens by viewModel.telegramBotTokens.collectAsState()
    val savedChatId by viewModel.telegramChatId.collectAsState()
    val backupEnabled by viewModel.telegramBackupEnabled.collectAsState()
    val stripLocation by viewModel.telegramStripLocation.collectAsState()
    val showFolderDialog = remember { mutableStateOf(false) }
    val selectedFolders by viewModel.telegramAutoBackupFolders.collectAsState()
    val allFolders by viewModel.allBucketNames.collectAsState()

    val smartSearchModelDownloaded by viewModel.smartSearchModelDownloaded.collectAsState()
    val modelDownloadWorkInfo by viewModel.modelDownloadWorkInfo.collectAsState(initial = null)
    val smartSearchIndexWorkInfo by viewModel.smartSearchIndexWorkInfo.collectAsState(initial = null)
    val unindexedSmartSearchCount by viewModel.unindexedSmartSearchCount.collectAsState()
    val smartSearchAutoIndex by viewModel.smartSearchAutoIndex.collectAsState()
    val smartSearchThreshold by viewModel.smartSearchThreshold.collectAsState()

    var passwordVisiblePrimary by remember { mutableStateOf(false) }
    var passwordVisibleSecondary by remember { mutableStateOf(false) }


    var primaryTokenInput by remember(savedTokens) { mutableStateOf(savedTokens.getOrNull(0) ?: "") }
    var secondaryTokenInput by remember(savedTokens) { mutableStateOf(savedTokens.getOrNull(1) ?: "") }
    var localChatId by remember(savedChatId) { mutableStateOf(savedChatId) }

    var showClearIndexConfirm by remember { mutableStateOf(false) }
    var showDeleteModelConfirm by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }

    val isCurrentlyDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    if (showClearIndexConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearIndexConfirm = false },
            title = { Text("Clear Smart Search Index") },
            text = { Text("This will wipe out all computed image embeddings for semantic search. You will need to run the indexer again to use smart search. Are you sure you want to proceed?") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showClearIndexConfirm = false
                        viewModel.clearSmartSearchEmbeddings()
                    }
                ) {
                    Text("Clear Index", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showClearIndexConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteModelConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteModelConfirm = false },
            title = { Text("Delete AI Model Files") },
            text = { Text("This will delete the local ONNX model files (approx. 30MB+). You will not be able to use semantic search or index new images until you download them again. Are you sure?") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showDeleteModelConfirm = false
                        viewModel.deleteSmartSearchModel()
                    }
                ) {
                    Text("Delete Files", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteModelConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showLicensesDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text("Open Source Licenses") },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Photon Gallery is built using open source software:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Jetpack Compose (Apache 2.0 License)\n" +
                               "• Coil 3 (Apache 2.0 License)\n" +
                               "• Room Database (Apache 2.0 License)\n" +
                               "• ONNX Runtime (MIT License)\n" +
                               "• MobileCLIP (MIT License)\n" +
                               "• Google Sans Flex (SIL Open Font License 1.1)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showLicensesDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    androidx.activity.compose.BackHandler(enabled = activeSection != null) {
        onActiveSectionChange(null)
    }

    Column(
        modifier = Modifier
            .padding(contentPadding)
            .fillMaxSize()
    ) {
            AnimatedContent(
                targetState = activeSection,
                transitionSpec = {
                    androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessHigh
                        )
                    ) togetherWith
                    androidx.compose.animation.fadeOut(
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessHigh
                        )
                    )
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "SettingsSectionContent"
            ) { section ->
                if (section == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CategoryCard(
                            title = "Look \u0026 Feel",
                            subtitle = "Theme, colors, and display preferences",
                            icon = Icons.Outlined.Palette,
                            onClick = { onActiveSectionChange("Look \u0026 Feel") }
                        )
                        CategoryCard(
                            title = "Layout \u0026 Navigation",
                            subtitle = "Grid size, dock style, and shapes",
                            icon = Icons.Outlined.Tune,
                            onClick = { onActiveSectionChange("Layout \u0026 Navigation") }
                        )
                        CategoryCard(
                            title = "General",
                            subtitle = "Full screen, brightness, and performance",
                            icon = Icons.Outlined.Settings,
                            onClick = { onActiveSectionChange("General") }
                        )
                        CategoryCard(
                            title = "Smart Search \u0026 OCR",
                            subtitle = "AI-powered search and text recognition",
                            icon = Icons.Outlined.AutoAwesome,
                            onClick = { onActiveSectionChange("Smart Search \u0026 OCR") }
                        )
                        CategoryCard(
                            title = "Cloud Backup",
                            subtitle = "Telegram cloud and backup options",
                            icon = Icons.Outlined.Cloud,
                            onClick = { onActiveSectionChange("Cloud Backup") }
                        )
                        CategoryCard(
                            title = "Privacy \u0026 Security",
                            subtitle = "Metadata stripping, deletion, and data control",
                            icon = Icons.Outlined.Shield,
                            onClick = { onActiveSectionChange("Privacy \u0026 Security") }
                        )
                        CategoryCard(
                            title = "Private Space",
                            subtitle = "Hidden photos protected with biometric lock",
                            icon = Icons.Outlined.Lock,
                            onClick = {
                                val activity = context as? androidx.fragment.app.FragmentActivity
                                if (activity != null) {
                                    galleryViewModel.vaultAuthManager.authenticate(
                                        activity = activity,
                                        onSuccess = { onNavigateToVault() },
                                        onFailure = {}
                                    )
                                }
                            }
                        )
                        CategoryCard(
                            title = "Excluded Folders",
                            subtitle = "Hide folders from the main gallery",
                            icon = Icons.Outlined.FolderOff,
                            onClick = { onActiveSectionChange("Excluded Folders") }
                        )
                        CategoryCard(
                            title = "About",
                            subtitle = "App information, updates, and licenses",
                            icon = Icons.Outlined.Info,
                            onClick = { onActiveSectionChange("About") }
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (section) {
                            "General" -> {
                                SettingsGroup(title = "Display Preferences") {
                                    ListItem(
                                        leadingContent = { Icon(Icons.Outlined.Fullscreen, contentDescription = null) },
                                        headlineContent = { Text("Full Screen Mode") },
                                        supportingContent = { Text("Hide status bar and navigation bar to maximize content area") },
                                        trailingContent = {
                                            Switch(
                                                checked = useFullScreen,
                                                onCheckedChange = { viewModel.setUseFullScreen(it) },
                                                thumbContent = {
                                                    Icon(
                                                        imageVector = if (useFullScreen) Icons.Outlined.Check else Icons.Outlined.Close,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                                    )
                                                }
                                            )
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )

                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    ListItem(
                                        leadingContent = { Icon(Icons.Outlined.BrightnessHigh, contentDescription = null) },
                                        headlineContent = { Text("Maximize Fullscreen Brightness") },
                                        supportingContent = { Text("Temporarily maximize screen brightness when viewing media in full screen") },
                                        trailingContent = {
                                            Switch(
                                                checked = maxBrightnessEnabled,
                                                onCheckedChange = { viewModel.setMaxBrightnessEnabled(it) },
                                                thumbContent = {
                                                    Icon(
                                                        imageVector = if (maxBrightnessEnabled) Icons.Outlined.Check else Icons.Outlined.Close,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                                    )
                                                }
                                            )
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                }

                                SettingsGroup(title = "Recycle Bin & Deletion") {
                                    val confirmDelete by viewModel.confirmDeleteEnabled.collectAsState()
                                    ListItem(
                                        leadingContent = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                        headlineContent = { Text("Confirm Deletion") },
                                        supportingContent = { Text("Show confirmation dialog when moving media to recycle bin") },
                                        trailingContent = {
                                            Switch(
                                                checked = confirmDelete,
                                                onCheckedChange = { viewModel.setConfirmDeleteEnabled(it) },
                                                thumbContent = {
                                                    Icon(
                                                        imageVector = if (confirmDelete) Icons.Outlined.Check else Icons.Outlined.Close,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                                    )
                                                }
                                            )
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )

                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    val autoCleanTrash by viewModel.autoCleanTrashEnabled.collectAsState()
                                    ListItem(
                                        leadingContent = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                        headlineContent = { Text("Auto-clean Recycle Bin") },
                                        supportingContent = { Text("Automatically delete old items in the recycle bin") },
                                        trailingContent = {
                                            Switch(
                                                checked = autoCleanTrash,
                                                onCheckedChange = { viewModel.setAutoCleanTrashEnabled(it) },
                                                thumbContent = {
                                                    Icon(
                                                        imageVector = if (autoCleanTrash) Icons.Outlined.Check else Icons.Outlined.Close,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                                    )
                                                }
                                            )
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )

                                    if (autoCleanTrash) {
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        val autoCleanDays by viewModel.autoCleanTrashDays.collectAsState()
                                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                            Text(text = "Retention Period: $autoCleanDays Days", style = MaterialTheme.typography.bodyLarge)
                                            SingleChoiceSegmentedButtonRow(
                                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                            ) {
                                                SegmentedButton(
                                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                                    onClick = { viewModel.setAutoCleanTrashDays(7) },
                                                    selected = autoCleanDays == 7,
                                                    label = { Text("7 Days") }
                                                )
                                                SegmentedButton(
                                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                                    onClick = { viewModel.setAutoCleanTrashDays(14) },
                                                    selected = autoCleanDays == 14,
                                                    label = { Text("14 Days") }
                                                )
                                                SegmentedButton(
                                                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                                    onClick = { viewModel.setAutoCleanTrashDays(30) },
                                                    selected = autoCleanDays == 30,
                                                    label = { Text("30 Days") }
                                                )
                                            }
                                        }
                                    }
                                }

                                SettingsGroup(title = "Performance") {
                                    ListItem(
                                        leadingContent = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                                        headlineContent = { Text("Cache Grid Thumbnails") },
                                        supportingContent = { Text("Pre-cache grid thumbnails for instant, super-smooth scrolling (uses device storage)") },
                                        trailingContent = {
                                            Switch(
                                                checked = cacheThumbnailsEnabled,
                                                onCheckedChange = { viewModel.setCacheThumbnailsEnabled(it) },
                                                thumbContent = {
                                                    Icon(
                                                        imageVector = if (cacheThumbnailsEnabled) Icons.Outlined.Check else Icons.Outlined.Close,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                                    )
                                                }
                                            )
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                }
                            }
                            "Privacy & Security" -> {
                                SettingsGroup(title = "Metadata Privacy") {
                                    ListItem(
                                        leadingContent = { Icon(Icons.Outlined.PrivacyTip, contentDescription = null) },
                                        headlineContent = { Text("Strip Metadata Before Sharing") },
                                        supportingContent = { Text("Remove GPS, camera specifications, timestamps, timezone offsets, and author/attribution details when sharing") },
                                        trailingContent = {
                                            Switch(
                                                checked = stripMetadataOnShare,
                                                onCheckedChange = { viewModel.setStripMetadataOnShare(it) },
                                                thumbContent = {
                                                    Icon(
                                                        imageVector = if (stripMetadataOnShare) Icons.Outlined.Check else Icons.Outlined.Close,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                                    )
                                                }
                                            )
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )

                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    ListItem(
                                        leadingContent = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                                        headlineContent = { Text("Strip Location on Backup") },
                                        supportingContent = { Text("Remove GPS coordinates from images before uploading to Telegram Cloud") },
                                        trailingContent = {
                                            Switch(
                                                checked = stripLocation,
                                                onCheckedChange = { viewModel.setTelegramStripLocation(it) },
                                                thumbContent = {
                                                    Icon(
                                                        imageVector = if (stripLocation) Icons.Outlined.Check else Icons.Outlined.Close,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                                    )
                                                }
                                            )
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                }

                                SettingsGroup(title = "Recycle Bin & Deletion") {
                                    val confirmDelete by viewModel.confirmDeleteEnabled.collectAsState()
                                    ListItem(
                                        leadingContent = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                        headlineContent = { Text("Confirm Deletion") },
                                        supportingContent = { Text("Show confirmation dialog when moving media to recycle bin") },
                                        trailingContent = {
                                            Switch(
                                                checked = confirmDelete,
                                                onCheckedChange = { viewModel.setConfirmDeleteEnabled(it) },
                                                thumbContent = {
                                                    Icon(
                                                        imageVector = if (confirmDelete) Icons.Outlined.Check else Icons.Outlined.Close,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                                    )
                                                }
                                            )
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )

                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    val autoCleanTrash by viewModel.autoCleanTrashEnabled.collectAsState()
                                    ListItem(
                                        leadingContent = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                        headlineContent = { Text("Auto-clean Recycle Bin") },
                                        supportingContent = { Text("Automatically delete old items in the recycle bin") },
                                        trailingContent = {
                                            Switch(
                                                checked = autoCleanTrash,
                                                onCheckedChange = { viewModel.setAutoCleanTrashEnabled(it) },
                                                thumbContent = {
                                                    Icon(
                                                        imageVector = if (autoCleanTrash) Icons.Outlined.Check else Icons.Outlined.Close,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                                    )
                                                }
                                            )
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )

                                    if (autoCleanTrash) {
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        val autoCleanDays by viewModel.autoCleanTrashDays.collectAsState()
                                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                            Text(text = "Retention Period: $autoCleanDays Days", style = MaterialTheme.typography.bodyLarge)
                                            SingleChoiceSegmentedButtonRow(
                                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                            ) {
                                                SegmentedButton(
                                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                                    onClick = { viewModel.setAutoCleanTrashDays(7) },
                                                    selected = autoCleanDays == 7,
                                                    label = { Text("7 Days") }
                                                )
                                                SegmentedButton(
                                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                                    onClick = { viewModel.setAutoCleanTrashDays(14) },
                                                    selected = autoCleanDays == 14,
                                                    label = { Text("14 Days") }
                                                )
                                                SegmentedButton(
                                                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                                    onClick = { viewModel.setAutoCleanTrashDays(30) },
                                                    selected = autoCleanDays == 30,
                                                    label = { Text("30 Days") }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            "Look & Feel" -> {
                                SettingsGroup(title = "Theme") {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        SingleChoiceSegmentedButtonRow(
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            SegmentedButton(
                                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                                onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                                                selected = themeMode == ThemeMode.SYSTEM,
                                                icon = { Icon(Icons.Outlined.BrightnessAuto, contentDescription = null) },
                                                label = { Text("System", style = MaterialTheme.typography.labelMedium) }
                                            )
                                            SegmentedButton(
                                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                                onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                                                selected = themeMode == ThemeMode.LIGHT,
                                                icon = { Icon(Icons.Outlined.LightMode, contentDescription = null) },
                                                label = { Text("Light", style = MaterialTheme.typography.labelMedium) }
                                            )
                                            SegmentedButton(
                                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                                onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                                                selected = themeMode == ThemeMode.DARK,
                                                icon = { Icon(Icons.Outlined.DarkMode, contentDescription = null) },
                                                label = { Text("Dark", style = MaterialTheme.typography.labelMedium) }
                                            )
                                        }
                                    }
                                    
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    ListItem(
                                        leadingContent = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                                        headlineContent = { Text("Material You") },
                                        supportingContent = { Text("Use dynamic system colors") },
                                        trailingContent = {
                                            Switch(
                                                checked = useMaterialYou,
                                                onCheckedChange = { viewModel.setUseMaterialYou(it) },
                                                thumbContent = {
                                                    Icon(
                                                        imageVector = if (useMaterialYou) Icons.Outlined.Check else Icons.Outlined.Close,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                                    )
                                                }
                                            )
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )

                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    ListItem(
                                        leadingContent = { Icon(Icons.Outlined.Contrast, contentDescription = null) },
                                        headlineContent = { Text("AMOLED Black") },
                                        supportingContent = { Text("Use pitch black background in dark mode") },
                                        trailingContent = {
                                            Switch(
                                                checked = useAmoledBlack,
                                                onCheckedChange = { viewModel.setUseAmoledBlack(it) },
                                                enabled = isCurrentlyDark,
                                                thumbContent = {
                                                    Icon(
                                                        imageVector = if (useAmoledBlack) Icons.Outlined.Check else Icons.Outlined.Close,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                                    )
                                                }
                                            )
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                }

                                SettingsGroup(title = "Media Playback") {
                                    ListItem(
                                        leadingContent = { Icon(Icons.Outlined.PlayCircleOutline, contentDescription = null) },
                                        headlineContent = { Text("Auto-Play Media") },
                                        supportingContent = { Text("Play GIFs and videos in grid") },
                                        trailingContent = {
                                            Switch(
                                                checked = gridAutoPlay,
                                                onCheckedChange = { galleryViewModel.toggleGridAutoPlay() },
                                                thumbContent = {
                                                    Icon(
                                                        imageVector = if (gridAutoPlay) Icons.Outlined.Check else Icons.Outlined.Close,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                                    )
                                                }
                                            )
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )

                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    val autoplayWithSound by viewModel.autoplayWithSoundEnabled.collectAsState()
                                    ListItem(
                                        leadingContent = { Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = null) },
                                        headlineContent = { Text("Autoplay Video with Sound") },
                                        supportingContent = { Text("Play videos with sound automatically in full screen (muted by default)") },
                                        trailingContent = {
                                            Switch(
                                                checked = autoplayWithSound,
                                                onCheckedChange = { viewModel.setAutoplayWithSoundEnabled(it) },
                                                thumbContent = {
                                                    Icon(
                                                        imageVector = if (autoplayWithSound) Icons.Outlined.Check else Icons.Outlined.Close,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                                    )
                                                }
                                            )
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                }
                            }
                            "Layout & Navigation" -> {
                                SettingsGroup(title = "Layout & Navigation") {
                                    ListItem(
                                        leadingContent = { Icon(Icons.Outlined.Menu, contentDescription = null) },
                                        headlineContent = { Text("Full-Width Dock") },
                                        supportingContent = { Text("Use standard edge-to-edge dock instead of floating pill") },
                                        trailingContent = {
                                            val isDockFullWidth = dockStyle == com.inferno.gallery.data.DockStyle.FULL_WIDTH
                                            Switch(
                                                checked = isDockFullWidth, 
                                                onCheckedChange = { isChecked ->
                                                    viewModel.setDockStyle(if (isChecked) com.inferno.gallery.data.DockStyle.FULL_WIDTH else com.inferno.gallery.data.DockStyle.PILL)
                                                },
                                                thumbContent = {
                                                    Icon(
                                                        imageVector = if (isDockFullWidth) Icons.Outlined.Check else Icons.Outlined.Close,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                                    )
                                                }
                                            )
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )

                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    ListItem(
                                        leadingContent = { Icon(Icons.Outlined.Visibility, contentDescription = null) },
                                        headlineContent = { Text("Show Hidden Albums") },
                                        supportingContent = { Text("Show albums that start with a dot (e.g., .nomedia folders)") },
                                        trailingContent = {
                                            Switch(
                                                checked = showHiddenAlbums, 
                                                onCheckedChange = { isChecked ->
                                                    viewModel.setShowHiddenAlbums(isChecked)
                                                },
                                                thumbContent = {
                                                    Icon(
                                                        imageVector = if (showHiddenAlbums) Icons.Outlined.Check else Icons.Outlined.Close,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                                    )
                                                }
                                            )
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )

                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text(text = "Grid Items per Row: $gridCellsCount", style = MaterialTheme.typography.bodyLarge)
                                        }
                                        androidx.compose.material3.Slider(
                                            value = gridCellsCount.toFloat(),
                                            onValueChange = { galleryViewModel.setGridCellsCount(it.toInt()) },
                                            valueRange = 2f..6f,
                                            steps = 3
                                        )
                                    }

                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = "Thumbnail Corner Radius", style = MaterialTheme.typography.bodyLarge)
                                            Surface(
                                                modifier = Modifier.size(48.dp),
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(thumbnailCornerRadius.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer
                                            ) {}
                                        }
                                        androidx.compose.material3.Slider(
                                            value = thumbnailCornerRadius,
                                            onValueChange = { viewModel.setThumbnailCornerRadius(it) },
                                            valueRange = 0f..24f
                                        )
                                    }
                                }
                            }
                            "Excluded Folders" -> {
                                val allFolders by galleryViewModel.allBucketNames.collectAsState()
                                val excluded by galleryViewModel.excludedFolders.collectAsState()
                                
                                SettingsGroup(title = "Excluded Folders") {
                                    ListItem(
                                        headlineContent = { 
                                            Text(
                                                "Excluded folders won't appear in the Photos tab or Albums grid. You can still access them by searching.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    )
                                }
                                
                                if (allFolders.isEmpty()) {
                                    SettingsGroup(title = "") {
                                        ListItem(
                                            headlineContent = { Text("No folders found") },
                                            supportingContent = { Text("Media folders will appear here once scanned") }
                                        )
                                    }
                                } else {
                                    SettingsGroup(title = "${excluded.size} folder${if (excluded.size != 1) "s" else ""} excluded") {
                                        allFolders.forEach { folderName ->
                                            val isExcluded = excluded.contains(folderName)
                                            ListItem(
                                                headlineContent = { Text(folderName) },
                                                trailingContent = {
                                                    androidx.compose.material3.Switch(
                                                        checked = isExcluded,
                                                        onCheckedChange = { galleryViewModel.toggleExcludedFolder(folderName) },
                                                        thumbContent = {
                                                            Icon(
                                                                imageVector = if (isExcluded) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    )
                                                },
                                                leadingContent = {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Folder,
                                                        contentDescription = null,
                                                        tint = if (isExcluded) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                               else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                },
                                                modifier = Modifier.then(
                                                    if (isExcluded) Modifier.alpha(0.6f) else Modifier
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            "Cloud Backup" -> {
                                SettingsGroup(title = "Telegram Cloud Backup") {
                                    val testResult by viewModel.connectionTestState.collectAsState()
                                    val detectedChatsResult by viewModel.detectedChatsState.collectAsState()

                                    LaunchedEffect(testResult) {
                                        val result = testResult
                                        if (result is ConnectionTestResult.Migrated) {
                                            localChatId = result.newChatId
                                        } else if (result is ConnectionTestResult.AutoCorrected) {
                                            localChatId = result.correctedChatId
                                        }
                                    }

                                    // Token format validation
                                    val tokenRegex = remember { Regex("^\\d+:[A-Za-z0-9_-]+$") }
                                    val chatIdRegex = remember { Regex("^-?\\d+$") }
                                    val isTokenValid = primaryTokenInput.isNotBlank() && tokenRegex.matches(primaryTokenInput.trim())
                                    val isChatIdValid = localChatId.isNotBlank() && chatIdRegex.matches(localChatId.trim())
                                    val isConfigured = savedTokens.isNotEmpty() && savedTokens.first().isNotBlank() && savedChatId.isNotBlank()

                                    var wizardStep by remember { mutableStateOf(if (isConfigured) -1 else 0) } // -1 = summary view
                                    var showSecondaryToken by remember { mutableStateOf(secondaryTokenInput.isNotBlank()) }

                                    if (wizardStep == -1 && isConfigured) {
                                        // ── Compact Summary View ──
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            shape = MaterialTheme.shapes.large,
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Outlined.Check,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier
                                                                .size(24.dp)
                                                                .background(
                                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                                    shape = androidx.compose.foundation.shape.CircleShape
                                                                )
                                                                .padding(4.dp)
                                                        )
                                                        Text(
                                                            "Connected",
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                    FilledTonalButton(
                                                        onClick = { wizardStep = 0 },
                                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                                        modifier = Modifier.height(32.dp)
                                                    ) {
                                                        Text("Edit", style = MaterialTheme.typography.labelMedium)
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    "Bot: ...${savedTokens.firstOrNull()?.takeLast(8) ?: ""}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    "Chat ID: $savedChatId",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (savedTokens.size > 1 && savedTokens[1].isNotBlank()) {
                                                    Text(
                                                        "Dual-bot: enabled",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        // ── Wizard View ──
                                        val actualStep = if (wizardStep == -1) 0 else wizardStep

                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            shape = MaterialTheme.shapes.large,
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(20.dp),
                                                verticalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                // Step indicator
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    for (i in 0..2) {
                                                        val isActive = i == actualStep
                                                        val isCompleted = i < actualStep
                                                        Surface(
                                                            shape = androidx.compose.foundation.shape.CircleShape,
                                                            color = when {
                                                                isActive -> MaterialTheme.colorScheme.primary
                                                                isCompleted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                                                else -> MaterialTheme.colorScheme.outlineVariant
                                                            },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                if (isCompleted) {
                                                                    Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                                } else {
                                                                    Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                                                }
                                                            }
                                                        }
                                                        if (i < 2) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .width(40.dp)
                                                                    .height(2.dp)
                                                                    .background(if (i < actualStep) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant)
                                                            )
                                                        }
                                                    }
                                                }

                                                when (actualStep) {
                                                    // ═══════════════ STEP 1: Bot Token ═══════════════
                                                    0 -> {
                                                        Text(
                                                            "Bot Token",
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            "Create a bot via @BotFather on Telegram, then paste the token here.",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )

                                                        OutlinedTextField(
                                                            value = primaryTokenInput,
                                                            onValueChange = {
                                                                primaryTokenInput = it
                                                                viewModel.clearConnectionTestResult()
                                                            },
                                                            label = { Text("Primary Bot Token") },
                                                            placeholder = { Text("123456789:AABb...xyz") },
                                                            singleLine = true,
                                                            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                                            trailingIcon = {
                                                                Row {
                                                                    if (primaryTokenInput.isNotBlank()) {
                                                                        Icon(
                                                                            if (isTokenValid) Icons.Outlined.Check else Icons.Outlined.Close,
                                                                            contentDescription = null,
                                                                            tint = if (isTokenValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                                                            modifier = Modifier.size(18.dp)
                                                                        )
                                                                        Spacer(Modifier.width(4.dp))
                                                                    }
                                                                    val image = if (passwordVisiblePrimary) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                                                    IconButton(onClick = { passwordVisiblePrimary = !passwordVisiblePrimary }) {
                                                                        Icon(imageVector = image, contentDescription = null)
                                                                    }
                                                                }
                                                            },
                                                            isError = primaryTokenInput.isNotBlank() && !isTokenValid,
                                                            supportingText = if (primaryTokenInput.isNotBlank() && !isTokenValid) {
                                                                { Text("Token format: numbers:letters (e.g. 123456:ABCdef...)") }
                                                            } else null,
                                                            visualTransformation = if (passwordVisiblePrimary) VisualTransformation.None else PasswordVisualTransformation(),
                                                            modifier = Modifier.fillMaxWidth()
                                                        )

                                                        // Optional secondary token
                                                        Column {
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .clickable { showSecondaryToken = !showSecondaryToken }
                                                                    .padding(vertical = 4.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Icon(
                                                                    if (showSecondaryToken) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(20.dp),
                                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                                Spacer(Modifier.width(8.dp))
                                                                Text(
                                                                    "Add secondary bot for 2× upload speed (optional)",
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }

                                                            androidx.compose.animation.AnimatedVisibility(visible = showSecondaryToken) {
                                                                OutlinedTextField(
                                                                    value = secondaryTokenInput,
                                                                    onValueChange = {
                                                                        secondaryTokenInput = it
                                                                        viewModel.clearConnectionTestResult()
                                                                    },
                                                                    label = { Text("Secondary Bot Token") },
                                                                    placeholder = { Text("654321:XYZ-UVW...") },
                                                                    singleLine = true,
                                                                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                                                    trailingIcon = {
                                                                        val image = if (passwordVisibleSecondary) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                                                        IconButton(onClick = { passwordVisibleSecondary = !passwordVisibleSecondary }) {
                                                                            Icon(imageVector = image, contentDescription = null)
                                                                        }
                                                                    },
                                                                    visualTransformation = if (passwordVisibleSecondary) VisualTransformation.None else PasswordVisualTransformation(),
                                                                    modifier = Modifier.fillMaxWidth()
                                                                )
                                                            }
                                                        }

                                                        // Navigation
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                                                        ) {
                                                            if (isConfigured) {
                                                                TextButton(onClick = { wizardStep = -1 }) {
                                                                    Text("Cancel")
                                                                }
                                                            }
                                                            Button(
                                                                onClick = { wizardStep = 1 },
                                                                enabled = isTokenValid
                                                            ) {
                                                                Text("Next")
                                                                Spacer(Modifier.width(4.dp))
                                                                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                                            }
                                                        }
                                                    }

                                                    // ═══════════════ STEP 2: Chat ID ═══════════════
                                                    1 -> {
                                                        Text(
                                                            "Chat ID",
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            "Add the bot as admin to your channel/group, then auto-detect or enter the ID manually.",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )

                                                        // Auto-detect button
                                                        OutlinedButton(
                                                            onClick = {
                                                                viewModel.detectChatIds(primaryTokenInput.trim())
                                                            },
                                                            modifier = Modifier.fillMaxWidth(),
                                                            enabled = detectedChatsResult !is DetectedChatsResult.Loading
                                                        ) {
                                                            Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                                            Spacer(Modifier.width(8.dp))
                                                            Text("Auto-detect Chat ID")
                                                        }

                                                        // Detection results
                                                        when (val detResult = detectedChatsResult) {
                                                            is DetectedChatsResult.Loading -> {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                                ) {
                                                                    com.inferno.gallery.ui.components.WavyProgressIndicator(
                                                                        modifier = Modifier.size(width = 28.dp, height = 16.dp),
                                                                        strokeWidth = 2.dp,
                                                                        amplitude = 3.dp,
                                                                        frequency = 1.5f
                                                                    )
                                                                    Text("Scanning for chats…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                }
                                                            }
                                                            is DetectedChatsResult.Success -> {
                                                                Column(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .background(
                                                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                                                            shape = MaterialTheme.shapes.medium
                                                                        )
                                                                        .padding(12.dp),
                                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                                ) {
                                                                    Text("Found ${detResult.chats.size} chat(s):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                                                    detResult.chats.forEach { chat ->
                                                                        val isSelected = localChatId == chat.id.toString()
                                                                        Surface(
                                                                            onClick = {
                                                                                localChatId = chat.id.toString()
                                                                                viewModel.clearConnectionTestResult()
                                                                            },
                                                                            shape = MaterialTheme.shapes.small,
                                                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                                                            modifier = Modifier.fillMaxWidth()
                                                                        ) {
                                                                            Row(
                                                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                                                verticalAlignment = Alignment.CenterVertically,
                                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                                            ) {
                                                                                RadioButton(
                                                                                    selected = isSelected,
                                                                                    onClick = {
                                                                                        localChatId = chat.id.toString()
                                                                                        viewModel.clearConnectionTestResult()
                                                                                    },
                                                                                    modifier = Modifier.size(20.dp)
                                                                                )
                                                                                Column {
                                                                                    Text(
                                                                                        chat.title,
                                                                                        style = MaterialTheme.typography.bodyMedium,
                                                                                        fontWeight = FontWeight.Medium
                                                                                    )
                                                                                    Text(
                                                                                        "${chat.id} · ${chat.type}",
                                                                                        style = MaterialTheme.typography.bodySmall,
                                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                                    )
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                            is DetectedChatsResult.Empty -> {
                                                                Surface(
                                                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                                                    shape = MaterialTheme.shapes.small
                                                                ) {
                                                                    Text(
                                                                        "No chats found. Make sure you've added the bot to your channel/group as admin, or sent /start to the bot.",
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                        modifier = Modifier.padding(12.dp)
                                                                    )
                                                                }
                                                            }
                                                            is DetectedChatsResult.Error -> {
                                                                Text(
                                                                    "Detection failed: ${detResult.message}",
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.error
                                                                )
                                                            }
                                                            null -> {} // Initial state
                                                        }

                                                        // Manual entry divider
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                        ) {
                                                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                                                            Text("or enter manually", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                                                        }

                                                        OutlinedTextField(
                                                            value = localChatId,
                                                            onValueChange = {
                                                                localChatId = it
                                                                viewModel.clearConnectionTestResult()
                                                            },
                                                            label = { Text("Channel / Chat ID") },
                                                            placeholder = { Text("-1001234567890") },
                                                            singleLine = true,
                                                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                                            trailingIcon = {
                                                                if (localChatId.isNotBlank()) {
                                                                    Icon(
                                                                        if (isChatIdValid) Icons.Outlined.Check else Icons.Outlined.Close,
                                                                        contentDescription = null,
                                                                        tint = if (isChatIdValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                                                        modifier = Modifier.size(18.dp)
                                                                    )
                                                                }
                                                            },
                                                            isError = localChatId.isNotBlank() && !isChatIdValid,
                                                            supportingText = if (localChatId.isNotBlank() && !isChatIdValid) {
                                                                { Text("Must be a number (e.g. -1001234567890)") }
                                                            } else null,
                                                            modifier = Modifier.fillMaxWidth()
                                                        )

                                                        // Navigation
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            TextButton(onClick = { wizardStep = 0 }) {
                                                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                                                Spacer(Modifier.width(4.dp))
                                                                Text("Back")
                                                            }
                                                            Button(
                                                                onClick = { wizardStep = 2 },
                                                                enabled = isChatIdValid
                                                            ) {
                                                                Text("Next")
                                                                Spacer(Modifier.width(4.dp))
                                                                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                                            }
                                                        }
                                                    }

                                                    // ═══════════════ STEP 3: Test & Save ═══════════════
                                                    2 -> {
                                                        Text(
                                                            "Test & Save",
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold
                                                        )

                                                        // Summary
                                                        Surface(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            shape = MaterialTheme.shapes.medium,
                                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                                        ) {
                                                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                    Text("Token:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                    Text("...${primaryTokenInput.trim().takeLast(10)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                                                }
                                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                    Text("Chat ID:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                    Text(localChatId.trim(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                                                }
                                                                if (secondaryTokenInput.isNotBlank()) {
                                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                        Text("Dual-bot:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                        Text("Enabled", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        // Test button
                                                        FilledTonalButton(
                                                            onClick = {
                                                                viewModel.testTelegramConnection(primaryTokenInput.trim(), localChatId.trim())
                                                            },
                                                            enabled = testResult != ConnectionTestResult.Testing,
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Icon(Icons.Outlined.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
                                                            Spacer(Modifier.width(8.dp))
                                                            Text("Test Connection")
                                                        }

                                                        // Test result display
                                                        if (testResult != null) {
                                                            when (val res = testResult) {
                                                                ConnectionTestResult.Testing -> {
                                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                        com.inferno.gallery.ui.components.WavyProgressIndicator(
                                                                            modifier = Modifier.size(width = 32.dp, height = 20.dp),
                                                                            strokeWidth = 2.dp, amplitude = 3.dp, frequency = 1.5f
                                                                        )
                                                                        Text("Testing connection…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                    }
                                                                }
                                                                ConnectionTestResult.Success, is ConnectionTestResult.AutoCorrected, is ConnectionTestResult.Migrated -> {
                                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                        Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                                                        Column {
                                                                            Text("Connected!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                                            if (res is ConnectionTestResult.AutoCorrected) {
                                                                                Text("Chat ID corrected to ${res.correctedChatId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                            } else if (res is ConnectionTestResult.Migrated) {
                                                                                Text("Chat ID updated to ${res.newChatId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                                is ConnectionTestResult.Error -> {
                                                                    Column {
                                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                            Icon(Icons.Outlined.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                                                            Text("Failed: ${res.message}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                                                        }
                                                                        val hint = when {
                                                                            res.message.contains("chat not found", ignoreCase = true) ->
                                                                                "💡 For groups/channels, the Chat ID must start with \"-100\" followed by the raw ID (e.g. -1001234567890). Try using Auto-detect on the previous step."
                                                                            res.message.contains("bot can't send messages", ignoreCase = true) || res.message.contains("bot was blocked", ignoreCase = true) ->
                                                                                "💡 Open Telegram, find your bot and tap /start. Bots can't message you until you start a conversation first."
                                                                            res.message.contains("Forbidden", ignoreCase = true) && res.message.contains("bot", ignoreCase = true) ->
                                                                                "💡 The bot doesn't have permission to send messages. Add the bot as admin to your group/channel."
                                                                            res.message.contains("Unauthorized", ignoreCase = true) ->
                                                                                "💡 The bot token is invalid. Go back and check the token from @BotFather."
                                                                            res.message.contains("Too Many Requests", ignoreCase = true) ->
                                                                                "💡 Rate limited by Telegram. Wait a minute and try again."
                                                                            else -> null
                                                                        }
                                                                        if (hint != null) {
                                                                            Spacer(Modifier.height(6.dp))
                                                                            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                        }
                                                                    }
                                                                }
                                                                else -> {}
                                                            }
                                                        }

                                                        // Navigation
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            TextButton(onClick = { wizardStep = 1 }) {
                                                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                                                Spacer(Modifier.width(4.dp))
                                                                Text("Back")
                                                            }
                                                            val isTestSuccess = testResult is ConnectionTestResult.Success || 
                                                                               testResult is ConnectionTestResult.AutoCorrected || 
                                                                               testResult is ConnectionTestResult.Migrated
                                                            Button(
                                                                onClick = {
                                                                    val tokens = listOfNotNull(
                                                                        primaryTokenInput.trim().takeIf { it.isNotEmpty() },
                                                                        secondaryTokenInput.trim().takeIf { it.isNotEmpty() }
                                                                    )
                                                                    viewModel.saveTelegramCredentials(tokens, localChatId.trim())
                                                                    viewModel.clearConnectionTestResult()
                                                                    viewModel.clearDetectedChats()
                                                                    wizardStep = -1
                                                                },
                                                                enabled = isTokenValid && isChatIdValid
                                                            ) {
                                                                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                                Spacer(Modifier.width(4.dp))
                                                                Text("Save")
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    val localContext = androidx.compose.ui.platform.LocalContext.current
                                    ListItem(
                                        leadingContent = { Icon(Icons.Outlined.Cloud, contentDescription = null) },
                                        headlineContent = { Text("Restore from Cloud") },
                                        supportingContent = { Text("Fetch sync manifest and restore database entries") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.restoreFromManifest(localContext)
                                            },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )

                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    ListItem(
                                        leadingContent = { Icon(Icons.Outlined.Cloud, contentDescription = null) },
                                        headlineContent = { Text("Automated Background Backup") },
                                        supportingContent = { Text("Backup new media in background based on criteria") },
                                        trailingContent = {
                                            Switch(
                                                checked = backupEnabled,
                                                onCheckedChange = { viewModel.setTelegramBackupEnabled(it) },
                                                thumbContent = {
                                                    Icon(
                                                        imageVector = if (backupEnabled) Icons.Outlined.Check else Icons.Outlined.Close,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                                    )
                                                }
                                            )
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                }

                                SettingsGroup(title = "Background Sync Settings") {
                                    val wifiOnly by viewModel.telegramAutoBackupWifiOnly.collectAsState()
                                    val batteryPause by viewModel.telegramAutoBackupBatteryLowPause.collectAsState()

                                    ListItem(
                                        leadingContent = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                                        headlineContent = { Text("Choose Backup Folders") },
                                        supportingContent = {
                                            Text(
                                                if (selectedFolders.isEmpty()) "No folders selected (Nothing will backup)"
                                                else selectedFolders.joinToString(", ")
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showFolderDialog.value = true },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )

                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    ListItem(
                                        leadingContent = { Icon(Icons.Outlined.Wifi, contentDescription = null) },
                                        headlineContent = { Text("Wi-Fi Only") },
                                        supportingContent = { Text("Backup only when connected to an unmetered Wi-Fi network") },
                                        trailingContent = {
                                            Switch(
                                                checked = wifiOnly,
                                                onCheckedChange = { viewModel.setTelegramAutoBackupWifiOnly(it) },
                                                thumbContent = {
                                                    Icon(
                                                        imageVector = if (wifiOnly) Icons.Outlined.Check else Icons.Outlined.Close,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                                    )
                                                }
                                            )
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )

                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    ListItem(
                                        leadingContent = { Icon(Icons.Outlined.BatteryChargingFull, contentDescription = null) },
                                        headlineContent = { Text("Battery Saver Pause") },
                                        supportingContent = { Text("Pause backup if battery is less than 35% (unless charging)") },
                                        trailingContent = {
                                            Switch(
                                                checked = batteryPause,
                                                onCheckedChange = { viewModel.setTelegramAutoBackupBatteryLowPause(it) },
                                                thumbContent = {
                                                    Icon(
                                                        imageVector = if (batteryPause) Icons.Outlined.Check else Icons.Outlined.Close,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                                    )
                                                }
                                            )
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                }
                            }
                            "Smart Search & OCR" -> {
                                SettingsGroup(title = "Local Text Search (OCR)") {
                                    val dbIndexed = totalImagesCount - unindexedOcrImagesCount
                                    val isRunning = ocrProgressState.isIndexing
                                    val indexed = if (isRunning) ocrProgressState.progress else dbIndexed
                                    val total = if (isRunning) ocrProgressState.total else totalImagesCount

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Search,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = "Text (OCR) Indexing",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            
                                            val badgeColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                            val badgeTextColor = if (isRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            val badgeText = if (isRunning) "Indexing" else "Idle"
                                            
                                            Surface(
                                                color = badgeColor,
                                                contentColor = badgeTextColor,
                                                shape = MaterialTheme.shapes.extraSmall,
                                                modifier = Modifier.padding(start = 8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    if (isRunning) {
                                                        com.inferno.gallery.ui.components.WavyProgressIndicator(
                                                            modifier = Modifier.size(width = 16.dp, height = 10.dp),
                                                            strokeWidth = 1.5.dp,
                                                            amplitude = 2.dp,
                                                            frequency = 1.2f,
                                                            color = badgeTextColor
                                                        )
                                                    }
                                                    Text(
                                                        text = badgeText,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = "Index photos locally to search for text printed on them.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        if (total > 0) {
                                            val progressFloat = (indexed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(10.dp)
                                                        .background(
                                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                                            shape = CircleShape
                                                        )
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth(progressFloat)
                                                            .fillMaxHeight()
                                                            .background(
                                                                color = MaterialTheme.colorScheme.primary,
                                                                shape = CircleShape
                                                            )
                                                    )
                                                }
                                                
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    val currentImageName = ocrProgressState.currentImageName
                                                    Text(
                                                        text = if (isRunning && !currentImageName.isNullOrBlank()) "Scanning: $currentImageName" 
                                                               else if (indexed == total) "Indexing complete" 
                                                               else "Progress",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.weight(1f),
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "$indexed / $total images (${(progressFloat * 100).toInt()}%)",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (isRunning) {
                                                Button(
                                                    onClick = { viewModel.stopOcrIndexing() },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                                    ),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("Stop Indexing")
                                                }
                                            } else {
                                                FilledTonalButton(
                                                    onClick = { viewModel.startOcrIndexing() },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("Start Indexing")
                                                }
                                                androidx.compose.material3.OutlinedButton(
                                                    onClick = { viewModel.rebuildOcrIndex() },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("Rebuild Index")
                                                }
                                            }
                                        }
                                    }
                                }

                                SettingsGroup(title = "Local Semantic Search (AI)") {
                                    val isDownloading = modelDownloadWorkInfo?.state == WorkInfo.State.RUNNING || modelDownloadWorkInfo?.state == WorkInfo.State.ENQUEUED
                                    val downloadProgress = modelDownloadWorkInfo?.progress?.getInt("progress", 0) ?: 0
                                    val isIndexing = clipProgressState.isIndexing
                                    val unindexedCount = unindexedSmartSearchCount
                                    val indexedCount = maxOf(0, totalImagesCount - unindexedCount)
                                    val displayIndexed = if (isIndexing) clipProgressState.progress else indexedCount
                                    val displayTotal = if (isIndexing) clipProgressState.total else totalImagesCount
                                    val smartCurrentImageName = clipProgressState.currentImageName

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.AutoAwesome,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = "Smart Search (Semantic)",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            
                                            val badgeColor = if (isIndexing) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else if (isDownloading) {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            } else if (smartSearchModelDownloaded) {
                                                MaterialTheme.colorScheme.tertiaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            }
                                            
                                            val badgeTextColor = if (isIndexing) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else if (isDownloading) {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            } else if (smartSearchModelDownloaded) {
                                                MaterialTheme.colorScheme.onTertiaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                            
                                            val badgeText = if (isIndexing) {
                                                "Indexing"
                                            } else if (isDownloading) {
                                                "Downloading"
                                            } else if (smartSearchModelDownloaded) {
                                                "Ready"
                                            } else {
                                                "No Model"
                                            }

                                            Surface(
                                                color = badgeColor,
                                                contentColor = badgeTextColor,
                                                shape = MaterialTheme.shapes.extraSmall,
                                                modifier = Modifier.padding(start = 8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    if (isDownloading || isIndexing) {
                                                        com.inferno.gallery.ui.components.WavyProgressIndicator(
                                                            modifier = Modifier.size(width = 16.dp, height = 10.dp),
                                                            strokeWidth = 1.5.dp,
                                                            amplitude = 2.dp,
                                                            frequency = 1.2f,
                                                            color = badgeTextColor
                                                        )
                                                    }
                                                    Text(
                                                        text = badgeText,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = "Search inside your photos locally by semantic concepts (e.g. 'sunset', 'cat in grass') using AI vector embeddings.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        if (!smartSearchModelDownloaded) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                                        shape = MaterialTheme.shapes.large
                                                    )
                                                    .padding(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "AI Search model files must be downloaded first (~100MB). Downloads run in background and can be paused.",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                                
                                                if (isDownloading) {
                                                    LinearProgressIndicator(
                                                        progress = { downloadProgress / 100f },
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                    Text(
                                                        text = "Downloading model: $downloadProgress%",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                } else {
                                                    Button(
                                                        onClick = { viewModel.startModelDownload() },
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text("Download AI Model Files")
                                                    }
                                                }
                                            }
                                        } else {
                                            ListItem(
                                                leadingContent = { Icon(Icons.Outlined.Cloud, contentDescription = null) },
                                                headlineContent = { Text("Auto Index New Images") },
                                                supportingContent = { Text("Automatically generate embeddings for new photos added to gallery") },
                                                trailingContent = {
                                                    Switch(
                                                        checked = smartSearchAutoIndex,
                                                        onCheckedChange = { viewModel.setSmartSearchAutoIndex(it) },
                                                        thumbContent = {
                                                            Icon(
                                                                imageVector = if (smartSearchAutoIndex) Icons.Outlined.Check else Icons.Outlined.Close,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(SwitchDefaults.IconSize)
                                                            )
                                                        }
                                                    )
                                                },
                                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                                modifier = Modifier.padding(horizontal = 0.dp)
                                            )

                                            val thresholdString = when {
                                                smartSearchThreshold < 0.16f -> "Relaxed (Shows more results, matches weaker concepts)"
                                                smartSearchThreshold > 0.25f -> "Strict (Shows fewer results, matches exact concepts only)"
                                                else -> "Balanced (Recommended, default)"
                                            }
                                            
                                            Column(
                                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Search Strictness",
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Text(
                                                        text = String.format(java.util.Locale.US, "%.2f", smartSearchThreshold),
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                Text(
                                                    text = thresholdString,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Slider(
                                                    value = smartSearchThreshold,
                                                    onValueChange = { viewModel.setSmartSearchThreshold(it) },
                                                    valueRange = 0.1f..0.35f
                                                )
                                            }

                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 8.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                            )

                                            // Smart Search Indexing Progress
                                            val isIndexing = clipProgressState.isIndexing
                                            val unindexedCount = unindexedSmartSearchCount
                                            val indexedCount = maxOf(0, totalImagesCount - unindexedCount)
                                            val displayIndexed = if (isIndexing) clipProgressState.progress else indexedCount
                                            val displayTotal = if (isIndexing) clipProgressState.total else totalImagesCount
                                            val smartCurrentImageName = clipProgressState.currentImageName

                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "AI Embedding Generation",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    
                                                    val badgeColor = if (isIndexing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                                    val badgeTextColor = if (isIndexing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                    val badgeText = if (isIndexing) "Indexing" else "Idle"
                                                    
                                                    Surface(
                                                        color = badgeColor,
                                                        contentColor = badgeTextColor,
                                                        shape = MaterialTheme.shapes.extraSmall
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            if (isIndexing) {
                                                                com.inferno.gallery.ui.components.WavyProgressIndicator(
                                                                    modifier = Modifier.size(width = 16.dp, height = 10.dp),
                                                                    strokeWidth = 1.5.dp,
                                                                    amplitude = 2.dp,
                                                                    frequency = 1.2f,
                                                                    color = badgeTextColor
                                                                )
                                                            }
                                                            Text(
                                                                text = badgeText,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }

                                                if (displayTotal > 0) {
                                                    val progressFloat = (displayIndexed.toFloat() / displayTotal.toFloat()).coerceIn(0f, 1f)
                                                    Column(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(10.dp)
                                                                .background(
                                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                                    shape = CircleShape
                                                                )
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth(progressFloat)
                                                                    .fillMaxHeight()
                                                                    .background(
                                                                        color = MaterialTheme.colorScheme.primary,
                                                                        shape = CircleShape
                                                                    )
                                                            )
                                                        }
                                                        
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = if (isIndexing && !smartCurrentImageName.isNullOrBlank()) "Scanning: $smartCurrentImageName" 
                                                                       else if (displayIndexed == displayTotal) "AI indexing complete" 
                                                                       else "Progress",
                                                                style = MaterialTheme.typography.labelMedium,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.weight(1f),
                                                                maxLines = 1,
                                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = "$displayIndexed / $displayTotal images (${(progressFloat * 100).toInt()}%)",
                                                                style = MaterialTheme.typography.labelMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                    }
                                                }

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    if (isIndexing) {
                                                        Button(
                                                            onClick = { viewModel.stopSmartSearchIndexing() },
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                                                            ),
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Text("Stop Indexing")
                                                        }
                                                    } else {
                                                        FilledTonalButton(
                                                            onClick = { viewModel.startSmartSearchIndexing() },
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Text("Start Indexing")
                                                        }
                                                        androidx.compose.material3.OutlinedButton(
                                                            onClick = { showClearIndexConfirm = true },
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Text("Clear Index")
                                                        }
                                                    }
                                                }
                                                if (!isIndexing) {
                                                    androidx.compose.material3.OutlinedButton(
                                                        onClick = { showDeleteModelConfirm = true },
                                                        colors = ButtonDefaults.outlinedButtonColors(
                                                            contentColor = MaterialTheme.colorScheme.error
                                                        ),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text("Delete AI Model Files")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            "About" -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // 1. Placeholder app icon in the top center
                                    Card(
                                        shape = ShapeLargeIncreased3 as RoundedCornerShape,
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        modifier = Modifier.size(96.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Image,
                                                contentDescription = null,
                                                modifier = Modifier.size(56.dp)
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    // 2. App Name below it
                                    Text(
                                        text = "Photon Gallery",
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "open source android media gallery app .",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                                    )
                                    
                                    Spacer(modifier = Modifier.height(24.dp))
                                    
                                    // 3. Card below them (containing dev name and github icon next to the name)
                                    Card(
                                        shape = ShapeLarge as RoundedCornerShape,
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .clickable { uriHandler.openUri("https://github.com/Bn5prS") }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = GithubIcon,
                                                contentDescription = "GitHub",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = "Bn5prS",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(24.dp))
                                    
                                    // 4. Options below the card
                                    val context = androidx.compose.ui.platform.LocalContext.current
                                    SettingsGroup(title = "App Details") {
                                        // Check for updates
                                        ListItem(
                                            leadingContent = { Icon(Icons.Outlined.Update, contentDescription = null) },
                                            headlineContent = { Text("Check for updates") },
                                            supportingContent = { Text("Verify if you are running the latest release") },
                                            modifier = Modifier.clickable {
                                                android.widget.Toast.makeText(context, "Photon Gallery is up to date (v1.0.0)", android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                        )
                                        
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        )
                                        
                                        // Version number
                                        ListItem(
                                            leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                                            headlineContent = { Text("Version") },
                                            supportingContent = { Text("v2.0.0 (Material 3 Expressive)") },
                                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                        )
                                        
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        )
                                        
                                        // Licenses
                                        ListItem(
                                            leadingContent = { Icon(Icons.Outlined.Description, contentDescription = null) },
                                            headlineContent = { Text("Licenses") },
                                            supportingContent = { Text("Open source libraries and licenses") },
                                            modifier = Modifier.clickable {
                                                showLicensesDialog = true
                                            },
                                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
}

@Composable
fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            ),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = ShapeExtraLarge as RoundedCornerShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                content()
            }
        }
    }
}

// AboutCard deleted - replaced with inline details inside SettingsScreen

@Composable
fun CategoryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = if (isPressed) Spring.DampingRatioNoBouncy else 0.55f,
            stiffness = if (isPressed) 12000f else Spring.StiffnessMedium
        ),
        label = "CategoryCardScale"
    )

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ShapeExtraLarge as RoundedCornerShape,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .photonContainer(
                shape = ShapeExtraLarge as RoundedCornerShape,
                backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow,
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.large
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

val GithubIcon: ImageVector = ImageVector.Builder(
    name = "Github",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).path(
    fill = SolidColor(Color.Black),
) {
    moveTo(12f, 2f)
    curveTo(6.477f, 2f, 2f, 6.477f, 2f, 12f)
    curveTo(2f, 16.42f, 4.865f, 20.166f, 8.839f, 21.489f)
    curveTo(9.339f, 21.581f, 9.521f, 21.272f, 9.521f, 21.007f)
    curveTo(9.521f, 20.77f, 9.513f, 20.141f, 9.508f, 19.307f)
    curveTo(6.726f, 19.91f, 6.139f, 17.967f, 6.139f, 17.967f)
    curveTo(5.685f, 16.811f, 5.029f, 16.503f, 5.029f, 16.503f)
    curveTo(4.121f, 15.883f, 5.09f, 15.895f, 5.09f, 15.895f)
    curveTo(6.093f, 15.965f, 6.621f, 16.925f, 6.621f, 16.925f)
    curveTo(7.513f, 18.454f, 8.962f, 18.012f, 9.531f, 17.756f)
    curveTo(9.623f, 17.11f, 9.881f, 16.67f, 10.167f, 16.42f)
    curveTo(7.947f, 16.167f, 5.612f, 15.31f, 5.612f, 11.477f)
    curveTo(5.612f, 10.386f, 6.002f, 9.493f, 6.641f, 8.794f)
    curveTo(6.538f, 8.541f, 6.195f, 7.524f, 6.739f, 6.147f)
    curveTo(6.739f, 6.147f, 7.579f, 5.878f, 9.489f, 7.172f)
    curveTo(10.289f, 6.95f, 11.144f, 6.839f, 12f, 6.839f)
    curveTo(12.856f, 6.839f, 13.711f, 6.95f, 14.511f, 7.172f)
    curveTo(16.421f, 5.878f, 17.261f, 6.147f, 17.261f, 6.147f)
    curveTo(17.805f, 7.524f, 17.462f, 8.541f, 17.359f, 8.794f)
    curveTo(17.998f, 9.493f, 18.388f, 10.386f, 18.388f, 11.477f)
    curveTo(18.388f, 15.32f, 16.05f, 16.164f, 13.823f, 16.412f)
    curveTo(14.182f, 16.721f, 14.501f, 17.331f, 14.501f, 18.264f)
    curveTo(14.501f, 19.6f, 14.489f, 20.679f, 14.489f, 21.007f)
    curveTo(14.489f, 21.275f, 14.669f, 21.587f, 15.177f, 21.489f)
    curveTo(19.141f, 20.163f, 22f, 16.42f, 22f, 12f)
    curveTo(22f, 6.477f, 17.522f, 2f, 12f, 2f)
    close()
}.build()

@Composable
private fun SetupGuideStep(
    stepNumber: Int,
    title: String,
    instructions: List<String>,
    highlight: String?
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        "$stepNumber",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
        }

        Column(
            modifier = Modifier.padding(start = 36.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            instructions.forEach { instruction ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("•", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        instruction,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (highlight != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        highlight,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
