package com.ejden.app.ui.sales

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ejden.app.EjdenApp
import com.ejden.app.data.CartLine
import com.ejden.app.data.CartRefresh
import com.ejden.app.data.CartResult
import com.ejden.app.data.CheckoutField
import com.ejden.app.data.CheckoutInput
import com.ejden.app.data.CheckoutPlan
import com.ejden.app.data.Client
import com.ejden.app.data.DiscountResult
import com.ejden.app.data.NEW_CLIENT
import com.ejden.app.data.PaymentMethod
import com.ejden.app.data.Product
import com.ejden.app.data.SaleCheckout
import com.ejden.app.data.StockStatus
import com.ejden.app.data.SubmitResult
import com.ejden.app.data.addToCart
import com.ejden.app.data.cartQuantity
import com.ejden.app.data.cartSubtotal
import com.ejden.app.data.cartTotal
import com.ejden.app.data.changeCartQuantity
import com.ejden.app.data.clampDiscount
import com.ejden.app.data.clientDebts
import com.ejden.app.data.formatMoney
import com.ejden.app.data.parseDiscount
import com.ejden.app.data.plainNumber
import com.ejden.app.data.planCheckout
import com.ejden.app.data.refreshCart
import com.ejden.app.data.removeFromCart
import com.ejden.app.data.stockStatusOf
import com.ejden.app.data.jsTrim
import com.ejden.app.ui.components.AppIcons
import com.ejden.app.ui.components.ProductIcons
import com.ejden.app.ui.components.SalesIcons
import com.ejden.app.ui.components.StrokeIcon
import com.ejden.app.ui.components.pressable
import com.ejden.app.ui.components.rememberResumeKey
import com.ejden.app.ui.scanner.BarcodeScannerDialog
import com.ejden.app.ui.scanner.ScannerStyle
import com.ejden.app.ui.scanner.rememberSalesScanner
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.nav.Routes
import com.ejden.app.ui.nav.goBack
import com.ejden.app.ui.nav.open
import com.ejden.app.ui.theme.Ejden
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

private fun pluralize(count: Int, word: String): String = "$count $word" + if (count > 1) "s" else ""

@Composable
fun SalesScreen(nav: NavController) {
    val app = LocalContext.current.applicationContext as EjdenApp
    val c = Ejden.colors
    val resumeKey = rememberResumeKey()
    val collator = remember { Collator.getInstance(Locale.FRENCH) }
    val checkout = remember { SaleCheckout(app.sales, app.clients) }
    val allowCredit = remember { app.preferences.getAllowCredit() }

    // Catalogue.
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(resumeKey) {
        products = withContext(Dispatchers.Default) { app.products.getProducts() }
        loaded = true
    }
    var query by rememberSaveable { mutableStateOf("") }

    // Panier et remise.
    var cart by remember { mutableStateOf<List<CartLine>>(emptyList()) }
    var discount by remember { mutableDoubleStateOf(0.0) }
    var cartOpen by remember { mutableStateOf(false) }
    var checkoutOpen by remember { mutableStateOf(false) }
    var showDiscount by remember { mutableStateOf(false) }

    // Paiement.
    var method by remember { mutableStateOf(PaymentMethod.Cash) }
    var amountField by remember { mutableStateOf(TextFieldValue("")) }
    var clientChoice by remember { mutableStateOf("") }
    var typedName by remember { mutableStateOf("") }
    var saveClient by remember { mutableStateOf(true) }
    var clients by remember { mutableStateOf<List<Client>>(emptyList()) }
    var debts by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    val nameFocus = remember { FocusRequester() }
    val amountFocus = remember { FocusRequester() }

    // En-tête et message.
    var status by remember { mutableStateOf("En cours") }
    var statusToken by remember { mutableIntStateOf(0) }
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
            delay(2800)
            noticeVisible = false
        }
    }
    LaunchedEffect(statusToken) {
        if (statusToken > 0) {
            delay(2500)
            status = "En cours"
        }
    }

    // ---- Calculs ----
    val subtotal = cartSubtotal(cart)
    val currentDiscount = clampDiscount(discount, subtotal)
    val total = cartTotal(cart, currentDiscount)
    val quantity = cartQuantity(cart)

    fun applyCart(result: CartResult) {
        cart = result.lines
        discount = clampDiscount(discount, cartSubtotal(result.lines))
        result.message?.let { notice(it) }
    }

    /** Relit le stock réel et resynchronise le panier. false : la vente ne peut pas continuer. */
    fun refreshCartProducts(): Boolean {
        val latest = app.products.getProducts()
        return when (val result = refreshCart(cart, latest)) {
            is CartRefresh.Failed -> {
                notice(result.message)
                false
            }
            is CartRefresh.Ok -> {
                products = latest
                cart = result.lines
                true
            }
        }
    }

    fun refreshBorrowerClients() {
        val list = app.clients.getClients().sortedWith(Comparator { a, b -> collator.compare(a.name, b.name) })
        clients = list
        debts = clientDebts(list, app.sales.getSales(), app.credits.getCreditPayments())
        val options = (if (list.isNotEmpty()) listOf("") else emptyList()) + list.map { it.id } + NEW_CLIENT
        clientChoice = when {
            clientChoice in options -> clientChoice
            list.isEmpty() -> NEW_CLIENT
            else -> ""
        }
    }

    fun selectMethod(newMethod: PaymentMethod) {
        method = newMethod
        if (newMethod == PaymentMethod.Credit) {
            refreshBorrowerClients()
        } else {
            typedName = ""
            saveClient = true
            clientChoice = ""
        }
        // Crédit : rien n'est encaissé par défaut. Autres moyens : le total complet est proposé.
        val liveTotal = cartTotal(cart, clampDiscount(discount, cartSubtotal(cart)))
        val text = if (newMethod == PaymentMethod.Credit) "0" else plainNumber(liveTotal)
        amountField = TextFieldValue(text, TextRange(0, text.length))
    }

    fun openCheckout() {
        if (cart.isEmpty()) {
            notice("Ajoutez au moins un produit.")
            return
        }
        if (!refreshCartProducts()) return
        if (cartTotal(cart, clampDiscount(discount, cartSubtotal(cart))) <= 0.0) {
            notice("Le montant de la vente est invalide.")
            return
        }
        selectMethod(method)
        checkoutOpen = true
    }

    fun confirmSale() {
        if (cart.isEmpty()) {
            notice("Le panier est vide.")
            return
        }
        // Relecture du stock réel avant validation.
        if (!refreshCartProducts()) return

        val plan = planCheckout(
            CheckoutInput(
                lines = cart,
                discount = clampDiscount(discount, cartSubtotal(cart)),
                method = method,
                amountText = amountField.text,
                clientChoice = clientChoice,
                typedName = typedName,
                saveClient = saveClient,
                clients = app.clients.getClients()
            )
        )

        when (plan) {
            is CheckoutPlan.Rejected -> {
                notice(plan.message)
                try {
                    when (plan.field) {
                        CheckoutField.Name -> nameFocus.requestFocus()
                        CheckoutField.Amount -> amountFocus.requestFocus()
                        else -> Unit
                    }
                } catch (e: IllegalStateException) {
                    // champ pas affiché
                }
            }
            is CheckoutPlan.Ready -> when (val result = checkout.submit(plan)) {
                is SubmitResult.OutOfStock -> {
                    notice("Stock insuffisant : ${result.productName}.")
                    refreshCartProducts()
                }
                SubmitResult.Failed -> notice("La vente n'a pas pu être enregistrée.")
                is SubmitResult.Ok -> {
                    products = app.products.getProducts()
                    cart = emptyList()
                    discount = 0.0
                    checkoutOpen = false
                    cartOpen = false
                    selectMethod(PaymentMethod.Cash)
                    status = "Vente enregistrée"
                    statusToken += 1
                    notice(result.warning ?: ("Vente enregistrée : " + formatMoney(result.total)))
                }
            }
        }
    }

    // Focus sur le montant à l'ouverture de l'encaissement.
    val scanner = rememberSalesScanner({ app.products.getProducts() }, { cart }, { products = it }, { applyCart(it) }, { notice(it) })
    LaunchedEffect(checkoutOpen, method) {
        if (checkoutOpen && method == PaymentMethod.Credit) {
            delay(300)
            try {
                amountFocus.requestFocus()
            } catch (e: IllegalStateException) {
                // panneau pas encore affiché
            }
        }
    }

    // Retour : l'encaissement, puis le panier, puis l'écran.
    BackHandler(enabled = checkoutOpen) { checkoutOpen = false }
    BackHandler(enabled = cartOpen && !checkoutOpen) { cartOpen = false }

    // ---- Catalogue filtré ----
    val search = query.jsTrim().lowercase(Locale.FRANCE)
    val filtered = remember(products, search) {
        if (search.isEmpty()) {
            products
        } else {
            products.filter {
                it.name.lowercase(Locale.FRANCE).contains(search) ||
                    (it.barcode?.lowercase(Locale.FRANCE) ?: "").contains(search)
            }
        }
    }

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            SalesHeader(
                status = status,
                onBack = { nav.goBack() },
                onHistory = { nav.open(Routes.module("historique-ventes")) }
            )

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 202.dp + navInset)
            ) {
                SearchArea(
                    query = query,
                    onQuery = { query = it },
                    onScan = { scanner.open() }
                )

                Column(Modifier.padding(start = 2.dp, end = 2.dp, top = 24.dp, bottom = 12.dp)) {
                    Text("Produits", color = c.text, fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight(750))
                    Spacer(Modifier.height(3.dp))
                    Text(
                        when {
                            products.isEmpty() -> "Aucun produit"
                            search.isNotEmpty() -> pluralize(filtered.size, "résultat")
                            else -> pluralize(filtered.size, "produit")
                        },
                        color = c.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight(500)
                    )
                }

                if (loaded) {
                    if (filtered.isEmpty()) {
                        val hasSearch = search.isNotEmpty()
                        EmptyCard(
                            SalesIcons.Box,
                            if (hasSearch) "Aucun produit trouvé" else "Aucun produit disponible",
                            if (hasSearch) "Essayez un autre nom ou code-barres."
                            else "Ajoutez d'abord vos produits pour commencer une vente."
                        ) {
                            if (!hasSearch) {
                                Box(
                                    Modifier
                                        .padding(top = 18.dp)
                                        .heightIn(min = 42.dp)
                                        .background(c.primary, RoundedCornerShape(12.dp))
                                        .tap { nav.open(Routes.PRODUCT_ADD) }
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Ajouter un produit",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (product in filtered) {
                                ProductRow(product) { applyCart(addToCart(cart, products, product.id)) }
                            }
                        }
                    }
                }
            }
        }

        CartBar(
            count = quantity,
            total = total,
            onCart = { cartOpen = true },
            onPay = { openCheckout() },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        CartPanel(
            visible = cartOpen,
            lines = cart,
            quantity = quantity,
            subtotal = subtotal,
            discount = currentDiscount,
            total = total,
            onClose = { cartOpen = false },
            onChange = { id, delta -> applyCart(changeCartQuantity(cart, id, delta)) },
            onRemove = { id -> applyCart(removeFromCart(cart, id)) },
            onDiscount = {
                if (subtotal <= 0.0) notice("Le panier est vide.") else showDiscount = true
            },
            onCheckout = { openCheckout() }
        )

        CheckoutPanel(
            visible = checkoutOpen,
            total = total,
            method = method,
            allowCredit = allowCredit,
            amountField = amountField,
            onAmountChange = { amountField = it },
            clients = clients,
            debts = debts,
            clientChoice = clientChoice,
            onClientChoice = { choice ->
                clientChoice = choice
                if (choice == NEW_CLIENT) {
                    try {
                        nameFocus.requestFocus()
                    } catch (e: IllegalStateException) {
                        // le champ s'affiche juste après
                    }
                }
            },
            typedName = typedName,
            onTypedName = { typedName = it },
            saveClient = saveClient,
            onSaveClient = { saveClient = it },
            onMethod = { selectMethod(it) },
            onClose = { checkoutOpen = false },
            onConfirm = { confirmSale() },
            nameFocus = nameFocus,
            amountFocus = amountFocus
        )

        if (noticeVisible) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 92.dp + navInset)
                    .fillMaxWidth()
                    .shadow(
                        10.dp, RoundedCornerShape(13.dp),
                        ambientColor = Color(0xFF172126).copy(alpha = 0.20f),
                        spotColor = Color(0xFF172126).copy(alpha = 0.20f)
                    )
                    .background(c.text, RoundedCornerShape(13.dp))
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StrokeIcon(SalesIcons.Check, c.surface, 19.dp, strokeWidth = 2f)
                Text(noticeText, color = c.surface, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight(600))
            }
        }
    }

    BarcodeScannerDialog(scanner, ScannerStyle.Sales, "Scanner un produit")
    if (showDiscount) {
        var text by remember { mutableStateOf(if (discount > 0.0) plainNumber(discount) else "") }
        AlertDialog(
            onDismissRequest = { showDiscount = false },
            text = {
                Column {
                    Text("Montant de la remise en FCFA :")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDiscount = false
                    when (val result = parseDiscount(text, cartSubtotal(cart))) {
                        is DiscountResult.Invalid -> notice(result.message)
                        is DiscountResult.Ok -> discount = clampDiscount(result.amount, cartSubtotal(cart))
                    }
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDiscount = false }) { Text("Annuler") } }
        )
    }
}

// ---------------------------------------------------------------------------
// En-tête, recherche, produits, barre du panier
// ---------------------------------------------------------------------------

@Composable
private fun SalesHeader(status: String, onBack: () -> Unit, onHistory: () -> Unit) {
    val c = Ejden.colors
    Column(Modifier.fillMaxWidth().background(c.background.copy(alpha = 0.94f)).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HeaderButton(ProductIcons.Back, onBack)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nouvelle vente", color = c.text, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(1.dp))
                Text(status, color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight(500))
            }
            HeaderButton(SalesIcons.History, onHistory)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    }
}

@Composable
private fun HeaderButton(icon: com.ejden.app.ui.components.IconSpec, onClick: () -> Unit) {
    val c = Ejden.colors
    Box(
        Modifier
            .size(42.dp)
            .pressable(0.94f, onClick)
            .background(c.surface, RoundedCornerShape(12.dp))
            .border(1.dp, c.border, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        StrokeIcon(icon, c.text, 21.dp)
    }
}

@Composable
private fun SearchArea(query: String, onQuery: (String) -> Unit, onScan: () -> Unit) {
    val c = Ejden.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)

    Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(c.surface, shape)
                .border(1.dp, if (focused) c.primary else c.border, shape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StrokeIcon(SalesIcons.Search, c.textSecondary, 20.dp)
            Spacer(Modifier.width(9.dp))
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 14.sp),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text("Rechercher un produit", color = c.textSecondary, fontSize = 14.sp)
                        }
                        inner()
                    }
                }
            )
            if (query.isNotEmpty()) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(if (Ejden.dark) Color(0xFF202D32) else c.background, CircleShape)
                        .tap { onQuery("") },
                    contentAlignment = Alignment.Center
                ) {
                    StrokeIcon(AppIcons.Close, c.textSecondary, 15.dp)
                }
            }
        }

        Row(
            Modifier
                .width(94.dp)
                .fillMaxHeight()
                .pressable(0.96f, onScan)
                .background(c.primarySoft, shape),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally)
        ) {
            StrokeIcon(SalesIcons.Scan, c.primary, 19.dp, strokeWidth = 1.7f)
            Text("Scanner", color = c.primary, fontSize = 12.sp, fontWeight = FontWeight(700))
        }
    }
}

@Composable
private fun ProductRow(product: Product, onAdd: () -> Unit) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(16.dp)
    val status = stockStatusOf(product)
    val stockColor = when (status) {
        StockStatus.Out -> c.error
        StockStatus.Low -> c.warning
        StockStatus.Normal -> c.textSecondary
    }
    val stockText = when (status) {
        StockStatus.Out -> "Rupture de stock"
        StockStatus.Low -> "Stock faible : ${product.stock}"
        StockStatus.Normal -> "Stock : ${product.stock}"
    }
    val glow = Color(0xFF172126).copy(alpha = 0.06f)

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 74.dp)
            .then(if (Ejden.dark) Modifier else Modifier.shadow(2.dp, shape, ambientColor = glow, spotColor = glow))
            .background(c.surface, shape)
            .border(1.dp, c.border, shape)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SalesPhoto(product, 54.dp, 12.dp, 160, 24.dp)

        Column(Modifier.weight(1f)) {
            Text(
                product.name,
                color = c.text,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight(650),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                stockText,
                color = stockColor,
                fontSize = 11.sp,
                fontWeight = if (status == StockStatus.Normal) FontWeight.Normal else FontWeight(650)
            )
            Spacer(Modifier.height(4.dp))
            Text(formatMoney(product.salePrice), color = c.primary, fontSize = 13.sp, fontWeight = FontWeight(750))
        }

        val enabled = product.stock > 0
        Box(
            Modifier
                .size(42.dp)
                .then(if (enabled) Modifier.pressable(0.92f, onAdd) else Modifier)
                .background(if (enabled) c.primary else c.border, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            StrokeIcon(AppIcons.Plus, if (enabled) Color.White else c.textSecondary, 19.dp, strokeWidth = 2f)
        }
    }
}

@Composable
private fun CartBar(
    count: Int, total: Double,
    onCart: () -> Unit, onPay: () -> Unit,
    modifier: Modifier
) {
    val c = Ejden.colors
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val shape = RoundedCornerShape(18.dp)
    val glow = Color(0xFF12566C).copy(alpha = 0.28f)

    Row(
        modifier
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp + navInset)
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .shadow(14.dp, shape, ambientColor = glow, spotColor = glow)
            .background(c.primary, shape)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.weight(1f).pressable(0.97f, onCart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(42.dp).background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                StrokeIcon(SalesIcons.Cart, Color.White, 21.dp, strokeWidth = 1.7f)
            }
            Column {
                Text("Panier", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight(750))
                Spacer(Modifier.height(2.dp))
                Text(pluralize(count, "article"), color = Color.White.copy(alpha = 0.78f), fontSize = 11.sp)
            }
        }
        Row(
            Modifier
                .pressable(0.96f, onPay)
                .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text("Encaisser · " + formatMoney(total), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight(800))
            StrokeIcon(AppIcons.Arrow, Color.White, 18.dp)
        }
    }
}
