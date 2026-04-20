package com.nomad.travel.ui.setup

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nomad.travel.R
import com.nomad.travel.llm.DownloadStatus
import com.nomad.travel.llm.ModelCatalog
import com.nomad.travel.llm.ModelEntry
import com.nomad.travel.ui.theme.NomadGlow
import com.nomad.travel.ui.theme.NomadInputField
import com.nomad.travel.ui.theme.NomadMist
import com.nomad.travel.ui.theme.NomadMuted
import com.nomad.travel.ui.theme.NomadRoyal
import com.nomad.travel.ui.theme.NomadSilver

@Composable
fun ModelSetupScreen(
    onReady: () -> Unit,
    vm: SetupViewModel = viewModel(factory = SetupViewModel.Factory)
) {
    val state by vm.state.collectAsStateWithLifecycle()

    val notifPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* proceed regardless of result */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var upgradeExpanded by rememberSaveable { mutableStateOf(false) }
    var pendingCancel by remember { mutableStateOf<ModelEntry?>(null) }
    var pendingDelete by remember { mutableStateOf<ModelEntry?>(null) }
    var autoStartAfterDownload by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.selected?.downloaded, autoStartAfterDownload) {
        if (autoStartAfterDownload && state.selected?.downloaded == true) {
            autoStartAfterDownload = false
            vm.commitSelectionAnd(onReady)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_logo),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.size(14.dp))
            Column {
                Text(
                    text = stringResource(R.string.setup_title),
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = stringResource(R.string.setup_subtitle),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        IntroCard()

        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val primaryRow = state.rows.firstOrNull { it.entry.id == ModelCatalog.gemma4E2B.id }
            primaryRow?.let { row ->
                ModelCard(
                    row = row,
                    active = row.entry.id == state.selectedId,
                    onSelect = { vm.select(row.entry) },
                    onDownload = { vm.startDownload(row.entry) },
                    onCancel = { pendingCancel = row.entry },
                    onDelete = { pendingDelete = row.entry }
                )
            }

            UpgradeSection(
                expanded = upgradeExpanded,
                onToggle = { upgradeExpanded = !upgradeExpanded }
            )

            AnimatedVisibility(visible = upgradeExpanded) {
                val upgradeRow = state.rows.firstOrNull { it.entry.id == ModelCatalog.gemma4E4B.id }
                upgradeRow?.let { row ->
                    ModelCard(
                        row = row,
                        active = row.entry.id == state.selectedId,
                        onSelect = { vm.select(row.entry) },
                        onDownload = { vm.startDownload(row.entry) },
                        onCancel = { pendingCancel = row.entry },
                        onDelete = { pendingDelete = row.entry }
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
        }

        val selected = state.selected
        val downloaded = selected?.downloaded == true
        val downloading = selected?.status is DownloadStatus.Progress && !downloaded

        val buttonLabel = when {
            selected == null -> stringResource(R.string.setup_download_first)
            downloaded -> stringResource(R.string.setup_start)
            downloading -> stringResource(R.string.setup_downloading)
            else -> stringResource(R.string.setup_download_and_start)
        }
        val buttonEnabled = selected != null && !downloading

        Button(
            onClick = {
                val entry = selected?.entry ?: return@Button
                if (downloaded) {
                    vm.commitSelectionAnd(onReady)
                } else {
                    autoStartAfterDownload = true
                    vm.startDownload(entry)
                }
            },
            enabled = buttonEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NomadRoyal,
                contentColor = NomadSilver,
                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                disabledContentColor = NomadMuted
            )
        ) {
            Text(
                text = buttonLabel,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    pendingCancel?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingCancel = null },
            title = { Text(stringResource(R.string.model_cancel_confirm_title)) },
            text = { Text(stringResource(R.string.model_cancel_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.cancelDownload(entry)
                    autoStartAfterDownload = false
                    pendingCancel = null
                }) { Text(stringResource(R.string.common_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingCancel = null }) {
                    Text(stringResource(R.string.common_no))
                }
            },
            containerColor = NomadInputField,
            titleContentColor = NomadSilver,
            textContentColor = NomadMist
        )
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.settings_delete_model_title)) },
            text = {
                Text(
                    text = entry.displayName + "\n\n" +
                        stringResource(R.string.settings_delete_model_body)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(entry)
                    pendingDelete = null
                }) {
                    Text(
                        stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            containerColor = NomadInputField,
            titleContentColor = NomadSilver,
            textContentColor = NomadMist
        )
    }
}

@Composable
private fun IntroCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NomadRoyal.copy(alpha = 0.12f))
            .border(1.dp, NomadRoyal.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.setup_intro),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = NomadSilver,
                lineHeight = 20.sp
            )
        )
    }
}

