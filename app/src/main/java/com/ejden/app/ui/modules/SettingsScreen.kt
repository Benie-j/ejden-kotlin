package com.ejden.app.ui.modules

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ejden.app.EjdenApp
import com.ejden.app.data.DEFAULT_INVOICE_TERMS
import com.ejden.app.data.DEFAULT_QUOTE_TERMS
import com.ejden.app.data.DocumentSettings
import com.ejden.app.data.SettingsField
import com.ejden.app.data.SettingsFormInput
import com.ejden.app.data.SettingsFormResult
import com.ejden.app.data.ShopSettings
import com.ejden.app.data.TextSize
import com.ejden.app.data.ThemeChoice
import com.ejden.app.data.UserProfile
import com.ejden.app.data.greetingFor
import com.ejden.app.data.jsTrim
import com.ejden.app.data.lastBackupLabel
import com.ejden.app.data.plainNumber
import com.ejden.app.data.receiptPreviewText
import com.ejden.app.data.validateSettingsForm
import com.ejden.app.ui.components.AppIcons
import com.ejden.app.ui.components.IconSpec
import com.ejden.app.ui.components.PhotoPrep
import com.ejden.app.ui.components.ProductIcons
import com.ejden.app.ui.components.SettingsIcons
import com.ejden.app.ui.components.StrokeIcon
import com.ejden.app.ui.components.rememberPhoto
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.nav.Routes
import com.ejden.app.ui.nav.goBack
import com.ejden.app.ui.nav.open
import com.ejden.app.ui.nav.returnTo
import com.ejden.app.ui.theme.Ejden
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

private class Category(val id: String, val title: String, val subtitle: String, val icon: IconSpec)

private val CATEGORIES = listOf(
    Category("mon-espace", "Mon espace", "Activité, coordonnées et documents", SettingsIcons.Home),
    Category("equipe", "Équipe & accès", "Membres, rôles et appareils", SettingsIcons.Team),
    Category("ventes", "Ventes", "Paiements et crédits", SettingsIcons.Sales),
    Category("stock", "Produits & stock", "Alertes, codes-barres et photos", SettingsIcons.Stock),
    Category("clients", "Clients", "Fiches et historique", SettingsIcons.Client),
    Category("apparence", "Apparence", "Thème, texte et animations", SettingsIcons.Appearance),
    Category("donnees", "Données & sauvegarde", "Sauvegarde, restauration et export", SettingsIcons.Data),
    Category("securite", "Sécurité", "Protection et accès", SettingsIcons.Security),
    Category("apropos", "À propos", "Version, aide et confidentialité", SettingsIcons.About)
)

private val PANEL_TITLES = mapOf(
    "mon-espace" to "Mon espace",
    "equipe" to "Équipe & accès",
    "ventes" to "Ventes",
    "stock" to "Produits & stock",
    "clients" to "Clients",
    "apparence" to "Apparence",
    "donnees" to "Données & sauvegarde",
    "securite" to "Sécurité",
    "apropos" to "À propos d'EJDEN"
)

@Composable
fun SettingsScreen(nav: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as EjdenApp
    val toast = rememberModuleToast(2500)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Catégorie ouverte (null : menu). Retour ramène d'abord au menu.
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = panel != null) { panel = null }
    LaunchedEffect(panel) { listState.scrollToItem(0) }

    ModuleScaffold(
        eyebrow = if (panel == null) "CONFIGURATION" else "PARAMÈTRES",
        title = PANEL_TITLES[panel] ?: "Paramètres",
        onBack = { if (panel != null) panel = null else nav.goBack() },
        toast = toast,
        listState = listState,
        maxWidth = 560.dp
    ) {
        when (panel) {
            null -> item { Hub { panel = it } }
            "mon-espace" -> item { MySpacePanel(app, toast, scope) }
            "equipe" -> item { TeamPanel(app) }
            "ventes" -> item { SalesPanel(app, toast) }
            "stock" -> item { StockPanel(app, toast) }
            "clients" -> item {
                SettingsSection("Clients", "Les informations et l'historique d'un client se consultent dans sa fiche.") {}
            }
            "apparence" -> item { AppearancePanel(app, toast) }
            "donnees" -> item { DataPanel(app, nav, toast, scope) }
            "securite" -> item { SecurityPanel() }
            "apropos" -> item { AboutPanel() }
        }
    }
}

// ---------------------------------------------------------------------------
// Briques de la page
// ---------------------------------------------------------------------------

@Composable
private fun SettingsSection(title: String, help: String? = null, content: @Composable ColumnScope.() -> Unit) {
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
        if (help != null) {
            Text(
                help,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                color = c.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.8.sp
            )
        }
        content()
    }
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ejden.colors.border))
}

@Composable
private fun SoonRow(title: String, subtitle: String, last: Boolean, badge: String = "Bientôt", ok: Boolean = false) {
    val c = Ejden.colors
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, modifier = Modifier.padding(top = 2.dp), color = c.textSecondary, fontSize = 12.sp, lineHeight = 16.8.sp)
            }
            Text(
                badge,
                modifier = Modifier
                    .background(if (ok) c.successSoft else c.warningSoft, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                color = if (ok) c.success else c.warning,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (!last) RowDivider()
    }
}

@Composable
private fun InfoRow(label: String, value: String, last: Boolean) {
    val c = Ejden.colors
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = c.textSecondary, fontSize = 13.sp)
            Text(value, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
        }
        if (!last) RowDivider()
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    val c = Ejden.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, modifier = Modifier.padding(top = 2.dp), color = c.textSecondary, fontSize = 12.sp, lineHeight = 16.8.sp)
        }
        SettingsSwitch(checked, onToggle)
    }
}

@Composable
private fun SettingsSwitch(checked: Boolean, onToggle: () -> Unit) {
    val c = Ejden.colors
    val offset by animateDpAsState(if (checked) 23.dp else 3.dp, tween(180), label = "switch")
    Box(
        Modifier
            .size(width = 50.dp, height = 30.dp)
            .background(if (checked) c.primary else c.border, CircleShape)
            .tap(onToggle)
    ) {
        Box(Modifier.offset(x = offset, y = 3.dp).size(24.dp).background(Color.White, CircleShape))
    }
}

@Composable
private fun ChoiceGroup(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((id, label) in options) {
            val active = id == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .background(if (active) c.primary else c.surface, shape)
                    .border(1.dp, if (active) c.primary else c.border, shape)
                    .tap { onSelect(id) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (active) Color.White else c.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TextAreaField(value: String, onChange: (String) -> Unit, placeholder: String, rows: Int, maxLength: Int) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(12.dp)
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .background(c.surface, shape)
            .border(if (focused) 2.dp else 1.dp, if (focused) c.primary else c.border, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = { onChange(it.take(maxLength)) },
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            minLines = rows,
            textStyle = TextStyle(color = c.text, fontSize = 16.sp),
            cursorBrush = SolidColor(c.primary),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, color = Color(0xFF757575), fontSize = 16.sp)
                    inner()
                }
            }
        )
    }
}

/** Champ numérique des réglages (fond de la page, 48 dp). */
@Composable
private fun SettingsNumberField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    onBlur: () -> Unit
) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)
    var focused by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(c.background, shape)
            .border(if (focused) 2.dp else 1.dp, if (focused) c.primary else c.border, shape)
            .padding(horizontal = 14.dp)
            .onFocusChanged {
                if (hadFocus && !it.hasFocus) onBlur()
                hadFocus = it.hasFocus
                focused = it.hasFocus
            },
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = { onChange(it.filter { ch -> ch.isDigit() }.take(7)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = TextStyle(color = c.text, fontSize = 15.sp),
            cursorBrush = SolidColor(c.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, color = Color(0xFF757575), fontSize = 15.sp)
                    inner()
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Menu des catégories
// ---------------------------------------------------------------------------

@Composable
private fun Hub(onOpen: (String) -> Unit) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().background(c.surface, shape).border(1.dp, c.border, shape).clip(shape)) {
        CATEGORIES.forEachIndexed { index, category ->
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            Column(
                Modifier
                    .background(if (pressed) c.primarySoft else Color.Transparent)
                    .clickable(interactionSource = interaction, indication = null) { onOpen(category.id) }
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        Modifier.size(40.dp).background(c.primarySoft, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        StrokeIcon(category.icon, c.primary, 20.dp, strokeWidth = 2f)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(category.title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(category.subtitle, modifier = Modifier.padding(top = 2.dp), color = c.textSecondary, fontSize = 12.sp)
                    }
                    StrokeIcon(AppIcons.Arrow, c.textSecondary, 18.dp, strokeWidth = 2f)
                }
                if (index < CATEGORIES.size - 1) RowDivider()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Mon espace
// ---------------------------------------------------------------------------

@Composable
private fun MySpacePanel(app: EjdenApp, toast: ModuleToastState, scope: kotlinx.coroutines.CoroutineScope) {
    val context = LocalContext.current
    val c = Ejden.colors
    val shop = remember { app.settings.getShop() }
    val user = remember { app.settings.getUser() }
    val docs = remember { app.documents.getSettings() }

    var shopName by rememberSaveable { mutableStateOf(shop.name) }
    var shopPhone by rememberSaveable { mutableStateOf(shop.phone) }
    var firstName by rememberSaveable { mutableStateOf(user.firstName) }
    var lastName by rememberSaveable { mutableStateOf(user.lastName) }
    var userPhone by rememberSaveable { mutableStateOf(user.phone) }

    var address by rememberSaveable { mutableStateOf(docs.address) }
    var email by rememberSaveable { mutableStateOf(docs.email) }
    var website by rememberSaveable { mutableStateOf(docs.website) }
    var taxId by rememberSaveable { mutableStateOf(docs.taxId) }
    var registry by rememberSaveable { mutableStateOf(docs.registry) }
    var paymentInfo by rememberSaveable { mutableStateOf(docs.paymentInfo) }
    var tax by rememberSaveable { mutableStateOf(if (docs.defaultTaxRate > 0.0) plainNumber(docs.defaultTaxRate) else "") }
    var validity by rememberSaveable { mutableStateOf(docs.defaultValidityDays.toString()) }
    var quoteTerms by rememberSaveable { mutableStateOf(docs.quoteTerms) }
    var dueDays by rememberSaveable { mutableStateOf(docs.defaultDueDays.toString()) }
    var invoiceTerms by rememberSaveable { mutableStateOf(docs.invoiceTerms) }
    var footer by rememberSaveable { mutableStateOf(docs.footer) }
    var logo by remember { mutableStateOf(docs.logo) }

    var invalid by remember { mutableStateOf<SettingsField?>(null) }
    val focus = remember { SettingsField.entries.associateWith { FocusRequester() } }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    logo = withContext(Dispatchers.Default) { PhotoPrep.prepare(context, uri) }
                } catch (e: Exception) {
                    toast.show("Logo illisible ou trop lourd. Essayez une autre image.")
                }
            }
        }
    }

    fun save() {
        val input = SettingsFormInput(shopName, shopPhone, firstName, lastName, userPhone, email, tax, validity, dueDays)
        when (val result = validateSettingsForm(input)) {
            is SettingsFormResult.Invalid -> {
                invalid = result.field
                toast.show(result.message)
                try {
                    focus[result.field]?.requestFocus()
                } catch (e: IllegalStateException) {
                    // champ pas affiché
                }
            }
            is SettingsFormResult.Valid -> {
                invalid = null
                // Les enregistrements sont indépendants : un échec est signalé précisément.
                if (!app.settings.saveShop(ShopSettings(result.shopName, result.shopPhone))) {
                    toast.show("Impossible d'enregistrer les informations de l'entreprise.")
                    return
                }
                val documents = DocumentSettings(
                    address = address.jsTrim(),
                    email = result.email,
                    website = website.jsTrim(),
                    taxId = taxId.jsTrim(),
                    registry = registry.jsTrim(),
                    paymentInfo = paymentInfo.jsTrim(),
                    logo = logo,
                    defaultTaxRate = result.tax,
                    defaultValidityDays = result.validity,
                    quoteTerms = quoteTerms.jsTrim().ifEmpty { DEFAULT_QUOTE_TERMS },
                    defaultDueDays = result.dueDays,
                    invoiceTerms = invoiceTerms.jsTrim().ifEmpty { DEFAULT_INVOICE_TERMS },
                    footer = footer.jsTrim()
                )
                if (!app.documents.saveSettings(documents)) {
                    toast.show("Entreprise enregistrée, mais pas les réglages des documents.")
                    return
                }
                if (!app.settings.saveUser(UserProfile(result.firstName, result.lastName, result.userPhone))) {
                    toast.show("Entreprise enregistrée, mais pas vos informations personnelles.")
                    return
                }
                toast.show("Informations enregistrées.")
            }
        }
    }

    Column {
        SettingsSection(
            "Mon entreprise",
            "Ces informations apparaissent en haut des reçus que vous partagez à vos clients, " +
                "et le nom de l'entreprise s'affiche sur le tableau de bord."
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ModuleField("Nom de l'entreprise") {
                    ModuleTextField(
                        shopName, { shopName = it }, "Ex. : Boutique Awa",
                        invalid = invalid == SettingsField.ShopName, maxLength = 60,
                        focusRequester = focus[SettingsField.ShopName]
                    )
                }
                ModuleField("Téléphone de l'entreprise") {
                    ModuleTextField(
                        shopPhone, { shopPhone = it; invalid = null }, "Ex. : +225 07 00 00 00 00",
                        keyboardType = KeyboardType.Phone, invalid = invalid == SettingsField.ShopPhone,
                        maxLength = 20, focusRequester = focus[SettingsField.ShopPhone]
                    )
                }
            }
        }

        SettingsSection("Aperçu du reçu") {
            Text(
                receiptPreviewText(shopName, shopPhone),
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .background(c.background, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                color = c.text,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }

        SettingsSection(
            "Documents (devis, factures)",
            "Ces informations complètent l'en-tête de vos devis et factures imprimés. Toutes sont facultatives."
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ModuleField("Logo") {
                    Column {
                        val bitmap = rememberPhoto(logo, 320)
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Logo",
                                modifier = Modifier.padding(bottom = 8.dp).widthIn(max = 160.dp).heightIn(max = 80.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                        SecondaryButton(if (logo == null) "Choisir un logo" else "Changer le logo") {
                            logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        if (logo != null) {
                            Text(
                                "Retirer le logo",
                                modifier = Modifier.padding(top = 8.dp).tap { logo = null },
                                color = c.error,
                                fontWeight = FontWeight(600)
                            )
                        }
                    }
                }
                ModuleField("Adresse") {
                    TextAreaField(address, { address = it }, "Ex. : Marché de Cocody, Abidjan", 2, 160)
                }
                ModuleField("E-mail") {
                    ModuleTextField(
                        email, { email = it; invalid = null }, "Ex. : contact@maboutique.com",
                        keyboardType = KeyboardType.Email, invalid = invalid == SettingsField.Email,
                        maxLength = 80, focusRequester = focus[SettingsField.Email]
                    )
                }
                ModuleField("Site web / réseau social") { ModuleTextField(website, { website = it }, "", maxLength = 80) }
                ModuleField("NIF / IFU") { ModuleTextField(taxId, { taxId = it }, "", maxLength = 40) }
                ModuleField("RCCM") { ModuleTextField(registry, { registry = it }, "", maxLength = 40) }
                ModuleField("Moyens de paiement") {
                    TextAreaField(paymentInfo, { paymentInfo = it }, "Ex. : Mobile Money 07 00 00 00 00 (Awa Koné)", 3, 600)
                }
                ModuleField("TVA par défaut (%)") {
                    ModuleTextField(
                        tax, { tax = it; invalid = null }, "0",
                        keyboardType = KeyboardType.Decimal, invalid = invalid == SettingsField.Tax,
                        focusRequester = focus[SettingsField.Tax]
                    )
                }
                ModuleField("Validité d'un devis (jours)") {
                    ModuleTextField(
                        validity, { validity = it; invalid = null }, "30",
                        keyboardType = KeyboardType.Number, invalid = invalid == SettingsField.Validity,
                        focusRequester = focus[SettingsField.Validity]
                    )
                }
                ModuleField("Conditions générales des devis") { TextAreaField(quoteTerms, { quoteTerms = it }, "", 4, 600) }
                ModuleField("Délai de paiement d'une facture (jours)") {
                    ModuleTextField(
                        dueDays, { dueDays = it; invalid = null }, "30",
                        keyboardType = KeyboardType.Number, invalid = invalid == SettingsField.DueDays,
                        focusRequester = focus[SettingsField.DueDays]
                    )
                }
                ModuleField("Mentions des factures") { TextAreaField(invoiceTerms, { invoiceTerms = it }, "", 4, 600) }
                ModuleField("Pied de page") {
                    ModuleTextField(footer, { footer = it }, "Ex. : Merci de votre confiance !", maxLength = 160)
                }
            }
        }

        SettingsSection(
            "Mes informations",
            "Vos informations personnelles servent à vous saluer dans l'application. " +
                "Elles n'apparaissent jamais sur les reçus."
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ModuleField("Prénom") {
                    ModuleTextField(
                        firstName, { firstName = it }, "Ex. : Awa",
                        invalid = invalid == SettingsField.FirstName, maxLength = 40,
                        focusRequester = focus[SettingsField.FirstName]
                    )
                }
                ModuleField("Nom (facultatif)") {
                    ModuleTextField(
                        lastName, { lastName = it }, "Ex. : Koné",
                        invalid = invalid == SettingsField.LastName, maxLength = 40,
                        focusRequester = focus[SettingsField.LastName]
                    )
                }
                ModuleField("Mon téléphone (facultatif)") {
                    ModuleTextField(
                        userPhone, { userPhone = it; invalid = null }, "Ex. : +225 05 00 00 00 00",
                        keyboardType = KeyboardType.Phone, invalid = invalid == SettingsField.UserPhone,
                        maxLength = 20, focusRequester = focus[SettingsField.UserPhone]
                    )
                }
            }
            if (firstName.jsTrim().isNotEmpty()) {
                Text(
                    "Sur le tableau de bord : « ${greetingFor(LocalDateTime.now().hour)}, ${firstName.jsTrim()} »",
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .background(c.background, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    color = c.textSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Box(
            Modifier
                .padding(bottom = 14.dp)
                .fillMaxWidth()
                .height(50.dp)
                .background(c.primary, RoundedCornerShape(10.dp))
                .tap { save() },
            contentAlignment = Alignment.Center
        ) {
            Text("Enregistrer", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        SettingsSection(
            "Devise",
            "Tous les montants sont en franc CFA (FCFA). Une autre devise n'est pas encore disponible."
        ) {}
    }
}

// ---------------------------------------------------------------------------
// Autres catégories
// ---------------------------------------------------------------------------

@Composable
private fun TeamPanel(app: EjdenApp) {
    val c = Ejden.colors
    val profile = remember { app.settings.getUser() }
    val name = (profile.firstName + " " + profile.lastName).jsTrim()

    Column {
        SettingsSection(
            "Utilisateurs de cet espace",
            "Aujourd'hui, EJDEN fonctionne pour une seule personne sur cet appareil. Le travail en équipe " +
                "demandera des comptes et un espace en ligne : il n'est pas encore disponible."
        ) {
            SoonRow(name.ifEmpty { "Vous" }, "Utilisateur de cet appareil", last = false, badge = "Actif", ok = true)
            SoonRow("Membres de l'équipe", "Ajouter et retirer des personnes", last = false)
            SoonRow("Inviter un membre", "Envoyer une invitation", last = true)
        }
        SettingsSection("Rôles prévus", "Aperçu des rôles à venir. Aucune permission n'est appliquée pour le moment.") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((title, text) in listOf(
                    "Administrateur" to "Gère l'activité, l'équipe et toutes les données.",
                    "Gérant" to "Suit les ventes, le stock et les finances.",
                    "Vendeur" to "Enregistre les ventes et consulte les produits.",
                    "Gestionnaire de stock" to "Gère les produits, achats et mouvements de stock."
                )) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(c.background, RoundedCornerShape(10.dp))
                            .border(1.dp, c.border, RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(text, modifier = Modifier.padding(top = 2.dp), color = c.textSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
        SettingsSection("Accès") {
            SoonRow("Permissions", "Choisir ce que chaque rôle peut faire", last = false)
            SoonRow("Appareils connectés", "Voir les appareils autorisés", last = false)
            SoonRow("Sessions", "Fermer un accès à distance", last = true)
        }
    }
}

@Composable
private fun SalesPanel(app: EjdenApp, toast: ModuleToastState) {
    var allowCredit by remember { mutableStateOf(app.preferences.getAllowCredit()) }
    Column {
        SettingsSection("Paiements") {
            SwitchRow(
                "Ventes à crédit",
                "Propose « Crédit » comme moyen de paiement à la caisse. Les crédits déjà enregistrés restent visibles.",
                allowCredit
            ) {
                val next = !allowCredit
                if (app.preferences.saveAllowCredit(next)) {
                    allowCredit = next
                    toast.show("Réglage enregistré.")
                } else {
                    toast.show("Impossible d'enregistrer ce réglage.")
                }
            }
            Text(
                "Moyens de paiement disponibles : espèces, Mobile Money, carte, autre et crédit.",
                modifier = Modifier.padding(top = 4.dp),
                color = Ejden.colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.8.sp
            )
        }
        SettingsSection("Raccourcis") {}
    }
}

@Composable
private fun StockPanel(app: EjdenApp, toast: ModuleToastState) {
    var threshold by remember {
        val current = app.preferences.getDefaultThreshold()
        mutableStateOf(if (current > 0) current.toString() else "")
    }

    fun saveThreshold() {
        val raw = threshold.jsTrim()
        val value = if (raw.isEmpty()) 0 else (raw.toIntOrNull() ?: -1)
        if (app.preferences.saveDefaultThreshold(value)) {
            toast.show("Seuil enregistré.")
        } else {
            toast.show("Seuil invalide : entrez un nombre entier.")
            val current = app.preferences.getDefaultThreshold()
            threshold = if (current > 0) current.toString() else ""
        }
    }

    Column {
        SettingsSection(
            "Alertes de stock faible",
            "Seuil proposé pour chaque nouveau produit. Vous pouvez le modifier produit par produit. " +
                "Laissez vide pour ne rien proposer."
        ) {
            ModuleField("Seuil par défaut") {
                SettingsNumberField(threshold, { threshold = it }, "Ex. 5") { saveThreshold() }
            }
        }
        SettingsSection("Raccourcis") {}
    }
}

@Composable
private fun AppearancePanel(app: EjdenApp, toast: ModuleToastState) {
    val c = Ejden.colors
    val appearance by app.appearanceState
    var reduceMotion by remember { mutableStateOf(appearance.reduceMotion) }
    var languageOpen by remember { mutableStateOf(false) }

    Column {
        SettingsSection("Thème", "Le thème sombre est activé par défaut. « Automatique » suit le réglage du téléphone.") {
            ChoiceGroup(
                listOf("dark" to "Sombre", "light" to "Clair", "auto" to "Automatique"),
                appearance.theme.id
            ) { id ->
                val choice = ThemeChoice.entries.first { it.id == id }
                toast.show(if (app.setTheme(choice)) "Thème enregistré." else "Impossible d'enregistrer le thème.")
            }
        }

        SettingsSection("Taille du texte") {
            ChoiceGroup(
                listOf("normal" to "Normal", "large" to "Grand", "xlarge" to "Très grand"),
                appearance.textSize.id ?: "normal"
            ) { id ->
                val size = TextSize.entries.first { (it.id ?: "normal") == id }
                toast.show(
                    if (app.setTextSize(size)) "Taille du texte enregistrée." else "Impossible d'enregistrer ce réglage."
                )
            }
        }

        SettingsSection("Animations") {
            SwitchRow(
                "Réduire les animations",
                "Supprime les transitions et mouvements de l'interface.",
                reduceMotion
            ) {
                val next = !reduceMotion
                if (app.setReduceMotion(next)) {
                    reduceMotion = next
                    toast.show("Réglage enregistré.")
                } else {
                    toast.show("Impossible d'enregistrer ce réglage.")
                }
            }
        }

        SettingsSection("Langue") {
            ModuleField("Langue de l'application") {
                Box {
                    val shape = RoundedCornerShape(10.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(c.background, shape)
                            .border(1.dp, c.border, shape)
                            .tap { languageOpen = true }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Français", color = c.text, fontSize = 15.sp)
                        StrokeIcon(IconSpec(listOf("M6 9l6 6 6-6")), c.textSecondary, 18.dp)
                    }
                    DropdownMenu(expanded = languageOpen, onDismissRequest = { languageOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Français") },
                            onClick = {
                                languageOpen = false
                                app.preferences.saveLanguage("fr")
                                toast.show("Langue enregistrée.")
                            }
                        )
                        DropdownMenuItem(text = { Text("English (bientôt)") }, enabled = false, onClick = {})
                    }
                }
            }
        }
    }
}

@Composable
private fun DataPanel(app: EjdenApp, nav: NavController, toast: ModuleToastState, scope: kotlinx.coroutines.CoroutineScope) {
    val meta = remember { app.backup.getMeta() }
    var confirming by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val c = Ejden.colors

    Column {
        SettingsSection(
            "Vos données vous appartiennent",
            "EJDEN est pensée local-first : vos données sont stockées sur cet appareil et l'application " +
                "fonctionne sans connexion. Rien n'est envoyé à EJDEN. La sauvegarde Google Drive est " +
                "facultative et se fait vers votre propre compte."
        ) {}

        SettingsSection("Dernière sauvegarde") {
            InfoRow("Fichier de sauvegarde", lastBackupLabel(meta.lastFile), last = false)
            InfoRow("Google Drive", lastBackupLabel(meta.lastDrive), last = false)
            SettingsLink(
                "Sauvegarder, restaurer, exporter, importer",
                "Fichier de sauvegarde et Google Drive"
            ) { nav.open(Routes.BACKUP) }
        }

        SettingsSection(
            "Supprimer toutes les données",
            "Efface les produits, ventes, clients et finances de cet appareil. Cette action est " +
                "définitive : faites une sauvegarde avant."
        ) {
            val shape = RoundedCornerShape(10.dp)
            if (!confirming) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .border(1.dp, c.error, shape)
                        .tap { confirming = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Effacer toutes les données", color = c.error, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Column {
                    Text(
                        "Produits, ventes, clients, finances… tout sera supprimé de cet appareil. Continuer ?",
                        color = c.error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight(600)
                    )
                    Box(
                        Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .height(46.dp)
                            .background(c.error, shape)
                            .border(1.dp, c.error, shape)
                            .tap {
                                if (busy) return@tap
                                if (!app.backup.resetAllData()) {
                                    toast.show("Impossible d'effacer les données.")
                                } else {
                                    busy = true
                                    toast.show("Données effacées.")
                                    scope.launch {
                                        delay(1000)
                                        nav.returnTo(Routes.DASHBOARD)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Oui, tout effacer", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .height(46.dp)
                            .border(1.dp, c.border, shape)
                            .tap { confirming = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Non, garder", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityPanel() {
    Column {
        SettingsSection(
            "Protection de l'application",
            "Ces protections ne sont pas encore disponibles : l'application s'ouvre sans code. " +
                "En attendant, verrouillez l'écran de votre téléphone."
        ) {
            SoonRow("Verrouillage de l'application", "Demander un code à l'ouverture", last = false)
            SoonRow("Code PIN", "Choisir un code à 4 chiffres ou plus", last = false)
            SoonRow("Biométrie", "Empreinte ou reconnaissance du visage", last = true)
        }
        SettingsSection("Accès") {
            SoonRow("Sessions", "Voir et fermer les accès", last = false)
            SoonRow("Appareils connectés", "Gérer les appareils autorisés", last = true)
        }
    }
}

@Composable
private fun AboutPanel() {
    val context = LocalContext.current
    val c = Ejden.colors
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    Column {
        SettingsSection("EJDEN") {
            Text(
                "Gestion de boutique pour commerçants, artisans et petites entreprises.",
                color = c.textSecondary,
                fontSize = 13.sp
            )
            InfoRow("Version", "Version $version", last = true)
        }
        SettingsSection(
            "Confidentialité",
            "Vos données commerciales restent sur votre appareil. EJDEN ne les collecte pas. " +
                "Si vous activez la sauvegarde Google Drive, une copie est placée dans l'espace privé " +
                "de l'application de votre propre compte Google."
        ) {}
        SettingsSection("Assistance") {
            SoonRow("Aide", "Guide d'utilisation", last = false)
            SoonRow("Contacter EJDEN", "Poser une question", last = false)
            SoonRow("Signaler un problème", "Décrire un souci rencontré", last = false)
            SoonRow("Conditions d'utilisation", "Règles d'usage de l'application", last = true)
        }
    }
}

@Composable
private fun SettingsLink(title: String, subtitle: String, onClick: () -> Unit) {
    val c = Ejden.colors
    Row(
        Modifier.padding(top = 4.dp).fillMaxWidth().heightIn(min = 56.dp).tap(onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = c.textSecondary, fontSize = 12.sp)
        }
        StrokeIcon(AppIcons.Arrow, c.textSecondary, 18.dp, strokeWidth = 2f)
    }
}
