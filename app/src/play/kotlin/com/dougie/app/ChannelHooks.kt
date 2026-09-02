package com.dougie.app

import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dougie.feature.chat.DougieColors

object ChannelHooks {
    @Suppress("UNUSED_PARAMETER")
    fun hasChannelConsent(context: Context): Boolean = true

    @Suppress("UNUSED_PARAMETER")
    fun seedBundledModels(context: Context) {
    }

    @Suppress("UNUSED_PARAMETER")
    fun syncOverlay(context: Context) {
    }

    @Suppress("UNUSED_PARAMETER")
    fun screenShortcutHint(context: Context, task: com.dougie.core.model.AgentTask?): String? = null

    @Suppress("UNUSED_PARAMETER")
    fun overlayPermissionItem(context: Context): com.dougie.feature.permissions.PermissionItem? = null

    @Composable
    fun Root(content: @Composable () -> Unit) {
        content()
    }

    @Composable
    fun ShortcutLayerSettings() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DougieColors.OutlineVariant, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.shortcut_layer_title),
                color = DougieColors.OnSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.shortcut_layer_body),
                color = DougieColors.OnSurfaceVariant,
                fontSize = 14.sp,
            )
        }
    }
}
