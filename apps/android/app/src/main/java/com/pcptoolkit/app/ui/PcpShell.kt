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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.pcptoolkit.app.LANGS
import com.pcptoolkit.app.feature.home.HomePage
import com.pcptoolkit.app.feature.script.ScriptsPage
import com.pcptoolkit.app.t
import kotlin.math.cos
import kotlin.math.sin

private val MenuHeadingColor = Color(0xFF5E5E5E)
private val SettingsHeadingColor = Color(0xFF187BDE)

/**
 * @brief 应用主界面中的页面。
 */
private enum class PcpPage(val titleKey: String) {
    HOME("nav.home"),
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
    onLanguageChange: (String) -> Unit,
    rtonContent: @Composable () -> Unit
) {
    var page by remember { mutableStateOf(PcpPage.HOME) }
    var menuOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var languageOpen by remember { mutableStateOf(false) }

    val menuProgress by animateFloatAsState(
        targetValue = if (menuOpen) 1f else 0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "menu"
    )

    BackHandler(enabled = settingsOpen || languageOpen || menuOpen) {
        when {
            languageOpen -> languageOpen = false
            settingsOpen -> settingsOpen = false
            else -> menuOpen = false
        }
    }

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
                    .graphicsLayer { translationX = menuWidth.toPx() * menuProgress }
                    .zIndex(1f)
            ) {
                Column(Modifier.fillMaxSize()) {
                    PcpTopBar(
                        title = t(page.titleKey),
                        onMenuClick = { menuOpen = !menuOpen }
                    )
                    Box(Modifier.weight(1f)) {
                        when (page) {
                            PcpPage.HOME -> HomePage()
                            PcpPage.RTON -> rtonContent()
                            PcpPage.SCRIPTS -> ScriptsPage()
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
                    menuOpen = false
                },
                onSettingsClick = {
                    settingsOpen = true
                    languageOpen = false
                }
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
                    languageOpen = languageOpen,
                    onBack = { settingsOpen = false },
                    onLanguageClick = { languageOpen = true },
                    onLanguageDismiss = { languageOpen = false },
                    onLanguageChange = onLanguageChange
                )
            }
        }
    }
}

/**
 * @brief 显示带菜单按钮的顶部栏。
 * @param title 页面标题。
 * @param onMenuClick 菜单按钮回调。
 */
@Composable
private fun PcpTopBar(title: String, onMenuClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
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
                    .semantics { contentDescription = t("nav.menu") }
            ) {
                HamburgerIcon()
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
    onSettingsClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Text(
                text = "PCPToolKit",
                modifier = Modifier.padding(start = 14.dp, top = 22.dp, end = 10.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Spacer(Modifier.height(18.dp))

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MenuSection(
                    pages = listOf(PcpPage.HOME),
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
                    .padding(end = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
    languageOpen: Boolean,
    onBack: () -> Unit,
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
                SettingsSection(t("settings.general"))
                HorizontalDivider()
                SettingsSection(t("settings.appearance"))
                HorizontalDivider()
                SettingsSection(t("settings.other")) {
                    LanguageRow(
                        lang = lang,
                        onClick = onLanguageClick
                    )
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
 * @brief 显示设置页面顶部栏。
 * @param onBack 返回回调。
 */
@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
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
