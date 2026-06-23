package com.inferno.gallery.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow

import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.CreateNewFolder

import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoAlbum
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LibraryAddCheck
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.inferno.gallery.data.DockStyle
import com.inferno.gallery.ui.components.overscrollStretch

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.os.Environment
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.material3.TextButton
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Button
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.LaunchedEffect
import com.inferno.gallery.ui.theme.ShapeLargeIncreased3
import com.inferno.gallery.ui.theme.ShapeExtraLarge
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.io.File
import androidx.compose.runtime.rememberCoroutineScope
import com.inferno.gallery.data.SettingsRepository

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainAppLayout(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onPhotoClick: (String, String?, String?) -> Unit,
    onCreateCollage: (List<String>) -> Unit = {},
    onCreateStitch: (List<String>) -> Unit = {},
    onNavigateToVault: () -> Unit = {},
    onNavigateToUserbotSetup: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel = viewModel()
) {
    val selectedFilter by viewModel.selectedFilterIndex.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedUris by viewModel.selectedUris.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val albumSortOrder by viewModel.albumSortOrder.collectAsState()
    val dockStyle by viewModel.dockStyle.collectAsState()
    val gridAutoPlay by viewModel.gridAutoPlay.collectAsState()
    val nestedNavController = rememberNavController()

    var showMenu by remember { mutableStateOf(false) }
    var showCreateAlbumDialog by remember { mutableStateOf(false) }
    var newAlbumName by remember { mutableStateOf("") }
    var settingsActiveSection by remember { mutableStateOf<String?>(null) }
    val navBackStackEntry by nestedNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isScrollDockVisible by viewModel.isScrollDockVisible.collectAsState()

    LaunchedEffect(currentRoute) {
        viewModel.setScrollDockVisible(true)
    }

    val albumNameArg = navBackStackEntry?.arguments?.getString("bucketName")
    val coroutineScope = rememberCoroutineScope()

    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    // Collect toast events from ViewModel
    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val onboardingCompleted by viewModel.onboardingCompleted.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    fun checkPhotosPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
             ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED)
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun checkVideosPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
             ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED)
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun checkAllFilesPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    var photosGranted by remember { mutableStateOf(checkPhotosPermission()) }
    var videosGranted by remember { mutableStateOf(checkVideosPermission()) }
    var allFilesGranted by remember { mutableStateOf(checkAllFilesPermission()) }
    var hasRequestedMediaOnce by remember { mutableStateOf(false) }

    fun updatePermissionStates() {
        photosGranted = checkPhotosPermission()
        videosGranted = checkVideosPermission()
        allFilesGranted = checkAllFilesPermission()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                updatePermissionStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val intentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        updatePermissionStates()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        updatePermissionStates()
    }

    val triggerSync = {
        val syncWorkRequest = OneTimeWorkRequestBuilder<com.inferno.gallery.workers.MediaSyncWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "MediaSyncWorker",
            androidx.work.ExistingWorkPolicy.REPLACE,
            syncWorkRequest
        )
    }

    val trashLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val selected = viewModel.selectedUris.value.toList()
            viewModel.deleteSelectedMediaFromDb(selected)
            viewModel.clearSelection()
            triggerSync()
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.clearSelection()
            triggerSync()
        }
    }

    val permanentDeleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val selected = viewModel.selectedUris.value.toList()
            viewModel.deleteSelectedMediaFromDb(selected)
            viewModel.clearSelection()
            triggerSync()
        }
    }

    val hasRequiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        photosGranted && videosGranted && allFilesGranted
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        photosGranted && allFilesGranted
    } else {
        photosGranted
    }
    if (!onboardingCompleted || !hasRequiredPermissions) {
        PermissionOnboardingScreen(
            photosGranted = photosGranted,
            videosGranted = videosGranted,
            allFilesGranted = allFilesGranted,
            onGrantMediaClick = {
                val activity = context as? android.app.Activity
                val showRationale = activity?.let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.READ_MEDIA_IMAGES) ||
                        androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.READ_MEDIA_VIDEO)
                    } else {
                        androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                } ?: true

                if (hasRequestedMediaOnce && !photosGranted && !showRationale) {
                    Toast.makeText(context, "Permissions permanently denied. Opening settings...", Toast.LENGTH_LONG).show()
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    intentLauncher.launch(intent)
                } else {
                    hasRequestedMediaOnce = true
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_MEDIA_IMAGES,
                                Manifest.permission.READ_MEDIA_VIDEO,
                                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                            )
                        )
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_MEDIA_IMAGES,
                                Manifest.permission.READ_MEDIA_VIDEO
                            )
                        )
                    } else {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                        )
                    }
                }
            },
            onGrantAllFilesClick = {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                intentLauncher.launch(intent)
            },
            onContinueClick = {
                viewModel.completeOnboarding()
                triggerSync()
            },
            modifier = modifier
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize().overscrollStretch(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .statusBarsPadding()
            ) {
                    if (isSelectionMode) {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { viewModel.clearSelection() }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear selection")
                                }
                                AnimatedContent(
                                    targetState = selectedUris.size,
                                    transitionSpec = {
                                        if (targetState > initialState) {
                                            (slideInVertically { -it } + fadeIn()) togetherWith
                                                    (slideOutVertically { it } + fadeOut())
                                        } else {
                                            (slideInVertically { it } + fadeIn()) togetherWith
                                                    (slideOutVertically { -it } + fadeOut())
                                        }
                                    },
                                    label = "selectionCount",
                                    modifier = Modifier.padding(start = 16.dp).weight(1f)
                                ) { count ->
                                    Text(
                                        "$count Selected",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                IconButton(onClick = { viewModel.toggleSelectAll() }) {
                                    Icon(
                                        imageVector = Icons.Rounded.LibraryAddCheck,
                                        contentDescription = "Select or Deselect All"
                                    )
                                }
                            }
                        }
                    } else if (currentRoute == "album/{bucketName}") {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { nestedNavController.popBackStack() }) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                                }
                                val friendlyTitle = when (albumNameArg) {
                                    "search_text" -> "Text Matches"
                                    "search_smart" -> "Semantic Matches"
                                    else -> albumNameArg ?: "Album"
                                }
                                Text(
                                    friendlyTitle,
                                    style = MaterialTheme.typography.displayMedium,
                                    modifier = Modifier.padding(start = 16.dp).weight(1f)
                                )
                            }
                        }
                    } else if (currentRoute == "settings") {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    if (settingsActiveSection != null) {
                                        settingsActiveSection = null
                                    } else {
                                        nestedNavController.popBackStack()
                                    }
                                }) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                                }
                                Text(
                                    settingsActiveSection ?: "Settings",
                                    style = MaterialTheme.typography.displayMedium,
                                    modifier = Modifier.padding(start = 16.dp).weight(1f)
                                )
                            }
                        }
                    } else {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val titleText = when (currentRoute) {
                                    "photos" -> "Photos"
                                    "albums" -> "Albums"
                                    "search" -> "Search"
                                    "cloud" -> "Cloud"
                                    else -> "Photon Gallery"
                                }
                                Text(
                                    titleText,
                                    style = MaterialTheme.typography.displayMedium
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (currentRoute == "photos") {
                                        var showPhotoSortMenu by remember { mutableStateOf(false) }
                                        Box {
                                            IconButton(onClick = { 
                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                showPhotoSortMenu = true 
                                            }) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Rounded.Sort,
                                                    contentDescription = "Sort",
                                                    tint = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = showPhotoSortMenu,
                                                onDismissRequest = { showPhotoSortMenu = false },
                                                shape = ShapeExtraLarge,
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Newest to Oldest") },
                                                    trailingIcon = {
                                                        androidx.compose.material3.RadioButton(
                                                            selected = sortOrder == SortOrder.NewToOld,
                                                            onClick = null
                                                        )
                                                    },
                                                    onClick = { viewModel.setSortOrder(SortOrder.NewToOld); showPhotoSortMenu = false }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Oldest to Newest") },
                                                    trailingIcon = {
                                                        androidx.compose.material3.RadioButton(
                                                            selected = sortOrder == SortOrder.OldToNew,
                                                            onClick = null
                                                        )
                                                    },
                                                    onClick = { viewModel.setSortOrder(SortOrder.OldToNew); showPhotoSortMenu = false }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Largest to Smallest") },
                                                    trailingIcon = {
                                                        androidx.compose.material3.RadioButton(
                                                            selected = sortOrder == SortOrder.BigToSmall,
                                                            onClick = null
                                                        )
                                                    },
                                                    onClick = { viewModel.setSortOrder(SortOrder.BigToSmall); showPhotoSortMenu = false }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Smallest to Largest") },
                                                    trailingIcon = {
                                                        androidx.compose.material3.RadioButton(
                                                            selected = sortOrder == SortOrder.SmallToBig,
                                                            onClick = null
                                                        )
                                                    },
                                                    onClick = { viewModel.setSortOrder(SortOrder.SmallToBig); showPhotoSortMenu = false }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("A to Z") },
                                                    trailingIcon = {
                                                        androidx.compose.material3.RadioButton(
                                                            selected = sortOrder == SortOrder.NameAsc,
                                                            onClick = null
                                                        )
                                                    },
                                                    onClick = { viewModel.setSortOrder(SortOrder.NameAsc); showPhotoSortMenu = false }
                                                )
                                            }
                                        }
                                    }
                                    if (currentRoute == "albums") {
                                        IconButton(onClick = { showCreateAlbumDialog = true }) {
                                            Icon(
                                                imageVector = Icons.Rounded.CreateNewFolder,
                                                contentDescription = "Create Album",
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    Box {
                                        IconButton(onClick = { showMenu = true }) {
                                            Icon(Icons.Rounded.MoreVert, contentDescription = "Menu")
                                        }
                                        DropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = { showMenu = false },
                                            shape = ShapeLargeIncreased3,
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            if (currentRoute == "photos") {
                                                DropdownMenuItem(
                                                    text = { Text("Immersive View") },
                                                    trailingIcon = {
                                                        androidx.compose.material3.RadioButton(
                                                            selected = viewMode == ViewMode.Immersive,
                                                            onClick = null
                                                        )
                                                    },
                                                    onClick = {
                                                        viewModel.setViewMode(ViewMode.Immersive)
                                                        showMenu = false
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Grouped View") },
                                                    trailingIcon = {
                                                        androidx.compose.material3.RadioButton(
                                                            selected = viewMode == ViewMode.Grouped,
                                                            onClick = null
                                                        )
                                                    },
                                                    onClick = {
                                                        viewModel.setViewMode(ViewMode.Grouped)
                                                        showMenu = false
                                                    }
                                                )
                                            }
                                            DropdownMenuItem(
                                                text = { Text("Settings") },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Settings,
                                                        contentDescription = "Settings"
                                                    )
                                                },
                                                onClick = {
                                                    nestedNavController.navigate("settings") {
                                                        popUpTo("photos") { saveState = true }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                    showMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (currentRoute == "photos" && !isSelectionMode) {
                        QuickFilterRow(
                            selectedFilter = selectedFilter,
                            onFilterSelected = { viewModel.setFilter(it) }
                        )
                }
            }
        },
        bottomBar = {
            val isDockVisible = currentRoute != "settings" && currentRoute != "duplicate_cleaner" && !isSelectionMode && (dockStyle != DockStyle.PILL || isScrollDockVisible)
            AnimatedVisibility(
                visible = isDockVisible,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                if (dockStyle == DockStyle.PILL) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 4.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        HorizontalFloatingToolbar(
                            expanded = true,
                            modifier = Modifier.fillMaxWidth(0.80f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 0.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DockItem(
                                    icon = { Icon(Icons.Rounded.Image, contentDescription = "Photos", modifier = Modifier.size(20.dp)) },
                                    label = "Photos",
                                    isSelected = currentRoute == "photos",
                                    onClick = { nestedNavController.navigate("photos") {
                                        popUpTo("photos") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    } }
                                )
                                DockItem(
                                    icon = { Icon(Icons.Rounded.PhotoAlbum, contentDescription = "Albums", modifier = Modifier.size(20.dp)) },
                                    label = "Albums",
                                    isSelected = currentRoute?.startsWith("album") == true,
                                    onClick = { nestedNavController.navigate("albums") {
                                        popUpTo("photos") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    } }
                                )
                                DockItem(
                                    icon = { Icon(Icons.Rounded.Cloud, contentDescription = "Cloud", modifier = Modifier.size(20.dp)) },
                                    label = "Cloud",
                                    isSelected = currentRoute == "cloud",
                                    onClick = { nestedNavController.navigate("cloud") {
                                        popUpTo("photos") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    } }
                                )
                                DockItem(
                                    icon = { MagicSearchIcon(modifier = Modifier.size(20.dp)) },
                                    label = "Search",
                                    isSelected = currentRoute == "search",
                                    onClick = { nestedNavController.navigate("search") {
                                        popUpTo("photos") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    } }
                                )
                            }
                        }
                    }
                } else {
                    androidx.compose.material3.Surface(
                        modifier = Modifier
                            .fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .height(60.dp)
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DockItem(
                                icon = { Icon(Icons.Rounded.Image, contentDescription = "Photos", modifier = Modifier.size(20.dp)) },
                                label = "Photos",
                                isSelected = currentRoute == "photos",
                                onClick = { nestedNavController.navigate("photos") {
                                    popUpTo("photos") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                } }
                            )
                            DockItem(
                                icon = { Icon(Icons.Rounded.PhotoAlbum, contentDescription = "Albums", modifier = Modifier.size(20.dp)) },
                                label = "Albums",
                                isSelected = currentRoute?.startsWith("album") == true,
                                onClick = { nestedNavController.navigate("albums") {
                                    popUpTo("photos") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                } }
                            )
                            DockItem(
                                icon = { Icon(Icons.Rounded.Cloud, contentDescription = "Cloud", modifier = Modifier.size(20.dp)) },
                                label = "Cloud",
                                isSelected = currentRoute == "cloud",
                                onClick = { nestedNavController.navigate("cloud") {
                                    popUpTo("photos") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                } }
                            )
                            DockItem(
                                icon = { MagicSearchIcon(modifier = Modifier.size(20.dp)) },
                                label = "Search",
                                isSelected = currentRoute == "search",
                                onClick = { nestedNavController.navigate("search") {
                                    popUpTo("photos") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                } }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = nestedNavController,
            startDestination = "photos",
            enterTransition = { getEnterTransition(initialState.destination.route, targetState.destination.route) },
            exitTransition = { getExitTransition(initialState.destination.route, targetState.destination.route) },
            popEnterTransition = { getEnterTransition(initialState.destination.route, targetState.destination.route) },
            popExitTransition = { getExitTransition(initialState.destination.route, targetState.destination.route) }
        ) {
            composable("photos") {
                GalleryScreen(
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onPhotoClick = onPhotoClick,
                    viewModel = viewModel,
                    contentPadding = innerPadding,
                    isMainTab = true
                )
            }
            composable(
                route = "albums",
                enterTransition = {
                    if (initialState.destination.route == "album/{bucketName}") {
                        slideInHorizontally(
                            initialOffsetX = { -it / 3 },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
                        ) + fadeIn(spring(stiffness = Spring.StiffnessHigh))
                    } else {
                        getEnterTransition(initialState.destination.route, targetState.destination.route)
                    }
                },
                exitTransition = {
                    if (targetState.destination.route == "album/{bucketName}") {
                        slideOutHorizontally(
                            targetOffsetX = { -it / 3 },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
                        ) + fadeOut(spring(stiffness = Spring.StiffnessHigh))
                    } else {
                        getExitTransition(initialState.destination.route, targetState.destination.route)
                    }
                },
                popEnterTransition = {
                    if (initialState.destination.route == "album/{bucketName}") {
                        slideInHorizontally(
                            initialOffsetX = { -it / 3 },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
                        ) + fadeIn(spring(stiffness = Spring.StiffnessHigh))
                    } else {
                        getEnterTransition(initialState.destination.route, targetState.destination.route)
                    }
                },
                popExitTransition = {
                    if (targetState.destination.route == "album/{bucketName}") {
                        slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
                        ) + fadeOut(spring(stiffness = Spring.StiffnessHigh))
                    } else {
                        getExitTransition(initialState.destination.route, targetState.destination.route)
                    }
                }
            ) {
                AlbumsScreen(
                    viewModel = viewModel,
                    contentPadding = innerPadding,
                    onAlbumClick = { bucketName -> 
                        nestedNavController.navigate("album/$bucketName")
                    },
                    onNavigateToVault = onNavigateToVault,
                    onNavigateToDuplicateCleaner = { nestedNavController.navigate("duplicate_cleaner") },
                    onNavigateToPhotoMap = { nestedNavController.navigate("photo_map") }
                )
            }
            composable(
                route = "album/{bucketName}",
                enterTransition = {
                    if (initialState.destination.route == "albums") {
                        slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
                        ) + fadeIn(spring(stiffness = Spring.StiffnessHigh))
                    } else {
                        getEnterTransition(initialState.destination.route, targetState.destination.route)
                    }
                },
                exitTransition = {
                    if (targetState.destination.route == "albums") {
                        slideOutHorizontally(
                            targetOffsetX = { -it },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
                        ) + fadeOut(spring(stiffness = Spring.StiffnessHigh))
                    } else {
                        getExitTransition(initialState.destination.route, targetState.destination.route)
                    }
                },
                popEnterTransition = {
                    if (initialState.destination.route == "albums") {
                        slideInHorizontally(
                            initialOffsetX = { -it },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
                        ) + fadeIn(spring(stiffness = Spring.StiffnessHigh))
                    } else {
                        getEnterTransition(initialState.destination.route, targetState.destination.route)
                    }
                },
                popExitTransition = {
                    if (targetState.destination.route == "albums") {
                        slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
                        ) + fadeOut(spring(stiffness = Spring.StiffnessHigh))
                    } else {
                        getExitTransition(initialState.destination.route, targetState.destination.route)
                    }
                }
            ) { backStackEntry ->
                val bucketName = backStackEntry.arguments?.getString("bucketName")
                GalleryScreen(
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onPhotoClick = onPhotoClick,
                    viewModel = viewModel,
                    contentPadding = innerPadding,
                    bucketName = bucketName
                )
            }
            composable("search") {
                SearchScreen(
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onPhotoClick = onPhotoClick,
                    viewModel = viewModel,
                    contentPadding = innerPadding,
                    onAlbumClick = { bucketName ->
                        nestedNavController.navigate("album/$bucketName")
                    }
                )
            }
            composable("settings") {
                SettingsScreen(
                    contentPadding = innerPadding,
                    galleryViewModel = viewModel,
                    onBackClick = { nestedNavController.popBackStack() },
                    onNavigateToVault = onNavigateToVault,
                    onNavigateToUserbotSetup = onNavigateToUserbotSetup,
                    activeSection = settingsActiveSection,
                    onActiveSectionChange = { settingsActiveSection = it }
                )
            }
            composable("cloud") {
                CloudScreen(
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onPhotoClick = onPhotoClick,
                    viewModel = viewModel,
                    contentPadding = innerPadding,
                    onNavigateToSettings = {
                        settingsActiveSection = "Cloud Backup"
                        nestedNavController.navigate("settings")
                    }
                )
            }
            composable("duplicate_cleaner") {
                DuplicateCleanerScreen(
                    galleryViewModel = viewModel,
                    onBackClick = { nestedNavController.popBackStack() }
                )
            }
            composable("photo_map") {
                PhotoMapScreen(
                    galleryViewModel = viewModel,
                    onBackClick = { nestedNavController.popBackStack() }
                )
            }
        }
        
        // Selection Mode SplitButton Overlay
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = slideInVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) { it } + fadeIn(spring(stiffness = Spring.StiffnessMedium)),
                exit = slideOutVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) { it } + fadeOut(spring(stiffness = Spring.StiffnessMedium)),
                modifier = Modifier
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp)
            ) {
            var expanded by remember { mutableStateOf(false) }
            var showMoveSheet by remember { mutableStateOf(false) }
            var showCopySheet by remember { mutableStateOf(false) }
            var showShareSheet by remember { mutableStateOf(false) }
            var showSmartDeleteDialog by remember { mutableStateOf(false) }
            var showCloudDeleteDialog by remember { mutableStateOf(false) }
            var showMultiDeleteConfirmDialog by remember { mutableStateOf(false) }
            val confirmDeleteEnabled by viewModel.settingsRepository.confirmDeleteEnabledFlow.collectAsState(initial = true)
            
            val cloudMediaIds by viewModel.cloudMediaIds.collectAsState()
            val selectedIds = remember(selectedUris) {
                selectedUris.mapNotNull { Uri.parse(it).lastPathSegment }
            }
            val hasUnbackedUp = remember(selectedIds, cloudMediaIds) {
                selectedIds.isEmpty() || selectedIds.any { id -> !cloudMediaIds.contains(id) }
            }
            val hasBackedUp = remember(selectedIds, cloudMediaIds) {
                selectedIds.isNotEmpty() && selectedIds.any { id -> cloudMediaIds.contains(id) }
            }
            
            if (showShareSheet) {
                CustomShareSheet(
                    uris = selectedUris.map { Uri.parse(it) },
                    onDismiss = { showShareSheet = false }
                )
            }

            if (showMoveSheet) {
                androidx.compose.material3.ModalBottomSheet(
                    onDismissRequest = { showMoveSheet = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    val albums by viewModel.allAlbums.collectAsState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 24.dp)
                    ) {
                        Text(
                            "Move to Album",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            "${selectedUris.size} items · ${albums.size} albums",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.heightIn(max = 400.dp)
                        ) {
                            // Private Space as first item
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showMoveSheet = false
                                            val activity = context as? androidx.fragment.app.FragmentActivity
                                            if (activity != null) {
                                                viewModel.vaultAuthManager.authenticate(
                                                    activity = activity,
                                                    onSuccess = {
                                                        val uris = selectedUris.mapNotNull { android.net.Uri.parse(it) }
                                                        viewModel.hideMedia(uris)
                                                        viewModel.clearSelection()
                                                    },
                                                    onFailure = {}
                                                )
                                            }
                                        }
                                        .padding(vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Rounded.Security,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Private Space",
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                                        )
                                        Text(
                                            "Hidden \u00b7 Biometric protected",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        Icons.Rounded.VisibilityOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            }
                            items(albums.size) { index ->
                                val album = albums[index]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showMoveSheet = false
                                            viewModel.moveSelectedMedia(album.bucketName)
                                        }
                                        .padding(vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Rounded.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            album.bucketName,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                                        )
                                        Text(
                                            "${album.itemCount} items",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (index < albums.size - 1) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                        modifier = Modifier.padding(start = 56.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            if (showCopySheet) {
                androidx.compose.material3.ModalBottomSheet(
                    onDismissRequest = { showCopySheet = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    val albums by viewModel.allAlbums.collectAsState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 24.dp)
                    ) {
                        Text(
                            "Copy to Album",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            "${selectedUris.size} items · ${albums.size} albums",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.heightIn(max = 400.dp)
                        ) {
                            items(albums.size) { index ->
                                val album = albums[index]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showCopySheet = false
                                            viewModel.copySelectedMedia(album.bucketName)
                                        }
                                        .padding(vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Rounded.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            album.bucketName,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                                        )
                                        Text(
                                            "${album.itemCount} items",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (index < albums.size - 1) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                        modifier = Modifier.padding(start = 56.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showSmartDeleteDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showSmartDeleteDialog = false },
                    title = { Text("Delete Items") },
                    text = { Text("Some selected items are backed up to the cloud. Choose how to delete:") },
                    confirmButton = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            // Option 1: Delete Everywhere
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    showSmartDeleteDialog = false
                                    // Delete cloud backups
                                    viewModel.deleteCloudBackupsByUris(selectedUris)
                                    // Delete from device (trash)
                                    val uris = selectedUris.map { Uri.parse(it) }
                                    if (uris.isNotEmpty()) {
                                        try {
                                            val trashIntent = android.provider.MediaStore.createTrashRequest(
                                                context.contentResolver,
                                                uris,
                                                true
                                            )
                                            trashLauncher.launch(
                                                androidx.activity.result.IntentSenderRequest.Builder(trashIntent.intentSender).build()
                                            )
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            ) {
                                Text("Delete Everywhere", color = MaterialTheme.colorScheme.error)
                            }
                            // Option 2: Device Only
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    showSmartDeleteDialog = false
                                    // Delete from device only, keep cloud backup
                                    val uris = selectedUris.map { Uri.parse(it) }
                                    if (uris.isNotEmpty()) {
                                        try {
                                            val trashIntent = android.provider.MediaStore.createTrashRequest(
                                                context.contentResolver,
                                                uris,
                                                true
                                            )
                                            trashLauncher.launch(
                                                androidx.activity.result.IntentSenderRequest.Builder(trashIntent.intentSender).build()
                                            )
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            ) {
                                Text("Device Only")
                            }
                            // Option 3: Cloud Only
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    showSmartDeleteDialog = false
                                    // Delete cloud backups only, keep local files
                                    viewModel.deleteCloudBackupsByUris(selectedUris)
                                    viewModel.clearSelection()
                                    android.widget.Toast.makeText(
                                        context,
                                        "Removed cloud backups. Local files kept.",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            ) {
                                Text("Cloud Only")
                            }
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { showSmartDeleteDialog = false }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showCloudDeleteDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showCloudDeleteDialog = false },
                    title = { Text("Delete Backup from Cloud") },
                    text = { Text("Are you sure you want to delete the cloud backup for the selected item(s)? The files on your device will NOT be deleted, but the backups on Telegram will be permanently removed.") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                showCloudDeleteDialog = false
                                viewModel.deleteCloudBackupsByUris(selectedUris)
                                viewModel.clearSelection()
                            }
                        ) {
                            Text("Delete Backup", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showCloudDeleteDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showMultiDeleteConfirmDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showMultiDeleteConfirmDialog = false },
                    title = { Text("Move to Recycle Bin") },
                    text = { Text("Move ${selectedUris.size} items to the Recycle Bin?") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                showMultiDeleteConfirmDialog = false
                                val uris = selectedUris.map { Uri.parse(it) }
                                if (uris.isNotEmpty()) {
                                    try {
                                        val trashIntent = android.provider.MediaStore.createTrashRequest(
                                            context.contentResolver,
                                            uris,
                                            true
                                        )
                                        trashLauncher.launch(
                                            androidx.activity.result.IntentSenderRequest.Builder(trashIntent.intentSender).build()
                                        )
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            Text("Move to Bin", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { showMultiDeleteConfirmDialog = false }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
            
            val isTrashPage = currentRoute == "album/{bucketName}" && albumNameArg == "Trash"
            if (isTrashPage) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val uris = selectedUris.map { Uri.parse(it) }
                            if (uris.isNotEmpty()) {
                                try {
                                    val restoreIntent = android.provider.MediaStore.createTrashRequest(
                                        context.contentResolver,
                                        uris,
                                        false
                                    )
                                    restoreLauncher.launch(
                                        androidx.activity.result.IntentSenderRequest.Builder(restoreIntent.intentSender).build()
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    android.widget.Toast.makeText(context, "Error restoring: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(50),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Restore",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Restore", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            val uris = selectedUris.map { Uri.parse(it) }
                            if (uris.isNotEmpty()) {
                                try {
                                    val deleteIntent = android.provider.MediaStore.createDeleteRequest(
                                        context.contentResolver,
                                        uris
                                    )
                                    permanentDeleteLauncher.launch(
                                        androidx.activity.result.IntentSenderRequest.Builder(deleteIntent.intentSender).build()
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    android.widget.Toast.makeText(context, "Error deleting: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(50),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete Permanently",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Delete", fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                val isCreateEnabled = selectedUris.size in 1..8
                val isStitchEnabled = selectedUris.size in 2..10
                var createMenuExpanded by remember { mutableStateOf(false) }
                var moreMenuExpanded by remember { mutableStateOf(false) }

                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Share
                        IconButton(onClick = {
                            if (selectedUris.isNotEmpty()) showShareSheet = true
                        }) {
                            Icon(Icons.Rounded.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        }

                        // Copy
                        IconButton(onClick = { showCopySheet = true }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        }

                        // Move
                        IconButton(onClick = { showMoveSheet = true }) {
                            Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = "Move", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        }

                        // Create (Collage/Stitch)
                        Box {
                            IconButton(onClick = { createMenuExpanded = true }) {
                                Icon(Icons.Rounded.Add, contentDescription = "Create", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            }
                            DropdownMenu(
                                expanded = createMenuExpanded,
                                onDismissRequest = { createMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Collage") },
                                    leadingIcon = { Icon(Icons.Rounded.GridView, contentDescription = null) },
                                    onClick = {
                                        createMenuExpanded = false
                                        if (isCreateEnabled) {
                                            onCreateCollage(selectedUris.toList())
                                        } else {
                                            android.widget.Toast.makeText(context, "Collage supports up to 8 images", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Stitch") },
                                    leadingIcon = { Icon(Icons.Rounded.SwapVert, contentDescription = null) },
                                    onClick = {
                                        createMenuExpanded = false
                                        if (isStitchEnabled) {
                                            onCreateStitch(selectedUris.toList())
                                        } else {
                                            android.widget.Toast.makeText(context, "Select between 2 and 10 images to stitch", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }

                        // Delete
                        IconButton(onClick = {
                            if (hasBackedUp) {
                                showSmartDeleteDialog = true
                            } else if (confirmDeleteEnabled) {
                                showMultiDeleteConfirmDialog = true
                            } else {
                                val uris = selectedUris.map { Uri.parse(it) }
                                if (uris.isNotEmpty()) {
                                    try {
                                        val trashIntent = android.provider.MediaStore.createTrashRequest(context.contentResolver, uris, true)
                                        trashLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(trashIntent.intentSender).build())
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = com.inferno.gallery.ui.theme.LocalHarmonizedColors.current.error, modifier = Modifier.size(22.dp))
                        }

                        // More (hide + cloud actions)
                        Box {
                            IconButton(onClick = { moreMenuExpanded = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                            }
                            DropdownMenu(
                                expanded = moreMenuExpanded,
                                onDismissRequest = { moreMenuExpanded = false }
                            ) {
                                // Hide (Private Space)
                                DropdownMenuItem(
                                    text = { Text("Hide") },
                                    leadingIcon = { Icon(Icons.Rounded.VisibilityOff, contentDescription = null) },
                                    onClick = {
                                        moreMenuExpanded = false
                                        val activity = context as? androidx.fragment.app.FragmentActivity
                                        if (activity != null) {
                                            viewModel.vaultAuthManager.authenticate(
                                                activity = activity,
                                                onSuccess = {
                                                    val uris = selectedUris.mapNotNull { android.net.Uri.parse(it) }
                                                    viewModel.hideMedia(uris)
                                                    viewModel.clearSelection()
                                                },
                                                onFailure = {}
                                            )
                                        }
                                    }
                                )
                                if (hasUnbackedUp) {
                                    DropdownMenuItem(
                                        text = { Text("Backup to Cloud") },
                                        leadingIcon = { Icon(Icons.Rounded.Cloud, contentDescription = null) },
                                        onClick = { moreMenuExpanded = false; viewModel.backupSelectedMedia() }
                                    )
                                }
                                if (hasBackedUp) {
                                    DropdownMenuItem(
                                        text = { Text("Delete from Cloud") },
                                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = { moreMenuExpanded = false; showCloudDeleteDialog = true }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateAlbumDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {
                showCreateAlbumDialog = false
                newAlbumName = ""
            }
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Create new album",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    )
                    
                    androidx.compose.material3.OutlinedTextField(
                        value = newAlbumName,
                        onValueChange = { newAlbumName = it },
                        label = { Text("Album name") },
                        placeholder = { Text("Enter album name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                showCreateAlbumDialog = false
                                newAlbumName = ""
                            }
                        ) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newAlbumName.isNotBlank()) {
                                    val albumToCreate = newAlbumName.trim()
                                    viewModel.createAlbum(
                                        albumName = albumToCreate,
                                        onSuccess = {
                                            showCreateAlbumDialog = false
                                            newAlbumName = ""
                                            android.widget.Toast.makeText(context, "Album '$albumToCreate' created successfully", android.widget.Toast.LENGTH_SHORT).show()
                                            triggerSync()
                                        },
                                        onError = { errorMsg ->
                                            android.widget.Toast.makeText(context, "Error: $errorMsg", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    )
                                }
                            },
                            enabled = newAlbumName.isNotBlank()
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
}
