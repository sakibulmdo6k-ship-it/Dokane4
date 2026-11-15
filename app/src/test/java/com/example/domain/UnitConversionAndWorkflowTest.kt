package com.example.domain

import com.example.data.model.CompletedSaleReceipt
import com.example.data.model.Product
import com.example.data.model.Purchase
import com.example.data.model.Sale
import com.example.data.model.SaleItem
import com.example.data.model.Supplier
import com.example.data.repository.StoreRepository
import com.example.util.AppLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitConversionAndWorkflowTest {

    @Test
    fun supportedUnits_containsAllRequiredUnits() {
        val expected = listOf(
            "pcs", "dozen", "box", "pack", "bag", "carton",
            "kg", "gram", "litre", "ml", "meter", "feet"
        )
        assertEquals(expected, CalculationEngine.SUPPORTED_UNITS)
    }

    @Test
    fun mandatoryScenario_buyBoxes_sellGramsAndKg_verifiesStockPriceProfitReceiptAndBlocking() = runBlocking {
        val repo = StoreRepository(db = null, appContext = null)
        val storeId = "demo_store"

        // 1. Add product: Rice counted in kg, bought by box (1 box = 25 kg), sold in kg & gram (1 kg = 1000 g)
        val addOutcome = repo.addProduct(
            storeId = storeId,
            product = Product(
                name = "Test Rice",
                category = "Grocery",
                unit = "kg",
                buyUnit = "box",
                buyConversion = 25.0,
                sellUnits = mapOf("gram" to 1000.0),
                buyPrice = 0.0,
                sellPrice = 160.0,
                stock = 0.0,
                lowStockLimit = 5.0
            )
        )
        val initialProd = repo.observeProducts(storeId).first().first { it.id == addOutcome.productId }
        assertEquals("kg", initialProd.unit)
        assertEquals("box", initialProd.buyUnit)
        assertEquals(25.0, initialProd.buyConversion, 1e-6)
        assertEquals(0.0, initialProd.stock, 1e-6)

        // 2. Buy 2 boxes at 3,000 each -> stock = 50.0 kg, buyPrice = 120.0 / kg
        val purchaseItem = CalculationEngine.buildPurchaseItem(
            product = initialProd,
            buyQty = 2.0,
            buyUnitPrice = 3000.0,
            buyUnit = "box",
            conversion = 25.0
        )
        assertEquals(50.0, purchaseItem.stockQty, 1e-6)
        assertEquals(120.0, purchaseItem.costPerStockUnit, 1e-4)
        assertEquals(6000.0, purchaseItem.lineTotal, 1e-4)

        val supplier = Supplier(id = "sup_test", storeId = storeId, name = "Rice Mill")
        repo.recordPurchase(
            storeId = storeId,
            purchase = Purchase(
                storeId = storeId,
                supplierId = supplier.id,
                supplierName = supplier.name,
                items = listOf(purchaseItem),
                totalItems = purchaseItem.stockQty,
                totalAmount = purchaseItem.lineTotal,
                paidAmount = purchaseItem.lineTotal,
                dueAmount = 0.0,
                paymentType = "paid"
            )
        )

        val afterPurchase = repo.observeProducts(storeId).first().first { it.id == addOutcome.productId }
        assertEquals(50.0, afterPurchase.stock, 1e-6)
        assertEquals(120.0, afterPurchase.buyPrice, 1e-4)

        // 3. Sell 500 g and 3 kg -> price: 500*0.16=80 + 3*160=480 = 560; profit: (80-60)+(480-360) = 140
        val gramLine = CalculationEngine.buildSaleItem(afterPurchase, qty = 500.0, unit = "gram")
        val kgLine = CalculationEngine.buildSaleItem(afterPurchase, qty = 3.0, unit = "kg")
        assertEquals(0.16, gramLine.sellPrice, 1e-4)
        assertEquals(0.12, gramLine.buyPrice, 1e-4)
        assertEquals(0.5, gramLine.stockQty, 1e-6)
        assertEquals(80.0, gramLine.lineTotal, 1e-4)
        assertEquals(20.0, gramLine.profit, 1e-4)

        assertEquals(160.0, kgLine.sellPrice, 1e-4)
        assertEquals(3.0, kgLine.stockQty, 1e-6)
        assertEquals(480.0, kgLine.lineTotal, 1e-4)
        assertEquals(120.0, kgLine.profit, 1e-4)

        val invoiceNo = repo.executeSaleCheckout(
            storeId = storeId,
            items = listOf(gramLine, kgLine),
            paymentType = "cash"
        )
        assertTrue(invoiceNo.startsWith("INV-"))

        val afterSale = repo.observeProducts(storeId).first().first { it.id == addOutcome.productId }
        assertEquals(46.5, afterSale.stock, 1e-6)

        // Check stock value of this product = 46.5 * 120 = 5580.0
        val singleStockSummary = CalculationEngine.calculateStockSummary(listOf(afterSale))
        assertEquals(5580.0, singleStockSummary.stockValueAtCost, 1e-4)

        // Check receipt text shows both units used
        val receipt = CompletedSaleReceipt(
            invoiceNo = invoiceNo,
            storeName = "Test Store",
            items = listOf(gramLine, kgLine),
            totalItems = 3.5,
            subtotal = 560.0,
            discount = 0.0,
            grandTotal = 560.0,
            paymentType = "cash"
        )
        val receiptText = receipt.formatTextReceipt(AppLanguage.EN)
        assertTrue(receiptText.contains("500 gram"))
        assertTrue(receiptText.contains("3 kg"))

        // Check sold stats keep gram & kg separated cleanly with unit labels
        val saleRecord = repo.observeSales(storeId).first().first { it.invoiceNo == invoiceNo }
        assertEquals(560.0, saleRecord.grandTotal, 1e-4)
        assertEquals(140.0, saleRecord.profit, 1e-4)
        val soldStats = CalculationEngine.calculateProductsSoldStats(listOf(saleRecord))
        assertEquals(2, soldStats.size)
        val gramStat = soldStats.first { it.unit == "gram" }
        val kgStat = soldStats.first { it.unit == "kg" }
        assertEquals(500.0, gramStat.quantitySold, 1e-6)
        assertEquals(3.0, kgStat.quantitySold, 1e-6)

        // 4. Block selling more than 46.5 kg (single line or combined multi-unit lines)
        assertFalse(CalculationEngine.isStockSufficient(afterSale.stock, 46.51))
        var threw = false
        try {
            repo.executeSaleCheckout(
                storeId = storeId,
                items = listOf(
                    CalculationEngine.buildSaleItem(afterSale, qty = 46.0, unit = "kg"),
                    CalculationEngine.buildSaleItem(afterSale, qty = 600.0, unit = "gram")
                ),
                paymentType = "cash"
            )
        } catch (e: IllegalStateException) {
            threw = true
        }
        assertTrue("Selling 46 kg + 600 g when stock is 46.5 kg must throw", threw)

        val afterBlocked = repo.observeProducts(storeId).first().first { it.id == addOutcome.productId }
        assertEquals(46.5, afterBlocked.stock, 1e-6)
        assertTrue(afterBlocked.stock >= 0.0)
    }

    @Test
    fun legacyProductAndOldSale_stayUnchanged() = runBlocking {
        val repo = StoreRepository(db = null, appContext = null)
        val storeId = "demo_store"
        val existingProducts = repo.observeProducts(storeId).first()
        val oldProduct = existingProducts.first()

        // Default buyUnit falls back cleanly to stock unit and conversion = 1.0
        assertEquals(oldProduct.unit, CalculationEngine.effectiveStockUnit(oldProduct))
        assertEquals(oldProduct.unit, CalculationEngine.effectiveBuyUnit(oldProduct))
        assertEquals(1.0, CalculationEngine.effectiveBuyConversion(oldProduct), 1e-6)
        assertEquals(listOf(oldProduct.unit to 1.0), CalculationEngine.availableSellUnits(oldProduct))

        // Old sale without stockQty keeps exact qty, price, total, profit, and receipt output
        val legacySaleItem = SaleItem(
            productId = oldProduct.id,
            name = oldProduct.name,
            qty = 2.0,
            unit = oldProduct.unit,
            buyPrice = oldProduct.buyPrice,
            sellPrice = oldProduct.sellPrice,
            lineTotal = oldProduct.sellPrice * 2.0,
            profit = (oldProduct.sellPrice - oldProduct.buyPrice) * 2.0
        )
        assertEquals(2.0, CalculationEngine.saleItemStockQty(legacySaleItem), 1e-6)

        val legacySale = Sale(
            id = "sale_legacy_1",
            storeId = storeId,
            invoiceNo = "INV-0099",
            items = listOf(legacySaleItem),
            totalItems = 2.0,
            subtotal = legacySaleItem.lineTotal,
            grandTotal = legacySaleItem.lineTotal,
            profit = legacySaleItem.profit,
            paymentType = "cash"
        )
        val stats = CalculationEngine.calculateProductsSoldStats(listOf(legacySale))
        assertEquals(1, stats.size)
        assertEquals(2.0, stats.first().quantitySold, 1e-6)
        assertEquals(oldProduct.unit, stats.first().unit)
        assertEquals(legacySaleItem.lineTotal, stats.first().totalSalesAmount, 1e-4)
        assertEquals(legacySaleItem.profit, stats.first().totalProfit, 1e-4)
    }
}
