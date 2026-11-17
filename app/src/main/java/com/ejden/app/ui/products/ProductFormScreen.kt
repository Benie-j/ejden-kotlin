package com.ejden.app.ui.products

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.ejden.app.EjdenApp
import com.ejden.app.data.Product
import com.ejden.app.data.ProductField
import com.ejden.app.data.ProductFormInput
import com.ejden.app.data.ProductFormResult
import com.ejden.app.data.getCategories
import com.ejden.app.data.validateEditedProduct
import com.ejden.app.data.validateNewProduct
import com.ejden.app.ui.components.AppIcons
import com.ejden.app.ui.components.FormIcons
import com.ejden.app.ui.components.IconSpec
import com.ejden.app.ui.components.PhotoException
import com.ejden.app.ui.components.PhotoPrep
import com.ejden.app.ui.components.ProductIcons
import com.ejden.app.ui.components.StrokeIcon
import com.ejden.app.ui.components.dashedBorder
import com.ejden.app.ui.components.rememberCameraGate
import com.ejden.app.ui.components.rememberPhoto
import com.ejden.app.ui.scanner.BarcodeScannerDialog
import com.ejden.app.ui.scanner.ScannerStyle
import com.ejden.app.ui.scanner.rememberFormScanner
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.nav.Routes
import com.ejden.app.ui.nav.goBack
import com.ejden.app.ui.nav.returnTo
import com.ejden.app.ui.theme.Ejden
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** productId == null : ajout. Sinon : modification de ce produit. */
@Composable
fun ProductFormScreen(nav: NavController, productId: String?) {
    val app = LocalContext.current.applicationContext as EjdenApp
    val c = Ejden.colors

    var all by remember { mutableStateOf<List<Product>?>(null) }
    LaunchedEffect(Unit) {
        all = withContext(Dispatchers.Default) { app.products.getProducts() }
    }

    val list = all
    val editing = if (productId != null && list != null) list.firstOrNull { it.id == productId } else null

    Box(Modifier.fillMaxSize().background(c.background)) {
        when {
            list == null -> Unit
            productId != null && editing == null -> ProductNotFound(nav)
            else -> ProductForm(nav, app, list, editing)
        }
    }
}

@Composable
private fun ProductNotFound(nav: NavController) {
    val c = Ejden.colors
    Column(Modifier.fillMaxSize()) {
        FormHeader("Modifier le produit", edit = true, onBack = { nav.goBack() })
        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .fillMaxWidth()
                .background(c.surface, RoundedCornerShape(16.dp))
                .border(1.dp, c.border, RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Produit introuvable", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                "Ce produit n'existe plus ou a été supprimé.",
                color = c.textSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(0.dp))
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .heightIn(min = 46.dp)
                    .background(c.primary, RoundedCornerShape(13.dp))
                    .tap { nav.returnTo(Routes.PRODUCTS) }
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Retour aux produits", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Formulaire
// ---------------------------------------------------------------------------

private fun plain(value: Double): String =
    if (value == Math.floor(value) && Math.abs(value) < 1e15) value.toLong().toString() else value.toString()

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductForm(nav: NavController, app: EjdenApp, all: List<Product>, editing: Product?) {
    val c = Ejden.colors
    val isEdit = editing != null
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val defaultThreshold = remember { app.preferences.getDefaultThreshold() }

    var name by rememberSaveable { mutableStateOf(editing?.name ?: "") }
    var category by rememberSaveable { mutableStateOf(editing?.category ?: "") }
    var purchase by rememberSaveable { mutableStateOf(editing?.let { plain(it.purchasePrice) } ?: "") }
    var sale by rememberSaveable { mutableStateOf(editing?.let { plain(it.salePrice) } ?: "") }
    var stock by rememberSaveable { mutableStateOf(editing?.stock?.toString() ?: "") }
    var threshold by rememberSaveable {
        mutableStateOf(
            when {
                editing != null -> editing.stockThreshold.toString()
                defaultThreshold > 0 -> defaultThreshold.toString()
                else -> ""
            }
        )
    }
    var barcode by rememberSaveable { mutableStateOf(editing?.barcode ?: "") }
    var photo by remember { mutableStateOf(editing?.photo) }

    val focus = remember { ProductField.entries.associateWith { FocusRequester() } }
    var known by remember { mutableStateOf(all) }
    val categories = remember(known) { getCategories(known) }

    // Nouveau produit : le nom est prêt à être saisi.
    LaunchedEffect(Unit) {
        if (!isEdit) {
            delay(300)
            try {
                focus[ProductField.Name]?.requestFocus()
            } catch (e: IllegalStateException) {
                // champ pas encore affiché
            }
        }
    }

    // Message temporaire.
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

    // Sortie : retour à la liste après un court délai (le temps de lire le message).
    var saving by remember { mutableStateOf(false) }
    var exitDelay by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(exitDelay) {
        val wait = exitDelay
        if (wait != null) {
            delay(wait)
            nav.returnTo(Routes.PRODUCTS)
        }
    }

    // Photo : appareil photo ou galerie.
    val cameraGate = rememberCameraGate { notice("Accès à la caméra refusé. Autorisez-le dans les réglages.") }
    var showSource by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    fun usePhoto(uri: Uri, temp: File?) {
        scope.launch {
            try {
                photo = withContext(Dispatchers.Default) { PhotoPrep.prepare(context, uri) }
            } catch (e: PhotoException) {
                notice(e.message ?: "Impossible de prendre la photo.")
            } catch (e: Exception) {
                notice("Impossible de prendre la photo.")
            } finally {
                temp?.delete()
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) usePhoto(uri, null)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val uri = cameraUri
        val file = cameraFile
        if (taken && uri != null) usePhoto(uri, file) else file?.delete()
    }

    fun takePicture() {
        try {
            val dir = File(context.cacheDir, "photos").apply { mkdirs() }
            val file = File(dir, "ejden-photo-" + System.currentTimeMillis() + ".jpg")
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            cameraFile = file
            cameraUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            notice("Impossible de prendre la photo.")
        }
    }

    fun submit(another: Boolean = false) {
        if (saving) return
        val input = ProductFormInput(name, purchase, sale, stock, threshold, barcode, category, photo)
        val result = if (editing == null) {
            validateNewProduct(input, known)
        } else {
            validateEditedProduct(input, editing, all)
        }

        when (result) {
            is ProductFormResult.Invalid -> {
                notice(result.message)
                focus[result.field]?.requestFocus()
            }
            is ProductFormResult.Valid -> {
                saving = true
                val ok = if (editing == null) {
                    app.products.addProduct(result.product)
                } else {
                    app.products.updateProduct(result.product)
                }
                if (!ok) {
                    saving = false
                    notice(
                        if (editing == null) "Enregistrement impossible : la mémoire est pleine."
                        else "Impossible d'enregistrer les modifications."
                    )
                } else if (another && editing == null) {
                    notice("Produit enregistré. Ajoutez le suivant.")
                    known = app.products.getProducts()
                    name = ""
                    purchase = ""
                    sale = ""
                    stock = ""
                    barcode = ""
                    photo = null
                    saving = false
                    focus[ProductField.Name]?.requestFocus()
                } else {
                    notice(if (editing == null) "Produit enregistré avec succès." else "Produit modifié avec succès.")
                    exitDelay = 800L
                }
            }
        }
    }

    val scanner = rememberFormScanner(known, editing?.id, { barcode = it }, { notice(it) })
    var confirmDelete by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.fillMaxSize()) {
            FormHeader(
                if (isEdit) "Modifier le produit" else "Ajouter un produit",
                edit = isEdit,
                onBack = { nav.goBack() }
            )

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isEdit) 24.dp else 14.dp)
                ) {
                    if (!isEdit) {
                        FormSection("Photo du produit", "Facultatif", edit = false) {
                            AddPhotoButton(photo) { showSource = true }
                        }
                    }

                    FormSection(if (isEdit) null else "Informations", null, edit = isEdit) {
                        Field("Nom du produit") {
                            FormInput(
                                name, { name = it },
                                placeholder = if (isEdit) "Nom du produit" else "Ex. Coca-Cola 50 cl",
                                edit = isEdit,
                                focusRequester = focus[ProductField.Name],
                                onAction = { focus[ProductField.Purchase]?.requestFocus() }
                            )
                        }
                        Spacer(Modifier.height(if (isEdit) 18.dp else 16.dp))
                        Field("Catégorie (facultatif)") {
                            FormInput(
                                category, { category = it },
                                placeholder = "Ex. Boissons",
                                edit = isEdit,
                                maxLength = 40
                            )
                            if (categories.isNotEmpty()) {
                                FlowRow(
                                    Modifier.padding(top = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (item in categories) {
                                        val selected = category.jsTrimLower() == item.lowercase()
                                        CategoryChip(item, selected) {
                                            category = if (selected) "" else item
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isEdit) {
                        FormSection("Photo", "Facultatif", edit = true) {
                            EditPhotoEditor(
                                photo = photo,
                                onPick = { showSource = true },
                                onRemove = { photo = null }
                            )
                        }
                    }

                    FormSection("Prix", null, edit = isEdit) {
                        Field("Prix d'achat") {
                            FormInput(
                                purchase, { purchase = it },
                                placeholder = "0",
                                edit = isEdit,
                                keyboardType = KeyboardType.Number,
                                digitsOnly = true,
                                suffix = "FCFA",
                                focusRequester = focus[ProductField.Purchase]
                            )
                        }
                        Spacer(Modifier.height(if (isEdit) 18.dp else 16.dp))
                        Field("Prix de vente") {
                            FormInput(
                                sale, { sale = it },
                                placeholder = "0",
                                edit = isEdit,
                                keyboardType = KeyboardType.Number,
                                digitsOnly = true,
                                suffix = "FCFA",
                                focusRequester = focus[ProductField.Sale]
                            )
                            val buyPrice = purchase.toLongOrNull()
                            val sellPrice = sale.toLongOrNull()
                            if (buyPrice != null && sellPrice != null && sellPrice > 0L) {
                                val gain = sellPrice - buyPrice
                                val gainText = com.ejden.app.data.formatMoney(kotlin.math.abs(gain).toDouble())
                                Text(
                                    if (gain < 0L) "Vente à perte : -" + gainText
                                    else "Marge : " + gainText +
                                        (if (buyPrice > 0L) " (" + (gain * 100L / buyPrice) + " %)" else ""),
                                    modifier = Modifier.padding(top = 7.dp),
                                    color = if (gain < 0L) c.error else c.success,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    FormSection("Stock", null, edit = isEdit) {
                        Field(if (isEdit) "Stock actuel" else "Stock initial") {
                            FormInput(
                                stock, { stock = it },
                                placeholder = "0",
                                edit = isEdit,
                                keyboardType = KeyboardType.Number,
                                digitsOnly = true,
                                focusRequester = focus[ProductField.Stock],
                                imeAction = ImeAction.Done,
                                onAction = { submit() }
                            )
                        }
                        Spacer(Modifier.height(if (isEdit) 18.dp else 16.dp))
                        Field("Seuil d'alerte", hint = if (isEdit) null else "Facultatif") {
                            FormInput(
                                threshold, { threshold = it },
                                placeholder = "Ex. 5",
                                edit = isEdit,
                                keyboardType = KeyboardType.Number,
                                digitsOnly = true,
                                focusRequester = focus[ProductField.Threshold]
                            )
                            Text(
                                if (isEdit) "EJDEN vous alertera lorsque le stock atteindra ce niveau."
                                else "EJDEN vous avertira lorsque le stock atteindra ce niveau.",
                                modifier = Modifier.padding(top = if (isEdit) 7.dp else 0.dp),
                                color = c.textSecondary,
                                fontSize = if (isEdit) 12.sp else 11.sp,
                                lineHeight = if (isEdit) 18.sp else 16.5.sp
                            )
                        }
                    }

                    FormSection("Code-barres", "Facultatif", edit = isEdit) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BarcodeInput(
                                barcode, { barcode = it },
                                edit = isEdit,
                                focusRequester = focus[ProductField.Barcode],
                                onScan = { scanner.open() }
                            )
                            Text(
                                "Saisissez le code ou utilisez l'appareil photo pour le scanner.",
                                modifier = Modifier.padding(start = 2.dp),
                                color = c.textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.4.sp
                            )
                        }
                    }

                    if (isEdit) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            FormButton("Annuler", filled = false, glow = false) { nav.goBack() }
                            FormButton("Enregistrer les modifications", filled = true, glow = false) { submit() }
                        }
                    }

                    if (editing != null) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DeleteButton(enabled = !saving) { confirmDelete = true }
                            Text(
                                "Les ventes déjà enregistrées restent dans l'historique.",
                                color = c.textSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(Modifier.height((if (isEdit) 28.dp else 32.dp) + navInset))
                }
            }

            if (!isEdit) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(c.surface)
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.weight(1f)) {
                        FormButton("Enregistrer", filled = true, glow = false) { submit() }
                    }
                    Box(Modifier.weight(1f)) {
                        FormButton("Enregistrer + nouveau", filled = false, glow = false) { submit(another = true) }
                    }
                }
            }
        }

        AnimatedVisibility(
            noticeVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp + navInset),
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { 20 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { 20 }
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(c.text, RoundedCornerShape(13.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    noticeText,
                    color = c.surface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight(600),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    BarcodeScannerDialog(scanner, ScannerStyle.Plain, "Scanner un code-barres")
    if (showSource) {
        AlertDialog(
            onDismissRequest = { showSource = false },
            title = { Text("Photo") },
            text = {
                Column {
                    TextButton(onClick = {
                        showSource = false
                        cameraGate { takePicture() }
                    }) { Text("Prendre une photo") }
                    TextButton(onClick = {
                        showSource = false
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) { Text("Choisir dans la galerie") }
                }
            },
            confirmButton = { TextButton(onClick = { showSource = false }) { Text("Annuler") } }
        )
    }

    if (confirmDelete && editing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = {
                Text(
                    "Supprimer « ${editing.name} » ?\n\n" +
                        "Cette action est définitive. Les ventes déjà " +
                        "enregistrées restent dans l'historique."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    if (!app.products.deleteProduct(editing.id)) {
                        notice("Impossible de supprimer ce produit.")
                    } else {
                        saving = true
                        notice("Produit supprimé.")
                        exitDelay = 700L
                    }
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } }
        )
    }
}

private fun String.jsTrimLower(): String = this.trim().lowercase()

// ---------------------------------------------------------------------------
// Briques du formulaire
// ---------------------------------------------------------------------------

@Composable
private fun FormHeader(title: String, edit: Boolean, onBack: () -> Unit) {
    val c = Ejden.colors
    Column(Modifier.fillMaxWidth().background(c.surface.copy(alpha = 0.94f)).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(if (edit) 42.dp else 40.dp).tap(onBack),
                contentAlignment = Alignment.Center
            ) {
                StrokeIcon(ProductIcons.Back, c.text, 22.dp, strokeWidth = 2f)
            }
            if (edit) {
                Text(
                    title,
                    modifier = Modifier.weight(1f).padding(start = 14.dp),
                    color = c.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    color = c.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.size(40.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    }
}

@Composable
private fun FormSection(
    title: String?,
    hint: String?,
    edit: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(16.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface, shape)
            .border(1.dp, c.border, shape)
            .padding(horizontal = if (edit) 18.dp else 16.dp, vertical = 18.dp)
    ) {
        if (title != null) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = if (edit) 18.dp else 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (hint != null) {
                    Text(
                        hint,
                        color = c.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (edit) FontWeight(600) else FontWeight.Normal
                    )
                }
            }
        }
        content()
    }
}

@Composable
private fun Field(label: String, hint: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = Ejden.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (hint != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, color = c.text, fontSize = 13.sp, fontWeight = FontWeight(600))
                Text(hint, color = c.textSecondary, fontSize = 12.sp)
            }
        } else {
            Text(label, color = c.text, fontSize = 13.sp, fontWeight = FontWeight(600))
        }
        Column { content() }
    }
}

/** Anneau de focus (le box-shadow 0 0 0 3px du CSS), dessiné autour du champ. */
private fun Modifier.focusRing(visible: Boolean, color: Color, radius: Dp): Modifier = drawBehind {
    if (visible) {
        val ring = 3.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(-ring / 2, -ring / 2),
            size = Size(size.width + ring, size.height + ring),
            cornerRadius = CornerRadius(radius.toPx() + ring / 2),
            style = Stroke(ring)
        )
    }
}

@Composable
private fun FormInput(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    edit: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    digitsOnly: Boolean = false,
    suffix: String? = null,
    maxLength: Int? = null,
    focusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Next,
    onAction: (() -> Unit)? = null
) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(12.dp)
    var focused by remember { mutableStateOf(false) }
    val background = if (edit || focused) c.surface else c.background

    Box(
        Modifier
            .fillMaxWidth()
            .height(if (edit) 50.dp else 48.dp)
            .focusRing(focused, c.primary.copy(alpha = 0.10f), 12.dp)
            .background(background, shape)
            .border(1.dp, if (focused) c.primary else c.border, shape),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = { raw ->
                var text = raw
                if (digitsOnly) text = text.filter { it.isDigit() }
                if (maxLength != null) text = text.take(maxLength)
                onChange(text)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = if (suffix != null) 68.dp else 14.dp)
                .onFocusChanged { focused = it.isFocused }
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            singleLine = true,
            textStyle = TextStyle(color = c.text, fontSize = 15.sp),
            cursorBrush = SolidColor(c.primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = {
                if (onAction != null) onAction() else defaultKeyboardAction(imeAction)
            }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = Color(0xFF9AA7AC), fontSize = 15.sp)
                    }
                    inner()
                }
            }
        )
        if (suffix != null) {
            Text(
                suffix,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp),
                color = c.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight(600)
            )
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .background(if (selected) c.primarySoft else c.surface, shape)
            .border(1.dp, if (selected) c.primary else c.border, shape)
            .tap(onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) c.primary else c.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight(600),
            maxLines = 1
        )
    }
}

@Composable
private fun BarcodeInput(
    value: String,
    onChange: (String) -> Unit,
    edit: Boolean,
    focusRequester: FocusRequester?,
    onScan: () -> Unit
) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(14.dp)
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> c.surface
        edit && Ejden.dark -> c.surface
        else -> c.background
    }

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .focusRing(focused, c.primary.copy(alpha = 0.10f), 14.dp)
            .background(background, shape)
            .border(1.dp, if (focused) c.primary else c.border, shape)
            .padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StrokeIcon(FormIcons.Barcode, c.textSecondary, 21.dp, strokeWidth = 1.7f)

        Box(Modifier.weight(1f).height(42.dp), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 14.sp),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text("Saisir le code-barres", color = Color(0xFF9AA7AC), fontSize = 14.sp)
                        }
                        inner()
                    }
                }
            )
        }

        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        Row(
            Modifier
                .defaultMinSize(minWidth = 92.dp)
                .height(42.dp)
                .background(if (pressed) c.primaryDark else c.primary, RoundedCornerShape(10.dp))
                .clickable(interactionSource = interaction, indication = null, onClick = onScan)
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StrokeIcon(FormIcons.ScanSmall, Color.White, 18.dp, strokeWidth = 1.7f)
            Text("Scanner", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight(700))
        }
    }
}

// ---------------------------------------------------------------------------
// Photo
// ---------------------------------------------------------------------------

@Composable
private fun AddPhotoButton(photo: String?, onPick: () -> Unit) {
    val c = Ejden.colors
    val bitmap = rememberPhoto(photo, 160)
    val shape = RoundedCornerShape(14.dp)

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .dashedBorder(c.border, 14.dp, 1.5.dp)
            .background(c.background, shape)
            .tap(onPick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(c.primarySoft),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Aperçu du produit",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                StrokeIcon(FormIcons.PhotoAdd, c.primary, 24.dp)
            }
        }
        // Dans l'original, ce bloc n'est pas mis en forme : titre et description se suivent sur la même ligne.
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(if (photo == null) "Ajouter une photo" else "Photo sélectionnée")
                }
                append(" ")
                append(if (photo == null) "Appareil photo ou galerie" else "Photo prête à être enregistrée")
            },
            modifier = Modifier.weight(1f),
            color = c.text,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun EditPhotoEditor(photo: String?, onPick: () -> Unit, onRemove: () -> Unit) {
    val c = Ejden.colors
    val bitmap = rememberPhoto(photo, 600)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.background)
                .border(1.dp, c.border, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Photo du produit",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StrokeIcon(FormIcons.PhotoAdd, c.textSecondary, 32.dp, strokeWidth = 1.5f)
                    Text("Aucune photo", color = c.textSecondary, fontSize = 12.sp)
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .dashedBorder(c.border, 14.dp)
                .background(c.background, RoundedCornerShape(14.dp))
                .tap(onPick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(48.dp).background(c.primarySoft, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                StrokeIcon(FormIcons.PhotoAdd, c.primary, 24.dp)
            }
            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Modifier la photo", color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Appareil photo ou galerie", color = c.textSecondary, fontSize = 12.sp)
            }
        }

        if (photo != null) {
            val red = c.error
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .border(1.dp, red.copy(alpha = 0.25f), RoundedCornerShape(11.dp))
                    .tap(onRemove),
                contentAlignment = Alignment.Center
            ) {
                Text("Supprimer la photo", color = red, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Boutons
// ---------------------------------------------------------------------------

@Composable
private fun FormButton(label: String, filled: Boolean, glow: Boolean, onClick: () -> Unit) {
    val c = Ejden.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(13.dp)
    val background by animateColorAsState(
        when {
            filled && pressed -> c.primaryDark
            filled -> c.primary
            pressed -> c.background
            else -> c.surface
        },
        tween(150),
        label = "formButton"
    )
    val shadow = c.primary.copy(alpha = 0.18f)

    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .then(if (glow) Modifier.shadow(8.dp, shape, ambientColor = shadow, spotColor = shadow) else Modifier)
            .background(background, shape)
            .then(if (filled) Modifier else Modifier.border(1.dp, c.border, shape))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (filled) Color.White else c.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DeleteButton(enabled: Boolean, onClick: () -> Unit) {
    val c = Ejden.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(13.dp)

    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .background(if (pressed) Color(0xFFFDF1F1) else c.surface, shape)
            .border(1.dp, c.error, shape)
            .then(
                if (enabled) {
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Supprimer ce produit",
            color = c.error.copy(alpha = if (enabled) 1f else 0.5f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
