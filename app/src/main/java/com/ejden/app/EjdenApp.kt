package com.ejden.app

import android.app.Application
import com.ejden.app.data.AppearanceRepository
import com.ejden.app.data.CashRepository
import com.ejden.app.data.ClientRepository
import com.ejden.app.data.CreditPaymentRepository
import com.ejden.app.data.DashboardRepository
import com.ejden.app.data.ExpenseRepository
import com.ejden.app.data.FileStore
import com.ejden.app.data.KeyValueStore
import com.ejden.app.data.PeriodRepository
import com.ejden.app.data.ProductRepository
import com.ejden.app.data.SaleRepository
import com.ejden.app.data.SettingsRepository
import java.io.File

class EjdenApp : Application() {
    lateinit var store: KeyValueStore
        private set
    lateinit var products: ProductRepository
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
    lateinit var appearance: AppearanceRepository
        private set
    lateinit var period: PeriodRepository
        private set
    lateinit var dashboard: DashboardRepository
        private set

    override fun onCreate() {
        super.onCreate()
        store = FileStore(File(filesDir, "data"))
        products = ProductRepository(store)
        sales = SaleRepository(store, products)
        credits = CreditPaymentRepository(store, sales)
        clients = ClientRepository(store, sales, credits)
        expenses = ExpenseRepository(store)
        cash = CashRepository(store, sales, credits, expenses)
        settings = SettingsRepository(store)
        appearance = AppearanceRepository(store)
        period = PeriodRepository(store)
        dashboard = DashboardRepository(sales, cash, clients, credits, products)
    }
}
