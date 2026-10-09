package com.localbite.app

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.localbite.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private val Canvas = Color(0xFFF8F5F0)
private val Ink = Color(0xFF24231F)
private val Muted = Color(0xFF817D75)
private val CardWhite = Color(0xFFFFFEFC)
private val Hairline = Color(0xFFEFE9E1)

// 把主题色提亮一档，用于渐变收尾，避免渐变显得脏。
private fun lighten(color: Color, amount: Float): Color = Color(
    red = color.red + (1f - color.red) * amount,
    green = color.green + (1f - color.green) * amount,
    blue = color.blue + (1f - color.blue) * amount,
    alpha = color.alpha
)

// 主按钮渐变：主题色 → 提亮的主题色。
private fun accentBrush(accent: Color): Brush = Brush.horizontalGradient(listOf(accent, lighten(accent, 0.22f)))

// 统一的卡片外观：大圆角 + 柔和投影，代替此前的平面白底。
private fun cardElevation(radius: Int, elevation: Int): Modifier = Modifier.shadow(elevation.dp, RoundedCornerShape(radius.dp), ambientColor = Color(0x14000000), spotColor = Color(0x1F000000))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.get(this)
        lifecycleScope.launch {
            // 首次运行插入少量示例菜品，方便安装后立即体验；不会重复插入。
            if (db.productDao().all().isEmpty()) {
                db.productDao().insert(Product(name = "炙烤鸡腿饭", category = "主食", description = "炙烤鸡腿 · 时蔬 · 米饭", priceCents = 2800))
                db.productDao().insert(Product(name = "黑松露奶油意面", category = "主食", description = "奶香浓郁，口感顺滑", priceCents = 3600))
                db.productDao().insert(Product(name = "手作柠檬茶", category = "饮品", description = "鲜柠檬 · 清爽回甘", priceCents = 1200))
                db.productDao().insert(Product(name = "焦糖布丁", category = "甜品", description = "每日现做，细腻柔滑", priceCents = 1600))
            }
            if (db.settingsDao().get() == null) db.settingsDao().save(ShopSettings())
        }
        setContent { LocalBiteApp(db) }
    }
}

@Composable
private fun LocalBiteApp(db: AppDatabase) {
    val context = LocalContext.current
    val products by db.productDao().observeAll().collectAsStateWithLifecycle(emptyList())
    val orders by db.orderDao().observeAll().collectAsStateWithLifecycle(emptyList())
    val settings by db.settingsDao().observe().collectAsStateWithLifecycle(ShopSettings())
    val shop = settings ?: ShopSettings()
    val accent = Color(shop.accentColor.toInt())
    var tab by remember { mutableStateOf(0) }
    var cart by remember { mutableStateOf(mapOf<Long, Int>()) }
    var category by remember { mutableStateOf("全部") }
    var search by remember { mutableStateOf("") }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Product?>(null) }
    var notice by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            try {
                notice = withContext(Dispatchers.IO) { exportBackup(context, db, shop, uri) }
            } catch (e: Exception) { notice = "备份失败：${e.localizedMessage ?: "未知错误"}" }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("无法读取文件")
                notice = withContext(Dispatchers.IO) { restoreBackup(context, db, bytes) }
            } catch (e: Exception) { notice = "恢复失败：请确认选择的是本软件备份文件" }
        }
    }

    MaterialTheme(colorScheme = lightColorScheme(
        primary = accent, onPrimary = Color.White,
        secondaryContainer = accent.copy(alpha = .15f), onSecondaryContainer = accent,
        background = Canvas, surface = CardWhite, onBackground = Ink, onSurface = Ink,
        outline = Hairline
    )) {
        Scaffold(containerColor = Canvas, bottomBar = {
            NavigationBar(containerColor = CardWhite, tonalElevation = 0.dp, modifier = Modifier.shadow(14.dp, RoundedCornerShape(0.dp), ambientColor = Color(0x10000000), spotColor = Color(0x1A000000))) {
                val navColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = accent, selectedTextColor = Ink,
                    indicatorColor = accent.copy(alpha = .16f),
                    unselectedIconColor = Muted, unselectedTextColor = Muted)
                NavigationBarItem(colors = navColors, selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("点餐") })
                NavigationBarItem(colors = navColors, selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.ShoppingBag, null) }, label = { Text("购物车${if (cart.values.sum() > 0) "·${cart.values.sum()}" else ""}") })
                NavigationBarItem(colors = navColors, selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.ListAlt, null) }, label = { Text("订单") })
                NavigationBarItem(colors = navColors, selected = tab == 3, onClick = { tab = 3 }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("管理") })
            }
        }, snackbarHost = { SnackbarHost(hostState = remember { SnackbarHostState() }) }) { padding ->
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colorStops = arrayOf(0f to accent.copy(alpha = .10f), 0.30f to Canvas, 1f to Canvas))).padding(padding)) {
                when (tab) {
                    0 -> MenuScreen(shop, products, cart, category, { category = it }, search, { search = it }, accent,
                        add = { id -> cart = cart + (id to ((cart[id] ?: 0) + 1)) }, remove = { id ->
                            val count = (cart[id] ?: 0) - 1; cart = if (count <= 0) cart - id else cart + (id to count)
                        })
                    1 -> CartScreen(products, cart, accent, onAdd = { id -> cart = cart + (id to ((cart[id] ?: 0) + 1)) }, onRemove = { id ->
                        val count = (cart[id] ?: 0) - 1; cart = if (count <= 0) cart - id else cart + (id to count)
                    }, onCheckout = {
                        val chosen = products.filter { (cart[it.id] ?: 0) > 0 }
                        if (chosen.isNotEmpty()) scope.launch {
                            val summary = chosen.joinToString("、") { "${it.name}×${cart[it.id] ?: 0}" }
                            val total = chosen.sumOf { it.priceCents * (cart[it.id] ?: 0) }
                            db.orderDao().insert(OrderEntity(totalCents = total, itemSummary = summary))
                            cart = emptyMap(); tab = 2; notice = "下单成功，订单已保存在本机"
                        }
                    })
                    2 -> OrdersScreen(orders, accent,
                        onAdvance = { o -> scope.launch { db.orderDao().update(o.copy(status = nextStatus(o.status))) } },
                        onCancel = { o -> scope.launch { db.orderDao().update(o.copy(status = "已取消")) } },
                        onDelete = { o -> scope.launch { db.orderDao().delete(o.id) } })
                    else -> AdminScreen(shop, products, accent,
                        onAdd = { editing = null; showEditor = true },
                        onEdit = { editing = it; showEditor = true },
                        onDelete = { product -> scope.launch { db.productDao().delete(product.id); notice = "已删除 ${product.name}" } },
                        onShopSave = { updated -> scope.launch { db.settingsDao().save(updated); notice = "店铺设置已保存" } },
                        onExport = { exportLauncher.launch("拾味点餐备份-${System.currentTimeMillis()}.zip") },
                        onImport = { importLauncher.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "text/*")) })
                }
                if (showEditor) ProductEditor(editing, accent, onDismiss = { showEditor = false }, onSave = { product, imageUri ->
                    scope.launch {
                        val imagePath = imageUri?.let { copyImageToPrivateStorage(context, it) } ?: product.imagePath
                        val finalProduct = product.copy(imagePath = imagePath)
                        if (finalProduct.id == 0L) db.productDao().insert(finalProduct) else db.productDao().update(finalProduct)
                        showEditor = false; notice = "菜品已保存"
                    }
                })
                if (notice.isNotBlank()) AlertDialog(onDismissRequest = { notice = "" }, confirmButton = { TextButton(onClick = { notice = "" }) { Text("知道了") } }, text = { Text(notice) }, containerColor = CardWhite)
            }
        }
    }
}

@Composable
private fun MenuScreen(shop: ShopSettings, products: List<Product>, cart: Map<Long, Int>, category: String, onCategory: (String) -> Unit, search: String, onSearch: (String) -> Unit, accent: Color, add: (Long) -> Unit, remove: (Long) -> Unit) {
    val categories = listOf("全部") + products.map { it.category }.distinct()
    val shown = products.filter { it.available && (category == "全部" || it.category == category) && (it.name.contains(search, true) || it.description.contains(search, true)) }
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("GOOD FOOD, GOOD MOOD", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text(shop.shopName, fontSize = 31.sp, fontWeight = FontWeight.ExtraBold, color = Ink, letterSpacing = (-0.5).sp)
                    Spacer(Modifier.height(2.dp))
                    Text(shop.tagline, color = Muted, fontSize = 13.sp)
                }
                Box(Modifier.size(54.dp).shadow(7.dp, CircleShape, ambientColor = Color(0x1F000000), spotColor = Color(0x29000000)).clip(CircleShape).background(accentBrush(accent)), contentAlignment = Alignment.Center) { Icon(Icons.Default.RestaurantMenu, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
            }
        }
        item { SectionTitle("今天，想吃点什么？", accent) }
        item {
            OutlinedTextField(
                value = search, onValueChange = onSearch,
                modifier = Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x12000000), spotColor = Color(0x18000000)),
                placeholder = { Text("搜索菜品或口味", color = Muted) }, singleLine = true, shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Default.Search, null, tint = accent) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent, unfocusedBorderColor = Hairline,
                    focusedContainerColor = CardWhite, unfocusedContainerColor = CardWhite,
                    focusedLeadingIconColor = accent, unfocusedLeadingIconColor = accent.copy(alpha = .75f),
                    unfocusedPlaceholderColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                categories.forEach { cat ->
                    val selected = category == cat
                    FilterChip(
                        selected = selected, onClick = { onCategory(cat) },
                        label = { Text(cat, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = CardWhite, labelColor = Ink,
                            selectedContainerColor = accent, selectedLabelColor = Color.White))
                }
            }
        }
        item { SectionTitle("人气菜单", accent, trailing = "${shown.size} 款") }
        if (shown.isEmpty()) item { EmptyState("还没有找到菜品", "可以在「管理」里添加你的菜单", accent) }
        items(shown, key = { it.id }) { product -> ProductCard(product, cart[product.id] ?: 0, accent, add, remove) }
    }
}

// 分区标题：主题色短竖条 + 标题 + 右侧补充信息。
@Composable
private fun SectionTitle(title: String, accent: Color, trailing: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(19.dp).clip(RoundedCornerShape(4.dp)).background(accent))
        Spacer(Modifier.width(11.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink, letterSpacing = (-0.3).sp)
        Spacer(Modifier.weight(1f))
        if (trailing.isNotEmpty()) Text(trailing, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ProductCard(product: Product, count: Int, accent: Color, add: (Long) -> Unit, remove: (Long) -> Unit) {
    Row(cardElevation(24, 5).fillMaxWidth().background(CardWhite).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        ProductImage(product.imagePath, Modifier.size(108.dp).clip(RoundedCornerShape(18.dp)), accent, label = product.name)
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f)) {
            Text(product.category.uppercase(Locale.getDefault()), color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            Text(product.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = (-0.3).sp)
            Text(product.description.ifBlank { "用心制作 · 新鲜出品" }, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(priceText(product.priceCents, 21, 13), color = accent)
                Spacer(Modifier.weight(1f))
                if (count > 0) {
                    SmallRoundButton("−", accent) { remove(product.id) }
                    Text("$count", modifier = Modifier.padding(horizontal = 10.dp), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                    SmallRoundButton("+", accent) { add(product.id) }
                } else FilledIconButton(onClick = { add(product.id) }, modifier = Modifier.size(36.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent)) { Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp), tint = Color.White) }
            }
        }
    }
}

@Composable
private fun CartScreen(products: List<Product>, cart: Map<Long, Int>, accent: Color, onAdd: (Long) -> Unit, onRemove: (Long) -> Unit, onCheckout: () -> Unit) {
    val chosen = products.filter { (cart[it.id] ?: 0) > 0 }
    val total = chosen.sumOf { it.priceCents * (cart[it.id] ?: 0) }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("购物车", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
        Text("确认一下今天的美味", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        if (chosen.isEmpty()) { EmptyState("购物车还是空的", "去点餐页挑选喜欢的菜品吧", accent); return@Column }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(chosen, key = { it.id }) { p ->
                Row(cardElevation(20, 3).fillMaxWidth().background(CardWhite).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProductImage(p.imagePath, Modifier.size(66.dp).clip(RoundedCornerShape(14.dp)), accent, label = p.name)
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(3.dp))
                        Text(priceText(p.priceCents, 17, 12), color = accent)
                    }
                    SmallRoundButton("−", accent) { onRemove(p.id) }
                    Text("${cart[p.id]}", modifier = Modifier.padding(horizontal = 10.dp), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                    SmallRoundButton("+", accent) { onAdd(p.id) }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(cardElevation(22, 12).fillMaxWidth().background(CardWhite).padding(horizontal = 18.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("合计", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(2.dp))
                Text(priceText(total, 27, 15), color = accent)
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onCheckout,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 15.dp),
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(accentBrush(accent))) {
                Text("确认下单", fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun OrdersScreen(orders: List<OrderEntity>, accent: Color, onAdvance: (OrderEntity) -> Unit, onCancel: (OrderEntity) -> Unit, onDelete: (OrderEntity) -> Unit) {
    var selectedId by remember { mutableStateOf<Long?>(null) }
    val selected = orders.find { it.id == selectedId }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(22.dp))
        Text("订单记录", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
        Text("你的每一份选择，都有迹可循", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        if (orders.isEmpty()) { EmptyState("还没有订单", "完成一次点餐后，订单会显示在这里", accent); return@Column }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(13.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(orders, key = { it.id }) { order ->
                Column(cardElevation(22, 4).fillMaxWidth().background(CardWhite).clickable { selectedId = order.id }.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("订单 #${order.id.toString().padStart(4, '0')}", fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = (-0.3).sp)
                        Spacer(Modifier.weight(1f))
                        StatusPill(order.status)
                    }
                    Spacer(Modifier.height(9.dp))
                    Text(order.itemSummary, color = Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                    Spacer(Modifier.height(13.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(order.createdAt)), color = Muted, fontSize = 11.sp)
                        Spacer(Modifier.weight(1f))
                        Text(priceText(order.totalCents, 21, 13), color = accent)
                    }
                }
            }
        }
    }
    if (selected != null) OrderDetailDialog(selected,
        onAdvance = onAdvance, onCancel = onCancel,
        onDelete = { onDelete(it); selectedId = null },
        onDismiss = { selectedId = null })
}

// 状态胶囊：淡色底 + 同色加粗文字，代替此前的纯色文字。
@Composable
private fun StatusPill(status: String) {
    val c = statusColor(status)
    Box(Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = .13f)).padding(horizontal = 11.dp, vertical = 4.dp)) {
        Text(status, color = c, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

// 订单详情：查看菜品明细，并提供状态推进、取消与删除入口。
@Composable
private fun OrderDetailDialog(order: OrderEntity, onAdvance: (OrderEntity) -> Unit, onCancel: (OrderEntity) -> Unit, onDelete: (OrderEntity) -> Unit, onDismiss: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    val canOperate = order.status == "已下单" || order.status == "制作中"
    AlertDialog(onDismissRequest = onDismiss, containerColor = CardWhite,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("订单 #${order.id.toString().padStart(4, '0')}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.weight(1f))
                StatusPill(order.status)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(order.createdAt)), color = Muted, fontSize = 12.sp)
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEFE9E2)))
                Text("菜品明细", color = Muted, fontSize = 12.sp)
                order.itemSummary.split("、").filter { it.isNotBlank() }.forEach { Text(it, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEFE9E2)))
                Row(verticalAlignment = Alignment.CenterVertically) { Text("合计", color = Muted); Spacer(Modifier.weight(1f)); Text("¥${money(order.totalCents)}", color = statusColor(order.status), fontWeight = FontWeight.ExtraBold, fontSize = 21.sp) }
            }
        },
        confirmButton = {
            if (canOperate) TextButton(onClick = { onAdvance(order) }) { Text(if (order.status == "已下单") "开始制作" else "完成订单") }
            else TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (canOperate) TextButton(onClick = { onCancel(order) }, colors = ButtonDefaults.textButtonColors(contentColor = Muted)) { Text("取消订单") }
                TextButton(onClick = { confirmDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFB65B52))) { Text("删除") }
            }
        })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, containerColor = CardWhite,
        title = { Text("删除这笔订单？", fontWeight = FontWeight.Bold) },
        text = { Text("订单 #${order.id.toString().padStart(4, '0')} 删除后无法恢复。") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(order) }, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFB65B52))) { Text("删除") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("再想想") } })
}

private fun nextStatus(status: String): String = when (status) {
    "已下单" -> "制作中"
    "制作中" -> "已完成"
    else -> status
}

private fun statusColor(status: String): Color = when (status) {
    "已下单" -> Color(0xFF6378A8)
    "制作中" -> Color(0xFFC56A45)
    "已完成" -> Color(0xFF39866A)
    "已取消" -> Color(0xFF9A9690)
    else -> Muted
}

@Composable
private fun AdminScreen(shop: ShopSettings, products: List<Product>, accent: Color, onAdd: () -> Unit, onEdit: (Product) -> Unit, onDelete: (Product) -> Unit, onShopSave: (ShopSettings) -> Unit, onExport: () -> Unit, onImport: () -> Unit) {
    var name by remember(shop.shopName) { mutableStateOf(shop.shopName) }
    var tagline by remember(shop.tagline) { mutableStateOf(shop.tagline) }
    var color by remember(shop.accentColor) { mutableLongStateOf(shop.accentColor) }
    val colors = listOf(0xFFC56A45, 0xFF4F7665, 0xFF6378A8, 0xFF9A6D92, 0xFF3E3D39)
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("店铺管理", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
            Text("把这里变成你自己的小店", color = Muted, fontSize = 13.sp)
        }
        item {
            Column(cardElevation(24, 5).fillMaxWidth().background(CardWhite).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("店铺形象", accent)
                OutlinedTextField(name, { name = it }, label = { Text("店铺名称") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, unfocusedBorderColor = Hairline, focusedLabelColor = accent))
                OutlinedTextField(tagline, { tagline = it }, label = { Text("首页欢迎语") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, unfocusedBorderColor = Hairline, focusedLabelColor = accent))
                Text("主题色", color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    colors.forEach { c ->
                        val selected = color == c
                        Box(
                            Modifier.size(42.dp).clip(CircleShape).background(CardWhite)
                                .border(2.dp, if (selected) Color(c.toInt()) else Hairline, CircleShape)
                                .clickable { color = c }.padding(4.dp),
                            contentAlignment = Alignment.Center) {
                            Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(c.toInt())))
                        }
                    }
                }
                Button(
                    onClick = { onShopSave(shop.copy(shopName = name.ifBlank { "拾味小馆" }, tagline = tagline, accentColor = color)) },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(accentBrush(accent)),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)) { Text("保存店铺设置", fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).height(19.dp).clip(RoundedCornerShape(4.dp)).background(accent))
                Spacer(Modifier.width(11.dp))
                Text("菜单管理", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Button(onClick = onAdd, shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)) { Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("添加菜品", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            }
        }
        items(products, key = { it.id }) { p ->
            Row(cardElevation(18, 3).fillMaxWidth().background(CardWhite).padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                ProductImage(p.imagePath, Modifier.size(62.dp).clip(RoundedCornerShape(14.dp)), accent, label = p.name)
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(buildAnnotatedString {
                        append("${p.category} · ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("¥${money(p.priceCents)}") }
                    }, color = Muted, fontSize = 12.sp)
                }
                IconButton(onClick = { onEdit(p) }) { Icon(Icons.Default.Edit, "编辑", tint = accent) }
                IconButton(onClick = { onDelete(p) }) { Icon(Icons.Default.Delete, "删除", tint = Color(0xFFB65B52)) }
            }
        }
        item {
            Column(cardElevation(24, 5).fillMaxWidth().background(CardWhite).padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                SectionTitle("本地数据", accent)
                Text("所有内容只保存在这台手机。建议定期导出备份，备份会连同菜品图片一起打包为 ZIP。", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), border = ButtonDefaults.outlinedButtonBorder(enabled = true)) { Text("导出数据备份（ZIP）", fontWeight = FontWeight.Medium) }
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), border = ButtonDefaults.outlinedButtonBorder(enabled = true)) { Text("从备份恢复数据", fontWeight = FontWeight.Medium) }
            }
        }
    }
}

@Composable
private fun ProductEditor(product: Product?, accent: Color, onDismiss: () -> Unit, onSave: (Product, Uri?) -> Unit) {
    val context = LocalContext.current
    var name by remember(product) { mutableStateOf(product?.name ?: "") }
    var category by remember(product) { mutableStateOf(product?.category ?: "主食") }
    var description by remember(product) { mutableStateOf(product?.description ?: "") }
    var price by remember(product) { mutableStateOf(product?.priceCents?.let { "%.2f".format(Locale.US, it / 100.0) } ?: "") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imagePath by remember(product) { mutableStateOf(product?.imagePath ?: "") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { imageUri = it }
    AlertDialog(onDismissRequest = onDismiss, containerColor = CardWhite, title = { Text(if (product == null) "添加菜品" else "编辑菜品", fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProductImage(imageUri?.let { uri -> uri.toString() } ?: imagePath, Modifier.size(76.dp).clip(RoundedCornerShape(15.dp)), accent, label = name)
                Spacer(Modifier.width(13.dp))
                OutlinedButton(onClick = { picker.launch("image/*") }, shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Image, null, tint = accent); Spacer(Modifier.width(6.dp)); Text("选择图片") }
            }
            OutlinedTextField(name, { name = it }, label = { Text("菜品名称") }, singleLine = true, shape = RoundedCornerShape(13.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, unfocusedBorderColor = Hairline, focusedLabelColor = accent))
            OutlinedTextField(category, { category = it }, label = { Text("分类，如主食、饮品") }, singleLine = true, shape = RoundedCornerShape(13.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, unfocusedBorderColor = Hairline, focusedLabelColor = accent))
            OutlinedTextField(description, { description = it }, label = { Text("菜品描述") }, minLines = 2, shape = RoundedCornerShape(13.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, unfocusedBorderColor = Hairline, focusedLabelColor = accent))
            OutlinedTextField(price, { price = it.filter { ch -> ch.isDigit() || ch == '.' }.take(8) }, label = { Text("价格（元）") }, singleLine = true, shape = RoundedCornerShape(13.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, unfocusedBorderColor = Hairline, focusedLabelColor = accent))
        }
    }, confirmButton = { TextButton(onClick = {
        val cents = ((price.toDoubleOrNull() ?: 0.0) * 100).toInt()
        if (name.isNotBlank() && cents >= 0) onSave(Product(id = product?.id ?: 0, name = name.trim(), category = category.ifBlank { "其他" }, description = description.trim(), priceCents = cents, imagePath = product?.imagePath ?: ""), imageUri)
    }) { Text("保存", fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = Muted)) { Text("取消") } })
}

@Composable
private fun ProductImage(path: String, modifier: Modifier, accent: Color, label: String = "") {
    val context = LocalContext.current
    val bitmap = remember(path) {
        try {
            if (path.isBlank()) null
            else if (path.startsWith("content://")) context.contentResolver.openInputStream(Uri.parse(path))?.use { BitmapFactory.decodeStream(it) }
            else BitmapFactory.decodeFile(path)
        } catch (_: Exception) { null }
    }
    if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = "菜品图片", modifier = modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(Brush.linearGradient(listOf(accent.copy(alpha = .16f), accent.copy(alpha = .05f)))), contentAlignment = Alignment.Center) {
        val initial = label.trim().take(1)
        if (initial.isNotEmpty()) Text(initial, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = accent.copy(alpha = .9f))
        else Icon(Icons.Default.RestaurantMenu, null, tint = accent.copy(alpha = .75f), modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun SmallRoundButton(label: String, accent: Color, onClick: () -> Unit) {
    Box(Modifier.size(31.dp).clip(CircleShape).background(accent.copy(alpha = .13f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = accent, fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String, accent: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(80.dp).shadow(6.dp, CircleShape, ambientColor = Color(0x14000000), spotColor = Color(0x1A000000)).clip(CircleShape).background(Brush.linearGradient(listOf(accent.copy(alpha = .16f), accent.copy(alpha = .05f)))), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.RestaurantMenu, null, tint = accent, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(17.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = (-0.3).sp)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = Muted, fontSize = 12.5.sp, textAlign = TextAlign.Center)
    }
}

// 价格排版：小号 ¥ + 大号金额，同一 Text 内天然共用基线。
private fun priceText(cents: Int, size: Int, symbolSize: Int) = buildAnnotatedString {
    withStyle(SpanStyle(fontSize = symbolSize.sp, fontWeight = FontWeight.Bold)) { append("¥") }
    withStyle(SpanStyle(fontSize = size.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)) { append(money(cents)) }
}

private fun money(cents: Int): String = "%.2f".format(Locale.CHINA, cents / 100.0)

// 把用户选择的图片复制到 App 私有目录；不申请存储权限，也不依赖原 URI 永久有效。
private suspend fun copyImageToPrivateStorage(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val folder = File(context.filesDir, "menu_images").apply { mkdirs() }
    val file = File(folder, "dish_${System.currentTimeMillis()}.img")
    context.contentResolver.openInputStream(uri).use { input -> file.outputStream().use { output -> input?.copyTo(output) ?: error("图片读取失败") } }
    file.absolutePath
}

// 导出为 ZIP：backup.json + images/ 下的菜品图片。json 里 imagePath 记录 zip 内的图片路径，恢复时按此重新映射。
private suspend fun exportBackup(context: Context, db: AppDatabase, settings: ShopSettings, uri: Uri): String = withContext(Dispatchers.IO) {
    val root = JSONObject().put("app", "LocalBite").put("version", 2)
    root.put("settings", JSONObject().put("shopName", settings.shopName).put("tagline", settings.tagline).put("accentColor", settings.accentColor))
    val images = mutableListOf<Pair<String, File>>()
    val ps = JSONArray(); db.productDao().all().forEach { p ->
        val src = if (p.imagePath.isBlank()) null else File(p.imagePath)
        val entry = if (src != null && src.isFile) "images/${p.id}_${src.name}" else ""
        if (entry.isNotEmpty()) images.add(entry to src!!)
        ps.put(JSONObject().put("id", p.id).put("name", p.name).put("category", p.category).put("description", p.description).put("priceCents", p.priceCents).put("available", p.available).put("imagePath", entry))
    }; root.put("products", ps)
    val os = JSONArray(); db.orderDao().all().forEach { o -> os.put(JSONObject().put("id", o.id).put("createdAt", o.createdAt).put("totalCents", o.totalCents).put("itemSummary", o.itemSummary).put("status", o.status)) }; root.put("orders", os)
    val output = context.contentResolver.openOutputStream(uri) ?: error("无法写入文件")
    ZipOutputStream(output.buffered()).use { zip ->
        zip.putNextEntry(ZipEntry("backup.json")); zip.write(root.toString(2).toByteArray(Charsets.UTF_8)); zip.closeEntry()
        images.forEach { (entry, file) -> zip.putNextEntry(ZipEntry(entry)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
    }
    if (images.isEmpty()) "备份已导出（该备份不含菜品图片）" else "备份已导出，含 ${images.size} 张菜品图片"
}

// 恢复前清空旧菜品和订单，再导入备份；ZIP 备份会把图片解压到私有目录并重建 imagePath，旧版纯 JSON 备份同样兼容。
private suspend fun restoreBackup(context: Context, db: AppDatabase, raw: ByteArray): String = withContext(Dispatchers.IO) {
    var json = ""
    val imagePaths = mutableMapOf<String, String>()
    val isZip = raw.size >= 2 && raw[0] == 'P'.code.toByte() && raw[1] == 'K'.code.toByte()
    if (isZip) {
        var seq = 0
        ZipInputStream(raw.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "backup.json") json = zip.readBytes().toString(Charsets.UTF_8)
                else if (!entry.isDirectory && entry.name.startsWith("images/")) {
                    val folder = File(context.filesDir, "menu_images").apply { mkdirs() }
                    val file = File(folder, "restore_${System.currentTimeMillis()}_${seq++}_${File(entry.name).name}")
                    file.outputStream().use { zip.copyTo(it) }
                    imagePaths[entry.name] = file.absolutePath
                }
                zip.closeEntry(); entry = zip.nextEntry
            }
        }
        require(json.isNotEmpty()) { "备份中缺少 backup.json" }
    } else {
        json = String(raw, Charsets.UTF_8)
    }
    val root = JSONObject(json)
    require(root.optString("app") == "LocalBite") { "不是 LocalBite 备份" }
    val s = root.optJSONObject("settings") ?: JSONObject()
    db.settingsDao().save(ShopSettings(shopName = s.optString("shopName", "拾味小馆"), tagline = s.optString("tagline", "认真做好每一口"), accentColor = s.optLong("accentColor", 0xFFC56A45)))
    db.productDao().clear(); db.orderDao().clear()
    val ps = root.optJSONArray("products") ?: JSONArray()
    for (i in 0 until ps.length()) { val p = ps.getJSONObject(i); db.productDao().insert(Product(name = p.optString("name", "未命名菜品"), category = p.optString("category", "其他"), description = p.optString("description", ""), priceCents = p.optInt("priceCents", 0), available = p.optBoolean("available", true), imagePath = imagePaths[p.optString("imagePath")] ?: "")) }
    val os = root.optJSONArray("orders") ?: JSONArray()
    for (i in 0 until os.length()) { val o = os.getJSONObject(i); db.orderDao().insert(OrderEntity(createdAt = o.optLong("createdAt", System.currentTimeMillis()), totalCents = o.optInt("totalCents", 0), itemSummary = o.optString("itemSummary", ""), status = o.optString("status", "已下单"))) }
    when {
        imagePaths.isEmpty() && isZip -> "数据已恢复；此备份不含菜品图片"
        imagePaths.isEmpty() -> "数据已恢复；旧版备份不含图片，请重新选择"
        else -> "数据已恢复，含 ${imagePaths.size} 张菜品图片"
    }
}
