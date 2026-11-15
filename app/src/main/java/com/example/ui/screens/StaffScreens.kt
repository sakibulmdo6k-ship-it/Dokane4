package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StoreInvite
import com.example.data.model.StoreMember
import com.example.data.model.StorePermissions
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassOutlinedButton
import com.example.ui.components.GlassTextField
import com.example.ui.components.NoPermissionScreen
import com.example.ui.components.PlainEmptyState
import com.example.ui.components.QrCodeView
import com.example.ui.components.VividButton
import com.example.ui.theme.AlertRed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VividGreen
import com.example.ui.viewmodel.StoreViewModel
import com.example.util.AppLanguage
import com.example.util.SyncStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val StaffRoles = listOf("Partner", "Manager", "Accountant", "Sales", "Stock", "Entry")
private val PermissionLevels = listOf("Full", "Half", "Limited", "Selected")

private data class RealPermissionItem(
    val key: String,
    val labelEn: String,
    val labelBn: String
)

private val RealPermissionItems = listOf(
    RealPermissionItem(StorePermissions.SELL, "Sell (Make Sales)", "বিক্রয় করা"),
    RealPermissionItem(StorePermissions.VIEW_STOCK, "View Stock", "স্টক দেখা"),
    RealPermissionItem(StorePermissions.ADD_EDIT_PRODUCTS, "Add & Edit Products", "পণ্য যোগ ও সম্পাদনা"),
    RealPermissionItem(StorePermissions.SEE_BUYING_PRICE_AND_PROFIT, "See Buying Price & Profit", "কেনা দাম ও লাভ দেখা"),
    RealPermissionItem(StorePermissions.HISTORY, "History & Timeline", "লেনদেন ইতিহাস"),
    RealPermissionItem(StorePermissions.CUSTOMERS_AND_BAKI, "Customers & Baki Ledger", "বাকি খাতা ও কাস্টমার"),
    RealPermissionItem(StorePermissions.RECEIVE_PAYMENT, "Receive Customer Payments", "কাস্টমার পেমেন্ট গ্রহণ"),
    RealPermissionItem(StorePermissions.SUPPLIERS_AND_PURCHASES, "Suppliers & Purchases", "সাপ্লায়ার ও ক্রয়"),
    RealPermissionItem(StorePermissions.PAY_SUPPLIER, "Pay Supplier", "সাপ্লায়ারকে পরিশোধ"),
    RealPermissionItem(StorePermissions.REPORTS, "Reports", "ব্যবসায়িক রিপোর্ট"),
    RealPermissionItem(StorePermissions.CASH_AND_BANK, "Cash & Bank Accounts", "ক্যাশ ও ব্যাংক হিসাব"),
    RealPermissionItem(StorePermissions.EXPENSES_AND_OTHER_INCOME, "Expenses & Other Income", "খরচ ও অন্যান্য আয়"),
    RealPermissionItem(StorePermissions.NOTES, "Store Notes", "দোকানের নোট")
)

// -------------------------------------------------------------
// STAFF MANAGEMENT SCREEN (Owner Only)
// -------------------------------------------------------------
@Composable
fun StaffManagementScreen(
    storeViewModel: StoreViewModel,
    modifier: Modifier = Modifier
) {
    val members by storeViewModel.members.collectAsState()
    val invites by storeViewModel.invites.collectAsState()
    val store by storeViewModel.store.collectAsState()
    val currentLanguage by storeViewModel.currentLanguage.collectAsState()
    val isOwner by storeViewModel.isOwner.collectAsState()
    val syncStatus by storeViewModel.syncStatus.collectAsState()

    var showAddSheet by remember { mutableStateOf(false) }
    var createdInviteForDisplay by remember { mutableStateOf<StoreInvite?>(null) }

    BackHandler {
        storeViewModel.navigateBackInBusiness()
    }

    if (!isOwner) {
        NoPermissionScreen(
            onBack = { storeViewModel.navigateBackInBusiness() },
            modifier = modifier
        )
        return
    }

    val activeInvites = remember(invites) {
        invites
            .filter { it.status == "active" && System.currentTimeMillis() <= it.expiresAt }
            .sortedByDescending { it.createdAt }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x2E5DD62C), Color.Transparent),
                        center = Offset(size.width * 0.65f, size.height * 0.14f),
                        radius = size.width * 0.72f
                    ),
                    radius = size.width * 0.72f,
                    center = Offset(size.width * 0.65f, size.height * 0.14f)
                )
            }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("staff_management_screen"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { storeViewModel.navigateBackInBusiness() },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("staff_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (currentLanguage == AppLanguage.BN) "স্টাফ" else "Staff",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable { showAddSheet = true }
                            .testTag("btn_add_staff"),
                        shape = RoundedCornerShape(50),
                        color = Color(0x265DD62C),
                        border = BorderStroke(1.dp, VividGreen.copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = VividGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (currentLanguage == AppLanguage.BN) "স্টাফ যোগ" else "Add Staff",
                                color = VividGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // Multi-Phone Live Sync Hero Card
            item {
                LiveSyncHeroCard(
                    syncStatus = syncStatus,
                    hasActiveInvite = activeInvites.isNotEmpty(),
                    currentLanguage = currentLanguage,
                    onShowQrClick = {
                        val latestInvite = activeInvites.firstOrNull()
                        if (latestInvite != null) {
                            createdInviteForDisplay = latestInvite
                        } else {
                            showAddSheet = true
                        }
                    }
                )
            }

            // Non-admin Staff Notice Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF1A1B1A),
                    border = BorderStroke(1.dp, GlassBorder)
                ) {
                    Text(
                        text = if (currentLanguage == AppLanguage.BN) {
                            "ফুল অ্যাক্সেস থাকলেও স্টাফ অ্যাকাউন্ট কখনো অ্যাডমিন হয় না।"
                        } else {
                            "Staff accounts are never admin, even with Full access."
                        },
                        color = Color(0xFFDADADA),
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                }
            }

            // Pending Invites Section
            if (activeInvites.isNotEmpty()) {
                item {
                    Text(
                        text = if (currentLanguage == AppLanguage.BN) {
                            "সক্রিয় আমন্ত্রণ (${activeInvites.size})"
                        } else {
                            "PENDING INVITES (${activeInvites.size})"
                        },
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(activeInvites, key = { it.code }) { inv ->
                    InviteCard(
                        invite = inv,
                        onViewQr = { createdInviteForDisplay = inv },
                        onCancel = { storeViewModel.cancelInvite(inv.code) }
                    )
                }
            }

            // Team Section
            item {
                Text(
                    text = if (currentLanguage == AppLanguage.BN) {
                        "টিম (${members.size})"
                    } else {
                        "TEAM (${members.size})"
                    },
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (members.isEmpty()) {
                item {
                    PlainEmptyState(
                        message = if (currentLanguage == AppLanguage.BN) {
                            "এখনো কোনো স্টাফ সদস্য নেই। টিম যোগ করতে 'Add Staff' চাপুন।"
                        } else {
                            "No staff members yet. Tap 'Add Staff' or 'Show QR' to invite team members."
                        }
                    )
                }
            } else {
                items(members, key = { it.userId }) { member ->
                    val isStoreOwner = member.userId == store?.ownerId ||
                        member.roleLabel.equals("Owner", ignoreCase = true)
                    MemberRowCard(
                        member = member,
                        isStoreOwner = isStoreOwner,
                        onClick = { storeViewModel.selectMember(member) }
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        AddStaffInviteSheet(
            currentLanguage = currentLanguage,
            onDismiss = { showAddSheet = false },
            onInviteCreated = { inv ->
                showAddSheet = false
                createdInviteForDisplay = inv
            },
            storeViewModel = storeViewModel
        )
    }

    if (createdInviteForDisplay != null) {
        InviteQrDisplayDialog(
            invite = createdInviteForDisplay!!,
            onDismiss = { createdInviteForDisplay = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveSyncHeroCard(
    syncStatus: SyncStatus,
    hasActiveInvite: Boolean,
    currentLanguage: AppLanguage,
    onShowQrClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val statusDotColor = when (syncStatus) {
        SyncStatus.ONLINE -> VividGreen
        SyncStatus.SYNCING -> Color(0xFFFFC107)
        SyncStatus.OFFLINE -> AlertRed
    }
    val statusLabel = when (syncStatus) {
        SyncStatus.ONLINE -> if (currentLanguage == AppLanguage.BN) "ক্লাউড সংযুক্ত" else "Cloud connected"
        SyncStatus.SYNCING -> if (currentLanguage == AppLanguage.BN) "সিঙ্ক হচ্ছে..." else "Syncing..."
        SyncStatus.OFFLINE -> if (currentLanguage == AppLanguage.BN) "অফলাইন" else "Offline"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = shape,
                ambientColor = VividGreen.copy(alpha = 0.25f),
                spotColor = VividGreen.copy(alpha = 0.35f)
            ),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.2.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    VividGreen.copy(alpha = 0.85f),
                    VividGreen.copy(alpha = 0.35f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF33671F),
                            Color(0xFF1C3513),
                            Color(0xFF141813)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Text(
                text = if (currentLanguage == AppLanguage.BN) "মাল্টি-ফোন লাইভ সিঙ্ক" else "Multi-Phone Live Sync",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (currentLanguage == AppLanguage.BN) {
                    "স্টাফের ফোনে সংযোগ করতে ২৪ ঘণ্টার জন্য একটি একবার ব্যবহারযোগ্য QR ও কোড তৈরি করুন।"
                } else {
                    "Add a staff phone: create or show a one-time invite QR valid for 24 hours."
                },
                color = Color(0xFFD5E2D0),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .defaultMinSize(minHeight = 48.dp)
                        .shadow(
                            elevation = 10.dp,
                            shape = RoundedCornerShape(50),
                            ambientColor = VividGreen.copy(alpha = 0.45f),
                            spotColor = VividGreen.copy(alpha = 0.55f)
                        )
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onShowQrClick)
                        .testTag("btn_show_qr"),
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF285319),
                    border = BorderStroke(1.3.dp, VividGreen)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (hasActiveInvite) {
                                if (currentLanguage == AppLanguage.BN) "QR দেখুন" else "Show QR"
                            } else {
                                if (currentLanguage == AppLanguage.BN) "QR তৈরি করুন" else "Show QR"
                            },
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = statusLabel,
                        color = Color(0xFFC9D5C5),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// MEMBER ROW CARD
// -------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemberRowCard(
    member: StoreMember,
    isStoreOwner: Boolean,
    onClick: () -> Unit
) {
    val isRemoved = member.status.equals("Removed", ignoreCase = true)
    val isActive = member.status.equals("Active", ignoreCase = true)
    val displayName = member.displayName.ifBlank {
        if (isStoreOwner) "Store Owner" else "Staff (${member.userId.take(6)})"
    }
    val initial = displayName.trim().firstOrNull()?.uppercaseChar()?.toString()
    val levelBadge = if (isStoreOwner) "Full" else member.permissionLevel.ifBlank { "Limited" }

    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
            .testTag("member_row_${member.userId}"),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            color = if (isStoreOwner) VividGreen.copy(alpha = 0.35f) else GlassBorder
        )
    ) {
        Row(
            modifier = Modifier
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF252625), Color(0xFF1A1B1A))
                    )
                )
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Monogram Avatar
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isStoreOwner -> Color(0x2E5DD62C)
                            isRemoved -> Color(0x2EE53935)
                            else -> Color(0xFF2C2D2C)
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = when {
                            isStoreOwner -> VividGreen.copy(alpha = 0.55f)
                            isRemoved -> AlertRed.copy(alpha = 0.55f)
                            else -> GlassBorder
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (initial != null) {
                    Text(
                        text = initial,
                        color = when {
                            isStoreOwner -> VividGreen
                            isRemoved -> AlertRed
                            else -> TextPrimary
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Icon(
                        imageVector = if (isStoreOwner) Icons.Default.Security else Icons.Default.Person,
                        contentDescription = null,
                        tint = if (isStoreOwner) VividGreen else TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Name · Role · Level badge (wraps gracefully without cutting off)
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = displayName,
                    color = if (isRemoved) TextMuted else TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                Text(
                    text = "·",
                    color = TextMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                Text(
                    text = member.roleLabel.ifBlank { if (isStoreOwner) "Owner" else "Staff" },
                    color = if (isStoreOwner) VividGreen else TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                Surface(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF232423),
                    border = BorderStroke(1.dp, GlassBorder)
                ) {
                    Text(
                        text = levelBadge,
                        color = if (isRemoved) TextMuted else Color(0xFFD8D8D8),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Active / Removed Status
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isActive -> VividGreen
                                isRemoved -> AlertRed
                                else -> TextMuted
                            }
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = member.status.ifBlank { "Active" },
                    color = if (isRemoved) AlertRed else Color(0xFFC2C8C0),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// -------------------------------------------------------------
// PENDING INVITE CARD
// -------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InviteCard(
    invite: StoreInvite,
    onViewQr: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(20.dp)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, VividGreen.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF222821), Color(0xFF181A18))
                    )
                )
                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = invite.staffName.ifBlank { "Invited Staff" },
                        color = TextPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    Surface(
                        modifier = Modifier.align(Alignment.CenterVertically),
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF252825),
                        border = BorderStroke(1.dp, GlassBorder)
                    ) {
                        Text(
                            text = "${invite.roleLabel} · ${invite.permissionLevel}",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Code: ${invite.code}",
                    color = VividGreen,
                    fontSize = 12.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }

            IconButton(
                onClick = onViewQr,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = "View QR",
                    tint = VividGreen
                )
            }

            IconButton(
                onClick = {
                    val shareText = "Join my shop '${invite.storeName}' on StoreOS!\nRole: ${invite.roleLabel}\nInvite Code: ${invite.code}\nValid for 24 hours."
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share StoreOS Invite"))
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share Invite",
                    tint = TextSecondary
                )
            }

            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel Invite",
                    tint = AlertRed
                )
            }
        }
    }
}

// -------------------------------------------------------------
// ADD STAFF MEMBER BOTTOM SHEET
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStaffInviteSheet(
    currentLanguage: AppLanguage,
    onDismiss: () -> Unit,
    onInviteCreated: (StoreInvite) -> Unit,
    storeViewModel: StoreViewModel
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var staffName by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var selectedRole by remember { mutableStateOf("Sales") }
    var selectedLevel by remember { mutableStateOf("Limited") }

    val permissionMap = remember {
        mutableStateMapOf<String, Boolean>().apply {
            putAll(StorePermissions.forLevel("Limited"))
        }
    }

    fun onLevelPicked(level: String) {
        selectedLevel = level
        if (level != "Selected") {
            permissionMap.clear()
            permissionMap.putAll(StorePermissions.forLevel(level))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF171817),
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color(0x4DFFFFFF))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .testTag("add_staff_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (currentLanguage == AppLanguage.BN) "নতুন স্টাফ যোগ করুন" else "Add Staff Member",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            GlassTextField(
                value = staffName,
                onValueChange = {
                    staffName = it
                    if (nameError != null && it.isNotBlank()) nameError = null
                },
                label = if (currentLanguage == AppLanguage.BN) "নাম *" else "Name *",
                placeholder = "e.g. Rahim",
                isError = nameError != null,
                errorMessage = nameError,
                testTag = "input_staff_name"
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = if (currentLanguage == AppLanguage.BN) "দোকানে ভূমিকা" else "Role in shop",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))

            RoleSelectorFlow(
                roles = StaffRoles,
                selectedRole = selectedRole,
                onRoleSelected = { selectedRole = it }
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = if (currentLanguage == AppLanguage.BN) "অনুমতির স্তর" else "Permission level",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))

            PermissionLevelSegmentedBar(
                levels = PermissionLevels,
                selectedLevel = selectedLevel,
                onLevelSelected = ::onLevelPicked
            )

            Spacer(modifier = Modifier.height(14.dp))

            RealPermissionsGrid(
                selectedLevel = selectedLevel,
                permissionMap = permissionMap,
                currentLanguage = currentLanguage,
                onTogglePermission = { key, checked ->
                    if (selectedLevel == "Selected") {
                        permissionMap[key] = checked
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF1F201F),
                border = BorderStroke(1.dp, GlassBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Staff accounts are never admin. Balance adjustments, archiving accounts, and data fixes stay owner-only.",
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            VividButton(
                text = if (currentLanguage == AppLanguage.BN) "আমন্ত্রণ কোড তৈরি করুন" else "Create Invite Code",
                onClick = {
                    val trimmed = staffName.trim()
                    if (trimmed.isBlank()) {
                        nameError = if (currentLanguage == AppLanguage.BN) {
                            "স্টাফের নাম লিখুন"
                        } else {
                            "Staff name is required"
                        }
                        return@VividButton
                    }
                    storeViewModel.createStaffInvite(
                        staffName = trimmed,
                        roleLabel = selectedRole,
                        permissionLevel = selectedLevel,
                        customPermissions = permissionMap.toMap(),
                        onSuccess = onInviteCreated
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                fontSize = 15.5.sp,
                testTag = "btn_submit_invite"
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoleSelectorFlow(
    roles: List<String>,
    selectedRole: String,
    onRoleSelected: (String) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        roles.forEach { role ->
            val isSelected = selectedRole == role
            Surface(
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable { onRoleSelected(role) }
                    .testTag("role_chip_${role.lowercase()}"),
                shape = RoundedCornerShape(50),
                color = if (isSelected) VividGreen else Color(0xFF222322),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) VividGreen else GlassBorder
                )
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = role,
                        color = if (isSelected) DarkBackground else Color(0xFFDADADA),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionLevelSegmentedBar(
    levels: List<String>,
    selectedLevel: String,
    onLevelSelected: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50),
        color = Color(0xFF1F201F),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            levels.forEach { level ->
                val isSelected = selectedLevel.equals(level, ignoreCase = true)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable { onLevelSelected(level) }
                        .testTag("level_pill_${level.lowercase()}"),
                    shape = RoundedCornerShape(50),
                    color = if (isSelected) VividGreen else Color.Transparent
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = level,
                            color = if (isSelected) DarkBackground else Color(0xFFD4D4D4),
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RealPermissionsGrid(
    selectedLevel: String,
    permissionMap: Map<String, Boolean>,
    currentLanguage: AppLanguage,
    onTogglePermission: (String, Boolean) -> Unit
) {
    val canToggle = selectedLevel.equals("Selected", ignoreCase = true)
    val rows = remember(RealPermissionItems) { RealPermissionItems.chunked(2) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF1C1D1C),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (canToggle) {
                    if (currentLanguage == AppLanguage.BN) {
                        "কাস্টম অনুমতি বেছে নিন ($selectedLevel):"
                    } else {
                        "Tap permissions to customize ($selectedLevel):"
                    }
                } else {
                    if (currentLanguage == AppLanguage.BN) {
                        "$selectedLevel স্তরে অন্তর্ভুক্ত অনুমতি (কাস্টমাইজ করতে Selected চাপুন):"
                    } else {
                        "Included in $selectedLevel (tap Selected above to customize):"
                    }
                },
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            rows.forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    pair.forEach { item ->
                        val isChecked = permissionMap[item.key] == true
                        val label = if (currentLanguage == AppLanguage.BN) item.labelBn else item.labelEn

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = canToggle) {
                                    onTogglePermission(item.key, !isChecked)
                                }
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                                .testTag("perm_item_${item.key}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(
                                        when {
                                            isChecked && canToggle -> VividGreen
                                            isChecked -> Color(0xFF28471D)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .border(
                                        width = 1.3.dp,
                                        color = when {
                                            isChecked && canToggle -> VividGreen
                                            isChecked -> VividGreen.copy(alpha = 0.7f)
                                            canToggle -> TextSecondary
                                            else -> TextMuted.copy(alpha = 0.6f)
                                        },
                                        shape = RoundedCornerShape(5.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isChecked) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (canToggle) DarkBackground else VividGreen,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                color = if (isChecked) TextPrimary else TextMuted,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// INVITE QR DISPLAY DIALOG
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteQrDisplayDialog(
    invite: StoreInvite,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)),
            color = Color(0xFF171817),
            border = BorderStroke(1.2.dp, VividGreen.copy(alpha = 0.65f))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Store Invite Code",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Invite for ${invite.staffName} (${invite.roleLabel} · ${invite.permissionLevel})",
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                QrCodeView(
                    content = invite.code,
                    sizeDp = 180
                )

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF212421),
                    border = BorderStroke(1.dp, GlassBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = invite.code,
                            color = VividGreen,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Valid for 24 hours · Usable once",
                            color = TextMuted,
                            fontSize = 11.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassOutlinedButton(
                        text = "Share",
                        onClick = {
                            val shareText = "Join my shop '${invite.storeName}' on StoreOS!\nRole: ${invite.roleLabel}\nInvite Code: ${invite.code}\nValid for 24 hours."
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share StoreOS Invite"))
                        },
                        modifier = Modifier.weight(1f)
                    )

                    VividButton(
                        text = "Done",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// STAFF MEMBER DETAILS & PERMISSION EDIT SCREEN (Owner Only)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffMemberDetailsScreen(
    storeViewModel: StoreViewModel,
    modifier: Modifier = Modifier
) {
    val member = storeViewModel.selectedMember.collectAsState().value
    val store by storeViewModel.store.collectAsState()
    val currentLanguage by storeViewModel.currentLanguage.collectAsState()
    val isOwner by storeViewModel.isOwner.collectAsState()
    val isBusy by storeViewModel.isBusy.collectAsState()

    BackHandler {
        storeViewModel.navigateBackInBusiness()
    }

    if (!isOwner) {
        NoPermissionScreen(
            onBack = { storeViewModel.navigateBackInBusiness() },
            modifier = modifier
        )
        return
    }

    if (member == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            PlainEmptyState(message = "No member selected")
        }
        return
    }

    val isStoreOwner = member.userId == store?.ownerId || member.roleLabel.equals("Owner", ignoreCase = true)
    val isRemoved = member.status.equals("Removed", ignoreCase = true)

    var currentRole by remember(member) { mutableStateOf(member.roleLabel) }
    var currentLevel by remember(member) { mutableStateOf(member.permissionLevel) }

    val permissionMap = remember(member) {
        mutableStateMapOf<String, Boolean>().apply {
            putAll(member.permissions)
        }
    }

    var showConfirmSaveDialog by remember { mutableStateOf(false) }
    var showConfirmRemoveDialog by remember { mutableStateOf(false) }
    var showConfirmRestoreDialog by remember { mutableStateOf(false) }

    fun onLevelPicked(level: String) {
        currentLevel = level
        if (level != "Selected") {
            permissionMap.clear()
            permissionMap.putAll(StorePermissions.forLevel(level))
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("staff_details_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { storeViewModel.navigateBackInBusiness() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = member.displayName.ifBlank { if (isStoreOwner) "Store Owner" else "Staff Member" },
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isStoreOwner) "Owner Account (Protected)" else "Staff Member Details & Access",
                        color = TextSecondary,
                        fontSize = 12.5.sp
                    )
                }
            }
        }

        // Profile Overview Card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val displayName = member.displayName.ifBlank { if (isStoreOwner) "Owner" else "Staff" }
                    val initial = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "S"
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (isStoreOwner) Color(0x335DD62C)
                                else if (isRemoved) Color(0x33E53935)
                                else Color(0xFF2C2D2C)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isStoreOwner) VividGreen.copy(alpha = 0.55f) else GlassBorder,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            color = if (isStoreOwner) VividGreen else if (isRemoved) AlertRed else TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (member.email.isNotBlank()) {
                            Text(
                                text = member.email,
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Status: ${member.status} · Joined: ${SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(member.joinedTime))}",
                            color = if (isRemoved) AlertRed else TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        if (isStoreOwner) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0x225DD62C),
                    border = BorderStroke(1.dp, VividGreen.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = VividGreen)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "This is the store owner record. Owners always have full permissions and cannot be removed or demoted.",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        } else {
            item {
                Text(
                    text = "Role in shop",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                RoleSelectorFlow(
                    roles = StaffRoles,
                    selectedRole = currentRole,
                    onRoleSelected = { currentRole = it }
                )
            }

            item {
                Text(
                    text = "Permission level",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                PermissionLevelSegmentedBar(
                    levels = PermissionLevels,
                    selectedLevel = currentLevel,
                    onLevelSelected = ::onLevelPicked
                )
            }

            item {
                RealPermissionsGrid(
                    selectedLevel = currentLevel,
                    permissionMap = permissionMap,
                    currentLanguage = currentLanguage,
                    onTogglePermission = { key, checked ->
                        if (currentLevel == "Selected") {
                            permissionMap[key] = checked
                        }
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                VividButton(
                    text = "Save Permission Changes",
                    onClick = { showConfirmSaveDialog = true },
                    isLoading = isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isRemoved) {
                    GlassOutlinedButton(
                        text = "Restore Staff Member",
                        onClick = { showConfirmRestoreDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    )
                } else {
                    GlassOutlinedButton(
                        text = "Remove Staff Member",
                        onClick = { showConfirmRemoveDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    )
                }
            }
        }
    }

    if (showConfirmSaveDialog) {
        BasicAlertDialog(onDismissRequest = { showConfirmSaveDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)),
                color = Color(0xFF171817),
                border = BorderStroke(1.dp, GlassBorder)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "Save Permissions?", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Change ${member.displayName}'s role to '$currentRole' with '$currentLevel' permissions? Changes take effect immediately on their phone.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassOutlinedButton(text = "Cancel", onClick = { showConfirmSaveDialog = false }, modifier = Modifier.weight(1f))
                        VividButton(
                            text = "Confirm",
                            onClick = {
                                storeViewModel.updateMemberPermissions(
                                    member = member,
                                    roleLabel = currentRole,
                                    permissionLevel = currentLevel,
                                    permissions = permissionMap.toMap(),
                                    onSuccess = {
                                        showConfirmSaveDialog = false
                                        storeViewModel.navigateBackInBusiness()
                                    }
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    if (showConfirmRemoveDialog) {
        BasicAlertDialog(onDismissRequest = { showConfirmRemoveDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)),
                color = Color(0xFF171817),
                border = BorderStroke(1.dp, GlassBorder)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "Remove Staff Member?", color = AlertRed, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Are you sure you want to remove ${member.displayName}? They will immediately lose access to this store. Their past recorded sales and entries will stay preserved with their name.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassOutlinedButton(text = "Cancel", onClick = { showConfirmRemoveDialog = false }, modifier = Modifier.weight(1f))
                        VividButton(
                            text = "Remove",
                            onClick = {
                                storeViewModel.removeMember(member) {
                                    showConfirmRemoveDialog = false
                                    storeViewModel.navigateBackInBusiness()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    if (showConfirmRestoreDialog) {
        BasicAlertDialog(onDismissRequest = { showConfirmRestoreDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)),
                color = Color(0xFF171817),
                border = BorderStroke(1.dp, GlassBorder)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "Restore Staff Member?", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Restore ${member.displayName} to active status? They will regain access to the store with their configured permissions.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassOutlinedButton(text = "Cancel", onClick = { showConfirmRestoreDialog = false }, modifier = Modifier.weight(1f))
                        VividButton(
                            text = "Restore",
                            onClick = {
                                storeViewModel.restoreMember(member) {
                                    showConfirmRestoreDialog = false
                                    storeViewModel.navigateBackInBusiness()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
