package com.example.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.StoreRepository
import com.example.ui.screens.MainShopScreen
import com.example.ui.theme.StoreOSTheme
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.StoreViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RestyledScreensUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var repository: StoreRepository
    private lateinit var authViewModel: AuthViewModel
    private lateinit var storeViewModel: StoreViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("user_mode_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        repository = StoreRepository(db = null, appContext = context)
        authViewModel = AuthViewModel(repository).apply {
            launchDemoMode()
        }
        storeViewModel = StoreViewModel(
            storeId = "demo_store",
            repository = repository,
            networkMonitor = null,
            currentUserId = "demo_user"
        )
    }

    private fun setMainContent() {
        composeRule.setContent {
            StoreOSTheme {
                MainShopScreen(
                    authViewModel = authViewModel,
                    storeViewModel = storeViewModel
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp-xxhdpi")
    fun cashAndBankRestyle_showsUnifiedTabsHeroProportionBarAndWorkingDialogs() {
        setMainContent()
        composeRule.waitUntil(timeoutMillis = 3000L) {
            storeViewModel.accounts.value.isNotEmpty() &&
                storeViewModel.expenses.value.isNotEmpty() &&
                storeViewModel.otherIncomes.value.isNotEmpty()
        }

        // Open Cash & Bank via More
        composeRule.onNodeWithTag("nav_more").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("more_menu_list").performScrollToNode(hasTestTag("more_item_cash_bank"))
        composeRule.onNodeWithTag("more_item_cash_bank").performClick()
        composeRule.waitForIdle()

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInWindow

        // Hero Card & Interactive Pills
        composeRule.onNodeWithTag("cash_and_bank_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("overall_balance_card").assertIsDisplayed()
        composeRule.onNodeWithText("TOTAL BALANCE").assertIsDisplayed()
        composeRule.onNodeWithTag("hero_pill_expenses").assertIsDisplayed()
        composeRule.onNodeWithTag("hero_pill_income").assertIsDisplayed()

        // Multi-segment account proportion bar & 3 capsule tabs
        composeRule.onNodeWithTag("account_proportion_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("cash_tab_accounts").assertIsDisplayed()
        composeRule.onNodeWithTag("cash_tab_expenses").assertIsDisplayed()
        composeRule.onNodeWithTag("cash_tab_income").assertIsDisplayed()

        // No fake "DEFAULT tag" wording
        assertEquals(0, composeRule.onAllNodesWithText("DEFAULT tag", substring = true).fetchSemanticsNodes().size)

        // Real account cards + owner-only Adjust Balance
        composeRule.onNodeWithTag("account_card_cash_in_hand").assertIsDisplayed()
        composeRule.onNodeWithTag("account_card_acc_bkash").assertIsDisplayed()
        composeRule.onNodeWithText("DEFAULT").assertIsDisplayed()
        composeRule.onNodeWithText("ID · 01712-345678").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_adjust_balance_cash_in_hand").assertIsDisplayed()

        val adjustBounds = composeRule.onNodeWithTag("btn_adjust_balance_cash_in_hand").fetchSemanticsNode().boundsInWindow
        assertTrue(adjustBounds.right <= rootBounds.right + 1f)

        // Tap Adjust Balance -> AdjustBalanceDialog opens
        composeRule.onNodeWithTag("btn_adjust_balance_cash_in_hand").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Adjust Balance: Cash in Hand").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        // Tap "+ New Account" FAB -> NewAccountDialog opens
        composeRule.onNodeWithTag("btn_new_account").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Add New Account").assertIsDisplayed()
        composeRule.onNodeWithTag("dialog_cancel_account").performClick()
        composeRule.waitForIdle()

        val cashList = composeRule.onNodeWithTag("cash_bank_list")

        // Switch to Expenses tab via hero pill -> Expenses list + search + Add Expense FAB
        composeRule.onNodeWithTag("hero_pill_expenses").performClick()
        composeRule.waitForIdle()
        cashList.performScrollToNode(hasTestTag("expenses_search_input"))
        composeRule.onNodeWithTag("expenses_search_input").assertIsDisplayed()
        cashList.performScrollToNode(hasTestTag("expense_card_exp_1"))
        composeRule.onNodeWithTag("expense_card_exp_1").assertIsDisplayed()
        cashList.performScrollToNode(hasTestTag("expense_card_exp_2"))
        composeRule.onNodeWithTag("expense_card_exp_2").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_add_expense").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("input_expense_amount").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        // Switch to Other Income tab -> Income list + search + Add Income FAB
        cashList.performScrollToNode(hasTestTag("cash_tab_income"))
        composeRule.onNodeWithTag("cash_tab_income").performClick()
        composeRule.waitForIdle()
        cashList.performScrollToNode(hasTestTag("incomes_search_input"))
        composeRule.onNodeWithTag("incomes_search_input").assertIsDisplayed()
        cashList.performScrollToNode(hasTestTag("income_card_inc_1"))
        composeRule.onNodeWithTag("income_card_inc_1").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_add_income").assertIsDisplayed()

        // Back button returns cleanly
        composeRule.onNodeWithTag("cash_bank_back_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("more_menu_list").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp-xxhdpi")
    fun partiesRestyle_showsCustomersAndSuppliersTabsWithReceiveAndPay_hidesUnbuiltItems() {
        setMainContent()
        composeRule.waitUntil(timeoutMillis = 3000L) {
            storeViewModel.customers.value.isNotEmpty() &&
                storeViewModel.suppliers.value.isNotEmpty() &&
                storeViewModel.bakiOverview.value.totalBakiOwed > 0.0
        }

        // Open Parties (Customer Baki) via More
        composeRule.onNodeWithTag("nav_more").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("more_menu_list").performScrollToNode(hasTestTag("more_item_baki"))
        composeRule.onNodeWithTag("more_item_baki").performClick()
        composeRule.waitForIdle()

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInWindow

        // Unified Parties screen header + Split hero card
        composeRule.onNodeWithTag("baki_hub_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("parties_summary_card").assertIsDisplayed()
        composeRule.onNodeWithText("TO RECEIVE").assertIsDisplayed()
        composeRule.onNodeWithText("TO GIVE").assertIsDisplayed()
        composeRule.onNodeWithTag("parties_tab_customers").assertIsDisplayed()
        composeRule.onNodeWithTag("parties_tab_suppliers").assertIsDisplayed()
        composeRule.onNodeWithTag("baki_search_input").assertIsDisplayed()

        // Unbuilt UI items must NOT appear anywhere
        val hiddenUnbuiltLabels = listOf(
            "Custom Rate",
            "Wholesale",
            "VIP",
            "Retail",
            "Import from contacts",
            "Pick from phone contacts"
        )
        for (label in hiddenUnbuiltLabels) {
            assertEquals(
                "Unbuilt item '$label' must be hidden on Parties",
                0,
                composeRule.onAllNodesWithText(label, substring = true).fetchSemanticsNodes().size
            )
        }

        val partiesList = composeRule.onNodeWithTag("parties_list")

        // Owing customer shows Receive pill; settled customer shows Settled pill
        partiesList.performScrollToNode(hasTestTag("customer_card_cust_1"))
        composeRule.onNodeWithTag("customer_card_cust_1").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_receive_customer_cust_1").assertIsDisplayed()
        val receiveBounds = composeRule.onNodeWithTag("btn_receive_customer_cust_1").fetchSemanticsNode().boundsInWindow
        assertTrue(receiveBounds.right <= rootBounds.right + 1f)

        partiesList.performScrollToNode(hasTestTag("customer_card_cust_4"))
        composeRule.onNodeWithTag("customer_card_cust_4").assertIsDisplayed()
        composeRule.onNode(
            hasText("Settled") and hasAnyAncestor(hasTestTag("customer_card_cust_4")),
            useUnmergedTree = true
        ).assertIsDisplayed()

        // Tapping Receive opens ReceivePaymentDialog
        partiesList.performScrollToNode(hasTestTag("customer_card_cust_1"))
        composeRule.onNodeWithTag("btn_receive_customer_cust_1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("receive_payment_amount_input").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close").performClick()
        composeRule.waitForIdle()

        // Search filters customer rows
        partiesList.performScrollToNode(hasTestTag("baki_search_input"))
        composeRule.onNodeWithTag("baki_search_input").performTextInput("Kabir")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("customer_card_cust_2").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("customer_card_cust_1").fetchSemanticsNodes().size)
        composeRule.onNodeWithContentDescription("Clear").performClick()
        composeRule.waitForIdle()

        // Switch to Suppliers tab -> Pay button on owed supplier opens SingleSupplierPayDialog
        partiesList.performScrollToNode(hasTestTag("parties_tab_suppliers"))
        composeRule.onNodeWithTag("parties_tab_suppliers").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("supplier_item_sup_1").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_pay_supplier_sup_1").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("single_supplier_pay_dialog").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close").performClick()
        composeRule.waitForIdle()

        // "+ Add Party" pill FAB opens HomeAddPartyDialog with Supplier pre-selected and no contact import
        composeRule.onNodeWithTag("btn_add_party").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Save Supplier").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("Import from contacts", substring = true).fetchSemanticsNodes().size)
        composeRule.onNodeWithContentDescription("Close").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("baki_back_btn").performClick()
        composeRule.waitForIdle()

        // Opening Suppliers from More goes directly to Suppliers tab
        composeRule.onNodeWithTag("more_menu_list").performScrollToNode(hasTestTag("more_item_suppliers"))
        composeRule.onNodeWithTag("more_item_suppliers").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("supplier_item_sup_1").assertIsDisplayed()
        composeRule.onNodeWithTag("suppliers_back_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("more_menu_list").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp-xxhdpi")
    fun purchasesRestyle_showsHeroBuyStockSearchDayGroupedOrdersAndHidesReturns() {
        setMainContent()
        composeRule.waitUntil(timeoutMillis = 3000L) {
            storeViewModel.purchases.value.isNotEmpty()
        }

        // Open Purchases via More
        composeRule.onNodeWithTag("nav_more").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("more_menu_list").performScrollToNode(hasTestTag("more_item_purchases"))
        composeRule.onNodeWithTag("more_item_purchases").performClick()
        composeRule.waitForIdle()

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInWindow

        composeRule.onNodeWithTag("purchases_report_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("purchases_summary_card").assertIsDisplayed()
        composeRule.onNodeWithText("TOTAL PURCHASES").assertIsDisplayed()
        composeRule.onNodeWithTag("purchases_buy_stock_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("purchases_search_input").assertIsDisplayed()

        val buyBounds = composeRule.onNodeWithTag("purchases_buy_stock_btn").fetchSemanticsNode().boundsInWindow
        assertTrue(buyBounds.right <= rootBounds.right + 1f)

        // No unbuilt Return/returns UI anywhere on Purchases
        assertEquals(0, composeRule.onAllNodesWithText("Returns deducted", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Return", substring = false).fetchSemanticsNodes().size)

        // Switch to All period so seeded purchases show cleanly
        composeRule.onNodeWithTag("period_tab_all").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("purchase_row_PUR-0103").assertIsDisplayed()
        val rowBounds = composeRule.onNodeWithTag("purchase_row_PUR-0103").fetchSemanticsNode().boundsInWindow
        assertTrue(rowBounds.right <= rootBounds.right + 1f)

        // Search filters purchases cleanly
        composeRule.onNodeWithTag("purchases_search_input").performTextInput("Abul")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("purchase_row_PUR-0101").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("purchase_row_PUR-0103").fetchSemanticsNodes().size)

        // Tap purchase row -> opens PurchaseDetailsDialog
        composeRule.onNodeWithTag("purchase_row_PUR-0101").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("purchase_details_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.waitForIdle()

        // Tap Buy Stock button -> opens HomeRecordPurchaseDialog
        composeRule.onNodeWithTag("purchases_search_input").performTextClearance()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("purchases_buy_stock_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Record Purchase").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("report_back_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("more_menu_list").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp-xxhdpi")
    fun staffRestyle_showsLiveSyncHeroTeamListAndAddSheetWithRealPermissions() {
        setMainContent()

        // Open Staff via More (Wait for WhileSubscribed members flow after screen subscribes)
        composeRule.onNodeWithTag("nav_more").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("more_menu_list").performScrollToNode(hasTestTag("more_item_staff"))
        composeRule.onNodeWithTag("more_item_staff").performClick()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 3000L) {
            storeViewModel.members.value.isNotEmpty()
        }

        // Restyled Staff screen header, Live Sync hero card, notice card, and Team list
        composeRule.onNodeWithTag("staff_management_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Multi-Phone Live Sync").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_show_qr").assertIsDisplayed()
        composeRule.onNodeWithText("Staff accounts are never admin, even with Full access.").assertIsDisplayed()
        composeRule.onNodeWithTag("member_row_demo_user").assertExists()

        composeRule.onNodeWithTag("btn_add_staff").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        // Add Staff Member bottom sheet opens
        composeRule.onNodeWithTag("add_staff_sheet").assertIsDisplayed()
        composeRule.onNodeWithText("Add Staff Member").assertIsDisplayed()
        composeRule.onNodeWithTag("input_staff_name").assertExists()

        // 6 unique roles, with "Stock" appearing only once (never duplicated)
        val expectedRoles = listOf("Partner", "Manager", "Accountant", "Sales", "Stock", "Entry")
        for (role in expectedRoles) {
            assertEquals(
                "Role '$role' must appear exactly once",
                1,
                composeRule.onAllNodesWithTag("role_chip_${role.lowercase()}").fetchSemanticsNodes().size
            )
        }

        // 4 permission levels
        for (level in listOf("Full", "Half", "Limited", "Selected")) {
            composeRule.onNodeWithTag("level_pill_${level.lowercase()}").assertExists()
        }

        // Real permission names used; fake labels absent
        composeRule.onNodeWithTag("perm_item_sell").assertExists()
        composeRule.onNodeWithTag("perm_item_see_buying_price_and_profit").assertExists()
        assertEquals(0, composeRule.onAllNodesWithText("Edit Prices").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Add Stock").fetchSemanticsNodes().size)

        // Under default "Limited", "See Buying Price & Profit" is NOT ticked
        val limitedPerms = com.example.data.model.StorePermissions.forLevel("Limited")
        assertEquals(false, limitedPerms[com.example.data.model.StorePermissions.SEE_BUYING_PRICE_AND_PROFIT])

        // Submitting with blank name shows validation error and does not crash
        composeRule.onNodeWithTag("btn_submit_invite").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Staff name is required").assertExists()

        // Entering a name and submitting creates invite and opens QR dialog
        composeRule.onNodeWithTag("input_staff_name").performScrollTo().performTextInput("Rahim")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("btn_submit_invite").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 3000L) {
            storeViewModel.invites.value.isNotEmpty()
        }
        composeRule.onNodeWithText("Store Invite Code").assertIsDisplayed()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitForIdle()

        // Back button returns cleanly
        composeRule.onNodeWithTag("staff_back_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("more_menu_list").assertIsDisplayed()
    }
}
