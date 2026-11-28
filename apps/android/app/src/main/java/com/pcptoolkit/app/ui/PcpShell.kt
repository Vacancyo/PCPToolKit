/**
 * PCP ToolKit
 * Copyright (C) 2026 VacancyoTP
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 *
 * @file PcpShell.kt
 * @brief 应用主界面框架。
 *
 * 该代码文件实现主界面导航、侧边菜单、设置页面、语言选择和通用图标。
 */
package com.pcptoolkit.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.pcptoolkit.app.LANGS
import com.pcptoolkit.app.R
import com.pcptoolkit.app.feature.editor.LoadedTextDocument
import com.pcptoolkit.app.feature.editor.TextEditorPage
import com.pcptoolkit.app.feature.editor.TextEditorTarget
import com.pcptoolkit.app.feature.sandbox.SandboxApp
import com.pcptoolkit.app.feature.sandbox.SandboxPage
import com.pcptoolkit.app.feature.script.ScriptsPage
import com.pcptoolkit.app.platform.overlay.FloatingWindowService
import com.pcptoolkit.app.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

private val MenuHeadingColor = Color(0xFF5E5E5E)
private val SettingsHeadingColor = Color(0xFF187BDE)

/**
 * @brief 应用主界面中的页面。
 */
private enum class PcpPage(val titleKey: String) {
    HOME("nav.home"),
    SANDBOX("nav.sandbox"),
    RTON("nav.rton"),
    SCRIPTS("nav.scripts")
}

/**
 * @brief 组合应用主导航和页面内容。
 * @param vm RTON 转换状态管理对象。
 * @param prefs 应用设置存储。
 */
@Composable
fun PcpShell(
    lang: String,
    overlayEnabled: Boolean,
    onOverlayPreferenceChange: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit,
    rtonContent: @Composable () -> Unit
) {
    var page by remember { mutableStateOf(PcpPage.HOME) }
    var menuOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var languageOpen by remember { mutableStateOf(false) }
    var restartNoticeOpen by remember { mutableStateOf(false) }
    var sandboxFileApp by remember { mutableStateOf<SandboxApp?>(null) }
    var editorDocument by remember { mutableStateOf<LoadedTextDocument?>(null) }
    var editorVisible by remember { mutableStateOf(false) }
    var editorLoading by remember { mutableStateOf(false) }
    val toastState = rememberPcpToastState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val editorProgress by animateFloatAsState(
        targetValue = if (editorVisible) 1f else 0f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "editor"
    )

    val menuProgress by animateFloatAsState(
        targetValue = if (menuOpen) 1f else 0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "menu"
    )

    fun openEditor(target: TextEditorTarget) {
        if (editorLoading || editorDocument != null) return
        editorLoading = true
        scope.launch {
            val result = runCatching {
                target.read(context)
            }
            editorLoading = false
            result.onSuccess { text ->
                menuOpen = false
                settingsOpen = false
                editorDocument = LoadedTextDocument(target, text)
                editorVisible = true
            }.onFailure {
                scope.launch {
                    toastState.show(
                        t("editor.read_failed") + ": " +
                            (it.message ?: it.javaClass.simpleName),
                        isError = true
                    )
                }
            }
        }
    }

    fun closeEditor() {
        editorVisible = false
        scope.launch {
            delay(280)
            editorDocument = null
        }
    }

    val showToast: (String, Boolean) -> Unit = { text, isError ->
        scope.launch { toastState.show(text, isError) }
    }

    fun navigateBack() {
        when {
            restartNoticeOpen -> restartNoticeOpen = false
            languageOpen -> languageOpen = false
            settingsOpen -> settingsOpen = false
            menuOpen -> menuOpen = false
            sandboxFileApp != null -> sandboxFileApp = null
            page != PcpPage.HOME -> page = PcpPage.HOME
        }
    }
    val canNavigateBack =
        restartNoticeOpen || settingsOpen || languageOpen || menuOpen ||
            sandboxFileApp != null || page != PcpPage.HOME

    BackHandler(enabled = canNavigateBack, onBack = ::navigateBack)

    // NativeBridge 的语言不是 Compose 状态，用语言作为组合键重建所有 t（） 文本。
    key(lang) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            val menuWidth = maxWidth * 3f / 4f
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX =
                            menuWidth.toPx() * menuProgress - size.width * editorProgress
                    }
                    .zIndex(1f)
            ) {
                val fileManagerOpen = page == PcpPage.SANDBOX && sandboxFileApp != null
                Column(Modifier.fillMaxSize()) {
                    PcpTopBar(
                        title = if (fileManagerOpen) t("sandbox.file_manager")
                        else t(page.titleKey),
                        showBack = fileManagerOpen,
                        onMenuClick = {
                            if (fileManagerOpen) {
                                sandboxFileApp = null
                            } else {
                                menuOpen = !menuOpen
                            }
                        }
                    )
                    Box(Modifier.weight(1f)) {
                        when (page) {
                            PcpPage.HOME -> PcpHomePage()
                            PcpPage.SANDBOX -> SandboxPage(
                                overlayEnabled = overlayEnabled,
                                fileApp = sandboxFileApp,
                                onFileAppChange = { sandboxFileApp = it },
                                onEditText = ::openEditor,
                                onToast = showToast
                            )
                            PcpPage.RTON -> rtonContent()
                            PcpPage.SCRIPTS -> ScriptsPage(
                                onEditText = ::openEditor,
                                onToast = showToast
                            )
                        }
                    }
                }
                if (menuProgress > 0.001f) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.36f * menuProgress))
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { menuOpen = false }
                            .semantics { contentDescription = t("nav.close_menu") }
                            .zIndex(1f)
                    )
                }
            }

            SideMenu(
                modifier = Modifier
                    .width(menuWidth)
                    .fillMaxHeight()
                    .graphicsLayer { translationX = menuWidth.toPx() * (menuProgress - 1f) }
                    .zIndex(0f),
                selected = page,
                onPageClick = {
                    page = it
                    sandboxFileApp = null
                    menuOpen = false
                },
                onSettingsClick = {
                    settingsOpen = true
                    languageOpen = false
                },
                onBackClick = ::navigateBack
            )

            AnimatedVisibility(
                visible = settingsOpen,
                enter = slideInHorizontally(
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                    initialOffsetX = { it }
                ),
                exit = slideOutHorizontally(
                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                    targetOffsetX = { it }
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f)
            ) {
                SettingsPage(
                    lang = lang,
                    overlayEnabled = overlayEnabled,
                    languageOpen = languageOpen,
                    onBack = { settingsOpen = false },
                    onOverlayEnabledChange = { enabled ->
                        if (!enabled) FloatingWindowService.stop(context)
                        onOverlayPreferenceChange(enabled)
                    },
                    onLanguageClick = { languageOpen = true },
                    onLanguageDismiss = { languageOpen = false },
                    onLanguageChange = { code ->
                        if (code != lang) {
                            onLanguageChange(code)
                            restartNoticeOpen = true
                        }
                    }
                )
            }

            if (restartNoticeOpen) {
                RestartRequiredDialog(
                    onDismiss = { restartNoticeOpen = false }
                )
            }

            AnimatedVisibility(
                visible = editorVisible,
                enter = slideInHorizontally(
                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                    initialOffsetX = { it }
                ),
                exit = slideOutHorizontally(
                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                    targetOffsetX = { it }
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(3f)
            ) {
                editorDocument?.let { document ->
                    TextEditorPage(
                        document = document,
                        toastState = toastState,
                        onClose = ::closeEditor
                    )
                }
            }

            if (editorLoading) {
                EditorLoadingDialog()
            }

            PcpToastHost(
                state = toastState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .zIndex(4f)
            )
        }
    }
}

/**
 * @brief 显示 PCPToolKit 内部品牌主页。
 */
@Composable
private fun PcpHomePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.pcp_logo_dark),
                contentDescription = null,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "PCPToolKit",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "v1.0.0",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
    }
}

/**
 * @brief 显示带菜单按钮的顶部栏。
 * @param title 页面标题。
 * @param onMenuClick 菜单按钮回调。
 */
@Composable
private fun PcpTopBar(
    title: String,
    showBack: Boolean,
    onMenuClick: () -> Unit
) {
    Surface(color = colorResource(R.color.pcp_top_bar)) {
        Box(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
        ) {
            Text(
                text = title,
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .semantics {
                        contentDescription = if (showBack) {
                            t("sandbox.files.back")
                        } else {
                            t("nav.menu")
                        }
                    }
            ) {
                if (showBack) {
                    BackIcon()
                } else {
                    HamburgerIcon()
                }
            }
        }
    }
}

/**
 * @brief 显示应用侧边菜单。
 * @param current 当前页面。
 * @param onSelect 页面切换回调。
 * @param onSettings 设置入口回调。
 * @param onClose 关闭菜单回调。
 */
@Composable
private fun SideMenu(
    modifier: Modifier,
    selected: PcpPage,
    onPageClick: (PcpPage) -> Unit,
    onSettingsClick: () -> Unit,
    onBackClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        color = colorResource(R.color.pcp_top_bar),
        tonalElevation = 4.dp
    ) {
        Column(
            Modifier.fillMaxSize()
        ) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(64.dp)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "PCPToolKit",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MenuSection(
                    pages = listOf(PcpPage.HOME, PcpPage.SANDBOX),
                    selected = selected,
                    onPageClick = onPageClick
                )
                HorizontalDivider()
                MenuSection(
                    title = t("menu.script_tasks"),
                    pages = listOf(PcpPage.SCRIPTS),
                    selected = selected,
                    onPageClick = onPageClick
                )
                HorizontalDivider()
                MenuSection(title = t("menu.level_editor"))
                HorizontalDivider()
                MenuSection(
                    title = t("menu.dev_tools"),
                    pages = listOf(PcpPage.RTON),
                    selected = selected,
                    onPageClick = onPageClick
                )
                Spacer(Modifier.height(6.dp))
            }

            HorizontalDivider(Modifier.padding(horizontal = 12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.semantics {
                        contentDescription = t("settings.back")
                    }
                ) {
                    BackIcon()
                }
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.semantics { contentDescription = t("settings.open") }
                ) {
                    GearIcon()
                }
            }
        }
    }
}

/**
 * @brief 显示一组菜单项。
 * @param title 分组标题。
 * @param content 菜单内容。
 */
@Composable
private fun MenuSection(
    title: String? = null,
    pages: List<PcpPage> = emptyList(),
    selected: PcpPage? = null,
    onPageClick: (PcpPage) -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Column(
            Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (title != null) {
                Text(
                    text = title,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    color = MenuHeadingColor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            pages.forEach { item ->
                val active = item == selected
                Text(
                    text = t(item.titleKey),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .background(Color.Transparent)
                        .clickable { onPageClick(item) }
                        .padding(horizontal = 9.dp, vertical = 11.dp),
                    color = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * @brief 显示设置页面。
 * @param prefs 应用设置存储。
 * @param onBack 返回回调。
 */
@Composable
private fun SettingsPage(
    lang: String,
    overlayEnabled: Boolean,
    languageOpen: Boolean,
    onBack: () -> Unit,
    onOverlayEnabledChange: (Boolean) -> Unit,
    onLanguageClick: () -> Unit,
    onLanguageDismiss: () -> Unit,
    onLanguageChange: (String) -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(Modifier.fillMaxSize()) {
            SettingsTopBar(onBack)
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
            ) {
                Spacer(Modifier.height(14.dp))
                SettingsSection(t("settings.game")) {
                    OverlaySettingRow(
                        overlayEnabled = overlayEnabled,
                        onEnabledChange = onOverlayEnabledChange
                    )
                }
                HorizontalDivider()
                SettingsSection(t("settings.other")) {
                    LanguageRow(
                        lang = lang,
                        onClick = onLanguageClick
                    )
                    OpenSourceRow()
                }
                Spacer(Modifier.height(18.dp))
            }
        }

        AnimatedVisibility(
            visible = languageOpen,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
        ) {
            LanguagePicker(
                lang = lang,
                onDismiss = onLanguageDismiss,
                onLanguageChange = onLanguageChange
            )
        }
    }
}

/**
 * @brief 显示启动游戏时自动打开悬浮窗的设置项。
 */
@Composable
private fun OverlaySettingRow(
    overlayEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = Settings.canDrawOverlays(context)
        permissionDenied = !granted
        if (granted) onEnabledChange(true)
    }
    val hasPermission = Settings.canDrawOverlays(context)
    val checked = overlayEnabled && hasPermission

    fun changeEnabled(enabled: Boolean) {
        permissionDenied = false
        if (!enabled) {
            onEnabledChange(false)
            return
        }
        if (Settings.canDrawOverlays(context)) {
            onEnabledChange(true)
        } else {
            runCatching {
                permissionLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }.onFailure {
                permissionDenied = true
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { changeEnabled(!checked) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = t("settings.floating_window"),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp)
                )
                Text(
                    text = t("settings.floating_window_desc"),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = ::changeEnabled
            )
        }
        if (permissionDenied) {
            Text(
                text = t("settings.floating_window_permission_denied"),
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * @brief 显示设置页面顶部栏。
 * @param onBack 返回回调。
 */
@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    Surface(color = colorResource(R.color.pcp_top_bar)) {
        Box(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
        ) {
            Text(
                text = t("settings.title"),
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .semantics { contentDescription = t("settings.back") }
            ) {
                BackIcon()
            }
        }
    }
}

/**
 * @brief 显示一组设置项。
 * @param title 分组标题。
 * @param content 设置内容。
 */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                color = SettingsHeadingColor,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            content()
        }
    }
}

/**
 * @brief 显示一个语言选项。
 * @param lang 语言代码。
 * @param onClick 点击回调。
 */
@Composable
private fun LanguageRow(lang: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = t("settings.language"),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp)
            )
            Text(
                text = LANGS.firstOrNull { it.first == lang }?.second ?: lang,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * @brief 显示项目开源地址并提供浏览器跳转。
 */
@Composable
private fun OpenSourceRow() {
    val context = LocalContext.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable {
                runCatching {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/Vacancyo/PCPToolKit")
                        )
                    )
                }
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = t("settings.open_source"),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp)
            )
            Text(
                text = t("settings.open_source_value"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * @brief 显示语言选择对话框。
 * @param current 当前语言代码。
 * @param onSelect 语言选择回调。
 * @param onDismiss 关闭回调。
 */
@Composable
private fun LanguagePicker(
    lang: String,
    onDismiss: () -> Unit,
    onLanguageChange: (String) -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(horizontal = 24.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) },
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column {
                Text(
                    text = t("settings.language"),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                HorizontalDivider()

                LANGS.forEach { (code, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onLanguageChange(code)
                                onDismiss()
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = code == lang,
                            onClick = {
                                onLanguageChange(code)
                                onDismiss()
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                HorizontalDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = t("common.cancel"),
                            color = Color(0xFF2563EB)
                        )
                    }
                }
            }
        }
    }
}

/**
 * @brief 提示语言修改需要重启应用后生效。
 * @param onDismiss 关闭回调。
 */
@Composable
private fun RestartRequiredDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column {
                Text(
                    text = t("settings.restart_required_title"),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                HorizontalDivider()
                Text(
                    text = t("settings.restart_required_message"),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = t("settings.restart_required_close"),
                            color = Color(0xFF2563EB)
                        )
                    }
                }
            }
        }
    }
}

/**
 * @brief 显示文本文件加载提示。
 */
@Composable
private fun EditorLoadingDialog() {
    Dialog(onDismissRequest = {}) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                Text(
                    text = t("editor.loading"),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

/**
 * @brief 绘制菜单图标。
 */
@Composable
private fun HamburgerIcon() {
    val color = MaterialTheme.colorScheme.onSurface
    Column(
        Modifier
            .width(22.dp)
            .drawBehind {
                val stroke = 2.dp.toPx()
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(0f, size.height / 3f),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height / 3f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(0f, size.height * 2f / 3f),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height * 2f / 3f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
            .height(22.dp)
    ) {}
}

/**
 * @brief 绘制返回图标。
 */
@Composable
private fun BackIcon() {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        drawLine(
            color,
            androidx.compose.ui.geometry.Offset(size.width * 0.72f, size.height * 0.5f),
            androidx.compose.ui.geometry.Offset(size.width * 0.28f, size.height * 0.5f),
            stroke,
            StrokeCap.Round
        )
        drawLine(
            color,
            androidx.compose.ui.geometry.Offset(size.width * 0.28f, size.height * 0.5f),
            androidx.compose.ui.geometry.Offset(size.width * 0.48f, size.height * 0.3f),
            stroke,
            StrokeCap.Round
        )
        drawLine(
            color,
            androidx.compose.ui.geometry.Offset(size.width * 0.28f, size.height * 0.5f),
            androidx.compose.ui.geometry.Offset(size.width * 0.48f, size.height * 0.7f),
            stroke,
            StrokeCap.Round
        )
    }
}

/**
 * @brief 绘制设置图标。
 */
@Composable
private fun GearIcon() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(24.dp)) {
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val stroke = 2.dp.toPx()
        repeat(8) { index ->
            val angle = Math.PI * index / 4.0
            val inner = 9.dp.toPx()
            val outer = 11.dp.toPx()
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(
                    center.x + (cos(angle) * inner).toFloat(),
                    center.y + (sin(angle) * inner).toFloat()
                ),
                end = androidx.compose.ui.geometry.Offset(
                    center.x + (cos(angle) * outer).toFloat(),
                    center.y + (sin(angle) * outer).toFloat()
                ),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
        drawCircle(color, radius = 7.dp.toPx(), center = center, style = Stroke(stroke))
        drawCircle(color, radius = 2.5.dp.toPx(), center = center)
    }
}
