package com.nomad.travel.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nomad.travel.R
import com.nomad.travel.llm.ModelEntry
import com.nomad.travel.ui.setup.ModelCard
import com.nomad.travel.ui.theme.NomadGlow
import com.nomad.travel.ui.theme.NomadInputField
import com.nomad.travel.ui.theme.NomadMist
import com.nomad.travel.ui.theme.NomadMuted
import com.nomad.travel.ui.theme.NomadRoyal
import com.nomad.travel.ui.theme.NomadSilver

private data class LangOption(val code: String, val label: String, val flag: String)

private val LANGS = listOf(
    LangOption("ko", "한국어", "🇰🇷"),
    LangOption("en", "English", "🇺🇸"),
    LangOption("zh", "中文", "🇨🇳"),
    LangOption("ja", "日本語", "🇯🇵")
)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLanguageChanged: () -> Unit = {},
    vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var promptDraft by remember { mutableStateOf(state.systemPrompt) }
    LaunchedEffect(state.systemPrompt) { promptDraft = state.systemPrompt }

    var confirmClear by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ModelEntry?>(null) }
    var languageExpanded by remember { mutableStateOf(false) }
    var modelsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        SettingsTopBar(onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ─── Language ─────────────────────────
            Section(stringResource(R.string.settings_language)) {
                val current = LANGS.firstOrNull { it.code == state.language } ?: LANGS[0]
                CollapsibleRow(
                    leading = {
                        Text(current.flag, fontSize = 22.sp)
                        Spacer(Modifier.size(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.settings_language_change),
                                style = MaterialTheme.typography.labelSmall.copy(color = NomadMuted)
                            )
                            Text(
                                text = current.label,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    },
                    expanded = languageExpanded,
                    onToggle = { languageExpanded = !languageExpanded }
                )
                AnimatedVisibility(visible = languageExpanded) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        LANGS.forEach { opt ->
                            LanguageRow(
                                option = opt,
                                selected = state.language == opt.code,
                                onClick = {
                                    if (state.language != opt.code) {
                                        vm.setLanguage(opt.code)
                                        onLanguageChanged()
                                    } else {
                                        languageExpanded = false
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // ─── System prompt ────────────────────
            Section(stringResource(R.string.settings_system_prompt)) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(NomadInputField)
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        if (promptDraft.isEmpty()) {
                            Text(
                                text = stringResource(R.string.settings_system_prompt_hint),
                                style = TextStyle(
                                    color = NomadMuted,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            )
                        }
                        BasicTextField(
                            value = promptDraft,
                            onValueChange = { promptDraft = it },
                            textStyle = TextStyle(
                                color = NomadSilver,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            ),
                            cursorBrush = SolidColor(NomadGlow),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton(stringResource(R.string.settings_prompt_clear)) {
                            promptDraft = ""
                            vm.resetSystemPrompt()
                        }
                        PrimaryButton(stringResource(R.string.settings_prompt_save)) {
                            vm.setSystemPrompt(promptDraft)
                        }
                    }
                }
            }

            // ─── Model ────────────────────────────
            Section(stringResource(R.string.settings_model_management)) {
                val activeRow = state.modelRows.firstOrNull { it.entry.id == state.activeModelId }
                CollapsibleRow(
                    leading = {
                        Column {
                            Text(
                                text = stringResource(
                                    if (modelsExpanded) R.string.settings_model_hide
                                    else R.string.settings_model_show
                                ),
                                style = MaterialTheme.typography.labelSmall.copy(color = NomadMuted)
                            )
                            Text(
                                text = activeRow?.entry?.displayName ?: "—",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    },
                    expanded = modelsExpanded,
                    onToggle = { modelsExpanded = !modelsExpanded }
                )
                AnimatedVisibility(visible = modelsExpanded) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        state.modelRows.forEach { row ->
                            ModelCard(
                                row = row,
                                active = row.entry.id == state.activeModelId && row.downloaded,
                                onSelect = { vm.selectModel(row.entry) },
                                onDownload = { vm.startDownload(row.entry) },
                                onCancel = { vm.cancelDownload(row.entry) },
                                onDelete = { pendingDelete = row.entry }
                            )
                        }
                        Text(
                            text = stringResource(R.string.settings_model_help),
                            style = MaterialTheme.typography.labelSmall,
                            color = NomadMuted
                        )
                    }
                }
            }

            // ─── Danger zone ──────────────────────
            Section(stringResource(R.string.settings_chat_section)) {
                DangerButton(
                    label = stringResource(R.string.settings_clear_chats),
                    onClick = { confirmClear = true }
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.settings_clear_confirm_title)) },
            text = { Text(stringResource(R.string.settings_clear_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearChats()
                    confirmClear = false
                }) {
                    Text(
                        stringResource(R.string.settings_clear_confirm_ok),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.common_cancel))
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
                    vm.deleteModel(entry)
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
private fun SettingsTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.settings_back),
                tint = NomadSilver
            )
        }
        Spacer(Modifier.size(4.dp))
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(
                color = NomadMuted,
                letterSpacing = 1.sp
            ),
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
        )
        content()
    }
}

@Composable
private fun CollapsibleRow(
    leading: @Composable () -> Unit,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chev")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) { leading() }
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = null,
            tint = NomadMist,
            modifier = Modifier
                .size(22.dp)
                .rotate(rotation)
        )
    }
}

@Composable
private fun LanguageRow(option: LangOption, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) NomadGlow else Color.White.copy(alpha = 0.08f)
    val bg = if (selected) NomadRoyal.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.5.dp, border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(option.flag, fontSize = 22.sp)
        Spacer(Modifier.size(12.dp))
        Text(option.label, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        if (selected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(NomadGlow)
            )
        }
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(NomadRoyal)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(color = NomadSilver),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SecondaryButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, NomadMuted, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(color = NomadMist)
        )
    }
}

@Composable
private fun DangerButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.error
            ),
            fontWeight = FontWeight.Medium
        )
    }
}
