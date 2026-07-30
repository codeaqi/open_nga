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
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    onDismiss: () -> Unit,
    onConfirm: (StockTarget) -> Unit
) {
    fun display(price: Float) = if (price > 0f) String.format("%.2f", price) else ""

    var build by remember { mutableStateOf(display(target.buildPrice)) }
    var add by remember { mutableStateOf(display(target.addPrice)) }
    var full by remember { mutableStateOf(display(target.fullPrice)) }
    var note by remember { mutableStateOf(target.note) }
    var showNote by remember { mutableStateOf(target.showNote) }

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
                PriceField(StockTarget.Level.BUILD, build) { build = it }
                PriceField(StockTarget.Level.ADD, add) { add = it }
                PriceField(StockTarget.Level.FULL, full) { full = it }

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
                onConfirm(
                    StockTarget(
                        buildPrice = build.toFloatOrNull() ?: 0f,
                        addPrice = add.toFloatOrNull() ?: 0f,
                        fullPrice = full.toFloatOrNull() ?: 0f,
                        note = note.trim(),
                        showNote = showNote
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
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
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    color = MaterialTheme.colors.onBackground
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
                TargetTag(StockTarget.Level.BUILD, target.buildPrice, reached)
                TargetTag(StockTarget.Level.ADD, target.addPrice, reached)
                TargetTag(StockTarget.Level.FULL, target.fullPrice, reached)
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
 */
@Composable
private fun RowScope.TargetTag(
    level: StockTarget.Level,
    price: Float,
    reached: StockTarget.Level?
) {
    if (price <= 0f) {
        Box(modifier = Modifier.weight(1f))
        return
    }
    val isReached = reached == level
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
            text = "${level.percent}%${level.label} ${String.format("%.2f", price)}",
            fontSize = 9.sp,
            // 小字号下行高默认会偏大，压紧一点让文字在边框里居中
            lineHeight = 11.sp,
            maxLines = 1,
            fontWeight = if (isReached) FontWeight.Bold else FontWeight.Normal,
            // 底色变淡后文字不能再用白色，改深红
            color = if (isReached) ColorReachedFg else ColorFlat
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
