package com.qyf.rememberenglish.ui.add

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qyf.rememberenglish.R

/** 扫词添加：相机实时取景识别 + 候选词实时释义 + 勾选批量加入 + 手动输入 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AddWordScreen(
    onDone: () -> Unit,
    viewModel: AddWordViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    val resultText = state.result?.let { r ->
        buildString {
            append(stringResource(R.string.add_result_added, r.added))
            if (r.customCreated > 0) append(stringResource(R.string.add_result_custom, r.customCreated))
            if (r.alreadyInMine > 0) append(stringResource(R.string.add_result_already, r.alreadyInMine))
        }
    }
    LaunchedEffect(resultText) {
        resultText?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.add_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (hasCameraPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.45f),
                ) {
                    OcrCameraPreview(
                        onText = viewModel::onOcrText,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Text(
                        text = stringResource(
                            if (state.frozen) R.string.add_frozen_hint else R.string.add_live_hint,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .clickable { viewModel.toggleFrozen() }
                            .padding(8.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.45f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.add_camera_needed))
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text(stringResource(R.string.add_grant_camera))
                        }
                    }
                }
            }

            // 候选词列表：实时显示释义，勾选批量加入
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.55f),
            ) {
                if (state.candidates.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.add_empty_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(state.candidates, key = { it.text }) { candidate ->
                    CandidateRow(
                        candidate = candidate,
                        selected = candidate.text in state.selected,
                        onToggle = { viewModel.toggleSelect(candidate.text) },
                    )
                }
            }

            // 底部：手动输入 + 批量加入
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.manualInput,
                    onValueChange = viewModel::setManualInput,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.add_manual_hint)) },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.size(8.dp))
                OutlinedButton(onClick = viewModel::addManual, enabled = state.manualInput.isNotBlank()) {
                    Text(stringResource(R.string.add_manual_join))
                }
            }
            Button(
                onClick = viewModel::addSelected,
                enabled = state.selected.isNotEmpty() && !state.adding,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text(
                    if (state.selected.isEmpty()) stringResource(R.string.add_batch_empty)
                    else stringResource(R.string.add_batch_n, state.selected.size),
                )
            }
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: CandidateWord,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f)) {
                Text(candidate.text, style = MaterialTheme.typography.titleMedium)
                val meaning = candidate.matched?.meanings?.firstOrNull()
                Text(
                    text = when {
                        meaning != null -> meaning
                        candidate.matched == null -> stringResource(R.string.add_custom_will)
                        else -> stringResource(R.string.add_no_meaning)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (candidate.matched == null) {
                Text(
                    text = stringResource(R.string.add_custom_tag),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
