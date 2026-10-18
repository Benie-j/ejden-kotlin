package com.ejden.app.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ejden.app.EjdenApp
import com.ejden.app.data.PrintableDocument
import com.ejden.app.data.Quote
import com.ejden.app.data.QuoteDisplayStatus
import com.ejden.app.data.QuoteStatus
import com.ejden.app.data.documentToHtml
import com.ejden.app.data.documentToText
import com.ejden.app.data.quoteToDocument
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.theme.Ejden

private fun quoteStatusLabel(status: QuoteStatus): String = when (status) {
    QuoteStatus.Draft -> "Brouillon"
    QuoteStatus.Sent -> "Envoyé"
    QuoteStatus.Accepted -> "Accepté"
    QuoteStatus.Refused -> "Refusé"
}

/** Aperçu d'un devis : actions, changement de statut, document, suppression. */
internal fun LazyListScope.quotePreview(
    quote: Quote?,
    loaded: Boolean,
    toast: ModuleToastState,
    onEdit: (Quote) -> Unit,
    onChanged: () -> Unit,
    onGone: () -> Unit,
    onOpen: (String) -> Unit
) {
    if (quote == null) {
        if (loaded) item { ModuleEmpty("Devis introuvable", "Ce devis n'existe plus.") }
        return
    }

    item { QuotePreviewContent(quote, toast, onEdit, onChanged, onGone, onOpen) }
}

@Composable
private fun QuotePreviewContent(
    quote: Quote,
    toast: ModuleToastState,
    onEdit: (Quote) -> Unit,
    onChanged: () -> Unit,
    onGone: () -> Unit,
    onOpen: (String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as EjdenApp
    var deleteAttempt by remember { mutableIntStateOf(0) }

    val status = remember(quote) { app.quotes.displayStatus(quote) }
    val shop = remember(quote) { app.settings.getShop() }
    val settings = remember(quote) { app.documents.getSettings() }
    val doc: PrintableDocument = remember(quote) { quoteToDocument(quote, shop, settings, status) }
    val editable = quote.status == QuoteStatus.Draft || quote.status == QuoteStatus.Sent
    val jobName = doc.title + " " + doc.number

    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Imprimer / PDF", Modifier.fillMaxWidth()) {
                if (!printHtml(context, documentToHtml(doc, shop, settings), jobName)) {
                    toast.show("Impression indisponible sur cet appareil.")
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editable) {
                    ModuleActionButton("Modifier", ModuleButtonStyle.Normal, Modifier.weight(1f)) { onEdit(quote) }
                }
                ModuleActionButton("Dupliquer", ModuleButtonStyle.Normal, Modifier.weight(1f)) {
                    val copy = app.quotes.duplicateQuote(quote.id)
                    if (copy != null) {
                        toast.show("Copie créée : " + copy.number)
                        onOpen(copy.id)
                    } else {
                        toast.show("Impossible de dupliquer le devis.")
                    }
                }
                ModuleActionButton("Partager", ModuleButtonStyle.Normal, Modifier.weight(1f)) {
                    if (!shareDocumentText(context, jobName, documentToText(doc, shop))) {
                        toast.show("Impossible de partager sur cet appareil.")
                    }
                }
            }

            QuoteStatusSwitch(quote.status) { choice ->
                if (choice != quote.status) {
                    if (app.quotes.setQuoteStatus(quote.id, choice)) {
                        toast.show("Devis marqué « " + quoteStatusLabel(choice) + " ».")
                        onChanged()
                    } else {
                        toast.show("Impossible de changer le statut.")
                    }
                }
            }

            if (status == QuoteDisplayStatus.Expired) {
                Text(
                    "Ce devis a dépassé sa date de validité.",
                    color = Ejden.colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }

        DocumentSheet(doc, shop, settings)

        key(deleteAttempt) {
            DeleteControl(
                label = "Supprimer ce devis",
                question = "Supprimer définitivement le devis " + quote.number + " ?"
            ) {
                if (app.quotes.deleteQuote(quote.id)) {
                    toast.show("Devis supprimé.")
                    onGone()
                } else {
                    toast.show("Impossible de supprimer le devis.")
                    deleteAttempt += 1
                }
            }
        }
    }
}

/** Les quatre statuts d'un devis, en boutons côte à côte. */
@Composable
private fun QuoteStatusSwitch(current: QuoteStatus, onPick: (QuoteStatus) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (choice in listOf(QuoteStatus.Draft, QuoteStatus.Sent, QuoteStatus.Accepted, QuoteStatus.Refused)) {
            QuoteStatusPill(quoteStatusLabel(choice), choice == current, Modifier.weight(1f)) { onPick(choice) }
        }
    }
}

@Composable
private fun QuoteStatusPill(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .height(38.dp)
            .background(if (active) c.primary else c.surface, shape)
            .border(1.dp, if (active) c.primary else c.border, shape)
            .tap(onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (active) Color.White else c.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
