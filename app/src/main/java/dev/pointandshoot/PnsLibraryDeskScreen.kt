@file:Suppress("FunctionNaming", "MagicNumber", "CyclomaticComplexMethod", "ForEachOnRange", "MaxLineLength")

package dev.pointandshoot

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Tabs =
    listOf(
        "Days",
        "Cull",
        "Library",
        "Travel",
        "Finish",
        "Offload",
        "Privacy",
    )

@Composable
fun PnsLibraryDeskScreen(
    items: List<MediaItem>,
    selected: MediaItem?,
    compare: MediaItem?,
    onBack: () -> Unit,
    onOpenCompare: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var keyword by remember { mutableStateOf("") }
    var collection by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var publishPassword by remember { mutableStateOf("") }
    val groups = remember(items) { GalleryCaptureGroups.group(items) }
    val days = remember(items) { GalleryLibrary.groupByDay(items) }
    val travel = remember(items) { GalleryLibrary.travelBuckets(items) }
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
            if (tree == null) return@rememberLauncherForActivityResult
            scope.launch {
                val n =
                    withContext(Dispatchers.IO) {
                        GalleryFolderExport.copyToday(context, tree, items, System.currentTimeMillis() / 1000L)
                    }
                status = "Exported $n files from today"
            }
        }
    val vaultLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
            if (tree == null) return@rememberLauncherForActivityResult
            scope.launch {
                val keepers =
                    items.filter { !GalleryLibrary.load(context, galleryUriKey(it.uri)).rejected }
                val report = withContext(Dispatchers.IO) { GalleryVault.offload(context, tree, keepers) }
                status = report.message
            }
        }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Text("Library desk", style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
        if (status.isNotBlank()) {
            Text(status, color = PnsColors.PhotoOrange, style = MaterialTheme.typography.bodySmall)
        }
        ScrollableTabRow(selectedTabIndex = tab, containerColor = Color.Black, contentColor = Color.White) {
            Tabs.forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
            }
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (tab) {
                0 -> {
                    Text("Contact sheet by day", color = Color.White)
                    days.forEach { (day, dayItems) ->
                        Text(
                            "$day · ${dayItems.size} files",
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )
                    }
                    if (days.isEmpty()) Text("No captures yet.", color = Color.Gray)
                }
                1 -> {
                    Text("Stack culling — keep, reject, pick hero", color = Color.White)
                    val stack = groups.firstOrNull { it.kind == GalleryCaptureGroups.Kind.Stack }
                    if (stack == null) {
                        Text("No BKT / NightScape / HDR stack in this roll.", color = Color.Gray)
                    } else {
                        stack.items.forEach { item ->
                            val meta = GalleryLibrary.load(context, galleryUriKey(item.uri))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    item.displayName.take(28),
                                    color = if (meta.rejected) Color.Gray else Color.White,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedButton(
                                    onClick = {
                                        GalleryLibrary.toggleReject(context, galleryUriKey(item.uri))
                                        status = "Updated ${item.displayName}"
                                    },
                                ) { Text(if (meta.rejected) "Keep" else "Reject") }
                                OutlinedButton(
                                    onClick = {
                                        GalleryLibrary.setHero(
                                            context,
                                            stack.items.map { galleryUriKey(it.uri) },
                                            galleryUriKey(item.uri),
                                        )
                                        status = "Hero ${item.displayName}"
                                    },
                                ) { Text(if (meta.hero) "Hero" else "Make hero") }
                            }
                        }
                    }
                    Button(onClick = onOpenCompare, enabled = selected != null && compare != null) {
                        Text("Open side-by-side compare")
                    }
                }
                2 -> {
                    Text("Keywords and collections", color = Color.White)
                    val key = selected?.let { galleryUriKey(it.uri) }
                    if (key == null) {
                        Text("Open a file in gallery first.", color = Color.Gray)
                    } else {
                        val meta = GalleryLibrary.load(context, key)
                        Text("Rating ${meta.rating}/5 · ${meta.keywords.joinToString()}", color = Color.White)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (0..5).forEach { star ->
                                OutlinedButton(onClick = { GalleryLibrary.setRating(context, key, star) }) {
                                    Text("$star")
                                }
                            }
                        }
                        OutlinedTextField(keyword, { keyword = it }, label = { Text("Keyword") })
                        Button(
                            onClick = {
                                GalleryLibrary.addKeyword(context, key, keyword)
                                keyword = ""
                                status = "Keyword saved"
                            },
                        ) { Text("Add keyword") }
                        OutlinedTextField(collection, { collection = it }, label = { Text("Collection / job") })
                        Button(
                            onClick = {
                                GalleryLibrary.addCollection(context, key, collection)
                                collection = ""
                                status = "Collection saved"
                            },
                        ) { Text("Add to collection") }
                        if (selected.isRaw) {
                            Button(
                                onClick = {
                                    val file = DngXmpSidecar.write(context, selected.displayName, meta)
                                    status = if (file != null) "XMP sidecar ${file.name}" else "XMP skipped"
                                },
                            ) { Text("Write DNG XMP sidecar") }
                        }
                    }
                    Text(
                        "Collections: ${GalleryLibrary.allCollections(context).joinToString().ifBlank { "none" }}",
                        color = Color.Gray,
                    )
                    if (PnsProductPrefs.peopleAlbumsOptIn(context)) {
                        Text("People albums: use a keyword like person:ada — on-device only.", color = Color.Gray)
                    }
                }
                3 -> {
                    Text("Travel albums (files that already have GPS)", color = Color.White)
                    if (travel.isEmpty()) {
                        Text("No geotagged files. Turn Precise or Coarse geotag on for new shots.", color = Color.Gray)
                    } else {
                        travel.forEach { (day, dayItems) ->
                            Text("$day · ${dayItems.size} mapped files", color = Color.White)
                        }
                    }
                    Text("Syncthing path example: ${items.firstOrNull()?.let { SyncthingDcimLayout.relativePath(it) } ?: "-"}", color = Color.Gray)
                }
                4 -> {
                    Text("Video finish + look bake", color = Color.White)
                    val video = selected?.takeIf { it.isVideo }
                    val still = selected?.takeIf { !it.isVideo && !it.isRaw }
                    if (video != null) {
                        val marks = GalleryVideoFinish.chapterStartsMs()
                        Text("Chapters: ${if (marks.isEmpty()) "none this session" else marks.joinToString()}", color = Color.White)
                        Button(
                            onClick = {
                                scope.launch {
                                    val r =
                                        withContext(Dispatchers.IO) {
                                            GalleryVideoFinish.extractFrame(context, video.uri, marks.firstOrNull() ?: 0L)
                                        }
                                    status = r.message
                                }
                            },
                        ) { Text("Extract frame") }
                        if (marks.size >= 2) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        val r =
                                            withContext(Dispatchers.IO) {
                                                GalleryVideoFinish.trimToFile(context, video.uri, marks.first(), marks.last())
                                            }
                                        status = r.message
                                    }
                                },
                            ) { Text("Trim first→last chapter") }
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    val lut = PnsActiveLook.resolveLut3d(context, LutCatalog.None.name, null)
                                    val r = withContext(Dispatchers.IO) { LogLutBakeExport.bakeVideoProxy(context, video.uri, lut) }
                                    status = r.message
                                    r.uri?.let { SharingManager.shareSingle(context, it, "Share baked proxy") }
                                }
                            },
                        ) { Text("Bake LUT proxy zip") }
                    } else if (still != null) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val lut = PnsActiveLook.resolveLut3d(context, LutCatalog.PnsCinematic.name, null)
                                    val r = LogLutBakeExport.bakeStill(context, still.uri, lut, still.displayName)
                                    status = r.message
                                }
                            },
                        ) { Text("Bake still LUT JPEG") }
                    } else {
                        Text("Select a video or JPEG in gallery.", color = Color.Gray)
                    }
                }
                5 -> {
                    Text("Offload and publish", color = Color.White)
                    Button(onClick = { exportLauncher.launch(null) }) { Text("Export today's roll") }
                    Button(onClick = { vaultLauncher.launch(null) }) { Text("Vault: copy keepers to folder") }
                    OutlinedTextField(
                        PnsProductPrefs.publishUrl(context),
                        { PnsProductPrefs.setPublishUrl(context, it) },
                        label = { Text("https:// Immich or WebDAV URL") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        publishPassword,
                        { publishPassword = it },
                        label = { Text("API key or user:password (not backed up)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { PnsProductPrefs.setPublishKind(context, GalleryPublish.Kind.WebDav.id) },
                        ) { Text("WebDAV") }
                        OutlinedButton(
                            onClick = { PnsProductPrefs.setPublishKind(context, GalleryPublish.Kind.Immich.id) },
                        ) { Text("Immich") }
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                val r =
                                    withContext(Dispatchers.IO) {
                                        GalleryPublish.publish(context, items.take(12), publishPassword)
                                    }
                                status = r.message
                            }
                        },
                    ) { Text("Publish up to 12 files") }
                    Text("LAN proofing: http://<phone>:${LanMediaTransferServer.DEFAULT_PORT}/proofing", color = Color.Gray)
                }
                else -> {
                    Text("Redact, evidence, bug pack", color = Color.White)
                    if (selected != null && !selected.isRaw && !selected.isVideo) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val uri = GalleryRedact.redactCopy(context, selected.uri, emptyList(), selected.displayName)
                                    if (uri != null) {
                                        SharingManager.shareSingle(context, uri, "Share redacted copy")
                                        status = "Redacted copy shared"
                                    } else {
                                        status = "Redact failed"
                                    }
                                }
                            },
                        ) { Text("Redact-before-share") }
                    }
                    if (selected != null) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val receipt =
                                        withContext(Dispatchers.IO) { GalleryEvidence.receipt(context, selected) }
                                    status = receipt?.text() ?: "Hash failed"
                                    if (receipt != null) {
                                        Toast.makeText(context, "SHA ${receipt.sha256.take(12)}…", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                        ) { Text("Evidence SHA-256") }
                    }
                    Button(
                        onClick = {
                            val pack = PnsBugReportPack.write(context)
                            val uri =
                                androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    SharingManager.FILE_PROVIDER_AUTHORITY,
                                    pack,
                                )
                            SharingManager.shareSingle(context, uri, "Share bug pack")
                            status = "Bug pack ready"
                        },
                    ) { Text("Share redacted bug pack") }
                    val last = PnsLastGoodSession.formatHint(PnsLastGoodSession.load(context))
                    Text(last ?: "No last-good session yet.", color = Color.Gray)
                    Text(
                        "Thermal / endurance: ${PnsPowerProfile.load(context).label}",
                        color = Color.Gray,
                    )
                }
            }
        }
    }
}
