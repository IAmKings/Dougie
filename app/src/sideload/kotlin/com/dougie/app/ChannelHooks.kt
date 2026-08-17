package com.dougie.app

import android.app.Activity
import android.content.Context
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import com.dougie.feature.chat.DougieColors

object ChannelHooks {
    fun hasChannelConsent(context: Context): Boolean = SideloadConsentStore.isGranted(context)

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
