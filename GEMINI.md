# Photon Gallery — Project Rules

> A high-performance, AI-powered local gallery app.
> Design language: Material 3 Expressive (creative, not limited by the spec).
> Target device: iQOO Neo 7 (I2214).

---

## 1. Dependencies (Version Catalog)

All third-party versions live in `libs.versions.toml`. No hardcoded versions in `build.gradle.kts`.

| Library | Minimum Version | Notes |
|---------|----------------|-------|
| Compose Material 3 | 1.4.0+ | M3 Expressive APIs required |
| Compose Animation | Latest stable | Must support `SharedTransitionScope` |
| Coil | 2.6.0+ | Image loading — no Glide, no Picasso |
| Room | Latest stable | FTS4/FTS5 for text search |

---

## 2. Banned Patterns

These will be rejected on sight:

- **XML layouts** — `res/layout/*.xml` is forbidden. 100% Jetpack Compose.
- **Ad-hoc singletons** — Never write `SettingsRepository(context)` in a `remember {}` block. Access shared services through `GalleryViewModel`.
- **Per-cell coroutines** — Never launch `produceState`, `LaunchedEffect`, or `withContext(IO)` inside individual grid/list cells for I/O. Pre-compute in the ViewModel or PagingSource.
- **Main-thread I/O** — `File.exists()`, `File.length()`, or any disk/network call on the main thread is banned. Always use `Dispatchers.IO`.
- **Deprecated APIs** — Fix deprecation warnings immediately. Don't ignore them.
- **Duplicate imports** — Optimize imports before committing.

---

## 3. Design & Animation

The app is built on M3 Expressive, but **you are not limited by it**. Be creative with design, animations, layouts, and micro-interactions.

### Animation Rules
- Every animation must be **fast and fluid** — no sluggish 300ms+ fades.
- `spring()` physics are preferred but not mandatory. Whatever you choose, it must feel snappy.
- Use `SharedTransitionScope` + `Modifier.sharedBounds()` for grid-to-fullscreen image transitions.
- Use `HorizontalFloatingToolbar` instead of `BottomAppBar` for navigation.

### Design Rules
- Avoid legacy components: `BottomAppBar`, standard `TopAppBar`, standard `CircularProgressIndicator`, standard `Chip`.
- Dynamic typography: Use variable fonts (Roboto Flex) with shifting weights/widths for visual hierarchy.
- Every interactive element should have tactile feedback (haptics on selection, spring-animated press states).

---

## 4. Architecture

### Pattern: MVVM + Repository
```
UI (Composable) → ViewModel (StateFlow) → Repository → Room / ContentResolver / ML
```

- **ViewModels** own all business logic and state. Expose `StateFlow` to the UI.
- **Composables** are stateless renderers. They observe state and emit events — no data fetching, no file I/O, no ML inference.
- **Repositories** handle data access (Room, MediaStore, network). Called from ViewModel with appropriate dispatchers.

### File Organization
- No single `.kt` file should exceed **~800 lines**. Extract composables by feature area:
  - `PermissionsFlow.kt` — permission request UI
  - `NavigationDock.kt` — floating toolbar dock + filter chips
  - `SelectionToolbar.kt` — selection mode actions
- One composable per concern. Don't cram unrelated UI into the same file.

### State Management
- `StateFlow` for ViewModel → UI communication.
- `remember` / `mutableStateOf` for local UI-only state (toggles, text fields, animation flags).
- Never use `LiveData`.

---

## 5. Performance

### Image Loading (Coil)
- Thumbnail grids: Always set `.size(width, height)` on `ImageRequest` (≤ 384px for thumbnails).
- Use `.memoryCacheKey()` with a stable key (e.g., media ID + modified date) for cache hits.
- Set `.precision(Precision.EXACT)` to prevent decode waste.
- Use `crossfade(150)` for smooth loading — no jarring pop-in.

### Bitmap Safety
- Before creating composite bitmaps (collage, stitch), estimate canvas memory: `width × height × 4 bytes`.
- Cap maximum canvas dimension to **20,000px** on any axis.
- Always wrap large bitmap operations in `try/catch` for `OutOfMemoryError`.

### Threading
- **UI thread:** Rendering, spring physics, gesture handling only.
- **Dispatchers.IO:** File access, Room queries, MediaStore queries.
- **Dispatchers.Default:** Vector math, ML inference, heavy computation.
- **WorkManager:** Background media scanning, ML model downloads, thumbnail pre-caching.

### Room & SQL
- Prefer `@Query`-annotated DAO methods over raw `SimpleSQLiteQuery`.
- If raw SQL is unavoidable, extract query strings to companion object constants.
- Always use `@Transaction` for multi-table operations.

---

## 6. AI / On-Device ML

AI is a **core differentiator** of this app. Rules for ML features:

- All ML inference runs on `Dispatchers.Default`, never on the main thread.
- Use `WorkManager` for batch operations (scanning entire library, embedding generation).
- ML models must be downloaded on-demand, not bundled in the APK (keep APK size small).
- Smart search uses vector embeddings stored in Room. Pre-compute embeddings during media sync.
- Face detection and scene recognition should run incrementally (process new photos only, skip already-indexed).
- Always provide graceful fallbacks when ML models aren't downloaded yet (show standard search, not errors).

---

## 7. Telegram Cloud Backup

Cloud backup via Telegram is a **first-class feature**. Handle it with care:

### URI Resolution
- Resolve Telegram file URIs in the **ViewModel layer**, never in per-cell composables.
- Pre-resolve `localExists` boolean and `resolvedUri` when building `GalleryItem` lists — don't check file existence per-cell.

### Offline Handling
- If a backed-up file doesn't exist locally, show a cloud icon overlay on the thumbnail.
- Thumbnail download from Telegram should use Coil's custom `Fetcher` pattern (`TelegramCoilFetcher`).
- Full-resolution download is on-demand (user taps to download).

### Sync Safety
- Always handle Telegram API `ChatMigratedException` — update the chat ID automatically.
- Manifest-based sync: Track what's been uploaded via `SyncManifestManager` to avoid re-uploading.
- Never block the UI while waiting for Telegram API responses.

---

## 8. Gesture & Interaction Standards

### DetailScreen (Image Viewer)
- **Swipe-to-dismiss:** Vertical drag at 1× zoom translates the image and fades the scrim. Dismiss threshold: 200px offset OR 800px/s velocity. Must trigger the shared-element back animation.
- **Double-tap to zoom:** Toggle between 1× and 3.5× with animated scale + offset.
- **Pinch-to-zoom:** Focal-point zoom up to 20×, with fling on release.
- **Pager swipe:** Only enabled when `scale ≤ 1.05f` and dismiss offset is zero (prevents conflicts with zoom gestures).

### Grid Screen
- **Pinch-to-zoom columns:** Changes column count (2–6). Each snap triggers `HapticFeedbackType.LongPress`.
- **Long-press selection:** Enters selection mode with haptic feedback. Animated selection overlay.

---

## 9. Safety & Edge Cases

- **Guard screen entry points:** If a screen requires N items (e.g., Stitch requires ≥2), validate on entry and navigate back with a toast if insufficient.
- **File existence checks:** Always on `Dispatchers.IO`. Never assume a file exists without checking.
- **Error handling:** Every user-facing action (delete, move, share, backup) must have `try/catch` with a user-friendly toast. Never let exceptions crash the app silently.
- **Permission recovery:** If storage permission is revoked while the app is running, detect it on resume and redirect to the permissions screen.

---

## 10. Build & Release

- **Release command:** `.\gradlew assembleRelease installRelease --no-daemon`
- **Target device:** iQOO Neo 7 (I2214)
- **After every feature:** Push a release build to the phone. Don't forget.
- **Deprecation warnings in build output:** Treat as bugs. Fix them before moving on.
- **Commit flow:** Always suggest commit messages to the user using the `ask_question` tool before making any git commit.
