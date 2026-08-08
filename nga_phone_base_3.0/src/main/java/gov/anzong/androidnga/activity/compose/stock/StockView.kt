package gov.anzong.androidnga.activity.compose.stock

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.Checkbox
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.sp
import gov.anzong.androidnga.activity.compose.stock.data.DividendInfo
import gov.anzong.androidnga.activity.compose.stock.data.StockInfo
import gov.anzong.androidnga.activity.compose.stock.data.StockTarget

/** A 股习惯：涨红跌绿 */
private val ColorUp = Color(0xFFE53935)
private val ColorDown = Color(0xFF43A047)
private val ColorFlat = Color(0xFF9E9E9E)

/** 股息率用独立颜色，避免和涨跌的红绿混淆 */
private val ColorDividend = Color(0xFFEF6C00)

/** 备注文字，比正文淡一档 */
private val ColorNote = Color(0xFF757575)

/** 已触及档位的标签底色。比涨跌的正红淡，避免一排标签太扎眼 */
private val ColorReachedBg = Color(0xFFFCE4E6)

/** 已触及档位的边框与文字，用深红保证在淡底上看得清 */
private val ColorReachedFg = Color(0xFFC62828)

/** 档位标签的正常字号 */
private val TagFontSize = 9.sp

/** 档位标签自动缩字号的下限，再小就看不清了，宁可让尾部裁掉 */
private val TagFontSizeMin = 6.sp

private fun colorOf(changePercent: Float): Color = when {
    changePercent > 0 -> ColorUp
    changePercent < 0 -> ColorDown
    else -> ColorFlat
}

@Composable
fun StockView(viewModel: StockViewModel) {
    val stocks by viewModel.stockLiveData.observeAsState(emptyList())
    val targets by viewModel.targetLiveData.observeAsState(emptyMap())
    val dividends by viewModel.dividendLiveData.observeAsState(emptyMap())

    // 只在本页真正可见且 app 在前台时轮询：切到别的 Tab 或退到后台都会停
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onResume()
                Lifecycle.Event.ON_PAUSE -> viewModel.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // 进入本页时先刷一次并起轮询（此时 ON_RESUME 可能已经过去了）
        viewModel.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onPause()
        }
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<StockInfo?>(null) }
    var pendingTarget by remember { mutableStateOf<StockInfo?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (stocks.isEmpty()) {
            Text(
                text = "还没有自选股\n点击右下角按钮添加\n添加后点击股票可设置建仓价",
                textAlign = TextAlign.Center,
                color = ColorFlat,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                StockListHeader()
                HorizontalDivider()
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(stocks, key = { it.code }) { stock ->
                        StockItem(
                            stock = stock,
                            target = targets[stock.code] ?: StockTarget(),
                            dividend = dividends[stock.code],
                            onClick = { pendingTarget = stock },
                            onLongClick = { pendingRemove = stock }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Text(text = "+", fontSize = 24.sp)
        }
    }

    if (showAddDialog) {
        AddStockDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = {
                viewModel.addStock(it)
                showAddDialog = false
            }
        )
    }

    pendingRemove?.let { stock ->
        val index = stocks.indexOfFirst { it.code == stock.code }
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text(stock.name) },
            text = {
                Column {
                    Text("调整位置或删除", fontSize = 13.sp, color = ColorFlat)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.moveStock(stock, true)
                                pendingRemove = null
                            },
                            enabled = index > 0
                        ) { Text("↑ 上移") }
                        TextButton(
                            onClick = {
                                viewModel.moveStock(stock, false)
                                pendingRemove = null
                            },
                            enabled = index >= 0 && index < stocks.size - 1
                        ) { Text("↓ 下移") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeStock(stock)
                    pendingRemove = null
                }) { Text("删除", color = ColorUp) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text("取消") }
            }
        )
    }

    pendingTarget?.let { stock ->
        TargetPriceDialog(
            stock = stock,
            target = targets[stock.code] ?: StockTarget(),
            dividend = dividends[stock.code],
            onDismiss = { pendingTarget = null },
            onConfirm = {
                viewModel.saveTarget(stock.code, it)
                pendingTarget = null
            }
        )
    }
}

/**
 * 设置三档建仓价。留空表示不设置该档。
 */
@Composable
private fun TargetPriceDialog(
    stock: StockInfo,
    target: StockTarget,
    dividend: DividendInfo?,
    onDismiss: () -> Unit,
    onConfirm: (StockTarget) -> Unit
) {
    fun display(price: Float) = if (price > 0f) String.format("%.2f", price) else ""

    var build by remember { mutableStateOf(display(target.buildPrice)) }
    var add by remember { mutableStateOf(display(target.addPrice)) }
    var full by remember { mutableStateOf(display(target.fullPrice)) }
    var note by remember { mutableStateOf(target.note) }
    var showNote by remember { mutableStateOf(target.showNote) }
    var autoCalc by remember { mutableStateOf(target.autoCalc) }
    var targetYield by remember {
        mutableStateOf(String.format("%.2f", target.targetYield).trimEnd('0').trimEnd('.'))
    }

    val perShare = dividend?.perShareDividend ?: 0f
    val hasDividend = perShare > 0f
    // 打开自动计算时，三档价格随目标股息率实时算出来
    val calculated = if (autoCalc) {
        StockTarget.calculate(perShare, targetYield.toFloatOrNull() ?: 0f)
    } else {
        null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${stock.name} 建仓价") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "当前价 ${String.format("%.2f", stock.price)}，股价跌到目标价时会高亮提示",
                    fontSize = 12.sp,
                    color = ColorFlat,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                AutoCalcSection(
                    hasDividend = hasDividend,
                    perShare = perShare,
                    autoCalc = autoCalc,
                    targetYield = targetYield,
                    calculated = calculated,
                    onToggle = { autoCalc = it },
                    onYieldChange = { targetYield = it }
                )

                if (!autoCalc) {
                    PriceField(StockTarget.Level.BUILD, build) { build = it }
                    PriceField(StockTarget.Level.ADD, add) { add = it }
                    PriceField(StockTarget.Level.FULL, full) { full = it }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注") },
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showNote = !showNote }
                        .padding(top = 4.dp)
                ) {
                    Checkbox(checked = showNote, onCheckedChange = { showNote = it })
                    Text(text = "在列表中显示备注", fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // 自动计算时存算出来的价格，这样列表和提醒逻辑不用关心是手填还是算的
                onConfirm(
                    StockTarget(
                        buildPrice = calculated?.first ?: build.toFloatOrNull() ?: 0f,
                        addPrice = calculated?.second ?: add.toFloatOrNull() ?: 0f,
                        fullPrice = calculated?.third ?: full.toFloatOrNull() ?: 0f,
                        note = note.trim(),
                        showNote = showNote,
                        autoCalc = autoCalc,
                        targetYield = targetYield.toFloatOrNull()
                            ?: StockTarget.DEFAULT_TARGET_YIELD
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 按目标股息率自动推算三档价格的开关与预览。
 *
 * 建仓价 = 每股股息 / 目标股息率，加仓、满仓依次再降 10%。
 * 没有分红数据的股票（不分红或还没拉到）不能用，开关置灰。
 */
@Composable
private fun AutoCalcSection(
    hasDividend: Boolean,
    perShare: Float,
    autoCalc: Boolean,
    targetYield: String,
    calculated: Triple<Float, Float, Float>?,
    onToggle: (Boolean) -> Unit,
    onYieldChange: (String) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = hasDividend) { onToggle(!autoCalc) }
    ) {
        Checkbox(
            checked = autoCalc,
            onCheckedChange = { onToggle(it) },
            enabled = hasDividend
        )
        Text(
            text = "按股息率自动计算",
            fontSize = 14.sp,
            color = if (hasDividend) MaterialTheme.colors.onSurface else ColorFlat
        )
    }

    if (!hasDividend) {
        Text(
            text = "该股无分红数据，无法自动计算",
            fontSize = 11.sp,
            color = ColorFlat,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        return
    }

    if (!autoCalc) {
        return
    }

    OutlinedTextField(
        value = targetYield,
        onValueChange = onYieldChange,
        label = { Text("建仓时的目标股息率 %") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )

    if (calculated == null) {
        Text(
            text = "请输入大于 0 的股息率",
            fontSize = 11.sp,
            color = ColorUp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        return
    }

    Text(
        text = "每股股息 ${String.format("%.3f", perShare)} 元，加仓/满仓依次再降 10%",
        fontSize = 11.sp,
        color = ColorFlat
    )
    Column(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
        CalcRow(StockTarget.Level.BUILD, calculated.first, perShare)
        CalcRow(StockTarget.Level.ADD, calculated.second, perShare)
        CalcRow(StockTarget.Level.FULL, calculated.third, perShare)
    }
}

/** 预览一档算出来的价格，顺带标出在该价位的股息率 */
@Composable
private fun CalcRow(level: StockTarget.Level, price: Float, perShare: Float) {
    val yieldAt = if (price > 0f) perShare / price * 100 else 0f
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "${level.percent}%${level.label}",
            fontSize = 13.sp,
            color = ColorFlat,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = String.format("%.2f", price),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colors.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = String.format("股息%.2f%%", yieldAt),
            fontSize = 12.sp,
            color = ColorDividend,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.2f)
        )
    }
}

@Composable
private fun PriceField(level: StockTarget.Level, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("${level.percent}% ${level.label}") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun StockListHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.background)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // 权重要和 StockItem 里的三列保持一致，否则表头和数据对不齐
        Text("名称", color = ColorFlat, fontSize = 13.sp, modifier = Modifier.weight(1.9f))
        Text(
            "最新价", color = ColorFlat, fontSize = 13.sp,
            textAlign = TextAlign.End, modifier = Modifier.weight(1f)
        )
        Text(
            "涨跌幅", color = ColorFlat, fontSize = 13.sp,
            textAlign = TextAlign.End, modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StockItem(
    stock: StockInfo,
    target: StockTarget,
    dividend: DividendInfo?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val color = colorOf(stock.changePercent)
    val reached = target.reachedLevel(stock.price)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1.9f)
            ) {
                Text(
                    text = stock.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    color = MaterialTheme.colors.onBackground,
                    // 光晕：同色柔光扩散一圈，让名字更立体
                    style = LocalTextStyle.current.copy(
                        shadow = Shadow(
                            color = MaterialTheme.colors.onBackground.copy(alpha = 0.35f),
                            offset = Offset.Zero,
                            blurRadius = 12f
                        )
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stock.simpleCode,
                        fontSize = 12.sp,
                        maxLines = 1,
                        color = ColorFlat
                    )
                    // 股息率为 0 的（不分红或近12个月没分红）不显示，省得每行都挂个 0.00%
                    val yieldValue = dividend?.yieldOf(stock.price) ?: 0f
                    if (yieldValue > 0f) {
                        Text(
                            text = String.format("股息%.2f%%", yieldValue),
                            fontSize = 11.sp,
                            color = ColorDividend,
                            // 不许换行，宽度不够时整体缩窄而不是把百分号挤到第二行
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
            Text(
                text = String.format("%.2f", stock.price),
                fontSize = 16.sp,
                color = color,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = String.format("%+.2f%%", stock.changePercent),
                fontSize = 16.sp,
                color = color,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }

        // 没设价格就不画这排标签；只写了备注的股票不该出现三个空框
        if (!target.hasNoPrice()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                TargetTag(StockTarget.Level.BUILD, target.buildPrice, reached, dividend)
                TargetTag(StockTarget.Level.ADD, target.addPrice, reached, dividend)
                TargetTag(StockTarget.Level.FULL, target.fullPrice, reached, dividend)
            }
        }

        if (target.hasVisibleNote()) {
            Text(
                text = target.note,
                fontSize = 12.sp,
                color = ColorNote,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/**
 * 目标价标签。已触及的档位用醒目底色标出，未设置的档位留空占位保持对齐。
 *
 * 标签前缀是「跌到这个价时的股息率」而不是仓位百分比：仓位那三个数（20/30/50）
 * 是写死的，看一眼就知道，占着位置不提供信息；股息率才是决定要不要在这个价位
 * 下手的依据。没有分红数据的股票退回显示仓位百分比。
 */
@Composable
private fun RowScope.TargetTag(
    level: StockTarget.Level,
    price: Float,
    reached: StockTarget.Level?,
    dividend: DividendInfo?
) {
    if (price <= 0f) {
        Box(modifier = Modifier.weight(1f))
        return
    }
    val isReached = reached == level
    val yieldAt = dividend?.yieldOf(price) ?: 0f
    val prefix = if (yieldAt > 0f) {
        String.format("%.2f%%", yieldAt)
    } else {
        "${level.percent}%"
    }
    val text = "$prefix${level.label} ${String.format("%.2f", price)}"
    // 整串放不下时按实测结果逐级缩字号。默认的换行行为在 maxLines=1 下会把价格断
    // 到第二行再整行裁掉，标签只剩「4.50%建仓」——价格无声消失。系统字体放大到
    // 1.4 倍以上、或价格是三位数时就会踩到，宁可字小一点也要把价格显示全。
    var fontSize by remember(text) { mutableStateOf(TagFontSize) }
    // 缩到合适字号前先别画，否则会看到一帧大字
    var measured by remember(text) { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .background(
                color = if (isReached) ColorReachedBg else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .border(
                width = 1.dp,
                color = if (isReached) ColorReachedFg else ColorFlat,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            // 小字号下行高默认会偏大，压紧一点让文字在边框里居中
            lineHeight = fontSize * 1.22f,
            maxLines = 1,
            // 不许换行：宽度不够时要么缩字号，要么裁尾，绝不把价格甩到看不见的第二行
            softWrap = false,
            fontWeight = if (isReached) FontWeight.Bold else FontWeight.Normal,
            // 底色变淡后文字不能再用白色，改深红
            color = if (isReached) ColorReachedFg else ColorFlat,
            onTextLayout = { result ->
                if (result.hasVisualOverflow && fontSize > TagFontSizeMin) {
                    fontSize *= 0.92f
                } else {
                    measured = true
                }
            },
            modifier = Modifier.drawWithContent {
                if (measured) {
                    drawContent()
                }
            }
        )
    }
}

@Composable
private fun AddStockDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加自选股") },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("股票代码") },
                    singleLine = true
                )
                Text(
                    text = "输入6位代码即可，如 601398",
                    fontSize = 12.sp,
                    color = ColorFlat,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(input) }) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
