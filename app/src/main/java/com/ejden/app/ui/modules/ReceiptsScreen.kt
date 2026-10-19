package com.ejden.app.ui.modules

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ejden.app.EjdenApp
import com.ejden.app.data.InvoiceReceipt
import com.ejden.app.data.PrintableDocument
import com.ejden.app.data.documentToHtml
import com.ejden.app.data.documentToText
import com.ejden.app.data.formatMoney
import com.ejden.app.data.jsTrim
import com.ejden.app.data.paymentLabel
import com.ejden.app.data.receiptToDocument
import com.ejden.app.ui.components.rememberResumeKey
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.nav.Routes
import com.ejden.app.ui.nav.goBack
import com.ejden.app.ui.nav.open
import com.ejden.app.ui.theme.Ejden
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ReceiptsData(
    val receipts: List<InvoiceReceipt>,
    val shopName: String,
    val address: String
)

private fun receiptCount(n: Int): String = n.toString() + " reçu" + (if (n > 1) "s" else "")

@Composable
fun ReceiptsScreen(nav: NavController, openKey: String? = null) {
    val context = LocalContext.current
    val app = context.applicationContext as EjdenApp
    val toast = rememberModuleToast()
    val resumeKey = rememberResumeKey()
    val listState = rememberLazyListState()

    var data by remember { mutableStateOf(ReceiptsData(emptyList(), "", "")) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(resumeKey) {
        data = withContext(Dispatchers.Default) {
            ReceiptsData(
                app.invoices.getReceipts(),
                app.settings.getShop().name,
                app.documents.getSettings().address
            )
        }
        loaded = true
    }

    var screen by rememberSaveable { mutableStateOf(if (openKey != null) "preview" else "list") }
    var previewKey by rememberSaveable { mutableStateOf<String?>(openKey) }
    var query by rememberSaveable { mutableStateOf("") }

    BackHandler(enabled = screen == "preview") {
        screen = "list"
        previewKey = null
    }
    LaunchedEffect(screen) { listState.scrollToItem(0) }

    val total = data.receipts.sumOf { it.payment.amount }
    val search = query.jsTrim().lowercase()
    val visible = data.receipts.filter { receipt ->
        search.isEmpty() ||
            receipt.number.lowercase().contains(search) ||
            receipt.invoice.number.lowercase().contains(search) ||
            receipt.invoice.clientName.lowercase().contains(search)
    }

    ModuleScaffold(
        "DOCUMENTS", if (screen == "preview") "Reçu" else "Reçus",
        {
            if (screen == "preview") {
                screen = "list"
                previewKey = null
            } else {
                nav.goBack()
            }
        },
        toast, listState
    ) {
        if (screen == "preview") {
            receiptPreview(
                receipt = data.receipts.firstOrNull { it.key == previewKey },
                loaded = loaded,
                toast = toast,
                onOpenInvoice = { nav.open(Routes.invoice(it)) }
            )
        } else {
            if (loaded && (data.shopName.isEmpty() || data.address.isEmpty())) {
                item { ReceiptsSettingsNotice { nav.open(Routes.SETTINGS) } }
            }

            item {
                Row(
                    Modifier
                        .padding(bottom = 14.dp)
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryCard(
                        "Encaissé (tous reçus)", formatMoney(total),
                        Modifier.weight(1f).fillMaxHeight(), unit = receiptCount(data.receipts.size)
                    )
                    Spacer(Modifier.weight(1f))
                }
            }

            item {
                Text(
                    "Un reçu est créé automatiquement à chaque paiement enregistré sur une facture. " +
                        "Le montant entre aussi dans la caisse.",
                    modifier = Modifier.padding(bottom = 12.dp),
                    color = Ejden.colors.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }

            item { ModuleSearch(query, { query = it }, "Rechercher (client, numéro…)") }

            if (loaded && visible.isEmpty()) {
                item {
                    ModuleEmpty(
                        if (data.receipts.isEmpty()) "Aucun reçu" else "Aucun résultat",
                        if (data.receipts.isEmpty()) "Enregistrez un paiement sur une facture émise pour créer un reçu."
                        else "Aucun reçu ne correspond à cette recherche."
                    )
                }
            }

            items(visible, key = { it.key }) { receipt ->
                ReceiptRow(receipt) {
                    previewKey = receipt.key
                    screen = "preview"
                }
            }
        }
    }
}

/** Aperçu d'un reçu : impression, partage, lien vers la facture. */
private fun LazyListScope.receiptPreview(
    receipt: InvoiceReceipt?,
    loaded: Boolean,
    toast: ModuleToastState,
    onOpenInvoice: (String) -> Unit
) {
    if (receipt == null) {
        if (loaded) item { ModuleEmpty("Reçu introuvable", "Ce reçu n'existe plus.") }
        return
    }

    item { ReceiptPreviewContent(receipt, toast, onOpenInvoice) }
}

@Composable
private fun ReceiptPreviewContent(
    receipt: InvoiceReceipt,
    toast: ModuleToastState,
    onOpenInvoice: (String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as EjdenApp

    val shop = remember(receipt) { app.settings.getShop() }
    val settings = remember(receipt) { app.documents.getSettings() }
    val doc: PrintableDocument = remember(receipt) { receiptToDocument(receipt) }
    val jobName = doc.title + " " + doc.number

    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Imprimer / PDF", Modifier.fillMaxWidth()) {
                if (!printHtml(context, documentToHtml(doc, shop, settings), jobName)) {
                    toast.show("Impression indisponible sur cet appareil.")
                }
            }
            ModuleActionButton("Partager", ModuleButtonStyle.Normal, Modifier.fillMaxWidth()) {
                if (!shareDocumentText(context, jobName, documentToText(doc, shop))) {
                    toast.show("Impossible de partager sur cet appareil.")
                }
            }
            ModuleActionButton("Voir la facture", ModuleButtonStyle.Normal, Modifier.fillMaxWidth()) {
                onOpenInvoice(receipt.invoice.id)
            }
        }

        DocumentSheet(doc, shop, settings)
    }
}

/** Encart « Complétez votre entreprise » (.settings-notice). */
@Composable
private fun ReceiptsSettingsNotice(onOpen: () -> Unit) {
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
                "Adresse, e-mail, NIF, logo… apparaîtront sur vos reçus.",
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
private fun ReceiptRow(receipt: InvoiceReceipt, onClick: () -> Unit) {
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
                receipt.invoice.clientName,
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                receipt.number + " · " + docDayText(receipt.payment.date),
                modifier = Modifier.padding(top = 2.dp),
                color = c.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatMoney(receipt.payment.amount),
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                paymentLabel(receipt.payment.method),
                modifier = Modifier.padding(top = 2.dp),
                color = c.textSecondary,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
    }
}
