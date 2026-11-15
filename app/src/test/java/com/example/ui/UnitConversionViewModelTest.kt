package com.example.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Supplier
import com.example.data.repository.StoreRepository
import com.example.domain.CalculationEngine
import com.example.ui.viewmodel.StoreViewModel
import com.example.util.AppLanguage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UnitConversionViewModelTest {

    private lateinit var context: Context
    private lateinit var repository: StoreRepository
    private lateinit var viewModel: StoreViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = StoreRepository(db = null, appContext = context)
        viewModel = StoreViewModel(
            storeId = "demo_store",
            repository = repository,
            networkMonitor = null,
            currentUserId = "demo_user"
        )
    }

    private suspend fun waitUntil(timeoutMs: Long = 3000L, predicate: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!predicate()) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                throw AssertionError("Condition not met within ${timeoutMs}ms")
            }
            delay(25L)
        }
    }

    @Test
    fun viewModel_addBuySellUnits_cartBlocking_andCheckout() = runBlocking {
        val collectJob = launch { viewModel.products.collect { } }
        try {
            waitUntil { viewModel.products.value.isNotEmpty() }
            val legacyBefore = viewModel.products.value.first()

            // 1. Add new product via wizard
            var added = false
            viewModel.addNewProductWizard(
                name = "Sugar Premium",
                nameBn = "চিনি",
                category = "Grocery",
                unit = "kg",
                buyPrice = 0.0,
                sellPrice = 160.0,
                stock = 0.0,
                lowStockLimit = 5.0,
                photoUri = "",
                buyUnit = "box",
                buyConversion = 25.0,
                sellUnits = mapOf("gram" to 1000.0),
                onSuccess = { added = true }
            )
            waitUntil { added && viewModel.products.value.any { it.name == "Sugar Premium" } }
            val prod = viewModel.products.value.first { it.name == "Sugar Premium" }
            assertEquals("kg", prod.unit)
            assertEquals("box", prod.buyUnit)
            assertEquals(25.0, prod.buyConversion, 1e-6)

            // 2. Record purchase of 2 boxes at 3,000 each -> 50 kg stock, 120/kg cost
            var purchaseDone = false
            val pItem = CalculationEngine.buildPurchaseItem(
                product = prod,
                buyQty = 2.0,
                buyUnitPrice = 3000.0,
                buyUnit = "box",
                conversion = 25.0
            )
            viewModel.recordPurchase(
                supplier = Supplier(id = "sup_vm", storeId = "demo_store", name = "Mill"),
                items = listOf(pItem),
                paidAmount = 6000.0,
                note = "2 boxes",
                onSuccess = { purchaseDone = true }
            )
            waitUntil {
                purchaseDone && (viewModel.products.value.first { it.id == prod.id }.stock >= 49.9)
            }
            val afterPurchase = viewModel.products.value.first { it.id == prod.id }
            assertEquals(50.0, afterPurchase.stock, 1e-6)
            assertEquals(120.0, afterPurchase.buyPrice, 1e-4)

            // 3. Add 500 g + 3 kg to cart -> 560 total, 140 profit, 3.5 stock eq
            viewModel.addProductToCart(afterPurchase, qtyToAdd = 500.0, unit = "gram")
            viewModel.addProductToCart(afterPurchase, qtyToAdd = 3.0, unit = "kg")
            assertEquals(2, viewModel.cart.value.items.size)
            assertEquals(560.0, viewModel.cart.value.grandTotal, 1e-4)
            assertEquals(140.0, viewModel.cart.value.projectedProfit, 1e-4)
            assertEquals(3.5, viewModel.cart.value.totalItems, 1e-6)

            // 4. Attempting to add another 47 kg (total 50.5 kg > 50 kg stock) must be blocked
            viewModel.addProductToCart(afterPurchase, qtyToAdd = 47.0, unit = "kg")
            assertNotNull(viewModel.errorMessage.value)
            assertEquals(3.5, viewModel.cart.value.totalItems, 1e-6)
            viewModel.clearErrorMessage()

            // 5. Complete checkout -> remaining stock is 46.5 kg & receipt shows gram + kg
            var receiptText = ""
            viewModel.checkoutCurrentCart { receipt ->
                receiptText = receipt.formatTextReceipt(AppLanguage.EN)
            }
            waitUntil {
                receiptText.isNotEmpty() && viewModel.products.value.first { it.id == prod.id }.stock < 47.0
            }
            val afterCheckout = viewModel.products.value.first { it.id == prod.id }
            assertEquals(46.5, afterCheckout.stock, 1e-6)
            assertTrue(receiptText.contains("500 gram"))
            assertTrue(receiptText.contains("3 kg"))

            // 6. Existing legacy product remained untouched
            val legacyAfter = viewModel.products.value.first { it.id == legacyBefore.id }
            assertEquals(legacyBefore.unit, legacyAfter.unit)
            assertEquals(legacyBefore.stock, legacyAfter.stock, 1e-6)
            assertEquals(1.0, CalculationEngine.effectiveBuyConversion(legacyAfter), 1e-6)
        } finally {
            collectJob.cancel()
        }
    }
}
