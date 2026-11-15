package com.example.util

import com.example.data.model.AccountStatementReport
import com.example.data.model.Expense
import com.example.data.model.OtherIncome
import com.example.data.model.Product
import com.example.data.model.Purchase
import com.example.data.model.PurchaseReturn
import com.example.data.model.Sale
import com.example.data.model.Store
import com.example.domain.CustomerStatementReport
import com.example.domain.DayBookEntry
import com.example.domain.LedgerEntryType
import com.example.domain.MonthlyReportData
import com.example.domain.ProfitAndLossStatement
import com.example.domain.StaffReportSummary
import com.example.domain.SupplierStatementReport

object ReportDataBuilders {

    private fun nowFormatted(lang: AppLanguage): String =
        ShopLocale.formatDateLong(System.currentTimeMillis(), lang)

    fun buildMonthlyReportTable(
        reportData: MonthlyReportData,
        year: Int,
        month: Int,
        store: Store?,
        canSeeProfit: Boolean,
        lang: AppLanguage = AppLanguage.EN
    ): ReportTableData {
        val currentMonthLabel = ShopLocale.formatMonthYear(year, month, lang)

        val headers = if (canSeeProfit) {
            listOf(
                ShopLocale.get("col_product", lang),
                ShopLocale.get("col_qty_sold", lang),
                ShopLocale.get("col_total_amount", lang),
                ShopLocale.get("col_profit", lang)
            )
        } else {
            listOf(
                ShopLocale.get("col_product", lang),
                ShopLocale.get("col_qty_sold", lang),
                ShopLocale.get("col_total_amount", lang)
            )
        }

        val rows = reportData.itemsSold.map { stat ->
            val qtyWithUnit = "${ShopLocale.formatNumber(stat.quantitySold, lang)} ${ShopLocale.displayUnit(stat.unit, lang)}"
            if (canSeeProfit) {
                listOf(
                    stat.productName,
                    qtyWithUnit,
                    ShopLocale.formatMoney(stat.totalSalesAmount, lang),
                    ShopLocale.formatMoney(stat.totalProfit, lang)
                )
            } else {
                listOf(
                    stat.productName,
                    qtyWithUnit,
                    ShopLocale.formatMoney(stat.totalSalesAmount, lang)
                )
            }
        }
        val rawRows = reportData.itemsSold.map { stat ->
            if (canSeeProfit) {
                listOf(null, stat.quantitySold.toDouble(), stat.totalSalesAmount, stat.totalProfit)
            } else {
                listOf(null, stat.quantitySold.toDouble(), stat.totalSalesAmount)
            }
        }

        val totalOrdersText = ShopLocale.format("total_orders_row", lang, reportData.totalOrders)
        val totalItemsText = ShopLocale.format("items_count_label", lang, reportData.totalItemsSold)
        val totals = if (canSeeProfit) {
            listOf(
                totalOrdersText,
                totalItemsText,
                ShopLocale.formatMoney(reportData.totalSales, lang),
                ShopLocale.formatMoney(reportData.totalProfit, lang)
            )
        } else {
            listOf(
                totalOrdersText,
                totalItemsText,
                ShopLocale.formatMoney(reportData.totalSales, lang)
            )
        }
        val rawTotals = if (canSeeProfit) {
            listOf(null, reportData.totalItemsSold.toDouble(), reportData.totalSales, reportData.totalProfit)
        } else {
            listOf(null, reportData.totalItemsSold.toDouble(), reportData.totalSales)
        }

        val title = ShopLocale.format("rep_monthly_title", lang, currentMonthLabel)
        val notes = if (canSeeProfit) {
            ShopLocale.format(
                "monthly_notes_profit",
                lang,
                ShopLocale.formatMoney(reportData.stockValueAtCost, lang),
                ShopLocale.formatMoney(reportData.bakiCustomersOwe, lang),
                ShopLocale.formatMoney(reportData.debtToSuppliers, lang)
            )
        } else {
            ShopLocale.format(
                "monthly_notes_no_profit",
                lang,
                ShopLocale.formatMoney(reportData.bakiCustomersOwe, lang),
                ShopLocale.formatMoney(reportData.debtToSuppliers, lang)
            )
        }

        return ReportTableData(
            title = title,
            storeName = store?.name ?: ShopLocale.get("receipt_header_store_default", lang),
            storePhone = store?.phone ?: "",
            storeAddress = store?.address ?: "",
            periodLabel = currentMonthLabel,
            generatedAt = nowFormatted(lang),
            headers = headers,
            rows = rows,
            numericColumns = if (canSeeProfit) setOf(1, 2, 3) else setOf(1, 2),
            rawNumbers = rawRows,
            totals = totals,
            rawTotals = rawTotals,
            notes = notes
        )
    }

    fun buildSalesReportTable(
        sales: List<Sale>,
        store: Store?,
        periodLabel: String,
        canSeeProfit: Boolean,
        lang: AppLanguage = AppLanguage.EN
    ): ReportTableData {
        val totalAmount = sales.sumOf { it.grandTotal }
        val totalDiscount = sales.sumOf { it.discount }
        val totalProfit = sales.sumOf { it.profit }

        val rows = sales.map { s ->
            val dateStr = s.createdAt?.toDate()?.time?.let { ShopLocale.formatDateShort(it, lang) } ?: "—"
            val status = if (s.paymentType == "baki") ShopLocale.get("status_baki", lang) else ShopLocale.get("status_paid", lang)
            val custName = ShopLocale.displayCustomerName(s.customerName, lang)
            if (canSeeProfit) {
                listOf(
                    s.invoiceNo,
                    dateStr,
                    custName,
                    ShopLocale.formatMoney(s.grandTotal, lang),
                    ShopLocale.formatMoney(s.discount, lang),
                    ShopLocale.formatMoney(s.profit, lang),
                    status
                )
            } else {
                listOf(
                    s.invoiceNo,
                    dateStr,
                    custName,
                    ShopLocale.formatMoney(s.grandTotal, lang),
                    ShopLocale.formatMoney(s.discount, lang),
                    status
                )
            }
        }
        val rawRows = sales.map { s ->
            if (canSeeProfit) {
                listOf(null, null, null, s.grandTotal, s.discount, s.profit, null)
            } else {
                listOf(null, null, null, s.grandTotal, s.discount, null)
            }
        }
        val totalLabel = ShopLocale.format("total_orders_row", lang, sales.size)
        val totals = if (canSeeProfit) {
            listOf(
                totalLabel,
                "",
                "",
                ShopLocale.formatMoney(totalAmount, lang),
                ShopLocale.formatMoney(totalDiscount, lang),
                ShopLocale.formatMoney(totalProfit, lang),
                ""
            )
        } else {
            listOf(
                totalLabel,
                "",
                "",
                ShopLocale.formatMoney(totalAmount, lang),
                ShopLocale.formatMoney(totalDiscount, lang),
                ""
            )
        }
        val rawTotals = if (canSeeProfit) {
            listOf(null, null, null, totalAmount, totalDiscount, totalProfit, null)
        } else {
            listOf(null, null, null, totalAmount, totalDiscount, null)
        }

        val headers = if (canSeeProfit) {
            listOf(
                ShopLocale.get("col_invoice", lang),
                ShopLocale.get("col_date", lang),
                ShopLocale.get("col_customer", lang),
                ShopLocale.get("col_total", lang),
                ShopLocale.get("col_discount", lang),
                ShopLocale.get("col_profit", lang),
                ShopLocale.get("col_status", lang)
            )
        } else {
            listOf(
                ShopLocale.get("col_invoice", lang),
                ShopLocale.get("col_date", lang),
                ShopLocale.get("col_customer", lang),
                ShopLocale.get("col_total", lang),
                ShopLocale.get("col_discount", lang),
                ShopLocale.get("col_status", lang)
            )
        }

        return ReportTableData(
            title = ShopLocale.get("rep_sales_title", lang),
            storeName = store?.name ?: ShopLocale.get("receipt_header_store_default", lang),
            storePhone = store?.phone ?: "",
            storeAddress = store?.address ?: "",
            periodLabel = periodLabel,
            generatedAt = nowFormatted(lang),
            headers = headers,
            rows = rows,
            numericColumns = if (canSeeProfit) setOf(3, 4, 5) else setOf(3, 4),
            rawNumbers = rawRows,
            totals = totals,
            rawTotals = rawTotals
        )
    }

    fun buildPurchaseReportTable(
        purchases: List<Purchase>,
        returns: List<PurchaseReturn>,
        store: Store?,
        periodLabel: String,
        lang: AppLanguage = AppLanguage.EN
    ): ReportTableData {
        val totalPurchases = purchases.sumOf { it.totalAmount }
        val totalPaid = purchases.sumOf { it.paidAmount }
        val totalDue = purchases.sumOf { it.dueAmount }

        val rows = purchases.map { p ->
            val dateStr = p.createdAt?.toDate()?.time?.let { ShopLocale.formatDateShort(it, lang) } ?: "—"
            val status = if (p.paymentType == "credit") ShopLocale.get("status_credit_due", lang) else ShopLocale.get("status_paid", lang)
            listOf(
                p.purchaseNo,
                dateStr,
                p.supplierName,
                ShopLocale.formatMoney(p.totalAmount, lang),
                ShopLocale.formatMoney(p.paidAmount, lang),
                ShopLocale.formatMoney(p.dueAmount, lang),
                status
            )
        }
        val rawRows = purchases.map { p ->
            listOf(null, null, null, p.totalAmount, p.paidAmount, p.dueAmount, null)
        }
        val totals = listOf(
            ShopLocale.format("total_orders_row", lang, purchases.size),
            "",
            "",
            ShopLocale.formatMoney(totalPurchases, lang),
            ShopLocale.formatMoney(totalPaid, lang),
            ShopLocale.formatMoney(totalDue, lang),
            ""
        )
        val rawTotals = listOf<Double?>(null, null, null, totalPurchases, totalPaid, totalDue, null)
        return ReportTableData(
            title = ShopLocale.get("rep_purchase_title", lang),
            storeName = store?.name ?: ShopLocale.get("receipt_header_store_default", lang),
            storePhone = store?.phone ?: "",
            storeAddress = store?.address ?: "",
            periodLabel = periodLabel,
            generatedAt = nowFormatted(lang),
            headers = listOf(
                ShopLocale.get("col_order_no", lang),
                ShopLocale.get("col_date", lang),
                ShopLocale.get("col_supplier", lang),
                ShopLocale.get("col_total", lang),
                ShopLocale.get("col_paid", lang),
                ShopLocale.get("col_due", lang),
                ShopLocale.get("col_status", lang)
            ),
            rows = rows,
            numericColumns = setOf(3, 4, 5),
            rawNumbers = rawRows,
            totals = totals,
            rawTotals = rawTotals,
            notes = null
        )
    }

    fun buildDayBookReportTable(
        entries: List<DayBookEntry>,
        store: Store?,
        periodLabel: String,
        lang: AppLanguage = AppLanguage.EN
    ): ReportTableData {
        val totalIn = entries.filter { it.isIncome }.sumOf { it.amount }
        val totalOut = entries.filter { !it.isIncome }.sumOf { it.amount }

        val rows = entries.map { e ->
            val dateStr = ShopLocale.formatDateShort(e.timestamp, lang)
            val direction = if (e.isIncome) ShopLocale.get("flow_in", lang) else ShopLocale.get("flow_out", lang)
            val typeStr = ShopLocale.displayEntryType(e.type, lang)
            val partyStr = ShopLocale.displayCustomerName(e.partyName, lang)
            listOf(
                dateStr,
                typeStr,
                e.referenceNo,
                partyStr,
                direction,
                ShopLocale.formatMoney(e.amount, lang)
            )
        }
        val rawRows = entries.map { e ->
            listOf(null, null, null, null, null, if (e.isIncome) e.amount else -e.amount)
        }
        val totals = listOf(
            ShopLocale.get("net_flow_row", lang),
            "",
            "",
            ShopLocale.format(
                "daybook_totals_note",
                lang,
                ShopLocale.formatMoney(totalIn, lang),
                ShopLocale.formatMoney(totalOut, lang)
            ),
            "",
            ShopLocale.formatMoney(totalIn - totalOut, lang)
        )
        val rawTotals = listOf<Double?>(null, null, null, null, null, totalIn - totalOut)
        return ReportTableData(
            title = ShopLocale.get("rep_day_book_title", lang),
            storeName = store?.name ?: ShopLocale.get("receipt_header_store_default", lang),
            storePhone = store?.phone ?: "",
            storeAddress = store?.address ?: "",
            periodLabel = periodLabel,
            generatedAt = nowFormatted(lang),
            headers = listOf(
                ShopLocale.get("col_datetime", lang),
                ShopLocale.get("col_type", lang),
                ShopLocale.get("col_ref", lang),
                ShopLocale.get("col_party", lang),
                ShopLocale.get("col_flow", lang),
                ShopLocale.get("col_amount", lang)
            ),
            rows = rows,
            numericColumns = setOf(5),
            rawNumbers = rawRows,
            totals = totals,
            rawTotals = rawTotals
        )
    }

    fun buildProfitLossReportTable(
        pnl: ProfitAndLossStatement,
        isStockBased: Boolean,
        store: Store?,
        periodLabel: String,
        lang: AppLanguage = AppLanguage.EN
    ): ReportTableData {
        val cogsMethod = if (isStockBased) ShopLocale.get("pnl_stock_based", lang) else ShopLocale.get("pnl_purchases_based", lang)
        val rows = mutableListOf(
            listOf(ShopLocale.get("pnl_net_sales", lang), ShopLocale.formatMoney(pnl.netSales, lang)),
            listOf(
                ShopLocale.format("pnl_cogs_label", lang, cogsMethod),
                "-${ShopLocale.formatMoney(pnl.costOfGoodsSold, lang)}"
            ),
            listOf(ShopLocale.get("pnl_gross_profit", lang), ShopLocale.formatMoney(pnl.grossProfit, lang)),
            listOf(ShopLocale.get("pnl_other_income", lang), "+${ShopLocale.formatMoney(pnl.otherIncome, lang)}"),
            listOf(ShopLocale.get("pnl_operating_expenses", lang), "-${ShopLocale.formatMoney(pnl.expenses, lang)}")
        )
        pnl.expensesByCategory.forEach { (cat, amt) ->
            rows.add(listOf("  • ${ShopLocale.displayCategory(cat, lang)}", "-${ShopLocale.formatMoney(amt, lang)}"))
        }
        rows.add(listOf(ShopLocale.get("pnl_net_profit", lang), ShopLocale.formatMoney(pnl.netProfit, lang)))

        val rawRows = mutableListOf(
            listOf<Double?>(null, pnl.netSales),
            listOf<Double?>(null, -pnl.costOfGoodsSold),
            listOf<Double?>(null, pnl.grossProfit),
            listOf<Double?>(null, pnl.otherIncome),
            listOf<Double?>(null, -pnl.expenses)
        )
        pnl.expensesByCategory.forEach { (_, amt) ->
            rawRows.add(listOf<Double?>(null, -amt))
        }
        rawRows.add(listOf<Double?>(null, pnl.netProfit))

        return ReportTableData(
            title = ShopLocale.get("rep_pnl_title", lang),
            storeName = store?.name ?: ShopLocale.get("receipt_header_store_default", lang),
            storePhone = store?.phone ?: "",
            storeAddress = store?.address ?: "",
            periodLabel = periodLabel,
            generatedAt = nowFormatted(lang),
            headers = listOf(ShopLocale.get("col_line_item", lang), ShopLocale.get("col_amount", lang)),
            rows = rows,
            numericColumns = setOf(1),
            rawNumbers = rawRows,
            totals = listOf(ShopLocale.get("pnl_net_profit", lang).uppercase(), ShopLocale.formatMoney(pnl.netProfit, lang)),
            rawTotals = listOf(null, pnl.netProfit),
            notes = if (isStockBased) ShopLocale.get("pnl_note_stock", lang) else ShopLocale.get("pnl_note_purchase", lang)
        )
    }

    fun buildExpenseReportTable(
        expenses: List<Expense>,
        store: Store?,
        periodLabel: String,
        lang: AppLanguage = AppLanguage.EN
    ): ReportTableData {
        val totalExpenses = expenses.sumOf { it.amount }
        val rows = expenses.map { exp ->
            listOf(
                ShopLocale.formatDateLongFullMonth(exp.date, lang),
                ShopLocale.displayCategory(exp.category, lang),
                ShopLocale.displayAccountName(exp.accountName, exp.accountId, lang),
                exp.note,
                ShopLocale.formatMoneyFixed2(exp.amount, lang)
            )
        }
        val rawNumbers = expenses.map { exp ->
            listOf<Double?>(null, null, null, null, exp.amount)
        }
        val totalFormatted = ShopLocale.formatMoneyFixed2(totalExpenses, lang)
        val totals = listOf(
            ShopLocale.get("total_expenses_row", lang),
            "",
            "",
            ShopLocale.format("entries_count", lang, expenses.size),
            totalFormatted
        )
        val rawTotals = listOf<Double?>(null, null, null, null, totalExpenses)

        return ReportTableData(
            title = ShopLocale.get("rep_expense_title", lang),
            storeName = store?.name ?: ShopLocale.get("receipt_header_store_default", lang),
            storePhone = store?.phone ?: "",
            storeAddress = store?.address ?: "",
            periodLabel = periodLabel,
            generatedAt = nowFormatted(lang),
            headers = listOf(
                ShopLocale.get("col_date", lang),
                ShopLocale.get("col_category", lang),
                ShopLocale.get("col_account", lang),
                ShopLocale.get("col_note", lang),
                ShopLocale.get("col_amount", lang)
            ),
            rows = rows,
            numericColumns = setOf(4),
            rawNumbers = rawNumbers,
            totals = totals,
            rawTotals = rawTotals,
            notes = "${ShopLocale.get("total_expenses_row", lang)}: $totalFormatted"
        )
    }

    fun buildOtherIncomeReportTable(
        incomes: List<OtherIncome>,
        store: Store?,
        periodLabel: String,
        lang: AppLanguage = AppLanguage.EN
    ): ReportTableData {
        val totalIncome = incomes.sumOf { it.amount }
        val rows = incomes.map { inc ->
            listOf(
                ShopLocale.formatDateLongFullMonth(inc.date, lang),
                ShopLocale.displayCategory(inc.category, lang),
                ShopLocale.displayAccountName(inc.accountName, inc.accountId, lang),
                inc.note,
                ShopLocale.formatMoneyFixed2(inc.amount, lang)
            )
        }
        val rawNumbers = incomes.map { inc ->
            listOf<Double?>(null, null, null, null, inc.amount)
        }
        val totalFormatted = "+${ShopLocale.formatMoneyFixed2(totalIncome, lang)}"
        val totals = listOf(
            ShopLocale.get("total_other_income_row", lang),
            "",
            "",
            ShopLocale.format("entries_count", lang, incomes.size),
            totalFormatted
        )
        val rawTotals = listOf<Double?>(null, null, null, null, totalIncome)

        return ReportTableData(
            title = ShopLocale.get("rep_other_income_title", lang),
            storeName = store?.name ?: ShopLocale.get("receipt_header_store_default", lang),
            storePhone = store?.phone ?: "",
            storeAddress = store?.address ?: "",
            periodLabel = periodLabel,
            generatedAt = nowFormatted(lang),
            headers = listOf(
                ShopLocale.get("col_date", lang),
                ShopLocale.get("col_category", lang),
                ShopLocale.get("col_account", lang),
                ShopLocale.get("col_note", lang),
                ShopLocale.get("col_amount", lang)
            ),
            rows = rows,
            numericColumns = setOf(4),
            rawNumbers = rawNumbers,
            totals = totals,
            rawTotals = rawTotals,
            notes = "${ShopLocale.get("total_other_income_row", lang)}: ${ShopLocale.formatMoneyFixed2(totalIncome, lang)}"
        )
    }

    fun buildInventoryReportTable(
        products: List<Product>,
        store: Store?,
        canSeeProfit: Boolean,
        lang: AppLanguage = AppLanguage.EN
    ): ReportTableData {
        val now = nowFormatted(lang)
        val totalItemsCount = products.sumOf { it.stock }
        val totalCostValue = products.sumOf { it.stock * it.buyPrice }
        val totalSaleValue = products.sumOf { it.stock * it.sellPrice }

        val rows = products.map { p ->
            val stockText = "${ShopLocale.formatNumber(p.stock, lang)} ${ShopLocale.displayUnit(p.unit, lang)}"
            if (canSeeProfit) {
                listOf(
                    p.name,
                    stockText,
                    ShopLocale.formatMoney(p.buyPrice, lang),
                    ShopLocale.formatMoney(p.sellPrice, lang),
                    ShopLocale.formatMoney(p.stock * p.buyPrice, lang)
                )
            } else {
                listOf(
                    p.name,
                    stockText,
                    ShopLocale.formatMoney(p.sellPrice, lang),
                    ShopLocale.formatMoney(p.stock * p.sellPrice, lang)
                )
            }
        }
        val rawRows = products.map { p ->
            if (canSeeProfit) {
                listOf(null, p.stock.toDouble(), p.buyPrice, p.sellPrice, p.stock * p.buyPrice)
            } else {
                listOf(null, p.stock.toDouble(), p.sellPrice, p.stock * p.sellPrice)
            }
        }
        val totalProductsLabel = ShopLocale.format("total_products_row", lang, products.size)
        val totalItemsLabel = ShopLocale.format("items_count_label", lang, totalItemsCount)
        val totals = if (canSeeProfit) {
            listOf(
                totalProductsLabel,
                totalItemsLabel,
                "",
                "",
                ShopLocale.formatMoney(totalCostValue, lang)
            )
        } else {
            listOf(
                totalProductsLabel,
                totalItemsLabel,
                "",
                ShopLocale.formatMoney(totalSaleValue, lang)
            )
        }
        val rawTotals = if (canSeeProfit) {
            listOf(null, totalItemsCount.toDouble(), null, null, totalCostValue)
        } else {
            listOf(null, totalItemsCount.toDouble(), null, totalSaleValue)
        }
        val headers = if (canSeeProfit) {
            listOf(
                ShopLocale.get("col_product", lang),
                ShopLocale.get("col_stock", lang),
                ShopLocale.get("col_buy_price", lang),
                ShopLocale.get("col_sell_price", lang),
                ShopLocale.get("col_cost_value", lang)
            )
        } else {
            listOf(
                ShopLocale.get("col_product", lang),
                ShopLocale.get("col_stock", lang),
                ShopLocale.get("col_sell_price", lang),
                ShopLocale.get("col_sale_value", lang)
            )
        }
        return ReportTableData(
            title = ShopLocale.get("rep_inventory_title", lang),
            storeName = store?.name ?: ShopLocale.get("receipt_header_store_default", lang),
            storePhone = store?.phone ?: "",
            storeAddress = store?.address ?: "",
            periodLabel = ShopLocale.format("as_of_date", lang, now),
            generatedAt = now,
            headers = headers,
            rows = rows,
            numericColumns = if (canSeeProfit) setOf(1, 2, 3, 4) else setOf(1, 2, 3),
            rawNumbers = rawRows,
            totals = totals,
            rawTotals = rawTotals
        )
    }

    fun buildCustomerStatementReportTable(
        rep: CustomerStatementReport,
        store: Store?,
        periodLabel: String,
        lang: AppLanguage = AppLanguage.EN
    ): ReportTableData {
        val rows = rep.entries.map { e ->
            val dStr = ShopLocale.formatDateShort(e.timestamp, lang)
            val isBakiSale = e.type == LedgerEntryType.BAKI_SALE
            val typeStr = if (isBakiSale) ShopLocale.get("entry_baki_sale", lang) else ShopLocale.get("entry_payment_in", lang)
            val signedAmt = if (isBakiSale) "+${ShopLocale.formatMoney(e.amount, lang)}" else "-${ShopLocale.formatMoney(e.amount, lang)}"
            listOf(
                dStr,
                typeStr,
                e.referenceNo,
                signedAmt,
                ShopLocale.formatMoney(e.runningBalance, lang)
            )
        }
        val rawRows = rep.entries.map { e ->
            val isBakiSale = e.type == LedgerEntryType.BAKI_SALE
            listOf<Double?>(null, null, null, if (isBakiSale) e.amount else -e.amount, e.runningBalance)
        }
        val totals = listOf(
            ShopLocale.get("closing_balance_row", lang),
            "",
            "",
            ShopLocale.format(
                "cust_stmt_totals_note",
                lang,
                ShopLocale.formatMoney(rep.totalBakiSales, lang),
                ShopLocale.formatMoney(rep.totalPayments, lang)
            ),
            ShopLocale.formatMoney(rep.closingBalance, lang)
        )
        val rawTotals = listOf<Double?>(null, null, null, null, rep.closingBalance)
        return ReportTableData(
            title = ShopLocale.format("rep_customer_stmt_title", lang, rep.customer.name),
            storeName = store?.name ?: ShopLocale.get("receipt_header_store_default", lang),
            storePhone = store?.phone ?: "",
            storeAddress = store?.address ?: "",
            periodLabel = periodLabel,
            generatedAt = nowFormatted(lang),
            headers = listOf(
                ShopLocale.get("col_date", lang),
                ShopLocale.get("col_type", lang),
                ShopLocale.get("col_ref", lang),
                ShopLocale.get("col_amount", lang),
                ShopLocale.get("col_balance", lang)
            ),
            rows = rows,
            numericColumns = setOf(3, 4),
            rawNumbers = rawRows,
            totals = totals,
            rawTotals = rawTotals,
            notes = ShopLocale.format("opening_balance_note", lang, ShopLocale.formatMoney(rep.openingBalance, lang))
        )
    }

    fun buildSupplierStatementReportTable(
        rep: SupplierStatementReport,
        store: Store?,
        periodLabel: String,
        lang: AppLanguage = AppLanguage.EN
    ): ReportTableData {
        val rows = rep.entries.map { e ->
            val dStr = ShopLocale.formatDateShort(e.timestamp, lang)
            listOf(
                dStr,
                ShopLocale.displayEntryType(e.type, lang),
                e.referenceNo,
                ShopLocale.formatMoney(e.amount, lang),
                ShopLocale.formatMoney(e.runningBalance, lang)
            )
        }
        val rawRows = rep.entries.map { e ->
            listOf<Double?>(null, null, null, e.amount, e.runningBalance)
        }
        val totals = listOf(
            ShopLocale.get("closing_balance_row", lang),
            "",
            "",
            ShopLocale.format(
                "sup_stmt_totals_note",
                lang,
                ShopLocale.formatMoney(rep.totalPurchases, lang),
                ShopLocale.formatMoney(rep.totalReturns, lang)
            ),
            ShopLocale.formatMoney(rep.closingBalance, lang)
        )
        val rawTotals = listOf<Double?>(null, null, null, null, rep.closingBalance)
        return ReportTableData(
            title = ShopLocale.format("rep_supplier_stmt_title", lang, rep.supplier.name),
            storeName = store?.name ?: ShopLocale.get("receipt_header_store_default", lang),
            storePhone = store?.phone ?: "",
            storeAddress = store?.address ?: "",
            periodLabel = periodLabel,
            generatedAt = nowFormatted(lang),
            headers = listOf(
                ShopLocale.get("col_date", lang),
                ShopLocale.get("col_type", lang),
                ShopLocale.get("col_ref", lang),
                ShopLocale.get("col_amount", lang),
                ShopLocale.get("col_balance", lang)
            ),
            rows = rows,
            numericColumns = setOf(3, 4),
            rawNumbers = rawRows,
            totals = totals,
            rawTotals = rawTotals,
            notes = ShopLocale.format("opening_balance_note", lang, ShopLocale.formatMoney(rep.openingBalance, lang))
        )
    }

    fun buildAccountStatementReportTable(
        rep: AccountStatementReport,
        store: Store?,
        periodLabel: String,
        lang: AppLanguage = AppLanguage.EN
    ): ReportTableData {
        val rows = rep.entries.map { entry ->
            listOf(
                ShopLocale.formatDateShort(entry.timestamp, lang),
                ShopLocale.displayEntryType(entry.type, lang),
                entry.referenceNo,
                if (entry.moneyIn > 0.0) ShopLocale.formatMoneyFixed2(entry.moneyIn, lang) else "—",
                if (entry.moneyOut > 0.0) ShopLocale.formatMoneyFixed2(entry.moneyOut, lang) else "—",
                ShopLocale.formatMoneyFixed2(entry.runningBalance, lang)
            )
        }
        val rawRows = rep.entries.map { entry ->
            listOf(
                null,
                null,
                null,
                if (entry.moneyIn > 0.0) entry.moneyIn else null,
                if (entry.moneyOut > 0.0) entry.moneyOut else null,
                entry.runningBalance
            )
        }
        val totals = listOf(
            ShopLocale.get("closing_balance_row", lang),
            "",
            "",
            ShopLocale.formatMoneyFixed2(rep.totalMoneyIn, lang),
            ShopLocale.formatMoneyFixed2(rep.totalMoneyOut, lang),
            ShopLocale.formatMoneyFixed2(rep.closingBalance, lang)
        )
        val rawTotals = listOf<Double?>(null, null, null, rep.totalMoneyIn, rep.totalMoneyOut, rep.closingBalance)
        val accDisplay = ShopLocale.displayAccountName(rep.account.name, rep.account.id, lang)
        return ReportTableData(
            title = ShopLocale.format("rep_account_stmt_title", lang, accDisplay),
            storeName = store?.name ?: ShopLocale.get("receipt_header_store_default", lang),
            storePhone = store?.phone ?: "",
            storeAddress = store?.address ?: "",
            periodLabel = periodLabel,
            generatedAt = nowFormatted(lang),
            headers = listOf(
                ShopLocale.get("col_date", lang),
                ShopLocale.get("col_type", lang),
                ShopLocale.get("col_ref", lang),
                ShopLocale.get("col_money_in", lang),
                ShopLocale.get("col_money_out", lang),
                ShopLocale.get("col_running_balance", lang)
            ),
            rows = rows,
            numericColumns = setOf(3, 4, 5),
            rawNumbers = rawRows,
            totals = totals,
            rawTotals = rawTotals,
            notes = "${ShopLocale.format("opening_balance_note", lang, ShopLocale.formatMoneyFixed2(rep.openingBalance, lang))} • ${ShopLocale.get("closing_balance_row", lang)}: ${ShopLocale.formatMoneyFixed2(rep.closingBalance, lang)}"
        )
    }

    fun buildStaffReportTable(
        staffSummary: StaffReportSummary,
        store: Store?,
        periodLabel: String,
        canSeeProfit: Boolean,
        lang: AppLanguage = AppLanguage.EN
    ): ReportTableData {
        val headers = if (canSeeProfit) {
            listOf(
                ShopLocale.get("col_staff_member", lang),
                ShopLocale.get("col_role", lang),
                ShopLocale.get("col_orders", lang),
                ShopLocale.get("col_qty_sold", lang),
                ShopLocale.get("col_sales_total", lang),
                ShopLocale.get("col_profit", lang),
                ShopLocale.get("col_payments_in", lang)
            )
        } else {
            listOf(
                ShopLocale.get("col_staff_member", lang),
                ShopLocale.get("col_role", lang),
                ShopLocale.get("col_orders", lang),
                ShopLocale.get("col_qty_sold", lang),
                ShopLocale.get("col_sales_total", lang),
                ShopLocale.get("col_payments_in", lang)
            )
        }
        val rows = staffSummary.rows.map { row ->
            val roleDisplay = ShopLocale.displayStaffRole(row.roleLabel, lang)
            val displayMember = if (row.memberName == "Owner") ShopLocale.get("role_owner", lang) else row.memberName
            if (canSeeProfit) {
                listOf(
                    displayMember,
                    roleDisplay,
                    ShopLocale.formatNumber(row.salesCount, lang),
                    ShopLocale.formatNumber(row.itemsSold, lang),
                    ShopLocale.formatMoney(row.totalSales, lang),
                    ShopLocale.formatMoney(row.profit, lang),
                    ShopLocale.formatMoney(row.paymentsReceived, lang)
                )
            } else {
                listOf(
                    displayMember,
                    roleDisplay,
                    ShopLocale.formatNumber(row.salesCount, lang),
                    ShopLocale.formatNumber(row.itemsSold, lang),
                    ShopLocale.formatMoney(row.totalSales, lang),
                    ShopLocale.formatMoney(row.paymentsReceived, lang)
                )
            }
        }
        val rawRows: List<List<Double?>> = staffSummary.rows.map { row ->
            if (canSeeProfit) {
                listOf(null, null, row.salesCount.toDouble(), row.itemsSold.toDouble(), row.totalSales, row.profit, row.paymentsReceived)
            } else {
                listOf(null, null, row.salesCount.toDouble(), row.itemsSold.toDouble(), row.totalSales, row.paymentsReceived)
            }
        }
        val totalStaffLabel = ShopLocale.format("total_staff_row", lang, staffSummary.rows.size)
        val totals = if (canSeeProfit) {
            listOf(
                totalStaffLabel,
                "",
                ShopLocale.formatNumber(staffSummary.totalSalesCount, lang),
                ShopLocale.formatNumber(staffSummary.totalItemsSold, lang),
                ShopLocale.formatMoney(staffSummary.totalSalesAmount, lang),
                ShopLocale.formatMoney(staffSummary.totalProfit, lang),
                ShopLocale.formatMoney(staffSummary.totalPaymentsReceived, lang)
            )
        } else {
            listOf(
                totalStaffLabel,
                "",
                ShopLocale.formatNumber(staffSummary.totalSalesCount, lang),
                ShopLocale.formatNumber(staffSummary.totalItemsSold, lang),
                ShopLocale.formatMoney(staffSummary.totalSalesAmount, lang),
                ShopLocale.formatMoney(staffSummary.totalPaymentsReceived, lang)
            )
        }
        val rawTotals = if (canSeeProfit) {
            listOf(null, null, staffSummary.totalSalesCount.toDouble(), staffSummary.totalItemsSold.toDouble(), staffSummary.totalSalesAmount, staffSummary.totalProfit, staffSummary.totalPaymentsReceived)
        } else {
            listOf(null, null, staffSummary.totalSalesCount.toDouble(), staffSummary.totalItemsSold.toDouble(), staffSummary.totalSalesAmount, staffSummary.totalPaymentsReceived)
        }
        return ReportTableData(
            title = ShopLocale.get("rep_staff_title", lang),
            storeName = store?.name ?: ShopLocale.get("receipt_header_store_default", lang),
            storePhone = store?.phone ?: "",
            storeAddress = store?.address ?: "",
            periodLabel = periodLabel,
            generatedAt = nowFormatted(lang),
            headers = headers,
            rows = rows,
            numericColumns = if (canSeeProfit) setOf(2, 3, 4, 5, 6) else setOf(2, 3, 4, 5),
            rawNumbers = rawRows,
            totals = totals,
            rawTotals = rawTotals
        )
    }
}
