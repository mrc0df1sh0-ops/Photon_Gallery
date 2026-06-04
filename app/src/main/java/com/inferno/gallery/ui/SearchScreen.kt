package com.inferno.gallery.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkInfo
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.core.tween
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SearchScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onPhotoClick: (String, String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel = viewModel(),
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val query by viewModel.searchQuery.collectAsState()
    val semanticResults by viewModel.semanticSearchResults.collectAsState()
    val ftsResults by viewModel.ftsSearchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val aiIndexWorkInfo by viewModel.aiIndexWorkInfo.collectAsState(initial = null)

    val hasAnyResults = semanticResults.isNotEmpty() || ftsResults.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        // ── Search Bar ──────────────────────────────────────────────────────────
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = { viewModel.updateSearchQuery(it) },
                    onSearch = { /* Auto-search handles this via debounce */ },
                    expanded = false,
                    onExpandedChange = {},
                    leadingIcon = {
                        MagicSearchIcon(modifier = Modifier.size(24.dp))
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    placeholder = {
                        Text("Search photos, scenes, or text…")
                    }
                )
            },
            expanded = false,
            onExpandedChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {}

        // ── Results Area ────────────────────────────────────────────────────────
        AnimatedContent(
            targetState = Triple(query.isBlank(), isSearching, hasAnyResults),
            transitionSpec = {
                (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                    scaleIn(spring(stiffness = Spring.StiffnessMediumLow), initialScale = 0.95f))
                    .togetherWith(fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) +
                        scaleOut(spring(stiffness = Spring.StiffnessMediumLow), targetScale = 0.95f))
            },
            modifier = Modifier.fillMaxSize()
        ) { (isBlank, searching, hasResults) ->
            when {
                isBlank -> EmptySearchState(
                    aiIndexWorkInfo = aiIndexWorkInfo,
                    onStart = { viewModel.startAiIndexing() },
                    onStop = { viewModel.stopAiIndexing() },
                    onClearAndReindex = { viewModel.clearIndexAndReindex() }
                )
                searching -> SearchingState()
                !hasResults -> NoResultsState(query)
                else -> UnifiedSearchResults(
                    semanticResults = semanticResults,
                    ftsResults = ftsResults,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onPhotoClick = onPhotoClick
                )
            }
        }
    }
}

@Composable
private fun EmptySearchState(
    aiIndexWorkInfo: WorkInfo?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClearAndReindex: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                text = "Find anything",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Describe a scene, a feeling,\nor text you remember seeing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Live Indexing Banner
            Spacer(modifier = Modifier.height(24.dp))
            LiveIndexingBanner(aiIndexWorkInfo, onStart, onStop, onClearAndReindex)
        }
    }
}

@Composable
private fun SearchingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            LinearProgressIndicator(
                modifier = Modifier
                    .width(160.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer
            )
            Text(
                text = "Thinking…",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoResultsState(query: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "No results for",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "\u201c$query\u201d",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Try different words or descriptions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun UnifiedSearchResults(
    semanticResults: List<GalleryItem>,
    ftsResults: List<GalleryItem>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onPhotoClick: (String, String?) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // ── Text Matches (FTS/OCR) ── horizontal filmstrip at top
        if (ftsResults.isNotEmpty()) {
            item(span = { GridItemSpan(3) }) {
                Column {
                    Text(
                        text = "\uD83D\uDCDD Text Matches  •  ${ftsResults.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        items(
                            items = ftsResults,
                            key = { "fts_${it.id}" }
                        ) { item ->
                            AsyncImage(
                                model = item.uri,
                                contentDescription = item.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onPhotoClick(item.id, null) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        // ── Visual Matches (AI Semantic) ── standard grid
        if (semanticResults.isNotEmpty()) {
            item(span = { GridItemSpan(3) }) {
                Text(
                    text = "\u2728 Visual Matches  •  ${semanticResults.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            items(
                items = semanticResults,
                key = { "ai_${it.id}" }
            ) { item ->
                GalleryGridItem(
                    item = item,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onClick = { onPhotoClick(item.id, null) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiveIndexingBanner(
    workInfo: WorkInfo?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClearAndReindex: () -> Unit = {}
) {
    val state = workInfo?.state
    val isRunning = state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.RUNNING
    val progress = workInfo?.progress
    val indexed = progress?.getInt("progress", 0) ?: 0
    val total = progress?.getInt("total", 0) ?: 0
    val recentUris = progress?.getStringArray("recent_uris") ?: emptyArray()

    Card(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .padding(16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live AI Indexing",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (isRunning) {
                    FilledTonalButton(onClick = onStop, modifier = Modifier.height(36.dp)) {
                        Text("Pause")
                    }
                } else {
                    FilledTonalButton(onClick = onStart, modifier = Modifier.height(36.dp)) {
                        Text("Start")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (recentUris.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(
                        items = recentUris,
                        key = { it } // Important for animations
                    ) { uriString ->
                        AsyncImage(
                            model = android.net.Uri.parse(uriString),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .animateItem()
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (isRunning) "Scanning for images..." else "Ready to index",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val progressFloat = if (total > 0) indexed.toFloat() / total.toFloat() else 0f
            LinearProgressIndicator(
                progress = { progressFloat },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Indexed $indexed of $total images", 
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            // Wipes all stored embeddings and re-indexes from scratch.
            // Use this once after a model pipeline fix to discard stale vectors.
            OutlinedButton(
                onClick = onClearAndReindex,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                )
            ) {
                Text("Clear Index & Re-index All", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
