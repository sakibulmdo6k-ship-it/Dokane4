package com.example.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Product
import com.example.data.repository.StoreRepository
import com.example.domain.CalculationEngine
import com.example.domain.ShopType
import com.example.domain.ShopTypeCatalog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StockScreen
import com.example.ui.screens.StoreOnboardingScreen
import com.example.ui.theme.StoreOSTheme
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.StoreViewModel
import com.example.util.AppLanguage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShopTypePickerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var repository: StoreRepository
    private lateinit var authViewModel: AuthViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("user_mode_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("storeos_locale", Context.MODE_PRIVATE).edit().clear().commit()
        repository = StoreRepository(db = null, appContext = context)
        authViewModel = AuthViewModel(repository)
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp-xxhdpi")
    fun onboarding_requiresShopType_pickHardwareCreatesCategoriesNoFakeProductsAndDefaultUnits() {
        val showStockScreen = mutableStateOf(false)
        var storeViewModel: StoreViewModel? = null

        composeRule.setContent {
            StoreOSTheme {
                if (!showStockScreen.value) {
                    StoreOnboardingScreen(authViewModel = authViewModel)
                } else {
                    val vm = remember {
                        StoreViewModel(
                            storeId = "demo_store",
                            repository = repository,
                            networkMonitor = null,
                            currentUserId = "demo_user"
                        ).also { storeViewModel = it }
                    }
                    StockScreen(storeViewModel = vm)
                }
            }
        }
        composeRule.waitForIdle()

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInWindow

        // All 7 shop type tiles are rendered and stay within screen bounds
        for (type in ShopType.entries) {
            val node = composeRule.onNodeWithTag("shop_type_tile_${type.id}")
            node.performScrollTo()
            node.assertIsDisplayed()
            val bounds = node.fetchSemanticsNode().boundsInWindow
            assertTrue("Tile ${type.id} must not overflow horizontally", bounds.right <= rootBounds.right + 1f)
        }

        // Fill store form without picking shop type -> does NOT default to Grocery, shows error
        composeRule.onNodeWithTag("store_name_input").performScrollTo().performTextInput("Karim Hardware")
        composeRule.onNodeWithTag("store_phone_input").performScrollTo().performTextInput("01711223344")
        composeRule.onNodeWithTag("store_address_input").performScrollTo().performTextInput("Nawabpur, Dhaka")
        composeRule.onNodeWithTag("create_store_submit_btn").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Please select your shop type").assertIsDisplayed()
        assertFalse(authViewModel.uiState.value is AuthUiState.DemoMode)

        // Pick Hardware and submit
        composeRule.onNodeWithTag("shop_type_tile_hardware").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("shop_type_preview").assertIsDisplayed()
        composeRule.onNodeWithTag("create_store_submit_btn").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 3000L) {
            authViewModel.uiState.value is AuthUiState.DemoMode
        }

        // Switch view to StockScreen within the same composition root
        showStockScreen.value = true
        composeRule.waitUntil(timeoutMillis = 3000L) {
            storeViewModel?.store?.value?.shopType == "hardware"
        }
        composeRule.waitForIdle()

        val activeVm = storeViewModel!!
        assertEquals(ShopType.HARDWARE, activeVm.shopType.value)
        assertEquals(
            listOf("Hand Tools", "Power Tools", "Nuts & Bolts", "Electrical", "Paint", "Plumbing"),
            activeVm.store.value?.productCategories
        )
        assertEquals(
            listOf("Hand Tools", "Power Tools", "Nuts & Bolts", "Electrical", "Paint", "Plumbing"),
            activeVm.availableProductCategories.value
        )
        // Real store created via onboarding must contain ZERO fake products
        assertTrue(activeVm.products.value.isEmpty())

        composeRule.onNodeWithTag("stock_filter_cat_Hand Tools").assertExists()
        composeRule.onNodeWithTag("stock_filter_cat_Nuts & Bolts").assertExists()
        composeRule.onNodeWithTag("stock_filter_cat_Electrical").assertExists()

        composeRule.onNodeWithTag("stock_add_product_btn").performClick()
        composeRule.waitForIdle()

        // Starting categories from Hardware appear; Grocery is NOT default
        composeRule.onNodeWithTag("wizard_category_Hand Tools").assertExists()
        composeRule.onNodeWithTag("wizard_category_Power Tools").assertExists()
        composeRule.onNodeWithTag("wizard_category_Nuts & Bolts").assertExists()
        composeRule.onNodeWithTag("wizard_category_Electrical").assertExists()
        composeRule.onNodeWithTag("wizard_category_Paint").assertExists()
        composeRule.onNodeWithTag("wizard_category_Plumbing").assertExists()
        assertEquals(0, composeRule.onAllNodesWithTag("wizard_category_Grocery").fetchSemanticsNodes().size)

        // Tap Nuts & Bolts -> defaults nails to buy by box (25 kg), sell by kg/gram
        composeRule.onNodeWithTag("wizard_category_Nuts & Bolts").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wizard_buy_conversion").performScrollTo().assertTextContains("25")
        composeRule.onNodeWithTag("wizard_sell_unit_factor_gram").performScrollTo().assertTextContains("1000")

        // Tap Electrical wire preset -> wire bought by roll (90 meter), sold by meter/feet
        composeRule.onNodeWithTag("wizard_unit_preset_hw_wire").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wizard_buy_unit_row").performScrollTo().performScrollToNode(hasTestTag("wizard_buy_unit_roll"))
        composeRule.onNodeWithTag("wizard_buy_unit_roll").assertIsDisplayed()
        composeRule.onNodeWithText("1 roll =").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("wizard_buy_conversion").performScrollTo().assertTextContains("90")
        composeRule.onNodeWithTag("wizard_sell_unit_factor_feet").performScrollTo().assertExists()
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp-xxhdpi")
    fun groceryPresets_riceSackToKg_andOilCartonToLitre() {
        val groceryPresets = ShopTypeCatalog.unitPresets(ShopType.GROCERY)
        val ricePreset = groceryPresets.first { it.id == "gr_rice" }
        assertEquals("sack", ricePreset.buyUnit)
        assertEquals("kg", ricePreset.stockUnit)
        assertEquals(50.0, ricePreset.buyConversion, 1e-6)
        assertEquals(1000.0, ricePreset.sellUnits["gram"]!!, 1e-6)

        val oilPreset = groceryPresets.first { it.id == "gr_oil" }
        assertEquals("carton", oilPreset.buyUnit)
        assertEquals("litre", oilPreset.stockUnit)
        assertEquals(12.0, oilPreset.buyConversion, 1e-6)
        assertEquals(1000.0, oilPreset.sellUnits["ml"]!!, 1e-6)
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp-xxhdpi")
    fun exploreDemoStore_pickHardwareThenPharmacy_samplesChangeAndMarkedDemo() {
        var storeViewModel: StoreViewModel? = null
        composeRule.setContent {
            val authState by authViewModel.uiState.collectAsStateWithLifecycle()
            StoreOSTheme {
                if (authState is AuthUiState.DemoMode) {
                    val vm = remember {
                        StoreViewModel(
                            storeId = "demo_store",
                            repository = repository,
                            networkMonitor = null,
                            currentUserId = "demo_user"
                        ).also { storeViewModel = it }
                    }
                    StockScreen(storeViewModel = vm)
                } else {
                    AuthScreen(authViewModel = authViewModel)
                }
            }
        }
        composeRule.waitForIdle()

        // Tap Explore Demo Store -> Shop Type Picker opens
        composeRule.onNodeWithTag("explore_demo_store_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("demo_shop_type_dialog").assertIsDisplayed()

        // Pick Hardware in Demo Store -> AuthScreen transitions to StockScreen
        composeRule.onNodeWithTag("shop_type_tile_hardware").performClick()
        composeRule.waitUntil(timeoutMillis = 3000L) {
            authViewModel.uiState.value is AuthUiState.DemoMode &&
                storeViewModel?.products?.value?.any { it.id == "demo_hw_1" } == true
        }
        composeRule.waitForIdle()

        val activeVm = storeViewModel!!
        assertEquals(ShopType.HARDWARE, activeVm.shopType.value)
        assertTrue(activeVm.products.value.all { it.isDemo })
        composeRule.onNodeWithTag("stock_screen_list").performScrollToNode(hasTestTag("stock_product_demo_hw_1"))
        composeRule.onNodeWithTag("stock_product_demo_hw_1").assertIsDisplayed()
        composeRule.onNodeWithTag("demo_badge_demo_hw_1", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNode(
            hasText("Demo") and hasAnyAncestor(hasTestTag("demo_badge_demo_hw_1")),
            useUnmergedTree = true
        ).assertIsDisplayed()

        // Pick another type (Pharmacy) in Demo Store -> samples change to Pharmacy & marked Demo
        authViewModel.launchDemoMode(ShopType.PHARMACY)
        composeRule.waitUntil(timeoutMillis = 3000L) {
            activeVm.products.value.any { it.id == "demo_ph_1" }
        }
        composeRule.waitForIdle()

        assertEquals(ShopType.PHARMACY, activeVm.shopType.value)
        assertTrue(activeVm.products.value.all { it.isDemo })
        assertEquals(0, composeRule.onAllNodesWithTag("stock_product_demo_hw_1").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("stock_screen_list").performScrollToNode(hasTestTag("stock_product_demo_ph_1"))
        composeRule.onNodeWithTag("stock_product_demo_ph_1").assertIsDisplayed()
        composeRule.onNodeWithTag("demo_badge_demo_ph_1", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp-xxhdpi")
    fun existingStore_unchangedWhenChangingTypeInSettings_withConfirmation() {
        val savedOutcome = runBlocking {
            repository.createStore(
                name = "Existing Legacy Shop",
                phone = "01811223344",
                address = "Chawkbazar",
                shopType = "general",
                productCategories = listOf("Special Grain", "Spices")
            )
            val added = repository.addProduct(
                "demo_store",
                Product(
                    name = "Existing Basmati",
                    category = "Special Grain",
                    unit = "kg",
                    buyUnit = "sack",
                    buyConversion = 25.0,
                    buyPrice = 110.0,
                    sellPrice = 140.0,
                    stock = 30.0
                )
            )
            val prod = Product(
                id = added.productId,
                storeId = "demo_store",
                name = "Existing Basmati",
                category = "Special Grain",
                unit = "kg",
                buyUnit = "sack",
                buyConversion = 25.0,
                buyPrice = 110.0,
                sellPrice = 140.0,
                stock = 30.0
            )
            repository.executeSaleCheckout(
                storeId = "demo_store",
                items = listOf(CalculationEngine.buildSaleItem(prod, qty = 2.0, unit = "kg")),
                paymentType = "cash"
            )
            added
        }

        authViewModel.launchDemoMode(shopType = null)
        val storeViewModel = StoreViewModel(
            storeId = "demo_store",
            repository = repository,
            networkMonitor = null,
            currentUserId = "demo_user"
        )

        composeRule.setContent {
            storeViewModel.products.collectAsStateWithLifecycle()
            storeViewModel.sales.collectAsStateWithLifecycle()
            StoreOSTheme {
                SettingsScreen(
                    authViewModel = authViewModel,
                    storeViewModel = storeViewModel,
                    onBack = {},
                    onLogout = {}
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 3000L) {
            storeViewModel.products.value.isNotEmpty() && storeViewModel.sales.value.isNotEmpty()
        }
        composeRule.waitForIdle()

        val initialProducts = storeViewModel.products.value
        val initialSales = storeViewModel.sales.value
        assertEquals(1, initialProducts.size)
        assertEquals("Existing Basmati", initialProducts.first().name)
        assertTrue(initialSales.any { sale -> sale.items.any { it.name == "Existing Basmati" } })

        // Scroll to and open Shop Type Picker in Settings
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_shop_type_row"))
        composeRule.onNodeWithTag("settings_shop_type_row").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_shop_type_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("shop_type_tile_hardware").performClick()
        composeRule.waitForIdle()

        // Confirmation dialog appears explaining existing data is unchanged
        composeRule.onNodeWithTag("shop_type_confirm_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Change Shop Type?").assertIsDisplayed()
        composeRule.onNodeWithText("Existing products, categories, and sales stay completely unchanged.").assertIsDisplayed()

        // Confirm with extra suggestions toggled OFF first -> existing categories, products & sales completely unchanged
        composeRule.onNodeWithTag("shop_type_suggestions_toggle").performClick()
        composeRule.onNodeWithTag("shop_type_confirm_btn").performClick()
        composeRule.waitUntil(timeoutMillis = 3000L) {
            storeViewModel.shopType.value == ShopType.HARDWARE
        }
        composeRule.waitForIdle()

        assertEquals(listOf("Special Grain", "Spices"), storeViewModel.store.value?.productCategories)
        assertEquals(initialProducts, storeViewModel.products.value)
        assertEquals(initialSales, storeViewModel.sales.value)

        // Now change to Clothing WITH extra suggestions ON -> adds suggestions while preserving existing categories, products & sales
        composeRule.onNodeWithTag("settings_shop_type_row").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("shop_type_tile_clothing").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("shop_type_confirm_btn").performClick()
        composeRule.waitUntil(timeoutMillis = 3000L) {
            storeViewModel.shopType.value == ShopType.CLOTHING
        }

        val updatedCategories = storeViewModel.store.value?.productCategories.orEmpty()
        assertTrue(updatedCategories.contains("Special Grain"))
        assertTrue(updatedCategories.contains("Spices"))
        assertTrue(updatedCategories.contains("Men's Wear"))
        assertTrue(updatedCategories.contains("Fabrics"))
        assertEquals(savedOutcome.productId, storeViewModel.products.value.first().id)
        assertEquals("Existing Basmati", storeViewModel.products.value.first().name)
        assertEquals(initialProducts, storeViewModel.products.value)
        assertEquals(initialSales, storeViewModel.sales.value)
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp-xxhdpi")
    fun banglaLanguage_rendersShopTypeTilesAndDemoBadgeCleanly() {
        authViewModel.setLanguage(AppLanguage.BN)
        authViewModel.launchDemoMode(ShopType.HARDWARE)

        val storeViewModel = StoreViewModel(
            storeId = "demo_store",
            repository = repository,
            networkMonitor = null,
            currentUserId = "demo_user"
        )
        val showSettings = mutableStateOf(false)

        composeRule.setContent {
            StoreOSTheme {
                if (!showSettings.value) {
                    StockScreen(storeViewModel = storeViewModel)
                } else {
                    SettingsScreen(
                        authViewModel = authViewModel,
                        storeViewModel = storeViewModel,
                        onBack = {},
                        onLogout = {}
                    )
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 3000L) {
            storeViewModel.products.value.any { it.id == "demo_hw_1" }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("stock_screen_list").performScrollToNode(hasTestTag("stock_product_demo_hw_1"))
        composeRule.onNodeWithTag("demo_badge_demo_hw_1", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNode(
            hasText("ডেমো") and hasAnyAncestor(hasTestTag("demo_badge_demo_hw_1")),
            useUnmergedTree = true
        ).assertIsDisplayed()

        showSettings.value = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_shop_type_row"))
        composeRule.onNodeWithText("দোকানের ধরন").assertIsDisplayed()
        composeRule.onNodeWithText("হার্ডওয়্যার").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("Not set").fetchSemanticsNodes().size)
    }
}
