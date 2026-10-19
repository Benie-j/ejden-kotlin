package com.ejden.app.ui.modules

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ejden.app.EjdenApp
import com.ejden.app.data.Client
import com.ejden.app.data.DocLimits
import com.ejden.app.data.DocumentLimits
import com.ejden.app.data.DocumentLine
import com.ejden.app.data.DocumentTotals
import com.ejden.app.data.ModuleLimits
import com.ejden.app.data.Product
import com.ejden.app.data.Quote
import com.ejden.app.data.QuoteDisplayStatus
import com.ejden.app.data.QuoteStatus
import com.ejden.app.data.addDaysToDay
import com.ejden.app.data.computeDocumentTotals
import com.ejden.app.data.documentLineTotal
import com.ejden.app.data.formatMoney
import com.ejden.app.data.isValidDay
import com.ejden.app.data.isValidEmail
import com.ejden.app.data.isValidPhone
import com.ejden.app.data.jsTrim
import com.ejden.app.data.newId
import com.ejden.app.data.toIso
import com.ejden.app.ui.components.rememberResumeKey
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.nav.Routes
import com.ejden.app.ui.nav.goBack
import com.ejden.app.ui.nav.open
import com.ejden.app.ui.theme.Ejden
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

private data class QuotesData(
    val quotes: List<Quote>,
    val clients: List<Client>,
    val products: List<Product>,
    val shopName: String,
    val address: String
)

/** Une ligne du devis en cours de saisie. */
internal class QuoteLineDraft(val id: Int) {
    var productId by mutableStateOf("")
    var description by mutableStateOf("")
    var unit by mutableStateOf("")
    var quantity by mutableStateOf("1")
    var price by mutableStateOf("")
    var discount by mutableStateOf("")
    var descInvalid by mutableStateOf(false)
    var qtyInvalid by mutableStateOf(false)
    var priceInvalid by mutableStateOf(false)
}

internal fun readQuoteLine(draft: QuoteLineDraft): DocumentLine = DocumentLine(
    productId = draft.productId.ifEmpty { null },
    description = draft.description.jsTrim(),
    unit = draft.unit.jsTrim(),
    quantity = parseDocNumber(draft.quantity),
    unitPrice = parseDocNumber(draft.price),
    discountPct = parseDocNumber(draft.discount)
)

internal fun quoteLineIsValid(line: DocumentLine): Boolean =
    line.description.isNotEmpty() &&
        line.quantity.isFinite() && line.quantity > 0.0 && line.quantity <= 1_000_000.0 &&
        line.unitPrice.isFinite() && line.unitPrice >= 0.0 && line.unitPrice <= ModuleLimits.AMOUNT_MAX &&
        line.discountPct.isFinite() && line.discountPct >= 0.0 && line.discountPct <= 100.0

internal fun draftFromLine(line: DocumentLine, products: List<Product>, id: Int): QuoteLineDraft {
    val draft = QuoteLineDraft(id)
    val linked = line.productId
    draft.productId = if (linked != null && products.any { it.id == linked }) linked else ""
    draft.description = line.description
    draft.unit = line.unit
    draft.quantity = formatPlainNumber(line.quantity)
    draft.price = formatPlainNumber(line.unitPrice)
    draft.discount = if (line.discountPct > 0.0) formatPlainNumber(line.discountPct) else ""
    return draft
}

@Composable
fun QuotesScreen(nav: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as EjdenApp
    val c = Ejden.colors
    val toast = rememberModuleToast()
    val resumeKey = rememberResumeKey()
    val listState = rememberLazyListState()

    var data by remember { mutableStateOf(QuotesData(emptyList(), emptyList(), emptyList(), "", "")) }
    var loaded by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(resumeKey, reloadKey) {
        data = withContext(Dispatchers.Default) {
            QuotesData(
                app.quotes.getQuotes(),
                app.clients.getClients(),
                app.products.getProducts(),
                app.settings.getShop().name,
                app.documents.getSettings().address
            )
        }
        loaded = true
    }

    var screen by rememberSaveable { mutableStateOf("list") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    var filterKey by rememberSaveable { mutableStateOf("all") }
    var query by rememberSaveable { mutableStateOf("") }

    // Formulaire du devis.
    var clientId by remember { mutableStateOf("") }
    var clientName by remember { mutableStateOf("") }
    var clientPhone by remember { mutableStateOf("") }
    var clientEmail by remember { mutableStateOf("") }
    var clientAddress by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var issueDate by remember { mutableStateOf("") }
    var validUntil by remember { mutableStateOf("") }
    var discountText by remember { mutableStateOf("") }
    var taxText by remember { mutableStateOf("") }
    var depositText by remember { mutableStateOf("") }
    var deliveryTerms by remember { mutableStateOf("") }
    var paymentTerms by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var terms by remember { mutableStateOf("") }
    var nextLineId by remember { mutableIntStateOf(1) }
    val lines = remember { mutableStateListOf<QuoteLineDraft>() }

    var nameInvalid by remember { mutableStateOf(false) }
    var phoneInvalid by remember { mutableStateOf(false) }
    var emailInvalid by remember { mutableStateOf(false) }
    var issueInvalid by remember { mutableStateOf(false) }
    var validInvalid by remember { mutableStateOf(false) }
    var discountInvalid by remember { mutableStateOf(false) }
    var taxInvalid by remember { mutableStateOf(false) }
    var depositInvalid by remember { mutableStateOf(false) }
    var pickerFor by remember { mutableStateOf<String?>(null) }

    fun closeEditor() {
        screen = if (editingId != null && previewId == editingId) "preview" else "list"
        editingId = null
    }

    fun openEditor(quote: Quote?) {
        val docSettings = app.documents.getSettings()
        val today = app.quotes.today()
        val linked = quote?.clientId

        editingId = quote?.id
        clientId = if (linked != null && data.clients.any { it.id == linked }) linked else ""
        clientName = quote?.clientName ?: ""
        clientPhone = quote?.clientPhone ?: ""
        clientEmail = quote?.clientEmail ?: ""
        clientAddress = quote?.clientAddress ?: ""
        subject = quote?.subject ?: ""
        issueDate = quote?.issueDate ?: today
        validUntil = quote?.validUntil ?: addDaysToDay(today, docSettings.defaultValidityDays)
        discountText = if (quote != null && quote.discount > 0.0) formatPlainNumber(quote.discount) else ""
        taxText = formatPlainNumber(quote?.taxRate ?: docSettings.defaultTaxRate)
        depositText = if (quote != null && quote.depositPct > 0.0) formatPlainNumber(quote.depositPct) else ""
        deliveryTerms = quote?.deliveryTerms ?: ""
        paymentTerms = quote?.paymentTerms ?: ""
        notes = quote?.notes ?: ""
        terms = quote?.terms ?: docSettings.quoteTerms

        lines.clear()
        if (quote != null) {
            for (line in quote.lines) {
                lines.add(draftFromLine(line, data.products, nextLineId))
                nextLineId += 1
            }
        } else {
            lines.add(QuoteLineDraft(nextLineId))
            nextLineId += 1
        }

        nameInvalid = false
        phoneInvalid = false
        emailInvalid = false
        issueInvalid = false
        validInvalid = false
        discountInvalid = false
        taxInvalid = false
        depositInvalid = false
        screen = "editor"
    }

    fun addLine() {
        if (lines.size >= DocumentLimits.LINES_MAX) {
            toast.show(DocumentLimits.LINES_MAX.toString() + " lignes maximum.")
            return
        }
        lines.add(QuoteLineDraft(nextLineId))
        nextLineId += 1
    }

    fun chooseClient(id: String) {
        clientId = id
        val client = data.clients.firstOrNull { it.id == id }
        if (client != null) {
            clientName = client.name
            clientPhone = client.phone
            nameInvalid = false
            phoneInvalid = false
        }
    }

    fun chooseProduct(line: QuoteLineDraft, id: String) {
        line.productId = id
        val product = data.products.firstOrNull { it.id == id }
        if (product != null) {
            line.description = product.name
            line.price = formatPlainNumber(product.salePrice)
            line.descInvalid = false
            line.priceInvalid = false
        }
    }

    fun submit() {
        val name = clientName.jsTrim()
        val phone = clientPhone.jsTrim()
        val email = clientEmail.jsTrim()

        if (name.isEmpty()) { nameInvalid = true; toast.show("Indiquez le nom du client."); return }
        if (!isValidPhone(phone)) { phoneInvalid = true; toast.show("Téléphone du client invalide."); return }
        if (!isValidEmail(email)) { emailInvalid = true; toast.show("E-mail du client invalide."); return }
        if (!isValidDay(issueDate)) { issueInvalid = true; toast.show("Date du devis invalide."); return }
        if (!isValidDay(validUntil) || validUntil < issueDate) {
            validInvalid = true
            toast.show("La validité doit être postérieure à la date du devis.")
            return
        }
        if (lines.isEmpty()) { toast.show("Ajoutez au moins une ligne."); return }

        val built = ArrayList<DocumentLine>()
        for (draft in lines) {
            val line = readQuoteLine(draft)
            if (!quoteLineIsValid(line)) {
                when {
                    line.description.isEmpty() -> draft.descInvalid = true
                    !(line.quantity > 0.0) -> draft.qtyInvalid = true
                    else -> draft.priceInvalid = true
                }
                toast.show("Ligne incomplète : désignation, quantité > 0 et prix valides.")
                return
            }
            built.add(line)
        }

        val discount = parseDocNumber(discountText)
        val taxRate = parseDocNumber(taxText)
        val depositPct = parseDocNumber(depositText)
        val subtotal = computeDocumentTotals(built, 0.0, 0.0, 0.0).subtotal

        if (!discount.isFinite() || discount < 0.0 || discount > subtotal) {
            discountInvalid = true
            toast.show("Remise invalide (entre 0 et le sous-total).")
            return
        }
        if (!taxRate.isFinite() || taxRate < 0.0 || taxRate > 100.0) {
            taxInvalid = true
            toast.show("TVA invalide (0 à 100 %).")
            return
        }
        if (!depositPct.isFinite() || depositPct < 0.0 || depositPct > 100.0) {
            depositInvalid = true
            toast.show("Acompte invalide (0 à 100 %).")
            return
        }
        if (computeDocumentTotals(built, discount, taxRate, depositPct).total > DocumentLimits.TOTAL_MAX) {
            toast.show("Montant total trop élevé.")
            return
        }

        val existing = editingId?.let { app.quotes.getQuoteById(it) }
        val stamp = toIso(Instant.now())
        val quote = Quote(
            id = existing?.id ?: newId(),
            number = existing?.number ?: app.quotes.nextNumber(issueDate),
            status = existing?.status ?: QuoteStatus.Draft,
            createdAt = existing?.createdAt ?: stamp,
            updatedAt = stamp,
            issueDate = issueDate,
            validUntil = validUntil,
            clientId = clientId.ifEmpty { null },
            clientName = name,
            clientPhone = phone,
            clientEmail = email,
            clientAddress = clientAddress.jsTrim(),
            subject = subject.jsTrim(),
            lines = built,
            discount = Math.round(discount).toDouble(),
            taxRate = taxRate,
            depositPct = depositPct,
            deliveryTerms = deliveryTerms.jsTrim(),
            paymentTerms = paymentTerms.jsTrim(),
            notes = notes.jsTrim(),
            terms = terms.jsTrim(),
            invoiceId = existing?.invoiceId
        )

        if (!app.quotes.saveQuote(quote)) {
            toast.show("Impossible d'enregistrer le devis.")
            return
        }

        toast.show(if (existing != null) "Devis modifié." else "Devis " + quote.number + " créé.")
        previewId = quote.id
        editingId = null
        screen = "preview"
        reloadKey += 1
    }

    BackHandler(enabled = screen == "editor") { closeEditor() }
    BackHandler(enabled = screen == "preview") {
        screen = "list"
        previewId = null
    }
    LaunchedEffect(screen) { listState.scrollToItem(0) }

    // ----- Liste : résumé, filtre, recherche -----
    val statusOf = remember(data.quotes) {
        data.quotes.associate { it.id to app.quotes.displayStatus(it) }
    }
    var pending = 0.0
    var pendingCount = 0
    var accepted = 0.0
    var acceptedCount = 0
    for (quote in data.quotes) {
        when (statusOf[quote.id]) {
            QuoteDisplayStatus.Sent -> { pending += quote.totals().total; pendingCount += 1 }
            QuoteDisplayStatus.Accepted -> { accepted += quote.totals().total; acceptedCount += 1 }
            else -> Unit
        }
    }

    val search = query.jsTrim().lowercase()
    val visible = data.quotes.filter { quote ->
        val status = statusOf[quote.id] ?: QuoteDisplayStatus.Draft
        val matchesFilter = when (filterKey) {
            "draft" -> status == QuoteDisplayStatus.Draft
            "sent" -> status == QuoteDisplayStatus.Sent
            "accepted" -> status == QuoteDisplayStatus.Accepted
            "closed" -> status == QuoteDisplayStatus.Refused || status == QuoteDisplayStatus.Expired
            else -> true
        }
        matchesFilter && (
            search.isEmpty() ||
                quote.number.lowercase().contains(search) ||
                quote.clientName.lowercase().contains(search) ||
                quote.subject.lowercase().contains(search)
            )
    }

    // ----- Éditeur : totaux en direct -----
    val validLines = lines.map { readQuoteLine(it) }.filter { quoteLineIsValid(it) }
    val liveTax = parseDocNumber(taxText).let { if (it.isNaN()) 0.0 else minOf(100.0, maxOf(0.0, it)) }
    val liveDeposit = parseDocNumber(depositText).let { if (it.isNaN()) 0.0 else minOf(100.0, maxOf(0.0, it)) }
    val liveDiscount = parseDocNumber(discountText).let { if (it.isNaN()) 0.0 else maxOf(0.0, it) }
    val liveTotals = computeDocumentTotals(validLines, liveDiscount, liveTax, liveDeposit)

    val clientOptions = remember(data.clients) {
        listOf("" to "Nouveau client (saisie libre)") + data.clients.map { it.id to it.name }
    }
    val productOptions = remember(data.products) {
        listOf("" to "Saisie libre ou choisir un produit…") + data.products.map { it.id to it.name }
    }

    val title = when {
        screen != "editor" -> "Devis"
        editingId != null -> "Modifier le devis"
        else -> "Nouveau devis"
    }

    ModuleScaffold(
        "DOCUMENTS", title,
        {
            if (screen == "editor") closeEditor()
            else if (screen == "preview") { screen = "list"; previewId = null }
            else nav.goBack()
        },
        toast, listState
    ) {
        if (screen == "preview") {
            quotePreview(
                quote = data.quotes.firstOrNull { it.id == previewId },
                loaded = loaded,
                toast = toast,
                onEdit = { openEditor(it) },
                onChanged = { reloadKey += 1 },
                onGone = { screen = "list"; previewId = null; reloadKey += 1 },
                onOpen = { previewId = it; reloadKey += 1 }
            )
        } else if (screen == "list") {
            if (loaded && (data.shopName.isEmpty() || data.address.isEmpty())) {
                item { SettingsNotice { nav.open(Routes.SETTINGS) } }
            }

            item {
                Row(
                    Modifier.padding(bottom = 4.dp).fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryCard(
                        "En attente de réponse", formatMoney(pending),
                        Modifier.weight(1f).fillMaxHeight(), unit = pendingCount.toString() + " devis"
                    )
                    SummaryCard(
                        "Acceptés", formatMoney(accepted),
                        Modifier.weight(1f).fillMaxHeight(), unit = acceptedCount.toString() + " devis"
                    )
                }
            }

            item {
                PrimaryButton("＋ Nouveau devis", Modifier.padding(vertical = 12.dp).fillMaxWidth()) {
                    openEditor(null)
                }
            }

            item { QuoteFilterChips(filterKey) { filterKey = it } }
            item { ModuleSearch(query, { query = it }, "Rechercher (client, numéro…)") }

            if (loaded && visible.isEmpty()) {
                item {
                    ModuleEmpty(
                        if (data.quotes.isEmpty()) "Aucun devis" else "Aucun résultat",
                        if (data.quotes.isEmpty()) "Créez votre premier devis professionnel."
                        else "Aucun devis ne correspond à cette recherche."
                    )
                }
            }

            items(visible, key = { it.id }) { quote ->
                QuoteRow(quote, statusOf[quote.id] ?: QuoteDisplayStatus.Draft) {
                    previewId = quote.id
                    screen = "preview"
                }
            }
        } else {
            item {
                ModuleSection("Client") {
                    ModuleField("Client enregistré (facultatif)") {
                        ModuleSelect(clientId, clientOptions) { chooseClient(it) }
                    }
                    Spacer(Modifier.height(12.dp))
                    ModuleField("Nom du client") {
                        ModuleTextField(
                            clientName, { clientName = it; nameInvalid = false }, "Ex. : Société Koné SARL",
                            invalid = nameInvalid, maxLength = DocLimits.SHORT_MAX
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    ModuleField("Téléphone (facultatif)") {
                        ModuleTextField(
                            clientPhone, { clientPhone = it; phoneInvalid = false }, "Ex. : +225 07 00 00 00 00",
                            keyboardType = KeyboardType.Phone, invalid = phoneInvalid, maxLength = ModuleLimits.PHONE_MAX
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    ModuleField("E-mail (facultatif)") {
                        ModuleTextField(
                            clientEmail, { clientEmail = it; emailInvalid = false }, "Ex. : contact@exemple.com",
                            keyboardType = KeyboardType.Email, invalid = emailInvalid, maxLength = DocLimits.SHORT_MAX
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    ModuleField("Adresse (facultatif)") {
                        DocTextArea(clientAddress, { clientAddress = it }, "Ex. : Cocody, Abidjan", 2, DocLimits.ADDRESS_MAX)
                    }
                }
            }

            item {
                ModuleSection("Devis") {
                    ModuleField("Objet (facultatif)") {
                        ModuleTextField(
                            subject, { subject = it }, "Ex. : Fourniture de matériel de bureau",
                            maxLength = DocumentLimits.DESCRIPTION_MAX
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) {
                            ModuleField("Date du devis") {
                                DocDateField(issueDate, issueInvalid) { pickerFor = "issue" }
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            ModuleField("Valable jusqu'au") {
                                DocDateField(validUntil, validInvalid) { pickerFor = "valid" }
                            }
                        }
                    }
                }
            }

            item {
                ModuleSection("Lignes du devis") {
                    for (draft in lines) {
                        key(draft.id) {
                            QuoteLineCard(
                                draft = draft,
                                productOptions = productOptions,
                                onChooseProduct = { chooseProduct(draft, it) },
                                onRemove = { lines.remove(draft) }
                            )
                        }
                    }
                    ModuleActionButton(
                        "＋ Ajouter une ligne", ModuleButtonStyle.Normal, Modifier.padding(top = 10.dp)
                    ) { addLine() }
                }
            }

            item {
                ModuleSection("Montants") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) {
                            ModuleField("Remise (FCFA)") {
                                ModuleTextField(
                                    discountText,
                                    { discountText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }; discountInvalid = false },
                                    "0", keyboardType = KeyboardType.Decimal, invalid = discountInvalid, maxLength = 12
                                )
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            ModuleField("TVA (%)") {
                                ModuleTextField(
                                    taxText,
                                    { taxText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }; taxInvalid = false },
                                    "0", keyboardType = KeyboardType.Decimal, invalid = taxInvalid, maxLength = 6
                                )
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            ModuleField("Acompte (%)") {
                                ModuleTextField(
                                    depositText,
                                    { depositText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }; depositInvalid = false },
                                    "0", keyboardType = KeyboardType.Decimal, invalid = depositInvalid, maxLength = 6
                                )
                            }
                        }
                    }
                    QuoteTotalsBox(liveTotals, liveTax, liveDeposit)
                }
            }

            item {
                ModuleSection("Conditions") {
                    ModuleField("Délai de livraison / réalisation (facultatif)") {
                        ModuleTextField(
                            deliveryTerms, { deliveryTerms = it }, "Ex. : 7 jours après acceptation",
                            maxLength = DocLimits.ADDRESS_MAX
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    ModuleField("Conditions de paiement (facultatif)") {
                        ModuleTextField(
                            paymentTerms, { paymentTerms = it }, "Ex. : 50 % à la commande, solde à la livraison",
                            maxLength = DocLimits.ADDRESS_MAX
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    ModuleField("Notes (facultatif)") {
                        DocTextArea(notes, { notes = it }, "", 2, DocLimits.LONG_MAX)
                    }
                    Spacer(Modifier.height(12.dp))
                    ModuleField("Conditions générales") {
                        DocTextArea(terms, { terms = it }, "", 4, DocLimits.LONG_MAX)
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Enregistrer le devis", Modifier.weight(1f)) { submit() }
                    SecondaryButton("Annuler") { closeEditor() }
                }
            }
        }
    }

    if (pickerFor != null) {
        DocDayPickerDialog(
            initialDay = if (pickerFor == "issue") issueDate else validUntil,
            onPick = {
                if (pickerFor == "issue") {
                    issueDate = it
                    issueInvalid = false
                } else {
                    validUntil = it
                    validInvalid = false
                }
            },
            onDismiss = { pickerFor = null }
        )
    }
}

/** Encart « Complétez votre entreprise » (.settings-notice). */
@Composable
private fun SettingsNotice(onOpen: () -> Unit) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .background(c.primarySoft, shape)
            .border(1.dp, c.border, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text("Complétez votre entreprise", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                "Adresse, e-mail, NIF, logo… apparaîtront sur vos devis.",
                modifier = Modifier.padding(top = 2.dp),
                color = c.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
        Text(
            "Ouvrir les Paramètres",
            modifier = Modifier
                .background(c.primary, RoundedCornerShape(10.dp))
                .tap(onOpen)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun QuoteFilterChips(selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuoteChip("Tous", selected == "all") { onSelect("all") }
        QuoteChip("Brouillons", selected == "draft") { onSelect("draft") }
        QuoteChip("Envoyés", selected == "sent") { onSelect("sent") }
        QuoteChip("Acceptés", selected == "accepted") { onSelect("accepted") }
        QuoteChip("Refusés / expirés", selected == "closed") { onSelect("closed") }
    }
}

@Composable
private fun QuoteChip(label: String, active: Boolean, onClick: () -> Unit) {
    val c = Ejden.colors
    Box(
        Modifier
            .height(36.dp)
            .background(if (active) c.primary else c.surface, CircleShape)
            .border(1.dp, if (active) c.primary else c.border, CircleShape)
            .tap(onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (active) Color.White else c.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/** Statut du devis : brouillon (gris), envoyé (bleu), accepté (vert), refusé ou expiré (rouge). */
@Composable
private fun QuoteStatusBadge(status: QuoteDisplayStatus) {
    val c = Ejden.colors
    when (status) {
        QuoteDisplayStatus.Accepted -> Badge(status.label, BadgeTone.Success)
        QuoteDisplayStatus.Refused, QuoteDisplayStatus.Expired -> Badge(status.label, BadgeTone.Overdue)
        QuoteDisplayStatus.Draft -> Text(
            status.label,
            modifier = Modifier.background(Color(0xFFEEF1F2), CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
            color = c.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        QuoteDisplayStatus.Sent -> Text(
            status.label,
            modifier = Modifier.background(c.primarySoft, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
            color = c.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun QuoteRow(quote: Quote, status: QuoteDisplayStatus, onClick: () -> Unit) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .background(c.surface, shape)
            .border(1.dp, c.border, shape)
            .tap(onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                quote.clientName,
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                quote.number + " · " + docDayText(quote.issueDate),
                modifier = Modifier.padding(top = 2.dp),
                color = c.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatMoney(quote.totals().total),
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Box(Modifier.height(4.dp))
            QuoteStatusBadge(status)
        }
    }
}

@Composable
internal fun QuoteLineCard(
    draft: QuoteLineDraft,
    productOptions: List<Pair<String, String>>,
    onChooseProduct: (String) -> Unit,
    onRemove: () -> Unit
) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)
    val line = readQuoteLine(draft)
    val lineTotal = if (quoteLineIsValid(line)) documentLineTotal(line) else 0.0

    Column(
        Modifier
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .background(c.background, shape)
            .border(1.dp, c.border, shape)
            .padding(12.dp)
    ) {
        ModuleField("Produit (facultatif)") {
            ModuleSelect(draft.productId, productOptions) { onChooseProduct(it) }
        }
        Spacer(Modifier.height(8.dp))
        ModuleField("Désignation") {
            ModuleTextField(
                draft.description,
                { draft.description = it; draft.descInvalid = false },
                "Désignation (ex. : Ramette papier A4)",
                invalid = draft.descInvalid,
                maxLength = DocumentLimits.DESCRIPTION_MAX
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                ModuleField("Quantité") {
                    ModuleTextField(
                        draft.quantity,
                        { draft.quantity = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }; draft.qtyInvalid = false },
                        "1", keyboardType = KeyboardType.Decimal, invalid = draft.qtyInvalid, maxLength = 10
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                ModuleField("Unité") {
                    ModuleTextField(draft.unit, { draft.unit = it }, "pièce, kg, h…", maxLength = DocumentLimits.UNIT_MAX)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                ModuleField("Prix unitaire (FCFA)") {
                    ModuleTextField(
                        draft.price,
                        { draft.price = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }; draft.priceInvalid = false },
                        "0", keyboardType = KeyboardType.Decimal, invalid = draft.priceInvalid, maxLength = 12
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                ModuleField("Remise (%)") {
                    ModuleTextField(
                        draft.discount,
                        { draft.discount = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                        "0", keyboardType = KeyboardType.Decimal, maxLength = 6, imeAction = ImeAction.Done
                    )
                }
            }
        }
        Row(
            Modifier.padding(top = 10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Supprimer la ligne",
                modifier = Modifier.tap(onRemove).padding(vertical = 6.dp),
                color = c.error,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(formatMoney(lineTotal), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Totaux du devis en cours (sous-total, remise, TVA, total, acompte). */
@Composable
internal fun QuoteTotalsBox(totals: DocumentTotals, taxRate: Double, depositPct: Double) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)

    Column(
        Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .background(c.background, shape)
            .border(1.dp, c.border, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        TotalLine("Sous-total", formatMoney(totals.subtotal))
        if (totals.discount != 0.0) TotalLine("Remise", "− " + formatMoney(totals.discount))
        if (taxRate != 0.0) TotalLine("TVA (" + formatPlainNumber(taxRate) + " %)", formatMoney(totals.tax))
        TotalLine("Total", formatMoney(totals.total), grand = true)
        if (depositPct != 0.0) {
            TotalLine("Acompte", formatMoney(totals.deposit) + " (reste " + formatMoney(totals.balance) + ")")
        }
    }
}

@Composable
private fun TotalLine(label: String, value: String, grand: Boolean = false) {
    val c = Ejden.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            color = if (grand) c.primary else c.text,
            fontSize = if (grand) 16.sp else 13.sp,
            fontWeight = if (grand) FontWeight.ExtraBold else FontWeight.Normal
        )
        Text(
            value,
            color = if (grand) c.primary else c.text,
            fontSize = if (grand) 16.sp else 13.sp,
            fontWeight = if (grand) FontWeight.ExtraBold else FontWeight.Bold
        )
    }
}
