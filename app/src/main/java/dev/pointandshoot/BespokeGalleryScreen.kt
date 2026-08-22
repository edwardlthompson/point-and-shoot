package dev.pointandshoot

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.provider.MediaStore
import android.media.ExifInterface
import android.graphics.Matrix
import android.graphics.Bitmap
import android.widget.VideoView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlin.math.roundToInt

/**
 * Bespoke gallery with media specs display.
 * Shows media preview at top, detailed specs below, and external gallery option.
 */

private fun parseRationalOrDecimal(value: String?): Double? {
    val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (raw.contains("/")) {
        val parts = raw.split("/")
        if (parts.size == 2) {
            val n = parts[0].trim().toDoubleOrNull()
            val d = parts[1].trim().toDoubleOrNull()
            if (n != null && d != null && d != 0.0) {
                return n / d
            }
        }
        return null
    }
    return raw.toDoubleOrNull()
}

private fun formatFocalLength(
    focalLength: String?,
    focalLength35mm: String?,
): String {
    val actualMm = parseRationalOrDecimal(focalLength)
    val eqMm = parseRationalOrDecimal(focalLength35mm)
    val actualLabel =
        actualMm?.let { "${String.format("%.1f", it)}mm" } ?: focalLength ?: "Unknown"
    return if (eqMm != null && eqMm > 0.0) {
        "$actualLabel (${String.format("%.0f", eqMm)}mm eq)"
    } else {
        actualLabel
    }
}

private fun formatShutterSpeed(shutterSpeed: String?): String {
    val sec = parseRationalOrDecimal(shutterSpeed) ?: return shutterSpeed ?: "Unknown"
    if (sec <= 0.0) return shutterSpeed ?: "Unknown"
    return if (sec >= 1.0) {
        "${String.format("%.3f", sec)}s"
    } else {
        val inv = (1.0 / sec).roundToInt().coerceAtLeast(1)
        "1/$inv"
    }
}

private fun formatDimensionsWithMegapixels(width: Int, height: Int): String {
    if (width <= 0 || height <= 0) return "${width}x${height}"
    val mp = (width.toDouble() * height.toDouble()) / 1_000_000.0
    return "${width}x${height} (${String.format("%.1f", mp)}MP)"
}

@Composable
fun GridItem(
    media: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selected: Boolean = false,
) {
    val context = LocalContext.current
    var thumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var displayThumbnail by remember { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(media.uri) {
        PnsBitmapGuard.safeRecycle(thumbnail, "GridItem.thumb")
        PnsBitmapGuard.safeRecycle(displayThumbnail, "GridItem.thumbRotated")
        thumbnail = null
        displayThumbnail = null
        val loaded = loadGalleryThumbnail(context, media.uri, 120)
        thumbnail = loaded
        PnsBitmapGuard.onAllocated("GridItem.thumb", loaded)
        if (loaded != null) {
            val isDng = media.displayName.lowercase().endsWith(".dng")
            val exifOrient =
                withContext(Dispatchers.IO) {
                    extractExifMetadata(context, media.uri).orientation
                }
            val rotated =
                DngGalleryOrientation.applyGalleryDisplayRotation(loaded, isDng, exifOrient)
            displayThumbnail = rotated
            if (rotated !== loaded) {
                PnsBitmapGuard.onAllocated("GridItem.thumbRotated", rotated)
            }
        }
    }
    DisposableEffect(media.uri) {
        onDispose {
            PnsBitmapGuard.safeRecycle(thumbnail, "GridItem.dispose")
            PnsBitmapGuard.safeRecycle(displayThumbnail, "GridItem.disposeRotated")
            thumbnail = null
            displayThumbnail = null
        }
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
    ) {
        val currentThumbnail = displayThumbnail ?: thumbnail
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (currentThumbnail != null) {
                Image(
                    bitmap = currentThumbnail.asImageBitmap(),
                    contentDescription = media.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Show media type indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(
                            Color.Black.copy(alpha = 0.6f),
                            RoundedCornerShape(4.dp)
                        )
                ) {
                    Text(
                        text = if (media.isVideo) "🎥" else if (media.isRaw) "📷" else "📸",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(2.dp)
                    )
                }
                if (selectionMode) {
                    Icon(
                        imageVector =
                            if (selected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircleOutline,
                        contentDescription = if (selected) "Selected" else "Not selected",
                        tint = if (selected) Color(0xFFFF9800) else Color.White.copy(alpha = 0.55f),
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .size(22.dp),
                    )
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BespokeGalleryScreen(
    initialUri: Uri? = null,
    adbBatchShareCount: Int? = null,
    onBack: () -> Unit,
    onExternalGallery: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleScope = LocalLifecycleOwner.current.lifecycleScope
    var mediaItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var selectedMedia by remember { mutableStateOf<MediaItem?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedUris by remember { mutableStateOf(setOf<Uri>()) }
    var adbBatchShareDone by remember { mutableStateOf(false) }
    var showShareFormat by remember { mutableStateOf(false) }
    var showFileCard by remember { mutableStateOf(false) }
    var compareUri by remember { mutableStateOf<Uri?>(null) }
    var showLibraryDesk by remember { mutableStateOf(false) }
    var showComparePane by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var trashStash by remember { mutableStateOf<GalleryTrash.Stash?>(null) }
    var undoUntil by remember { mutableStateOf(0L) }
    val groups = remember(mediaItems) { GalleryCaptureGroups.group(mediaItems) }
    val selectedGroup =
        remember(selectedMedia, groups) {
            selectedMedia?.let { media ->
                groups.firstOrNull { group -> group.items.any { it.uri == media.uri } }
            }
        }

    // Initialize memory profiler
    val memoryProfiler = remember { MemoryProfiler.getInstance(context, lifecycleScope) }
    
    var selectedDetail by remember { mutableStateOf<MediaItem?>(null) }

    LaunchedEffect(Unit) {
        memoryProfiler.startProfiling(5000L)
        memoryProfiler.logEvent("Gallery screen opened")
    }

    LaunchedEffect(selectedMedia) {
        val media = selectedMedia ?: run {
            selectedDetail = null
            return@LaunchedEffect
        }
        selectedDetail =
            if (media.isVideo) {
                val videoMeta =
                    withContext(Dispatchers.IO) {
                        VideoCaptureMetadata.readFromUri(context, media.uri)
                    }
                media.copy(
                    frameRate = VideoCaptureMetadata.formatFrameRate(videoMeta.frameRate),
                    duration = VideoCaptureMetadata.formatDuration(videoMeta.durationMs),
                    bitRate = videoMeta.bitRate,
                    codec = videoMeta.codec,
                )
            } else {
                val exif = withContext(Dispatchers.IO) { extractExifMetadata(context, media.uri) }
                media.copy(
                    cameraId = exif.cameraId,
                    lens = exif.lens,
                    focalLength = exif.focalLength,
                    focalLength35mm = exif.focalLength35mm,
                    aperture = exif.aperture,
                    iso = exif.iso,
                    shutterSpeed = exif.shutterSpeed,
                    whiteBalance = exif.whiteBalance,
                )
            }
        memoryProfiler.logEvent("Lazy metadata loaded: ${media.displayName}")
    }

    DisposableEffect(Unit) {
        onDispose {
            PnsBitmapGuard.safeRecycle(selectedBitmap, "BespokeGallery.Cleanup")
            selectedBitmap = null
            PnsBitmapGuard.logLeakCheck("BespokeGallery")
            memoryProfiler.logEvent("Gallery screen closed")
            val report = memoryProfiler.stopProfiling()
            runCatching {
                val reportPath = memoryProfiler.saveReportToFile(report)
                Log.i("BespokeGallery", "Memory profiling report saved: $reportPath")
            }.onFailure { e ->
                Log.e("BespokeGallery", "Failed to save memory report", e)
            }
        }
    }
    
    // Reset zoom when changing media
    LaunchedEffect(selectedMedia) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }
    
    BackHandler {
        if (selectionMode) {
            selectionMode = false
            selectedUris = emptySet()
            PnsAdbLog.i(context, "navBack gallerySelectionCleared")
        } else {
            PnsAdbLog.i(context, "navBack galleryExit")
            onBack()
        }
    }

    LaunchedEffect(adbBatchShareCount, mediaItems, isLoading, adbBatchShareDone) {
        val want = adbBatchShareCount ?: return@LaunchedEffect
        if (adbBatchShareDone || isLoading) return@LaunchedEffect
        if (mediaItems.size < want) {
            PnsAdbLog.e(context, "gallery batchShare skipped items=${mediaItems.size} want=$want")
            adbBatchShareDone = true
            return@LaunchedEffect
        }
        adbBatchShareDone = true
        isGridView = true
        selectionMode = true
        selectedUris = mediaItems.take(want).map { it.uri }.toSet()
        shareMediaBatch(context, selectedUris.toList())
        selectionMode = false
        selectedUris = emptySet()
    }
    
    // Load media items
    LaunchedEffect(Unit) {
        Log.d("BespokeGallery", "=== Gallery screen opened, loading media items ===")
        memoryProfiler.logEvent("Starting media items load")
        isLoading = true
        lifecycleScope.launch {
            try {
                mediaItems = PnsMediaStoreGallery.loadIndex(context)
                Log.d("BespokeGallery", "Loaded ${mediaItems.size} media items")
                memoryProfiler.logEvent("Loaded ${mediaItems.size} media items")
                selectedMedia = initialUri?.let { uri ->
                    mediaItems.find { it.uri == uri }
                } ?: mediaItems.firstOrNull()
                isLoading = false
            } catch (e: Exception) {
                Log.e("BespokeGallery", "Error loading media items", e)
                memoryProfiler.logEvent("Error loading media items: ${e.message}")
                isLoading = false
            }
        }
    }
    
    // Load bitmap for selected media
    LaunchedEffect(selectedMedia) {
        selectedMedia?.let { media ->
            if (media.isVideo) {
                PnsBitmapGuard.safeRecycle(selectedBitmap, "BespokeGallery.VideoSelected")
                selectedBitmap = null
                return@let
            }
            lifecycleScope.launch {
                PnsBitmapGuard.safeRecycle(selectedBitmap, "BespokeGallery.BitmapChange")
                selectedBitmap = null
                memoryProfiler.logEvent("Loading bitmap: ${media.displayName}")
                val loaded = loadGalleryThumbnail(context, media.uri, 800)
                selectedBitmap = loaded
                PnsBitmapGuard.onAllocated("BespokeGallery.preview", loaded)
                loaded?.let { bitmap ->
                    memoryProfiler.logEvent(
                        "Loaded bitmap: ${bitmap.width}x${bitmap.height}, ${bitmap.byteCount / 1024}KB",
                    )
                }
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top bar
        TopAppBar(
            title = {
                Text(
                    "Gallery",
                    color = Color.White,
                    fontSize = 18.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = GalleryA11y.BACK,
                        tint = Color.White
                    )
                }
            },
            actions = {
                IconButton(onClick = {
                    isGridView = !isGridView
                    memoryProfiler.logEvent("Toggled view mode: ${if (isGridView) "grid" else "single"}")
                }) {
                    Icon(
                        if (isGridView) Icons.Default.Add else Icons.Default.Remove,
                        contentDescription = if (isGridView) GalleryA11y.SINGLE else GalleryA11y.GRID,
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = { showShareFormat = true },
                    enabled = !selectionMode || selectedUris.isNotEmpty() || selectedMedia != null,
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = GalleryA11y.SHARE,
                        tint = Color.White
                    )
                }
                IconButton(onClick = { showDeleteConfirmation = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = GalleryA11y.DELETE,
                        tint = Color.White
                    )
                }
                Box {
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = GalleryA11y.MORE,
                            tint = Color.White,
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Compare") },
                            onClick = {
                                showOverflowMenu = false
                                val media = selectedMedia ?: return@DropdownMenuItem
                                if (compareUri != null && compareUri != media.uri) {
                                    showComparePane = true
                                } else {
                                    compareUri = if (compareUri == media.uri) null else media.uri
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (compareUri != null) PnsColors.PhotoOrange else LocalContentColor.current,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Library desk") },
                            onClick = {
                                showOverflowMenu = false
                                showLibraryDesk = true
                            },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text("What this file is") },
                            onClick = {
                                showOverflowMenu = false
                                showFileCard = true
                            },
                            leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text("System gallery") },
                            onClick = {
                                showOverflowMenu = false
                                onExternalGallery()
                            },
                            leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black,
                titleContentColor = Color.White
            )
        )
        
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        } else if (mediaItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No media files found",
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
        } else {
            if (isGridView) {
                // Grid view
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(mediaItems) { media ->
                        GridItem(
                            media = media,
                            onClick = {
                                selectedMedia = media
                                isGridView = false
                                memoryProfiler.logEvent("Selected item from grid: ${media.displayName}")
                            }
                        )
                    }
                }
            } else {
                // Single view
                val pagerState = rememberPagerState { mediaItems.size }
                
                LaunchedEffect(pagerState.currentPage) {
                    selectedMedia = mediaItems[pagerState.currentPage]
                }
                
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    val media = mediaItems[pageIndex]
                    
                    Column(modifier = Modifier.fillMaxSize()) {
                        var pageExif by remember(media.uri) { mutableStateOf<ExifMetadata?>(null) }
                        var pageVideoMeta by remember(media.uri) { mutableStateOf<VideoCaptureMetadata.ReadInfo?>(null) }
                        LaunchedEffect(media.uri) {
                            if (media.isVideo) {
                                pageExif = null
                                pageVideoMeta =
                                    withContext(Dispatchers.IO) {
                                        VideoCaptureMetadata.readFromUri(context, media.uri)
                                    }
                            } else {
                                pageVideoMeta = null
                                pageExif =
                                    withContext(Dispatchers.IO) {
                                        extractExifMetadata(context, media.uri)
                                    }
                            }
                        }
                        val exifMetadata = pageExif ?: ExifMetadata()
                        val isDng = media.displayName.lowercase().endsWith(".dng")
                        val videoRot = pageVideoMeta?.rotationDegrees ?: 0

                        // Sprint **15.7**: 3:4 viewer tile, letterbox media (ContentScale.Fit).
                        // Size the tile to the leftover slot so a tall app bar cannot shove it off-screen.
                        BoxWithConstraints(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(Color.DarkGray),
                            contentAlignment = Alignment.Center,
                        ) {
                            val tileWidth = minOf(maxWidth, maxHeight * 3f / 4f)
                            val tileHeight = tileWidth * 4f / 3f
                            Box(
                                modifier =
                                    Modifier
                                        .size(tileWidth, tileHeight)
                                        .background(Color.DarkGray),
                                contentAlignment = Alignment.Center,
                            ) {
                            if (media.isVideo) {
                                GalleryInlineVideoPlayer(
                                    uri = media.uri,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                val bmp = if (selectedMedia?.uri == media.uri) selectedBitmap else null
                                if (bmp != null && !bmp.isRecycled) {
                                    val rotatedBitmap =
                                        DngGalleryOrientation.applyGalleryDisplayRotation(
                                            bmp,
                                            isDng,
                                            exifMetadata.orientation,
                                        )
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxSize()
                                                .pointerInput(media.uri) {
                                                    detectTapGestures(
                                                        onDoubleTap = {
                                                            scale = 1f
                                                            offsetX = 0f
                                                            offsetY = 0f
                                                        },
                                                    )
                                                }
                                                .pointerInput(media.uri) {
                                                    detectTransformGestures { _, pan, zoom, _ ->
                                                        val newScale = (scale * zoom).coerceIn(1f, 8f)
                                                        scale = newScale
                                                        if (scale > 1f) {
                                                            offsetX += pan.x
                                                            offsetY += pan.y
                                                            val maxPanX = (scale - 1) * rotatedBitmap.width / 2
                                                            val maxPanY = (scale - 1) * rotatedBitmap.height / 2
                                                            offsetX = offsetX.coerceIn(-maxPanX, maxPanX)
                                                            offsetY = offsetY.coerceIn(-maxPanY, maxPanY)
                                                        }
                                                    }
                                                },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Image(
                                            bitmap = rotatedBitmap.asImageBitmap(),
                                            contentDescription = media.displayName,
                                            modifier =
                                                Modifier
                                                    .fillMaxSize()
                                                    .graphicsLayer(
                                                        scaleX = scale,
                                                        scaleY = scale,
                                                        translationX = offsetX,
                                                        translationY = offsetY,
                                                    ),
                                            contentScale = ContentScale.Fit,
                                        )
                                    }
                                    LaunchedEffect(rotatedBitmap) {
                                        if (rotatedBitmap != bmp) {
                                            PnsBitmapGuard.safeRecycle(
                                                rotatedBitmap,
                                                "BespokeGallery.RotatedBitmap",
                                            )
                                        }
                                    }
                                } else {
                                    CircularProgressIndicator(color = Color.White)
                                }
                            }
                            }
                        }
                        
                        // Media specs below - scrollable
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            // File information
                            Text(
                                "File: ${media.displayName}",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            Text(
                                "Size: ${String.format("%.1f", media.size / (1024.0 * 1024.0))}MB",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            Text(
                                "Dimensions: ${formatDimensionsWithMegapixels(media.width, media.height)}",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            Text(
                                "MIME Type: ${media.mimeType}",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            Text(
                                "Modified: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(media.date * 1000))}",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val detail =
                                if (selectedMedia?.uri == media.uri) {
                                    selectedDetail ?: media
                                } else {
                                    media
                                }

                            // Camera metadata for images (lazy EXIF for current item)
                            if (!detail.isVideo) {
                                Text(
                                    "=== Camera Metadata ===",
                                    color = Color.Cyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                detail.cameraId?.let { camera ->
                                    Text(
                                        "Camera: $camera",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                                detail.lens?.let { lens ->
                                    Text(
                                        "Lens: $lens",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                                if (!detail.focalLength.isNullOrBlank() || !detail.focalLength35mm.isNullOrBlank()) {
                                    val formattedFocal =
                                        formatFocalLength(
                                            detail.focalLength,
                                            detail.focalLength35mm,
                                        )
                                    Text(
                                        "Focal Length: $formattedFocal",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                                detail.aperture?.let { aperture ->
                                    Text(
                                        "Aperture: f/$aperture",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                                detail.iso?.let { iso ->
                                    Text(
                                        "ISO: $iso",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                                detail.shutterSpeed?.let { shutter ->
                                    val formattedShutter = formatShutterSpeed(shutter)
                                    Text(
                                        "Shutter Speed: $formattedShutter",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                                detail.whiteBalance?.let { wb ->
                                    Text(
                                        "White Balance: $wb",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                                Text(
                                    "Color Space: ${media.colorSpace}",
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                                
                                if (media.isRaw) {
                                    Text(
                                        "RAW Format: Yes",
                                        color = Color.Yellow,
                                        fontSize = 12.sp
                                    )
                                }
                                
                                if (media.isHdr) {
                                    Text(
                                        "HDR: Yes",
                                        color = Color.Yellow,
                                        fontSize = 12.sp
                                    )
                                }
                            } else {
                                // Video metadata
                                Text(
                                    "=== Video Metadata ===",
                                    color = Color.Cyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                val videoMeta: MediaItem =
                                    when {
                                        selectedMedia?.uri == media.uri ->
                                            selectedDetail ?: detail
                                        else ->
                                            pageVideoMeta?.let { v ->
                                                detail.copy(
                                                    frameRate = VideoCaptureMetadata.formatFrameRate(v.frameRate),
                                                    duration = VideoCaptureMetadata.formatDuration(v.durationMs),
                                                    bitRate = v.bitRate,
                                                    codec = v.codec,
                                                )
                                            } ?: detail
                                    }
                                videoMeta.frameRate?.let { fps ->
                                    Text(
                                        "Frame Rate: $fps",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                    )
                                }
                                videoMeta.duration?.let { duration ->
                                    Text(
                                        "Duration: $duration",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                                videoMeta.bitRate?.let { bitrate ->
                                    Text(
                                        "Bit Rate: ${String.format("%.1f", bitrate / 1000000.0)} Mbps",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                                videoMeta.codec?.let { codec ->
                                    Text(
                                        "Codec: $codec",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                                pageVideoMeta?.audioSummaryLabel()?.let { audio ->
                                    Text(
                                        "Audio: $audio",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                    )
                                }
                                if (videoRot != 0) {
                                    Text(
                                        "Rotation: ${videoRot}°",
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Location information
                            Text(
                                "=== Location ===",
                                color = Color.Cyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "GPS: ${if (media.hasLocation) "Available" else "None"}",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
    
    if (showShareFormat && selectedGroup != null) {
        AlertDialog(
            onDismissRequest = { showShareFormat = false },
            title = { Text("Share") },
            text = { Text(GalleryCaptureGroups.describe(selectedGroup)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        shareMediaBatch(
                            context,
                            GalleryCaptureGroups.urisForShare(selectedGroup, GalleryCaptureGroups.ShareFormat.Jpeg),
                        )
                        showShareFormat = false
                    },
                ) { Text("JPEG") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        shareMediaBatch(
                            context,
                            GalleryCaptureGroups.urisForShare(selectedGroup, GalleryCaptureGroups.ShareFormat.Both),
                        )
                        showShareFormat = false
                    },
                ) { Text("Both") }
            },
        )
    }
    if (showFileCard && selectedGroup != null) {
        AlertDialog(
            onDismissRequest = { showFileCard = false },
            title = { Text("What this file is") },
            text = { Text(GalleryCaptureGroups.describe(selectedGroup)) },
            confirmButton = { TextButton(onClick = { showFileCard = false }) { Text("OK") } },
        )
    }
    if (undoUntil > 0L && trashStash != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Deleted") },
            text = { Text("Undo within 30 seconds to put the files back.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val stash = trashStash
                        trashStash = null
                        undoUntil = 0L
                        if (stash != null) {
                            lifecycleScope.launch {
                                withContext(Dispatchers.IO) { GalleryTrash.restore(context, stash) }
                                mediaItems = PnsMediaStoreGallery.loadIndex(context)
                                selectedMedia = mediaItems.firstOrNull()
                            }
                        }
                    },
                ) { Text("Undo") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        GalleryTrash.prune(trashStash)
                        trashStash = null
                        undoUntil = 0L
                    },
                ) { Text("Done") }
            },
        )
    }
    val compareLeft = compareUri?.let { uri -> mediaItems.firstOrNull { it.uri == uri } }
    val compareRight = selectedMedia
    val comparePairReady = compareLeft != null && compareRight != null && compareLeft.uri != compareRight.uri
    if (showComparePane && comparePairReady) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showComparePane = false }) {
            GalleryComparePane(
                left = compareLeft,
                right = compareRight,
                onClose = { showComparePane = false },
            )
        }
    }
    if (showLibraryDesk) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showLibraryDesk = false }) {
            PnsLibraryDeskScreen(
                items = mediaItems,
                selected = selectedMedia,
                compare = compareLeft,
                onBack = { showLibraryDesk = false },
                onOpenCompare = {
                    if (compareLeft != null && selectedMedia != null) {
                        showComparePane = true
                    }
                },
            )
        }
    }
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Media") },
            text = { Text("Delete this capture? You can undo for 30 seconds.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val victims = selectedGroup?.items ?: listOfNotNull(selectedMedia)
                        showDeleteConfirmation = false
                        lifecycleScope.launch {
                            trashStash =
                                withContext(Dispatchers.IO) { GalleryTrash.stashAndDelete(context, victims) }
                            undoUntil = System.currentTimeMillis() + GalleryTrash.UNDO_MS
                            mediaItems = PnsMediaStoreGallery.loadIndex(context)
                            selectedMedia = mediaItems.firstOrNull()
                        }
                    },
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

data class MediaItem(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val size: Long,
    val date: Long,
    val width: Int,
    val height: Int,
    val isVideo: Boolean,
    val isRaw: Boolean,
    val isHdr: Boolean,
    val hasLocation: Boolean,
    val cameraId: String?,
    val lens: String?,
    val focalLength: String?,
    val focalLength35mm: String? = null,
    val aperture: String?,
    val iso: Int?,
    val shutterSpeed: String?,
    val whiteBalance: String?,
    val frameRate: String?,
    val bitRate: Long?,
    val duration: String?,
    val codec: String?,
    val colorSpace: String?
)

data class ExifMetadata(
    val cameraId: String? = null,
    val lens: String? = null,
    val focalLength: String? = null,
    val focalLength35mm: String? = null,
    val aperture: String? = null,
    val iso: Int? = null,
    val shutterSpeed: String? = null,
    val whiteBalance: String? = null,
    val orientation: Int = ExifInterface.ORIENTATION_NORMAL
)

@Composable
private fun GalleryInlineVideoPlayer(uri: Uri, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                tag = uri
                setVideoURI(uri)
                setOnPreparedListener { mp ->
                    mp.isLooping = true
                    start()
                }
            }
        },
        modifier = modifier,
        update = { view ->
            if (view.tag != uri) {
                view.tag = uri
                view.setVideoURI(uri)
            }
        },
        onRelease = { view ->
            view.stopPlayback()
        },
    )
}

private fun extractExifMetadata(context: Context, uri: Uri): ExifMetadata {
    return try {
        val path = uri.toString().lowercase()
        val mime = context.contentResolver.getType(uri)?.lowercase()
        val isDng =
            path.contains(".dng") ||
                path.endsWith(".dng") ||
                mime == "image/x-adobe-dng"
        val isTiff =
            path.endsWith(".tif") ||
                path.endsWith(".tiff") ||
                mime == "image/tiff" ||
                mime == "image/x-tiff"

        Log.d("BespokeGallery", "Metadata route isDng=$isDng isTiff=$isTiff mime=$mime")

        if (isDng || isTiff) {
            // Primary path: ExifInterface can read TIFF-family tags on many builds, including
            // ISO/shutter/aperture/focal stamped by StillCaptureMetadata.
            val exifFirst = readExifInterfaceMetadata(context, uri)
            val descMeta = readDescriptionMetadata(context, uri)
            val mergedPrimary = exifFirst.mergeMissing(descMeta)
            if (isTiff) {
                // Avoid heavyweight full-file TIFF parse on large 16-bit exports. For TIFF we rely on
                // ExifInterface + MediaStore description metadata written at save time.
                return mergedPrimary
            }
            if (exifFirst.hasCompletePrimaryCaptureFields()) {
                return mergedPrimary
            }

            // Fallback path: raw TIFF parser for DNG/TIFF files where ExifInterface is sparse.
            try {
                val tiffReader = DngTiffReader()
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val tiffMetadata = tiffReader.readMetadata(inputStream)
                    Log.d(
                        "BespokeGallery",
                        "TIFF fallback metadata - Aperture: ${tiffMetadata.aperture}, ISO: ${tiffMetadata.iso}, Exposure: ${tiffMetadata.exposureTime}, Focal: ${tiffMetadata.focalLength}",
                    )
                    ExifMetadata(
                        cameraId = tiffMetadata.model ?: exifFirst.cameraId,
                        aperture = tiffMetadata.aperture?.toString() ?: exifFirst.aperture,
                        iso = tiffMetadata.iso?.toInt(),
                        focalLength = tiffMetadata.focalLength?.toString() ?: exifFirst.focalLength,
                        focalLength35mm = exifFirst.focalLength35mm,
                        shutterSpeed = tiffMetadata.exposureTime?.toString() ?: exifFirst.shutterSpeed,
                        whiteBalance = exifFirst.whiteBalance,
                        orientation =
                            if (tiffMetadata.exifOrientation != ExifInterface.ORIENTATION_NORMAL) {
                                tiffMetadata.exifOrientation
                            } else {
                                exifFirst.orientation
                            },
                    ).mergeMissing(descMeta)
                } ?: mergedPrimary
            } catch (e: Exception) {
                Log.e("BespokeGallery", "Error reading DNG metadata", e)
                mergedPrimary
            }
        } else {
            readExifInterfaceMetadata(context, uri).mergeMissing(readDescriptionMetadata(context, uri))
        }
    } catch (e: Exception) {
        Log.e("BespokeGallery", "Error extracting EXIF metadata", e)
        ExifMetadata()
    }
}

private fun ExifMetadata.hasPrimaryCaptureFields(): Boolean =
    !focalLength.isNullOrBlank() ||
        !aperture.isNullOrBlank() ||
        iso != null ||
        !shutterSpeed.isNullOrBlank()

private fun ExifMetadata.hasCompletePrimaryCaptureFields(): Boolean =
    !focalLength.isNullOrBlank() &&
        !aperture.isNullOrBlank() &&
        iso != null &&
        !shutterSpeed.isNullOrBlank()

private fun ExifMetadata.mergeMissing(other: ExifMetadata): ExifMetadata =
    ExifMetadata(
        cameraId = cameraId ?: other.cameraId,
        lens = lens ?: other.lens,
        focalLength = focalLength ?: other.focalLength,
        focalLength35mm = focalLength35mm ?: other.focalLength35mm,
        aperture = aperture ?: other.aperture,
        iso = iso ?: other.iso,
        shutterSpeed = shutterSpeed ?: other.shutterSpeed,
        whiteBalance = whiteBalance ?: other.whiteBalance,
        orientation =
            if (orientation != ExifInterface.ORIENTATION_NORMAL) {
                orientation
            } else {
                other.orientation
            },
    )

private fun readExifInterfaceMetadata(context: Context, uri: Uri): ExifMetadata {
    return context.contentResolver.openInputStream(uri)?.use { inputStream ->
        val exif = ExifInterface(inputStream)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        ExifMetadata(
            cameraId = exif.getAttribute(ExifInterface.TAG_MODEL),
            lens = exif.getAttribute("LensModel"),
            focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH),
            focalLength35mm = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM),
            aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER),
            iso =
                exif.getAttribute("PhotographicSensitivity")?.toIntOrNull()
                    ?: exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)?.toIntOrNull(),
            shutterSpeed = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME),
            whiteBalance = exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE),
            orientation = orientation,
        )
    } ?: ExifMetadata()
}

private fun readDescriptionMetadata(context: Context, uri: Uri): ExifMetadata {
    return runCatching {
        val projection = arrayOf(MediaStore.Images.Media.DESCRIPTION)
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            val descIdx = c.getColumnIndex(MediaStore.Images.Media.DESCRIPTION)
            if (descIdx < 0 || !c.moveToFirst()) return@use ExifMetadata()
            val desc = c.getString(descIdx) ?: return@use ExifMetadata()
            ExifMetadata(
                focalLength = Regex("""\bfocalMm=([0-9]+(?:\.[0-9]+)?)""").find(desc)?.groupValues?.getOrNull(1),
                focalLength35mm =
                    Regex("""\bfocal(?:35mm|EqMm)=([0-9]+(?:\.[0-9]+)?)""")
                        .find(desc)
                        ?.groupValues
                        ?.getOrNull(1),
                aperture = Regex("""\baperture=([0-9]+(?:\.[0-9]+)?)""").find(desc)?.groupValues?.getOrNull(1),
                iso = Regex("""\biso=(\d+)""").find(desc)?.groupValues?.getOrNull(1)?.toIntOrNull(),
                shutterSpeed =
                    Regex("""\bexpNs=(\d+)""").find(desc)?.groupValues?.getOrNull(1)?.let { ns ->
                        ns.toLongOrNull()?.let { "%.6f".format(it / 1_000_000_000.0) }
                    },
            )
        } ?: ExifMetadata()
    }.getOrDefault(ExifMetadata())
}

private fun shareMediaBatch(context: Context, uris: List<Uri>) {
    if (uris.isEmpty()) return
    Log.i("PNS.Gallery", "batchShare count=${uris.size}")
    PnsAdbLog.i(context, "gallery batchShare count=${uris.size}")
    SharingManager.shareUris(context, uris, "Share ${uris.size} items")
}
