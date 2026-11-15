package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Product
import com.example.domain.CalculationEngine
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassOutlinedButton
import com.example.ui.components.GlassTextField
import com.example.ui.components.ProductThumbnail
import com.example.ui.components.QuantityProgressRing
import com.example.ui.components.VividButton
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AlertRedContainer
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VividGreen
import com.example.ui.viewmodel.StoreViewModel
import com.example.util.AppLanguage
import com.example.util.LocalAppLanguage
import com.example.util.ShopLocale

private const val FILTER_ALL = "All"
private const val FILTER_LOW_STOCK = "Low Stock"
private val AmberLow = Color(0xFFFFB300)

@Composable
fun StockScreen(
    storeViewModel: StoreViewModel,
    modifier: Modifier = Modifier
) {
    val currentLanguage by storeViewModel.currentLanguage.collectAsState()
    val products by storeViewModel.products.collectAsState()
    val stockSummary by storeViewModel.stockSummary.collectAsState()
    val isBusy by storeViewModel.isBusy.collectAsState()

    val isOwner by storeViewModel.isOwner.collectAsState()
    val canAddEdit by storeViewModel.canAddEditProductsFlow.collectAsState()
    val canSeeProfit by storeViewModel.canSeeBuyingPriceAndProfitFlow.collectAsState()
    val isGridView by storeViewModel.isStockGridView.collectAsState()
    val errorMessage by storeViewModel.errorMessage.collectAsState()
    val isSimpleMode by storeViewModel.isSimpleMode.collectAsState()
    val isPrivacyHidden by storeViewModel.isPrivacyHidden.collectAsState()
    val shopType by storeViewModel.shopType.collectAsState()
    val storeCategories by storeViewModel.availableProductCategories.collectAsState()
    val unitPresets by storeViewModel.unitPresets.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf(FILTER_ALL) }
    var showAddProductWizard by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var restockProduct by remember { mutableStateOf<Product?>(null) }

    val totalUnits = remember(products) {
        products.sumOf { it.stock.coerceAtLeast(0.0) }
    }
    val lowAlertCount = stockSummary.lowStockCount + stockSummary.outOfStockCount

    val categoryList = remember(storeCategories, products) {
        val merged = mutableListOf<String>()
        fun addUnique(raw: String) {
            val trimmed = raw.trim()
            if (trimmed.isNotBlank() && merged.none { it.equals(trimmed, ignoreCase = true) }) {
                merged.add(trimmed)
            }
        }
        storeCategories.forEach(::addUnique)
        products.forEach { addUnique(it.category) }
        merged.toList()
    }
    val filterOptions = remember(categoryList) {
        listOf(FILTER_ALL, FILTER_LOW_STOCK) + categoryList
    }

    val filteredProducts = remember(products, searchQuery, selectedCategoryFilter, isSimpleMode) {
        products.filter { prod ->
            val matchesFilter = when (selectedCategoryFilter) {
                FILTER_ALL -> true
                FILTER_LOW_STOCK -> prod.stock <= prod.lowStockLimit
                else -> prod.category.equals(selectedCategoryFilter, ignoreCase = true)
            }
            val matchesSearch = searchQuery.isBlank() ||
                prod.name.contains(searchQuery, ignoreCase = true) ||
                prod.nameBn.contains(searchQuery, ignoreCase = true) ||
                (!isSimpleMode && (
                    prod.barcode.contains(searchQuery, ignoreCase = true) ||
                        prod.itemLocation.contains(searchQuery, ignoreCase = true)
                    ))
            matchesFilter && matchesSearch
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            VividGreen.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.78f, size.height * 0.14f),
                        radius = size.width * 0.65f
                    )
                )
            }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("stock_screen_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Clean Stock Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Stock",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (selectedCategoryFilter != FILTER_ALL || searchQuery.isNotBlank()) {
                        Text(
                            text = "${filteredProducts.size} shown",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // 3 Glass Stat Tiles: ITEMS | LOW STOCK | STOCK VALUE
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ITEMS tile
                    StockStatGlassTile(
                        title = "ITEMS",
                        value = ShopLocale.formatNumber(stockSummary.totalItems, currentLanguage),
                        subtitle = "${ShopLocale.formatNumber(totalUnits, currentLanguage)} units",
                        valueColor = TextPrimary,
                        borderBrush = Brush.verticalGradient(
                            colors = listOf(Color(0x3AFFFFFF), Color(0x12FFFFFF))
                        ),
                        backgroundBrush = Brush.verticalGradient(
                            colors = listOf(Color(0xEB262926), Color(0xEC141614))
                        ),
                        onClick = { selectedCategoryFilter = FILTER_ALL },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag("stock_stat_items")
                    )

                    // LOW STOCK tile (amber glow when > 0)
                    val hasLow = lowAlertCount > 0
                    val lowSubtitle = when {
                        stockSummary.outOfStockCount > 0 ->
                            "${ShopLocale.formatNumber(stockSummary.outOfStockCount, currentLanguage)} out"
                        stockSummary.lowStockCount > 0 ->
                            "${ShopLocale.formatNumber(stockSummary.lowStockCount, currentLanguage)} low"
                        else -> "all healthy"
                    }
                    StockStatGlassTile(
                        title = "LOW STOCK",
                        value = ShopLocale.formatNumber(lowAlertCount, currentLanguage),
                        subtitle = lowSubtitle,
                        valueColor = if (hasLow) AmberLow else TextPrimary,
                        borderBrush = if (hasLow) {
                            Brush.verticalGradient(
                                colors = listOf(AmberLow.copy(alpha = 0.72f), AmberLow.copy(alpha = 0.22f))
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(Color(0x3AFFFFFF), Color(0x12FFFFFF))
                            )
                        },
                        backgroundBrush = if (hasLow) {
                            Brush.verticalGradient(
                                colors = listOf(Color(0xD93C2E0B), Color(0xE617140B))
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(Color(0xEB262926), Color(0xEC141614))
                            )
                        },
                        onClick = {
                            selectedCategoryFilter = if (selectedCategoryFilter == FILTER_LOW_STOCK) {
                                FILTER_ALL
                            } else {
                                FILTER_LOW_STOCK
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag("stock_stat_low")
                    )

                    // STOCK VALUE tile (emerald glow, only if allowed to view cost/profit)
                    if (canSeeProfit) {
                        val stockValueText = formatHomeMoney(
                            amount = stockSummary.stockValueAtCost,
                            lang = currentLanguage,
                            isHidden = isPrivacyHidden
                        )
                        val adaptiveValueSize = when {
                            stockValueText.length <= 6 -> 20.sp
                            stockValueText.length <= 8 -> 17.5.sp
                            stockValueText.length <= 10 -> 15.sp
                            else -> 13.sp
                        }
                        StockStatGlassTile(
                            title = "STOCK VALUE",
                            value = stockValueText,
                            pillBadge = "at cost",
                            valueColor = TextPrimary,
                            valueFontSize = adaptiveValueSize,
                            borderBrush = Brush.verticalGradient(
                                colors = listOf(VividGreen.copy(alpha = 0.72f), VividGreen.copy(alpha = 0.22f))
                            ),
                            backgroundBrush = Brush.verticalGradient(
                                colors = listOf(Color(0xDE1E3B14), Color(0xEA10180E))
                            ),
                            modifier = Modifier
                                .weight(1.26f)
                                .fillMaxHeight()
                                .testTag("stock_stat_value")
                        )
                    }
                }
            }

            // Pill Search Bar (no barcode icon) + Circular Grid/List Toggle
            item {
                var isSearchFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = if (isSearchFocused) Color(0xCC181B18) else Color(0xB8161916),
                        border = BorderStroke(
                            1.dp,
                            if (isSearchFocused) VividGreen.copy(alpha = 0.7f) else Color(0x28FFFFFF)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = TextSecondary,
                                modifier = Modifier.size(19.dp)
                            )
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = if (isSimpleMode) "Search products..." else "Search name, shelf...",
                                        color = TextSecondary,
                                        fontSize = 13.5.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    cursorBrush = SolidColor(VividGreen),
                                    textStyle = TextStyle(
                                        color = TextPrimary,
                                        fontSize = 13.5.sp
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { isSearchFocused = it.isFocused }
                                        .testTag("stock_search_input")
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xC41B1E1B))
                            .border(
                                width = 1.dp,
                                color = if (isGridView) VividGreen.copy(alpha = 0.72f) else Color(0x2EFFFFFF),
                                shape = CircleShape
                            )
                            .clickable(
                                onClickLabel = if (isGridView) "Switch to List View" else "Switch to Grid View",
                                onClick = { storeViewModel.toggleStockViewMode() }
                            )
                            .testTag("stock_view_toggle"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isGridView) {
                                Icons.AutoMirrored.Filled.ViewList
                            } else {
                                Icons.Default.GridView
                            },
                            contentDescription = if (isGridView) "Switch to List View" else "Switch to Grid View",
                            tint = if (isGridView) VividGreen else Color(0xFFA7EF85),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Filter Chips Row: All, Low Stock <count>, Categories
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(filterOptions) { option ->
                        val isSelected = selectedCategoryFilter == option
                        val isLowChip = option == FILTER_LOW_STOCK

                        val containerColor = when {
                            isLowChip && isSelected -> Color(0x52453108)
                            isLowChip -> Color(0x2E2E2208)
                            isSelected -> Color(0x305DD62C)
                            else -> Color(0xAD191C19)
                        }
                        val borderColor = when {
                            isLowChip && isSelected -> AmberLow
                            isLowChip -> AmberLow.copy(alpha = if (lowAlertCount > 0) 0.7f else 0.3f)
                            isSelected -> VividGreen
                            else -> Color(0x24FFFFFF)
                        }
                        val labelColor = when {
                            isLowChip -> AmberLow
                            isSelected -> VividGreen
                            else -> TextPrimary
                        }

                        Surface(
                            onClick = { selectedCategoryFilter = option },
                            shape = RoundedCornerShape(22.dp),
                            color = containerColor,
                            border = BorderStroke(1.dp, borderColor),
                            modifier = Modifier.testTag(
                                when (option) {
                                    FILTER_ALL -> "stock_filter_all"
                                    FILTER_LOW_STOCK -> "stock_filter_low_stock"
                                    else -> "stock_filter_cat_$option"
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = when (option) {
                                        FILTER_ALL -> ShopLocale.get("cat_all", currentLanguage)
                                        FILTER_LOW_STOCK -> ShopLocale.get("cat_low_stock", currentLanguage)
                                        else -> ShopLocale.displayCategory(option, currentLanguage)
                                    },
                                    color = labelColor,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected || isLowChip) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                if (isLowChip) {
                                    Surface(
                                        shape = CircleShape,
                                        color = AmberLow.copy(alpha = 0.22f)
                                    ) {
                                        Text(
                                            text = ShopLocale.formatNumber(lowAlertCount, currentLanguage),
                                            color = AmberLow,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Product Cards
            if (filteredProducts.isEmpty()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (selectedCategoryFilter == FILTER_LOW_STOCK) {
                                    "All stock levels are healthy"
                                } else {
                                    "No products match your filter"
                                },
                                color = TextSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else if (isGridView) {
                items(
                    items = filteredProducts.chunked(2),
                    key = { row -> "grid_" + row.joinToString("_") { it.id } }
                ) { rowProducts ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowProducts.forEach { product ->
                            StockProductGridCard(
                                product = product,
                                currentLanguage = currentLanguage,
                                canAddEdit = canAddEdit,
                                canSeeProfit = canSeeProfit,
                                isPrivacyHidden = isPrivacyHidden,
                                onClick = {
                                    if (canAddEdit) {
                                        storeViewModel.clearErrorMessage()
                                        editingProduct = product
                                    }
                                },
                                onRestock = { if (canAddEdit) restockProduct = product },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowProducts.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                items(filteredProducts, key = { it.id }) { product ->
                    StockProductCard(
                        product = product,
                        currentLanguage = currentLanguage,
                        canAddEdit = canAddEdit,
                        canSeeProfit = canSeeProfit,
                        isSimpleMode = isSimpleMode,
                        isPrivacyHidden = isPrivacyHidden,
                        onClick = {
                            if (canAddEdit) {
                                storeViewModel.clearErrorMessage()
                                editingProduct = product
                            }
                        },
                        onRestock = { if (canAddEdit) restockProduct = product }
                    )
                }
            }
        }

        // Floating Vivid Green "+ Add Product" Pill FAB
        if (canAddEdit) {
            Surface(
                onClick = {
                    storeViewModel.clearErrorMessage()
                    showAddProductWizard = true
                },
                shape = RoundedCornerShape(28.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, Color(0x88FFFFFF)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 10.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(28.dp),
                        spotColor = VividGreen,
                        ambientColor = VividGreen
                    )
                    .height(48.dp)
                    .testTag("stock_add_product_btn")
            ) {
                Row(
                    modifier = Modifier
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFF7CEF4F), VividGreen)
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color(0xFF0A1406),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Add Product",
                        color = Color(0xFF0A1406),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // Add Product 3-Step Wizard Dialog
        if (showAddProductWizard) {
            AddProductWizardDialog(
                currentLanguage = currentLanguage,
                isBusy = isBusy,
                canSeeBuyingPrice = canSeeProfit,
                isSimpleMode = isSimpleMode,
                shopType = shopType,
                availableCategories = categoryList,
                unitPresets = unitPresets,
                externalError = errorMessage,
                onDismiss = {
                    storeViewModel.clearErrorMessage()
                    showAddProductWizard = false
                },
                onConfirm = { name, nameBn, category, unit, buy, sell, stock, lowLimit, photo, barcode, wholesale, mrp, location, partyRate, buyUnit, buyConversion, sellUnits ->
                    storeViewModel.addNewProductWizard(
                        name = name,
                        nameBn = nameBn,
                        category = category,
                        unit = unit,
                        buyPrice = buy,
                        sellPrice = sell,
                        stock = stock,
                        lowStockLimit = lowLimit,
                        photoUri = photo,
                        barcode = barcode,
                        wholesalePrice = wholesale,
                        mrp = mrp,
                        itemLocation = location,
                        partyRate = partyRate,
                        buyUnit = buyUnit,
                        buyConversion = buyConversion,
                        sellUnits = sellUnits,
                        onSuccess = { showAddProductWizard = false }
                    )
                }
            )
        }

        // Edit Product Wizard Dialog
        if (editingProduct != null) {
            AddProductWizardDialog(
                productToEdit = editingProduct,
                currentLanguage = currentLanguage,
                isBusy = isBusy,
                canSeeBuyingPrice = canSeeProfit,
                isSimpleMode = isSimpleMode,
                shopType = shopType,
                availableCategories = categoryList,
                unitPresets = unitPresets,
                externalError = errorMessage,
                onDismiss = {
                    storeViewModel.clearErrorMessage()
                    editingProduct = null
                },
                onConfirm = { name, nameBn, category, unit, buy, sell, stock, lowLimit, photo, barcode, wholesale, mrp, location, partyRate, buyUnit, buyConversion, sellUnits ->
                    storeViewModel.updateProductWizard(
                        productId = editingProduct!!.id,
                        name = name,
                        nameBn = nameBn,
                        category = category,
                        unit = unit,
                        buyPrice = buy,
                        sellPrice = sell,
                        stock = stock,
                        lowStockLimit = lowLimit,
                        photoUri = photo,
                        barcode = barcode,
                        wholesalePrice = wholesale,
                        mrp = mrp,
                        itemLocation = location,
                        partyRate = partyRate,
                        buyUnit = buyUnit,
                        buyConversion = buyConversion,
                        sellUnits = sellUnits,
                        onSuccess = { editingProduct = null }
                    )
                },
                onDelete = if (isOwner) {
                    {
                        storeViewModel.deleteProduct(editingProduct!!.id) {
                            editingProduct = null
                        }
                    }
                } else null
            )
        }

        // Quick Restock Dialog (Atomic FieldValue.increment)
        if (restockProduct != null) {
            QuickRestockDialog(
                product = restockProduct!!,
                onDismiss = { restockProduct = null },
                onConfirm = { amount ->
                    storeViewModel.addQuickStock(restockProduct!!.id, amount)
                    restockProduct = null
                }
            )
        }
    }
}

@Composable
private fun StockStatGlassTile(
    title: String,
    value: String,
    valueColor: Color,
    borderBrush: Brush,
    backgroundBrush: Brush,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    pillBadge: String? = null,
    valueFontSize: androidx.compose.ui.unit.TextUnit = 21.sp,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(20.dp)
    var tileModifier = modifier
        .clip(shape)
        .background(backgroundBrush)
    if (onClick != null) {
        tileModifier = tileModifier.clickable(onClick = onClick)
    }

    Surface(
        modifier = tileModifier.defaultMinSize(minHeight = 76.dp),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, borderBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
        ) {
            Text(
                text = title,
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )

            Text(
                text = value,
                color = valueColor,
                fontSize = valueFontSize,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )

            when {
                !pillBadge.isNullOrBlank() -> {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0x22FFFFFF),
                        border = BorderStroke(1.dp, Color(0x28FFFFFF))
                    ) {
                        Text(
                            text = pillBadge,
                            color = Color(0xFFCDE8C2),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                !subtitle.isNullOrBlank() -> {
                    Text(
                        text = subtitle,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                else -> {
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StockProductCard(
    product: Product,
    currentLanguage: AppLanguage,
    canAddEdit: Boolean = true,
    canSeeProfit: Boolean = true,
    isSimpleMode: Boolean = false,
    isPrivacyHidden: Boolean = false,
    onClick: () -> Unit,
    onRestock: () -> Unit
) {
    val isOut = product.stock <= 0.0
    val isLow = product.stock > 0.0 && product.stock <= product.lowStockLimit
    val shape = RoundedCornerShape(22.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xEB232723), Color(0xEC141614))
                )
            )
            .clickable(enabled = canAddEdit, onClick = onClick)
            .testTag("stock_product_${product.id}"),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0x40FFFFFF), Color(0x12FFFFFF))
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Product Photo + Name & Category/Location Badges + Quantity Ring
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProductThumbnail(
                    photoUri = product.photoUri,
                    category = product.category,
                    contentDescription = product.name,
                    modifier = Modifier
                        .size(48.dp)
                        .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(14.dp)),
                    iconSize = 22.dp,
                    cornerRadius = 14.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = product.name,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        lineHeight = 19.sp,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (product.nameBn.isNotBlank()) {
                        Text(
                            text = product.nameBn,
                            color = TextMuted,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Badges FlowRow: Low / Out badge + Category pill + Location (Detailed)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isOut) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = AlertRedContainer,
                                border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = "Out",
                                    color = AlertRed,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        } else if (isLow) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0x30453108),
                                border = BorderStroke(1.dp, AmberLow.copy(alpha = 0.7f))
                            ) {
                                Text(
                                    text = "Low",
                                    color = AmberLow,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (product.isDemo) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0x285DD62C),
                                border = BorderStroke(1.dp, VividGreen.copy(alpha = 0.65f)),
                                modifier = Modifier.testTag("demo_badge_${product.id}")
                            ) {
                                Text(
                                    text = ShopLocale.get("demo_badge_label", currentLanguage),
                                    color = VividGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (product.category.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0x1EFFFFFF),
                                border = BorderStroke(1.dp, Color(0x20FFFFFF))
                            ) {
                                Text(
                                    text = ShopLocale.displayCategory(product.category, currentLanguage),
                                    color = TextSecondary,
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (!isSimpleMode && product.itemLocation.isNotBlank()) {
                            Text(
                                text = product.itemLocation,
                                color = TextMuted,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Quantity Ring with stock number + unit inside (Amber when low, red at 0, green healthy)
                QuantityProgressRing(
                    quantity = product.stock,
                    lowStockLimit = product.lowStockLimit,
                    size = 54.dp,
                    unitLabel = product.unit
                )
            }

            // Subtle divider separating header & price/action footer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0x14FFFFFF))
            )

            // Bottom Footer Row: Sell & Buy Prices + "+ Stock" Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val unitTag = ShopLocale.displayUnit(product.unit, currentLanguage)
                val sellText = "${formatHomeMoney(product.sellPrice, currentLanguage, false)}/$unitTag"
                val buyText = "${formatHomeMoney(product.buyPrice, currentLanguage, isPrivacyHidden)}/$unitTag"
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = TextSecondary)) {
                            append("Sell ")
                        }
                        withStyle(SpanStyle(color = TextPrimary, fontWeight = FontWeight.Bold)) {
                            append(sellText)
                        }
                        if (canSeeProfit) {
                            withStyle(SpanStyle(color = TextSecondary)) {
                                append("  •  Buy ")
                            }
                            withStyle(SpanStyle(color = TextSecondary, fontWeight = FontWeight.Medium)) {
                                append(buyText)
                            }
                        }
                    },
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (canAddEdit) {
                    StockRestockPillButton(
                        onClick = onRestock,
                        testTag = "restock_btn_${product.id}"
                    )
                }
            }
        }
    }
}

@Composable
private fun StockRestockPillButton(
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = Color(0x33295A13),
        border = BorderStroke(1.dp, VividGreen.copy(alpha = 0.78f)),
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(50),
                spotColor = VividGreen.copy(alpha = 0.35f),
                ambientColor = VividGreen.copy(alpha = 0.2f)
            )
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "+ Stock",
                color = Color(0xFF91F26B),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun StockProductGridCard(
    product: Product,
    currentLanguage: AppLanguage,
    canAddEdit: Boolean = true,
    canSeeProfit: Boolean = true,
    isPrivacyHidden: Boolean = false,
    onClick: () -> Unit,
    onRestock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOut = product.stock <= 0.0
    val isLow = product.stock > 0.0 && product.stock <= product.lowStockLimit
    val shape = RoundedCornerShape(22.dp)

    Surface(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xEB232723), Color(0xEC141614))
                )
            )
            .clickable(enabled = canAddEdit, onClick = onClick)
            .testTag("stock_grid_product_${product.id}"),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0x40FFFFFF), Color(0x12FFFFFF))
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                ProductThumbnail(
                    photoUri = product.photoUri,
                    category = product.category,
                    contentDescription = product.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(106.dp),
                    iconSize = 34.dp,
                    cornerRadius = 14.dp
                )

                if (isOut || isLow) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isOut) Color(0xD93E1212) else Color(0xD93E2D08),
                        border = BorderStroke(1.dp, if (isOut) AlertRed else AmberLow),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = if (isOut) "Out" else "Low",
                            color = if (isOut) AlertRed else AmberLow,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                if (product.isDemo) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xD912240E),
                        border = BorderStroke(1.dp, VividGreen.copy(alpha = 0.78f)),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .testTag("demo_grid_badge_${product.id}")
                    ) {
                        Text(
                            text = ShopLocale.get("demo_badge_label", currentLanguage),
                            color = VividGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC0D100D))
                ) {
                    QuantityProgressRing(
                        quantity = product.stock,
                        lowStockLimit = product.lowStockLimit,
                        size = 44.dp,
                        unitLabel = product.unit
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = product.name,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    lineHeight = 17.sp,
                    overflow = TextOverflow.Ellipsis
                )

                val unitTag = ShopLocale.displayUnit(product.unit, currentLanguage)
                val sellText = "${formatHomeMoney(product.sellPrice, currentLanguage, false)}/$unitTag"
                val buyText = "${formatHomeMoney(product.buyPrice, currentLanguage, isPrivacyHidden)}/$unitTag"
                Text(
                    text = "Sell $sellText",
                    color = VividGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                if (canSeeProfit) {
                    Text(
                        text = "Buy $buyText",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (canAddEdit) {
                StockRestockPillButton(
                    onClick = onRestock,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "restock_grid_btn_${product.id}"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickRestockDialog(
    product: Product,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double) -> Unit
) {
    val currentLanguage = LocalAppLanguage.current
    var amountText by remember { mutableStateOf("10") }
    var error by remember { mutableStateOf<String?>(null) }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(26.dp)
        Surface(
            shape = shape,
            color = Color.Transparent,
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0x40FFFFFF), Color(0x14FFFFFF))
                )
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clip(shape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xF51D221D), Color(0xFA121512))
                    )
                )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Add Stock",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "${product.name} (Current: ${ShopLocale.formatNumber(product.stock, currentLanguage)} ${ShopLocale.displayUnit(product.unit, currentLanguage)})",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(14.dp))

                GlassTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        error = null
                    },
                    label = "Quantity to Add (${ShopLocale.displayUnit(product.unit, currentLanguage)})",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    testTag = "restock_input"
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(5.0, 10.0, 25.0, 50.0).forEach { qty ->
                        val qtyStr = ShopLocale.formatNumber(qty, AppLanguage.EN)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x242A5C14),
                            border = BorderStroke(1.dp, VividGreen.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { amountText = qtyStr }
                        ) {
                            Text(
                                text = "+$qtyStr",
                                color = VividGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = error ?: "", color = AlertRed, fontSize = 11.sp, maxLines = 1, softWrap = false)
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassOutlinedButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    VividButton(
                        text = "Confirm",
                        onClick = {
                            val qty = amountText.trim().toDoubleOrNull()
                            if (qty == null || qty <= 0.0) {
                                error = "Enter a valid positive quantity"
                            } else {
                                onConfirm(CalculationEngine.roundStock(qty))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "confirm_restock_btn"
                    )
                }
            }
        }
    }
}
