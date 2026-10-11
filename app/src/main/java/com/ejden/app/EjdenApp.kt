package com.ejden.app

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.ejden.app.data.Appearance
import com.ejden.app.data.AppearanceRepository
import com.ejden.app.data.BackupRepository
import com.ejden.app.data.CashRepository
import com.ejden.app.data.ClientRepository
import com.ejden.app.data.CreditPaymentRepository
import com.ejden.app.data.DashboardRepository
import com.ejden.app.data.DocumentRepository
import com.ejden.app.data.ExpenseRepository
import com.ejden.app.data.FileStore
import com.ejden.app.data.KeyValueStore
import com.ejden.app.data.PeriodRepository
import com.ejden.app.data.PreferencesRepository
import com.ejden.app.data.ProductPrefsRepository
import com.ejden.app.data.ProductRepository
import com.ejden.app.data.SaleRepository
import com.ejden.app.data.SettingsRepository
import com.ejden.app.data.TextSize
import com.ejden.app.data.ThemeChoice
import java.io.File

class EjdenApp : Application() {
    lateinit var store: KeyValueStore
        private set
    lateinit var products: ProductRepository
        private set
    lateinit var productPrefs: ProductPrefsRepository
        private set
    lateinit var sales: SaleRepository
        private set
    lateinit var credits: CreditPaymentRepository
        private set
    lateinit var clients: ClientRepository
        private set
    lateinit var expenses: ExpenseRepository
        private set
    lateinit var cash: CashRepository
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var documents: DocumentRepository
        private set
    lateinit var backup: BackupRepository
        private set
    lateinit var appearance: AppearanceRepository
        private set
    lateinit var preferences: PreferencesRepository
        private set
    lateinit var period: PeriodRepository
        private set
    lateinit var dashboard: DashboardRepository
        private set

    /** Apparence en cours : toute l'application la suit, sans redémarrage. */
    lateinit var appearanceState: MutableState<Appearance>
        private set

    override fun onCreate() {
        super.onCreate()
        store = FileStore(File(filesDir, "data"))
        products = ProductRepository(store)
        productPrefs = ProductPrefsRepository(store)
        sales = SaleRepository(store, products)
        credits = CreditPaymentRepository(store, sales)
        clients = ClientRepository(store, sales, credits)
        expenses = ExpenseRepository(store)
        cash = CashRepository(store, sales, credits, expenses)
        settings = SettingsRepository(store)
        documents = DocumentRepository(store)
        backup = BackupRepository(store)
        appearance = AppearanceRepository(store)
        preferences = PreferencesRepository(store)
        period = PeriodRepository(store)
        dashboard = DashboardRepository(sales, cash, clients, credits, products)
        appearanceState = mutableStateOf(appearance.get())
    }

    fun setTheme(choice: ThemeChoice): Boolean {
        val ok = appearance.saveTheme(choice)
        if (ok) appearanceState.value = appearanceState.value.copy(theme = choice)
        return ok
    }

    fun setTextSize(size: TextSize): Boolean {
        val ok = appearance.saveTextSize(size)
        if (ok) appearanceState.value = appearanceState.value.copy(textSize = size)
        return ok
    }

    fun setReduceMotion(enabled: Boolean): Boolean {
        val ok = appearance.saveReduceMotion(enabled)
        if (ok) appearanceState.value = appearanceState.value.copy(reduceMotion = enabled)
        return ok
    }
}
