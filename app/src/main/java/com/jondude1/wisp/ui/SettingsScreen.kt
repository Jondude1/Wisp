package com.jondude1.wisp.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jondude1.wisp.BuildConfig
import com.jondude1.wisp.agent.AgentController
import com.jondude1.wisp.services.WispAccessibilityService
import com.jondude1.wisp.update.Updater
import kotlinx.coroutines.launch

private fun granted(ctx: Context, perm: String) =
    ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

private fun roleHeld(ctx: Context, role: String): Boolean =
    runCatching { ctx.getSystemService(RoleManager::class.java).isRoleHeld(role) }.getOrDefault(false)

private fun open(ctx: Context, intent: Intent) {
    runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun SettingsScreen(c: AgentController, host: HostActions, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val s = c.settings
    val scope = rememberCoroutineScope()
    c.resumeTick // reading this re-checks every status below whenever you come back from a system screen

    val a11yOn = WispAccessibilityService.instance != null
    val notifOn = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    val micOn = granted(ctx, Manifest.permission.RECORD_AUDIO)
    val smsOn = granted(ctx, Manifest.permission.SEND_SMS) && granted(ctx, Manifest.permission.READ_CONTACTS)
    val postOn = granted(ctx, Manifest.permission.POST_NOTIFICATIONS)
    val installOn = ctx.packageManager.canRequestPackageInstalls()

    Box(
        Modifier
            .fillMaxSize()
            .background(W.Void.copy(alpha = 0.985f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Settings", style = T.greeting.copy(fontSize = 30.sp, lineHeight = 36.sp), modifier = Modifier.weight(1f))
                    Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                        CloseIcon(W.Fog)
                    }
                }
            }

            item { Section("The brain") }
            item { Field("Anthropic API key", s.apiKey, hide = true) { s.updateApiKey(it) } }
            item { Field("Model", s.model, hide = false) { s.updateModel(it) } }
            item { Toggle("Speak replies", "After you talk to Wisp, it answers out loud.", s.speakReplies) { s.updateSpeak(it) } }
            item { Toggle("Web search", "Lets Wisp look things up. Your API key needs web search enabled.", s.webSearch) { s.updateWebSearch(it) } }

            item { Section("Making it your phone") }
            item {
                Item(
                    "Home screen", "Make Wisp what you see when you press Home.",
                    roleHeld(ctx, RoleManager.ROLE_HOME), "Set as home",
                ) { host.requestRole(RoleManager.ROLE_HOME) }
            }
            item {
                Item(
                    "Assistant", "Power-button hold or a corner swipe opens Wisp, already listening.",
                    roleHeld(ctx, RoleManager.ROLE_ASSISTANT), "Set as assistant",
                ) { host.requestRole(RoleManager.ROLE_ASSISTANT) }
            }
            item { Toggle("Immersive home", "Hide the status and navigation bars. Swipe from an edge to peek.", s.immersive) { s.updateImmersive(it); host.setImmersive(it) } }

            item { Section("What Wisp is allowed to do") }
            item {
                Item(
                    "Screen control", "Read the screen and tap, type and scroll in other apps, only while a task you started is running.",
                    a11yOn, "Turn on",
                ) { open(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            }
            if (!a11yOn) {
                item {
                    Item(
                        "Can't switch it on?", "Apps installed outside the Play Store need one extra step: open App info, tap the menu, choose Allow restricted settings.",
                        false, "Open App info",
                    ) { open(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }
                }
            }
            item {
                Item(
                    "Notification access", "So Wisp can tell you what you missed.",
                    notifOn, "Turn on",
                ) { open(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            }
            item {
                Item("Microphone", "For talking to Wisp.", micOn, "Allow") {
                    host.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO))
                }
            }
            item {
                Item("Texting and contacts", "Look up a contact and send a text, after you approve each one.", smsOn, "Allow") {
                    host.requestPermissions(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS))
                }
            }
            item {
                Item("Notifications from Wisp", "Shows that a task is running in the background.", postOn, "Allow") {
                    host.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                }
            }
            item {
                Item("Install updates", "Lets Wisp install its own new versions.", installOn, "Allow") {
                    open(ctx, Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")))
                }
            }

            item { Section("Updates") }
            item {
                Item(
                    "Version ${BuildConfig.VERSION_NAME}",
                    Updater.status.ifBlank { "Wisp checks for new versions on its own." },
                    null, "Check now",
                ) { scope.launch { Updater.check(force = true) } }
            }
            item { Spacer(Modifier.height(48.dp)) }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = T.small.copy(fontSize = 14.sp), modifier = Modifier.padding(top = 18.dp))
}

/** One row of status. `ok` true shows a check, false or null shows the action button. */
@Composable
private fun Item(title: String, subtitle: String, ok: Boolean?, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = T.bodyBone)
            Text(subtitle, style = T.small)
        }
        if (ok == true) {
            CheckIcon(W.Tide, Modifier.size(22.dp))
        } else {
            Text(
                action,
                style = T.button.copy(fontSize = 14.sp),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(W.Haze)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = T.bodyBone)
            Text(subtitle, style = T.small)
        }
        Switch(
            checked = value,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = W.Void,
                checkedTrackColor = W.Bone,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = W.Fog,
                uncheckedTrackColor = W.Haze,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun Field(label: String, value: String, hide: Boolean, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = T.small)
        val shape = RoundedCornerShape(16.dp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = T.bodyBone,
            cursorBrush = SolidColor(W.Bone),
            visualTransformation = if (hide) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(W.Deep)
                .border(1.dp, W.Haze, shape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}
