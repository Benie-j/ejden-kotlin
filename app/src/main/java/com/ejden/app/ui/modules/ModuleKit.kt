package com.ejden.app.ui.modules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ejden.app.ui.components.ProductIcons
import com.ejden.app.ui.components.StrokeIcon
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.theme.Ejden
import kotlinx.coroutines.delay

// Boîte à outils des écrans « module » (Clients, Crédits, Dépenses, Caisse...).
// Elle reproduit modules.css : clair des pages, sombre du tableau de bord.

class ModuleToastState {
    var text by mutableStateOf("")
    var visible by mutableStateOf(false)
    var token by mutableIntStateOf(0)

    fun show(message: String) {
        text = message
        visible = true
        token += 1
    }
}

@Composable
fun rememberModuleToast(durationMs: Long = 3000): ModuleToastState {
    val state = remember { ModuleToastState() }
    LaunchedEffect(state.token) {
        if (state.token > 0) {
            delay(durationMs)
            state.visible = false
        }
    }
    return state
}

/** Page « module » : en-tête, contenu défilant (640 dp max) et message en bas. */
@Composable
fun ModuleScaffold(
    eyebrow: String,
    title: String,
    onBack: () -> Unit,
    toast: ModuleToastState,
    listState: LazyListState = rememberLazyListState(),
    maxWidth: androidx.compose.ui.unit.Dp = 640.dp,
    content: LazyListScope.() -> Unit
) {
    val c = Ejden.colors
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(c.background), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = maxWidth).fillMaxHeight().statusBarsPadding().imePadding(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 40.dp + navInset)
        ) {
            item { ModuleHeader(eyebrow, title, onBack) }
            content()
        }

        AnimatedVisibility(
            toast.visible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp + navInset),
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { 12 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { 12 }
        ) {
            Text(
                toast.text,
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .background(c.text, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                color = c.surface,
                fontSize = 13.sp,
                fontWeight = FontWeight(600),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ModuleHeader(eyebrow: String, title: String, onBack: () -> Unit) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.padding(bottom = 16.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(44.dp).tap(onBack).background(c.surface, shape).border(1.dp, c.border, shape),
            contentAlignment = Alignment.Center
        ) {
            StrokeIcon(ProductIcons.Back, c.text, 20.dp, strokeWidth = 2f)
        }
        Column {
            Text(eyebrow, color = c.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em)
            Text(title, color = c.text, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------------------------------------------------------------------------
// Résumé
// ---------------------------------------------------------------------------

@Composable
fun SummaryCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    wide: Boolean = false,
    negative: Boolean = false,
    valueColor: Color? = null
) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)
    val background = when {
        wide && negative -> c.error
        wide -> c.primary
        else -> c.surface
    }
    val border = when {
        wide && negative -> c.error
        wide -> c.primary
        else -> c.border
    }
    val soft = if (wide) Color.White.copy(alpha = 0.8f) else c.textSecondary

    Column(
        modifier.background(background, shape).border(1.dp, border, shape).padding(14.dp)
    ) {
        Text(label, color = soft, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            color = if (wide) Color.White else (valueColor ?: c.text),
            fontSize = if (wide) 26.sp else 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (unit != null) Text(unit, color = soft, fontSize = 10.sp)
    }
}

// ---------------------------------------------------------------------------
// Formulaires
// ---------------------------------------------------------------------------

@Composable
fun ModuleSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .padding(bottom = 14.dp)
            .fillMaxWidth()
            .background(c.surface, shape)
            .border(1.dp, c.border, shape)
            .padding(16.dp)
    ) {
        Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
fun ModuleField(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = Ejden.colors.text, fontSize = 12.sp, fontWeight = FontWeight(600))
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
fun ModuleTextField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    invalid: Boolean = false,
    maxLength: Int? = null,
    focusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Next
) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(c.surface, shape)
            .border(
                if (focused) 2.dp else 1.dp,
                when {
                    focused -> c.primary
                    invalid -> c.error
                    else -> c.border
                },
                shape
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = { raw -> onChange(if (maxLength != null) raw.take(maxLength) else raw) },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            singleLine = true,
            textStyle = TextStyle(color = c.text, fontSize = 14.sp),
            cursorBrush = SolidColor(c.primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, color = Color(0xFF757575), fontSize = 14.sp)
                    inner()
                }
            }
        )
    }
}

@Composable
fun ModuleSearch(value: String, onChange: (String) -> Unit, placeholder: String) {
    ModuleTextField(
        value, { onChange(it) }, placeholder,
        modifier = Modifier.padding(bottom = 12.dp),
        maxLength = 80,
        imeAction = ImeAction.Search
    )
}

// ---------------------------------------------------------------------------
// Boutons
// ---------------------------------------------------------------------------

enum class ModuleButtonStyle { Primary, Normal, Danger, DangerSolid }

/** Bouton d'action pleine largeur (.action-button). */
@Composable
fun ModuleActionButton(
    label: String,
    style: ModuleButtonStyle,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)
    val (background, border, foreground) = when (style) {
        ModuleButtonStyle.Primary -> Triple(c.primary, c.primary, Color.White)
        ModuleButtonStyle.Normal -> Triple(c.surface, c.border, c.text)
        ModuleButtonStyle.Danger -> Triple(c.surface, c.error, c.error)
        ModuleButtonStyle.DangerSolid -> Triple(c.error, c.error, Color.White)
    }

    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .background(background, shape)
            .border(1.dp, border, shape)
            .then(if (enabled) Modifier.tap(onClick) else Modifier)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = foreground, fontSize = 14.sp, fontWeight = FontWeight(600))
    }
}

/** Boutons d'un formulaire : principal (large) et secondaire. */
@Composable
fun PrimaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Ejden.colors
    Box(
        modifier.heightIn(min = 48.dp).background(c.primary, RoundedCornerShape(10.dp)).tap(onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SecondaryButton(label: String, onClick: () -> Unit) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .background(c.surface, shape)
            .border(1.dp, c.border, shape)
            .tap(onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = c.text, fontSize = 14.sp, fontWeight = FontWeight(600))
    }
}

@Composable
fun FilterPill(label: String, active: Boolean, onClick: () -> Unit) {
    val c = Ejden.colors
    Box(
        Modifier
            .height(38.dp)
            .background(if (active) c.primary else c.surface, CircleShape)
            .border(1.dp, if (active) c.primary else c.border, CircleShape)
            .tap(onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (active) Color.White else c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight(600))
    }
}

/** Suppression en deux temps : un premier appui demande confirmation. */
@Composable
fun DeleteControl(label: String, question: String, onConfirm: () -> Unit) {
    val c = Ejden.colors
    var confirming by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    if (!confirming) {
        ModuleActionButton(label, ModuleButtonStyle.Danger) { confirming = true }
    } else {
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.errorSoft, RoundedCornerShape(10.dp))
                .border(1.dp, c.error, RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(question, color = c.text, fontSize = 13.sp, lineHeight = 18.2.sp)
            ModuleActionButton("Oui, supprimer", ModuleButtonStyle.DangerSolid, enabled = !busy) {
                busy = true
                onConfirm()
            }
            ModuleActionButton("Non, garder", ModuleButtonStyle.Normal) { confirming = false }
        }
    }
}

// ---------------------------------------------------------------------------
// Listes
// ---------------------------------------------------------------------------

/** Carte dépliable : résumé toujours visible, détail au toucher. */
@Composable
fun RowCard(
    expanded: Boolean,
    onToggle: () -> Unit,
    summary: @Composable RowScope.() -> Unit,
    details: @Composable ColumnScope.() -> Unit
) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .background(c.surface, shape)
            .border(1.dp, c.border, shape)
            .clip(shape)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp)
                .tap(onToggle)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = summary
        )
        if (expanded) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            Column(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 14.dp),
                content = details
            )
        }
    }
}

enum class BadgeTone { Success, Debt, Overdue }

@Composable
fun Badge(text: String, tone: BadgeTone) {
    val c = Ejden.colors
    val (background, foreground) = when (tone) {
        BadgeTone.Success -> c.successSoft to c.success
        BadgeTone.Debt -> c.warningSoft to c.warning
        BadgeTone.Overdue -> c.errorSoft to c.error
    }
    Text(
        text,
        modifier = Modifier.background(background, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
        color = foreground,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun RowMain(title: String, subtitle: String, modifier: Modifier) {
    val c = Ejden.colors
    Column(modifier) {
        Text(
            title,
            color = c.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        Text(subtitle, color = c.textSecondary, fontSize = 12.sp)
    }
}

@Composable
fun ModuleDetailTitle(text: String) {
    Text(
        text.uppercase(),
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        color = Ejden.colors.textSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.06.em
    )
}

@Composable
fun ModuleDetailRow(
    label: String,
    value: String,
    valueColor: Color? = null,
    onValueClick: (() -> Unit)? = null
) {
    val c = Ejden.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = c.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            modifier = Modifier
                .weight(1f, fill = false)
                .then(if (onValueClick != null) Modifier.tap(onValueClick) else Modifier),
            color = valueColor ?: c.text,
            fontSize = 13.sp,
            fontWeight = FontWeight(600),
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun ModuleEmpty(title: String, text: String) {
    val c = Ejden.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(
            text,
            modifier = Modifier.padding(top = 6.dp),
            color = c.textSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}
