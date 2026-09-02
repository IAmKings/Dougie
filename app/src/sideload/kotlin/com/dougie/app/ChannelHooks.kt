package com.dougie.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.dougie.core.model.AgentTask
import com.dougie.feature.permissions.PermissionItem
import com.dougie.feature.permissions.PermissionKind
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dougie.core.tool.BundledModelSeed
import com.dougie.feature.chat.DougieColors
import java.io.IOException

object ChannelHooks {
    fun hasChannelConsent(context: Context): Boolean = SideloadConsentStore.isGranted(context)

    fun seedBundledModels(context: Context) {
        val app = context.applicationContext
        BundledModelSeed.seed(app.filesDir) { relative ->
            try {
                app.assets.open(relative)
            } catch (_: IOException) {
                null
            }
        }
    }

    @Composable
    fun Root(content: @Composable () -> Unit) {
        val context = LocalContext.current
        val app = context.applicationContext as DougieApplication
        var consented by remember { mutableStateOf(SideloadConsentStore.isGranted(app)) }
        if (consented) {
            content()
            return
        }
        SideloadConsentScreen(
            onAgree = {
                SideloadConsentStore.grant(app)
                app.refreshChannelTools()
                consented = true
            },
            onExit = { (context as? Activity)?.finish() },
        )
    }

    fun syncOverlay(context: Context) {
        OverlayController.sync(context)
    }

    fun screenShortcutHint(context: Context, task: AgentTask?): String? {
        if (!ScreenShortcutHint.shouldShow(task)) return null
        return context.getString(R.string.overlay_shortcut_hint)
    }

    fun overlayPermissionItem(context: Context): PermissionItem =
        PermissionItem(
            id = "overlay",
            title = context.getString(R.string.overlay_permission_title),
            subtitle = context.getString(R.string.overlay_permission_subtitle),
            runtimePermission = null,
            granted = Settings.canDrawOverlays(context),
            riskLabel = "L1",
            lastUsedLabel = "尚未使用",
            kind = PermissionKind.OVERLAY,
        )

    @Composable
    fun ShortcutLayerSettings() {
        val context = LocalContext.current
        var enabled by remember { mutableStateOf(OverlayPrefs.isEnabled(context)) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DougieColors.OutlineVariant, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.overlay_title),
                color = DougieColors.OnSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.overlay_body),
                color = DougieColors.OnSurfaceVariant,
                fontSize = 14.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.overlay_toggle),
                    color = DougieColors.OnSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = { on ->
                        if (on && !Settings.canDrawOverlays(context)) {
                            OverlayPrefs.setEnabled(context, true)
                            enabled = true
                            val uri = Uri.parse("package:${context.packageName}")
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        } else {
                            OverlayPrefs.setEnabled(context, on)
                            enabled = on
                        }
                        OverlayController.sync(context)
                    },
                )
            }
        }
    }
}

@Composable
private fun SideloadConsentScreen(
    onAgree: () -> Unit,
    onExit: () -> Unit,
) {
    var a11y by remember { mutableStateOf(false) }
    var boundary by remember { mutableStateOf(false) }
    var revoke by remember { mutableStateOf(false) }
    val allChecked = a11y && boundary && revoke
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DougieColors.Surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.sideload_consent_title),
                color = DougieColors.OnSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.sideload_consent_body),
                color = DougieColors.OnSurfaceVariant,
                fontSize = 14.sp,
            )
            ConsentRow(
                checked = a11y,
                text = stringResource(R.string.sideload_consent_item_a11y),
                onCheckedChange = { a11y = it },
            )
            ConsentRow(
                checked = boundary,
                text = stringResource(R.string.sideload_consent_item_boundary),
                onCheckedChange = { boundary = it },
            )
            ConsentRow(
                checked = revoke,
                text = stringResource(R.string.sideload_consent_item_revoke),
                onCheckedChange = { revoke = it },
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onAgree,
                enabled = allChecked,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.sideload_consent_agree))
            }
            OutlinedButton(
                onClick = onExit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.sideload_consent_exit))
            }
        }
    }
}

@Composable
private fun ConsentRow(
    checked: Boolean,
    text: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(
            text = text,
            color = DougieColors.OnSurface,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
