package com.ejden.app.data

/** Le devis tel qu'il s'affiche, s'imprime et se partage. */
fun quoteToDocument(
    quote: Quote,
    shop: ShopSettings,
    settings: DocumentSettings,
    status: QuoteDisplayStatus
): PrintableDocument {
    val stamp = when (status) {
        QuoteDisplayStatus.Accepted -> "ACCEPTÉ"
        QuoteDisplayStatus.Refused -> "REFUSÉ"
        QuoteDisplayStatus.Expired -> "EXPIRÉ"
        else -> null
    }

    return PrintableDocument(
        title = "DEVIS",
        number = quote.number,
        issueDate = quote.issueDate,
        dueLabel = "Valable jusqu'au",
        dueDate = quote.validUntil,
        stamp = stamp,
        party = DocumentParty(
            label = "Destinataire",
            name = quote.clientName,
            phone = quote.clientPhone,
            email = quote.clientEmail,
            address = quote.clientAddress
        ),
        subject = quote.subject,
        lines = quote.lines,
        taxRate = quote.taxRate,
        depositPct = quote.depositPct,
        depositLabel = "Acompte à la commande",
        totals = quote.totals(),
        blocks = listOf(
            DocumentBlock("Délai de livraison", quote.deliveryTerms),
            DocumentBlock("Conditions de paiement", quote.paymentTerms),
            DocumentBlock("Moyens de paiement", settings.paymentInfo),
            DocumentBlock("Notes", quote.notes),
            DocumentBlock("Conditions générales", quote.terms)
        ),
        payments = emptyList(),
        balanceDue = null,
        amountInWordsLead = "Arrêté le présent devis à la somme de",
        signatures = Pair(
            "Le client — « Bon pour accord », date et signature",
            if (shop.name.isNotEmpty()) "Pour " + shop.name else "Pour l'entreprise"
        )
    )
}
