package dev.pointandshoot

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private const val TRAILING_STEPS_AFTER_RUNTIME = 3 // vibration, notification policy, done

/**
 * First-run flow: explains each permission or capability the app uses, then requests them in order.
 *
 * 1. Welcome  
 * 2. Each entry in [WelcomeFlowConfig.runtimePermissionSteps] (system runtime dialog per step)  
 * 3. Vibration (manifest-only; informational)  
 * 4. Notification policy (optional; opens system screen for DND-while-recording)  
 * 5. Done
 */
@Composable
fun WelcomePermissionsScreen(
    runtimeSteps: List<WelcomeRuntimePermissionStep> = WelcomeFlowConfig.runtimePermissionSteps,
    hasRuntimePermission: (String) -> Boolean,
    onRequestRuntimePermission: (String) -> Unit,
    onOpenNotificationPolicySettings: () -> Unit,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    val totalSteps = 1 + runtimeSteps.size + TRAILING_STEPS_AFTER_RUNTIME

    val introIndex = 0
    val firstRuntimeIndex = 1
    val lastRuntimeIndex = runtimeSteps.size
    val vibrationIndex = lastRuntimeIndex + 1
    val notificationIndex = lastRuntimeIndex + 2
    val doneIndex = lastRuntimeIndex + 3

    fun allRequiredRuntimeGranted(): Boolean =
        runtimeSteps.all { spec ->
            !spec.requiredToEnterApp || hasRuntimePermission(spec.permission)
        }

    fun canContinueFromCurrentStep(): Boolean =
        when (step) {
            introIndex -> true
            in firstRuntimeIndex..lastRuntimeIndex -> {
                val spec = runtimeSteps[step - firstRuntimeIndex]
                !spec.requiredToEnterApp || hasRuntimePermission(spec.permission)
            }
            vibrationIndex, notificationIndex -> true
            else -> false
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PnsColors.Charcoal)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Welcome",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
        )
        Text(
            text = "Step ${step + 1} of $totalSteps",
            style = MaterialTheme.typography.labelMedium,
            color = PnsColors.PhotoOrange,
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (step) {
                    introIndex -> {
                        Text(
                            "Point & Shoot",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                        )
                        Text(
                            "This app needs a few permissions to work well. " +
                                "We will explain each one and ask you to approve them one at a time — " +
                                "nothing is requested without context.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.88f),
                        )
                    }
                    in firstRuntimeIndex..lastRuntimeIndex -> {
                        val spec = runtimeSteps[step - firstRuntimeIndex]
                        Text(spec.title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text(
                            spec.rationaleBody,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.88f),
                        )
                        Text(
                            "Android will show its own permission dialog when you tap the button below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.65f),
                        )
                        if (hasRuntimePermission(spec.permission)) {
                            Text(
                                "Status: allowed",
                                style = MaterialTheme.typography.titleSmall,
                                color = PnsColors.OkGreen,
                            )
                        } else {
                            Button(
                                onClick = { onRequestRuntimePermission(spec.permission) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Grant permission")
                            }
                        }
                    }
                    vibrationIndex -> {
                        Text("Vibration", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text(
                            "A short vibration can confirm still captures (optional haptic feedback). " +
                                "This uses the normal vibration capability declared in the app manifest — " +
                                "Android does not show a separate prompt for it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.88f),
                        )
                    }
                    notificationIndex -> {
                        Text("Do Not Disturb (optional)", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text(
                            "If you later enable “Do Not Disturb while recording,” the app can silence " +
                                "interruptions during video takes. That uses Android’s notification policy access, " +
                                "which you control in system Settings — not a normal pop-up permission.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.88f),
                        )
                        OutlinedButton(
                            onClick = onOpenNotificationPolicySettings,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Open notification policy settings")
                        }
                        Text(
                            "You can skip this now and enable it later from the preview screen if you want.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                    doneIndex -> {
                        Text("You are set", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text(
                            "You can change permissions anytime in Android Settings → Apps → Point & Shoot.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.88f),
                        )
                        val missingRequired =
                            runtimeSteps.filter { spec ->
                                spec.requiredToEnterApp && !hasRuntimePermission(spec.permission)
                            }
                        if (missingRequired.isNotEmpty()) {
                            Text(
                                "Some required access is still off. Grant it below or open the app’s system page.",
                                style = MaterialTheme.typography.bodySmall,
                                color = PnsColors.WarnAmber,
                            )
                            for (spec in missingRequired) {
                                Button(
                                    onClick = { onRequestRuntimePermission(spec.permission) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Grant permission")
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        },
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Open app settings")
                            }
                        }
                    }
                    else -> {
                        Text("Unknown step", color = Color.White)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step > 0) {
                OutlinedButton(onClick = { step-- }) {
                    Text("Back")
                }
            }
            when (step) {
                introIndex, vibrationIndex, notificationIndex -> {
                    Button(
                        onClick = { step++ },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Continue")
                    }
                }
                in firstRuntimeIndex..lastRuntimeIndex -> {
                    Button(
                        onClick = { step++ },
                        enabled = canContinueFromCurrentStep(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Continue")
                    }
                }
                doneIndex -> {
                    Button(
                        onClick = onFinished,
                        enabled = allRequiredRuntimeGranted(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Enter app")
                    }
                }
                else -> {}
            }
        }
    }
}
