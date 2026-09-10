package com.qyf.rememberenglish.ui.add

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qyf.rememberenglish.R
import com.qyf.rememberenglish.data.ocr.PhotoDecoder

/** 扫词添加：全屏实时取景识别 + 候选词实时释义 + 勾选批量加入 + 拍照/相册（手写词录入） */
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

    val imageCapture = remember { ImageCapture.Builder().build() }

    // 相册选图（手写词录入）：解码 → 识别
    val albumLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bitmap = PhotoDecoder.decode(context, uri)
        if (bitmap == null) viewModel.notifyPhotoFailed() else viewModel.recognizePhoto(bitmap, 0)
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
    LaunchedEffect(state.photoFailed) {
        if (state.photoFailed) {
            snackbarHostState.showSnackbar(context.getString(R.string.add_photo_failed))
            viewModel.consumePhotoFailed()
        }
    }
    LaunchedEffect(state.cloudFailed) {
        if (state.cloudFailed) {
            snackbarHostState.showSnackbar(context.getString(R.string.add_cloud_fallback))
            viewModel.consumeCloudFailed()
        }
    }
    // 忽略伪词（date/sun 等）：Snackbar 可撤销
    LaunchedEffect(state.ignoredWord) {
        state.ignoredWord?.let { word ->
            val action = snackbarHostState.showSnackbar(
                message = context.getString(R.string.add_ignored, word),
                actionLabel = context.getString(R.string.mine_undo),
            )
            viewModel.consumeIgnoredWord()
            if (action == SnackbarResult.ActionPerformed) viewModel.unignoreWord(word)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (hasCameraPermission) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 相机区：占满除底部候选面板外的全部空间
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    OcrCameraPreview(
                        onText = viewModel::onOcrText,
                        imageCapture = imageCapture,
                        enhance = state.enhance,
                        modifier = Modifier.fillMaxSize(),
                    )

                    // 整个画面点击 → 暂停/继续识别（顶部按钮与底部面板各自消费点击，不在此范围）
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { viewModel.toggleFrozen() },
                    )

                    // 顶部：返回（左） + 手写增强/相册/拍照（右）
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(8.dp),
                    ) {
                        CircleIconButton(onClick = onDone)
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CameraPillButton(
                            text = stringResource(R.string.add_enhance),
                            active = state.enhance,
                            onClick = viewModel::toggleEnhance,
                        )
                        CameraPillButton(
                            text = stringResource(R.string.add_album),
                            active = false,
                            onClick = {
                                albumLauncher.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                        )
                        CameraPillButton(
                            text = stringResource(R.string.add_photo),
                            active = false,
                            onClick = {
                                imageCapture.takePicture(
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(image: ImageProxy) {
                                            val rotation = image.imageInfo.rotationDegrees
                                            val bitmap = runCatching { image.toBitmap() }.getOrNull()
                                            image.close()
                                            if (bitmap != null) {
                                                viewModel.recognizePhoto(bitmap, rotation)
                                            } else {
                                                viewModel.notifyPhotoFailed()
                                            }
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            viewModel.notifyPhotoFailed()
                                        }
                                    },
                                )
                            },
                        )
                    }

                    // 状态提示（底部居中，小字）
                    Text(
                        text = stringResource(
                            when {
                                state.recognizing -> R.string.add_recognizing
                                state.frozen -> R.string.add_frozen_hint
                                else -> R.string.add_live_hint
                            },
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp)
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }

                // 底部候选面板：紧凑（最高 ~300dp），不再遮挡大半画面
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .background(
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                        )
                        .navigationBarsPadding()
                        .padding(top = 8.dp, bottom = 8.dp),
                ) {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        if (state.candidates.isEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.add_empty_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 16.dp),
                                )
                            }
                        }
                        items(state.candidates, key = { it.text }) { candidate ->
                            CandidateRow(
                                candidate = candidate,
                                selected = candidate.text in state.selected,
                                inMine = candidate.text in state.inMine,
                                onlineMeanings = state.onlineMeanings[candidate.text],
                                onlineLoading = candidate.text in state.onlineLoading,
                                onToggle = { viewModel.toggleSelect(candidate.text) },
                                onIgnore = { viewModel.ignoreWord(candidate.text) },
                            )
                        }
                    }
                    Button(
                        onClick = viewModel::addSelected,
                        enabled = state.selected.isNotEmpty() && !state.adding,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            text = if (state.selected.isEmpty()) {
                                stringResource(R.string.add_batch_empty)
                            } else {
                                stringResource(R.string.add_batch_n, state.selected.size)
                            },
                        )
                    }
                }
            }
        } else {
            // 无相机权限：占满全屏的引导
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.add_camera_needed),
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CameraPillButton(
                        text = stringResource(R.string.add_grant_camera),
                        active = true,
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding(),
        )
    }
}

/** 相机上的圆形返回按钮（半透明白字，不挡画面） */
@Composable
private fun CircleIconButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.add_back),
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** 相机上的文字胶囊按钮：active 时用主题色高亮（如"手写增强"开启态） */
@Composable
private fun CameraPillButton(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .background(
                if (active) MaterialTheme.colorScheme.primary
                else Color.Black.copy(alpha = 0.45f),
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) MaterialTheme.colorScheme.onPrimary else Color.White,
        )
    }
}

/** 候选词行（紧凑版）：点行勾选；选中高亮 + 对勾；未收录词显示联网释义；右侧标注已添加/自定义/在线/考频 + 忽略 */
@Composable
private fun CandidateRow(
    candidate: CandidateWord,
    selected: Boolean,
    inMine: Boolean,
    onlineMeanings: List<String>?,
    onlineLoading: Boolean,
    onToggle: () -> Unit,
    onIgnore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent,
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.text,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val localMeaning = candidate.matched?.meanings?.firstOrNull()
            val onlineMeaning = onlineMeanings?.firstOrNull { it.isNotBlank() }
            Text(
                text = when {
                    localMeaning != null -> localMeaning
                    onlineMeaning != null -> onlineMeaning
                    onlineLoading -> stringResource(R.string.add_online_meaning_loading)
                    candidate.matched == null -> stringResource(R.string.add_custom_will)
                    else -> stringResource(R.string.add_no_meaning)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val showOnlineTag = candidate.matched == null &&
            onlineMeanings?.any { it.isNotBlank() } == true
        when {
            // 已在我要背：着重标注，杜绝重复添加（用户 2026-09-10）
            inMine -> Text(
                text = stringResource(R.string.add_in_mine_tag),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
            showOnlineTag -> Text(
                text = stringResource(R.string.add_online_tag),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            candidate.matched == null -> Text(
                text = stringResource(R.string.add_custom_tag),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            candidate.freq != null -> Text(
                text = stringResource(R.string.add_freq_tag, candidate.freq),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Spacer(modifier = Modifier.size(6.dp))
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.size(6.dp))
        // 忽略伪词（date/sun 等）：点 ✕ 从候选移除并持久化，Snackbar 可撤销
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = stringResource(R.string.add_ignore),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .clickable(onClick = onIgnore)
                .padding(2.dp),
        )
    }
}
