package com.ejden.app.data

/** Le reçu d'un paiement de facture, tel qu'il s'affiche, s'imprime et se partage. */
fun receiptToDocument(receipt: InvoiceReceipt): PrintableDocument {
    val invoice = receipt.invoice
    val payment = receipt.payment
    val amount = payment.amount
    val settled = receipt.remainingAfter == 0.0

    val description = if (invoice.subject.isNotEmpty()) {
        "Paiement facture " + invoice.number + " — " + invoice.subject
    } else {
        "Paiement facture " + invoice.number
    }

    val situation = if (settled) {
        "Facture n° " + invoice.number + " entièrement réglée."
    } else {
        "Reste à payer sur la facture n° " + invoice.number + " : " + formatMoney(receipt.remainingAfter) + "."
    }

    return PrintableDocument(
        title = "REÇU",
        number = receipt.number,
        issueDate = payment.date,
        dueLabel = null,
        dueDate = null,
        stamp = if (settled) "SOLDÉ" else "ACOMPTE",
        party = DocumentParty(
            label = "Reçu de",
            name = invoice.clientName,
            phone = invoice.clientPhone,
            email = invoice.clientEmail,
            address = invoice.clientAddress
        ),
        subject = "Paiement de la facture n° " + invoice.number,
        lines = listOf(DocumentLine(null, description, "", 1.0, amount, 0.0)),
        taxRate = 0.0,
        depositPct = 0.0,
        depositLabel = "",
        totals = DocumentTotals(amount, 0.0, amount, 0.0, amount, 0.0, amount),
        blocks = listOf(
            DocumentBlock("Mode de paiement", paymentLabel(payment.method)),
            DocumentBlock("Référence", payment.note),
            DocumentBlock("Situation de la facture", situation)
        ),
        payments = emptyList(),
        balanceDue = null,
        amountInWordsLead = "Reçu la somme de",
        signatures = Pair("Signature du client", "Cachet et signature")
    )
}
