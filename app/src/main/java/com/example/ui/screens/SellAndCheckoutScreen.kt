package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.example.data.model.CompletedSaleReceipt
import com.example.data.model.Product
import com.example.data.model.SaleItem
import com.example.domain.CalculationEngine
import com.example.ui.components.AccountPickerField
import com.example.ui.components.DigitalReceiptContent
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassOutlinedButton
import com.example.ui.components.GlassTextField
import com.example.ui.components.ProductThumbnail
import com.example.ui.components.SlideToConfirmSale
import com.example.ui.components.VividButton
import com.example.ui.components.getCategoryIcon
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AlertRedContainer
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VividGreen
import com.example.ui.viewmodel.StoreViewModel
import com.example.util.AppLanguage
import com.example.util.ShopLocale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val AmberLowSell = Color(0xFFFFB300)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellScreen(
    storeViewModel: StoreViewModel,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val currentLanguage by storeViewModel.currentLanguage.collectAsState()
    val products by storeViewModel.products.collectAsState()
    val cart by storeViewModel.cart.collectAsState()
    val isBusy by storeViewModel.isBusy.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var showCheckoutSheet by remember { mutableStateOf(false) }

    val categories = remember(products) {
        listOf("All") + products.map { it.category.trim() }.filter { it.isNotBlank() }.distinct()
    }

    val filteredProducts = remember(products, searchQuery, selectedCategory) {
        products.filter { prod ->
            val matchesCategory = (selectedCategory == "All") || prod.category.equals(selectedCategory, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                prod.name.contains(searchQuery, ignoreCase = true) ||
                prod.nameBn.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
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
                        center = Offset(size.width * 0.25f, size.height * 0.10f),
                        radius = size.width * 0.85f
                    )
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Compact Top Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("sell_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                    Text(
                        text = "Sell",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (filteredProducts.isNotEmpty()) {
                    Text(
                        text = "${filteredProducts.size} items",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Scrollable Search + Category Chips + 2-Column Product Tiles Grid
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("sell_product_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = 24.dp
                )
            ) {
                // Pill Search Bar (No mic / barcode icons until built)
                item(key = "sell_search_bar") {
                    var isSearchFocused by remember { mutableStateOf(false) }
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = if (isSearchFocused) Color(0xCC181B18) else Color(0xB8161916),
                        border = BorderStroke(
                            1.dp,
                            if (isSearchFocused) VividGreen.copy(alpha = 0.72f) else Color(0x2CFFFFFF)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 46.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                                        text = "Search products...",
                                        color = TextSecondary,
                                        fontSize = 14.sp,
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
                                        fontSize = 14.sp
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { isSearchFocused = it.isFocused }
                                        .testTag("sell_search_input")
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
                }

                // Horizontal Category Icon+Label Chips (matching Image 1)
                item(key = "sell_category_chips") {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories) { cat ->
                            val isSelected = selectedCategory == cat
                            val chipShape = RoundedCornerShape(16.dp)
                            Surface(
                                onClick = { selectedCategory = cat },
                                shape = chipShape,
                                color = Color.Transparent,
                                border = BorderStroke(
                                    width = if (isSelected) 1.3.dp else 1.dp,
                                    color = if (isSelected) VividGreen else Color(0x2AFFFFFF)
                                ),
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 64.dp, minHeight = 50.dp)
                                    .clip(chipShape)
                                    .background(
                                        brush = if (isSelected) {
                                            Brush.verticalGradient(
                                                colors = listOf(Color(0x422B6313), Color(0x2E13240B))
                                            )
                                        } else {
                                            Brush.verticalGradient(
                                                colors = listOf(Color(0xEB232723), Color(0xEC141614))
                                            )
                                        }
                                    )
                                    .testTag("sell_category_$cat")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (cat == "All") Icons.Default.GridView else getCategoryIcon(cat),
                                        contentDescription = null,
                                        tint = if (isSelected) VividGreen else Color(0xCCFFFFFF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (cat == "All") ShopLocale.get("cat_all", currentLanguage) else ShopLocale.displayCategory(cat, currentLanguage),
                                        color = if (isSelected) VividGreen else TextPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }

                if (filteredProducts.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(34.dp)
                                )
                                Text(
                                    text = "No matching products",
                                    color = TextSecondary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = filteredProducts.chunked(2),
                        key = { row -> "sell_row_" + row.joinToString("_") { it.id } }
                    ) { rowProducts ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowProducts.forEach { product ->
                                val matchingLines = cart.items.values.filter { it.productId == product.id }
                                val inCartStockQty = CalculationEngine.roundStock(
                                    matchingLines.sumOf { CalculationEngine.saleItemStockQty(it) }
                                )
                                val isOutOfStock = product.stock <= 0.0
                                val remainingStock = CalculationEngine.roundStock((product.stock - inCartStockQty).coerceAtLeast(0.0))
                                val canAddMore = remainingStock > CalculationEngine.STOCK_EPSILON

                                SellProductTile(
                                    product = product,
                                    inCartQty = inCartStockQty,
                                    canAddMore = canAddMore,
                                    isOutOfStock = isOutOfStock,
                                    currentLanguage = currentLanguage,
                                    onAdd = {
                                        val step = minOf(1.0, remainingStock)
                                        if (step > CalculationEngine.STOCK_EPSILON) {
                                            storeViewModel.addProductToCart(product, qtyToAdd = step)
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowProducts.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Floating Vivid Green Cart Pill Bar (cleanly below grid so it never covers '+' buttons)
            if (cart.items.isNotEmpty()) {
                val pillShape = RoundedCornerShape(28.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        onClick = { showCheckoutSheet = true },
                        shape = pillShape,
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, Color(0x8CFFFFFF)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .shadow(
                                elevation = 16.dp,
                                shape = pillShape,
                                spotColor = VividGreen,
                                ambientColor = VividGreen
                            )
                            .clip(pillShape)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF83F257), VividGreen, Color(0xFF4BB622))
                                )
                            )
                            .testTag("floating_cart_pill")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCart,
                                    contentDescription = null,
                                    tint = Color(0xFF0A1406),
                                    modifier = Modifier.size(18.dp)
                                )
                                val isSingleItem = abs(cart.totalItems - 1.0) < 1e-6
                                Text(
                                    text = "${ShopLocale.formatNumber(cart.totalItems, currentLanguage)} item${if (isSingleItem) "" else "s"}",
                                    color = Color(0xFF0A1406),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            val cartTotalStr = formatHomeMoney(cart.grandTotal, currentLanguage, false)
                            val cartTotalSize = if (cartTotalStr.length <= 7) 17.sp else 14.5.sp
                            Text(
                                text = cartTotalStr,
                                color = Color(0xFF0A1406),
                                fontSize = cartTotalSize,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Checkout",
                                    color = Color(0xFF0A1406),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color(0xFF0A1406),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Checkout Modal Bottom Sheet
        if (showCheckoutSheet) {
            CheckoutBottomSheet(
                storeViewModel = storeViewModel,
                currentLanguage = currentLanguage,
                isBusy = isBusy,
                onDismiss = { showCheckoutSheet = false }
            )
        }
    }
}

@Composable
fun SellProductTile(
    product: Product,
    inCartQty: Double,
    canAddMore: Boolean,
    isOutOfStock: Boolean,
    currentLanguage: AppLanguage,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLowStock = !isOutOfStock && product.stock <= product.lowStockLimit
    val isInCart = inCartQty > 0.0
    val shape = RoundedCornerShape(20.dp)

    val borderBrush = when {
        isInCart -> Brush.verticalGradient(listOf(VividGreen, VividGreen.copy(alpha = 0.65f)))
        isOutOfStock -> Brush.verticalGradient(listOf(Color(0x20FFFFFF), Color(0x0CFFFFFF)))
        else -> Brush.verticalGradient(listOf(Color(0x42FFFFFF), Color(0x14FFFFFF)))
    }
    val cardBackground = when {
        isInCart -> Brush.verticalGradient(listOf(Color(0xFF213C16), Color(0xFF10180E)))
        isOutOfStock -> Brush.verticalGradient(listOf(Color(0xC91C1E1C), Color(0xD9101210)))
        else -> Brush.verticalGradient(listOf(Color(0xEB262A26), Color(0xEE131513)))
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isInCart) {
                    Modifier.shadow(
                        elevation = 14.dp,
                        shape = shape,
                        spotColor = VividGreen.copy(alpha = 0.45f),
                        ambientColor = VividGreen.copy(alpha = 0.25f)
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(cardBackground)
            .clickable(enabled = canAddMore, onClick = onAdd)
            .testTag("sell_tile_${product.id}"),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(if (isInCart) 1.5.dp else 1.dp, borderBrush)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 136.dp)
                .clip(shape)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isOutOfStock) 0.48f else 1f)
                    .padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Hero Photo or Glowing Green Circular Icon
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (product.photoUri.isNotBlank()) {
                        ProductThumbnail(
                            photoUri = product.photoUri,
                            category = product.category,
                            contentDescription = product.name,
                            modifier = Modifier
                                .size(50.dp)
                                .border(
                                    width = 1.5.dp,
                                    color = if (isInCart) VividGreen else Color(0x36FFFFFF),
                                    shape = RoundedCornerShape(14.dp)
                                ),
                            iconSize = 24.dp,
                            cornerRadius = 14.dp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .shadow(
                                    elevation = if (isOutOfStock) 0.dp else 10.dp,
                                    shape = CircleShape,
                                    spotColor = VividGreen.copy(alpha = 0.4f),
                                    ambientColor = VividGreen.copy(alpha = 0.2f)
                                )
                                .clip(CircleShape)
                                .background(
                                    if (isOutOfStock) Color(0x241B1E1B) else Color(0x30254E14)
                                )
                                .border(
                                    width = 1.6.dp,
                                    color = if (isOutOfStock) Color(0x38FFFFFF) else VividGreen,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getCategoryIcon(product.category),
                                contentDescription = product.name,
                                tint = if (isOutOfStock) TextMuted else VividGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Product Name & Price
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = product.name,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        lineHeight = 16.sp,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.defaultMinSize(minHeight = 32.dp)
                    )
                    Text(
                        text = "${formatHomeMoney(product.sellPrice, currentLanguage, false)}/${ShopLocale.displayUnit(product.unit, currentLanguage)}",
                        color = if (isOutOfStock) TextSecondary else VividGreen,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Bottom Stock Dot + Remaining Qty + Add (+) Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        val dotColor = when {
                            isOutOfStock -> AlertRed
                            isLowStock -> AmberLowSell
                            else -> VividGreen
                        }
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Text(
                            text = "${ShopLocale.formatNumber(product.stock, currentLanguage)} ${ShopLocale.displayUnit(product.unit, currentLanguage)}",
                            color = if (isOutOfStock) AlertRed else TextSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        onClick = onAdd,
                        enabled = canAddMore,
                        shape = CircleShape,
                        color = when {
                            isInCart && canAddMore -> VividGreen.copy(alpha = 0.24f)
                            canAddMore -> Color(0x28FFFFFF)
                            else -> Color(0x14FFFFFF)
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = when {
                                isInCart && canAddMore -> VividGreen.copy(alpha = 0.78f)
                                canAddMore -> Color(0x38FFFFFF)
                                else -> Color(0x18FFFFFF)
                            }
                        ),
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("add_to_cart_${product.id}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add ${product.name}",
                                tint = when {
                                    isInCart && canAddMore -> VividGreen
                                    canAddMore -> TextPrimary
                                    else -> TextMuted
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Low Badge (top-left if cart count badge also present, else top-right)
            if (isLowStock) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xE03E2D08),
                    border = BorderStroke(1.dp, AmberLowSell.copy(alpha = 0.85f)),
                    modifier = Modifier
                        .align(if (isInCart) Alignment.TopStart else Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Low",
                        color = AmberLowSell,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // In-Cart Count Badge ("×2") at top-right
            if (isInCart) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xEB2A312A),
                    border = BorderStroke(1.dp, Color(0x52FFFFFF)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "×${ShopLocale.formatNumber(inCartQty, currentLanguage)}",
                        color = TextPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Diagonal "OUT OF STOCK" Red Ribbon across top-right corner
            if (isOutOfStock) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 34.dp, y = 18.dp)
                        .rotate(35f)
                        .width(136.dp)
                        .background(Color(0xE6B92626))
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "OUT OF STOCK",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutBottomSheet(
    storeViewModel: StoreViewModel,
    currentLanguage: AppLanguage,
    isBusy: Boolean,
    onDismiss: () -> Unit
) {
    val cart by storeViewModel.cart.collectAsState()
    val products by storeViewModel.products.collectAsState()
    val sales by storeViewModel.sales.collectAsState()

    var discountInput by remember {
        mutableStateOf(
            if (cart.invoiceDiscount > 0.0) formatCheckoutNumber(cart.invoiceDiscount) else ""
        )
    }
    var discountError by remember { mutableStateOf<String?>(null) }

    var selectedPaymentType by remember { mutableStateOf(cart.paymentType) } // "cash" or "baki"
    var customerNameInput by remember { mutableStateOf(cart.customerName) }
    var customerPhoneInput by remember { mutableStateOf(cart.customerPhone) }
    var notesInput by remember { mutableStateOf(cart.notes) }
    var completedReceipt by remember { mutableStateOf<CompletedSaleReceipt?>(null) }

    val allCustomerSummaries by storeViewModel.allCustomerSummaries.collectAsState()
    val canSeeProfit by storeViewModel.canSeeBuyingPriceAndProfitFlow.collectAsState()
    val canBaki by storeViewModel.canAccessCustomersAndBakiFlow.collectAsState()
    val canCashBank by storeViewModel.canAccessCashAndBankFlow.collectAsState()
    val isSimpleMode by storeViewModel.isSimpleMode.collectAsState()
    var showCustomerPicker by remember { mutableStateOf(false) }
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var editingQtyLine by remember { mutableStateOf<Triple<String, SaleItem, Product>?>(null) }

    // Real next sequential invoice number derived from existing store sales
    val previewInvoiceNo = remember(sales) {
        val maxSeq = sales.mapNotNull { sale ->
            Regex("(\\d+)$").find(sale.invoiceNo.trim())?.groupValues?.getOrNull(1)?.toLongOrNull()
        }.maxOrNull() ?: 100L
        CalculationEngine.formatInvoiceNumber(maxSeq + 1L)
    }
    val todayDateText = remember {
        SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date())
    }

    if (!canBaki && selectedPaymentType == "baki") {
        selectedPaymentType = "cash"
        storeViewModel.setPaymentType("cash")
    }

    if (showCustomerPicker) {
        CustomerPickerDialog(
            customerSummaries = allCustomerSummaries,
            currentLanguage = currentLanguage,
            canAddCustomer = canBaki,
            onDismiss = { showCustomerPicker = false },
            onSelectCustomer = { cust ->
                customerNameInput = cust.name
                customerPhoneInput = cust.mobile
                storeViewModel.selectCustomerForCheckout(cust)
            },
            onSelectCashSale = {
                customerNameInput = "Cash Sale"
                customerPhoneInput = ""
                storeViewModel.setCashSaleForCheckout()
            },
            onAddNewCustomer = {
                showAddCustomerDialog = true
            }
        )
    }

    if (showAddCustomerDialog) {
        val existingCustomers by storeViewModel.customers.collectAsState()
        AddEditCustomerDialog(
            existingCustomers = existingCustomers,
            currentLanguage = currentLanguage,
            onDismiss = { showAddCustomerDialog = false },
            onConfirm = { name, mobile, address, notes ->
                storeViewModel.addCustomer(name, mobile, address, notes) { created ->
                    customerNameInput = created.name
                    customerPhoneInput = created.mobile
                    storeViewModel.selectCustomerForCheckout(created)
                    showAddCustomerDialog = false
                }
            }
        )
    }

    val activeEdit = editingQtyLine
    if (activeEdit != null) {
        val (editKey, editItem, editProd) = activeEdit
        val otherStock = CalculationEngine.roundStock(
            cart.items.entries
                .filter { it.key != editKey && it.value.productId == editProd.id }
                .sumOf { CalculationEngine.saleItemStockQty(it.value) }
        )
        val remainingStock = CalculationEngine.roundStock((editProd.stock - otherStock).coerceAtLeast(0.0))
        val factor = CalculationEngine.sellFactorForUnit(editProd, editItem.unit)
        val maxInUnit = CalculationEngine.maxSellableQtyInUnit(remainingStock, factor)
        EditCartLineQtyDialog(
            item = editItem,
            maxInUnit = maxInUnit,
            currentLanguage = currentLanguage,
            onDismiss = { editingQtyLine = null },
            onConfirm = { newQty ->
                storeViewModel.updateCartItemQty(editKey, editProd, newQty)
                editingQtyLine = null
            }
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color(0xFF0B0E0B),
        scrimColor = Color(0xB3000000)
    ) {
        if (completedReceipt != null) {
            DigitalReceiptContent(
                receipt = completedReceipt!!,
                onDismiss = onDismiss,
                onStartNewSale = {
                    completedReceipt = null
                    onDismiss()
                }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0B0E0B))
                    .drawBehind {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    VividGreen.copy(alpha = 0.22f),
                                    Color(0xFF10250A).copy(alpha = 0.28f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.5f, size.height * 0.12f),
                                radius = size.width * 0.88f
                            )
                        )
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header: Close + "Checkout" + Invoice & Date Pill (guaranteed no cutoff on 320dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "Checkout",
                                color = TextPrimary,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xEB1D211D),
                            border = BorderStroke(1.dp, Color(0x32FFFFFF))
                        ) {
                            Text(
                                text = "$previewInvoiceNo · $todayDateText",
                                color = Color(0xFFD6E2D2),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }

                    // Glowing Hero TOTAL Card (matching Image 2)
                    val totalCardShape = RoundedCornerShape(22.dp)
                    val grandTotalText = formatHomeMoney(cart.grandTotal, currentLanguage, false)
                    val totalFontSize = when {
                        grandTotalText.length <= 7 -> 38.sp
                        grandTotalText.length <= 10 -> 32.sp
                        else -> 26.sp
                    }
                    Surface(
                        shape = totalCardShape,
                        color = Color.Transparent,
                        border = BorderStroke(1.5.dp, VividGreen.copy(alpha = 0.85f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 22.dp,
                                shape = totalCardShape,
                                spotColor = VividGreen.copy(alpha = 0.45f),
                                ambientColor = VividGreen.copy(alpha = 0.25f)
                            )
                            .clip(totalCardShape)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color(0xFF285117), Color(0xFF0E170B))
                                )
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "TOTAL",
                                color = Color(0xFFD6E2D2),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.2.sp,
                                maxLines = 1,
                                softWrap = false
                            )

                            Text(
                                text = grandTotalText,
                                color = VividGreen,
                                fontSize = totalFontSize,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )

                            HorizontalDivider(
                                color = Color(0x24FFFFFF),
                                modifier = Modifier.padding(vertical = 2.dp)
                            )

                            val subtotalStr = formatHomeMoney(cart.subtotal, currentLanguage, false)
                            val discountStr = formatHomeMoney(cart.totalDiscount, currentLanguage, false)
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(color = Color(0xFFD6E2D2))) {
                                        append("Subtotal $subtotalStr")
                                    }
                                    if (cart.totalDiscount > 0.0) {
                                        withStyle(SpanStyle(color = TextSecondary)) {
                                            append("  —  Discount ")
                                        }
                                        withStyle(SpanStyle(color = VividGreen, fontWeight = FontWeight.Bold)) {
                                            append(discountStr)
                                        }
                                    }
                                },
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (canSeeProfit) {
                                Text(
                                    text = "Projected Profit: ${formatHomeMoney(cart.projectedProfit, currentLanguage, false)}",
                                    color = Color(0xFFA7EF85),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    // Grouped Cart Items Card with Quantity Steppers & Unit Selector (enforcing stock limits)
                    val cartEntries = cart.items.entries.toList()
                    if (cartEntries.isNotEmpty()) {
                        val itemsCardShape = RoundedCornerShape(20.dp)
                        Surface(
                            shape = itemsCardShape,
                            color = Color.Transparent,
                            border = BorderStroke(
                                width = 1.dp,
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color(0x3CFFFFFF), Color(0x14FFFFFF))
                                )
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(itemsCardShape)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color(0xEB232723), Color(0xEE131513))
                                    )
                                )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                cartEntries.forEachIndexed { idx, (lineKey, item) ->
                                    val prod = products.find { it.id == item.productId }
                                    val unitLabel = ShopLocale.displayUnit(item.unit, currentLanguage)
                                    val sellUnitOptions = if (prod != null) {
                                        CalculationEngine.availableSellUnits(prod)
                                    } else {
                                        listOf(item.unit to 1.0)
                                    }
                                    val otherLinesStock = CalculationEngine.roundStock(
                                        cart.items.entries
                                            .filter { it.key != lineKey && it.value.productId == item.productId }
                                            .sumOf { CalculationEngine.saleItemStockQty(it.value) }
                                    )
                                    val canIncrementOne = prod != null && CalculationEngine.isStockSufficient(
                                        prod.stock,
                                        CalculationEngine.roundStock(
                                            otherLinesStock + CalculationEngine.saleItemStockQty(
                                                CalculationEngine.buildSaleItem(prod, item.qty + 1.0, item.unit)
                                            )
                                        )
                                    )
                                    val usedUnitsForProduct = cart.items.values
                                        .filter { it.productId == item.productId }
                                        .map { it.unit.trim().lowercase() }
                                        .toSet()
                                    val nextUnusedUnit = sellUnitOptions.firstOrNull {
                                        it.first.trim().lowercase() !in usedUnitsForProduct
                                    }?.first

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            ProductThumbnail(
                                                photoUri = prod?.photoUri ?: "",
                                                category = prod?.category ?: "",
                                                contentDescription = item.name,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .border(1.dp, Color(0x2EFFFFFF), CircleShape),
                                                iconSize = 18.dp,
                                                cornerRadius = 20.dp
                                            )

                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = item.name,
                                                    color = TextPrimary,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${ShopLocale.formatMoney(item.sellPrice, currentLanguage)}/$unitLabel",
                                                    color = TextSecondary,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }

                                            // Pill Quantity Stepper + tappable decimal qty badge
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = Color(0x362C312C),
                                                border = BorderStroke(1.dp, Color(0x2CFFFFFF))
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)
                                                ) {
                                                    IconButton(
                                                        onClick = {
                                                            if (prod != null) {
                                                                val nextQty = if (item.qty <= 1.0) 0.0 else CalculationEngine.roundStock(item.qty - 1.0)
                                                                storeViewModel.updateCartItemQty(lineKey, prod, nextQty)
                                                            }
                                                        },
                                                        enabled = prod != null,
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (item.qty <= 1.0) Icons.Default.DeleteOutline else Icons.Default.Remove,
                                                            contentDescription = "Decrease quantity",
                                                            tint = if (item.qty <= 1.0) AlertRed else TextPrimary,
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                    }

                                                    Text(
                                                        text = "×${ShopLocale.formatNumber(item.qty, currentLanguage)} $unitLabel",
                                                        color = TextPrimary,
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .clickable(enabled = prod != null) {
                                                                if (prod != null) {
                                                                    editingQtyLine = Triple(lineKey, item, prod)
                                                                }
                                                            }
                                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                                            .testTag("checkout_qty_btn_$lineKey")
                                                    )

                                                    IconButton(
                                                        onClick = {
                                                            if (prod != null) {
                                                                storeViewModel.updateCartItemQty(
                                                                    lineKey,
                                                                    prod,
                                                                    CalculationEngine.roundStock(item.qty + 1.0)
                                                                )
                                                            }
                                                        },
                                                        enabled = canIncrementOne,
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Add,
                                                            contentDescription = "Increase quantity",
                                                            tint = if (canIncrementOne) VividGreen else TextMuted,
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Text(
                                                text = ShopLocale.formatMoney(item.lineTotal, currentLanguage),
                                                color = if (idx == 0) VividGreen else TextPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false,
                                                textAlign = TextAlign.End
                                            )
                                        }

                                        if (prod != null && sellUnitOptions.size > 1) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                sellUnitOptions.forEach { (optUnit, _) ->
                                                    val isSelectedUnit = optUnit.equals(item.unit, ignoreCase = true)
                                                    Surface(
                                                        onClick = {
                                                            if (!isSelectedUnit) {
                                                                storeViewModel.updateCartItemLine(
                                                                    lineKey = lineKey,
                                                                    product = prod,
                                                                    newQty = item.qty,
                                                                    newUnit = optUnit
                                                                )
                                                            }
                                                        },
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = if (isSelectedUnit) VividGreen.copy(alpha = 0.22f) else Color(0x16FFFFFF),
                                                        border = BorderStroke(
                                                            1.dp,
                                                            if (isSelectedUnit) VividGreen else Color(0x28FFFFFF)
                                                        ),
                                                        modifier = Modifier.testTag("checkout_unit_${lineKey}_$optUnit")
                                                    ) {
                                                        Text(
                                                            text = ShopLocale.displayUnit(optUnit, currentLanguage),
                                                            color = if (isSelectedUnit) VividGreen else TextSecondary,
                                                            fontSize = 11.sp,
                                                            fontWeight = if (isSelectedUnit) FontWeight.Bold else FontWeight.Medium,
                                                            maxLines = 1,
                                                            softWrap = false,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }

                                                if (nextUnusedUnit != null) {
                                                    Surface(
                                                        onClick = { storeViewModel.addSecondaryUnitLineToCart(prod) },
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = Color(0x1A81F453),
                                                        border = BorderStroke(1.dp, VividGreen.copy(alpha = 0.55f)),
                                                        modifier = Modifier.testTag("add_unit_line_$lineKey")
                                                    ) {
                                                        Text(
                                                            text = "+ ${ShopLocale.displayUnit(nextUnusedUnit, currentLanguage)}",
                                                            color = VividGreen,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            softWrap = false,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (idx < cartEntries.lastIndex) {
                                        HorizontalDivider(
                                            color = Color(0x16FFFFFF),
                                            modifier = Modifier.padding(horizontal = 14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Discount Block (strictly bounded, inline card matching Image 2)
                    val discountFocusRequester = remember { FocusRequester() }
                    var isDiscountFocused by remember { mutableStateOf(false) }
                    val discountShape = RoundedCornerShape(18.dp)
                    Surface(
                        onClick = { discountFocusRequester.requestFocus() },
                        shape = discountShape,
                        color = Color.Transparent,
                        border = BorderStroke(
                            width = if (discountError != null || isDiscountFocused) 1.3.dp else 1.dp,
                            color = when {
                                discountError != null -> AlertRed
                                isDiscountFocused -> VividGreen.copy(alpha = 0.78f)
                                else -> Color(0x32FFFFFF)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(discountShape)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color(0xEB222522), Color(0xEE131513))
                                )
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Discount",
                                    color = TextPrimary,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = ShopLocale.CURRENCY,
                                    color = if (discountInput.isBlank()) TextMuted else VividGreen,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 24.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (discountInput.isEmpty()) {
                                        Text(
                                            text = "0",
                                            color = TextMuted,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                    BasicTextField(
                                        value = discountInput,
                                        onValueChange = { input ->
                                            discountInput = input
                                            val disc = input.toDoubleOrNull() ?: 0.0
                                            if (disc > cart.subtotal) {
                                                discountError = "Discount cannot exceed ৳${String.format(Locale.US, "%.0f", cart.subtotal)}"
                                            } else if (disc < 0.0) {
                                                discountError = "Discount cannot be negative"
                                            } else {
                                                discountError = null
                                                storeViewModel.setInvoiceDiscount(disc)
                                            }
                                        },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        cursorBrush = SolidColor(VividGreen),
                                        textStyle = TextStyle(
                                            color = VividGreen,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(discountFocusRequester)
                                            .onFocusChanged { isDiscountFocused = it.isFocused }
                                            .testTag("checkout_discount_input")
                                    )
                                }
                            }

                            Text(
                                text = discountError ?: "Cannot be more than the price",
                                color = if (discountError != null) AlertRed else TextSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = if (discountError != null) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Segmented Pill Toggle: Paid in Full vs Baki (Credit)
                    val toggleTrackShape = RoundedCornerShape(26.dp)
                    Surface(
                        shape = toggleTrackShape,
                        color = Color(0xEB151815),
                        border = BorderStroke(1.dp, Color(0x2EFFFFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val cashSelected = selectedPaymentType == "cash"
                            val cashShape = RoundedCornerShape(22.dp)
                            Surface(
                                onClick = {
                                    selectedPaymentType = "cash"
                                    storeViewModel.setPaymentType("cash")
                                },
                                shape = cashShape,
                                color = Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 44.dp)
                                    .then(
                                        if (cashSelected) {
                                            Modifier.shadow(
                                                elevation = 10.dp,
                                                shape = cashShape,
                                                spotColor = VividGreen.copy(alpha = 0.6f),
                                                ambientColor = VividGreen.copy(alpha = 0.3f)
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clip(cashShape)
                                    .background(
                                        brush = if (cashSelected) {
                                            Brush.horizontalGradient(
                                                colors = listOf(Color(0xFF7CEF4F), VividGreen)
                                            )
                                        } else {
                                            SolidColor(Color.Transparent)
                                        }
                                    )
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = "Paid in Full",
                                        color = if (cashSelected) Color(0xFF0A1406) else TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (canBaki) {
                                val bakiSelected = selectedPaymentType == "baki"
                                val bakiShape = RoundedCornerShape(22.dp)
                                Surface(
                                    onClick = {
                                        selectedPaymentType = "baki"
                                        storeViewModel.setPaymentType("baki")
                                    },
                                    shape = bakiShape,
                                    color = if (bakiSelected) Color(0xFFFFB300) else Color.Transparent,
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 44.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = "Baki (Credit)",
                                            color = if (bakiSelected) Color(0xFF1A1100) else TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Horizontal Customer Picker Row (Cash Sale + Real Customers + "+ Contact")
                    val isCashSale = customerNameInput.trim().equals("Cash Sale", ignoreCase = true) || customerNameInput.isBlank()
                    val isBakiBlockedForCash = selectedPaymentType == "baki" && isCashSale
                    val quickCustomers = remember(allCustomerSummaries) {
                        allCustomerSummaries.take(6)
                    }

                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("checkout_customer_picker_row"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            CheckoutCustomerTile(
                                initial = "C",
                                label = "Cash Sale",
                                isSelected = isCashSale,
                                isError = isBakiBlockedForCash,
                                onClick = {
                                    customerNameInput = "Cash Sale"
                                    customerPhoneInput = ""
                                    storeViewModel.setCashSaleForCheckout()
                                }
                            )
                        }

                        items(quickCustomers, key = { it.customer.id }) { summary ->
                            val cust = summary.customer
                            val isSelected = !isCashSale && (
                                cart.customerId == cust.id ||
                                    customerNameInput.equals(cust.name, ignoreCase = true)
                                )
                            CheckoutCustomerTile(
                                initial = cust.name.trim().firstOrNull()?.uppercase() ?: "?",
                                label = cust.name,
                                isSelected = isSelected,
                                isError = false,
                                onClick = {
                                    customerNameInput = cust.name
                                    customerPhoneInput = cust.mobile
                                    storeViewModel.selectCustomerForCheckout(cust)
                                }
                            )
                        }

                        item {
                            CheckoutCustomerTile(
                                initial = "+",
                                label = "+ Contact",
                                isSelected = false,
                                isError = false,
                                isAddAction = true,
                                onClick = { showCustomerPicker = true },
                                modifier = Modifier.testTag("checkout_customer_selector_card")
                            )
                        }
                    }

                    // Warning message if Baki is selected with Cash Sale
                    if (isBakiBlockedForCash) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = AlertRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Choose a customer for Baki",
                                color = AlertRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (!isSimpleMode && canCashBank && selectedPaymentType == "cash") {
                        AccountPickerField(
                            selectedAccountId = cart.accountId,
                            selectedAccountName = cart.accountName,
                            onAccountSelected = { acc ->
                                storeViewModel.updateCartAccount(acc.id, acc.name)
                            },
                            storeViewModel = storeViewModel,
                            label = "Payment Account"
                        )
                    }

                    if (!isSimpleMode) {
                        // Notes Glass Field (matching Image 2)
                        val notesFocusRequester = remember { FocusRequester() }
                        var isNotesFocused by remember { mutableStateOf(false) }
                        val notesShape = RoundedCornerShape(16.dp)
                        Surface(
                            onClick = { notesFocusRequester.requestFocus() },
                            shape = notesShape,
                            color = Color.Transparent,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isNotesFocused) VividGreen.copy(alpha = 0.72f) else Color(0x2CFFFFFF)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(notesShape)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color(0xEB222522), Color(0xEE131513))
                                    )
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Note,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (notesInput.isEmpty()) {
                                        Text(
                                            text = "Notes",
                                            color = TextSecondary,
                                            fontSize = 14.sp,
                                            maxLines = 1
                                        )
                                    }
                                    BasicTextField(
                                        value = notesInput,
                                        onValueChange = {
                                            notesInput = it
                                            storeViewModel.setNotes(it)
                                        },
                                        singleLine = true,
                                        cursorBrush = SolidColor(VividGreen),
                                        textStyle = TextStyle(
                                            color = TextPrimary,
                                            fontSize = 14.sp
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(notesFocusRequester)
                                            .onFocusChanged { isNotesFocused = it.isFocused }
                                            .testTag("checkout_notes")
                                    )
                                }
                            }
                        }
                    }

                    val checkoutError by storeViewModel.errorMessage.collectAsState()
                    if (checkoutError != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AlertRedContainer,
                            border = BorderStroke(1.dp, AlertRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = AlertRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = checkoutError ?: "",
                                    color = AlertRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Slide to Confirm Sale Component
                    val isBakiBlocked = selectedPaymentType == "baki" && (customerNameInput.trim().equals("Cash Sale", ignoreCase = true) || customerNameInput.isBlank())
                    val isSliderAllowed = (discountError == null) && (cart.items.isNotEmpty()) && !isBusy && !isBakiBlocked

                    SlideToConfirmSale(
                        onConfirmed = {
                            storeViewModel.checkoutCurrentCart { receipt ->
                                completedReceipt = receipt
                            }
                        },
                        enabled = isSliderAllowed,
                        text = if (selectedPaymentType == "baki") "Slide to confirm Baki sale" else "Slide to confirm sale"
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckoutCustomerTile(
    initial: String,
    label: String,
    isSelected: Boolean,
    isError: Boolean,
    isAddAction: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tileShape = RoundedCornerShape(16.dp)
    val borderColor = when {
        isError -> AlertRed
        isSelected -> VividGreen
        else -> Color(0x2EFFFFFF)
    }
    val bgBrush = when {
        isError -> Brush.verticalGradient(listOf(Color(0xD93E1818), Color(0xE6180C0C)))
        isSelected -> Brush.verticalGradient(listOf(Color(0xD9204114), Color(0xE6111B0D)))
        else -> Brush.verticalGradient(listOf(Color(0xEB232723), Color(0xEE131513)))
    }

    Surface(
        onClick = onClick,
        shape = tileShape,
        color = Color.Transparent,
        border = BorderStroke(if (isSelected || isError) 1.3.dp else 1.dp, borderColor),
        modifier = modifier
            .widthIn(min = 80.dp, max = 94.dp)
            .height(86.dp)
            .clip(tileShape)
            .background(bgBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isError -> AlertRed.copy(alpha = 0.25f)
                            isSelected -> VividGreen.copy(alpha = 0.24f)
                            else -> Color(0x24FFFFFF)
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = when {
                            isError -> AlertRed.copy(alpha = 0.7f)
                            isSelected -> VividGreen.copy(alpha = 0.75f)
                            else -> Color(0x36FFFFFF)
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isAddAction) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(
                        text = initial,
                        color = when {
                            isError -> AlertRed
                            isSelected -> VividGreen
                            else -> TextPrimary
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            Text(
                text = label,
                color = when {
                    isError -> AlertRed
                    isSelected -> VividGreen
                    else -> TextPrimary
                },
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                lineHeight = 13.5.sp,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditCartLineQtyDialog(
    item: SaleItem,
    maxInUnit: Double,
    currentLanguage: AppLanguage,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    val unitLabel = ShopLocale.displayUnit(item.unit, currentLanguage)
    var qtyText by remember(item) {
        mutableStateOf(ShopLocale.formatNumber(item.qty, AppLanguage.EN))
    }
    var error by remember { mutableStateOf<String?>(null) }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(24.dp)
        Surface(
            shape = shape,
            color = Color.Transparent,
            border = BorderStroke(1.dp, Color(0x3CFFFFFF)),
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
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = item.name,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Max: ${ShopLocale.formatNumber(maxInUnit, currentLanguage)} $unitLabel",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false
                )
                GlassTextField(
                    value = qtyText,
                    onValueChange = {
                        qtyText = it
                        error = null
                    },
                    label = "Qty ($unitLabel)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    testTag = "checkout_qty_input"
                )
                if (error != null) {
                    Text(
                        text = error ?: "",
                        color = AlertRed,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
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
                        text = "Apply",
                        onClick = {
                            val parsed = qtyText.trim().toDoubleOrNull()
                            when {
                                parsed == null || parsed <= 0.0 -> error = "Enter quantity > 0"
                                parsed - maxInUnit > CalculationEngine.STOCK_EPSILON -> {
                                    error = "Exceeds max ${ShopLocale.formatNumber(maxInUnit, currentLanguage)} $unitLabel"
                                }
                                else -> onConfirm(CalculationEngine.roundStock(parsed))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "checkout_qty_apply_btn"
                    )
                }
            }
        }
    }
}

private fun formatCheckoutNumber(amount: Double): String {
    val roundedLong = amount.toLong()
    return if (abs(amount - roundedLong.toDouble()) < 0.005) {
        roundedLong.toString()
    } else {
        String.format(Locale.US, "%.2f", amount)
            .trimEnd('0')
            .trimEnd('.')
    }
}
