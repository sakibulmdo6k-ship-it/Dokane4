package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.Customer
import com.example.data.model.Product
import com.example.data.model.SaleItem
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StoreRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createStoreAndAddProduct_authenticatedOwner_succeedsAndEmits() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = StoreRepository(firestore, appContext)

        val storeId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createStore("Alice Shop", "01711111111", "Dhaka")
        }
        assertTrue(storeId.isNotBlank())

        val outcome = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addProduct(
                storeId,
                Product(
                    name = "Miniket Rice",
                    buyPrice = 300.0,
                    sellPrice = 350.0,
                    stock = 20.0
                )
            )
        }
        assertTrue(outcome.productId.isNotBlank())

        val products = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeProducts(storeId).first { list -> list.any { it.id == outcome.productId } }
        }
        assertTrue(products.any { it.id == outcome.productId && it.name == "Miniket Rice" })
    }

    @Test
    fun executeSaleAndPayment_authenticatedOwner_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = StoreRepository(firestore, appContext)

        val storeId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createStore("Alice POS", "01722222222", "Dhaka")
        }
        val prodOutcome = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addProduct(
                storeId,
                Product(name = "Oil 1L", buyPrice = 150.0, sellPrice = 180.0, stock = 10.0)
            )
        }
        val customerId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addCustomer(storeId, Customer(name = "Rahim", mobile = "01733333333"))
        }

        val invoiceNo = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.executeSaleCheckout(
                storeId = storeId,
                items = listOf(
                    SaleItem(
                        productId = prodOutcome.productId,
                        name = "Oil 1L",
                        qty = 2.0,
                        unit = "bottle",
                        buyPrice = 150.0,
                        sellPrice = 180.0,
                        lineTotal = 360.0,
                        profit = 60.0
                    )
                ),
                paymentType = "baki",
                customerId = customerId,
                customerName = "Rahim"
            )
        }
        assertTrue(invoiceNo.isNotBlank())

        val payNo = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.recordCustomerPaymentAtomic(
                storeId = storeId,
                customerId = customerId,
                customerName = "Rahim",
                customerMobile = "01733333333",
                amount = 100.0,
                note = "Partial payment",
                paymentDate = System.currentTimeMillis()
            )
        }
        assertTrue(payNo.isNotBlank())
    }

    @Test
    fun observeProducts_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val aliceRepo = StoreRepository(firestore, appContext)
        val storeId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createStore("Alice Private Store", "01744444444", "Dhaka")
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = StoreRepository(firestore, appContext)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeProducts(storeId).first()
            }
            fail("Expected PERMISSION_DENIED for cross-user access")
        } catch (e: Exception) {
            val firestoreException = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException!!.code)
        }
    }

    @Test
    fun observeProducts_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val aliceRepo = StoreRepository(firestore, appContext)
        val storeId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createStore("Auth Gate Store", "01755555555", "Dhaka")
        }

        auth.signOut()
        val unauthRepo = StoreRepository(firestore, appContext)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                unauthRepo.observeProducts(storeId).first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated user")
        } catch (e: Exception) {
            val firestoreException = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException!!.code)
        }
    }

    @Test
    fun createAndNonDestructivelyUpdateStoreShopType_authenticatedOwner_succeedsAndKeepsProducts() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = StoreRepository(firestore, appContext)

        val hwCategories = com.example.domain.ShopTypeCatalog.startingCategories(com.example.domain.ShopType.HARDWARE)
        val storeId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createStore(
                name = "Alice Hardware",
                phone = "01766666666",
                address = "Nawabpur, Dhaka",
                shopType = com.example.domain.ShopType.HARDWARE.id,
                productCategories = hwCategories
            )
        }

        val createdStore = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeStore(storeId).first { it != null && it.shopType == "hardware" }
        }
        assertEquals("hardware", createdStore?.shopType)
        assertEquals(hwCategories, createdStore?.productCategories)

        // New real store has zero fake products
        val initialProducts = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeProducts(storeId).first()
        }
        assertTrue(initialProducts.isEmpty())

        // Add a real product, then change shopType in Settings -> product remains untouched
        val prodOutcome = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addProduct(
                storeId,
                Product(
                    name = "Wire Nails 2 inch",
                    category = "Nuts & Bolts",
                    unit = "kg",
                    buyUnit = "box",
                    buyConversion = 25.0,
                    sellUnits = mapOf("gram" to 1000.0),
                    buyPrice = 120.0,
                    sellPrice = 160.0,
                    stock = 50.0
                )
            )
        }

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateStoreShopType(
                storeId = storeId,
                shopType = com.example.domain.ShopType.GROCERY,
                addSuggestedCategories = true
            )
        }

        val updatedStore = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeStore(storeId).first { it != null && it.shopType == "grocery" }
        }
        assertTrue(updatedStore!!.productCategories.containsAll(hwCategories))
        assertTrue(updatedStore.productCategories.contains("Rice & Grains"))

        val productsAfter = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeProducts(storeId).first { list -> list.any { it.id == prodOutcome.productId } }
        }
        assertEquals(1, productsAfter.size)
        assertEquals("Wire Nails 2 inch", productsAfter.first().name)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_store@test.com"
        const val BOB_EMAIL = "bob_other@test.com"
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
