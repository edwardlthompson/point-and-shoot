@file:Suppress("FunctionNaming", "MagicNumber")

package dev.pointandshoot.wear

import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WearRemoteActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val client = WearRemoteClient(applicationContext)
        val prefs = getSharedPreferences("wear_remote", MODE_PRIVATE)
        client.lastHost = prefs.getString("host", client.lastHost) ?: client.lastHost
        val vibrator = getSystemService(Vibrator::class.java)
        setContent {
            MaterialTheme {
                WearRemoteScreen(
                    client = client,
                    persistHost = { host -> prefs.edit().putString("host", host).apply() },
                    keepScreen = { on ->
                        if (on) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    },
                    buzz = { ms ->
                        runCatching {
                            vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun WearRemoteScreen(
    client: WearRemoteClient,
    persistHost: (String) -> Unit,
    keepScreen: (Boolean) -> Unit,
    buzz: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(client.lastStatus) }
    var host by remember { mutableStateOf(client.lastHost) }
    var timerMode by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var phoneArmed by remember { mutableIntStateOf(0) }
    DisposableEffect(client) {
        client.startBleScan()
        onDispose {
            keepScreen(false)
            client.close()
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            status = withContext(Dispatchers.IO) { client.pollStatus() }
            if (phoneArmed == 0) phoneArmed = status.phoneTimerSec
            delay(1_500)
        }
    }
    LaunchedEffect(countdown) {
        keepScreen(countdown > 0)
        if (countdown <= 0) return@LaunchedEffect
        buzz(40)
        delay(1_000)
        val next = countdown - 1
        countdown = next
        if (next == 0) {
            buzz(180)
            status = withContext(Dispatchers.IO) { client.send(WearRemoteProtocol.Action.Shutter) }
        }
    }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(if (timerMode) "P&S Timer" else "P&S Remote", textAlign = TextAlign.Center, color = Color.White)
        Text(
            headline(countdown, phoneArmed, status),
            textAlign = TextAlign.Center,
            color = if (status.connected) Color(0xFFFF9800) else Color.Gray,
            fontSize = if (countdown > 0) 22.sp else 12.sp,
        )
        Button(
            onClick = { timerMode = !timerMode },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (timerMode) "Switch to remote" else "Switch to timer") }
        if (timerMode) {
            WearRemoteProtocol.TIMER_SECONDS.forEach { sec ->
                Button(
                    onClick = { countdown = sec },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Watch $sec s") }
            }
            Button(
                onClick = {
                    scope.launch {
                        phoneArmed = 5
                        status = withContext(Dispatchers.IO) {
                            client.send(WearRemoteProtocol.Action.Timer, timerSec = 5)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Phone 5 s") }
            Button(
                onClick = {
                    countdown = 0
                    phoneArmed = 0
                    keepScreen(false)
                    scope.launch {
                        status = withContext(Dispatchers.IO) {
                            client.send(WearRemoteProtocol.Action.CancelTimer)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Cancel") }
        } else {
            Button(
                onClick = {
                    scope.launch {
                        status = withContext(Dispatchers.IO) { client.send(WearRemoteProtocol.Action.Shutter) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Shutter") }
            Button(
                onClick = {
                    scope.launch {
                        status = withContext(Dispatchers.IO) { client.send(WearRemoteProtocol.Action.VideoToggle) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (status.recording) "Stop video" else "Start video") }
            Button(
                onClick = {
                    scope.launch {
                        status = withContext(Dispatchers.IO) { client.send(WearRemoteProtocol.Action.Chapter) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Chapter mark") }
            Button(
                onClick = {
                    host = nextLanGuess(host)
                    client.lastHost = host
                    persistHost(host)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("LAN $host") }
        }
    }
}

private fun headline(countdown: Int, phoneArmed: Int, status: WearRemoteClient.Status): String =
    when {
        countdown > 0 -> "$countdown"
        phoneArmed > 0 -> "Phone ${status.phoneTimerSec.coerceAtLeast(phoneArmed)}s"
        else -> status.detail
    }

private fun nextLanGuess(current: String): String {
    val guesses = listOf("192.168.1.1", "192.168.0.1", "192.168.1.100", "10.0.0.1", "172.16.0.1")
    val i = guesses.indexOf(current)
    return guesses[(i + 1).coerceAtLeast(0) % guesses.size]
}
