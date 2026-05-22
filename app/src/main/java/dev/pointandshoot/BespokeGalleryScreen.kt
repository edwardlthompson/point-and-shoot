package dev.pointandshoot

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.provider.MediaStore
import android.media.ExifInterface
import android.graphics.Matrix
import android.graphics.Bitmap
import android.app.ActivityManager
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
    
    LaunchedEffect(media.uri) {
        thumbnail = loadGalleryThumbnail(context, media.uri, 120)
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
    ) {
        val currentThumbnail = thumbnail
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

// Memory monitoring and bitmap cleanup utilities
private fun logMemoryUsage(context: Context, tag: String = "BespokeGallery") {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)
    
    val usedMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)
    val maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024)
    val availableMemory = memoryInfo.availMem / (1024 * 1024)
    
    Log.d(tag, "Memory - Used: ${usedMemory}MB, Max: ${maxMemory}MB, Available: ${availableMemory}MB")
    
    if (usedMemory > maxMemory * 0.8) {
        Log.w(tag, "High memory usage detected: ${usedMemory}MB / ${maxMemory}MB")
    }
}

private fun safeRecycleBitmap(bitmap: Bitmap?, tag: String = "BespokeGallery"): Boolean {
    return try {
        if (bitmap != null && !bitmap.isRecycled) {
            Log.d(tag, "Recycling bitmap: ${bitmap.width}x${bitmap.height}, size: ${bitmap.byteCount / 1024}KB")
            bitmap.recycle()
            true
        } else {
            false
        }
    } catch (e: Exception) {
        Log.e(tag, "Error recycling bitmap", e)
        false
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
    
    // Memory monitoring on screen entry
    LaunchedEffect(Unit) {
        logMemoryUsage(context, "BespokeGallery.Entry")
        memoryProfiler.startProfiling(5000L)
        memoryProfiler.logEvent("Gallery screen opened")
    }
    
    // Cleanup on screen exit
    DisposableEffect(Unit) {
        onDispose {
            Log.d("BespokeGallery", "Cleaning up gallery resources")
            selectedBitmap?.let { safeRecycleBitmap(it, "BespokeGallery.Cleanup") }
            selectedBitmap = null
            logMemoryUsage(context, "BespokeGallery.Exit")
            
            // Stop memory profiling and save report
            memoryProfiler.logEvent("Gallery screen closed")
            val report = memoryProfiler.stopProfiling()
            try {
                val reportPath = memoryProfiler.saveReportToFile(report)
                Log.i("BespokeGallery", "Memory profiling report saved: $reportPath")
            } catch (e: Exception) {
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
                mediaItems = loadMediaItems(context)
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
            lifecycleScope.launch {
                selectedBitmap?.let { oldBitmap ->
                    safeRecycleBitmap(oldBitmap, "BespokeGallery.BitmapChange")
                    memoryProfiler.logEvent("Recycled previous bitmap")
                }
                
                Log.d("BespokeGallery", "Loading bitmap for: ${media.displayName}")
                memoryProfiler.logEvent("Loading bitmap: ${media.displayName}")
                logMemoryUsage(context, "BespokeGallery.BeforeLoad")
                
                selectedBitmap = loadGalleryThumbnail(context, media.uri, 800)
                
                selectedBitmap?.let { bitmap ->
                    Log.d("BespokeGallery", "Loaded bitmap: ${bitmap.width}x${bitmap.height}, size: ${bitmap.byteCount / 1024}KB")
                    memoryProfiler.logEvent("Loaded bitmap: ${bitmap.width}x${bitmap.height}, ${bitmap.byteCount / 1024}KB")
                }
                
                logMemoryUsage(context, "BespokeGallery.AfterLoad")
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
                        // Preview at top with zoom and pan
                        val exifMetadata = extractExifMetadata(context, media.uri)
                        val needsRotation = exifMetadata.orientation in listOf(
                            ExifInterface.ORIENTATION_ROTATE_90, 
                            ExifInterface.ORIENTATION_ROTATE_270
                        )
                        val isDng = media.displayName.lowercase().endsWith(".dng")
                        val needsDngRotation = isDng
                        val totalRotationNeeded = needsRotation || needsDngRotation
                        
                        val aspectRatio = if (totalRotationNeeded) {
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
                            val bmp = if (selectedMedia?.uri == media.uri) selectedBitmap else null
                            if (bmp != null && !bmp.isRecycled) {
                                val matrix = Matrix()
                                when (exifMetadata.orientation) {
                                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                                }
                                
                                if (media.displayName.lowercase().endsWith(".dng")) {
                                    matrix.postRotate(90f)
                                }
                                val rotatedBitmap = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                                
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
                                        safeRecycleBitmap(rotatedBitmap, "BespokeGallery.RotatedBitmap")
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
                            
                            // Camera metadata for images
                            if (!media.isVideo) {
                                Text(
                                    "=== Camera Metadata ===",
                                    color = Color.Cyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                media.cameraId?.let { camera ->
                                    Text(
                                        "Camera: $camera",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                                media.lens?.let { lens ->
                                    Text(
                                        "Lens: $lens",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                                media.focalLength?.let { focal ->
                                    val formattedFocal = formatFocalLength(focal)
                                    Text(
                                        "Focal Length: $formattedFocal",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                                media.aperture?.let { aperture ->
                                    Text(
                                        "Aperture: f/$aperture",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                                media.iso?.let { iso ->
                                    Text(
                                        "ISO: $iso",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                                media.shutterSpeed?.let { shutter ->
                                    Text(
                                        "Shutter Speed: $shutter sec",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                                media.whiteBalance?.let { wb ->
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
                                
                                media.duration?.let { duration ->
                                    Text(
                                        "Duration: $duration",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                                media.frameRate?.let { fps ->
                                    Text(
                                        "Frame Rate: $fps fps",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                                media.bitRate?.let { bitrate ->
                                    Text(
                                        "Bit Rate: ${String.format("%.1f", bitrate / 1000000.0)} Mbps",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                                media.codec?.let { codec ->
                                    Text(
                                        "Codec: $codec",
                                        color = Color.White,
                                        fontSize = 12.sp
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
                                    mediaItems = loadMediaItems(context)
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

private suspend fun loadMediaItems(context: Context): List<MediaItem> = withContext(Dispatchers.IO) {
    val mediaItems = mutableListOf<MediaItem>()
    
    val startTime = System.currentTimeMillis()
    try {
        Log.d("BespokeGallery", "=== Loading media items ===")
        
        val projection = arrayOf(
            android.provider.MediaStore.MediaColumns._ID,
            android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
            android.provider.MediaStore.MediaColumns.MIME_TYPE,
            android.provider.MediaStore.MediaColumns.SIZE,
            android.provider.MediaStore.MediaColumns.DATE_MODIFIED,
            android.provider.MediaStore.MediaColumns.WIDTH,
            android.provider.MediaStore.MediaColumns.HEIGHT
        )
        
        val selection = "${android.provider.MediaStore.MediaColumns.MIME_TYPE} IS NOT NULL AND " +
                        "${android.provider.MediaStore.MediaColumns.SIZE} > 0 AND (" +
                        "${android.provider.MediaStore.MediaColumns.MIME_TYPE} LIKE 'image/%' OR " +
                        "${android.provider.MediaStore.MediaColumns.MIME_TYPE} LIKE 'video/%'" +
                        ")"
        
        val sortOrder = "${android.provider.MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        
        context.contentResolver.query(
            android.provider.MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.SIZE)
            val dateColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATE_MODIFIED)
            val widthColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.HEIGHT)
            
            Log.d("BespokeGallery", "Query returned ${cursor.count} results")
            
            var itemCount = 0
            val maxItems = 500
            while (cursor.moveToNext() && itemCount < maxItems) {
                val id = cursor.getLong(idColumn)
                val uri = Uri.withAppendedPath(android.provider.MediaStore.Files.getContentUri("external"), id.toString())
                val displayName = cursor.getString(nameColumn)
                val mimeType = cursor.getString(mimeColumn)
                val size = cursor.getLong(sizeColumn)
                val date = cursor.getLong(dateColumn)
                val width = cursor.getInt(widthColumn).takeIf { it > 0 } ?: 1920
                val height = cursor.getInt(heightColumn).takeIf { it > 0 } ?: 1080
                
                if (mediaItems.size < 5) {
                    Log.d("BespokeGallery", "Found file: $displayName, MIME: $mimeType, Size: ${size / 1024}KB")
                }
                
                val isVideo = mimeType?.startsWith("video/") == true
                val isRaw = displayName.lowercase().endsWith(".dng") || 
                           displayName.lowercase().endsWith(".raw") ||
                           displayName.lowercase().endsWith(".cr2") ||
                           displayName.lowercase().endsWith(".nef")
                
                val exifMetadata = if (!isVideo) extractExifMetadata(context, uri) else ExifMetadata()
                
                val mediaItem = MediaItem(
                    uri = uri,
                    displayName = displayName,
                    mimeType = mimeType,
                    size = size,
                    date = date,
                    width = width,
                    height = height,
                    isVideo = isVideo,
                    isRaw = isRaw,
                    isHdr = false,
                    hasLocation = false,
                    cameraId = exifMetadata.cameraId,
                    lens = exifMetadata.lens,
                    focalLength = exifMetadata.focalLength,
                    aperture = exifMetadata.aperture,
                    iso = exifMetadata.iso,
                    shutterSpeed = exifMetadata.shutterSpeed,
                    whiteBalance = exifMetadata.whiteBalance,
                    frameRate = null,
                    bitRate = null,
                    duration = null,
                    codec = null,
                    colorSpace = if (isRaw) "ProPhoto RGB" else "sRGB"
                )
                
                mediaItems.add(mediaItem)
                itemCount++
            }
        }
    } catch (e: Exception) {
        Log.e("BespokeGallery", "Error loading media items", e)
    }
    
    val loadTime = System.currentTimeMillis() - startTime
    Log.d("BespokeGallery", "Loaded ${mediaItems.size} media items in ${loadTime}ms")
    
    mediaItems
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
                        orientation = ExifInterface.ORIENTATION_NORMAL
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
