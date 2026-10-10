package com.ejden.app.ui.products

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ejden.app.EjdenApp
import com.ejden.app.data.Product
import com.ejden.app.data.ProductFilter
import com.ejden.app.data.ProductGroup
import com.ejden.app.data.ProductsGrouping
import com.ejden.app.data.ProductsViewMode
import com.ejden.app.data.StockStatus
import com.ejden.app.data.filterProducts
import com.ejden.app.data.formatPrice
import com.ejden.app.data.groupByCategory
import com.ejden.app.data.stockStatusOf
import com.ejden.app.ui.components.AppIcons
import com.ejden.app.ui.components.IconSpec
import com.ejden.app.ui.components.ProductIcons
import com.ejden.app.ui.components.StrokeIcon
import com.ejden.app.ui.components.pressable
import com.ejden.app.ui.components.rememberPhoto
import com.ejden.app.ui.components.rememberResumeKey
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.nav.Routes
import com.ejden.app.ui.nav.goBack
import com.ejden.app.ui.nav.open
import com.ejden.app.ui.theme.Ejden
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val FILTERS = listOf(
    ProductFilter.All to "Tous",
    ProductFilter.Available to "En stock",
    ProductFilter.Low to "Stock faible",
    ProductFilter.Out to "Rupture"
)

@Composable
fun ProductsScreen(nav: NavController) {
    val app = LocalContext.current.applicationContext as EjdenApp
    val c = Ejden.colors
    val resumeKey = rememberResumeKey()

    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(resumeKey) {
        products = withContext(Dispatchers.Default) { app.products.getProducts() }
        loaded = true
    }

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ProductFilter.All) }
    var viewMode by remember { mutableStateOf(app.productPrefs.getViewMode()) }
    var grouping by remember { mutableStateOf(app.productPrefs.getGrouping()) }
    var collapsed by remember { mutableStateOf(setOf<String>()) }

    val filtered = remember(products, query, filter) { filterProducts(products, query, filter) }

    var noticeText by remember { mutableStateOf("") }
    var noticeVisible by remember { mutableStateOf(false) }
    var noticeToken by remember { mutableIntStateOf(0) }

    fun notice(text: String) {
        noticeText = text
        noticeVisible = true
        noticeToken += 1
    }

    LaunchedEffect(noticeToken) {
        if (noticeToken > 0) {
            delay(2500)
            noticeVisible = false
        }
    }

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val goAdd = { nav.open(Routes.PRODUCT_ADD) }
    val goEdit = { product: Product -> nav.open(Routes.productEdit(product.id)) }

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 110.dp + navInset)
        ) {
            ProductsHeader(onBack = { nav.goBack() }, onAdd = goAdd)
            Spacer(Modifier.height(24.dp))

            SearchBox(query, { query = it }, onScan = { notice("Scanner : bientôt disponible.") })
            Spacer(Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for ((value, label) in FILTERS) {
                    FilterChip(label, selected = filter == value) { filter = value }
                }
            }
            Spacer(Modifier.height(4.dp))
            Spacer(Modifier.height(25.dp))

            SummaryRow(
                total = products.size,
                mode = viewMode,
                onMode = {
                    viewMode = it
                    app.productPrefs.saveViewMode(it)
                }
            )
            GroupingTabs(grouping) {
                grouping = it
                app.productPrefs.saveGrouping(it)
            }
            Spacer(Modifier.height(15.dp))

            if (loaded) {
                when {
                    products.isEmpty() -> EmptyState(onAdd = goAdd)
                    filtered.isEmpty() -> NoResults()
                    grouping == ProductsGrouping.Category -> {
                        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            for (group in groupByCategory(filtered)) {
                                CategoryGroup(
                                    group = group,
                                    collapsed = group.key in collapsed,
                                    onToggle = {
                                        collapsed = if (group.key in collapsed) collapsed - group.key else collapsed + group.key
                                    },
                                    mode = viewMode,
                                    onEdit = goEdit
                                )
                            }
                        }
                    }
                    else -> ProductCollection(filtered, viewMode, goEdit)
                }
            }
        }

        // Action principale fixe, au-dessus de la liste.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to c.background.copy(alpha = 0f),
                        0.3f to c.background,
                        1f to c.background
                    )
                )
                .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 12.dp + navInset)
        ) {
            AddProductButton(onClick = goAdd)
        }

        AnimatedVisibility(
            noticeVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 18.dp, end = 18.dp, bottom = 84.dp + navInset),
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { 12 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { 12 }
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .softCard(RoundedCornerShape(13.dp))
                    .padding(horizontal = 15.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(noticeText, color = c.text, fontSize = 12.sp, fontWeight = FontWeight(600), textAlign = TextAlign.Center)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Briques de style
// ---------------------------------------------------------------------------

@Composable
private fun Modifier.softCard(shape: Shape): Modifier {
    val c = Ejden.colors
    val glow = Color(0xFF172126).copy(alpha = 0.12f)
    return this
        .shadow(6.dp, shape, ambientColor = glow, spotColor = glow)
        .background(c.surface, shape)
        .border(1.dp, c.border, shape)
}

private fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = drawBehind {
    val w = 1.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(
            width = w,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
        )
    )
}

// ---------------------------------------------------------------------------
// En-tête, recherche, filtres, résumé
// ---------------------------------------------------------------------------

@Composable
private fun ProductsHeader(onBack: () -> Unit, onAdd: () -> Unit) {
    val c = Ejden.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SquareButton(ProductIcons.Back, onBack)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "GESTION",
                color = c.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight(700),
                letterSpacing = 0.14.em
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "Produits",
                color = c.text,
                fontSize = 21.sp,
                fontWeight = FontWeight(750),
                letterSpacing = (-0.02).em
            )
        }
        SquareButton(AppIcons.Plus, onAdd)
    }
}

@Composable
private fun SquareButton(icon: IconSpec, onClick: () -> Unit) {
    val c = Ejden.colors
    Box(
        Modifier.size(44.dp).pressable(0.95f, onClick).softCard(RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center
    ) {
        StrokeIcon(icon, c.text, 21.dp)
    }
}

@Composable
private fun SearchBox(query: String, onQuery: (String) -> Unit, onScan: () -> Unit) {
    val c = Ejden.colors
    val placeholder = if (Ejden.dark) Color(0xFF708187) else Color(0xFF91A0A6)

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .softCard(RoundedCornerShape(16.dp))
            .padding(start = 15.dp, end = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        StrokeIcon(ProductIcons.Search, c.textSecondary, 20.dp)
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(color = c.text, fontSize = 14.sp),
            cursorBrush = SolidColor(c.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text("Rechercher un produit...", color = placeholder, fontSize = 14.sp)
                    }
                    inner()
                }
            }
        )
        Box(
            Modifier
                .size(40.dp)
                .pressable(0.94f, onScan)
                .background(c.primarySoft, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            StrokeIcon(ProductIcons.Scan, c.primary, 20.dp, strokeWidth = 1.7f)
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = Ejden.colors
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .background(if (selected) c.primary else c.surface, CircleShape)
            .border(1.dp, if (selected) c.primary else c.border, CircleShape)
            .tap(onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color.White else c.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight(650)
        )
    }
}

@Composable
private fun SummaryRow(total: Int, mode: ProductsViewMode, onMode: (ProductsViewMode) -> Unit) {
    val c = Ejden.colors
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "Mes produits",
                color = c.text,
                fontSize = 19.sp,
                fontWeight = FontWeight(750),
                letterSpacing = (-0.02).em
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "$total produit" + if (total > 1) "s" else "",
                color = c.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight(600)
            )
        }
        ViewSwitcher(mode, onMode)
    }
}

@Composable
private fun ViewSwitcher(mode: ProductsViewMode, onMode: (ProductsViewMode) -> Unit) {
    val c = Ejden.colors
    val dark = Ejden.dark
    val shape = RoundedCornerShape(12.dp)

    Row(
        Modifier
            .background(if (dark) Color(0xFF183944) else c.primarySoft, shape)
            .border(1.dp, if (dark) Color(0xFF294852) else c.border, shape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ViewButton(ProductIcons.Grid, "Grille", mode == ProductsViewMode.GridView) {
            onMode(ProductsViewMode.GridView)
        }
        ViewButton(ProductIcons.ListView, "Liste", mode == ProductsViewMode.ListView) {
            onMode(ProductsViewMode.ListView)
        }
    }
}

@Composable
private fun ViewButton(icon: IconSpec, label: String, active: Boolean, onClick: () -> Unit) {
    val c = Ejden.colors
    val dark = Ejden.dark
    val shape = RoundedCornerShape(9.dp)
    val color = when {
        active && dark -> Color(0xFF8FD0E4)
        active -> c.primary
        else -> c.textSecondary
    }
    val glow = if (dark) Color.Black.copy(alpha = 0.20f) else c.primary.copy(alpha = 0.10f)

    Row(
        Modifier
            .pressable(0.96f, onClick)
            .then(if (active) Modifier.shadow(2.dp, shape, ambientColor = glow, spotColor = glow) else Modifier)
            .background(
                if (active) (if (dark) Color(0xFF22343A) else c.surface) else Color.Transparent,
                shape
            )
            .heightIn(min = 36.dp)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StrokeIcon(icon, color, 17.dp)
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight(700))
    }
}

@Composable
private fun GroupingTabs(grouping: ProductsGrouping, onGrouping: (ProductsGrouping) -> Unit) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(9.dp)
    val glow = Color(0xFF172126).copy(alpha = 0.12f)

    Row(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .background(c.primarySoft, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for ((value, label) in listOf(ProductsGrouping.All to "Tous", ProductsGrouping.Category to "Par catégorie")) {
            val active = grouping == value
            Box(
                Modifier
                    .weight(1f)
                    .then(if (active) Modifier.shadow(6.dp, shape, ambientColor = glow, spotColor = glow) else Modifier)
                    .background(if (active) c.surface else Color.Transparent, shape)
                    .heightIn(min = 40.dp)
                    .tap { onGrouping(value) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (active) c.primary else c.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight(700)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// États vides
// ---------------------------------------------------------------------------

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    val c = Ejden.colors
    InfoCard(ProductIcons.EmptyBox, "Aucun produit", "Ajoutez votre premier produit pour commencer à gérer votre stock.") {
        Box(
            Modifier
                .heightIn(min = 42.dp)
                .background(c.primary, RoundedCornerShape(12.dp))
                .tap(onAdd)
                .padding(horizontal = 17.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Ajouter un produit", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight(700))
        }
    }
}

@Composable
private fun NoResults() {
    InfoCard(ProductIcons.NoResult, "Aucun résultat", "Aucun produit ne correspond à votre recherche ou à ce filtre.") {}
}

@Composable
private fun InfoCard(icon: IconSpec, title: String, text: String, action: @Composable () -> Unit) {
    val c = Ejden.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface, RoundedCornerShape(22.dp))
            .dashedBorder(c.border, 22.dp)
            .padding(horizontal = 22.dp, vertical = 42.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(58.dp).background(c.primarySoft, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            StrokeIcon(icon, c.primary, 27.dp, strokeWidth = 1.6f)
        }
        Spacer(Modifier.height(17.dp))
        Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Text(
            text,
            modifier = Modifier.widthIn(max = 300.dp),
            color = c.textSecondary,
            fontSize = 12.sp,
            lineHeight = 19.2.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(19.dp))
        action()
    }
}

// ---------------------------------------------------------------------------
// Produits : grille / liste / groupes
// ---------------------------------------------------------------------------

@Composable
private fun ProductCollection(items: List<Product>, mode: ProductsViewMode, onEdit: (Product) -> Unit) {
    if (mode == ProductsViewMode.GridView) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (pair in items.chunked(2)) {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (product in pair) {
                        GridCard(product, { onEdit(product) }, Modifier.weight(1f).fillMaxHeight())
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (product in items) ListCard(product) { onEdit(product) }
        }
    }
}

@Composable
private fun CategoryGroup(
    group: ProductGroup,
    collapsed: Boolean,
    onToggle: () -> Unit,
    mode: ProductsViewMode,
    onEdit: (Product) -> Unit
) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(12.dp)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .background(c.surface, shape)
                .border(1.dp, c.border, shape)
                .tap(onToggle)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                group.name,
                modifier = Modifier.weight(1f),
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                Modifier
                    .defaultMinSize(minWidth = 26.dp)
                    .background(c.primarySoft, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(group.products.size.toString(), color = c.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            CategoryArrow(collapsed, c.textSecondary)
        }
        if (!collapsed) ProductCollection(group.products, mode, onEdit)
    }
}

@Composable
private fun CategoryArrow(collapsed: Boolean, color: Color) {
    val angle by animateFloatAsState(if (collapsed) -90f else 0f, tween(150), label = "arrow")
    Canvas(Modifier.size(12.dp).rotate(angle)) {
        val path = Path().apply {
            moveTo(0.34f * size.width, 0.17f * size.height)
            lineTo(0.5f * size.width, 0.83f * size.height)
        }
        // Chevron « v » : deux bras de 8 dp à 45 degrés.
        val cx = size.width / 2
        val cy = size.height / 2
        val arm = 2.83.dp.toPx()
        val chevron = Path().apply {
            moveTo(cx - 2 * arm, cy - arm)
            lineTo(cx, cy + arm)
            lineTo(cx + 2 * arm, cy - arm)
        }
        drawPath(
            chevron,
            color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Butt, join = StrokeJoin.Miter)
        )
        path.reset()
    }
}

@Composable
private fun GridCard(product: Product, onEdit: () -> Unit, modifier: Modifier) {
    val c = Ejden.colors

    Column(modifier.softCard(RoundedCornerShape(15.dp)).padding(10.dp)) {
        ProductImage(product, Modifier.fillMaxWidth().aspectRatio(1f), 360, 30.dp)

        Column(Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp)) {
            Text(
                product.name,
                color = c.text,
                fontSize = 14.sp,
                lineHeight = 18.9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(5.dp))
            Text(
                formatPrice(product.salePrice),
                color = c.primary,
                fontSize = 14.sp,
                lineHeight = 18.2.sp,
                fontWeight = FontWeight(800)
            )
            Spacer(Modifier.height(7.dp))
            StockChip(product)
            Spacer(Modifier.height(10.dp))
            Spacer(Modifier.weight(1f))
            EditButton(Modifier.fillMaxWidth().heightIn(min = 40.dp), onEdit)
        }
    }
}

@Composable
private fun ListCard(product: Product, onEdit: () -> Unit) {
    val c = Ejden.colors

    Row(
        Modifier.fillMaxWidth().softCard(RoundedCornerShape(14.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ProductImage(product, Modifier.size(58.dp), 160, 25.dp)

        Column(Modifier.weight(1f)) {
            Text(
                product.name,
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            // Dans l'original, le prix de la vue liste garde le style de texte par défaut.
            Text(formatPrice(product.salePrice), color = c.text, fontSize = 16.sp)
            Spacer(Modifier.height(5.dp))
            StockChip(product)
        }

        EditButton(Modifier.defaultMinSize(minWidth = 78.dp, minHeight = 38.dp), onEdit)
    }
}

@Composable
private fun ProductImage(product: Product, modifier: Modifier, targetPx: Int, iconSize: Dp) {
    val c = Ejden.colors
    val bitmap = rememberPhoto(product.photo, targetPx)

    Box(modifier.clip(RoundedCornerShape(11.dp)).background(c.primarySoft), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = product.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            StrokeIcon(ProductIcons.ImagePlaceholder, c.primary, iconSize, strokeWidth = 1.6f)
        }
    }
}

@Composable
private fun StockChip(product: Product) {
    val c = Ejden.colors
    val status = stockStatusOf(product)
    val color = when (status) {
        StockStatus.Out -> c.error
        StockStatus.Low -> c.warning
        StockStatus.Normal -> c.textSecondary
    }
    val dot = when (status) {
        StockStatus.Out -> c.error
        StockStatus.Low -> c.warning
        StockStatus.Normal -> c.success
    }
    val text = when (status) {
        StockStatus.Out -> "Rupture de stock"
        StockStatus.Low -> "Stock faible · ${product.stock}"
        StockStatus.Normal -> "${product.stock} en stock"
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(7.dp).background(dot, CircleShape))
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight(600), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EditButton(modifier: Modifier, onClick: () -> Unit) {
    val c = Ejden.colors
    val dark = Ejden.dark
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val background = when {
        dark && pressed -> Color(0xFF214853)
        dark -> Color(0xFF183944)
        pressed -> Color(0xFFDCEFF4)
        else -> c.primarySoft
    }

    Box(
        modifier
            .background(background, RoundedCornerShape(10.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Modifier",
            color = if (dark) Color(0xFF8FD0E4) else c.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight(700)
        )
    }
}

@Composable
private fun AddProductButton(onClick: () -> Unit) {
    val c = Ejden.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(180), label = "addScale")
    val background by animateColorAsState(
        if (pressed) c.primaryDark else c.primary, tween(180), label = "addColor"
    )
    val shape = RoundedCornerShape(16.dp)
    val glow = c.primary.copy(alpha = 0.22f)

    Row(
        Modifier
            .fillMaxWidth()
            .pressableScale(scale)
            .shadow(14.dp, shape, ambientColor = glow, spotColor = glow)
            .background(background, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .heightIn(min = 52.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StrokeIcon(AppIcons.Plus, Color.White, 19.dp, strokeWidth = 2f)
        Text("Ajouter un produit", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight(750))
    }
}

private fun Modifier.pressableScale(scale: Float): Modifier =
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
