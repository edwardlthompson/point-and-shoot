package dev.pointandshoot

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.pointandshoot.fleet.CameraCapabilityCatalogBuilder
import dev.pointandshoot.fleet.FleetDeviceMatrix
import dev.pointandshoot.fleet.FleetDeviceMatrixStore
import org.json.JSONObject

/** Hub menu row for [ProbeHubSearch.buildIndex]. */
data class ProbeHubMenuEntry(
    val title: String,
    val section: String,
)

sealed class ProbeHubSearchPick {
    data class HubMenu(val title: String) : ProbeHubSearchPick()

    data class CatalogFeature(val catalogId: String, val displayName: String) : ProbeHubSearchPick()

    data class ChromeSetting(val hit: ChromeSettingSearchHit) : ProbeHubSearchPick()
}

data class ProbeHubSearchHit(
    val title: String,
    val subtitle: String,
    val keywords: String,
    val kindLabel: String,
    val pick: ProbeHubSearchPick,
)

/** Engineering hub search across menu entries, capability catalog, and chrome settings (Milestone **17.4**). */
object ProbeHubSearch {
    fun buildIndex(
        context: Context,
        hubMenuEntries: List<ProbeHubMenuEntry>,
    ): List<ProbeHubSearchHit> {
        val hits = mutableListOf<ProbeHubSearchHit>()
        for (entry in hubMenuEntries) {
            hits +=
                ProbeHubSearchHit(
                    title = entry.title,
                    subtitle = entry.section,
                    keywords = "${entry.title} ${entry.section} probe hub menu".lowercase(),
                    kindLabel = "Hub",
                    pick = ProbeHubSearchPick.HubMenu(entry.title),
                )
        }
        catalogHits(context).forEach { hits += it }
        for (chrome in buildChromeSettingsSearchIndex()) {
            hits +=
                ProbeHubSearchHit(
                    title = chrome.title,
                    subtitle = "Settings · ${chrome.subtitle}",
                    keywords = "${chrome.title} ${chrome.subtitle} ${chrome.keywords} settings chrome",
                    kindLabel = "Setting",
                    pick = ProbeHubSearchPick.ChromeSetting(chrome),
                )
        }
        return hits
    }

    private fun catalogHits(context: Context): List<ProbeHubSearchHit> {
        val root =
            FleetDeviceMatrixStore.loadValid(context.applicationContext)
                ?: runCatching {
                    val f = FleetDeviceMatrixStore.matrixFile(context.applicationContext)
                    if (f.exists()) JSONObject(f.readText()) else null
                }.getOrNull()
                ?: return emptyList()
        val withCatalog =
            if (root.has(FleetDeviceMatrix.KEY_CAPABILITY_CATALOG)) {
                root
            } else {
                CameraCapabilityCatalogBuilder.attachTo(root)
            }
        val arr = withCatalog.optJSONArray(FleetDeviceMatrix.KEY_CAPABILITY_CATALOG) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val row = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = row.optString("id")
            val name = row.optString("displayName")
            ProbeHubSearchHit(
                title = name,
                subtitle = "Catalog · ${row.optString("category")}",
                keywords =
                    "$id $name ${row.optString("category")} ${row.optString("sourceLayer")} catalog feature".lowercase(),
                kindLabel = "Feature",
                pick = ProbeHubSearchPick.CatalogFeature(id, name),
            )
        }
    }

    fun filter(index: List<ProbeHubSearchHit>, query: String): List<ProbeHubSearchHit> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return index.filter { hit ->
            hit.title.lowercase().contains(q) ||
                hit.subtitle.lowercase().contains(q) ||
                hit.keywords.contains(q) ||
                hit.keywords.split(' ').any { token -> token.startsWith(q) || q.startsWith(token) }
        }
    }
}

@Composable
fun ProbeHubSearchResults(
    query: String,
    index: List<ProbeHubSearchHit>,
    onPick: (ProbeHubSearchHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val q = query.trim()
    if (q.isEmpty()) return
    val hits = remember(q, index) { ProbeHubSearch.filter(index, q) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "${hits.size} result${if (hits.size == 1) "" else "s"}",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.55f),
        )
        if (hits.isEmpty()) {
            Text(
                "No hub entries match \"$query\".",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f),
            )
        } else {
            hits.take(24).forEach { hit ->
                val shape = RoundedCornerShape(10.dp)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable { onPick(hit) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(hit.title, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            hit.subtitle,
                            color = Color.White.copy(alpha = 0.55f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        hit.kindLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFAAEECC),
                    )
                }
            }
        }
    }
}

/** Stash chrome-settings navigation from probe hub → preview engine (consumed once on preview open). */
object PendingChromeSettingsNavigation {
    @Volatile
    var pending: ChromeSettingSearchHit? = null

    fun stash(hit: ChromeSettingSearchHit) {
        pending = hit
    }

    fun consume(): ChromeSettingSearchHit? {
        val hit = pending
        pending = null
        return hit
    }
}
