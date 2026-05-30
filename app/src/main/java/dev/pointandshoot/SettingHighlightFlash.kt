package dev.pointandshoot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Three-pulse background highlight for a settings row (Milestone **17.4**).
 */
class SettingHighlightFlashState internal constructor(
    private val requestHighlightInternal: (String) -> Unit,
    private val isFlashingInternal: (String) -> Boolean,
) {
    fun request(key: String) = requestHighlightInternal(key)

    fun isFlashing(key: String): Boolean = isFlashingInternal(key)

    fun Modifier.settingHighlightBackground(settingKey: String?): Modifier {
        if (settingKey.isNullOrBlank()) return this
        return then(
            if (isFlashing(settingKey)) {
                Modifier
                    .background(PnsColors.PhotoOrange.copy(alpha = 0.38f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            } else {
                Modifier
            },
        )
    }

    /** Applies [settingHighlightBackground] to [base] when this flash state is non-null. */
    fun applyHighlight(base: Modifier, settingKey: String?): Modifier =
        with(this) { base.settingHighlightBackground(settingKey) }
}

@Composable
fun rememberSettingHighlightFlash(): SettingHighlightFlashState {
    var activeKey by remember { mutableStateOf<String?>(null) }
    var flashOn by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    return remember {
        SettingHighlightFlashState(
            requestHighlightInternal = { key ->
                scope.launch {
                    activeKey = key
                    repeat(3) {
                        flashOn = true
                        delay(220)
                        flashOn = false
                        delay(220)
                    }
                    activeKey = null
                    flashOn = false
                }
            },
            isFlashingInternal = { key -> activeKey == key && flashOn },
        )
    }
}
