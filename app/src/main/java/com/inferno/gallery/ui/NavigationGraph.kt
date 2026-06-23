package com.inferno.gallery.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.inferno.gallery.ui.theme.MotionTokens

/**
 * Top-level navigation graph for Photon Gallery.
 *
 * CRITICAL M3 EXPRESSIVE RULE: The entire [NavHost] is wrapped inside a
 * [SharedTransitionLayout] so that shared element transitions (shape-morphing)
 * work seamlessly between the gallery grid and the detail screen.
 *
 * Both [SharedTransitionScope] and [AnimatedVisibilityScope] are forwarded
 * into each screen composable, enabling [Modifier.sharedElement()] to
 * coordinate the cross-destination animation.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NavigationGraph(
    isLoading: Boolean,

    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    if (isLoading) {
        Box(modifier = modifier.fillMaxSize().background(Color.Black))
        return
    }

    val galleryViewModel: GalleryViewModel = viewModel()

    SharedTransitionLayout(modifier = modifier) {
        NavHost(
            navController = navController,
            startDestination = "gallery",
            // ── Forward navigation: slide in from right ──
            enterTransition = {
                slideInHorizontally(
                    animationSpec = tween(400, easing = MotionTokens.EmphasizedEasing),
                    initialOffsetX = { (it * 0.25f).toInt() }
                ) + fadeIn(
                    animationSpec = tween(220, 80, easing = MotionTokens.FadeEasing)
                ) + scaleIn(
                    animationSpec = tween(400, easing = MotionTokens.ScaleEasing),
                    initialScale = 0.94f
                )
            },
            exitTransition = {
                slideOutHorizontally(
                    animationSpec = tween(400, easing = MotionTokens.EmphasizedEasing),
                    targetOffsetX = { -(it * 0.12f).toInt() }
                ) + fadeOut(
                    animationSpec = tween(200, easing = MotionTokens.FadeEasing)
                ) + scaleOut(
                    animationSpec = tween(400, easing = MotionTokens.ScaleEasing),
                    targetScale = 0.94f
                )
            },
            // ── Back navigation: slide in from left ──
            popEnterTransition = {
                slideInHorizontally(
                    animationSpec = tween(400, easing = MotionTokens.EmphasizedEasing),
                    initialOffsetX = { -(it * 0.12f).toInt() }
                ) + fadeIn(
                    animationSpec = tween(220, 80, easing = MotionTokens.FadeEasing)
                ) + scaleIn(
                    animationSpec = tween(400, easing = MotionTokens.ScaleEasing),
                    initialScale = 0.94f
                )
            },
            popExitTransition = {
                slideOutHorizontally(
                    animationSpec = tween(400, easing = MotionTokens.EmphasizedEasing),
                    targetOffsetX = { (it * 0.25f).toInt() }
                ) + fadeOut(
                    animationSpec = tween(200, easing = MotionTokens.FadeEasing)
                ) + scaleOut(
                    animationSpec = tween(400, easing = MotionTokens.ScaleEasing),
                    targetScale = 0.94f
                )
            }
        ) {

            composable("gallery") {
                MainAppLayout(
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@composable,
                    onPhotoClick = { mediaId, bucket, query ->
                        var route = "detail/$mediaId"
                        if (bucket != null) route += "?bucket=${android.net.Uri.encode(bucket)}"
                        if (query != null) {
                            route += if (bucket != null) "&highlight=${android.net.Uri.encode(query)}"
                                     else "?highlight=${android.net.Uri.encode(query)}"
                        }
                        navController.navigate(route)
                    },
                    onCreateCollage = { uriStrings ->
                        val joined = uriStrings.joinToString("|")
                        val encoded = android.util.Base64.encodeToString(
                            joined.toByteArray(Charsets.UTF_8),
                            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                        )
                        navController.navigate("collage?uris=$encoded")
                    },
                    onCreateStitch = { uriStrings ->
                        val joined = uriStrings.joinToString("|")
                        val encoded = android.util.Base64.encodeToString(
                            joined.toByteArray(Charsets.UTF_8),
                            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                        )
                        navController.navigate("stitch?uris=$encoded")
                    },
                    onNavigateToVault = { navController.navigate("vault") },
                    onNavigateToUserbotSetup = { navController.navigate("userbot_setup") },
                    viewModel = galleryViewModel
                )
            }

            composable(
                route = "detail/{mediaId}?bucket={bucketName}&highlight={highlightText}",
                arguments = listOf(
                    navArgument("mediaId") { type = NavType.StringType },
                    navArgument("bucketName") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("highlightText") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) { backStackEntry ->
                val mediaId = backStackEntry.arguments?.getString("mediaId") ?: return@composable
                val bucketName = backStackEntry.arguments?.getString("bucketName")
                val highlightText = backStackEntry.arguments?.getString("highlightText")
                
                androidx.compose.runtime.LaunchedEffect(mediaId, bucketName) {
                    galleryViewModel.loadDetailMedia(mediaId, bucketName)
                }
                
                val useFullScreen by settingsViewModel.useFullScreen.collectAsState()
                DetailScreen(
                    mediaId = mediaId,
                    bucketName = bucketName,
                    highlightText = highlightText,
                    useFullScreenGlobal = useFullScreen,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@composable,
                    onBack = { navController.popBackStack() },
                    viewModel = galleryViewModel
                )
            }

            composable(
                route = "collage?uris={uris}",
                arguments = listOf(
                    navArgument("uris") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val encoded = backStackEntry.arguments?.getString("uris") ?: ""
                val joined = try {
                    String(
                        android.util.Base64.decode(encoded, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP),
                        Charsets.UTF_8
                    )
                } catch (e: Exception) { "" }
                val uris = joined.split("|").filter { it.isNotBlank() }
                CollageScreen(
                    initialUris = uris,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "stitch?uris={uris}",
                arguments = listOf(
                    navArgument("uris") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val encoded = backStackEntry.arguments?.getString("uris") ?: ""
                val joined = try {
                    String(
                        android.util.Base64.decode(encoded, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP),
                        Charsets.UTF_8
                    )
                } catch (e: Exception) { "" }
                val uris = joined.split("|").filter { it.isNotBlank() }
                StitchScreen(
                    initialUris = uris,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("vault") {
                com.inferno.gallery.ui.vault.PrivateSpaceScreen(
                    viewModel = galleryViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable("userbot_setup") {
                UserbotSetupScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
