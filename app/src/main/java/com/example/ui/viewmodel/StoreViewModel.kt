package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CompletedSaleReceipt
import com.example.data.model.Customer
import com.example.data.model.Payment
import com.example.data.model.Product
import com.example.data.model.Sale
import com.example.data.model.SaleItem
import com.example.data.model.Store
import com.example.data.repository.StoreRepository
import com.example.domain.BakiOverview
import com.example.domain.CalculationEngine
import com.example.domain.CustomerBalanceSummary
import com.example.domain.CustomerLedgerEntry
import com.example.domain.HomeInsights
import com.example.domain.LedgerEntryType
import com.example.domain.SevenDayCashflowSummary
import com.example.domain.ShopType
import com.example.domain.ShopTypeCatalog
import com.example.domain.ShopUnitPreset
import com.example.domain.StockSummary
import com.example.domain.StoreFinancials
import com.example.domain.PeriodFinancialSummary
import com.example.domain.ProductSoldStat
import com.example.data.model.Supplier
import com.example.data.model.Purchase
import com.example.data.model.PurchaseItem
import com.example.data.model.PurchaseReturn
import com.example.data.model.HealthFixLog
import com.example.data.model.Account
import com.example.data.model.AccountAdjustment
import com.example.data.model.Expense
import com.example.data.model.OtherIncome
import com.example.data.model.StoreCategory
import com.example.data.model.StoreInvite
import com.example.data.model.StoreMember
import com.example.data.model.StoreNote
import com.example.data.model.StorePermissions
import com.example.data.model.SupplierPayment
import com.example.ui.components.ShopNavTab
import com.example.data.model.AccountStatementReport
import com.example.data.model.AccountStatementEntry
import com.example.domain.DailySalesPoint
import com.example.domain.CategorySalesStat
import com.example.domain.MoneyNowSummary
import com.example.domain.ProfitAndLossStatement
import com.example.domain.DayBookEntry
import com.example.domain.CustomerStatementReport
import com.example.domain.SupplierStatementReport
import com.example.domain.MonthlyReportData
import com.example.domain.HealthProblem
import java.util.Calendar
import com.example.util.AppLanguage
import com.example.util.NetworkMonitor
import com.example.util.ProductImageUtil
import com.example.util.ShopLocale
import com.example.util.StockPreferences
import com.example.util.SyncStatus
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class HistoryPeriod(val labelEn: String, val labelBn: String) {
    TODAY("Today", "আজকে"),
    WEEK("Week", "সপ্তাহ"),
    MONTH("Month", "মাস"),
    YEAR("Year", "বছর"),
    ALL("All", "সব"),
    CUSTOM("Custom Date", "নির্দিষ্ট তারিখ")
}

enum class HistoryFilter(val labelEn: String, val labelBn: String) {
    ALL("All", "সব"),
    SALES_ONLY("Sales Only", "শুধু বিক্রয়"),
    PURCHASES_ONLY("Purchases Only", "শুধু ক্রয়")
}

enum class HistoryViewMode {
    TRANSACTIONS, PRODUCTS_SOLD
}

enum class BusinessSubScreen {
    BAKI_HUB, CUSTOMERS_LIST, CUSTOMER_DETAILS
}

enum class BusinessNavDestination {
    DASHBOARD,
    FINANCIAL_REPORTS_HUB,
    BAKI_SCREEN,
    SUPPLIERS_SCREEN,
    LOW_STOCK_SCREEN,
    SLOW_SELLERS_SCREEN,
    REPORT_SALES,
    REPORT_PURCHASES,
    REPORT_DAY_BOOK,
    REPORT_PROFIT_LOSS,
    REPORT_CUSTOMER_STATEMENT,
    REPORT_SUPPLIER_STATEMENT,
    REPORT_INVENTORY,
    MONTHLY_REPORT,
    DATA_HEALTH_CHECK,
    TEST_CHECKLIST,
    CASH_BANK_SCREEN,
    ACCOUNT_DETAILS_SCREEN,
    EXPENSES_SCREEN,
    OTHER_INCOMES_SCREEN,
    MANAGE_CATEGORIES_SCREEN,
    NOTES_SCREEN,
    REPORT_EXPENSES,
    REPORT_INCOMES,
    STAFF_MANAGEMENT_SCREEN,
    STAFF_MEMBER_DETAILS_SCREEN,
    REPORT_STAFF
}

sealed class HistoryTimelineItem {
    abstract val id: String
    abstract val timestamp: Long

    data class SaleItemRow(val sale: Sale) : HistoryTimelineItem() {
        override val id: String get() = sale.id
        override val timestamp: Long get() = sale.createdAt?.toDate()?.time ?: 0L
    }

    data class PaymentItemRow(val payment: Payment) : HistoryTimelineItem() {
        override val id: String get() = payment.id
        override val timestamp: Long get() = if (payment.paymentDate > 0L) payment.paymentDate else (payment.createdAt?.toDate()?.time ?: 0L)
    }

    data class ExpenseItemRow(val expense: Expense) : HistoryTimelineItem() {
        override val id: String get() = expense.id
        override val timestamp: Long get() = if (expense.date > 0L) expense.date else (expense.createdAt?.toDate()?.time ?: 0L)
    }

    data class OtherIncomeItemRow(val income: OtherIncome) : HistoryTimelineItem() {
        override val id: String get() = income.id
        override val timestamp: Long get() = if (income.date > 0L) income.date else (income.createdAt?.toDate()?.time ?: 0L)
    }

    data class AdjustmentItemRow(val adjustment: AccountAdjustment) : HistoryTimelineItem() {
        override val id: String get() = adjustment.id
        override val timestamp: Long get() = adjustment.createdAt?.toDate()?.time ?: 0L
    }

    data class PurchaseItemRow(val purchase: Purchase) : HistoryTimelineItem() {
        override val id: String get() = "pur_${purchase.id}"
        override val timestamp: Long get() = purchase.createdAt?.toDate()?.time ?: 0L
    }
}

data class PosCartState(
    val items: Map<String, SaleItem> = emptyMap(), // Keyed by "${productId}__${unit.lowercase()}"
    val totalItems: Double = 0.0,
    val subtotal: Double = 0.0,
    val totalDiscount: Double = 0.0,
    val invoiceDiscount: Double = 0.0,
    val grandTotal: Double = 0.0,
    val projectedProfit: Double = 0.0,
    val paymentType: String = "cash", // "cash" or "baki"
    val customerId: String = "",
    val customerName: String = "Cash Sale",
    val customerPhone: String = "",
    val accountId: String = "cash_in_hand",
    val accountName: String = "Cash in Hand",
    val notes: String = ""
)

class StoreViewModel(
    val storeId: String,
    private val repository: StoreRepository,
    networkMonitor: NetworkMonitor? = null,
    val currentUserId: String = repository.currentUid ?: "demo_user"
) : ViewModel() {

    // Language state (persisted in StoreRepository)
    val currentLanguage: StateFlow<AppLanguage> = repository.currentLanguage

    private fun tr(key: String): String = ShopLocale.get(key, currentLanguage.value)
    private fun tr(key: String, vararg args: Any): String =
        ShopLocale.format(key, currentLanguage.value, *args)

    private val stockPreferences = StockPreferences(repository.appContext)

    private var hasUserToggledGridView = false
    private val _isStockGridView = MutableStateFlow(false)
    val isStockGridView: StateFlow<Boolean> = _isStockGridView.asStateFlow()

    // Shared Simple / Detailed Mode state (default Detailed = false, persisted per user in Firebase)
    private val _isSimpleModeOverride = MutableStateFlow<Boolean?>(null)
    val isSimpleMode: StateFlow<Boolean> = combine(
        repository.observeUserProfile(currentUserId).map { it?.isSimpleMode ?: false },
        _isSimpleModeOverride
    ) { persisted, override ->
        override ?: persisted
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        repository.getCachedUserSimpleMode(currentUserId)
    )

    fun setSimpleMode(simple: Boolean) {
        _isSimpleModeOverride.value = simple
        if (simple && _historyPeriod.value == HistoryPeriod.CUSTOM) {
            _historyPeriod.value = HistoryPeriod.TODAY
        }
        if (simple) {
            _cart.value = _cart.value.copy(accountId = "cash_in_hand", accountName = "Cash in Hand")
        }
        viewModelScope.launch {
            try {
                repository.updateUserSimpleMode(currentUserId, simple)
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Failed to save simple mode preference", e)
            }
        }
    }

    fun toggleStockViewMode() {
        setStockGridView(!_isStockGridView.value)
    }

    fun setStockGridView(isGrid: Boolean) {
        hasUserToggledGridView = true
        _isStockGridView.value = isGrid
        viewModelScope.launch {
            stockPreferences.setStockGridView(isGrid)
        }
    }

    init {
        viewModelScope.launch {
            stockPreferences.isStockGridView.collect { persisted ->
                if (!hasUserToggledGridView) {
                    _isStockGridView.value = persisted
                }
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    // Status notifications
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    // Network and sync status (Online / Syncing / Offline)
    val syncStatus: StateFlow<SyncStatus> = if (networkMonitor != null) {
        combine(networkMonitor.isOnline, _isBusy) { online, busy ->
            when {
                !online -> SyncStatus.OFFLINE
                busy -> SyncStatus.SYNCING
                else -> SyncStatus.ONLINE
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), SyncStatus.ONLINE)
    } else {
        _isBusy.map { busy ->
            if (busy) SyncStatus.SYNCING else SyncStatus.ONLINE
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), SyncStatus.ONLINE)
    }

    private val _accessRevoked = MutableStateFlow(false)

    private fun isPermissionDenied(t: Throwable): Boolean {
        var cur: Throwable? = t
        while (cur != null) {
            if (cur is FirebaseFirestoreException && cur.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return true
            }
            cur = cur.cause
        }
        return false
    }

    private fun <T> Flow<List<T>>.orEmptyOnDenied(onDenied: (() -> Unit)? = null): Flow<List<T>> =
        catch { e ->
            if (isPermissionDenied(e)) {
                onDenied?.invoke()
                emit(emptyList())
            } else {
                throw e
            }
        }

    // Store profile
    val store: StateFlow<Store?> = repository.observeStore(storeId)
        .catch { e ->
            if (isPermissionDenied(e)) {
                _accessRevoked.value = true
                emit(null)
            } else {
                throw e
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isOwner: StateFlow<Boolean> = combine(store, MutableStateFlow(currentUserId)) { s, uid ->
        s != null && (s.ownerId == uid || (storeId == "demo_store" && uid == "demo_user"))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val myMemberDoc: StateFlow<StoreMember?> = repository.observeMyMember(storeId, currentUserId)
        .catch { e ->
            if (isPermissionDenied(e)) {
                _accessRevoked.value = true
                emit(null)
            } else {
                throw e
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val currentMember: StateFlow<StoreMember?> = combine(myMemberDoc, store, isOwner) { myMem, _, owner ->
        if (owner) {
            StoreMember(
                userId = currentUserId,
                displayName = "Owner",
                roleLabel = "Owner",
                permissionLevel = "Full",
                permissions = StorePermissions.defaultOwnerPermissions(),
                status = "Active"
            )
        } else {
            myMem
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isRemoved: StateFlow<Boolean> = combine(myMemberDoc, isOwner, _accessRevoked, store) { mem, owner, revoked, st ->
        !owner && (
            revoked ||
                (mem != null && mem.status.equals("Removed", ignoreCase = true)) ||
                (st != null && !st.memberUids.contains(currentUserId))
            )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private fun permissionStateFlow(key: String): StateFlow<Boolean> =
        combine(isOwner, currentMember, isRemoved) { owner, mem, removed ->
            when {
                owner -> true
                removed -> false
                mem == null -> false
                else -> mem.status.equals("Active", ignoreCase = true) && mem.permissions[key] == true
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val canSellFlow: StateFlow<Boolean> = permissionStateFlow(StorePermissions.SELL)
    val canViewStockFlow: StateFlow<Boolean> = permissionStateFlow(StorePermissions.VIEW_STOCK)
    val canAddEditProductsFlow: StateFlow<Boolean> = permissionStateFlow(StorePermissions.ADD_EDIT_PRODUCTS)
    val canSeeBuyingPriceAndProfitFlow: StateFlow<Boolean> = permissionStateFlow(StorePermissions.SEE_BUYING_PRICE_AND_PROFIT)
    val canSeeHistoryFlow: StateFlow<Boolean> = permissionStateFlow(StorePermissions.HISTORY)
    val canAccessCustomersAndBakiFlow: StateFlow<Boolean> = permissionStateFlow(StorePermissions.CUSTOMERS_AND_BAKI)
    val canReceivePaymentFlow: StateFlow<Boolean> = permissionStateFlow(StorePermissions.RECEIVE_PAYMENT)
    val canAccessSuppliersAndPurchasesFlow: StateFlow<Boolean> = permissionStateFlow(StorePermissions.SUPPLIERS_AND_PURCHASES)
    val canPaySupplierFlow: StateFlow<Boolean> = permissionStateFlow(StorePermissions.PAY_SUPPLIER)
    val canViewReportsFlow: StateFlow<Boolean> = permissionStateFlow(StorePermissions.REPORTS)
    val canAccessCashAndBankFlow: StateFlow<Boolean> = permissionStateFlow(StorePermissions.CASH_AND_BANK)
    val canAccessExpensesAndIncomeFlow: StateFlow<Boolean> = permissionStateFlow(StorePermissions.EXPENSES_AND_OTHER_INCOME)
    val canAccessNotesFlow: StateFlow<Boolean> = permissionStateFlow(StorePermissions.NOTES)

    fun hasPermission(key: String): Boolean =
        isOwner.value || (!isRemoved.value && currentMember.value?.status.equals("Active", ignoreCase = true) && currentMember.value?.permissions?.get(key) == true)
    fun canSeeBuyingPriceAndProfit(): Boolean = canSeeBuyingPriceAndProfitFlow.value
    fun canSell(): Boolean = canSellFlow.value
    fun canViewStock(): Boolean = canViewStockFlow.value
    fun canAddEditProducts(): Boolean = canAddEditProductsFlow.value
    fun canSeeHistory(): Boolean = canSeeHistoryFlow.value
    fun canAccessCustomersAndBaki(): Boolean = canAccessCustomersAndBakiFlow.value
    fun canReceivePayment(): Boolean = canReceivePaymentFlow.value
    fun canAccessSuppliersAndPurchases(): Boolean = canAccessSuppliersAndPurchasesFlow.value
    fun canPaySupplier(): Boolean = canPaySupplierFlow.value
    fun canViewReports(): Boolean = canViewReportsFlow.value
    fun canAccessCashAndBank(): Boolean = canAccessCashAndBankFlow.value
    fun canAccessExpensesAndIncome(): Boolean = canAccessExpensesAndIncomeFlow.value
    fun canAccessNotes(): Boolean = canAccessNotesFlow.value

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> gatedCollection(
        allowedFlow: Flow<Boolean>,
        source: () -> Flow<List<T>>
    ): StateFlow<List<T>> = combine(allowedFlow, isRemoved) { allowed, removed ->
        allowed && !removed
    }.flatMapLatest { canRead ->
        if (canRead) source().orEmptyOnDenied() else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Products catalog
    val products: StateFlow<List<Product>> = gatedCollection(
        combine(
            canViewStockFlow,
            canSellFlow,
            canAddEditProductsFlow,
            canViewReportsFlow,
            canAccessSuppliersAndPurchasesFlow
        ) { stock, sell, addEdit, rep, sup -> stock || sell || addEdit || rep || sup }
    ) { repository.observeProducts(storeId) }

    val shopType: StateFlow<ShopType?> = store.map { s ->
        ShopType.fromId(s?.shopType)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val availableProductCategories: StateFlow<List<String>> = combine(store, products) { s, prodList ->
        val result = mutableListOf<String>()
        fun addUnique(raw: String) {
            val trimmed = raw.trim()
            if (trimmed.isNotBlank() && result.none { it.equals(trimmed, ignoreCase = true) }) {
                result.add(trimmed)
            }
        }
        s?.productCategories?.forEach(::addUnique)
        val resolvedType = ShopType.fromId(s?.shopType)
        if (s?.productCategories.isNullOrEmpty() && resolvedType != null) {
            ShopTypeCatalog.startingCategories(resolvedType).forEach(::addUnique)
        }
        prodList.forEach { addUnique(it.category) }
        if (result.isEmpty()) addUnique("General")
        result.toList()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, listOf("General"))

    val unitPresets: StateFlow<List<ShopUnitPreset>> = shopType.map { st ->
        if (st != null) ShopTypeCatalog.unitPresets(st) else ShopTypeCatalog.unitPresets(ShopType.GENERAL)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ShopTypeCatalog.unitPresets(ShopType.GENERAL))

    fun updateStoreShopType(
        newType: ShopType,
        addSuggestedCategories: Boolean,
        onSuccess: () -> Unit = {}
    ) {
        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val existingFallback = (store.value?.productCategories.orEmpty() + products.value.map { it.category })
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() }
                repository.updateStoreShopType(
                    storeId = storeId,
                    shopType = newType,
                    addSuggestedCategories = addSuggestedCategories,
                    existingCategoriesFallback = existingFallback
                )
                _statusMessage.value = tr(
                    "shop_type_updated_msg",
                    ShopTypeCatalog.displayName(newType, currentLanguage.value)
                )
                onSuccess()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Failed to update shop type", e)
                _errorMessage.value = e.localizedMessage ?: tr("err_unknown")
            } finally {
                _isBusy.value = false
            }
        }
    }

    private val photoRetryMutex = Mutex()
    private val attemptedPhotoUploadKeys = mutableSetOf<String>()

    init {
        if (storeId != "demo_store" && networkMonitor != null) {
            viewModelScope.launch {
                var wasOnline = false
                combine(
                    networkMonitor.isOnline,
                    products,
                    canAddEditProductsFlow
                ) { online, prodList, canEdit ->
                    Triple(online, prodList, canEdit)
                }.collect { (online, prodList, canEdit) ->
                    if (!online) {
                        wasOnline = false
                        return@collect
                    }
                    if (!wasOnline) {
                        // Reset attempted keys whenever the device transitions from offline to online
                        attemptedPhotoUploadKeys.clear()
                        wasOnline = true
                    }
                    if (canEdit) {
                        val pending = prodList.filter { prod ->
                            ProductImageUtil.isLocalPhotoUri(prod.photoUri) &&
                                !attemptedPhotoUploadKeys.contains("${prod.id}|${prod.photoUri}")
                        }
                        if (pending.isNotEmpty()) {
                            retryPendingProductPhotosInternal(pending)
                        }
                    }
                }
            }
        }
    }

    private fun retryPendingProductPhotosInternal(pendingProducts: List<Product>) {
        viewModelScope.launch {
            photoRetryMutex.withLock {
                var uploadedCount = 0
                for (product in pendingProducts) {
                    val key = "${product.id}|${product.photoUri}"
                    if (attemptedPhotoUploadKeys.contains(key)) continue
                    attemptedPhotoUploadKeys.add(key)
                    try {
                        if (repository.retryProductPhotoUpload(storeId, product)) {
                            uploadedCount++
                        }
                    } catch (e: Exception) {
                        Log.w("StoreViewModel", "Retry upload failed for ${product.id}: ${e.message}")
                    }
                }
                if (uploadedCount > 0) {
                    _statusMessage.value = if (uploadedCount == 1) {
                        tr("pending_photo_uploaded_one")
                    } else {
                        tr("pending_photo_uploaded_many", uploadedCount)
                    }
                }
            }
        }
    }

    // Stock Summary: items count, low stock count, stock value at cost
    val stockSummary: StateFlow<StockSummary> = products.map { prodList ->
        CalculationEngine.calculateStockSummary(prodList)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        StockSummary(0L, 0L, 0L, 0.0)
    )

    // Low stock products (stock <= lowStockLimit)
    val lowStockProducts: StateFlow<List<Product>> = products.map { list ->
        list.filter { it.stock <= it.lowStockLimit }
            .sortedBy { it.stock }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Sales history
    val sales: StateFlow<List<Sale>> = gatedCollection(
        combine(canSeeHistoryFlow, canViewReportsFlow, canAccessCashAndBankFlow) { hist, rep, cash ->
            hist || rep || cash
        }
    ) { repository.observeSales(storeId) }

    // Recent sales (up to 10)
    val recentSales: StateFlow<List<Sale>> = sales.map { list ->
        list.take(10)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Customers management (real-time stream)
    val customers: StateFlow<List<Customer>> = gatedCollection(
        combine(
            canAccessCustomersAndBakiFlow,
            canReceivePaymentFlow,
            canSellFlow,
            canViewReportsFlow
        ) { baki, pay, sell, rep -> baki || pay || sell || rep }
    ) { repository.observeCustomers(storeId) }

    // Payments recorded (Payment In)
    val payments: StateFlow<List<Payment>> = gatedCollection(
        combine(
            canReceivePaymentFlow,
            canViewReportsFlow,
            canAccessCashAndBankFlow,
            canAccessCustomersAndBakiFlow,
            canSeeHistoryFlow
        ) { rec, rep, cash, baki, hist -> rec || rep || cash || (baki && hist) }
    ) { repository.observePayments(storeId) }

    // Suppliers (stream)
    val suppliers: StateFlow<List<Supplier>> = gatedCollection(
        combine(
            canAccessSuppliersAndPurchasesFlow,
            canPaySupplierFlow,
            canViewReportsFlow
        ) { sup, pay, rep -> sup || pay || rep }
    ) { repository.observeSuppliers(storeId) }

    // Purchases (stream)
    val purchases: StateFlow<List<Purchase>> = gatedCollection(
        combine(
            canAccessSuppliersAndPurchasesFlow,
            canViewReportsFlow,
            canAccessCashAndBankFlow
        ) { sup, rep, cash -> sup || rep || cash }
    ) { repository.observePurchases(storeId) }

    // Purchase Returns (stream)
    val purchaseReturns: StateFlow<List<PurchaseReturn>> = gatedCollection(
        combine(canAccessSuppliersAndPurchasesFlow, canViewReportsFlow) { sup, rep -> sup || rep }
    ) { repository.observePurchaseReturns(storeId) }

    // Health Fix Logs (stream - owner only)
    val healthFixLogs: StateFlow<List<HealthFixLog>> = gatedCollection(isOwner) {
        repository.observeHealthFixLogs(storeId)
    }

    // History and reporting period state
    private val _historyPeriod = MutableStateFlow(HistoryPeriod.TODAY)
    val historyPeriod: StateFlow<HistoryPeriod> = _historyPeriod.asStateFlow()

    private val _historyCustomDateRange = MutableStateFlow<Pair<Long, Long>?>(null)
    val historyCustomDateRange: StateFlow<Pair<Long, Long>?> = _historyCustomDateRange.asStateFlow()

    private val _historyFilter = MutableStateFlow(HistoryFilter.ALL)
    val historyFilter: StateFlow<HistoryFilter> = _historyFilter.asStateFlow()

    private val _historyViewMode = MutableStateFlow(HistoryViewMode.TRANSACTIONS)
    val historyViewMode: StateFlow<HistoryViewMode> = _historyViewMode.asStateFlow()

    private val _historySearchQuery = MutableStateFlow("")
    val historySearchQuery: StateFlow<String> = _historySearchQuery.asStateFlow()

    // Accounts (stream)
    val accounts: StateFlow<List<Account>> = gatedCollection(canAccessCashAndBankFlow) {
        repository.observeAccounts(storeId)
    }

    // Adjustments (stream)
    val adjustments: StateFlow<List<AccountAdjustment>> = gatedCollection(canAccessCashAndBankFlow) {
        repository.observeAdjustments(storeId)
    }

    // Expenses (stream)
    val expenses: StateFlow<List<Expense>> = gatedCollection(
        combine(canAccessExpensesAndIncomeFlow, canViewReportsFlow, canAccessCashAndBankFlow) { exp, rep, cash ->
            exp || rep || cash
        }
    ) { repository.observeExpenses(storeId) }

    // Other Incomes (stream)
    val otherIncomes: StateFlow<List<OtherIncome>> = gatedCollection(
        combine(canAccessExpensesAndIncomeFlow, canViewReportsFlow, canAccessCashAndBankFlow) { inc, rep, cash ->
            inc || rep || cash
        }
    ) { repository.observeOtherIncomes(storeId) }

    // Categories (stream)
    val categories: StateFlow<List<StoreCategory>> = gatedCollection(
        combine(canAccessExpensesAndIncomeFlow, canViewReportsFlow) { exp, rep -> exp || rep }
    ) { repository.observeCategories(storeId) }

    // Store Notes (stream)
    val notes: StateFlow<List<StoreNote>> = gatedCollection(canAccessNotesFlow) {
        repository.observeNotes(storeId)
    }

    // Supplier Payments (stream)
    val supplierPayments: StateFlow<List<SupplierPayment>> = gatedCollection(
        combine(
            canPaySupplierFlow,
            canAccessSuppliersAndPurchasesFlow,
            canViewReportsFlow,
            canAccessCashAndBankFlow
        ) { pay, sup, rep, cash -> pay || sup || rep || cash }
    ) { repository.observeSupplierPayments(storeId) }

    // Members & Invites streams
    val members: StateFlow<List<StoreMember>> = gatedCollection(
        combine(store, isOwner, myMemberDoc) { s, owner, mem ->
            s != null && (owner || (mem != null && mem.status.equals("Active", ignoreCase = true)))
        }
    ) { repository.observeMembers(storeId) }

    val invites: StateFlow<List<StoreInvite>> = gatedCollection(isOwner) {
        repository.observeInvites(storeId)
    }

    val selectedMember = MutableStateFlow<StoreMember?>(null)

    // Last used account ID (remembered across operations, defaults to "cash_in_hand")
    private val _lastUsedAccountId = MutableStateFlow("cash_in_hand")
    val lastUsedAccountId: StateFlow<String> = _lastUsedAccountId.asStateFlow()

    fun updateLastUsedAccount(accountId: String) {
        if (accountId.isNotBlank()) _lastUsedAccountId.value = accountId
    }

    // Account Balances Map: accountId -> Balance (Derived from CalculationEngine)
    val accountBalances: StateFlow<Map<String, Double>> = combine(
        combine(accounts, sales, payments) { accList, sList, payList -> Triple(accList, sList, payList) },
        combine(purchases, supplierPayments, expenses) { purList, spList, expList -> Triple(purList, spList, expList) },
        combine(otherIncomes, adjustments) { incList, adjList -> incList to adjList }
    ) { (accList, sList, payList), (purList, spList, expList), (incList, adjList) ->
        CalculationEngine.calculateAccountBalances(
            accounts = accList,
            sales = sList,
            paymentsIn = payList,
            purchases = purList,
            supplierPayments = spList,
            expenses = expList,
            otherIncomes = incList,
            adjustments = adjList
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyMap())

    // Total Balance across all non-archived accounts (Derived from CalculationEngine)
    val totalBalance: StateFlow<Double> = combine(accounts, accountBalances) { accList, balMap ->
        CalculationEngine.calculateTotalBalance(accList, balMap)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0.0)

    // Selected Account for Account Details & Statement Screen
    private val _selectedAccount = MutableStateFlow<Account?>(null)
    val selectedAccount: StateFlow<Account?> = _selectedAccount.asStateFlow()

    fun selectAccount(account: Account?) {
        _selectedAccount.value = account
    }

    // Account statement report for selected account & selected business period
    val selectedAccountStatement: StateFlow<AccountStatementReport?> = combine(
        combine(_selectedAccount, sales, payments) { acc, sList, payList -> Triple(acc, sList, payList) },
        combine(purchases, supplierPayments, expenses) { purList, spList, expList -> Triple(purList, spList, expList) },
        combine(otherIncomes, adjustments, _historyPeriod, _historyCustomDateRange) { incList, adjList, period, customRange ->
            listOf(incList, adjList, period, customRange)
        }
    ) { (acc, sList, payList), (purList, spList, expList), extras ->
        @Suppress("UNCHECKED_CAST")
        val incList = extras[0] as List<OtherIncome>
        @Suppress("UNCHECKED_CAST")
        val adjList = extras[1] as List<AccountAdjustment>
        val period = extras[2] as HistoryPeriod
        @Suppress("UNCHECKED_CAST")
        val customRange = extras[3] as Pair<Long, Long>?
        if (acc != null) {
            CalculationEngine.calculateAccountStatement(
                account = acc,
                allSales = sList,
                allPaymentsIn = payList,
                allPurchases = purList,
                allSupplierPayments = spList,
                allExpenses = expList,
                allOtherIncomes = incList,
                allAdjustments = adjList,
                period = period,
                customRange = customRange
            )
        } else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)


    // Baki overview: total owed, count, debtors list (All from CalculationEngine)
    val bakiOverview: StateFlow<BakiOverview> = combine(
        customers,
        sales,
        payments
    ) { custList, saleList, payList ->
        CalculationEngine.calculateBakiOverview(custList, saleList, payList)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        BakiOverview(0.0, 0L, emptyList())
    )

    // All customers with calculated balances
    val allCustomerSummaries: StateFlow<List<CustomerBalanceSummary>> = combine(
        customers,
        sales,
        payments
    ) { custList, saleList, payList ->
        val list = custList.toMutableList()
        for (sale in saleList) {
            if (sale.customerName != "Cash Sale" && sale.customerName.isNotBlank()) {
                val exists = list.any { CalculationEngine.doesSaleMatchCustomer(sale, it) }
                if (!exists) {
                    list.add(
                        Customer(
                            id = sale.customerId.ifBlank { "legacy_${sale.customerName.trim().lowercase().replace(' ', '_')}" },
                            name = sale.customerName.trim(),
                            mobile = sale.customerPhone.trim(),
                            notes = "Previous customer"
                        )
                    )
                }
            }
        }
        list.map { CalculationEngine.calculateCustomerSummary(it, saleList, payList) }
            .sortedBy { it.customer.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Business Sub-Screen and Selected Customer details
    private val _businessSubScreen = MutableStateFlow(BusinessSubScreen.BAKI_HUB)
    val businessSubScreen: StateFlow<BusinessSubScreen> = _businessSubScreen.asStateFlow()

    private val _selectedCustomer = MutableStateFlow<Customer?>(null)
    val selectedCustomer: StateFlow<Customer?> = _selectedCustomer.asStateFlow()

    val selectedCustomerSummary: StateFlow<CustomerBalanceSummary?> = combine(
        _selectedCustomer,
        sales,
        payments
    ) { cust, saleList, payList ->
        if (cust != null) CalculationEngine.calculateCustomerSummary(cust, saleList, payList) else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val selectedCustomerLedger: StateFlow<List<CustomerLedgerEntry>> = combine(
        _selectedCustomer,
        sales,
        payments
    ) { cust, saleList, payList ->
        if (cust != null) CalculationEngine.calculateCustomerLedger(cust, saleList, payList) else emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Financial Metrics - Strictly calculated via ONE module CalculationEngine
    val financials: StateFlow<StoreFinancials> = combine(products, sales) { prodList, saleList ->
        CalculationEngine.calculateStoreFinancials(prodList, saleList)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        StoreFinancials(0.0, 0.0, 0L, 0.0, 0.0, 0.0)
    )

    val homeInsights: StateFlow<HomeInsights> = sales.map { saleList ->
        CalculationEngine.calculateHomeInsights(saleList)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        HomeInsights(0.0, null, emptyList())
    )

    val sevenDayCashflow: StateFlow<SevenDayCashflowSummary> = combine(
        combine(sales, payments, otherIncomes) { s, p, inc -> Triple(s, p, inc) },
        combine(purchases, supplierPayments, expenses) { pur, sp, exp -> Triple(pur, sp, exp) },
        currentLanguage
    ) { (sList, pList, incList), (purList, spList, expList), lang ->
        CalculationEngine.calculateSevenDayCashflow(
            sales = sList,
            paymentsIn = pList,
            otherIncomes = incList,
            purchases = purList,
            supplierPayments = spList,
            expenses = expList,
            lang = lang
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        SevenDayCashflowSummary(emptyList(), 0.0, 0.0)
    )

    private val _isPrivacyHidden = MutableStateFlow(false)
    val isPrivacyHidden: StateFlow<Boolean> = _isPrivacyHidden.asStateFlow()

    fun togglePrivacyHidden() {
        _isPrivacyHidden.value = !_isPrivacyHidden.value
    }

    // POS Cart State
    private val _cart = MutableStateFlow(PosCartState())
    val cart: StateFlow<PosCartState> = _cart.asStateFlow()

    private val _lastCompletedReceipt = MutableStateFlow<CompletedSaleReceipt?>(null)
    val lastCompletedReceipt: StateFlow<CompletedSaleReceipt?> = _lastCompletedReceipt.asStateFlow()

    fun clearLastReceipt() {
        _lastCompletedReceipt.value = null
    }

    private val _productsSoldIsGrid = MutableStateFlow(false)
    val productsSoldIsGrid: StateFlow<Boolean> = _productsSoldIsGrid.asStateFlow()

    private val _selectedSaleDetail = MutableStateFlow<Sale?>(null)
    val selectedSaleDetail: StateFlow<Sale?> = _selectedSaleDetail.asStateFlow()

    // Filtered sales for History
    val historyFilteredSales: StateFlow<List<Sale>> = combine(
        sales,
        _historyPeriod,
        _historyCustomDateRange,
        _historyFilter
    ) { allSales, period, customRange, filter ->
        if (filter == HistoryFilter.PURCHASES_ONLY) {
            emptyList()
        } else {
            val (startMillis, endMillis) = computeDateRange(period, customRange)
            CalculationEngine.filterSalesByDateRange(allSales, startMillis, endMillis)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Filtered purchases for History
    val historyFilteredPurchases: StateFlow<List<Purchase>> = combine(
        purchases,
        _historyPeriod,
        _historyCustomDateRange,
        _historyFilter
    ) { allPurchases, period, customRange, filter ->
        if (filter == HistoryFilter.SALES_ONLY) {
            emptyList()
        } else {
            val (startMillis, endMillis) = computeDateRange(period, customRange)
            allPurchases.filter { p ->
                val ts = p.createdAt?.toDate()?.time ?: 0L
                ts in startMillis..endMillis
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Summary for History: All totals come strictly from CalculationEngine (Rule 4)
    val historySummary: StateFlow<PeriodFinancialSummary> = combine(
        historyFilteredSales,
        historyFilteredPurchases
    ) { filteredSales, filteredPurchases ->
        val purchasesTotal = filteredPurchases.sumOf { it.totalAmount }
        CalculationEngine.calculatePeriodSummary(filteredSales, purchasesTotal = purchasesTotal)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        PeriodFinancialSummary(0.0, 0.0, 0.0, 0L, 0.0)
    )

    // Products sold metrics for History: Strictly from CalculationEngine (Rule 4)
    val historyProductsSold: StateFlow<List<ProductSoldStat>> = combine(
        historyFilteredSales,
        _historySearchQuery
    ) { filteredList, query ->
        val stats = CalculationEngine.calculateProductsSoldStats(filteredList)
        val trimmed = query.trim()
        if (trimmed.isEmpty()) stats
        else stats.filter { it.productName.contains(trimmed, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Payments in date range for History
    val historyFilteredPayments: StateFlow<List<Payment>> = combine(
        payments,
        _historyPeriod,
        _historyCustomDateRange,
        _historyFilter
    ) { allPayments, period, customRange, filter ->
        if (filter == HistoryFilter.SALES_ONLY || filter == HistoryFilter.PURCHASES_ONLY) {
            emptyList()
        } else {
            val (startMillis, endMillis) = computeDateRange(period, customRange)
            allPayments.filter { p ->
                val pDate = if (p.paymentDate > 0L) p.paymentDate else (p.createdAt?.toDate()?.time ?: 0L)
                pDate in startMillis..endMillis
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Timeline items for History (Sales + Purchases + Payment In rows, newest first)
    val historyTimelineItems: StateFlow<List<HistoryTimelineItem>> = combine(
        historyFilteredSales,
        historyFilteredPayments,
        historyFilteredPurchases,
        _historyFilter,
        _historySearchQuery
    ) { filteredSales, filteredPayments, filteredPurchases, filter, query ->
        val items = mutableListOf<HistoryTimelineItem>()
        if (filter != HistoryFilter.PURCHASES_ONLY) {
            filteredSales.forEach { items.add(HistoryTimelineItem.SaleItemRow(it)) }
        }
        if (filter == HistoryFilter.ALL) {
            filteredPayments.forEach { items.add(HistoryTimelineItem.PaymentItemRow(it)) }
        }
        if (filter != HistoryFilter.SALES_ONLY) {
            filteredPurchases.forEach { items.add(HistoryTimelineItem.PurchaseItemRow(it)) }
        }
        val trimmed = query.trim()
        val matched = if (trimmed.isEmpty()) {
            items
        } else {
            items.filter { item ->
                when (item) {
                    is HistoryTimelineItem.SaleItemRow -> {
                        val s = item.sale
                        s.invoiceNo.contains(trimmed, ignoreCase = true) ||
                            s.customerName.contains(trimmed, ignoreCase = true) ||
                            s.customerPhone.contains(trimmed, ignoreCase = true) ||
                            s.notes.contains(trimmed, ignoreCase = true) ||
                            s.accountName.contains(trimmed, ignoreCase = true) ||
                            s.items.any { it.name.contains(trimmed, ignoreCase = true) }
                    }
                    is HistoryTimelineItem.PurchaseItemRow -> {
                        val p = item.purchase
                        p.purchaseNo.contains(trimmed, ignoreCase = true) ||
                            p.supplierName.contains(trimmed, ignoreCase = true) ||
                            p.supplierMobile.contains(trimmed, ignoreCase = true) ||
                            p.note.contains(trimmed, ignoreCase = true) ||
                            p.accountName.contains(trimmed, ignoreCase = true) ||
                            p.items.any { it.name.contains(trimmed, ignoreCase = true) }
                    }
                    is HistoryTimelineItem.PaymentItemRow -> {
                        val pay = item.payment
                        pay.paymentNo.contains(trimmed, ignoreCase = true) ||
                            pay.customerName.contains(trimmed, ignoreCase = true) ||
                            pay.customerMobile.contains(trimmed, ignoreCase = true) ||
                            pay.note.contains(trimmed, ignoreCase = true) ||
                            pay.accountName.contains(trimmed, ignoreCase = true)
                    }
                    is HistoryTimelineItem.ExpenseItemRow -> {
                        item.expense.category.contains(trimmed, ignoreCase = true) ||
                            item.expense.note.contains(trimmed, ignoreCase = true) ||
                            item.expense.accountName.contains(trimmed, ignoreCase = true)
                    }
                    is HistoryTimelineItem.OtherIncomeItemRow -> {
                        item.income.category.contains(trimmed, ignoreCase = true) ||
                            item.income.note.contains(trimmed, ignoreCase = true) ||
                            item.income.accountName.contains(trimmed, ignoreCase = true)
                    }
                    is HistoryTimelineItem.AdjustmentItemRow -> {
                        item.adjustment.accountName.contains(trimmed, ignoreCase = true) ||
                            item.adjustment.note.contains(trimmed, ignoreCase = true)
                    }
                }
            }
        }
        matched.sortedByDescending { it.timestamp }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private fun computeDateRange(period: HistoryPeriod, customRange: Pair<Long, Long>?): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        return when (period) {
            HistoryPeriod.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                Pair(start, Long.MAX_VALUE)
            }
            HistoryPeriod.WEEK -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.add(Calendar.DAY_OF_YEAR, -6)
                val start = cal.timeInMillis
                Pair(start, Long.MAX_VALUE)
            }
            HistoryPeriod.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                Pair(start, Long.MAX_VALUE)
            }
            HistoryPeriod.YEAR -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                Pair(start, Long.MAX_VALUE)
            }
            HistoryPeriod.ALL -> {
                Pair(0L, Long.MAX_VALUE)
            }
            HistoryPeriod.CUSTOM -> {
                customRange ?: Pair(0L, Long.MAX_VALUE)
            }
        }
    }

    fun setHistoryPeriod(period: HistoryPeriod) {
        _historyPeriod.value = period
    }

    fun setHistoryCustomDateRange(startMillis: Long, endMillis: Long) {
        _historyCustomDateRange.value = Pair(startMillis, endMillis)
        _historyPeriod.value = HistoryPeriod.CUSTOM
    }

    fun setHistoryFilter(filter: HistoryFilter) {
        _historyFilter.value = filter
    }

    fun setHistoryViewMode(mode: HistoryViewMode) {
        _historyViewMode.value = mode
    }

    fun setHistorySearchQuery(query: String) {
        _historySearchQuery.value = query
    }

    fun toggleProductsSoldGrid() {
        _productsSoldIsGrid.value = !_productsSoldIsGrid.value
    }

    fun selectSaleDetail(sale: Sale?) {
        _selectedSaleDetail.value = sale
    }

    fun toggleLanguage() {
        val next = if (currentLanguage.value == AppLanguage.EN) AppLanguage.BN else AppLanguage.EN
        setLanguage(next)
    }

    fun setLanguage(language: AppLanguage) {
        repository.setLanguage(language)
        if (_healthProblems.value != null) {
            scanDataHealth(silent = true)
        }
    }

    // -------------------------------------------------------------
    // BUSINESS & MORE TAB NAVIGATION & SHARED PERIOD
    // -------------------------------------------------------------

    private val _activeSubNavTab = MutableStateFlow(ShopNavTab.BUSINESS)
    val activeSubNavTab: StateFlow<ShopNavTab> = _activeSubNavTab.asStateFlow()

    private val _businessNavDestination = MutableStateFlow(BusinessNavDestination.DASHBOARD)
    val businessNavDestination: StateFlow<BusinessNavDestination> = _businessNavDestination.asStateFlow()
    private val _businessNavBackStack = mutableListOf<BusinessNavDestination>()

    private val _moreNavDestination = MutableStateFlow(BusinessNavDestination.DASHBOARD)
    val moreNavDestination: StateFlow<BusinessNavDestination> = _moreNavDestination.asStateFlow()
    private val _moreNavBackStack = mutableListOf<BusinessNavDestination>()

    fun setActiveSubNavTab(tab: ShopNavTab) {
        if (tab == ShopNavTab.BUSINESS || tab == ShopNavTab.MORE) {
            _activeSubNavTab.value = tab
        }
    }

    fun resetMoreToRoot() {
        _moreNavBackStack.clear()
        _moreNavDestination.value = BusinessNavDestination.DASHBOARD
    }

    fun resetBusinessToRoot() {
        _businessNavBackStack.clear()
        _businessNavDestination.value = BusinessNavDestination.DASHBOARD
    }

    fun navigateInBusiness(destination: BusinessNavDestination) {
        if (_activeSubNavTab.value == ShopNavTab.MORE) {
            if (_moreNavDestination.value != destination) {
                _moreNavBackStack.add(_moreNavDestination.value)
                _moreNavDestination.value = destination
            }
        } else {
            if (_businessNavDestination.value != destination) {
                _businessNavBackStack.add(_businessNavDestination.value)
                _businessNavDestination.value = destination
            }
        }
    }

    fun navigateBackInBusiness(): Boolean {
        return if (_activeSubNavTab.value == ShopNavTab.MORE) {
            if (_moreNavBackStack.isNotEmpty()) {
                _moreNavDestination.value = _moreNavBackStack.removeAt(_moreNavBackStack.size - 1)
                true
            } else if (_moreNavDestination.value != BusinessNavDestination.DASHBOARD) {
                _moreNavDestination.value = BusinessNavDestination.DASHBOARD
                true
            } else {
                false
            }
        } else {
            if (_businessNavBackStack.isNotEmpty()) {
                _businessNavDestination.value = _businessNavBackStack.removeAt(_businessNavBackStack.size - 1)
                true
            } else if (_businessNavDestination.value != BusinessNavDestination.DASHBOARD) {
                _businessNavDestination.value = BusinessNavDestination.DASHBOARD
                true
            } else {
                false
            }
        }
    }

    val businessPeriod: StateFlow<HistoryPeriod> = _historyPeriod.asStateFlow()
    val businessCustomDateRange: StateFlow<Pair<Long, Long>?> = _historyCustomDateRange.asStateFlow()

    fun setBusinessPeriod(period: HistoryPeriod) {
        _historyPeriod.value = period
    }

    fun setBusinessCustomDateRange(startMillis: Long, endMillis: Long) {
        _historyCustomDateRange.value = Pair(startMillis, endMillis)
        _historyPeriod.value = HistoryPeriod.CUSTOM
    }

    // Business filtered sales: strictly calculated by CalculationEngine
    val businessFilteredSales: StateFlow<List<Sale>> = combine(
        sales,
        _historyPeriod,
        _historyCustomDateRange
    ) { allSales, period, customRange ->
        CalculationEngine.filterSalesByPeriod(allSales, period, customRange)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Business filtered purchases: strictly calculated by CalculationEngine
    val businessFilteredPurchases: StateFlow<List<Purchase>> = combine(
        purchases,
        _historyPeriod,
        _historyCustomDateRange
    ) { allPurchases, period, customRange ->
        CalculationEngine.filterPurchasesByPeriod(allPurchases, period, customRange)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Business filtered returns
    val businessFilteredReturns: StateFlow<List<PurchaseReturn>> = combine(
        purchaseReturns,
        _historyPeriod,
        _historyCustomDateRange
    ) { allReturns, period, customRange ->
        CalculationEngine.filterReturnsByPeriod(allReturns, period, customRange)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Business filtered payments
    val businessFilteredPayments: StateFlow<List<Payment>> = combine(
        payments,
        _historyPeriod,
        _historyCustomDateRange
    ) { allPayments, period, customRange ->
        CalculationEngine.filterPaymentsByPeriod(allPayments, period, customRange)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Business filtered expenses
    val businessFilteredExpenses: StateFlow<List<Expense>> = combine(
        expenses,
        _historyPeriod,
        _historyCustomDateRange
    ) { allExpenses, period, customRange ->
        CalculationEngine.filterExpensesByPeriod(allExpenses, period, customRange)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Business filtered other incomes
    val businessFilteredOtherIncomes: StateFlow<List<OtherIncome>> = combine(
        otherIncomes,
        _historyPeriod,
        _historyCustomDateRange
    ) { allIncomes, period, customRange ->
        CalculationEngine.filterOtherIncomesByPeriod(allIncomes, period, customRange)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Business filtered supplier payments
    val businessFilteredSupplierPayments: StateFlow<List<SupplierPayment>> = combine(
        supplierPayments,
        _historyPeriod,
        _historyCustomDateRange
    ) { allPayments, period, customRange ->
        CalculationEngine.filterSupplierPaymentsByPeriod(allPayments, period, customRange)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Business filtered adjustments
    val businessFilteredAdjustments: StateFlow<List<AccountAdjustment>> = combine(
        adjustments,
        _historyPeriod,
        _historyCustomDateRange
    ) { allAdjustments, period, customRange ->
        CalculationEngine.filterAdjustmentsByPeriod(allAdjustments, period, customRange)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Financial KPI summary for the period
    val businessFinancials: StateFlow<PeriodFinancialSummary> = combine(
        businessFilteredSales,
        businessFilteredPurchases
    ) { filteredSales, filteredPurchases ->
        val purchasesTotal = filteredPurchases.sumOf { it.totalAmount }
        CalculationEngine.calculatePeriodSummary(filteredSales, purchasesTotal)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        PeriodFinancialSummary(0.0, 0.0, 0.0, 0L, 0.0)
    )

    // Money Now: Stock value at cost, Baki, debt to suppliers (CalculationEngine rule 4)
    val moneyNow: StateFlow<MoneyNowSummary> = combine(
        products,
        customers,
        suppliers,
        sales,
        payments
    ) { prodList, custList, supList, saleList, payList ->
        CalculationEngine.calculateMoneyNow(prodList, custList, supList, saleList, payList)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        MoneyNowSummary(0.0, 0.0, 0.0, 0L, 0L)
    )

    // Sales chart points (bars/lines grouped by day or month)
    val salesChartPoints: StateFlow<List<DailySalesPoint>> = combine(
        businessFilteredSales,
        _historyPeriod,
        _historyCustomDateRange,
        currentLanguage
    ) { filteredSales, period, customRange, lang ->
        CalculationEngine.calculateDailySalesChart(filteredSales, period, customRange, lang)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Best-selling products (top 5 by qty with amount)
    val topSellingProducts: StateFlow<List<ProductSoldStat>> = businessFilteredSales.map { filteredSales ->
        CalculationEngine.calculateTopSellingProducts(filteredSales, 5)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Sales by Category
    val salesByCategory: StateFlow<List<CategorySalesStat>> = combine(
        businessFilteredSales,
        products
    ) { filteredSales, allProducts ->
        CalculationEngine.calculateSalesByCategory(filteredSales, allProducts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Slow Sellers: products with stock > 0 but 0 sales in the period
    val slowSellingProducts: StateFlow<List<Product>> = combine(
        products,
        businessFilteredSales
    ) { allProducts, filteredSales ->
        CalculationEngine.calculateSlowSellingProducts(allProducts, filteredSales)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Profit & Loss Stock-Based toggle
    private val _isPnlStockBased = MutableStateFlow(true)
    val isPnlStockBased: StateFlow<Boolean> = _isPnlStockBased.asStateFlow()

    fun togglePnlStockBased() {
        _isPnlStockBased.value = !_isPnlStockBased.value
    }

    // Profit & Loss statement
    val pnlStatement: StateFlow<ProfitAndLossStatement> = combine(
        combine(businessFilteredSales, businessFilteredPurchases, businessFilteredReturns) { s, p, r -> Triple(s, p, r) },
        combine(businessFilteredExpenses, businessFilteredOtherIncomes, _isPnlStockBased) { exp, inc, stockBased -> Triple(exp, inc, stockBased) }
    ) { (filteredSales, filteredPurchases, filteredReturns), (filteredExpenses, filteredOtherIncomes, stockBased) ->
        CalculationEngine.calculateProfitAndLoss(
            sales = filteredSales,
            purchases = filteredPurchases,
            returns = filteredReturns,
            expenses = filteredExpenses,
            otherIncomes = filteredOtherIncomes,
            isStockBased = stockBased
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        ProfitAndLossStatement(
            netSales = 0.0,
            costOfGoodsSold = 0.0,
            grossProfit = 0.0,
            expenses = 0.0,
            otherIncome = 0.0,
            netProfit = 0.0,
            isStockBased = true
        )
    )

    // Day Book entries
    val dayBookEntries: StateFlow<List<DayBookEntry>> = combine(
        combine(businessFilteredSales, businessFilteredPurchases, businessFilteredPayments) { s, p, pay -> Triple(s, p, pay) },
        combine(businessFilteredReturns, businessFilteredSupplierPayments, businessFilteredExpenses) { r, sp, exp -> Triple(r, sp, exp) },
        combine(businessFilteredOtherIncomes, businessFilteredAdjustments) { inc, adj -> inc to adj }
    ) { (sList, pList, payList), (rList, spList, expList), (incList, adjList) ->
        CalculationEngine.calculateDayBook(
            sales = sList,
            purchases = pList,
            paymentsIn = payList,
            returns = rList,
            supplierPayments = spList,
            expenses = expList,
            otherIncomes = incList,
            adjustments = adjList
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Customer Statement selection & calculation
    private val _selectedStatementCustomer = MutableStateFlow<Customer?>(null)
    val selectedStatementCustomer: StateFlow<Customer?> = _selectedStatementCustomer.asStateFlow()

    fun selectStatementCustomer(customer: Customer?) {
        _selectedStatementCustomer.value = customer
    }

    val customerStatementReport: StateFlow<CustomerStatementReport?> = combine(
        _selectedStatementCustomer,
        sales,
        payments,
        _historyPeriod,
        _historyCustomDateRange
    ) { cust, allSales, allPayments, period, customRange ->
        if (cust != null) {
            CalculationEngine.calculateCustomerStatement(cust, allSales, allPayments, period, customRange)
        } else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    // Supplier Statement selection & calculation
    private val _selectedStatementSupplier = MutableStateFlow<Supplier?>(null)
    val selectedStatementSupplier: StateFlow<Supplier?> = _selectedStatementSupplier.asStateFlow()

    fun selectStatementSupplier(supplier: Supplier?) {
        _selectedStatementSupplier.value = supplier
    }

    val supplierStatementReport: StateFlow<SupplierStatementReport?> = combine(
        _selectedStatementSupplier,
        purchases,
        purchaseReturns,
        _historyPeriod,
        _historyCustomDateRange
    ) { sup, allPurchases, allReturns, period, customRange ->
        if (sup != null) {
            CalculationEngine.calculateSupplierStatement(sup, allPurchases, allReturns, period, customRange)
        } else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    // Monthly Report Month Picker & Data
    private val _monthlyReportYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val monthlyReportYear: StateFlow<Int> = _monthlyReportYear.asStateFlow()

    private val _monthlyReportMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH))
    val monthlyReportMonth: StateFlow<Int> = _monthlyReportMonth.asStateFlow()

    fun setMonthlyReportMonth(year: Int, month: Int) {
        _monthlyReportYear.value = year
        _monthlyReportMonth.value = month
    }

    val monthlyReportData: StateFlow<MonthlyReportData> = combine(
        _monthlyReportYear,
        _monthlyReportMonth,
        sales,
        products,
        customers,
        suppliers,
        payments,
        currentLanguage
    ) { args: Array<Any> ->
        val year = args[0] as Int
        val month = args[1] as Int
        @Suppress("UNCHECKED_CAST")
        val saleList = args[2] as List<Sale>
        @Suppress("UNCHECKED_CAST")
        val prodList = args[3] as List<Product>
        @Suppress("UNCHECKED_CAST")
        val custList = args[4] as List<Customer>
        @Suppress("UNCHECKED_CAST")
        val supList = args[5] as List<Supplier>
        @Suppress("UNCHECKED_CAST")
        val payList = args[6] as List<Payment>
        val lang = args[7] as AppLanguage

        CalculationEngine.calculateMonthlyReportData(
            year = year,
            month = month,
            allSales = saleList,
            products = prodList,
            customers = custList,
            suppliers = supList,
            allPayments = payList,
            lang = lang
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        CalculationEngine.calculateMonthlyReportData(
            Calendar.getInstance().get(Calendar.YEAR),
            Calendar.getInstance().get(Calendar.MONTH),
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            lang = currentLanguage.value
        )
    )

    // Health Check state
    private val _healthProblems = MutableStateFlow<List<HealthProblem>?>(null)
    val healthProblems: StateFlow<List<HealthProblem>?> = _healthProblems.asStateFlow()

    private val _lastHealthScanMillis = MutableStateFlow<Long?>(null)
    val lastHealthScanMillis: StateFlow<Long?> = _lastHealthScanMillis.asStateFlow()

    private val _isScanningHealth = MutableStateFlow(false)
    val isScanningHealth: StateFlow<Boolean> = _isScanningHealth.asStateFlow()

    fun scanDataHealth(silent: Boolean = false) {
        _isScanningHealth.value = true
        viewModelScope.launch {
            try {
                val scanned = CalculationEngine.scanDataHealth(
                    store = store.value,
                    products = products.value,
                    sales = sales.value,
                    purchases = purchases.value,
                    customers = customers.value,
                    suppliers = suppliers.value,
                    payments = payments.value,
                    accounts = accounts.value,
                    accountBalances = accountBalances.value,
                    expenses = expenses.value,
                    otherIncomes = otherIncomes.value,
                    supplierPayments = supplierPayments.value,
                    adjustments = adjustments.value,
                    lang = currentLanguage.value
                )
                _healthProblems.value = scanned
                _lastHealthScanMillis.value = System.currentTimeMillis()
                if (!silent) {
                    _statusMessage.value = if (scanned.isEmpty()) tr("scan_complete_clean") else tr("found_anomalies_msg", scanned.size)
                }
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Health scan error", e)
                if (!silent) {
                    _errorMessage.value = e.localizedMessage ?: tr("failed_scan_db")
                }
            } finally {
                _isScanningHealth.value = false
            }
        }
    }

    fun applyHealthFix(problem: HealthProblem, ownerUid: String, onSuccess: () -> Unit) {
        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                repository.applyHealthFix(storeId, problem, ownerUid)
                _healthProblems.value = _healthProblems.value?.filter { it.id != problem.id }
                _statusMessage.value = tr("fix_applied_success")
                onSuccess()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Health fix error", e)
                _errorMessage.value = e.localizedMessage ?: tr("failed_apply_fix")
            } finally {
                _isBusy.value = false
            }
        }
    }

    // -------------------------------------------------------------
    // PRODUCT ACTIONS (ADD, EDIT, DELETE, SAFE STOCK INCREMENT)
    // -------------------------------------------------------------

    fun addNewProductWizard(
        name: String,
        nameBn: String,
        category: String,
        unit: String,
        buyPrice: Double,
        sellPrice: Double,
        stock: Double,
        lowStockLimit: Double,
        photoUri: String,
        barcode: String = "",
        wholesalePrice: Double = 0.0,
        mrp: Double = 0.0,
        itemLocation: String = "",
        partyRate: Double = 0.0,
        buyUnit: String = unit,
        buyConversion: Double = 1.0,
        sellUnits: Map<String, Double> = emptyMap(),
        onSuccess: () -> Unit
    ) {
        if (name.isBlank()) {
            _errorMessage.value = tr("product_name_required")
            return
        }
        if (buyPrice < 0.0 || sellPrice < 0.0 || wholesalePrice < 0.0 || mrp < 0.0 || partyRate < 0.0 || stock < 0.0 || lowStockLimit < 0.0) {
            _errorMessage.value = tr("prices_non_negative")
            return
        }

        _isBusy.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val stockUnit = unit.trim().ifEmpty { "pcs" }
                val effectiveBuyU = buyUnit.trim().ifEmpty { stockUnit }
                val newProduct = Product(
                    storeId = storeId,
                    name = name.trim(),
                    nameBn = nameBn.trim(),
                    barcode = barcode.trim(),
                    category = category.trim().ifEmpty { "General" },
                    unit = stockUnit,
                    buyUnit = effectiveBuyU,
                    buyConversion = if (effectiveBuyU.equals(stockUnit, ignoreCase = true) || buyConversion <= 0.0) 1.0 else buyConversion,
                    sellUnits = sellUnits,
                    buyPrice = buyPrice,
                    sellPrice = sellPrice,
                    wholesalePrice = wholesalePrice,
                    mrp = mrp,
                    itemLocation = itemLocation.trim(),
                    partyRate = partyRate,
                    stock = CalculationEngine.roundStock(stock),
                    lowStockLimit = CalculationEngine.roundStock(lowStockLimit),
                    photoUri = photoUri.trim()
                )
                val outcome = repository.addProduct(storeId, newProduct)
                _statusMessage.value = if (outcome.photoPendingUpload) {
                    outcome.uploadWarning ?: tr("photo_pending_msg", name.trim())
                } else {
                    tr("product_added_msg", name.trim())
                }
                onSuccess()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Failed to add product", e)
                _errorMessage.value = e.localizedMessage ?: tr("failed_to_save_product")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun updateProductWizard(
        productId: String,
        name: String,
        nameBn: String,
        category: String,
        unit: String,
        buyPrice: Double,
        sellPrice: Double,
        stock: Double,
        lowStockLimit: Double,
        photoUri: String,
        barcode: String = "",
        wholesalePrice: Double = 0.0,
        mrp: Double = 0.0,
        itemLocation: String = "",
        partyRate: Double = 0.0,
        buyUnit: String? = null,
        buyConversion: Double? = null,
        sellUnits: Map<String, Double>? = null,
        onSuccess: () -> Unit
    ) {
        if (productId.isBlank()) return
        if (name.isBlank()) {
            _errorMessage.value = tr("product_name_required")
            return
        }
        if (buyPrice < 0.0 || sellPrice < 0.0 || wholesalePrice < 0.0 || mrp < 0.0 || partyRate < 0.0 || stock < 0.0 || lowStockLimit < 0.0) {
            _errorMessage.value = tr("prices_non_negative")
            return
        }

        _isBusy.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val existingProduct = products.value.find { it.id == productId }
                val safeBuyPrice = if (canSeeBuyingPriceAndProfit()) {
                    buyPrice
                } else {
                    existingProduct?.buyPrice ?: buyPrice
                }
                val preserveDetailedFields = isSimpleMode.value && existingProduct != null
                val stockUnit = unit.trim().ifEmpty { "pcs" }
                val effectiveBuyU = (buyUnit ?: existingProduct?.buyUnit ?: stockUnit).trim().ifEmpty { stockUnit }
                val rawBuyConv = buyConversion ?: existingProduct?.buyConversion ?: 1.0
                val effectiveSellUnits = sellUnits ?: existingProduct?.sellUnits ?: emptyMap()
                val updated = Product(
                    id = productId,
                    storeId = storeId,
                    name = name.trim(),
                    nameBn = nameBn.trim(),
                    barcode = if (preserveDetailedFields) existingProduct!!.barcode else barcode.trim(),
                    category = category.trim().ifEmpty { "General" },
                    unit = stockUnit,
                    buyUnit = effectiveBuyU,
                    buyConversion = if (effectiveBuyU.equals(stockUnit, ignoreCase = true) || rawBuyConv <= 0.0) 1.0 else rawBuyConv,
                    sellUnits = effectiveSellUnits,
                    buyPrice = safeBuyPrice,
                    sellPrice = sellPrice,
                    wholesalePrice = if (preserveDetailedFields) existingProduct!!.wholesalePrice else wholesalePrice,
                    mrp = if (preserveDetailedFields) existingProduct!!.mrp else mrp,
                    itemLocation = if (preserveDetailedFields) existingProduct!!.itemLocation else itemLocation.trim(),
                    partyRate = if (preserveDetailedFields) existingProduct!!.partyRate else partyRate,
                    stock = CalculationEngine.roundStock(stock),
                    lowStockLimit = CalculationEngine.roundStock(lowStockLimit),
                    photoUri = photoUri.trim()
                )
                val outcome = repository.updateProduct(
                    storeId = storeId,
                    product = updated,
                    previousPhotoUri = existingProduct?.photoUri ?: ""
                )
                _statusMessage.value = if (outcome.photoPendingUpload) {
                    outcome.uploadWarning ?: tr("photo_pending_msg", name.trim())
                } else {
                    tr("product_updated_msg", name.trim())
                }
                onSuccess()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Failed to update product", e)
                _errorMessage.value = e.localizedMessage ?: tr("failed_to_update_product")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun deleteProduct(productId: String, onSuccess: () -> Unit) {
        if (productId.isBlank()) return
        _isBusy.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val existingPhoto = products.value.find { it.id == productId }?.photoUri ?: ""
                repository.deleteProduct(storeId, productId, existingPhoto)
                _statusMessage.value = tr("product_deleted_msg")
                onSuccess()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Failed to delete product", e)
                _errorMessage.value = e.localizedMessage ?: tr("failed_to_delete_product")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun addQuickStock(productId: String, additionalStock: Double) {
        if (additionalStock <= 0.0) return
        _isBusy.value = true
        viewModelScope.launch {
            try {
                repository.addStock(storeId, productId, additionalStock)
                _statusMessage.value = tr("stock_added_msg", additionalStock)
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Failed to increment stock", e)
                _errorMessage.value = e.localizedMessage ?: tr("failed_to_update_stock")
            } finally {
                _isBusy.value = false
            }
        }
    }

    // -------------------------------------------------------------
    // POS CART & CHECKOUT (CALCULATION ENGINE ENFORCED)
    // -------------------------------------------------------------

    fun cartLineKey(productId: String, unit: String): String {
        val cleanUnit = unit.trim().ifBlank { "pcs" }.lowercase()
        return "${productId}__$cleanUnit"
    }

    private fun totalStockInCartForProduct(
        items: Map<String, SaleItem>,
        productId: String,
        excludingKeys: Set<String> = emptySet()
    ): Double {
        return CalculationEngine.roundStock(
            items.entries
                .filter { it.key !in excludingKeys && it.value.productId == productId }
                .sumOf { CalculationEngine.saleItemStockQty(it.value) }
        )
    }

    fun addProductToCart(
        product: Product,
        qtyToAdd: Double = 1.0,
        unit: String = CalculationEngine.effectiveStockUnit(product)
    ) {
        if (product.stock <= 0.0) {
            _errorMessage.value = tr("item_out_of_stock")
            return
        }
        if (qtyToAdd <= 0.0) return

        val chosenUnit = unit.trim().ifBlank { CalculationEngine.effectiveStockUnit(product) }
        val key = cartLineKey(product.id, chosenUnit)
        val currentMap = _cart.value.items.toMutableMap()
        val existing = currentMap[key]

        val newQty = CalculationEngine.roundStock((existing?.qty ?: 0.0) + qtyToAdd)
        val updatedItem = CalculationEngine.buildSaleItem(
            product = product,
            qty = newQty,
            unit = chosenUnit,
            requestedDiscount = existing?.discount ?: 0.0
        )
        val otherStock = totalStockInCartForProduct(currentMap, product.id, excludingKeys = setOf(key))
        val totalRequestedStock = CalculationEngine.roundStock(otherStock + CalculationEngine.saleItemStockQty(updatedItem))

        if (!CalculationEngine.isStockSufficient(product.stock, totalRequestedStock)) {
            val unitDisp = ShopLocale.displayUnit(product.unit, currentLanguage.value)
            _errorMessage.value = tr("cannot_add_more_stock", product.stock, unitDisp)
            return
        }

        currentMap[key] = updatedItem
        recalculateCart(currentMap, _cart.value.invoiceDiscount)
    }

    fun addSecondaryUnitLineToCart(product: Product) {
        if (product.stock <= 0.0) {
            _errorMessage.value = tr("item_out_of_stock")
            return
        }
        val currentMap = _cart.value.items
        val usedUnits = currentMap.values
            .filter { it.productId == product.id }
            .map { it.unit.trim().lowercase() }
            .toSet()
        val nextOption = CalculationEngine.availableSellUnits(product)
            .firstOrNull { it.first.trim().lowercase() !in usedUnits }
            ?: return

        val otherStock = totalStockInCartForProduct(currentMap, product.id)
        val remainingStock = CalculationEngine.roundStock((product.stock - otherStock).coerceAtLeast(0.0))
        val maxInNextUnit = CalculationEngine.maxSellableQtyInUnit(remainingStock, nextOption.second)
        if (maxInNextUnit <= 0.0) {
            val unitDisp = ShopLocale.displayUnit(product.unit, currentLanguage.value)
            _errorMessage.value = tr("cannot_add_more_stock", product.stock, unitDisp)
            return
        }
        val initialQty = if (maxInNextUnit >= 1.0) 1.0 else maxInNextUnit
        addProductToCart(product, qtyToAdd = initialQty, unit = nextOption.first)
    }

    fun updateCartItemLine(
        lineKey: String,
        product: Product,
        newQty: Double,
        newUnit: String? = null
    ) {
        val currentMap = _cart.value.items.toMutableMap()
        val existing = currentMap[lineKey]
        if (newQty <= 0.0) {
            currentMap.remove(lineKey)
            recalculateCart(currentMap, _cart.value.invoiceDiscount)
            return
        }

        val targetUnit = (newUnit ?: existing?.unit ?: product.unit)
            .trim()
            .ifBlank { CalculationEngine.effectiveStockUnit(product) }
        val targetKey = cartLineKey(product.id, targetUnit)
        val mergeExisting = if (targetKey != lineKey) currentMap[targetKey] else null
        val finalQty = if (mergeExisting != null) {
            CalculationEngine.roundStock(mergeExisting.qty + newQty)
        } else {
            CalculationEngine.roundStock(newQty)
        }
        val combinedDiscount = (existing?.discount ?: 0.0) + (mergeExisting?.discount ?: 0.0)

        val updated = CalculationEngine.buildSaleItem(
            product = product,
            qty = finalQty,
            unit = targetUnit,
            requestedDiscount = combinedDiscount
        )
        val otherStock = totalStockInCartForProduct(
            currentMap,
            product.id,
            excludingKeys = setOf(lineKey, targetKey)
        )
        val totalRequestedStock = CalculationEngine.roundStock(otherStock + CalculationEngine.saleItemStockQty(updated))

        if (!CalculationEngine.isStockSufficient(product.stock, totalRequestedStock)) {
            val unitDisp = ShopLocale.displayUnit(product.unit, currentLanguage.value)
            _errorMessage.value = tr("cannot_exceed_stock", product.stock, unitDisp)
            return
        }

        if (targetKey != lineKey) {
            currentMap.remove(lineKey)
        }
        currentMap[targetKey] = updated
        recalculateCart(currentMap, _cart.value.invoiceDiscount)
    }

    fun updateCartItemQty(lineKey: String, product: Product, newQty: Double) {
        updateCartItemLine(lineKey = lineKey, product = product, newQty = newQty, newUnit = null)
    }

    fun setInvoiceDiscount(discount: Double): Boolean {
        val currentSubtotal = _cart.value.subtotal
        if (discount < 0.0) {
            _errorMessage.value = tr("discount_negative_error")
            return false
        }
        if (discount > currentSubtotal) {
            _errorMessage.value = tr("discount_exceed_subtotal")
            return false
        }
        _errorMessage.value = null
        recalculateCart(_cart.value.items, discount)
        return true
    }

    fun setPaymentType(paymentType: String) {
        _cart.value = _cart.value.copy(paymentType = paymentType)
    }

    fun setCustomer(name: String, phone: String, id: String = "") {
        _cart.value = _cart.value.copy(customerId = id, customerName = name, customerPhone = phone)
    }

    fun selectCustomerForCheckout(customer: Customer) {
        _cart.value = _cart.value.copy(
            customerId = customer.id,
            customerName = customer.name,
            customerPhone = customer.mobile
        )
    }

    fun setCashSaleForCheckout() {
        _cart.value = _cart.value.copy(
            customerId = "",
            customerName = "Cash Sale",
            customerPhone = ""
        )
    }

    fun setNotes(notes: String) {
        _cart.value = _cart.value.copy(notes = notes)
    }

    fun clearCart() {
        _cart.value = PosCartState()
    }

    private fun recalculateCart(items: Map<String, SaleItem>, invoiceDiscount: Double) {
        var totalItems = 0.0
        var subtotal = 0.0
        var lineDiscounts = 0.0
        var totalProfit = 0.0

        for (item in items.values) {
            totalItems += CalculationEngine.saleItemStockQty(item)
            subtotal += CalculationEngine.calculateLineGrossTotal(item.sellPrice, item.qty)
            lineDiscounts += item.discount
            totalProfit += item.profit
        }

        val boundedInvDiscount = invoiceDiscount.coerceIn(0.0, (subtotal - lineDiscounts).coerceAtLeast(0.0))
        val totalDiscount = CalculationEngine.roundMoney(lineDiscounts + boundedInvDiscount)
        val grandTotal = CalculationEngine.roundMoney((subtotal - totalDiscount).coerceAtLeast(0.0))
        val projectedProfit = CalculationEngine.roundMoney(totalProfit - boundedInvDiscount)

        _cart.value = _cart.value.copy(
            items = items,
            totalItems = CalculationEngine.roundStock(totalItems),
            subtotal = CalculationEngine.roundMoney(subtotal),
            totalDiscount = totalDiscount,
            invoiceDiscount = boundedInvDiscount,
            grandTotal = grandTotal,
            projectedProfit = projectedProfit
        )
    }

    fun updateCartAccount(accountId: String, accountName: String) {
        _cart.value = _cart.value.copy(accountId = accountId, accountName = accountName)
        updateLastUsedAccount(accountId)
    }

    fun checkoutCurrentCart(onSuccess: (CompletedSaleReceipt) -> Unit) {
        val cartState = _cart.value
        val itemsList = cartState.items.values.toList()
        if (itemsList.isEmpty()) {
            _errorMessage.value = tr("cart_empty_error")
            return
        }

        // Baki is strictly blocked for Cash Sale with the exact clear message
        if (cartState.paymentType == "baki") {
            val isCashSale = cartState.customerName.trim().equals("Cash Sale", ignoreCase = true) ||
                    cartState.customerName.trim().isBlank()
            if (isCashSale) {
                _errorMessage.value = tr("choose_customer_for_baki")
                return
            }
        }

        _isBusy.value = true
        _errorMessage.value = null

        val simpleModeActive = isSimpleMode.value
        val effectiveAccountId = if (simpleModeActive) "cash_in_hand" else cartState.accountId
        val effectiveAccountName = if (simpleModeActive) "Cash in Hand" else cartState.accountName

        viewModelScope.launch {
            try {
                val invoiceNumber = repository.executeSaleCheckout(
                    storeId = storeId,
                    items = itemsList,
                    invoiceDiscount = cartState.invoiceDiscount,
                    paymentType = cartState.paymentType,
                    customerId = cartState.customerId,
                    customerName = cartState.customerName,
                    customerPhone = cartState.customerPhone,
                    notes = cartState.notes,
                    accountId = effectiveAccountId,
                    accountName = effectiveAccountName
                )
                if (cartState.paymentType != "baki") {
                    updateLastUsedAccount(effectiveAccountId)
                }

                val curStore = store.value
                val receipt = CompletedSaleReceipt(
                    invoiceNo = invoiceNumber,
                    storeName = curStore?.name?.ifBlank { "StoreOS" } ?: "StoreOS",
                    storePhone = curStore?.phone ?: "",
                    storeAddress = curStore?.address ?: "",
                    items = itemsList,
                    totalItems = cartState.totalItems,
                    subtotal = cartState.subtotal,
                    discount = cartState.totalDiscount,
                    grandTotal = cartState.grandTotal,
                    paymentType = cartState.paymentType,
                    customerName = cartState.customerName.trim().ifEmpty { "Cash Sale" },
                    customerPhone = cartState.customerPhone,
                    notes = cartState.notes,
                    timestamp = System.currentTimeMillis()
                )

                _lastCompletedReceipt.value = receipt
                _cart.value = PosCartState()
                _statusMessage.value = tr("invoice_completed_msg", invoiceNumber)
                onSuccess(receipt)
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Checkout transaction failed", e)
                _errorMessage.value = e.localizedMessage ?: tr("checkout_failed_msg")
            } finally {
                _isBusy.value = false
            }
        }
    }

    // -------------------------------------------------------------
    // CUSTOMER MANAGEMENT & PAYMENT METHODS
    // -------------------------------------------------------------

    fun setBusinessSubScreen(screen: BusinessSubScreen) {
        _businessSubScreen.value = screen
    }

    fun openCustomerDetails(customer: Customer) {
        _selectedCustomer.value = customer
        _businessSubScreen.value = BusinessSubScreen.CUSTOMER_DETAILS
    }

    fun closeCustomerDetails() {
        _selectedCustomer.value = null
        _businessSubScreen.value = BusinessSubScreen.BAKI_HUB
    }

    fun addCustomer(
        name: String,
        mobile: String,
        address: String,
        notes: String,
        onSuccess: (Customer) -> Unit
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            _errorMessage.value = tr("customer_name_required")
            return
        }
        val trimmedMobile = mobile.trim()
        if (trimmedMobile.isNotBlank()) {
            val duplicate = customers.value.find { it.mobile.isNotBlank() && it.mobile.trim() == trimmedMobile }
            if (duplicate != null) {
                _statusMessage.value = tr("duplicate_phone_warning", trimmedMobile, duplicate.name)
            }
        }

        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val newCust = Customer(
                    storeId = storeId,
                    name = trimmedName,
                    mobile = trimmedMobile,
                    address = address.trim(),
                    notes = notes.trim(),
                    currentBalance = 0.0
                )
                val newId = repository.addCustomer(storeId, newCust)
                val createdCust = newCust.copy(id = newId)
                _statusMessage.value = tr("customer_added_msg", trimmedName)
                onSuccess(createdCust)
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Add customer failed", e)
                _errorMessage.value = e.localizedMessage ?: tr("failed_add_customer")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun updateCustomer(customer: Customer, onSuccess: () -> Unit) {
        if (customer.name.isBlank()) {
            _errorMessage.value = tr("customer_name_required")
            return
        }
        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                repository.updateCustomer(storeId, customer)
                if (_selectedCustomer.value?.id == customer.id) {
                    _selectedCustomer.value = customer
                }
                _statusMessage.value = tr("customer_updated_msg")
                onSuccess()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Update customer failed", e)
                _errorMessage.value = e.localizedMessage ?: tr("failed_update_customer")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun deleteCustomer(customer: Customer, onSuccess: () -> Unit) {
        val summary = CalculationEngine.calculateCustomerSummary(customer, sales.value, payments.value)
        if (!CalculationEngine.canDeleteCustomer(summary)) {
            _errorMessage.value = tr("cannot_delete_customer_active")
            return
        }
        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                repository.deleteCustomer(storeId, customer.id)
                if (_selectedCustomer.value?.id == customer.id) {
                    _selectedCustomer.value = null
                    _businessSubScreen.value = BusinessSubScreen.BAKI_HUB
                }
                _statusMessage.value = tr("customer_deleted_msg")
                onSuccess()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Delete customer failed", e)
                _errorMessage.value = e.localizedMessage ?: tr("failed_delete_customer")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun recordCustomerPayment(
        customer: Customer,
        amount: Double,
        note: String,
        paymentDate: Long,
        accountId: String = "cash_in_hand",
        accountName: String = "Cash in Hand",
        onSuccess: (String) -> Unit
    ) {
        if (amount <= 0.0) {
            _errorMessage.value = tr("payment_positive_error")
            return
        }
        val summary = CalculationEngine.calculateCustomerSummary(customer, sales.value, payments.value)
        if (amount > summary.currentBalance + 0.001) {
            val lang = currentLanguage.value
            _errorMessage.value = tr(
                "payment_exceeds_baki",
                ShopLocale.formatMoney(amount, lang),
                ShopLocale.formatMoney(summary.currentBalance, lang)
            )
            return
        }

        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val paymentNo = repository.recordCustomerPaymentAtomic(
                    storeId = storeId,
                    customerId = customer.id,
                    customerName = customer.name,
                    customerMobile = customer.mobile,
                    amount = amount,
                    note = note,
                    paymentDate = paymentDate,
                    accountId = accountId,
                    accountName = accountName
                )
                updateLastUsedAccount(accountId)
                _statusMessage.value = tr("payment_recorded_msg", paymentNo)
                onSuccess(paymentNo)
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Record payment failed", e)
                _errorMessage.value = e.localizedMessage ?: tr("payment_failed_msg")
            } finally {
                _isBusy.value = false
            }
        }
    }

    // -------------------------------------------------------------
    // SUPPLIERS & PURCHASES ACTIONS
    // -------------------------------------------------------------

    fun addSupplier(
        name: String,
        mobile: String,
        company: String,
        address: String,
        onSuccess: (Supplier) -> Unit
    ) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            _errorMessage.value = tr("supplier_name_required")
            return
        }
        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val newSup = Supplier(
                    storeId = storeId,
                    name = trimmed,
                    mobile = mobile.trim(),
                    company = company.trim(),
                    address = address.trim(),
                    balance = 0.0
                )
                val id = repository.addSupplier(storeId, newSup)
                _statusMessage.value = tr("supplier_added_msg", trimmed)
                onSuccess(newSup.copy(id = id))
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Add supplier failed", e)
                _errorMessage.value = e.localizedMessage ?: tr("failed_add_supplier")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun updateSupplier(supplier: Supplier, onSuccess: () -> Unit) {
        if (supplier.name.isBlank()) {
            _errorMessage.value = tr("supplier_name_required")
            return
        }
        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                repository.updateSupplier(storeId, supplier)
                _statusMessage.value = tr("supplier_updated_msg")
                onSuccess()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Update supplier failed", e)
                _errorMessage.value = e.localizedMessage ?: tr("failed_update_supplier")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun deleteSupplier(supplierId: String, onSuccess: () -> Unit) {
        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                repository.deleteSupplier(storeId, supplierId)
                _statusMessage.value = tr("supplier_deleted_msg")
                onSuccess()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Delete supplier failed", e)
                _errorMessage.value = e.localizedMessage ?: tr("failed_delete_supplier")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun recordPurchase(
        supplier: Supplier,
        items: List<PurchaseItem>,
        paidAmount: Double,
        note: String,
        accountId: String = "cash_in_hand",
        accountName: String = "Cash in Hand",
        onSuccess: (String) -> Unit
    ) {
        if (items.isEmpty()) {
            _errorMessage.value = tr("purchase_min_one_item")
            return
        }
        val totalAmount = CalculationEngine.roundMoney(items.sumOf { it.lineTotal.takeIf { lt -> lt > 0.0 } ?: (it.buyPrice * it.qty) })
        val boundedPaid = paidAmount.coerceIn(0.0, totalAmount)
        val dueAmount = CalculationEngine.roundMoney((totalAmount - boundedPaid).coerceAtLeast(0.0))
        val paymentType = if (dueAmount > 0.001) "credit" else "paid"

        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val newPur = Purchase(
                    storeId = storeId,
                    supplierId = supplier.id,
                    supplierName = supplier.name,
                    supplierMobile = supplier.mobile,
                    items = items,
                    totalItems = CalculationEngine.roundStock(items.sumOf { CalculationEngine.purchaseItemStockQty(it) }),
                    totalAmount = totalAmount,
                    paidAmount = boundedPaid,
                    dueAmount = dueAmount,
                    paymentType = paymentType,
                    accountId = if (boundedPaid > 0.0) accountId else "",
                    accountName = if (boundedPaid > 0.0) accountName else "",
                    note = note.trim()
                )
                val purNo = repository.recordPurchase(storeId, newPur)
                if (boundedPaid > 0.0) {
                    updateLastUsedAccount(accountId)
                }
                _statusMessage.value = tr("purchase_recorded_msg", purNo)
                onSuccess(purNo)
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Record purchase failed", e)
                _errorMessage.value = e.localizedMessage ?: tr("failed_record_purchase")
            } finally {
                _isBusy.value = false
            }
        }
    }

    // -------------------------------------------------------------
    // ACCOUNTS & BALANCE ADJUSTMENTS ACTIONS
    // -------------------------------------------------------------

    fun createAccount(
        name: String,
        type: String,
        accountNumber: String,
        openingBalance: Double,
        onSuccess: (String) -> Unit
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            _errorMessage.value = tr("account_name_required")
            return
        }
        if (accounts.value.any { it.name.trim().equals(trimmedName, ignoreCase = true) }) {
            _errorMessage.value = tr("account_already_exists")
            return
        }

        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val newAcc = Account(
                    storeId = storeId,
                    name = trimmedName,
                    type = type,
                    accountNumber = accountNumber.trim(),
                    openingBalance = openingBalance
                )
                val id = repository.createAccount(newAcc)
                updateLastUsedAccount(id)
                _statusMessage.value = tr("account_created_msg", trimmedName)
                onSuccess(id)
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Create account failed", e)
                _errorMessage.value = e.localizedMessage ?: tr("failed_create_account")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun archiveAccount(account: Account, onSuccess: () -> Unit) {
        if (account.isDefault || account.id == "cash_in_hand") {
            _errorMessage.value = tr("cannot_archive_default_account")
            return
        }
        val currentBalance = accountBalances.value[account.id] ?: account.openingBalance
        if (kotlin.math.abs(currentBalance) > 0.001) {
            _errorMessage.value = tr(
                "archive_zero_balance_only",
                ShopLocale.formatMoneyFixed2(currentBalance, currentLanguage.value)
            )
            return
        }

        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                repository.archiveAccount(storeId, account.id, currentBalance)
                val dispName = ShopLocale.displayAccountName(account.name, account.id, currentLanguage.value)
                _statusMessage.value = tr("account_archived_msg", dispName)
                onSuccess()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Archive account failed", e)
                _errorMessage.value = e.localizedMessage ?: tr("failed_archive_account")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun adjustAccountBalance(
        account: Account,
        newBalance: Double,
        note: String,
        onSuccess: () -> Unit
    ) {
        val noteTrimmed = note.trim()
        if (noteTrimmed.isBlank()) {
            _errorMessage.value = tr("adjustment_note_required")
            return
        }
        val currentBalance = accountBalances.value[account.id] ?: account.openingBalance
        val difference = newBalance - currentBalance

        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val adjustment = AccountAdjustment(
                    storeId = storeId,
                    accountId = account.id,
                    accountName = account.name,
                    oldBalance = currentBalance,
                    newBalance = newBalance,
                    difference = difference,
                    note = noteTrimmed
                )
                repository.recordAccountAdjustment(adjustment)
                val lang = currentLanguage.value
                val dispName = ShopLocale.displayAccountName(account.name, account.id, lang)
                val diffStr = ShopLocale.formatSignedMoney(difference, lang)
                _statusMessage.value = tr("balance_adjusted_msg", dispName, diffStr)
                onSuccess()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Adjust balance failed", e)
                _errorMessage.value = e.localizedMessage ?: tr("failed_adjust_balance")
            } finally {
                _isBusy.value = false
            }
        }
    }

    // -------------------------------------------------------------
    // EXPENSES & OTHER INCOMES ACTIONS
    // -------------------------------------------------------------

    fun recordExpense(
        amount: Double,
        category: String,
        date: Long,
        accountId: String,
        accountName: String,
        note: String,
        receiptUri: String = "",
        onSuccess: () -> Unit
    ) {
        if (amount <= 0.0) {
            _errorMessage.value = tr("expense_amount_positive")
            return
        }
        val effectiveAccountId = accountId.ifBlank { "cash_in_hand" }
        val effectiveAccountName = accountName.ifBlank { "Cash in Hand" }

        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val exp = Expense(
                    storeId = storeId,
                    amount = amount,
                    category = category.ifBlank { "Other" },
                    accountId = effectiveAccountId,
                    accountName = effectiveAccountName,
                    date = if (date > 0L) date else System.currentTimeMillis(),
                    note = note.trim(),
                    receiptUri = receiptUri
                )
                repository.recordExpense(exp)
                updateLastUsedAccount(effectiveAccountId)
                _statusMessage.value = tr("expense_recorded_msg", ShopLocale.formatMoneyFixed2(amount, currentLanguage.value))
                onSuccess()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Record expense failed", e)
                _errorMessage.value = e.localizedMessage ?: tr("failed_record_expense")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun deleteExpense(expense: Expense, note: String, onSuccess: () -> Unit) {
        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                repository.deleteExpense(storeId, expense.id, note)
                _statusMessage.value = tr("expense_deleted_msg")
                onSuccess()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Delete expense failed", e)
                _errorMessage.value = e.localizedMessage ?: tr("failed_delete_expense")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun recordOtherIncome(
        amount: Double,
        category: String,
        date: Long,
        accountId: String,
        accountName: String,
        note: String,
        onSuccess: () -> Unit
    ) {
        if (amount <= 0.0) {
            _errorMessage.value = tr("income_amount_positive")
            return
        }
        val effectiveAccountId = accountId.ifBlank { "cash_in_hand" }
        val effectiveAccountName = accountName.ifBlank { "Cash in Hand" }

        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val inc = OtherIncome(
                    storeId = storeId,
                    amount = amount,
                    category = category.ifBlank { "Other" },
                    accountId = effectiveAccountId,
                    accountName = effectiveAccountName,
                    date = if (date > 0L) date else System.currentTimeMillis(),
                    note = note.trim()
                )
                repository.recordOtherIncome(inc)
                updateLastUsedAccount(effectiveAccountId)
                _statusMessage.value = tr("income_recorded_msg", ShopLocale.formatMoneyFixed2(amount, currentLanguage.value))
                onSuccess()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Record other income failed", e)
                _errorMessage.value = e.localizedMessage ?: tr("failed_record_income")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun deleteOtherIncome(income: OtherIncome, note: String, onSuccess: () -> Unit) {
        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                repository.deleteOtherIncome(storeId, income.id, note)
                _statusMessage.value = tr("income_deleted_msg")
                onSuccess()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Delete other income failed", e)
                _errorMessage.value = e.localizedMessage ?: tr("failed_delete_income")
            } finally {
                _isBusy.value = false
            }
        }
    }

    // -------------------------------------------------------------
    // CATEGORIES & STORE NOTES ACTIONS
    // -------------------------------------------------------------

    fun createCategory(name: String, type: String, onSuccess: () -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            _errorMessage.value = tr("category_name_required")
            return
        }
        if (categories.value.any { it.type == type && it.name.trim().equals(trimmed, ignoreCase = true) }) {
            _errorMessage.value = tr("category_exists")
            return
        }

        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val cat = StoreCategory(storeId = storeId, type = type, name = trimmed)
                repository.createCategory(cat)
                _statusMessage.value = tr("category_added_msg", trimmed)
                onSuccess()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Create category failed", e)
                _errorMessage.value = e.localizedMessage ?: tr("failed_add_category")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun updateCategory(category: StoreCategory, newName: String, onSuccess: () -> Unit) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            _errorMessage.value = tr("category_name_required")
            return
        }

        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                repository.updateCategory(storeId, category.id, trimmed)
                _statusMessage.value = tr("category_renamed_msg", trimmed)
                onSuccess()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Update category failed", e)
                _errorMessage.value = e.localizedMessage ?: tr("failed_update_category")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun archiveCategory(category: StoreCategory, onSuccess: () -> Unit) {
        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                repository.archiveCategory(storeId, category.id)
                _statusMessage.value = tr("category_archived_msg", ShopLocale.displayCategory(category.name, currentLanguage.value))
                onSuccess()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Archive category failed", e)
                _errorMessage.value = e.localizedMessage ?: tr("failed_archive_category")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun saveNote(id: String, title: String, text: String, date: Long, onSuccess: () -> Unit) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) {
            _errorMessage.value = tr("note_title_required")
            return
        }

        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val note = StoreNote(
                    id = id,
                    storeId = storeId,
                    title = trimmedTitle,
                    text = text.trim(),
                    date = if (date > 0L) date else System.currentTimeMillis()
                )
                repository.saveNote(note)
                _statusMessage.value = if (id.isBlank()) tr("note_saved_msg") else tr("note_updated_msg")
                onSuccess()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Save note failed", e)
                _errorMessage.value = e.localizedMessage ?: tr("failed_save_note")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun deleteNote(noteId: String, onSuccess: () -> Unit) {
        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                repository.deleteNote(storeId, noteId)
                _statusMessage.value = tr("note_deleted_msg")
                onSuccess()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Delete note failed", e)
                _errorMessage.value = e.localizedMessage ?: tr("failed_delete_note")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun recordSupplierPayment(
        supplier: Supplier,
        amount: Double,
        accountId: String,
        accountName: String,
        note: String,
        date: Long,
        onSuccess: () -> Unit
    ) {
        if (amount <= 0.0) {
            _errorMessage.value = tr("payment_positive_error")
            return
        }
        val effectiveAccountId = accountId.ifBlank { "cash_in_hand" }
        val effectiveAccountName = accountName.ifBlank { "Cash in Hand" }

        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val sp = SupplierPayment(
                    storeId = storeId,
                    supplierId = supplier.id,
                    supplierName = supplier.name,
                    amount = amount,
                    accountId = effectiveAccountId,
                    accountName = effectiveAccountName,
                    note = note.trim(),
                    paymentDate = if (date > 0L) date else System.currentTimeMillis()
                )
                repository.recordSupplierPayment(sp)
                updateLastUsedAccount(effectiveAccountId)
                _statusMessage.value = tr(
                    "supplier_payment_recorded_msg",
                    ShopLocale.formatMoneyFixed2(amount, currentLanguage.value),
                    supplier.name
                )
                onSuccess()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Record supplier payment failed", e)
                _errorMessage.value = e.localizedMessage ?: tr("failed_record_payment")
            } finally {
                _isBusy.value = false
            }
        }
    }

    // -------------------------------------------------------------
    // STAFF MANAGEMENT & REPORTS
    // -------------------------------------------------------------

    val staffReport: StateFlow<com.example.domain.StaffReportSummary> = combine(
        combine(members, store, sales) { mems, st, sList -> Triple(mems, st, sList) },
        combine(payments, historyPeriod, historyCustomDateRange) { pList, period, customRange -> Triple(pList, period, customRange) },
        currentLanguage
    ) { (mems, st, sList), (pList, period, customRange), lang ->
        CalculationEngine.calculateStaffReport(
            members = mems,
            ownerUid = st?.ownerId ?: "demo_user",
            sales = sList,
            paymentsIn = pList,
            period = period,
            customRange = customRange,
            lang = lang
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        com.example.domain.StaffReportSummary(emptyList(), 0L, 0.0, 0.0, 0.0, 0.0)
    )

    fun selectMember(member: StoreMember) {
        selectedMember.value = member
        navigateInBusiness(BusinessNavDestination.STAFF_MEMBER_DETAILS_SCREEN)
    }

    fun createStaffInvite(
        staffName: String,
        roleLabel: String,
        permissionLevel: String,
        customPermissions: Map<String, Boolean>,
        onSuccess: (StoreInvite) -> Unit
    ) {
        if (!isOwner.value) {
            _errorMessage.value = tr("only_owner_invites")
            return
        }
        val trimmedName = staffName.trim()
        if (trimmedName.isBlank()) {
            _errorMessage.value = tr("staff_name_required")
            return
        }
        val permissions = if (permissionLevel.equals("Selected", ignoreCase = true)) {
            customPermissions
        } else {
            StorePermissions.forLevel(permissionLevel)
        }
        val invite = StoreInvite(
            storeId = storeId,
            storeName = store.value?.name ?: "Store",
            staffName = trimmedName,
            roleLabel = roleLabel,
            permissionLevel = permissionLevel,
            permissions = permissions,
            createdByUid = currentUserId,
            createdByName = currentMember.value?.displayName ?: "Owner",
            status = "active"
        )
        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val code = repository.createStaffInvite(invite)
                val fullInvite = invite.copy(code = code, id = code)
                _statusMessage.value = tr("invite_created_msg", code)
                onSuccess(fullInvite)
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: tr("failed_create_invite")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun cancelInvite(code: String) {
        if (!isOwner.value) {
            _errorMessage.value = tr("only_owner_cancel_invites")
            return
        }
        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                repository.cancelInvite(code)
                _statusMessage.value = tr("invite_cancelled_msg")
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: tr("failed_cancel_invite")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun updateMemberPermissions(
        member: StoreMember,
        roleLabel: String,
        permissionLevel: String,
        permissions: Map<String, Boolean>,
        onSuccess: () -> Unit
    ) {
        if (!isOwner.value) {
            _errorMessage.value = tr("only_owner_manage_perms")
            return
        }
        if (member.userId == store.value?.ownerId) {
            _errorMessage.value = tr("owner_perms_immutable")
            return
        }
        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val targetPermissions = if (permissionLevel.equals("Selected", ignoreCase = true)) {
                    permissions
                } else {
                    StorePermissions.forLevel(permissionLevel)
                }
                repository.updateMemberPermissions(
                    storeId = storeId,
                    memberId = member.userId,
                    roleLabel = roleLabel,
                    permissionLevel = permissionLevel,
                    permissions = targetPermissions
                )
                _statusMessage.value = tr("perms_updated_for_msg", member.displayName)
                onSuccess()
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: tr("failed_update_perms")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun removeMember(member: StoreMember, onSuccess: () -> Unit) {
        if (!isOwner.value) {
            _errorMessage.value = tr("only_owner_remove_staff")
            return
        }
        if (member.userId == store.value?.ownerId) {
            _errorMessage.value = tr("owner_cannot_remove")
            return
        }
        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                repository.removeMember(storeId, member.userId)
                _statusMessage.value = tr("staff_removed_msg", member.displayName)
                onSuccess()
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: tr("failed_remove_staff")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun restoreMember(member: StoreMember, onSuccess: () -> Unit) {
        if (!isOwner.value) {
            _errorMessage.value = tr("only_owner_restore_staff")
            return
        }
        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                repository.restoreMember(storeId, member.userId)
                _statusMessage.value = tr("staff_restored_msg", member.displayName)
                onSuccess()
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: tr("failed_restore_staff")
            } finally {
                _isBusy.value = false
            }
        }
    }
}
