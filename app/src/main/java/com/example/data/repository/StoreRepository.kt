package com.example.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import com.example.R
import com.example.data.model.Account
import com.example.data.model.AccountAdjustment
import com.example.data.model.Customer
import com.example.data.model.Expense
import com.example.data.model.HealthFixLog
import com.example.data.model.InviteCodeConfig
import com.example.data.model.OtherIncome
import com.example.data.model.Payment
import com.example.data.model.Product
import com.example.data.model.Purchase
import com.example.data.model.PurchaseItem
import com.example.data.model.PurchaseReturn
import com.example.data.model.Sale
import com.example.data.model.SaleItem
import com.example.data.model.Store
import com.example.data.model.StoreCategory
import com.example.data.model.StoreInvite
import com.example.data.model.StoreMember
import com.example.data.model.StoreNote
import com.example.data.model.StorePermissions
import com.example.data.model.Supplier
import com.example.data.model.SupplierPayment
import com.example.data.model.UserProfile
import com.example.domain.CalculationEngine
import com.example.domain.HealthProblem
import com.example.domain.ShopType
import com.example.domain.ShopTypeCatalog
import com.example.util.AppLanguage
import com.example.util.ProductImageUtil
import com.example.util.ShopLocale
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import com.google.firebase.storage.storage
import com.google.firebase.storage.storageMetadata
import java.io.File
import java.util.Date
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

data class ProductSaveOutcome(
    val productId: String,
    val photoUri: String,
    val photoPendingUpload: Boolean = false,
    val uploadWarning: String? = null
)

class StoreRepository(
    private val db: FirebaseFirestore?,
    val appContext: Context? = null
) {

    constructor(context: Context) : this(
        db = try {
            val dbId = context.applicationContext.getString(R.string.firestore_database_id)
            if (dbId.isNotBlank()) FirebaseFirestore.getInstance(dbId) else null
        } catch (e: Exception) {
            Log.w("StoreRepository", "Firestore init fallback: ${e.message}")
            null
        },
        appContext = context.applicationContext
    )

    private val auth get() = try { Firebase.auth } catch (e: Exception) { null }

    val currentUid: String? get() = auth?.currentUser?.uid

    private fun requireUserId(): String {
        return auth?.currentUser?.uid
            ?: "demo_user"
    }

    // =============================================================
    // IN-MEMORY DEMO STORE STATE
    // =============================================================

    private val demoInvoiceCounter = AtomicLong(104L)
    private val demoPaymentCounter = AtomicLong(1L)

    private val _demoStore = MutableStateFlow<Store?>(
        Store(
            id = "demo_store",
            name = "Bismillah General Store (দোকান)",
            phone = "+880 1712-345678",
            address = "Shop 14, New Market, Dhaka",
            ownerId = "demo_user",
            memberUids = listOf("demo_user"),
            members = listOf(StoreMember(userId = "demo_user", role = "owner", status = "active")),
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
    )

    private val _demoProducts = MutableStateFlow<List<Product>>(
        listOf(
            Product(
                id = "prod_1",
                storeId = "demo_store",
                name = "Miniket Rice 5kg",
                nameBn = "মিনিকেট চাল ৫ কেজি",
                barcode = "890123456789",
                category = "Grocery",
                unit = "bag",
                buyPrice = 320.0,
                sellPrice = 375.0,
                stock = 45.0,
                lowStockLimit = 10.0,
                isDemo = true
            ),
            Product(
                id = "prod_2",
                storeId = "demo_store",
                name = "Rupchanda Soybean Oil 2L",
                nameBn = "রূপচাঁদা সয়াবিন তেল ২ লিটার",
                barcode = "890987654321",
                category = "Oil & Ghee",
                unit = "bottle",
                buyPrice = 360.0,
                sellPrice = 395.0,
                stock = 28.0,
                lowStockLimit = 8.0,
                isDemo = true
            ),
            Product(
                id = "prod_3",
                storeId = "demo_store",
                name = "Radhuni Turmeric Powder 200g",
                nameBn = "রাঁধুনী হলুদ গুঁড়া ২০০ গ্রাম",
                barcode = "890456123789",
                category = "Spices",
                unit = "pack",
                buyPrice = 70.0,
                sellPrice = 85.0,
                stock = 60.0,
                lowStockLimit = 15.0,
                isDemo = true
            ),
            Product(
                id = "prod_4",
                storeId = "demo_store",
                name = "Aarong Butter 200g",
                nameBn = "আড়ং মাখন ২০০ গ্রাম",
                barcode = "890321654987",
                category = "Dairy",
                unit = "pack",
                buyPrice = 180.0,
                sellPrice = 210.0,
                stock = 14.0,
                lowStockLimit = 5.0,
                isDemo = true
            ),
            Product(
                id = "prod_5",
                storeId = "demo_store",
                name = "Pran Frooto Mango 1L",
                nameBn = "প্রাণ ফ্রুটো ম্যাঙ্গো জুস",
                barcode = "890789456123",
                category = "Beverages",
                unit = "bottle",
                buyPrice = 90.0,
                sellPrice = 110.0,
                stock = 35.0,
                lowStockLimit = 10.0,
                isDemo = true
            ),
            Product(
                id = "prod_6",
                storeId = "demo_store",
                name = "Lifebuoy Soap 100g",
                nameBn = "লাইফবয় সাবান ১০০ গ্রাম",
                barcode = "890654321987",
                category = "Personal Care",
                unit = "pcs",
                buyPrice = 48.0,
                sellPrice = 60.0,
                stock = 4.0,
                lowStockLimit = 10.0,
                isDemo = true
            ),
            Product(
                id = "prod_7",
                storeId = "demo_store",
                name = "ACI Pure Salt 1kg",
                nameBn = "এসিআই পিওর লবণ ১ কেজি",
                barcode = "890111222333",
                category = "Grocery",
                unit = "pack",
                buyPrice = 35.0,
                sellPrice = 42.0,
                stock = 0.0,
                lowStockLimit = 10.0,
                isDemo = true
            )
        )
    )

    private val _demoCustomers = MutableStateFlow<List<Customer>>(
        listOf(
            Customer(
                id = "cust_1",
                storeId = "demo_store",
                name = "Mohammad Rahim",
                mobile = "01711223344",
                address = "House 12, Road 4, Mirpur",
                notes = "Regular buyer",
                currentBalance = 850.0,
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L * 5))
            ),
            Customer(
                id = "cust_2",
                storeId = "demo_store",
                name = "Kabir Hossain",
                mobile = "01819887766",
                address = "Shop 9, Market Lane",
                notes = "Clears every Thursday",
                currentBalance = 1420.0,
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L * 4))
            ),
            Customer(
                id = "cust_3",
                storeId = "demo_store",
                name = "Dr. Selim Ahmed",
                mobile = "01912334455",
                address = "Sector 3, Uttara",
                notes = "Local pharmacy owner",
                currentBalance = 320.0,
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L * 3))
            ),
            Customer(
                id = "cust_4",
                storeId = "demo_store",
                name = "Fatema Begum",
                mobile = "01677889900",
                address = "Apartment 5B",
                notes = "Clears prompt on 1st",
                currentBalance = 0.0,
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L * 2))
            )
        )
    )

    private val _demoSales = MutableStateFlow<List<Sale>>(
        listOf(
            Sale(
                id = "sale_1",
                storeId = "demo_store",
                invoiceNo = "INV-0101",
                items = listOf(
                    SaleItem(
                        productId = "prod_1",
                        name = "Miniket Rice 5kg",
                        qty = 1.0,
                        unit = "bag",
                        buyPrice = 320.0,
                        sellPrice = 375.0,
                        discount = 15.0,
                        lineTotal = 360.0,
                        profit = 40.0
                    ),
                    SaleItem(
                        productId = "prod_4",
                        name = "Aarong Butter 200g",
                        qty = 1.0,
                        unit = "pack",
                        buyPrice = 180.0,
                        sellPrice = 210.0,
                        discount = 0.0,
                        lineTotal = 210.0,
                        profit = 30.0
                    )
                ),
                totalItems = 2.0,
                subtotal = 585.0,
                discount = 15.0,
                grandTotal = 570.0,
                profit = 70.0,
                paymentType = "baki",
                customerId = "cust_1",
                customerName = "Mohammad Rahim",
                customerPhone = "01711223344",
                soldBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L * 2))
            ),
            Sale(
                id = "sale_2",
                storeId = "demo_store",
                invoiceNo = "INV-0102",
                items = listOf(
                    SaleItem(
                        productId = "prod_2",
                        name = "Rupchanda Soybean Oil 2L",
                        qty = 2.0,
                        unit = "bottle",
                        buyPrice = 360.0,
                        sellPrice = 395.0,
                        discount = 10.0,
                        lineTotal = 780.0,
                        profit = 60.0
                    )
                ),
                totalItems = 2.0,
                subtotal = 790.0,
                discount = 10.0,
                grandTotal = 780.0,
                profit = 60.0,
                paymentType = "cash",
                customerName = "Cash Sale",
                soldBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L))
            ),
            Sale(
                id = "sale_3",
                storeId = "demo_store",
                invoiceNo = "INV-0103",
                items = listOf(
                    SaleItem(
                        productId = "prod_1",
                        name = "Miniket Rice 5kg",
                        qty = 2.0,
                        unit = "bag",
                        buyPrice = 320.0,
                        sellPrice = 375.0,
                        discount = 0.0,
                        lineTotal = 750.0,
                        profit = 110.0
                    ),
                    SaleItem(
                        productId = "prod_2",
                        name = "Rupchanda Soybean Oil 2L",
                        qty = 1.0,
                        unit = "bottle",
                        buyPrice = 360.0,
                        sellPrice = 395.0,
                        discount = 0.0,
                        lineTotal = 395.0,
                        profit = 35.0
                    )
                ),
                totalItems = 3.0,
                subtotal = 1145.0,
                discount = 0.0,
                grandTotal = 1145.0,
                profit = 145.0,
                paymentType = "baki",
                customerId = "cust_2",
                customerName = "Kabir Hossain",
                customerPhone = "01819887766",
                soldBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 3600000L * 4))
            ),
            Sale(
                id = "sale_4",
                storeId = "demo_store",
                invoiceNo = "INV-0104",
                items = listOf(
                    SaleItem(
                        productId = "prod_5",
                        name = "Pran Frooto Mango 1L",
                        qty = 1.0,
                        unit = "bottle",
                        buyPrice = 90.0,
                        sellPrice = 110.0,
                        discount = 0.0,
                        lineTotal = 110.0,
                        profit = 20.0
                    )
                ),
                totalItems = 1.0,
                subtotal = 110.0,
                discount = 0.0,
                grandTotal = 110.0,
                profit = 20.0,
                paymentType = "cash",
                customerName = "Cash Sale",
                soldBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 1800000L))
            )
        )
    )

    private val _demoPayments = MutableStateFlow<List<Payment>>(
        listOf(
            Payment(
                id = "pay_1",
                storeId = "demo_store",
                paymentNo = "PAY-0001",
                customerId = "cust_1",
                customerName = "Mohammad Rahim",
                customerMobile = "01711223344",
                amount = 500.0,
                paymentMethod = "cash",
                paymentDate = System.currentTimeMillis() - 86400000L,
                note = "Weekly settlement",
                receivedBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L))
            )
        )
    )

    private val demoPurchaseCounter = AtomicLong(103L)
    private val demoReturnCounter = AtomicLong(1L)

    private val _demoSuppliers = MutableStateFlow<List<Supplier>>(
        listOf(
            Supplier(
                id = "sup_1",
                storeId = "demo_store",
                name = "Abul Bashar Traders",
                mobile = "01722334455",
                company = "Bashar Oil & Spices Co.",
                address = "Khatunganj, Chattogram",
                balance = 12500.0,
                createdBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L * 10))
            ),
            Supplier(
                id = "sup_2",
                storeId = "demo_store",
                name = "Meghna Flour & Rice Mills",
                mobile = "01833445566",
                company = "Meghna Group of Industries",
                address = "Tejgaon I/A, Dhaka",
                balance = 8400.0,
                createdBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L * 8))
            ),
            Supplier(
                id = "sup_3",
                storeId = "demo_store",
                name = "Unilever Bangladesh Agency",
                mobile = "01944556677",
                company = "Unilever Distribution Hub",
                address = "Kawran Bazar, Dhaka",
                balance = 0.0,
                createdBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L * 5))
            )
        )
    )

    private val _demoPurchases = MutableStateFlow<List<Purchase>>(
        listOf(
            Purchase(
                id = "pur_1",
                storeId = "demo_store",
                purchaseNo = "PUR-0101",
                supplierId = "sup_1",
                supplierName = "Abul Bashar Traders",
                supplierMobile = "01722334455",
                items = listOf(
                    PurchaseItem(
                        productId = "prod_2",
                        name = "Rupchanda Soybean Oil 2L",
                        qty = 30.0,
                        unit = "bottle",
                        buyPrice = 360.0,
                        lineTotal = 10800.0
                    ),
                    PurchaseItem(
                        productId = "prod_3",
                        name = "Radhuni Turmeric Powder 200g",
                        qty = 60.0,
                        unit = "pack",
                        buyPrice = 70.0,
                        lineTotal = 4200.0
                    )
                ),
                totalItems = 90.0,
                totalAmount = 15000.0,
                paidAmount = 2500.0,
                dueAmount = 12500.0,
                paymentType = "credit",
                note = "Received bulk delivery",
                createdBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L * 3))
            ),
            Purchase(
                id = "pur_2",
                storeId = "demo_store",
                purchaseNo = "PUR-0102",
                supplierId = "sup_2",
                supplierName = "Meghna Flour & Rice Mills",
                supplierMobile = "01833445566",
                items = listOf(
                    PurchaseItem(
                        productId = "prod_1",
                        name = "Miniket Rice 5kg",
                        qty = 45.0,
                        unit = "bag",
                        buyPrice = 320.0,
                        lineTotal = 14400.0
                    )
                ),
                totalItems = 45.0,
                totalAmount = 14400.0,
                paidAmount = 6000.0,
                dueAmount = 8400.0,
                paymentType = "credit",
                note = "Bags batch #B14",
                createdBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L * 2))
            ),
            Purchase(
                id = "pur_3",
                storeId = "demo_store",
                purchaseNo = "PUR-0103",
                supplierId = "sup_3",
                supplierName = "Unilever Bangladesh Agency",
                supplierMobile = "01944556677",
                items = listOf(
                    PurchaseItem(
                        productId = "prod_6",
                        name = "Lifebuoy Soap 100g",
                        qty = 100.0,
                        unit = "pcs",
                        buyPrice = 48.0,
                        lineTotal = 4800.0
                    )
                ),
                totalItems = 100.0,
                totalAmount = 4800.0,
                paidAmount = 4800.0,
                dueAmount = 0.0,
                paymentType = "paid",
                note = "Cash on delivery",
                createdBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L))
            )
        )
    )

    private val _demoPurchaseReturns = MutableStateFlow<List<PurchaseReturn>>(
        listOf(
            PurchaseReturn(
                id = "ret_1",
                storeId = "demo_store",
                returnNo = "RET-0001",
                purchaseId = "pur_1",
                supplierId = "sup_1",
                supplierName = "Abul Bashar Traders",
                amount = 720.0,
                reason = "2 bottles leaking cap returned on delivery",
                createdBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L * 2))
            )
        )
    )

    private val _demoHealthFixLogs = MutableStateFlow<List<HealthFixLog>>(emptyList())

    private val _demoAccounts = MutableStateFlow<List<Account>>(
        listOf(
            Account(
                id = "cash_in_hand",
                storeId = "demo_store",
                name = "Cash in Hand",
                type = "Cash",
                accountNumber = "",
                openingBalance = 0.0,
                isDefault = true,
                isArchived = false,
                createdBy = "system",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L * 15))
            ),
            Account(
                id = "acc_bkash",
                storeId = "demo_store",
                name = "bKash Merchant",
                type = "Wallet",
                accountNumber = "01712-345678",
                openingBalance = 3500.0,
                isDefault = false,
                isArchived = false,
                createdBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L * 10))
            )
        )
    )

    private val _demoAdjustments = MutableStateFlow<List<AccountAdjustment>>(emptyList())

    private val _demoExpenses = MutableStateFlow<List<Expense>>(
        listOf(
            Expense(
                id = "exp_1",
                storeId = "demo_store",
                amount = 1200.0,
                category = "Electricity",
                accountId = "cash_in_hand",
                accountName = "Cash in Hand",
                date = System.currentTimeMillis() - 86400000L * 3,
                note = "DESCO monthly electric bill",
                createdBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L * 3))
            ),
            Expense(
                id = "exp_2",
                storeId = "demo_store",
                amount = 150.0,
                category = "Tea & Snacks",
                accountId = "cash_in_hand",
                accountName = "Cash in Hand",
                date = System.currentTimeMillis() - 86400000L,
                note = "Tea & biscuits for shop visitors",
                createdBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L))
            )
        )
    )

    private val _demoOtherIncomes = MutableStateFlow<List<OtherIncome>>(
        listOf(
            OtherIncome(
                id = "inc_1",
                storeId = "demo_store",
                amount = 450.0,
                category = "Scrap Sale",
                accountId = "cash_in_hand",
                accountName = "Cash in Hand",
                date = System.currentTimeMillis() - 86400000L * 2,
                note = "Sold carton boxes and waste paper",
                createdBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L * 2))
            )
        )
    )

    private val _demoSupplierPayments = MutableStateFlow<List<SupplierPayment>>(
        listOf(
            SupplierPayment(
                id = "sp_1",
                storeId = "demo_store",
                paymentNo = "PAY-OUT-0001",
                supplierId = "sup_2",
                supplierName = "Meghna Flour & Rice Mills",
                amount = 2000.0,
                accountId = "cash_in_hand",
                accountName = "Cash in Hand",
                paymentDate = System.currentTimeMillis() - 86400000L,
                note = "Bank payment installment for flour bags",
                paidBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L))
            )
        )
    )

    private val _demoCategories = MutableStateFlow<List<StoreCategory>>(
        listOf(
            StoreCategory(id = "cat_exp_1", storeId = "demo_store", type = "expense", name = "Rent"),
            StoreCategory(id = "cat_exp_2", storeId = "demo_store", type = "expense", name = "Electricity"),
            StoreCategory(id = "cat_exp_3", storeId = "demo_store", type = "expense", name = "Staff Salary"),
            StoreCategory(id = "cat_exp_4", storeId = "demo_store", type = "expense", name = "Transport"),
            StoreCategory(id = "cat_exp_5", storeId = "demo_store", type = "expense", name = "Packaging"),
            StoreCategory(id = "cat_exp_6", storeId = "demo_store", type = "expense", name = "Tea & Snacks"),
            StoreCategory(id = "cat_exp_7", storeId = "demo_store", type = "expense", name = "Maintenance"),
            StoreCategory(id = "cat_exp_8", storeId = "demo_store", type = "expense", name = "Other"),
            StoreCategory(id = "cat_inc_1", storeId = "demo_store", type = "income", name = "Scrap Sale"),
            StoreCategory(id = "cat_inc_2", storeId = "demo_store", type = "income", name = "Owner Deposit"),
            StoreCategory(id = "cat_inc_3", storeId = "demo_store", type = "income", name = "Bank Interest"),
            StoreCategory(id = "cat_inc_4", storeId = "demo_store", type = "income", name = "Other")
        )
    )

    private val _demoNotes = MutableStateFlow<List<StoreNote>>(
        listOf(
            StoreNote(
                id = "note_1",
                storeId = "demo_store",
                title = "Wholesale Delivery Timings",
                text = "Bashar Oil deliveries arrive on Saturday mornings before 10 AM. Unload at back gate.",
                date = System.currentTimeMillis() - 86400000L * 4,
                createdBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L * 4))
            ),
            StoreNote(
                id = "note_2",
                storeId = "demo_store",
                title = "Customer Pre-orders",
                text = "Dr. Selim requested 5 bags Miniket Rice for clinic staff by 25th of month.",
                date = System.currentTimeMillis() - 86400000L,
                createdBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L))
            )
        )
    )

    private val _demoMembers = MutableStateFlow<List<StoreMember>>(
        listOf(
            StoreMember(
                userId = "demo_user",
                displayName = "Shop Owner",
                email = "owner@store.com",
                role = "owner",
                roleLabel = "Owner",
                permissionLevel = "Full",
                permissions = StorePermissions.defaultOwnerPermissions(),
                status = "Active",
                joinedTime = System.currentTimeMillis() - 86400000L * 30,
                invitedBy = ""
            )
        )
    )

    private val _demoInvites = MutableStateFlow<List<StoreInvite>>(emptyList())

    // -------------------------------------------------------------
    // USER PROFILE & STORE LINKAGE
    // -------------------------------------------------------------

    private val modePrefs by lazy {
        appContext?.getSharedPreferences("user_mode_prefs", Context.MODE_PRIVATE)
    }

    private fun loadPersistedLanguage(): AppLanguage {
        val code = modePrefs?.getString("app_language", AppLanguage.EN.code)
        val lang = if (code == AppLanguage.BN.code) AppLanguage.BN else AppLanguage.EN
        ShopLocale.activeLanguage = lang
        return lang
    }

    private val _currentLanguage = MutableStateFlow(loadPersistedLanguage())
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        ShopLocale.activeLanguage = language
        _currentLanguage.value = language
        modePrefs?.edit()?.putString("app_language", language.code)?.apply()
    }

    fun toggleLanguage() {
        val next = if (_currentLanguage.value == AppLanguage.EN) AppLanguage.BN else AppLanguage.EN
        setLanguage(next)
    }

    private val _localSimpleModes = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    fun getCachedUserSimpleMode(userId: String): Boolean {
        if (userId.isBlank()) return false
        _localSimpleModes.value[userId]?.let { return it }
        return modePrefs?.getBoolean("simple_mode_$userId", false) ?: false
    }

    private fun cacheUserSimpleModeLocally(userId: String, isSimpleMode: Boolean) {
        if (userId.isBlank()) return
        modePrefs?.edit()?.putBoolean("simple_mode_$userId", isSimpleMode)?.apply()
        _localSimpleModes.value = _localSimpleModes.value + (userId to isSimpleMode)
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        val database = db
        val signedInUid = currentUid
        if (database == null || userId.isBlank() || userId == "demo_user" || signedInUid == null || signedInUid != userId) {
            return _localSimpleModes.map { map ->
                val mode = map[userId] ?: getCachedUserSimpleMode(userId)
                UserProfile(
                    id = userId,
                    email = if (userId == "demo_user") "demo@storeos.app" else "",
                    storeId = if (userId == "demo_user") "demo_store" else "",
                    isSimpleMode = mode
                )
            }
        }
        return combine(
            database.collection("users").document(userId).snapshots()
                .map<DocumentSnapshot, DocumentSnapshot?> { it }
                .catch { e ->
                    Log.w("StoreRepository", "User profile snapshot fallback for $userId: ${e.message}")
                    emit(null)
                },
            _localSimpleModes
        ) { snap, localMap ->
            if (snap != null && snap.exists()) {
                val rawProfile = snap.toObject(UserProfile::class.java)
                if (rawProfile != null) {
                    val serverHasField = snap.contains("isSimpleMode") || snap.contains("simpleMode")
                    if (serverHasField) {
                        val serverMode = snap.getBoolean("isSimpleMode")
                            ?: snap.getBoolean("simpleMode")
                            ?: rawProfile.isSimpleMode
                        val profile = rawProfile.copy(isSimpleMode = serverMode)
                        modePrefs?.edit()?.putBoolean("simple_mode_$userId", profile.isSimpleMode)?.apply()
                        profile
                    } else {
                        val fallbackMode = localMap[userId] ?: getCachedUserSimpleMode(userId)
                        rawProfile.copy(isSimpleMode = fallbackMode)
                    }
                } else null
            } else {
                val localMode = localMap[userId] ?: getCachedUserSimpleMode(userId)
                UserProfile(id = userId, isSimpleMode = localMode)
            }
        }
    }

    suspend fun getUserProfileOnce(userId: String): UserProfile? {
        val database = db
        val signedInUid = currentUid
        if (database == null || userId.isBlank() || userId == "demo_user" || signedInUid == null || signedInUid != userId) {
            return UserProfile(
                id = userId,
                email = "demo@storeos.app",
                storeId = "demo_store",
                isSimpleMode = getCachedUserSimpleMode(userId)
            )
        }
        val snap = database.collection("users").document(userId).get().await()
        if (!snap.exists()) return null
        val rawProfile = snap.toObject(UserProfile::class.java) ?: return null
        val serverHasField = snap.contains("isSimpleMode") || snap.contains("simpleMode")
        return if (serverHasField) {
            val serverMode = snap.getBoolean("isSimpleMode")
                ?: snap.getBoolean("simpleMode")
                ?: rawProfile.isSimpleMode
            val profile = rawProfile.copy(isSimpleMode = serverMode)
            cacheUserSimpleModeLocally(userId, profile.isSimpleMode)
            profile
        } else {
            rawProfile.copy(isSimpleMode = getCachedUserSimpleMode(userId))
        }
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        val signedInUid = currentUid
        val uid = signedInUid ?: profile.id.ifBlank { "demo_user" }
        cacheUserSimpleModeLocally(uid, profile.isSimpleMode)
        val database = db ?: return
        if (signedInUid == null || uid == "demo_user") return
        val docRef = database.collection("users").document(uid)
        val payload = mapOf(
            "email" to profile.email,
            "storeId" to profile.storeId,
            "role" to profile.role,
            "isSimpleMode" to profile.isSimpleMode,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload, SetOptions.merge()).await()
    }

    suspend fun updateUserSimpleMode(userId: String, isSimpleMode: Boolean) {
        val signedInUid = currentUid
        val targetUid = userId.ifBlank { signedInUid ?: "demo_user" }
        cacheUserSimpleModeLocally(targetUid, isSimpleMode)
        val database = db ?: return
        if (targetUid == "demo_user" || signedInUid == null || signedInUid != targetUid) {
            return
        }
        val docRef = database.collection("users").document(targetUid)
        val payload = mapOf(
            "isSimpleMode" to isSimpleMode,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val writeTask = docRef.set(payload, SetOptions.merge())
        if (isDeviceOnline()) {
            writeTask.await()
        } else {
            withTimeoutOrNull(1_200L) { writeTask.await() }
        }
    }

    // -------------------------------------------------------------
    // STORE SETUP & MANAGEMENT
    // -------------------------------------------------------------

    fun observeStore(storeId: String): Flow<Store?> {
        if (storeId == "demo_store" || db == null) {
            return _demoStore.asStateFlow()
        }
        return db.collection("stores").document(storeId)
            .snapshots()
            .map { snap ->
                if (snap.exists()) snap.toObject(Store::class.java) else null
            }
    }

    fun configureDemoStore(shopType: ShopType?) {
        if (shopType == null) return
        val categories = ShopTypeCatalog.startingCategories(shopType)
        val baseStore = _demoStore.value ?: Store(
            id = "demo_store",
            phone = "+880 1712-345678",
            address = "Shop 14, New Market, Dhaka",
            ownerId = "demo_user",
            memberUids = listOf("demo_user"),
            members = listOf(StoreMember(userId = "demo_user", role = "owner", status = "active")),
            createdAt = Timestamp.now()
        )
        _demoStore.value = baseStore.copy(
            id = "demo_store",
            name = ShopTypeCatalog.demoStoreName(shopType),
            shopType = shopType.id,
            productCategories = categories,
            updatedAt = Timestamp.now()
        )
        _demoProducts.value = ShopTypeCatalog.demoProducts(shopType)
    }

    suspend fun updateStoreShopType(
        storeId: String,
        shopType: ShopType,
        addSuggestedCategories: Boolean,
        existingCategoriesFallback: List<String> = emptyList()
    ) {
        if (storeId == "demo_store" || db == null) {
            val currentStore = _demoStore.value ?: return
            val baseExisting = (currentStore.productCategories + existingCategoriesFallback)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
            val updatedCategories = if (addSuggestedCategories) {
                ShopTypeCatalog.mergeSuggestedCategories(baseExisting, shopType).take(50)
            } else {
                currentStore.productCategories
            }
            _demoStore.value = currentStore.copy(
                shopType = shopType.id,
                productCategories = updatedCategories,
                updatedAt = Timestamp.now()
            )
            return
        }

        val storeRef = db.collection("stores").document(storeId)
        val storeSnap = storeRef.get().await()
        if (!storeSnap.exists()) {
            throw IllegalStateException("Store not found")
        }
        val currentCategories = (storeSnap.get("productCategories") as? List<*>)
            ?.filterIsInstance<String>()
            .orEmpty()
        val baseExisting = (currentCategories + existingCategoriesFallback)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }

        val updates = buildMap<String, Any> {
            put("shopType", shopType.id)
            if (addSuggestedCategories) {
                put("productCategories", ShopTypeCatalog.mergeSuggestedCategories(baseExisting, shopType).take(50))
            }
            put("updatedAt", FieldValue.serverTimestamp())
        }
        storeRef.update(updates).await()
    }

    suspend fun createStore(
        name: String,
        phone: String,
        address: String,
        shopType: String = "",
        productCategories: List<String> = emptyList()
    ): String {
        val cleanShopType = shopType.trim().take(40)
        val cleanCategories = productCategories
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .take(50)

        val database = db ?: run {
            val newStore = Store(
                id = "demo_store",
                name = name.trim(),
                phone = phone.trim(),
                address = address.trim(),
                shopType = cleanShopType,
                productCategories = cleanCategories,
                ownerId = "demo_user",
                memberUids = listOf("demo_user"),
                members = listOf(StoreMember(userId = "demo_user", role = "owner", status = "active")),
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
            _demoStore.value = newStore
            _demoProducts.value = emptyList()
            return "demo_store"
        }

        val uid = requireUserId()
        val userEmail = auth?.currentUser?.email ?: ""
        val storeRef = database.collection("stores").document()
        val storeId = storeRef.id

        val storePayload = mapOf(
            "name" to name.trim(),
            "phone" to phone.trim(),
            "address" to address.trim(),
            "shopType" to cleanShopType,
            "productCategories" to cleanCategories,
            "ownerId" to uid,
            "memberUids" to listOf(uid),
            "members" to listOf(
                mapOf(
                    "userId" to uid,
                    "role" to "owner",
                    "status" to "active"
                )
            ),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val counterRef = storeRef.collection("meta").document("counters")
        val counterPayload = mapOf("invoiceSeq" to 100L)

        val userRef = database.collection("users").document(uid)
        val existingUserSnap = runCatching { userRef.get().await() }.getOrNull()
        val userPayload = buildMap<String, Any> {
            put("email", userEmail)
            put("storeId", storeId)
            put("role", "owner")
            if (existingUserSnap == null || !existingUserSnap.exists() || !existingUserSnap.contains("createdAt")) {
                put("createdAt", FieldValue.serverTimestamp())
            }
            put("updatedAt", FieldValue.serverTimestamp())
        }

        storeRef.set(storePayload).await()
        userRef.set(userPayload, SetOptions.merge()).await()
        try {
            counterRef.set(counterPayload, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("StoreRepository", "Counter init note: ${e.message}")
        }

        return storeId
    }

    // -------------------------------------------------------------
    // PRODUCTS & INVENTORY
    // -------------------------------------------------------------

    fun observeProducts(storeId: String): Flow<List<Product>> {
        if (storeId == "demo_store" || db == null) {
            return _demoProducts.asStateFlow()
        }
        return db.collection("stores").document(storeId).collection("products")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(Product::class.java) }
                    .sortedBy { it.name.lowercase() }
            }
    }

    private data class PhotoUploadResult(
        val photoUri: String,
        val pendingUpload: Boolean,
        val warning: String? = null
    )

    private fun isDeviceOnline(): Boolean {
        val ctx = appContext ?: return true
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    private suspend fun uploadBytesToFirebaseStorage(
        storeId: String,
        fileName: String,
        compressedBytes: ByteArray
    ): String {
        val storage = Firebase.storage
        storage.maxUploadRetryTimeMillis = 8_000L
        val storageRef = storage.reference
            .child("stores")
            .child(storeId)
            .child("products")
            .child(fileName)
        val metadata = storageMetadata {
            contentType = "image/jpeg"
        }
        return withTimeout(15_000L) {
            storageRef.putBytes(compressedBytes, metadata).await()
            storageRef.downloadUrl.await().toString()
        }
    }

    private suspend fun uploadProductPhotoIfNeeded(
        storeId: String,
        productId: String,
        photoUriString: String
    ): PhotoUploadResult {
        val trimmed = photoUriString.trim()
        if (trimmed.isBlank() || !ProductImageUtil.isLocalPhotoUri(trimmed)) {
            return PhotoUploadResult(photoUri = trimmed, pendingUpload = false)
        }
        val context = appContext
            ?: throw IllegalStateException(ShopLocale.get("err_context_required_photo", _currentLanguage.value))

        // Always resize to max 1000px JPEG and persist locally first so the photo is never lost
        val fileName = "${productId}_${System.currentTimeMillis()}.jpg"
        val (localFileUri, compressedBytes) = withContext(Dispatchers.IO) {
            ProductImageUtil.persistResizedLocalJpeg(
                context = context,
                sourceUri = Uri.parse(trimmed),
                fileName = fileName
            )
        }

        if (storeId == "demo_store" || db == null) {
            return PhotoUploadResult(photoUri = localFileUri, pendingUpload = false)
        }

        if (!isDeviceOnline()) {
            return PhotoUploadResult(
                photoUri = localFileUri,
                pendingUpload = true,
                warning = ShopLocale.get("err_photo_offline", _currentLanguage.value)
            )
        }

        return try {
            val downloadUrl = uploadBytesToFirebaseStorage(storeId, fileName, compressedBytes)
            ProductImageUtil.deleteLocalPhotoIfOwned(context, localFileUri)
            PhotoUploadResult(photoUri = downloadUrl, pendingUpload = false)
        } catch (e: Exception) {
            Log.w("StoreRepository", "Product photo upload failed; keeping local file for retry", e)
            val errReason = e.localizedMessage ?: ShopLocale.get("err_network", _currentLanguage.value)
            PhotoUploadResult(
                photoUri = localFileUri,
                pendingUpload = true,
                warning = ShopLocale.format("err_photo_upload_retry", _currentLanguage.value, errReason)
            )
        }
    }

    /**
     * Retries uploading a locally-stored product photo to Firebase Storage and updates the product's
     * Firestore photoUri once the upload succeeds.
     */
    suspend fun retryProductPhotoUpload(storeId: String, product: Product): Boolean {
        if (storeId == "demo_store" || db == null) return false
        if (product.id.isBlank() || !ProductImageUtil.isLocalPhotoUri(product.photoUri)) return false
        if (!isDeviceOnline()) return false
        val context = appContext ?: return false

        val uri = Uri.parse(product.photoUri.trim())
        val compressedBytes = withContext(Dispatchers.IO) {
            if (!ProductImageUtil.isLocalFilePresent(context, product.photoUri)) {
                return@withContext null
            }
            val file = if (uri.scheme == "file" && uri.path != null) File(uri.path!!) else null
            if (file != null && file.exists() && file.length() > 0L && file.length() <= ProductImageUtil.DEFAULT_MAX_BYTES) {
                file.readBytes()
            } else {
                ProductImageUtil.compressImageForUpload(context, uri)
            }
        } ?: return false

        val fileName = "${product.id}_${System.currentTimeMillis()}.jpg"
        val downloadUrl = uploadBytesToFirebaseStorage(storeId, fileName, compressedBytes)
        val docRef = db.collection("stores").document(storeId).collection("products").document(product.id)
        docRef.update(
            mapOf(
                "photoUri" to downloadUrl,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        ProductImageUtil.deleteLocalPhotoIfOwned(context, product.photoUri)
        return true
    }

    private suspend fun deleteProductPhotoBestEffort(photoUriString: String) {
        val trimmed = photoUriString.trim()
        if (trimmed.startsWith("https://firebasestorage.", ignoreCase = true)) {
            runCatching {
                Firebase.storage.getReferenceFromUrl(trimmed).delete().await()
            }
        } else if (trimmed.startsWith("file://", ignoreCase = true)) {
            appContext?.let { ProductImageUtil.deleteLocalPhotoIfOwned(it, trimmed) }
        }
    }

    suspend fun addProduct(storeId: String, product: Product): ProductSaveOutcome {
        val lang = _currentLanguage.value
        require(product.stock >= 0.0) { ShopLocale.get("err_stock_negative", lang) }
        require(product.buyPrice >= 0.0) { ShopLocale.get("err_cost_negative", lang) }
        require(product.sellPrice >= 0.0) { ShopLocale.get("err_sale_price_negative", lang) }

        val stockUnit = product.unit.trim().ifEmpty { "pcs" }
        val cleanBuyUnit = product.buyUnit.trim().ifEmpty { stockUnit }
        val cleanBuyConv = CalculationEngine.effectiveBuyConversion(
            product.copy(unit = stockUnit, buyUnit = cleanBuyUnit)
        )
        val cleanSellUnits = product.sellUnits.filter { (k, v) ->
            k.isNotBlank() && !k.trim().equals(stockUnit, ignoreCase = true) && v > 0.0
        }.mapKeys { it.key.trim() }

        if (storeId == "demo_store" || db == null) {
            val newId = "prod_${System.currentTimeMillis()}"
            val uploadResult = uploadProductPhotoIfNeeded(storeId, newId, product.photoUri)
            val newProd = product.copy(
                id = newId,
                storeId = storeId,
                unit = stockUnit,
                buyUnit = cleanBuyUnit,
                buyConversion = cleanBuyConv,
                sellUnits = cleanSellUnits,
                stock = CalculationEngine.roundStock(product.stock),
                lowStockLimit = CalculationEngine.roundStock(product.lowStockLimit),
                photoUri = uploadResult.photoUri,
                createdAt = Timestamp.now()
            )
            _demoProducts.value = (_demoProducts.value + newProd).sortedBy { it.name.lowercase() }
            if (product.photoUri != uploadResult.photoUri && product.photoUri.contains("/product_photos/cam_")) {
                appContext?.let { ProductImageUtil.deleteLocalPhotoIfOwned(it, product.photoUri) }
            }
            return ProductSaveOutcome(
                productId = newId,
                photoUri = uploadResult.photoUri,
                photoPendingUpload = uploadResult.pendingUpload,
                uploadWarning = uploadResult.warning
            )
        }

        val uid = requireUserId()
        val prodRef = db.collection("stores").document(storeId).collection("products").document()
        val uploadResult = uploadProductPhotoIfNeeded(storeId, prodRef.id, product.photoUri)
        val payload = mapOf(
            "storeId" to storeId,
            "name" to product.name.trim(),
            "nameBn" to product.nameBn.trim(),
            "barcode" to product.barcode.trim(),
            "category" to product.category.trim().ifEmpty { "General" },
            "unit" to stockUnit,
            "buyUnit" to cleanBuyUnit,
            "buyConversion" to cleanBuyConv,
            "sellUnits" to cleanSellUnits,
            "buyPrice" to product.buyPrice,
            "sellPrice" to product.sellPrice,
            "wholesalePrice" to product.wholesalePrice,
            "mrp" to product.mrp,
            "itemLocation" to product.itemLocation.trim(),
            "partyRate" to product.partyRate,
            "stock" to CalculationEngine.roundStock(product.stock),
            "lowStockLimit" to CalculationEngine.roundStock(product.lowStockLimit),
            "photoUri" to uploadResult.photoUri,
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val writeTask = prodRef.set(payload)
        if (isDeviceOnline()) {
            writeTask.await()
        } else {
            // Firestore caches offline writes immediately; don't block the UI waiting for server ACK.
            withTimeoutOrNull(1_200L) { writeTask.await() }
        }
        if (product.photoUri != uploadResult.photoUri && product.photoUri.contains("/product_photos/cam_")) {
            appContext?.let { ProductImageUtil.deleteLocalPhotoIfOwned(it, product.photoUri) }
        }
        return ProductSaveOutcome(
            productId = prodRef.id,
            photoUri = uploadResult.photoUri,
            photoPendingUpload = uploadResult.pendingUpload,
            uploadWarning = uploadResult.warning
        )
    }

    suspend fun updateProduct(
        storeId: String,
        product: Product,
        previousPhotoUri: String = ""
    ): ProductSaveOutcome {
        val lang = _currentLanguage.value
        require(product.id.isNotBlank()) { ShopLocale.get("err_product_id_blank", lang) }
        require(product.stock >= 0.0) { ShopLocale.get("err_stock_negative", lang) }
        require(product.buyPrice >= 0.0) { ShopLocale.get("err_cost_negative", lang) }
        require(product.sellPrice >= 0.0) { ShopLocale.get("err_sale_price_negative", lang) }

        val stockUnit = product.unit.trim().ifEmpty { "pcs" }
        val cleanBuyUnit = product.buyUnit.trim().ifEmpty { stockUnit }
        val cleanBuyConv = CalculationEngine.effectiveBuyConversion(
            product.copy(unit = stockUnit, buyUnit = cleanBuyUnit)
        )
        val cleanSellUnits = product.sellUnits.filter { (k, v) ->
            k.isNotBlank() && !k.trim().equals(stockUnit, ignoreCase = true) && v > 0.0
        }.mapKeys { it.key.trim() }

        if (storeId == "demo_store" || db == null) {
            val uploadResult = uploadProductPhotoIfNeeded(storeId, product.id, product.photoUri)
            _demoProducts.value = _demoProducts.value.map {
                if (it.id == product.id) {
                    product.copy(
                        unit = stockUnit,
                        buyUnit = cleanBuyUnit,
                        buyConversion = cleanBuyConv,
                        sellUnits = cleanSellUnits,
                        stock = CalculationEngine.roundStock(product.stock),
                        lowStockLimit = CalculationEngine.roundStock(product.lowStockLimit),
                        photoUri = uploadResult.photoUri,
                        updatedAt = Timestamp.now()
                    )
                } else it
            }
            if (product.photoUri != uploadResult.photoUri && product.photoUri.contains("/product_photos/cam_")) {
                appContext?.let { ProductImageUtil.deleteLocalPhotoIfOwned(it, product.photoUri) }
            }
            return ProductSaveOutcome(
                productId = product.id,
                photoUri = uploadResult.photoUri,
                photoPendingUpload = uploadResult.pendingUpload,
                uploadWarning = uploadResult.warning
            )
        }

        requireUserId()
        val uploadResult = uploadProductPhotoIfNeeded(storeId, product.id, product.photoUri)
        val docRef = db.collection("stores").document(storeId).collection("products").document(product.id)
        val payload = mapOf(
            "name" to product.name.trim(),
            "nameBn" to product.nameBn.trim(),
            "barcode" to product.barcode.trim(),
            "category" to product.category.trim().ifEmpty { "General" },
            "unit" to stockUnit,
            "buyUnit" to cleanBuyUnit,
            "buyConversion" to cleanBuyConv,
            "sellUnits" to cleanSellUnits,
            "buyPrice" to product.buyPrice,
            "sellPrice" to product.sellPrice,
            "wholesalePrice" to product.wholesalePrice,
            "mrp" to product.mrp,
            "itemLocation" to product.itemLocation.trim(),
            "partyRate" to product.partyRate,
            "stock" to CalculationEngine.roundStock(product.stock),
            "lowStockLimit" to CalculationEngine.roundStock(product.lowStockLimit),
            "photoUri" to uploadResult.photoUri,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val writeTask = docRef.update(payload)
        if (isDeviceOnline()) {
            writeTask.await()
        } else {
            withTimeoutOrNull(1_200L) { writeTask.await() }
        }
        if (previousPhotoUri.isNotBlank() && previousPhotoUri != uploadResult.photoUri) {
            deleteProductPhotoBestEffort(previousPhotoUri)
        }
        if (product.photoUri != uploadResult.photoUri && product.photoUri.contains("/product_photos/cam_")) {
            appContext?.let { ProductImageUtil.deleteLocalPhotoIfOwned(it, product.photoUri) }
        }
        return ProductSaveOutcome(
            productId = product.id,
            photoUri = uploadResult.photoUri,
            photoPendingUpload = uploadResult.pendingUpload,
            uploadWarning = uploadResult.warning
        )
    }

    suspend fun deleteProduct(storeId: String, productId: String, photoUri: String = "") {
        require(productId.isNotBlank()) { ShopLocale.get("err_product_id_blank", _currentLanguage.value) }

        if (storeId == "demo_store" || db == null) {
            _demoProducts.value = _demoProducts.value.filter { it.id != productId }
            return
        }

        requireUserId()
        db.collection("stores").document(storeId).collection("products").document(productId)
            .delete().await()
        if (photoUri.isNotBlank()) {
            deleteProductPhotoBestEffort(photoUri)
        }
    }

    suspend fun addStock(storeId: String, productId: String, additionalStock: Double) {
        require(additionalStock > 0.0) { ShopLocale.get("err_add_stock_positive", _currentLanguage.value) }
        val cleanDelta = CalculationEngine.roundStock(additionalStock)

        if (storeId == "demo_store" || db == null) {
            _demoProducts.value = _demoProducts.value.map {
                if (it.id == productId) it.copy(stock = CalculationEngine.roundStock(it.stock + cleanDelta)) else it
            }
            return
        }

        requireUserId()
        val docRef = db.collection("stores").document(storeId).collection("products").document(productId)
        docRef.update(
            "stock", FieldValue.increment(cleanDelta),
            "updatedAt", FieldValue.serverTimestamp()
        ).await()
    }

    suspend fun updateProductStock(storeId: String, productId: String, newStock: Double) {
        require(newStock >= 0.0) { ShopLocale.get("err_stock_below_zero", _currentLanguage.value) }
        val cleanStock = CalculationEngine.roundStock(newStock)

        if (storeId == "demo_store" || db == null) {
            _demoProducts.value = _demoProducts.value.map {
                if (it.id == productId) it.copy(stock = cleanStock) else it
            }
            return
        }

        requireUserId()
        val docRef = db.collection("stores").document(storeId).collection("products").document(productId)
        docRef.update(
            "stock", cleanStock,
            "updatedAt", FieldValue.serverTimestamp()
        ).await()
    }

    // -------------------------------------------------------------
    // SALES & CHECKOUT
    // -------------------------------------------------------------

    fun observeSales(storeId: String): Flow<List<Sale>> {
        if (storeId == "demo_store" || db == null) {
            return _demoSales.asStateFlow()
        }
        return db.collection("stores").document(storeId).collection("sales")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(Sale::class.java) }
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }
    }

    suspend fun executeSaleCheckout(
        storeId: String,
        items: List<SaleItem>,
        invoiceDiscount: Double = 0.0,
        paymentType: String = "cash",
        customerId: String = "",
        customerName: String = "Cash Sale",
        customerPhone: String = "",
        notes: String = "",
        accountId: String = "cash_in_hand",
        accountName: String = "Cash in Hand"
    ): String {
        val lang = _currentLanguage.value
        require(items.isNotEmpty()) { ShopLocale.get("cart_empty_error", lang) }

        val stockRequestedByProduct = items
            .groupBy { it.productId }
            .mapValues { (_, list) -> CalculationEngine.roundStock(list.sumOf { CalculationEngine.saleItemStockQty(it) }) }

        if (storeId == "demo_store" || db == null) {
            val currentProds = _demoProducts.value.toMutableList()
            for ((prodId, requestedStock) in stockRequestedByProduct) {
                val idx = currentProds.indexOfFirst { it.id == prodId }
                if (idx != -1) {
                    val p = currentProds[idx]
                    if (!CalculationEngine.isStockSufficient(p.stock, requestedStock)) {
                        val unitDisp = ShopLocale.displayUnit(p.unit, lang)
                        throw IllegalStateException(
                            ShopLocale.format("err_concurrent_stock", lang, p.stock, unitDisp, p.name)
                        )
                    }
                    val remaining = CalculationEngine.validateStockDeduction(p.stock, requestedStock)
                    currentProds[idx] = p.copy(stock = remaining)
                }
            }
            _demoProducts.value = currentProds

            val seq = demoInvoiceCounter.incrementAndGet()
            val formattedInvoiceNo = CalculationEngine.formatInvoiceNumber(seq)

            val totalItems = CalculationEngine.roundStock(items.sumOf { CalculationEngine.saleItemStockQty(it) })
            val subtotal = CalculationEngine.roundMoney(items.sumOf { CalculationEngine.calculateLineGrossTotal(it.sellPrice, it.qty) })
            val itemDiscounts = CalculationEngine.roundMoney(items.sumOf { CalculationEngine.calculateBoundedDiscount(it.sellPrice, it.qty, it.discount) })
            val totalDiscount = (itemDiscounts + invoiceDiscount).coerceIn(0.0, subtotal)
            val grandTotal = CalculationEngine.roundMoney((subtotal - totalDiscount).coerceAtLeast(0.0))
            val totalProfit = CalculationEngine.roundMoney(items.sumOf { CalculationEngine.calculateLineProfit(it.sellPrice, it.buyPrice, it.qty, it.discount) } - invoiceDiscount)

            // If Baki, adjust customer balance
            if (paymentType == "baki" && customerId.isNotBlank()) {
                _demoCustomers.value = _demoCustomers.value.map { cust ->
                    if (cust.id == customerId) cust.copy(currentBalance = cust.currentBalance + grandTotal) else cust
                }
            }

            val normalizedItems = items.map {
                val stockQ = CalculationEngine.saleItemStockQty(it)
                val boundD = CalculationEngine.calculateBoundedDiscount(it.sellPrice, it.qty, it.discount)
                val netT = CalculationEngine.calculateLineNetTotal(it.sellPrice, it.qty, boundD)
                val prof = CalculationEngine.calculateLineProfit(it.sellPrice, it.buyPrice, it.qty, boundD)
                it.copy(stockQty = stockQ, discount = boundD, lineTotal = netT, profit = prof)
            }

            val newSale = Sale(
                id = "sale_${System.currentTimeMillis()}",
                storeId = storeId,
                invoiceNo = formattedInvoiceNo,
                items = normalizedItems,
                totalItems = totalItems,
                subtotal = subtotal,
                discount = totalDiscount,
                grandTotal = grandTotal,
                profit = totalProfit,
                paymentType = paymentType,
                customerId = customerId.trim(),
                customerName = customerName.trim().ifEmpty { "Cash Sale" },
                customerPhone = customerPhone.trim(),
                accountId = if (paymentType == "baki") "" else accountId,
                accountName = if (paymentType == "baki") "" else accountName,
                notes = notes.trim(),
                soldBy = "demo_user",
                createdAt = Timestamp.now()
            )

            _demoSales.value = listOf(newSale) + _demoSales.value
            return formattedInvoiceNo
        }

        val database = db ?: throw IllegalStateException(ShopLocale.get("err_firestore_unavailable", lang))
        val uid = requireUserId()
        val storeRef = database.collection("stores").document(storeId)
        val counterRef = storeRef.collection("meta").document("counters")
        val saleRef = storeRef.collection("sales").document()
        val customerRef = if (customerId.isNotBlank()) storeRef.collection("customers").document(customerId) else null

        return database.runTransaction<String> { transaction ->
            val counterSnap = transaction.get(counterRef)
            val currentSeq = (counterSnap.getLong("invoiceSeq") ?: 100L) + 1L

            val customerSnap = customerRef?.let { transaction.get(it) }
            val currentCustomerBalance = customerSnap?.getDouble("currentBalance") ?: 0.0

            val uniqueProductIds = stockRequestedByProduct.keys.filter { it.isNotBlank() }
            val prodSnapMap = mutableMapOf<String, Pair<com.google.firebase.firestore.DocumentReference, DocumentSnapshot>>()
            for (prodId in uniqueProductIds) {
                val prodRef = storeRef.collection("products").document(prodId)
                prodSnapMap[prodId] = prodRef to transaction.get(prodRef)
            }

            val deductions = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Double>>()
            for ((prodId, requestedStock) in stockRequestedByProduct) {
                val pair = prodSnapMap[prodId] ?: continue
                val (prodRef, snap) = pair
                val liveStock = snap.getDouble("stock") ?: (snap.getLong("stock")?.toDouble() ?: 0.0)
                val prodName = snap.getString("name") ?: items.firstOrNull { it.productId == prodId }?.name ?: ""
                val stockUnit = snap.getString("unit") ?: items.firstOrNull { it.productId == prodId }?.unit ?: "pcs"
                if (!CalculationEngine.isStockSufficient(liveStock, requestedStock)) {
                    val unitDisp = ShopLocale.displayUnit(stockUnit, lang)
                    throw IllegalStateException(ShopLocale.format("err_concurrent_stock", lang, liveStock, unitDisp, prodName))
                }
                val remainingStock = CalculationEngine.validateStockDeduction(liveStock, requestedStock)
                deductions.add(prodRef to remainingStock)
            }

            val formattedInvoiceNo = CalculationEngine.formatInvoiceNumber(currentSeq)
            var totalItems = 0.0
            var subtotal = 0.0
            var itemDiscounts = 0.0
            var totalProfit = 0.0
            val itemMaps = mutableListOf<Map<String, Any>>()

            for (item in items) {
                val stockQ = CalculationEngine.saleItemStockQty(item)
                totalItems += stockQ
                val gross = CalculationEngine.calculateLineGrossTotal(item.sellPrice, item.qty)
                val boundDiscount = CalculationEngine.calculateBoundedDiscount(item.sellPrice, item.qty, item.discount)
                val netTotal = CalculationEngine.calculateLineNetTotal(item.sellPrice, item.qty, boundDiscount)
                val lineProfit = CalculationEngine.calculateLineProfit(item.sellPrice, item.buyPrice, item.qty, boundDiscount)

                subtotal += gross
                itemDiscounts += boundDiscount
                totalProfit += lineProfit

                itemMaps.add(
                    mapOf(
                        "productId" to item.productId,
                        "name" to item.name,
                        "qty" to item.qty,
                        "unit" to item.unit,
                        "stockQty" to stockQ,
                        "buyPrice" to item.buyPrice,
                        "sellPrice" to item.sellPrice,
                        "discount" to boundDiscount,
                        "lineTotal" to netTotal,
                        "profit" to lineProfit
                    )
                )
            }

            val totalDiscount = (itemDiscounts + invoiceDiscount).coerceIn(0.0, subtotal)
            val grandTotal = CalculationEngine.roundMoney((subtotal - totalDiscount).coerceAtLeast(0.0))
            val netProfit = CalculationEngine.roundMoney(totalProfit - invoiceDiscount)

            transaction.set(counterRef, mapOf("invoiceSeq" to currentSeq), SetOptions.merge())

            for ((prodRef, remainingStock) in deductions) {
                transaction.update(prodRef, "stock", remainingStock, "updatedAt", FieldValue.serverTimestamp())
            }

            if (paymentType == "baki" && customerRef != null && customerSnap != null && customerSnap.exists()) {
                val newCustomerBalance = currentCustomerBalance + grandTotal
                transaction.update(
                    customerRef,
                    "currentBalance", newCustomerBalance,
                    "updatedAt", FieldValue.serverTimestamp()
                )
            }

            val salePayload = mapOf(
                "storeId" to storeId,
                "invoiceNo" to formattedInvoiceNo,
                "items" to itemMaps,
                "totalItems" to CalculationEngine.roundStock(totalItems),
                "subtotal" to CalculationEngine.roundMoney(subtotal),
                "discount" to totalDiscount,
                "grandTotal" to grandTotal,
                "profit" to netProfit,
                "paymentType" to paymentType,
                "customerId" to customerId.trim(),
                "customerName" to customerName.trim().ifEmpty { "Cash Sale" },
                "customerPhone" to customerPhone.trim(),
                "accountId" to (if (paymentType == "baki") "" else accountId),
                "accountName" to (if (paymentType == "baki") "" else accountName),
                "notes" to notes.trim(),
                "soldBy" to uid,
                "createdAt" to FieldValue.serverTimestamp()
            )
            transaction.set(saleRef, salePayload)

            formattedInvoiceNo
        }.await()
    }

    // -------------------------------------------------------------
    // CUSTOMERS MANAGEMENT
    // -------------------------------------------------------------

    fun observeCustomers(storeId: String): Flow<List<Customer>> {
        if (storeId == "demo_store" || db == null) {
            return _demoCustomers.asStateFlow()
        }
        return db.collection("stores").document(storeId).collection("customers")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(Customer::class.java) }
                    .sortedBy { it.name.lowercase() }
            }
    }

    suspend fun addCustomer(storeId: String, customer: Customer): String {
        require(customer.name.isNotBlank()) { ShopLocale.get("customer_name_required", _currentLanguage.value) }

        if (storeId == "demo_store" || db == null) {
            val newId = "cust_${System.currentTimeMillis()}"
            val newCust = customer.copy(id = newId, storeId = storeId, createdAt = Timestamp.now())
            _demoCustomers.value = (_demoCustomers.value + newCust).sortedBy { it.name.lowercase() }
            return newId
        }

        val uid = requireUserId()
        val docRef = db.collection("stores").document(storeId).collection("customers").document()
        val payload = mapOf(
            "storeId" to storeId,
            "name" to customer.name.trim(),
            "mobile" to customer.mobile.trim(),
            "address" to customer.address.trim(),
            "notes" to customer.notes.trim(),
            "currentBalance" to customer.currentBalance.coerceAtLeast(0.0),
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
        return docRef.id
    }

    suspend fun updateCustomer(storeId: String, customer: Customer) {
        val lang = _currentLanguage.value
        require(customer.id.isNotBlank()) { ShopLocale.get("err_customer_id_blank", lang) }
        require(customer.name.isNotBlank()) { ShopLocale.get("customer_name_required", lang) }

        if (storeId == "demo_store" || db == null) {
            _demoCustomers.value = _demoCustomers.value.map {
                if (it.id == customer.id) customer.copy(updatedAt = Timestamp.now()) else it
            }
            return
        }

        requireUserId()
        val docRef = db.collection("stores").document(storeId).collection("customers").document(customer.id)
        val payload = mapOf(
            "name" to customer.name.trim(),
            "mobile" to customer.mobile.trim(),
            "address" to customer.address.trim(),
            "notes" to customer.notes.trim(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.update(payload).await()
    }

    suspend fun deleteCustomer(storeId: String, customerId: String) {
        require(customerId.isNotBlank()) { ShopLocale.get("err_customer_id_blank", _currentLanguage.value) }

        if (storeId == "demo_store" || db == null) {
            _demoCustomers.value = _demoCustomers.value.filter { it.id != customerId }
            return
        }

        requireUserId()
        db.collection("stores").document(storeId).collection("customers").document(customerId)
            .delete().await()
    }

    // -------------------------------------------------------------
    // BAKI PAYMENTS
    // -------------------------------------------------------------

    fun observePayments(storeId: String): Flow<List<Payment>> {
        if (storeId == "demo_store" || db == null) {
            return _demoPayments.asStateFlow()
        }
        return db.collection("stores").document(storeId).collection("payments")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(Payment::class.java) }
                    .sortedByDescending { if (it.paymentDate > 0L) it.paymentDate else (it.createdAt?.toDate()?.time ?: 0L) }
            }
    }

    suspend fun recordCustomerPaymentAtomic(
        storeId: String,
        customerId: String,
        customerName: String,
        customerMobile: String,
        amount: Double,
        note: String,
        paymentDate: Long,
        accountId: String = "cash_in_hand",
        accountName: String = "Cash in Hand"
    ): String {
        val lang = _currentLanguage.value
        require(amount > 0.0) { ShopLocale.get("payment_positive_error", lang) }

        if (storeId == "demo_store" || db == null) {
            val seq = demoPaymentCounter.incrementAndGet()
            val paymentNo = CalculationEngine.formatPaymentNumber(seq)

            _demoCustomers.value = _demoCustomers.value.map { cust ->
                if (cust.id == customerId) {
                    cust.copy(currentBalance = (cust.currentBalance - amount).coerceAtLeast(0.0))
                } else cust
            }

            val newPay = Payment(
                id = "pay_${System.currentTimeMillis()}",
                storeId = storeId,
                paymentNo = paymentNo,
                customerId = customerId.trim(),
                customerName = customerName.trim(),
                customerMobile = customerMobile.trim(),
                amount = amount,
                paymentMethod = "cash",
                accountId = accountId,
                accountName = accountName,
                paymentDate = if (paymentDate > 0L) paymentDate else System.currentTimeMillis(),
                note = note.trim(),
                receivedBy = "demo_user",
                createdAt = Timestamp.now()
            )

            _demoPayments.value = listOf(newPay) + _demoPayments.value
            return paymentNo
        }

        val database = db ?: throw IllegalStateException(ShopLocale.get("err_firestore_unavailable", lang))
        val uid = requireUserId()
        val storeRef = database.collection("stores").document(storeId)
        val counterRef = storeRef.collection("meta").document("counters")
        val paymentRef = storeRef.collection("payments").document()
        val customerRef = if (customerId.isNotBlank() && !customerId.startsWith("legacy_")) {
            storeRef.collection("customers").document(customerId)
        } else null

        return database.runTransaction<String> { transaction ->
            val counterSnap = transaction.get(counterRef)
            val currentSeq = (counterSnap.getLong("paymentSeq") ?: 0L) + 1L

            val customerSnap = customerRef?.let { transaction.get(it) }
            val liveBalance = customerSnap?.getDouble("currentBalance") ?: Double.MAX_VALUE

            if (customerSnap != null && customerSnap.exists() && amount > (liveBalance + 0.001)) {
                throw IllegalStateException(
                    ShopLocale.format(
                        "err_payment_exceeds_live_bal",
                        lang,
                        ShopLocale.formatMoney(amount, lang),
                        ShopLocale.formatMoney(liveBalance, lang)
                    )
                )
            }

            val newBalance = if (customerSnap != null && customerSnap.exists()) {
                (liveBalance - amount).coerceAtLeast(0.0)
            } else 0.0

            val paymentNo = CalculationEngine.formatPaymentNumber(currentSeq)

            transaction.set(counterRef, mapOf("paymentSeq" to currentSeq), SetOptions.merge())

            if (customerRef != null && customerSnap != null && customerSnap.exists()) {
                transaction.update(
                    customerRef,
                    "currentBalance", newBalance,
                    "updatedAt", FieldValue.serverTimestamp()
                )
            }

            val paymentPayload = mapOf(
                "storeId" to storeId,
                "paymentNo" to paymentNo,
                "customerId" to customerId.trim(),
                "customerName" to customerName.trim(),
                "customerMobile" to customerMobile.trim(),
                "amount" to amount,
                "paymentMethod" to "cash",
                "accountId" to accountId,
                "accountName" to accountName,
                "paymentDate" to if (paymentDate > 0L) paymentDate else System.currentTimeMillis(),
                "note" to note.trim(),
                "receivedBy" to uid,
                "createdAt" to FieldValue.serverTimestamp()
            )
            transaction.set(paymentRef, paymentPayload)

            paymentNo
        }.await()
    }

    // -------------------------------------------------------------
    // SUPPLIERS MANAGEMENT
    // -------------------------------------------------------------

    fun observeSuppliers(storeId: String): Flow<List<Supplier>> {
        if (storeId == "demo_store" || db == null) {
            return _demoSuppliers.asStateFlow()
        }
        return db.collection("stores").document(storeId).collection("suppliers")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(Supplier::class.java) }
                    .sortedBy { it.name.lowercase() }
            }
    }

    suspend fun addSupplier(storeId: String, supplier: Supplier): String {
        require(supplier.name.isNotBlank()) { ShopLocale.get("supplier_name_required", _currentLanguage.value) }

        if (storeId == "demo_store" || db == null) {
            val newId = "sup_${System.currentTimeMillis()}"
            val newSup = supplier.copy(id = newId, storeId = storeId, createdAt = Timestamp.now())
            _demoSuppliers.value = (_demoSuppliers.value + newSup).sortedBy { it.name.lowercase() }
            return newId
        }

        val uid = requireUserId()
        val docRef = db.collection("stores").document(storeId).collection("suppliers").document()
        val payload = mapOf(
            "storeId" to storeId,
            "name" to supplier.name.trim(),
            "mobile" to supplier.mobile.trim(),
            "company" to supplier.company.trim(),
            "address" to supplier.address.trim(),
            "balance" to supplier.balance.coerceAtLeast(0.0),
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
        return docRef.id
    }

    suspend fun updateSupplier(storeId: String, supplier: Supplier) {
        val lang = _currentLanguage.value
        require(supplier.id.isNotBlank()) { ShopLocale.get("err_supplier_id_blank", lang) }
        require(supplier.name.isNotBlank()) { ShopLocale.get("supplier_name_required", lang) }

        if (storeId == "demo_store" || db == null) {
            _demoSuppliers.value = _demoSuppliers.value.map {
                if (it.id == supplier.id) supplier.copy(updatedAt = Timestamp.now()) else it
            }
            return
        }

        requireUserId()
        val docRef = db.collection("stores").document(storeId).collection("suppliers").document(supplier.id)
        val payload = mapOf(
            "name" to supplier.name.trim(),
            "mobile" to supplier.mobile.trim(),
            "company" to supplier.company.trim(),
            "address" to supplier.address.trim(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.update(payload).await()
    }

    suspend fun deleteSupplier(storeId: String, supplierId: String) {
        require(supplierId.isNotBlank()) { ShopLocale.get("err_supplier_id_blank", _currentLanguage.value) }

        if (storeId == "demo_store" || db == null) {
            _demoSuppliers.value = _demoSuppliers.value.filter { it.id != supplierId }
            return
        }

        requireUserId()
        db.collection("stores").document(storeId).collection("suppliers").document(supplierId)
            .delete().await()
    }

    // -------------------------------------------------------------
    // PURCHASES & RETURNS
    // -------------------------------------------------------------

    fun observePurchases(storeId: String): Flow<List<Purchase>> {
        if (storeId == "demo_store" || db == null) {
            return _demoPurchases.asStateFlow()
        }
        return db.collection("stores").document(storeId).collection("purchases")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(Purchase::class.java) }
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }
    }

    suspend fun recordPurchase(storeId: String, purchase: Purchase): String {
        require(purchase.items.isNotEmpty()) { ShopLocale.get("purchase_min_one_item", _currentLanguage.value) }

        if (storeId == "demo_store" || db == null) {
            val seq = demoPurchaseCounter.incrementAndGet()
            val purNo = String.format(java.util.Locale.US, "PUR-%04d", seq)
            val newPur = purchase.copy(
                id = "pur_${System.currentTimeMillis()}",
                purchaseNo = purNo,
                storeId = storeId,
                createdAt = Timestamp.now()
            )

            // Adjust supplier balance if credit
            if (purchase.dueAmount > 0.0 && purchase.supplierId.isNotBlank()) {
                _demoSuppliers.value = _demoSuppliers.value.map { sup ->
                    if (sup.id == purchase.supplierId) sup.copy(balance = sup.balance + purchase.dueAmount) else sup
                }
            }

            // Adjust stock in stock unit & update product buying price per stock unit
            val curProds = _demoProducts.value.toMutableList()
            for (item in purchase.items) {
                val idx = curProds.indexOfFirst { it.id == item.productId }
                if (idx != -1) {
                    val p = curProds[idx]
                    val stockToAdd = CalculationEngine.purchaseItemStockQty(item)
                    val updatedBuyPrice = if (item.costPerStockUnit > 0.0) {
                        item.costPerStockUnit
                    } else if (item.conversionFactor > 0.0) {
                        CalculationEngine.costPerStockUnit(item.buyPrice, item.conversionFactor)
                    } else {
                        item.buyPrice
                    }
                    curProds[idx] = p.copy(
                        stock = CalculationEngine.roundStock(p.stock + stockToAdd),
                        buyPrice = updatedBuyPrice
                    )
                }
            }
            _demoProducts.value = curProds
            _demoPurchases.value = listOf(newPur) + _demoPurchases.value
            return purNo
        }

        val uid = requireUserId()
        val docRef = db.collection("stores").document(storeId).collection("purchases").document()
        val payload = mapOf(
            "storeId" to storeId,
            "purchaseNo" to purchase.purchaseNo.ifBlank { "PUR-${System.currentTimeMillis() % 10000}" },
            "supplierId" to purchase.supplierId,
            "supplierName" to purchase.supplierName,
            "supplierMobile" to purchase.supplierMobile,
            "items" to purchase.items.map {
                val stockQ = CalculationEngine.purchaseItemStockQty(it)
                val conv = if (it.conversionFactor > 0.0) it.conversionFactor else 1.0
                val unitCost = if (it.costPerStockUnit > 0.0) it.costPerStockUnit else CalculationEngine.costPerStockUnit(it.buyPrice, conv)
                mapOf(
                    "productId" to it.productId,
                    "name" to it.name,
                    "qty" to it.qty,
                    "unit" to it.unit,
                    "stockQty" to stockQ,
                    "conversionFactor" to conv,
                    "costPerStockUnit" to unitCost,
                    "buyPrice" to it.buyPrice,
                    "lineTotal" to it.lineTotal
                )
            },
            "totalItems" to CalculationEngine.roundStock(purchase.totalItems),
            "totalAmount" to purchase.totalAmount,
            "paidAmount" to purchase.paidAmount,
            "dueAmount" to purchase.dueAmount,
            "paymentType" to purchase.paymentType,
            "accountId" to purchase.accountId.ifBlank { "cash_in_hand" },
            "accountName" to purchase.accountName.ifBlank { "Cash in Hand" },
            "note" to purchase.note,
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()

        if (purchase.dueAmount > 0.0 && purchase.supplierId.isNotBlank()) {
            db.collection("stores").document(storeId).collection("suppliers").document(purchase.supplierId)
                .update("balance", FieldValue.increment(purchase.dueAmount))
        }

        for (item in purchase.items) {
            val stockToAdd = CalculationEngine.purchaseItemStockQty(item)
            val conv = if (item.conversionFactor > 0.0) item.conversionFactor else 1.0
            val updatedBuyPrice = if (item.costPerStockUnit > 0.0) item.costPerStockUnit else CalculationEngine.costPerStockUnit(item.buyPrice, conv)
            db.collection("stores").document(storeId).collection("products").document(item.productId)
                .update(
                    mapOf(
                        "stock" to FieldValue.increment(stockToAdd),
                        "buyPrice" to updatedBuyPrice,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
        }

        return docRef.id
    }

    fun observePurchaseReturns(storeId: String): Flow<List<PurchaseReturn>> {
        if (storeId == "demo_store" || db == null) {
            return _demoPurchaseReturns.asStateFlow()
        }
        return db.collection("stores").document(storeId).collection("purchaseReturns")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(PurchaseReturn::class.java) }
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }
    }

    suspend fun recordPurchaseReturn(storeId: String, returnItem: PurchaseReturn): String {
        require(returnItem.amount > 0.0) { ShopLocale.get("err_return_amount_positive", _currentLanguage.value) }

        if (storeId == "demo_store" || db == null) {
            val seq = demoReturnCounter.incrementAndGet()
            val retNo = String.format(java.util.Locale.US, "RET-%04d", seq)
            val newRet = returnItem.copy(
                id = "ret_${System.currentTimeMillis()}",
                returnNo = retNo,
                storeId = storeId,
                createdAt = Timestamp.now()
            )
            if (returnItem.supplierId.isNotBlank()) {
                _demoSuppliers.value = _demoSuppliers.value.map { sup ->
                    if (sup.id == returnItem.supplierId) sup.copy(balance = (sup.balance - returnItem.amount).coerceAtLeast(0.0)) else sup
                }
            }
            _demoPurchaseReturns.value = listOf(newRet) + _demoPurchaseReturns.value
            return retNo
        }

        val uid = requireUserId()
        val docRef = db.collection("stores").document(storeId).collection("purchaseReturns").document()
        val payload = mapOf(
            "storeId" to storeId,
            "returnNo" to returnItem.returnNo.ifBlank { "RET-${System.currentTimeMillis() % 10000}" },
            "purchaseId" to returnItem.purchaseId,
            "supplierId" to returnItem.supplierId,
            "supplierName" to returnItem.supplierName,
            "amount" to returnItem.amount,
            "reason" to returnItem.reason,
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()

        if (returnItem.supplierId.isNotBlank()) {
            db.collection("stores").document(storeId).collection("suppliers").document(returnItem.supplierId)
                .update("balance", FieldValue.increment(-returnItem.amount))
        }

        return docRef.id
    }

    // -------------------------------------------------------------
    // HEALTH FIX LOGS & AUDITED FIXES
    // -------------------------------------------------------------

    fun observeHealthFixLogs(storeId: String): Flow<List<HealthFixLog>> {
        if (storeId == "demo_store" || db == null) {
            return _demoHealthFixLogs.asStateFlow()
        }
        return db.collection("stores").document(storeId).collection("healthFixLogs")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(HealthFixLog::class.java) }
                    .sortedByDescending { it.fixedAt?.toDate()?.time ?: 0L }
            }
    }

    suspend fun applyHealthFix(storeId: String, problem: HealthProblem, ownerUid: String): Boolean {
        val fixLog = HealthFixLog(
            id = "fix_${System.currentTimeMillis()}",
            storeId = storeId,
            problemType = problem.type.name,
            description = problem.description,
            entityId = problem.entityId,
            entityCollection = problem.entityCollection,
            fixDetails = problem.fixDescription,
            fixedBy = ownerUid,
            fixedAt = Timestamp.now()
        )

        if (storeId == "demo_store" || db == null) {
            when (problem.entityCollection) {
                "products" -> {
                    _demoProducts.value = _demoProducts.value.map { p ->
                        if (p.id == problem.entityId) {
                            var updated = p
                            problem.fixActionPayload.forEach { (k, v) ->
                                when (k) {
                                    "stock" -> (v as? Number)?.toDouble()?.let { updated = updated.copy(stock = it) }
                                    "sellPrice" -> (v as? Number)?.toDouble()?.let { updated = updated.copy(sellPrice = it) }
                                    "barcode" -> (v as? String)?.let { updated = updated.copy(barcode = it) }
                                    "name" -> (v as? String)?.let { updated = updated.copy(name = it) }
                                    "category" -> (v as? String)?.let { updated = updated.copy(category = it) }
                                }
                            }
                            updated
                        } else p
                    }
                }
                "customers" -> {
                    _demoCustomers.value = _demoCustomers.value.map { c ->
                        if (c.id == problem.entityId) {
                            var updated = c
                            problem.fixActionPayload.forEach { (k, v) ->
                                when (k) {
                                    "currentBalance" -> (v as? Number)?.toDouble()?.let { updated = updated.copy(currentBalance = it) }
                                    "mobile" -> (v as? String)?.let { updated = updated.copy(mobile = it) }
                                }
                            }
                            updated
                        } else c
                    }
                }
                "suppliers" -> {
                    _demoSuppliers.value = _demoSuppliers.value.map { s ->
                        if (s.id == problem.entityId) {
                            var updated = s
                            problem.fixActionPayload.forEach { (k, v) ->
                                when (k) {
                                    "balance" -> (v as? Number)?.toDouble()?.let { updated = updated.copy(balance = it) }
                                    "mobile" -> (v as? String)?.let { updated = updated.copy(mobile = it) }
                                }
                            }
                            updated
                        } else s
                    }
                }
                "sales" -> {
                    _demoSales.value = _demoSales.value.map { s ->
                        if (s.id == problem.entityId) {
                            var updated = s
                            problem.fixActionPayload.forEach { (k, v) ->
                                when (k) {
                                    "customerId" -> (v as? String)?.let { updated = updated.copy(customerId = it) }
                                    "customerName" -> (v as? String)?.let { updated = updated.copy(customerName = it) }
                                }
                            }
                            updated
                        } else s
                    }
                }
                "purchases" -> {
                    _demoPurchases.value = _demoPurchases.value.map { pu ->
                        if (pu.id == problem.entityId) {
                            var updated = pu
                            problem.fixActionPayload.forEach { (k, v) ->
                                when (k) {
                                    "supplierId" -> (v as? String)?.let { updated = updated.copy(supplierId = it) }
                                }
                            }
                            updated
                        } else pu
                    }
                }
            }
            _demoHealthFixLogs.value = listOf(fixLog) + _demoHealthFixLogs.value
            return true
        }

        if (problem.entityCollection.isNotBlank() && problem.entityId.isNotBlank() && problem.fixActionPayload.isNotEmpty()) {
            val docRef = db.collection("stores").document(storeId)
                .collection(problem.entityCollection).document(problem.entityId)
            docRef.update(problem.fixActionPayload).await()
        }

        val logRef = db.collection("stores").document(storeId).collection("healthFixLogs").document()
        val logPayload = mapOf(
            "storeId" to storeId,
            "problemType" to fixLog.problemType,
            "description" to fixLog.description,
            "entityId" to fixLog.entityId,
            "entityCollection" to fixLog.entityCollection,
            "fixDetails" to fixLog.fixDetails,
            "fixedBy" to ownerUid,
            "fixedAt" to FieldValue.serverTimestamp()
        )
        logRef.set(logPayload).await()
        return true
    }

    // -------------------------------------------------------------
    // ACCOUNTS & CASH / BANK
    // -------------------------------------------------------------

    fun observeAccounts(storeId: String): Flow<List<Account>> {
        if (storeId == "demo_store" || db == null) {
            return _demoAccounts.asStateFlow()
        }
        return db.collection("stores").document(storeId)
            .collection("accounts")
            .snapshots()
            .map { snap ->
                val list = snap.documents.mapNotNull { it.toObject(Account::class.java) }
                if (list.none { it.isDefault || it.id == "cash_in_hand" }) {
                    val defaultAccount = Account(
                        id = "cash_in_hand",
                        storeId = storeId,
                        name = "Cash in Hand",
                        type = "Cash",
                        accountNumber = "",
                        openingBalance = 0.0,
                        isDefault = true,
                        isArchived = false
                    )
                    listOf(defaultAccount) + list
                } else {
                    list
                }
            }
    }

    suspend fun createAccount(account: Account): String {
        val lang = _currentLanguage.value
        val nameTrimmed = account.name.trim()
        if (nameTrimmed.isBlank()) {
            throw IllegalArgumentException(ShopLocale.get("account_name_required", lang))
        }

        if (account.storeId == "demo_store" || db == null) {
            if (_demoAccounts.value.any { it.name.trim().equals(nameTrimmed, ignoreCase = true) }) {
                throw IllegalArgumentException(ShopLocale.get("account_already_exists", lang))
            }
            val newId = "acc_${System.currentTimeMillis()}"
            val newAcc = account.copy(
                id = newId,
                name = nameTrimmed,
                isDefault = false,
                isArchived = false,
                createdBy = "demo_user",
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
            _demoAccounts.value = _demoAccounts.value + newAcc
            return newId
        }

        val colRef = db.collection("stores").document(account.storeId).collection("accounts")
        val existing = colRef.get().await()
        if (existing.documents.any { it.getString("name")?.trim().equals(nameTrimmed, ignoreCase = true) }) {
            throw IllegalArgumentException(ShopLocale.get("account_already_exists", lang))
        }

        val uid = requireUserId()
        val docRef = colRef.document()
        val payload = mapOf(
            "storeId" to account.storeId,
            "name" to nameTrimmed,
            "type" to account.type,
            "accountNumber" to account.accountNumber.trim(),
            "openingBalance" to account.openingBalance,
            "isDefault" to false,
            "isArchived" to false,
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
        return docRef.id
    }

    suspend fun archiveAccount(storeId: String, accountId: String, currentBalance: Double) {
        val lang = _currentLanguage.value
        if (accountId == "cash_in_hand") {
            throw IllegalArgumentException(ShopLocale.get("cannot_archive_default_account", lang))
        }
        if (kotlin.math.abs(currentBalance) > 0.001) {
            throw IllegalStateException(
                ShopLocale.format("archive_zero_balance_only", lang, ShopLocale.formatMoneyFixed2(currentBalance, lang))
            )
        }

        if (storeId == "demo_store" || db == null) {
            _demoAccounts.value = _demoAccounts.value.map { acc ->
                if (acc.id == accountId) {
                    if (acc.isDefault) throw IllegalArgumentException(ShopLocale.get("cannot_archive_default_account", lang))
                    acc.copy(isArchived = true)
                } else acc
            }
            return
        }

        val docRef = db.collection("stores").document(storeId).collection("accounts").document(accountId)
        val snap = docRef.get().await()
        if (snap.getBoolean("isDefault") == true) {
            throw IllegalArgumentException(ShopLocale.get("cannot_archive_default_account", lang))
        }
        docRef.update("isArchived", true, "updatedAt", FieldValue.serverTimestamp()).await()
    }

    // -------------------------------------------------------------
    // BALANCE ADJUSTMENTS
    // -------------------------------------------------------------

    fun observeAdjustments(storeId: String): Flow<List<AccountAdjustment>> {
        if (storeId == "demo_store" || db == null) {
            return _demoAdjustments.asStateFlow()
        }
        return db.collection("stores").document(storeId)
            .collection("accountAdjustments")
            .snapshots()
            .map { snap -> snap.documents.mapNotNull { it.toObject(AccountAdjustment::class.java) } }
    }

    suspend fun recordAccountAdjustment(adjustment: AccountAdjustment): String {
        if (adjustment.note.trim().isBlank()) {
            throw IllegalArgumentException(ShopLocale.get("adjustment_note_required", _currentLanguage.value))
        }

        if (adjustment.storeId == "demo_store" || db == null) {
            val newId = "adj_${System.currentTimeMillis()}"
            val newAdj = adjustment.copy(
                id = newId,
                note = adjustment.note.trim(),
                adjustedBy = "demo_user",
                createdAt = Timestamp.now()
            )
            _demoAdjustments.value = listOf(newAdj) + _demoAdjustments.value
            return newId
        }

        val uid = requireUserId()
        val docRef = db.collection("stores").document(adjustment.storeId)
            .collection("accountAdjustments").document()
        val payload = mapOf(
            "storeId" to adjustment.storeId,
            "accountId" to adjustment.accountId,
            "accountName" to adjustment.accountName,
            "oldBalance" to adjustment.oldBalance,
            "newBalance" to adjustment.newBalance,
            "difference" to adjustment.difference,
            "note" to adjustment.note.trim(),
            "adjustedBy" to uid,
            "createdAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
        return docRef.id
    }

    // -------------------------------------------------------------
    // EXPENSES
    // -------------------------------------------------------------

    fun observeExpenses(storeId: String): Flow<List<Expense>> {
        if (storeId == "demo_store" || db == null) {
            return _demoExpenses.asStateFlow()
        }
        return db.collection("stores").document(storeId)
            .collection("expenses")
            .snapshots()
            .map { snap -> snap.documents.mapNotNull { it.toObject(Expense::class.java) } }
    }

    suspend fun recordExpense(expense: Expense): String {
        if (expense.amount <= 0.0) {
            throw IllegalArgumentException(ShopLocale.get("expense_amount_positive", _currentLanguage.value))
        }

        if (expense.storeId == "demo_store" || db == null) {
            val newId = "exp_${System.currentTimeMillis()}"
            val newExp = expense.copy(
                id = newId,
                createdBy = "demo_user",
                isDeleted = false,
                createdAt = Timestamp.now()
            )
            _demoExpenses.value = listOf(newExp) + _demoExpenses.value
            return newId
        }

        val uid = requireUserId()
        val docRef = db.collection("stores").document(expense.storeId)
            .collection("expenses").document()
        val payload = mapOf(
            "storeId" to expense.storeId,
            "amount" to expense.amount,
            "category" to expense.category.ifBlank { "Other" },
            "accountId" to expense.accountId.ifBlank { "cash_in_hand" },
            "accountName" to expense.accountName.ifBlank { "Cash in Hand" },
            "date" to if (expense.date > 0L) expense.date else System.currentTimeMillis(),
            "note" to expense.note.trim(),
            "receiptUri" to expense.receiptUri,
            "createdBy" to uid,
            "isDeleted" to false,
            "deletedNote" to "",
            "deletedBy" to "",
            "createdAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
        return docRef.id
    }

    suspend fun deleteExpense(storeId: String, expenseId: String, deleteNote: String) {
        if (storeId == "demo_store" || db == null) {
            _demoExpenses.value = _demoExpenses.value.map {
                if (it.id == expenseId) it.copy(
                    isDeleted = true,
                    deletedNote = deleteNote.trim(),
                    deletedBy = "demo_user",
                    deletedAt = Timestamp.now()
                ) else it
            }
            return
        }

        val uid = requireUserId()
        val docRef = db.collection("stores").document(storeId)
            .collection("expenses").document(expenseId)
        val payload = mapOf(
            "isDeleted" to true,
            "deletedNote" to deleteNote.trim(),
            "deletedBy" to uid,
            "deletedAt" to FieldValue.serverTimestamp()
        )
        docRef.update(payload).await()
    }

    // -------------------------------------------------------------
    // OTHER INCOME
    // -------------------------------------------------------------

    fun observeOtherIncomes(storeId: String): Flow<List<OtherIncome>> {
        if (storeId == "demo_store" || db == null) {
            return _demoOtherIncomes.asStateFlow()
        }
        return db.collection("stores").document(storeId)
            .collection("otherIncomes")
            .snapshots()
            .map { snap -> snap.documents.mapNotNull { it.toObject(OtherIncome::class.java) } }
    }

    suspend fun recordOtherIncome(income: OtherIncome): String {
        if (income.amount <= 0.0) {
            throw IllegalArgumentException(ShopLocale.get("income_amount_positive", _currentLanguage.value))
        }

        if (income.storeId == "demo_store" || db == null) {
            val newId = "inc_${System.currentTimeMillis()}"
            val newInc = income.copy(
                id = newId,
                createdBy = "demo_user",
                isDeleted = false,
                createdAt = Timestamp.now()
            )
            _demoOtherIncomes.value = listOf(newInc) + _demoOtherIncomes.value
            return newId
        }

        val uid = requireUserId()
        val docRef = db.collection("stores").document(income.storeId)
            .collection("otherIncomes").document()
        val payload = mapOf(
            "storeId" to income.storeId,
            "amount" to income.amount,
            "category" to income.category.ifBlank { "Other" },
            "accountId" to income.accountId.ifBlank { "cash_in_hand" },
            "accountName" to income.accountName.ifBlank { "Cash in Hand" },
            "date" to if (income.date > 0L) income.date else System.currentTimeMillis(),
            "note" to income.note.trim(),
            "createdBy" to uid,
            "isDeleted" to false,
            "deletedNote" to "",
            "deletedBy" to "",
            "createdAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
        return docRef.id
    }

    suspend fun deleteOtherIncome(storeId: String, incomeId: String, deleteNote: String) {
        if (storeId == "demo_store" || db == null) {
            _demoOtherIncomes.value = _demoOtherIncomes.value.map {
                if (it.id == incomeId) it.copy(
                    isDeleted = true,
                    deletedNote = deleteNote.trim(),
                    deletedBy = "demo_user",
                    deletedAt = Timestamp.now()
                ) else it
            }
            return
        }

        val uid = requireUserId()
        val docRef = db.collection("stores").document(storeId)
            .collection("otherIncomes").document(incomeId)
        val payload = mapOf(
            "isDeleted" to true,
            "deletedNote" to deleteNote.trim(),
            "deletedBy" to uid,
            "deletedAt" to FieldValue.serverTimestamp()
        )
        docRef.update(payload).await()
    }

    // -------------------------------------------------------------
    // STORE CATEGORIES (Expense & Income)
    // -------------------------------------------------------------

    fun observeCategories(storeId: String): Flow<List<StoreCategory>> {
        if (storeId == "demo_store" || db == null) {
            return _demoCategories.asStateFlow()
        }
        return db.collection("stores").document(storeId)
            .collection("categories")
            .snapshots()
            .map { snap ->
                val list = snap.documents.mapNotNull { it.toObject(StoreCategory::class.java) }
                if (list.isEmpty()) {
                    // Seed default categories in memory if none present
                    listOf(
                        StoreCategory(id = "cat_1", storeId = storeId, type = "expense", name = "Rent"),
                        StoreCategory(id = "cat_2", storeId = storeId, type = "expense", name = "Electricity"),
                        StoreCategory(id = "cat_3", storeId = storeId, type = "expense", name = "Salary"),
                        StoreCategory(id = "cat_4", storeId = storeId, type = "expense", name = "Transport"),
                        StoreCategory(id = "cat_5", storeId = storeId, type = "expense", name = "Tea and Snacks"),
                        StoreCategory(id = "cat_6", storeId = storeId, type = "expense", name = "Repair"),
                        StoreCategory(id = "cat_7", storeId = storeId, type = "expense", name = "Other"),
                        StoreCategory(id = "cat_8", storeId = storeId, type = "income", name = "Commission"),
                        StoreCategory(id = "cat_9", storeId = storeId, type = "income", name = "Rent Received"),
                        StoreCategory(id = "cat_10", storeId = storeId, type = "income", name = "Other")
                    )
                } else {
                    list
                }
            }
    }

    suspend fun createCategory(category: StoreCategory): String {
        val lang = _currentLanguage.value
        val nameTrimmed = category.name.trim()
        if (nameTrimmed.isBlank()) {
            throw IllegalArgumentException(ShopLocale.get("category_name_required", lang))
        }

        if (category.storeId == "demo_store" || db == null) {
            if (_demoCategories.value.any { it.type == category.type && it.name.trim().equals(nameTrimmed, ignoreCase = true) }) {
                throw IllegalArgumentException(ShopLocale.get("category_exists", lang))
            }
            val newId = "cat_${System.currentTimeMillis()}"
            val newCat = category.copy(
                id = newId,
                name = nameTrimmed,
                isArchived = false,
                createdBy = "demo_user",
                createdAt = Timestamp.now()
            )
            _demoCategories.value = _demoCategories.value + newCat
            return newId
        }

        val colRef = db.collection("stores").document(category.storeId).collection("categories")
        val existing = colRef.whereEqualTo("type", category.type).get().await()
        if (existing.documents.any { it.getString("name")?.trim().equals(nameTrimmed, ignoreCase = true) }) {
            throw IllegalArgumentException(ShopLocale.get("category_exists", lang))
        }

        val uid = requireUserId()
        val docRef = colRef.document()
        val payload = mapOf(
            "storeId" to category.storeId,
            "type" to category.type,
            "name" to nameTrimmed,
            "isArchived" to false,
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
        return docRef.id
    }

    suspend fun updateCategory(storeId: String, categoryId: String, newName: String) {
        val nameTrimmed = newName.trim()
        if (nameTrimmed.isBlank()) throw IllegalArgumentException(ShopLocale.get("category_name_required", _currentLanguage.value))

        if (storeId == "demo_store" || db == null) {
            _demoCategories.value = _demoCategories.value.map {
                if (it.id == categoryId) it.copy(name = nameTrimmed) else it
            }
            return
        }

        val docRef = db.collection("stores").document(storeId)
            .collection("categories").document(categoryId)
        docRef.update("name", nameTrimmed).await()
    }

    suspend fun archiveCategory(storeId: String, categoryId: String) {
        if (storeId == "demo_store" || db == null) {
            _demoCategories.value = _demoCategories.value.map {
                if (it.id == categoryId) it.copy(isArchived = true) else it
            }
            return
        }

        val docRef = db.collection("stores").document(storeId)
            .collection("categories").document(categoryId)
        docRef.update("isArchived", true).await()
    }

    // -------------------------------------------------------------
    // STORE NOTES
    // -------------------------------------------------------------

    fun observeNotes(storeId: String): Flow<List<StoreNote>> {
        if (storeId == "demo_store" || db == null) {
            return _demoNotes.asStateFlow()
        }
        return db.collection("stores").document(storeId)
            .collection("notes")
            .snapshots()
            .map { snap -> snap.documents.mapNotNull { it.toObject(StoreNote::class.java) } }
    }

    suspend fun saveNote(note: StoreNote): String {
        val titleTrimmed = note.title.trim()
        if (titleTrimmed.isBlank()) throw IllegalArgumentException(ShopLocale.get("note_title_required", _currentLanguage.value))

        if (note.storeId == "demo_store" || db == null) {
            if (note.id.isNotBlank()) {
                _demoNotes.value = _demoNotes.value.map {
                    if (it.id == note.id) note.copy(updatedAt = Timestamp.now()) else it
                }
                return note.id
            } else {
                val newId = "note_${System.currentTimeMillis()}"
                val newNote = note.copy(
                    id = newId,
                    title = titleTrimmed,
                    date = if (note.date > 0L) note.date else System.currentTimeMillis(),
                    createdBy = "demo_user",
                    createdAt = Timestamp.now(),
                    updatedAt = Timestamp.now()
                )
                _demoNotes.value = listOf(newNote) + _demoNotes.value
                return newId
            }
        }

        val uid = requireUserId()
        val colRef = db.collection("stores").document(note.storeId).collection("notes")
        if (note.id.isNotBlank()) {
            val docRef = colRef.document(note.id)
            val payload = mapOf(
                "title" to titleTrimmed,
                "text" to note.text.trim(),
                "date" to if (note.date > 0L) note.date else System.currentTimeMillis(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(payload).await()
            return note.id
        } else {
            val docRef = colRef.document()
            val payload = mapOf(
                "storeId" to note.storeId,
                "title" to titleTrimmed,
                "text" to note.text.trim(),
                "date" to if (note.date > 0L) note.date else System.currentTimeMillis(),
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            return docRef.id
        }
    }

    suspend fun deleteNote(storeId: String, noteId: String) {
        if (storeId == "demo_store" || db == null) {
            _demoNotes.value = _demoNotes.value.filter { it.id != noteId }
            return
        }

        val docRef = db.collection("stores").document(storeId)
            .collection("notes").document(noteId)
        docRef.delete().await()
    }

    // -------------------------------------------------------------
    // SUPPLIER PAYMENTS (PAYMENTS OUT)
    // -------------------------------------------------------------

    fun observeSupplierPayments(storeId: String): Flow<List<SupplierPayment>> {
        if (storeId == "demo_store" || db == null) {
            return _demoSupplierPayments.asStateFlow()
        }
        return db.collection("stores").document(storeId)
            .collection("supplierPayments")
            .snapshots()
            .map { snap -> snap.documents.mapNotNull { it.toObject(SupplierPayment::class.java) } }
    }

    suspend fun recordSupplierPayment(payment: SupplierPayment): String {
        if (payment.amount <= 0.0) throw IllegalArgumentException(ShopLocale.get("payment_positive_error", _currentLanguage.value))

        if (payment.storeId == "demo_store" || db == null) {
            val newId = "sp_${System.currentTimeMillis()}"
            val newPay = payment.copy(
                id = newId,
                paidBy = "demo_user",
                createdAt = Timestamp.now()
            )
            _demoSupplierPayments.value = listOf(newPay) + _demoSupplierPayments.value

            // Reduce supplier debt in demo
            _demoSuppliers.value = _demoSuppliers.value.map { sup ->
                if (sup.id == payment.supplierId) {
                    sup.copy(balance = (sup.balance - payment.amount).coerceAtLeast(0.0))
                } else sup
            }
            return newId
        }

        val uid = requireUserId()
        val storeDoc = db.collection("stores").document(payment.storeId)
        val payDoc = storeDoc.collection("supplierPayments").document()
        val supDoc = storeDoc.collection("suppliers").document(payment.supplierId)

        db.runTransaction { tx ->
            val supSnap = tx.get(supDoc)
            val currentBal = supSnap.getDouble("balance") ?: 0.0
            val newBal = (currentBal - payment.amount).coerceAtLeast(0.0)

            val payload = mapOf(
                "storeId" to payment.storeId,
                "paymentNo" to payment.paymentNo.ifBlank { "PAY-OUT-${System.currentTimeMillis().toString().takeLast(6)}" },
                "supplierId" to payment.supplierId,
                "supplierName" to payment.supplierName,
                "amount" to payment.amount,
                "accountId" to payment.accountId.ifBlank { "cash_in_hand" },
                "accountName" to payment.accountName.ifBlank { "Cash in Hand" },
                "paymentDate" to if (payment.paymentDate > 0L) payment.paymentDate else System.currentTimeMillis(),
                "note" to payment.note.trim(),
                "paidBy" to uid,
                "createdAt" to FieldValue.serverTimestamp()
            )
            tx.set(payDoc, payload)
            tx.update(supDoc, "balance", newBal, "updatedAt", FieldValue.serverTimestamp())
        }.await()

        return payDoc.id
    }

    // -------------------------------------------------------------
    // STAFF MEMBERS & INVITES
    // -------------------------------------------------------------
    fun observeMyMember(storeId: String, uid: String): Flow<StoreMember?> {
        val database = db
        if (storeId == "demo_store" || database == null) {
            return _demoMembers.map { list -> list.find { it.userId == uid } }
        }
        return database.collection("stores").document(storeId)
            .collection("members").document(uid)
            .snapshots()
            .map { snap -> if (snap.exists()) snap.toObject(StoreMember::class.java) else null }
    }

    fun observeMembers(storeId: String): Flow<List<StoreMember>> {
        val database = db
        if (storeId == "demo_store" || database == null) {
            return _demoMembers.asStateFlow()
        }
        return database.collection("stores").document(storeId)
            .collection("members")
            .snapshots()
            .map { snap ->
                val list = snap.toObjects(StoreMember::class.java)
                if (list.isNotEmpty()) list else {
                    val storeDoc = database.collection("stores").document(storeId).get().await()
                    storeDoc.toObject(Store::class.java)?.members ?: emptyList()
                }
            }
    }

    fun observeInvites(storeId: String): Flow<List<StoreInvite>> {
        val database = db
        if (storeId == "demo_store" || database == null) {
            return _demoInvites.asStateFlow()
        }
        return database.collection("invites")
            .whereEqualTo("storeId", storeId)
            .snapshots()
            .map { it.toObjects(StoreInvite::class.java) }
    }

    suspend fun createStaffInvite(invite: StoreInvite): String {
        val database = db
        val code = if (invite.code.isBlank()) {
            InviteCodeConfig.generate()
        } else {
            InviteCodeConfig.normalize(invite.code)
        }
        require(InviteCodeConfig.isValidFormat(code)) {
            ShopLocale.format("err_invite_code_format", _currentLanguage.value, InviteCodeConfig.CODE_LENGTH)
        }
        val inviteWithCode = invite.copy(code = code, id = code)
        if (invite.storeId == "demo_store" || database == null) {
            _demoInvites.value = _demoInvites.value.filter { it.code != code } + inviteWithCode
            return code
        }
        val inviteRef = database.collection("invites").document(code)
        inviteRef.set(inviteWithCode).await()
        return code
    }

    suspend fun cancelInvite(code: String) {
        val cleanCode = InviteCodeConfig.normalize(code)
        val database = db
        if (database == null) {
            _demoInvites.value = _demoInvites.value.map {
                if (InviteCodeConfig.normalize(it.code) == cleanCode) it.copy(status = "cancelled") else it
            }
            return
        }
        database.collection("invites").document(cleanCode)
            .update("status", "cancelled", "updatedAt", FieldValue.serverTimestamp())
            .await()
    }

    suspend fun joinStoreWithInvite(
        code: String,
        uid: String,
        email: String,
        displayName: String
    ): Result<String> {
        val database = db
        val lang = _currentLanguage.value
        val now = System.currentTimeMillis()
        val cleanCode = InviteCodeConfig.normalize(code)

        if (!InviteCodeConfig.isValidFormat(cleanCode)) {
            return Result.failure(
                IllegalArgumentException(ShopLocale.format("err_wrong_invite_code", lang, InviteCodeConfig.CODE_LENGTH))
            )
        }

        // 1. Check if user already owns or belongs to another store
        val currentProfile = getUserProfileOnce(uid)
        if (currentProfile != null && currentProfile.storeId.isNotBlank()) {
            return Result.failure(
                IllegalStateException(ShopLocale.get("err_already_linked_store", lang))
            )
        }

        // Demo Mode joining
        if (database == null) {
            val invite = _demoInvites.value.find { InviteCodeConfig.normalize(it.code) == cleanCode }
                ?: return Result.failure(IllegalArgumentException(ShopLocale.format("err_wrong_invite_code", lang, InviteCodeConfig.CODE_LENGTH)))
            if (invite.status == "cancelled") {
                return Result.failure(IllegalStateException(ShopLocale.get("err_invite_cancelled", lang)))
            }
            if (invite.status == "used") {
                return Result.failure(IllegalStateException(ShopLocale.get("err_invite_used", lang)))
            }
            if (now > invite.expiresAt) {
                return Result.failure(IllegalStateException(ShopLocale.get("err_invite_expired", lang)))
            }

            _demoInvites.value = _demoInvites.value.map {
                if (it.code == invite.code) it.copy(status = "used", usedByUid = uid, usedByEmail = email, usedAt = now) else it
            }

            val newMember = StoreMember(
                userId = uid,
                displayName = invite.staffName.ifBlank { displayName.ifBlank { ShopLocale.get("staff_member_default", lang) } },
                email = email,
                role = invite.roleLabel.lowercase(),
                roleLabel = invite.roleLabel,
                permissionLevel = invite.permissionLevel,
                permissions = invite.permissions,
                status = "Active",
                joinedTime = now,
                invitedBy = invite.createdByUid,
                inviteCode = cleanCode
            )
            _demoMembers.value = _demoMembers.value.filter { it.userId != uid } + newMember

            val currentStore = _demoStore.value
            if (currentStore != null) {
                _demoStore.value = currentStore.copy(
                    memberUids = (currentStore.memberUids + uid).distinct(),
                    members = (currentStore.members.filter { it.userId != uid } + newMember)
                )
            }

            return Result.success(invite.storeId)
        }

        // Real Firestore Mode with single atomic Transaction.
        // Note: Non-members cannot read /stores/{storeId} until after joining, so we read only /invites/{code}
        // and update /stores/{storeId}.memberUids atomically with FieldValue.arrayUnion(uid).
        val inviteRef = database.collection("invites").document(cleanCode)
        return try {
            val storeIdResult = database.runTransaction { tx ->
                val inviteSnap = tx.get(inviteRef)
                if (!inviteSnap.exists()) {
                    throw IllegalArgumentException(ShopLocale.format("err_wrong_invite_code", lang, InviteCodeConfig.CODE_LENGTH))
                }
                val invite = inviteSnap.toObject(StoreInvite::class.java)
                    ?: throw IllegalArgumentException(ShopLocale.get("err_invite_corrupt", lang))

                when {
                    invite.status == "cancelled" ->
                        throw IllegalStateException(ShopLocale.get("err_invite_cancelled", lang))
                    invite.status == "used" ->
                        throw IllegalStateException(ShopLocale.get("err_invite_used", lang))
                    now > invite.expiresAt ->
                        throw IllegalStateException(ShopLocale.get("err_invite_expired", lang))
                    invite.storeId.isBlank() ->
                        throw IllegalArgumentException(ShopLocale.get("err_invite_store_missing", lang))
                    invite.createdByUid == uid ->
                        throw IllegalStateException(ShopLocale.get("err_already_owner", lang))
                }

                val targetStoreId = invite.storeId
                val storeRef = database.collection("stores").document(targetStoreId)
                val memberRef = storeRef.collection("members").document(uid)
                val userRef = database.collection("users").document(uid)

                val newMember = StoreMember(
                    userId = uid,
                    displayName = invite.staffName.ifBlank { displayName.ifBlank { ShopLocale.get("staff_member_default", lang) } },
                    email = email,
                    role = invite.roleLabel.lowercase(),
                    roleLabel = invite.roleLabel,
                    permissionLevel = invite.permissionLevel,
                    permissions = invite.permissions,
                    status = "Active",
                    joinedTime = now,
                    invitedBy = invite.createdByUid,
                    inviteCode = cleanCode
                )

                tx.update(
                    inviteRef,
                    mapOf(
                        "status" to "used",
                        "usedByUid" to uid,
                        "usedByEmail" to email,
                        "usedAt" to now,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                tx.set(memberRef, newMember)

                tx.update(
                    storeRef,
                    mapOf(
                        "memberUids" to FieldValue.arrayUnion(uid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                tx.set(
                    userRef,
                    mapOf(
                        "email" to email,
                        "storeId" to targetStoreId,
                        "role" to invite.roleLabel,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )

                targetStoreId
            }.await()

            Result.success(storeIdResult)
        } catch (e: FirebaseFirestoreException) {
            val friendlyMessage = resolveBlockedJoinMessage(inviteRef, uid, e)
            Result.failure(IllegalStateException(friendlyMessage, e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun resolveBlockedJoinMessage(
        inviteRef: com.google.firebase.firestore.DocumentReference,
        uid: String,
        exception: FirebaseFirestoreException
    ): String {
        val lang = _currentLanguage.value
        if (exception.code == FirebaseFirestoreException.Code.NOT_FOUND) {
            return ShopLocale.get("err_invite_not_found", lang)
        }
        val latestInvite = runCatching {
            inviteRef.get().await().toObject(StoreInvite::class.java)
        }.getOrNull()
        return when {
            latestInvite == null ->
                ShopLocale.format("err_wrong_invite_code", lang, InviteCodeConfig.CODE_LENGTH)
            latestInvite.status == "cancelled" ->
                ShopLocale.get("err_invite_cancelled", lang)
            latestInvite.status == "used" ->
                ShopLocale.get("err_invite_used", lang)
            System.currentTimeMillis() > latestInvite.expiresAt ->
                ShopLocale.get("err_invite_expired", lang)
            latestInvite.createdByUid == uid ->
                ShopLocale.get("err_already_owner", lang)
            else ->
                ShopLocale.get("err_invite_blocked", lang)
        }
    }

    suspend fun updateMemberPermissions(
        storeId: String,
        memberId: String,
        roleLabel: String,
        permissionLevel: String,
        permissions: Map<String, Boolean>
    ) {
        val database = db
        if (storeId == "demo_store" || database == null) {
            _demoMembers.value = _demoMembers.value.map {
                if (it.userId == memberId) {
                    it.copy(
                        roleLabel = roleLabel,
                        role = roleLabel.lowercase(),
                        permissionLevel = permissionLevel,
                        permissions = permissions
                    )
                } else it
            }
            return
        }

        val memberRef = database.collection("stores").document(storeId).collection("members").document(memberId)
        val payload = mapOf(
            "roleLabel" to roleLabel,
            "role" to roleLabel.lowercase(),
            "permissionLevel" to permissionLevel,
            "permissions" to permissions,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        memberRef.set(payload, SetOptions.merge()).await()
    }

    suspend fun removeMember(storeId: String, memberId: String) {
        val database = db
        if (storeId == "demo_store" || database == null) {
            _demoMembers.value = _demoMembers.value.map {
                if (it.userId == memberId) it.copy(status = "Removed") else it
            }
            val curStore = _demoStore.value
            if (curStore != null) {
                _demoStore.value = curStore.copy(
                    memberUids = curStore.memberUids.filter { it != memberId }
                )
            }
            return
        }

        val storeRef = database.collection("stores").document(storeId)
        val memberRef = storeRef.collection("members").document(memberId)

        database.runTransaction { tx ->
            val storeSnap = tx.get(storeRef)
            val currentUids = (storeSnap.get("memberUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val updatedUids = currentUids.filter { it != memberId }

            tx.update(memberRef, "status", "Removed", "updatedAt", FieldValue.serverTimestamp())
            tx.update(storeRef, "memberUids", updatedUids, "updatedAt", FieldValue.serverTimestamp())
        }.await()
    }

    suspend fun restoreMember(storeId: String, memberId: String) {
        val database = db
        if (storeId == "demo_store" || database == null) {
            _demoMembers.value = _demoMembers.value.map {
                if (it.userId == memberId) it.copy(status = "Active") else it
            }
            val curStore = _demoStore.value
            if (curStore != null) {
                _demoStore.value = curStore.copy(
                    memberUids = (curStore.memberUids + memberId).distinct()
                )
            }
            return
        }

        val storeRef = database.collection("stores").document(storeId)
        val memberRef = storeRef.collection("members").document(memberId)

        database.runTransaction { tx ->
            val storeSnap = tx.get(storeRef)
            val currentUids = (storeSnap.get("memberUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val updatedUids = (currentUids + memberId).distinct()

            tx.update(memberRef, "status", "Active", "updatedAt", FieldValue.serverTimestamp())
            tx.update(storeRef, "memberUids", updatedUids, "updatedAt", FieldValue.serverTimestamp())
        }.await()
    }
}
