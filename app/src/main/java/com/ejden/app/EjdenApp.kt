package com.ejden.app

import android.app.Application
import com.ejden.app.data.CreditPaymentRepository
import com.ejden.app.data.FileStore
import com.ejden.app.data.KeyValueStore
import com.ejden.app.data.ProductRepository
import com.ejden.app.data.SaleRepository
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

    override fun onCreate() {
        super.onCreate()
        store = FileStore(File(filesDir, "data"))
        products = ProductRepository(store)
        sales = SaleRepository(store, products)
        credits = CreditPaymentRepository(store, sales)
    }
}
