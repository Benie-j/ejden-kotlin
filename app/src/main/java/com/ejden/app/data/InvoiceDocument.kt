package com.ejden.app.data

/** La facture telle qu'elle s'affiche, s'imprime et se partage. */
fun invoiceToDocument(
    invoice: Invoice,
    settings: DocumentSettings,
    status: InvoiceDisplayStatus
): PrintableDocument {
    val stamp = when (status) {
        InvoiceDisplayStatus.Paid -> "PAYÉE"
        InvoiceDisplayStatus.Cancelled -> "ANNULÉE"
        InvoiceDisplayStatus.Draft -> "BROUILLON"
        InvoiceDisplayStatus.Overdue -> "EN RETARD"
        else -> null
    }

    val blocks = ArrayList<DocumentBlock>()
    if (invoice.quoteNumber.isNotEmpty()) {
        blocks.add(DocumentBlock("Référence", "Devis n° " + invoice.quoteNumber))
    }
    blocks.add(DocumentBlock("Modalités de paiement", invoice.paymentTerms))
    blocks.add(DocumentBlock("Moyens de paiement", settings.paymentInfo))
    blocks.add(DocumentBlock("Notes", invoice.notes))
    blocks.add(DocumentBlock("Mentions légales", invoice.terms))

    return PrintableDocument(
        title = "FACTURE",
        number = invoice.number.ifEmpty { "(brouillon)" },
        issueDate = invoice.issueDate,
        dueLabel = "Échéance",
        dueDate = invoice.dueDate,
        stamp = stamp,
        party = DocumentParty(
            label = "Facturé à",
            name = invoice.clientName,
            phone = invoice.clientPhone,
            email = invoice.clientEmail,
            address = invoice.clientAddress
        ),
        subject = invoice.subject,
        lines = invoice.lines,
        taxRate = invoice.taxRate,
        depositPct = 0.0,
        depositLabel = "",
        totals = invoice.totals(),
        blocks = blocks,
        payments = invoice.payments.map { DocumentPayment(it.date, paymentLabel(it.method), it.amount) },
        balanceDue = if (invoice.status == InvoiceStatus.Issued || invoice.payments.isNotEmpty()) {
            invoice.remaining()
        } else {
            null
        },
        amountInWordsLead = "Arrêtée la présente facture à la somme de",
        signatures = null
    )
}
