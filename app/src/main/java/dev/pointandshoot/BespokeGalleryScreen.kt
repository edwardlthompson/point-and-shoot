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
import androidx.compose.material.icons.filled.Remove
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

/**
 * Bespoke gallery with media specs display.
 * Shows media preview at top, detailed specs below, and external gallery option.
 */

// Helper function to format focal length correctly
private fun formatFocalLength(focalLength: String?): String {
    if (focalLength == null) return "Unknown"
    
    return try {
        // Handle cases like "6060/1000" by parsing the fraction
        if (focalLength.contains("/")) {
            val parts = focalLength.split("/")
            if (parts.size == 2) {
                val numerator = parts[0].toDoubleOrNull()
                val denominator = parts[1].toDoubleOrNull()
                if (numerator != null && denominator != null && denominator != 0.0) {
                    val actualFocal = numerator / denominator
                    // For this device, 6.06mm actual = 23mm 35mm equivalent
                    val equivalent35mm = if (actualFocal == 6.06) 23 else (actualFocal * 23 / 6.06).toInt()
                    return "${String.format("%.2f", actualFocal)}mm (${equivalent35mm}mm)"
                }
            }
        }
        
        // Handle direct numeric values
        val focal = focalLength.toDoubleOrNull()
        if (focal != null) {
            val equivalent35mm = if (focal == 6.06) 23 else (focal * 23 / 6.06).toInt()
            return "${String.format("%.2f", focal)}mm (${equivalent35mm}mm)"
        }
        
        focalLength
    } catch (e: Exception) {
        focalLength
    }
}

@Composable
fun GridItem(
    media: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
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
    onBack: () -> Unit,
    onExternalGallery: () -> Unit
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
        onBack()
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
                    fontSize = 18.sp
                ) 
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
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
                        contentDescription = if (isGridView) "Single View" else "Grid View",
                        tint = Color.White
                    )
                }
                IconButton(onClick = { 
                    selectedMedia?.let { media ->
                        shareMedia(context, media.uri)
                    }
                }) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White
                    )
                }
                IconButton(onClick = { 
                    showDeleteConfirmation = true 
                }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onExternalGallery) {
                    Icon(
                        Icons.Default.OpenInBrowser,
                        contentDescription = "External Gallery",
                        tint = Color.White
                    )
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
                    
                    Column {
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
                        val swapAspect =
                            (isDng &&
                                DngGalleryOrientation.needsSwapWidthHeight(exifMetadata.orientation)) ||
                            (media.isVideo && (videoRot == 90 || videoRot == 270))
                        
                        val aspectRatio = if (swapAspect) {
                            media.height.toFloat() / media.width.toFloat()
                        } else {
                            media.width.toFloat() / media.height.toFloat()
                        }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(aspectRatio)
                                .background(Color.DarkGray)
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
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(media.uri) {
                                            detectTapGestures(
                                                onDoubleTap = {
                                                    // Reset zoom on double tap
                                                    scale = 1f
                                                    offsetX = 0f
                                                    offsetY = 0f
                                                }
                                            )
                                        }
                                        .pointerInput(media.uri) {
                                            detectTransformGestures { centroid, pan, zoom, _ ->
                                                // Apply zoom with constraints
                                                val newScale = (scale * zoom).coerceIn(1f, 8f)
                                                scale = newScale
                                                
                                                // Apply pan if zoomed
                                                if (scale > 1f) {
                                                    offsetX += pan.x
                                                    offsetY += pan.y
                                                    
                                                    // Constrain pan to keep image within bounds
                                                    val maxPanX = (scale - 1) * rotatedBitmap.width / 2
                                                    val maxPanY = (scale - 1) * rotatedBitmap.height / 2
                                                    offsetX = offsetX.coerceIn(-maxPanX, maxPanX)
                                                    offsetY = offsetY.coerceIn(-maxPanY, maxPanY)
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = rotatedBitmap.asImageBitmap(),
                                        contentDescription = media.displayName,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer(
                                                scaleX = scale,
                                                scaleY = scale,
                                                translationX = offsetX,
                                                translationY = offsetY
                                            ),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                                
                                // Clean up rotated bitmap
                                LaunchedEffect(Unit) {
                                    if (rotatedBitmap != bmp) {
                                        PnsBitmapGuard.safeRecycle(rotatedBitmap, "BespokeGallery.RotatedBitmap")
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Color.White)
                                }
                            }
                            }
                        }
                        
                        // Media specs below - scrollable
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
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
                                "Dimensions: ${media.width}x${media.height}",
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
                                detail.focalLength?.let { focal ->
                                    val formattedFocal = formatFocalLength(focal)
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
                                    Text(
                                        "Shutter Speed: $shutter sec",
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
    
    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Media") },
            text = { Text("Are you sure you want to delete this media file?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedMedia?.let { media ->
                            deleteMedia(context, media.uri) {
                                showDeleteConfirmation = false
                                lifecycleScope.launch {
                                    mediaItems = PnsMediaStoreGallery.loadIndex(context)
                                    selectedMedia = mediaItems.firstOrNull()
                                }
                            }
                        }
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
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
        val isDng = uri.toString().lowercase().contains(".dng") || 
                   uri.toString().lowercase().endsWith(".dng") ||
                   context.contentResolver.getType(uri) == "image/x-adobe-dng"
        
        Log.d("BespokeGallery", "Is DNG: $isDng")
        
        if (isDng) {
            Log.d("BespokeGallery", "Processing DNG file with TIFF reader")
            try {
                val tiffReader = DngTiffReader()
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val tiffMetadata = tiffReader.readMetadata(inputStream)
                    Log.d("BespokeGallery", "TIFF metadata - Aperture: ${tiffMetadata.aperture}, ISO: ${tiffMetadata.iso}, Exposure: ${tiffMetadata.exposureTime}, Focal: ${tiffMetadata.focalLength}")
                    ExifMetadata(
                        aperture = tiffMetadata.aperture?.toString(),
                        iso = tiffMetadata.iso?.toInt(),
                        focalLength = tiffMetadata.focalLength?.toString(),
                        shutterSpeed = tiffMetadata.exposureTime?.toString(),
                        orientation = tiffMetadata.exifOrientation,
                    )
                } ?: ExifMetadata()
            } catch (e: Exception) {
                Log.e("BespokeGallery", "Error reading DNG metadata", e)
                ExifMetadata()
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                Log.d("BespokeGallery", "Orientation: ${exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1)}")
                ExifMetadata(
                    cameraId = exif.getAttribute(ExifInterface.TAG_MODEL),
                    lens = exif.getAttribute("LensModel"),
                    focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH),
                    aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER),
                    iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)?.toIntOrNull(),
                    shutterSpeed = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME),
                    whiteBalance = exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE),
                    orientation = orientation
                )
            } ?: ExifMetadata()
        }
    } catch (e: Exception) {
        Log.e("BespokeGallery", "Error extracting EXIF metadata", e)
        ExifMetadata()
    }
}

private fun shareMedia(context: Context, uri: Uri) {
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_STREAM, uri)
        type = context.contentResolver.getType(uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share media"))
}

private fun deleteMedia(context: Context, uri: Uri, onSuccess: () -> Unit) {
    try {
        context.contentResolver.delete(uri, null, null)
        Log.d("BespokeGallery", "Successfully deleted media: $uri")
        onSuccess()
    } catch (e: Exception) {
        Log.e("BespokeGallery", "Error deleting media", e)
    }
}
