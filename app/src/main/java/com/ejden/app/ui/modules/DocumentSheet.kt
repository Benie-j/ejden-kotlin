package com.ejden.app.ui.modules

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ejden.app.data.DocumentLine
import com.ejden.app.data.DocumentSettings
import com.ejden.app.data.PrintableDocument
import com.ejden.app.data.ShopSettings
import com.ejden.app.data.amountInWords
import com.ejden.app.data.documentLineTotal
import com.ejden.app.data.formatDay
import com.ejden.app.data.formatNumber
import com.ejden.app.data.formatPercent
import com.ejden.app.data.formatQuantity
import com.ejden.app.ui.theme.Ejden

private val DocInk = Color(0xFF111111)
private val DocText = Color(0xFF333333)
private val DocTeal = Color(0xFF176B87)
private val DocGray = Color(0xFF68777D)
private val DocRule = Color(0xFFDDE4E7)
private val DocBorder = Color(0xFFC9D3D7)
private val DocGreen = Color(0xFF168A5B)

/** « data:image/png;base64,… » → image (null si illisible). */
private fun decodeDocLogo(dataUrl: String): ImageBitmap? = try {
    val comma = dataUrl.indexOf(',')
    if (comma < 0) {
        null
    } else {
        val bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }
} catch (e: Exception) {
    null
}

@Composable
private fun DocLines(text: String) {
    for (line in text.split("\n")) {
        if (line.trim().isNotEmpty()) {
            Text(line, color = DocText, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

/** La feuille du document, comme sur le papier (mise en page téléphone). */
@Composable
internal fun DocumentSheet(doc: PrintableDocument, shop: ShopSettings, settings: DocumentSettings) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(6.dp)
    val logo = remember(settings.logo) { settings.logo?.let { decodeDocLogo(it) } }
    val t = doc.totals
    val hasTax = doc.taxRate > 0
    val showDiscount = doc.lines.any { it.discountPct > 0 }

    Column(
        Modifier
            .padding(bottom = 16.dp)
            .fillMaxWidth()
            .background(Color.White, shape)
            .border(1.dp, c.border, shape)
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp)
    ) {
        // ----- En-tête : entreprise, puis titre et dates -----
        if (logo != null) {
            Image(
                bitmap = logo,
                contentDescription = null,
                modifier = Modifier.padding(bottom = 8.dp).widthIn(max = 140.dp).heightIn(max = 64.dp),
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart
            )
        }
        Text(
            if (shop.name.isNotEmpty()) shop.name else "Nom de l'entreprise",
            color = DocTeal,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(4.dp))
        DocLines(settings.address)
        if (shop.phone.isNotEmpty()) DocLines("Tél : " + shop.phone)
        DocLines(settings.email)
        DocLines(settings.website)
        if (settings.taxId.isNotEmpty()) DocLines("NIF / IFU : " + settings.taxId)
        if (settings.registry.isNotEmpty()) DocLines("RCCM : " + settings.registry)

        Spacer(Modifier.height(16.dp))
        Text(
            doc.title,
            color = DocTeal,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.08.em
        )
        Text(
            "N° " + doc.number,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
            color = DocInk,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Text("Date : " + formatDay(doc.issueDate), color = DocText, fontSize = 12.sp, lineHeight = 17.sp)
        if (doc.dueLabel != null && doc.dueDate != null) {
            Text(doc.dueLabel + " : " + formatDay(doc.dueDate), color = DocText, fontSize = 12.sp, lineHeight = 17.sp)
        }
        if (doc.stamp != null) {
            Text(
                doc.stamp,
                modifier = Modifier
                    .padding(top = 10.dp, start = 4.dp)
                    .rotate(-4f)
                    .border(2.dp, DocGreen, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                color = DocGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.08.em
            )
        }

        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(DocTeal))

        // ----- Destinataire -----
        Column(
            Modifier
                .padding(top = 16.dp, bottom = 12.dp)
                .fillMaxWidth()
                .border(1.dp, DocBorder, RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                doc.party.label.uppercase(),
                modifier = Modifier.padding(bottom = 2.dp),
                color = DocGray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.em
            )
            Text(doc.party.name, color = DocInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            DocLines(doc.party.address)
            if (doc.party.phone.isNotEmpty()) DocLines("Tél : " + doc.party.phone)
            DocLines(doc.party.email)
        }

        if (doc.subject.isNotEmpty()) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Objet : ") }
                    append(doc.subject)
                },
                modifier = Modifier.padding(bottom = 10.dp),
                color = DocInk,
                fontSize = 13.sp
            )
        }

        // ----- Lignes -----
        doc.lines.forEachIndexed { index, line ->
            DocLineRow(index + 1, line, showDiscount)
        }
        Spacer(Modifier.height(12.dp))

        // ----- Totaux -----
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Column(Modifier.widthIn(max = 320.dp).fillMaxWidth()) {
                val plain = !hasTax && t.discount <= 0
                DocTotalRow(if (plain) "Total" else "Sous-total", fcfaText(t.subtotal), grand = plain)
                if (t.discount > 0) DocTotalRow("Remise", "− " + fcfaText(t.discount))
                if (hasTax) {
                    DocTotalRow("Total HT", fcfaText(t.net))
                    DocTotalRow("TVA (" + formatPercent(doc.taxRate) + ")", fcfaText(t.tax))
                    DocTotalRow("Total TTC", fcfaText(t.total), grand = true)
                } else if (t.discount > 0) {
                    DocTotalRow("Total net à payer", fcfaText(t.total), grand = true)
                }
                if (doc.depositPct > 0) {
                    DocTotalRow(doc.depositLabel + " (" + formatPercent(doc.depositPct) + ")", fcfaText(t.deposit))
                    DocTotalRow("Reste à payer", fcfaText(t.balance))
                }
                for (payment in doc.payments) {
                    DocTotalRow(
                        "Payé le " + formatDay(payment.date) + " (" + payment.label + ")",
                        "− " + fcfaText(payment.amount)
                    )
                }
                val balance = doc.balanceDue
                if (balance != null) {
                    DocTotalRow(if (balance == 0.0) "Solde" else "Reste à payer", fcfaText(balance), balance = true)
                }
            }
        }

        if (!hasTax) {
            Text(
                "TVA non applicable.",
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                color = DocGray,
                fontSize = 11.sp,
                textAlign = TextAlign.End
            )
        }

        val words = amountInWords(t.total)
        if (words.isNotEmpty()) {
            Text(
                buildAnnotatedString {
                    append(doc.amountInWordsLead + " ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(words) }
                    append(".")
                },
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                color = DocInk,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontStyle = FontStyle.Italic
            )
        }

        // ----- Conditions, modalités, notes -----
        val blocks = doc.blocks.filter { it.text.trim().isNotEmpty() }
        for (block in blocks) {
            val blockShape = RoundedCornerShape(4.dp)
            Row(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .background(Color(0xFFF7FAFB), blockShape)
                    .border(1.dp, DocRule, blockShape)
            ) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(DocTeal))
                Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
                    Text(
                        block.title.uppercase(),
                        modifier = Modifier.padding(bottom = 3.dp),
                        color = DocTeal,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.06.em
                    )
                    DocLines(block.text)
                }
            }
        }

        // ----- Signatures -----
        val signatures = doc.signatures
        if (signatures != null) {
            for (label in listOf(signatures.first, signatures.second)) {
                Column(
                    Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .border(1.dp, DocBorder, RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(label, color = DocInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(70.dp))
                }
            }
        }

        // ----- Pied de page -----
        if (settings.footer.trim().isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(DocBorder))
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                for (line in settings.footer.split("\n")) {
                    if (line.trim().isNotEmpty()) {
                        Text(line, color = DocGray, fontSize = 10.5.sp, lineHeight = 16.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

private fun fcfaText(value: Double): String = formatNumber(value) + " FCFA"

/** Une ligne du devis : numéro, désignation, détails et montant. */
@Composable
private fun DocLineRow(number: Int, line: DocumentLine, showDiscount: Boolean) {
    val detail = "Qté : " + (if (line.unit.isNotEmpty()) formatQuantity(line.quantity) + " " + line.unit else formatQuantity(line.quantity)) +
        " · Prix unitaire : " + formatNumber(line.unitPrice) +
        (if (showDiscount && line.discountPct > 0) " · Remise : " + formatPercent(line.discountPct) else "")

    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(number.toString(), modifier = Modifier.width(30.dp), color = DocGray, fontSize = 12.sp)
            Text(
                line.description,
                modifier = Modifier.weight(1f),
                color = DocInk,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 30.dp, top = 2.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                detail,
                modifier = Modifier.weight(1f).padding(end = 10.dp),
                color = DocText,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            Text(
                "Montant : " + formatNumber(documentLineTotal(line)),
                color = DocInk,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(DocRule))
    }
}

@Composable
private fun DocTotalRow(label: String, value: String, grand: Boolean = false, balance: Boolean = false) {
    if (grand) {
        Row(
            Modifier
                .padding(top = 2.dp)
                .fillMaxWidth()
                .background(DocTeal)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.White, fontSize = 14.sp)
            Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        return
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                color = if (balance) DocTeal else DocInk,
                fontSize = if (balance) 13.sp else 12.sp
            )
            Text(
                value,
                color = if (balance) DocTeal else DocInk,
                fontSize = if (balance) 13.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (balance) 2.dp else 1.dp)
                .background(if (balance) DocTeal else Color(0xFFE6ECEE))
        )
    }
}
