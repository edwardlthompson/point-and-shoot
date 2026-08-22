@file:Suppress("FunctionNaming", "MagicNumber")

package dev.pointandshoot

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Real side-by-side culling — two frames on screen at once. */
@Composable
fun GalleryComparePane(
    left: MediaItem,
    right: MediaItem,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var leftBmp by remember(left.uri) { mutableStateOf<Bitmap?>(null) }
    var rightBmp by remember(right.uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(left.uri, right.uri) {
        leftBmp = withContext(Dispatchers.IO) { loadGalleryThumbnail(context, left.uri, 720) }
        rightBmp = withContext(Dispatchers.IO) { loadGalleryThumbnail(context, right.uri, 720) }
    }
    DisposableEffect(left.uri, right.uri) {
        onDispose {
            PnsBitmapGuard.safeRecycle(leftBmp, "compare.left")
            PnsBitmapGuard.safeRecycle(rightBmp, "compare.right")
        }
    }
    Column(
        modifier = modifier.fillMaxSize().background(Color.Black),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Compare", color = Color.White, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Close", color = PnsColors.PhotoOrange) }
        }
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            CompareCell(left, leftBmp, Modifier.weight(1f).fillMaxHeight())
            Box(Modifier.fillMaxHeight().padding(horizontal = 1.dp).background(Color.DarkGray).then(Modifier.fillMaxHeight()))
            CompareCell(right, rightBmp, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun CompareCell(item: MediaItem, bmp: Bitmap?, modifier: Modifier) {
    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = item.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        Text(
            text = item.displayName,
            color = Color.White,
            fontSize = 11.sp,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(6.dp),
        )
    }
}

fun galleryUriKey(uri: Uri): String = uri.toString()
