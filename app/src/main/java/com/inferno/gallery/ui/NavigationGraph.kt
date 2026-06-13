package com.inferno.gallery.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
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
            enterTransition = { fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) },
            exitTransition = { fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) },
            popEnterTransition = { fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) },
            popExitTransition = { fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) }
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
                        // Join with | separator (safer than comma for content URIs),
                        // then double-encode so navArgs parser doesn't choke on URI chars
                        val joined = uriStrings.joinToString("|")
                        val encoded = android.util.Base64.encodeToString(
                            joined.toByteArray(Charsets.UTF_8),
                            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                        )
                        navController.navigate("collage?uris=$encoded")
                    },
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
        }
    }
}
