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
import androidx.compose.foundation.lazy.LazyListScope
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
import com.ejden.app.data.Invoice
import com.ejden.app.data.InvoiceDisplayStatus
import com.ejden.app.data.InvoiceStatus
import com.ejden.app.data.ModuleLimits
import com.ejden.app.data.PrintableDocument
import com.ejden.app.data.Product
import com.ejden.app.data.addDaysToDay
import com.ejden.app.data.computeDocumentTotals
import com.ejden.app.data.documentToHtml
import com.ejden.app.data.documentToText
import com.ejden.app.data.formatMoney
import com.ejden.app.data.invoiceToDocument
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

private data class InvoicesData(
    val invoices: List<Invoice>,
    val clients: List<Client>,
    val products: List<Product>,
    val shopName: String,
    val address: String
)

private fun invoiceCount(n: Int): String = n.toString() + " facture" + (if (n > 1) "s" else "")

@Composable
fun InvoicesScreen(nav: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as EjdenApp
    val toast = rememberModuleToast()
    val resumeKey = rememberResumeKey()
    val listState = rememberLazyListState()

    var data by remember { mutableStateOf(InvoicesData(emptyList(), emptyList(), emptyList(), "", "")) }
    var loaded by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(resumeKey, reloadKey) {
        data = withContext(Dispatchers.Default) {
            InvoicesData(
                app.invoices.getInvoices(),
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

    // Formulaire de la facture.
    var clientId by remember { mutableStateOf("") }
    var clientName by remember { mutableStateOf("") }
    var clientPhone by remember { mutableStateOf("") }
    var clientEmail by remember { mutableStateOf("") }
    var clientAddress by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var issueDate by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf("") }
    var discountText by remember { mutableStateOf("") }
    var taxText by remember { mutableStateOf("") }
    var paymentTerms by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var terms by remember { mutableStateOf("") }
    var nextLineId by remember { mutableIntStateOf(1) }
    val lines = remember { mutableStateListOf<QuoteLineDraft>() }

    var nameInvalid by remember { mutableStateOf(false) }
    var phoneInvalid by remember { mutableStateOf(false) }
    var emailInvalid by remember { mutableStateOf(false) }
    var issueInvalid by remember { mutableStateOf(false) }
    var dueInvalid by remember { mutableStateOf(false) }
    var discountInvalid by remember { mutableStateOf(false) }
    var taxInvalid by remember { mutableStateOf(false) }
    var pickerFor by remember { mutableStateOf<String?>(null) }

    fun closeEditor() {
        screen = if (editingId != null && previewId == editingId) "preview" else "list"
        editingId = null
    }

    fun openEditor(invoice: Invoice?) {
        val docSettings = app.documents.getSettings()
        val today = app.invoices.today()
        val linked = invoice?.clientId

        editingId = invoice?.id
        clientId = if (linked != null && data.clients.any { it.id == linked }) linked else ""
        clientName = invoice?.clientName ?: ""
        clientPhone = invoice?.clientPhone ?: ""
        clientEmail = invoice?.clientEmail ?: ""
        clientAddress = invoice?.clientAddress ?: ""
        subject = invoice?.subject ?: ""
        issueDate = invoice?.issueDate ?: today
        dueDate = invoice?.dueDate ?: addDaysToDay(today, docSettings.defaultDueDays)
        discountText = if (invoice != null && invoice.discount > 0.0) formatPlainNumber(invoice.discount) else ""
        taxText = formatPlainNumber(invoice?.taxRate ?: docSettings.defaultTaxRate)
        paymentTerms = invoice?.paymentTerms ?: ""
        notes = invoice?.notes ?: ""
        terms = invoice?.terms ?: docSettings.invoiceTerms

        lines.clear()
        if (invoice != null) {
            for (line in invoice.lines) {
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
        dueInvalid = false
        discountInvalid = false
        taxInvalid = false
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
        if (!isValidDay(issueDate)) { issueInvalid = true; toast.show("Date de la facture invalide."); return }
        if (!isValidDay(dueDate) || dueDate < issueDate) {
            dueInvalid = true
            toast.show("L'échéance doit suivre la date de la facture.")
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
        if (computeDocumentTotals(built, discount, taxRate, 0.0).total > DocumentLimits.TOTAL_MAX) {
            toast.show("Montant total trop élevé.")
            return
        }

        val existing = editingId?.let { app.invoices.getInvoiceById(it) }
        val stamp = toIso(Instant.now())
        val invoice = Invoice(
            id = existing?.id ?: newId(),
            number = existing?.number ?: "",
            status = existing?.status ?: InvoiceStatus.Draft,
            createdAt = existing?.createdAt ?: stamp,
            updatedAt = stamp,
            issueDate = issueDate,
            dueDate = dueDate,
            quoteId = existing?.quoteId,
            quoteNumber = existing?.quoteNumber ?: "",
            clientId = clientId.ifEmpty { null },
            clientName = name,
            clientPhone = phone,
            clientEmail = email,
            clientAddress = clientAddress.jsTrim(),
            subject = subject.jsTrim(),
            lines = built,
            discount = Math.round(discount).toDouble(),
            taxRate = taxRate,
            paymentTerms = paymentTerms.jsTrim(),
            notes = notes.jsTrim(),
            terms = terms.jsTrim(),
            payments = existing?.payments ?: emptyList()
        )

        if (!app.invoices.saveInvoice(invoice)) {
            toast.show("Impossible d'enregistrer la facture.")
            return
        }

        toast.show("Brouillon enregistré. Vérifiez puis émettez la facture.")
        previewId = invoice.id
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
    val statusOf = remember(data.invoices) {
        data.invoices.associate { it.id to app.invoices.displayStatus(it) }
    }
    var pending = 0.0
    var pendingCount = 0
    var late = 0.0
    var lateCount = 0
    for (invoice in data.invoices) {
        val status = statusOf[invoice.id]
        if (status == InvoiceDisplayStatus.Unpaid || status == InvoiceDisplayStatus.Partial ||
            status == InvoiceDisplayStatus.Overdue
        ) {
            pending += invoice.remaining()
            pendingCount += 1
        }
        if (status == InvoiceDisplayStatus.Overdue) {
            late += invoice.remaining()
            lateCount += 1
        }
    }

    val search = query.jsTrim().lowercase()
    val visible = data.invoices.filter { invoice ->
        val status = statusOf[invoice.id] ?: InvoiceDisplayStatus.Draft
        val matchesFilter = when (filterKey) {
            "draft" -> status == InvoiceDisplayStatus.Draft
            "open" -> status == InvoiceDisplayStatus.Unpaid || status == InvoiceDisplayStatus.Partial
            "overdue" -> status == InvoiceDisplayStatus.Overdue
            "paid" -> status == InvoiceDisplayStatus.Paid
            "cancelled" -> status == InvoiceDisplayStatus.Cancelled
            else -> true
        }
        matchesFilter && (
            search.isEmpty() ||
                invoice.number.lowercase().contains(search) ||
                invoice.clientName.lowercase().contains(search) ||
                invoice.subject.lowercase().contains(search)
            )
    }

    // ----- Éditeur : totaux en direct -----
    val validLines = lines.map { readQuoteLine(it) }.filter { quoteLineIsValid(it) }
    val liveTax = parseDocNumber(taxText).let { if (it.isNaN()) 0.0 else minOf(100.0, maxOf(0.0, it)) }
    val liveDiscount = parseDocNumber(discountText).let { if (it.isNaN()) 0.0 else maxOf(0.0, it) }
    val liveTotals = computeDocumentTotals(validLines, liveDiscount, liveTax, 0.0)

    val clientOptions = remember(data.clients) {
        listOf("" to "Nouveau client (saisie libre)") + data.clients.map { it.id to it.name }
    }
    val productOptions = remember(data.products) {
        listOf("" to "Saisie libre ou choisir un produit…") + data.products.map { it.id to it.name }
    }

    val title = when {
        screen != "editor" -> "Factures"
        editingId != null -> "Modifier la facture"
        else -> "Nouvelle facture"
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
            invoicePreview(
                invoice = data.invoices.firstOrNull { it.id == previewId },
                loaded = loaded,
                toast = toast,
                onEdit = { openEditor(it) },
                onChanged = { reloadKey += 1 },
                onGone = { screen = "list"; previewId = null; reloadKey += 1 },
                onOpen = { previewId = it; reloadKey += 1 }
            )
        } else if (screen == "list") {
            if (loaded && (data.shopName.isEmpty() || data.address.isEmpty())) {
                item { InvoicesSettingsNotice { nav.open(Routes.SETTINGS) } }
            }

            item {
                Row(
                    Modifier
                        .padding(bottom = if (lateCount > 0) 8.dp else 14.dp)
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryCard(
                        "À encaisser", formatMoney(pending),
                        Modifier.weight(1f).fillMaxHeight(), unit = invoiceCount(pendingCount)
                    )
                    Spacer(Modifier.weight(1f))
                }
            }

            if (lateCount > 0) {
                item {
                    SummaryCard(
                        "En retard", formatMoney(late),
                        Modifier.padding(bottom = 14.dp).fillMaxWidth(),
                        unit = invoiceCount(lateCount), wide = true
                    )
                }
            }

            item {
                PrimaryButton("＋ Nouvelle facture", Modifier.padding(bottom = 12.dp).fillMaxWidth()) {
                    openEditor(null)
                }
            }

            item { InvoiceFilterChips(filterKey) { filterKey = it } }
            item { ModuleSearch(query, { query = it }, "Rechercher (client, numéro…)") }

            if (loaded && visible.isEmpty()) {
                item {
                    ModuleEmpty(
                        if (data.invoices.isEmpty()) "Aucune facture" else "Aucun résultat",
                        if (data.invoices.isEmpty()) "Créez votre première facture professionnelle."
                        else "Aucune facture ne correspond à cette recherche."
                    )
                }
            }

            items(visible, key = { it.id }) { invoice ->
                InvoiceRow(invoice, statusOf[invoice.id] ?: InvoiceDisplayStatus.Draft) {
                    previewId = invoice.id
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
                ModuleSection("Facture") {
                    ModuleField("Objet (facultatif)") {
                        ModuleTextField(
                            subject, { subject = it }, "Ex. : Fourniture de matériel de bureau",
                            maxLength = DocumentLimits.DESCRIPTION_MAX
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) {
                            ModuleField("Date de la facture") {
                                DocDateField(issueDate, issueInvalid) { pickerFor = "issue" }
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            ModuleField("Échéance") {
                                DocDateField(dueDate, dueInvalid) { pickerFor = "due" }
                            }
                        }
                    }
                }
            }

            item {
                ModuleSection("Lignes de la facture") {
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
                    }
                    QuoteTotalsBox(liveTotals, liveTax, 0.0)
                }
            }

            item {
                ModuleSection("Conditions") {
                    ModuleField("Modalités de paiement (facultatif)") {
                        ModuleTextField(
                            paymentTerms, { paymentTerms = it }, "Ex. : Paiement par Mobile Money ou espèces",
                            maxLength = DocLimits.ADDRESS_MAX
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    ModuleField("Notes (facultatif)") {
                        DocTextArea(notes, { notes = it }, "", 2, DocLimits.LONG_MAX)
                    }
                    Spacer(Modifier.height(12.dp))
                    ModuleField("Mentions légales et conditions") {
                        DocTextArea(terms, { terms = it }, "", 4, DocLimits.LONG_MAX)
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Enregistrer la facture", Modifier.weight(1f)) { submit() }
                    SecondaryButton("Annuler") { closeEditor() }
                }
            }
        }
    }

    if (pickerFor != null) {
        DocDayPickerDialog(
            initialDay = if (pickerFor == "issue") issueDate else dueDate,
            onPick = {
                if (pickerFor == "issue") {
                    issueDate = it
                    issueInvalid = false
                } else {
                    dueDate = it
                    dueInvalid = false
                }
            },
            onDismiss = { pickerFor = null }
        )
    }
}

/** Aperçu d'une facture : actions, émission, document, suppression du brouillon. */
private fun LazyListScope.invoicePreview(
    invoice: Invoice?,
    loaded: Boolean,
    toast: ModuleToastState,
    onEdit: (Invoice) -> Unit,
    onChanged: () -> Unit,
    onGone: () -> Unit,
    onOpen: (String) -> Unit
) {
    if (invoice == null) {
        if (loaded) item { ModuleEmpty("Facture introuvable", "Cette facture n'existe plus.") }
        return
    }

    item { InvoicePreviewContent(invoice, toast, onEdit, onChanged, onGone, onOpen) }
}

@Composable
private fun InvoicePreviewContent(
    invoice: Invoice,
    toast: ModuleToastState,
    onEdit: (Invoice) -> Unit,
    onChanged: () -> Unit,
    onGone: () -> Unit,
    onOpen: (String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as EjdenApp
    var deleteAttempt by remember { mutableIntStateOf(0) }

    val status = remember(invoice) { app.invoices.displayStatus(invoice) }
    val shop = remember(invoice) { app.settings.getShop() }
    val settings = remember(invoice) { app.documents.getSettings() }
    val doc: PrintableDocument = remember(invoice) { invoiceToDocument(invoice, settings, status) }
    val isDraft = invoice.status == InvoiceStatus.Draft
    val jobName = doc.title + " " + doc.number

    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Imprimer / PDF", Modifier.fillMaxWidth()) {
                if (!printHtml(context, documentToHtml(doc, shop, settings), jobName)) {
                    toast.show("Impression indisponible sur cet appareil.")
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isDraft) {
                    ModuleActionButton("Modifier", ModuleButtonStyle.Normal, Modifier.weight(1f)) { onEdit(invoice) }
                }
                ModuleActionButton("Dupliquer", ModuleButtonStyle.Normal, Modifier.weight(1f)) {
                    val copy = app.invoices.duplicateInvoice(invoice.id)
                    if (copy != null) {
                        toast.show("Copie créée en brouillon.")
                        onOpen(copy.id)
                    } else {
                        toast.show("Impossible de dupliquer la facture.")
                    }
                }
                ModuleActionButton("Partager", ModuleButtonStyle.Normal, Modifier.weight(1f)) {
                    if (!shareDocumentText(context, jobName, documentToText(doc, shop))) {
                        toast.show("Impossible de partager sur cet appareil.")
                    }
                }
            }

            if (isDraft) {
                PrimaryButton("Émettre la facture (numéro définitif)", Modifier.fillMaxWidth()) {
                    val issued = app.invoices.issueInvoice(invoice.id)
                    if (issued != null) {
                        toast.show("Facture " + issued.number + " émise.")
                        onChanged()
                    } else {
                        toast.show("Impossible d'émettre la facture.")
                    }
                }
            }
        }

        DocumentSheet(doc, shop, settings)

        if (isDraft) {
            key(deleteAttempt) {
                DeleteControl(
                    label = "Supprimer ce brouillon",
                    question = "Supprimer définitivement ce brouillon ?"
                ) {
                    if (app.invoices.deleteInvoice(invoice.id)) {
                        toast.show("Brouillon supprimé.")
                        onGone()
                    } else {
                        toast.show("Impossible de supprimer le brouillon.")
                        deleteAttempt += 1
                    }
                }
            }
        }
    }
}

/** Encart « Complétez votre entreprise » (.settings-notice). */
@Composable
private fun InvoicesSettingsNotice(onOpen: () -> Unit) {
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
                "Adresse, e-mail, NIF, logo… apparaîtront sur vos factures.",
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
private fun InvoiceFilterChips(selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InvoiceChip("Tous", selected == "all") { onSelect("all") }
        InvoiceChip("Brouillons", selected == "draft") { onSelect("draft") }
        InvoiceChip("À payer", selected == "open") { onSelect("open") }
        InvoiceChip("En retard", selected == "overdue") { onSelect("overdue") }
        InvoiceChip("Payées", selected == "paid") { onSelect("paid") }
        InvoiceChip("Annulées", selected == "cancelled") { onSelect("cancelled") }
    }
}

@Composable
private fun InvoiceChip(label: String, active: Boolean, onClick: () -> Unit) {
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

/** Brouillon (gris), à payer (orange), partielle (bleu), en retard / annulée (rouge), payée (vert). */
@Composable
private fun InvoiceStatusBadge(status: InvoiceDisplayStatus) {
    val c = Ejden.colors
    when (status) {
        InvoiceDisplayStatus.Paid -> Badge(status.label, BadgeTone.Success)
        InvoiceDisplayStatus.Unpaid -> Badge(status.label, BadgeTone.Debt)
        InvoiceDisplayStatus.Overdue, InvoiceDisplayStatus.Cancelled -> Badge(status.label, BadgeTone.Overdue)
        InvoiceDisplayStatus.Partial -> Text(
            status.label,
            modifier = Modifier.background(c.primarySoft, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
            color = c.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        InvoiceDisplayStatus.Draft -> Text(
            status.label,
            modifier = Modifier.background(Color(0xFFEEF1F2), CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
            color = c.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun InvoiceRow(invoice: Invoice, status: InvoiceDisplayStatus, onClick: () -> Unit) {
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
                invoice.clientName,
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                invoice.number.ifEmpty { "Brouillon" } + " · " + docDayText(invoice.issueDate),
                modifier = Modifier.padding(top = 2.dp),
                color = c.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatMoney(invoice.totals().total),
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Box(Modifier.height(4.dp))
            InvoiceStatusBadge(status)
        }
    }
}
