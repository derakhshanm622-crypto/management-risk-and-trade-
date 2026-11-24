package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.model.Candle
import com.example.data.model.LiveQuote
import com.example.data.model.MarketInstrument
import com.example.data.model.PartialTpTier
import com.example.data.model.TradeRecord
import com.example.market.MarketSimulator
import com.example.ui.theme.BearOrangeRed
import com.example.ui.theme.BullGreen
import com.example.ui.theme.BullGreenBright
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CardSurface
import com.example.ui.theme.ChartGridLine
import com.example.ui.theme.MonoPriceStyle
import com.example.ui.theme.RoyalBlueBright
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val STAGED_TRADE_ID = "__staged_order__"
private const val DEFAULT_VISIBLE_CANDLES = 42f
private const val DEFAULT_SCROLL_OFFSET_CANDLES = -5f
private const val MIN_VISIBLE_CANDLES = 8f

data class StagedOrderDraft(
    val side: String,
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double = 0.0,
    val calculatedLots: Double,
    val riskAmount: Double,
    val actualRiskAmount: Double = riskAmount,
    val riskPercent: Double,
    val stopDistancePips: Double = 0.0,
    val quoteToDepositRate: Double = 1.0,
    val stepLots: Double = 0.01,
    val isBelowMinVolume: Boolean = false,
    val depositCurrency: String = "USD",
)

private enum class DragLineKind {
    SL,
    TP,
    STAGED_SL,
    STAGED_TP,
}

private enum class GestureMode {
    NONE,
    DRAG_LEVEL,
    Y_AXIS_ZOOM,
    X_AXIS_ZOOM,
    PAN_OR_TAP,
    PINCH_ZOOM,
}

private data class ActiveDragLevel(
    val tradeId: String,
    val kind: DragLineKind,
    val previewPrice: Double,
)

private data class ChartPriceBounds(
    val viewMin: Double,
    val viewMax: Double,
    val priceRange: Double,
    val centerPrice: Double,
    val halfSpan: Double,
    val chartWidth: Float,
    val chartHeight: Float,
    val yAxisWidth: Float,
    val xAxisHeight: Float,
    val startIdx: Int,
    val endIdx: Int,
) {
    fun priceToY(price: Double): Float {
        val norm = ((price - viewMin) / priceRange).toFloat()
        return chartHeight - (norm * chartHeight)
    }

    fun yToPrice(y: Float): Double {
        val norm = ((chartHeight - y) / chartHeight).toDouble()
        return viewMin + norm * priceRange
    }
}

private fun minScrollOffset(visibleCandleCount: Float): Float = -(visibleCandleCount * 0.78f)

private fun maxScrollOffset(candleCount: Int): Float = (candleCount - 4).coerceAtLeast(0).toFloat()

@Composable
fun InteractiveCandlestickChart(
    candles: List<Candle>,
    quote: LiveQuote,
    instrument: MarketInstrument,
    openTrades: List<TradeRecord>,
    onCloseTrade: (TradeRecord) -> Unit,
    onUpdateTradeLevels: (tradeId: String, stopLoss: Double, takeProfit: Double, notes: String) -> Unit,
    modifier: Modifier = Modifier,
    stagedOrder: StagedOrderDraft? = null,
    onStagedStopLossChange: (Double) -> Unit = {},
    onStagedTakeProfitChange: (Double) -> Unit = {},
    onToggleStagedTakeProfit: () -> Unit = {},
    autoPartialCloseEnabled: Boolean = false,
    partialTpTiers: List<PartialTpTier> = listOf(PartialTpTier(1.0, 50.0)),
    showFibonacciRrOverlay: Boolean = true,
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.US) }

    // Continuous float viewport state (negative scrollOffsetCandles = shifted left leaving empty future right margin like cTrader)
    var visibleCandleCount by remember(instrument.symbol) { mutableFloatStateOf(DEFAULT_VISIBLE_CANDLES) }
    var scrollOffsetCandles by remember(instrument.symbol) { mutableFloatStateOf(DEFAULT_SCROLL_OFFSET_CANDLES) }

    // TradingView-style Auto-Scale vs Free 2D Pan / Y-Axis Scaling
    var autoScalePrice by remember(instrument.symbol) { mutableStateOf(true) }
    var manualCenterPrice by remember(instrument.symbol) { mutableDoubleStateOf(quote.bid) }
    var manualHalfSpan by remember(instrument.symbol) { mutableDoubleStateOf(instrument.tickStep * 50.0) }

    var activeFlingJob by remember { mutableStateOf<Job?>(null) }
    var activeZoomJob by remember { mutableStateOf<Job?>(null) }

    var selectedTradeId by remember(instrument.symbol) { mutableStateOf<String?>(null) }
    var activeDrag by remember { mutableStateOf<ActiveDragLevel?>(null) }
    var lastYAxisTapMs by remember { mutableLongStateOf(0L) }
    var previousCandleCount by remember(instrument.symbol) { mutableIntStateOf(candles.size) }

    // Keep historical view anchored when a new bar closes while user is scrolled back in history
    LaunchedEffect(candles.size) {
        val diff = candles.size - previousCandleCount
        if (diff > 0 && previousCandleCount > 0 && scrollOffsetCandles > 2f) {
            scrollOffsetCandles = (scrollOffsetCandles + diff).coerceIn(
                minScrollOffset(visibleCandleCount),
                maxScrollOffset(candles.size),
            )
        }
        previousCandleCount = candles.size
    }

    // Clear selection if trade was closed
    LaunchedEffect(openTrades) {
        if (selectedTradeId != null && openTrades.none { it.id == selectedTradeId }) {
            selectedTradeId = null
        }
    }

    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0xFFCBD5E1).toArgb()
            textSize = 29f
            typeface = Typeface.MONOSPACE
        }
    }
    val pillTextPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.White.toArgb()
            textSize = 28f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
    }
    val tagTextPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.White.toArgb()
            textSize = 26f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }

    val currentCandles by rememberUpdatedState(candles)
    val currentQuote by rememberUpdatedState(quote)
    val currentOpenTrades by rememberUpdatedState(openTrades)
    val currentInstrument by rememberUpdatedState(instrument)
    val currentOnUpdateLevels by rememberUpdatedState(onUpdateTradeLevels)
    val currentStagedOrder by rememberUpdatedState(stagedOrder)
    val currentOnStagedSlChange by rememberUpdatedState(onStagedStopLossChange)
    val currentOnStagedTpChange by rememberUpdatedState(onStagedTakeProfitChange)

    fun animateZoomTo(targetVisible: Float) {
        activeFlingJob?.cancel()
        activeZoomJob?.cancel()
        val maxCandles = max(candles.size.toFloat(), 120f)
        val clampedTarget = targetVisible.coerceIn(MIN_VISIBLE_CANDLES, maxCandles)
        val startVisible = visibleCandleCount
        val startScroll = scrollOffsetCandles
        // Anchor the right-hand candle/margin center while zooming via +/- buttons
        val anchorRightFrac = 0.25f
        activeZoomJob = coroutineScope.launch {
            val anim = Animatable(0f)
            anim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
            ) {
                val t = value
                val nextVisible = startVisible + (clampedTarget - startVisible) * t
                val nextScroll = (startScroll + anchorRightFrac * (startVisible - nextVisible))
                    .coerceIn(minScrollOffset(nextVisible), maxScrollOffset(currentCandles.size))
                visibleCandleCount = nextVisible
                scrollOffsetCandles = nextScroll
            }
        }
    }

    fun resetViewportSmoothly() {
        activeFlingJob?.cancel()
        activeZoomJob?.cancel()
        autoScalePrice = true
        val startVisible = visibleCandleCount
        val startScroll = scrollOffsetCandles
        activeZoomJob = coroutineScope.launch {
            val anim = Animatable(0f)
            anim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            ) {
                val t = value
                visibleCandleCount = startVisible + (DEFAULT_VISIBLE_CANDLES - startVisible) * t
                scrollOffsetCandles = startScroll + (DEFAULT_SCROLL_OFFSET_CANDLES - startScroll) * t
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }

            val yAxisWidth = with(density) { 74.dp.toPx() }.coerceAtMost(widthPx * 0.28f)
            val xAxisHeight = with(density) { 24.dp.toPx() }
            val chartWidth = (widthPx - yAxisWidth).coerceAtLeast(80f)
            val chartHeight = (heightPx - xAxisHeight).coerceAtLeast(80f)

            val maxCandles = max(candles.size.toFloat(), 120f)
            val clampedVisible = visibleCandleCount.coerceIn(MIN_VISIBLE_CANDLES, maxCandles)
            val clampedScroll = if (candles.isNotEmpty()) {
                scrollOffsetCandles.coerceIn(
                    minScrollOffset(clampedVisible),
                    maxScrollOffset(candles.size),
                )
            } else {
                0f
            }

            // Continuous right-edge candle coordinate (if clampedScroll < 0, future empty slots sit on the right like cTrader)
            val rightEdgeCandleFloatIdx = if (candles.isNotEmpty()) {
                (candles.size - 1).toFloat() - clampedScroll
            } else {
                0f
            }

            val startIdx = if (candles.isNotEmpty()) {
                floor(rightEdgeCandleFloatIdx - clampedVisible).toInt().coerceIn(0, candles.lastIndex)
            } else {
                0
            }
            val endIdx = if (candles.isNotEmpty()) {
                (ceil(rightEdgeCandleFloatIdx).toInt() + 1).coerceIn(startIdx + 1, candles.size)
            } else {
                0
            }
            val slice = if (candles.isNotEmpty() && startIdx < endIdx) {
                candles.subList(startIdx, endIdx)
            } else {
                emptyList()
            }

            val autoBounds = remember(
                slice,
                quote.bid,
                quote.ask,
                openTrades,
                stagedOrder?.side,
                stagedOrder?.entryPrice,
                stagedOrder?.stopLoss,
                stagedOrder?.takeProfit,
                widthPx,
                heightPx,
                activeDrag?.tradeId,
                activeDrag?.kind,
            ) {
                var minP = if (slice.isNotEmpty()) slice.minOf { it.low } else quote.bid
                var maxP = if (slice.isNotEmpty()) slice.maxOf { it.high } else quote.ask
                // Always include current quote when latest candles are near or visible
                if (clampedScroll <= 4f || slice.isEmpty()) {
                    minP = min(minP, quote.bid)
                    maxP = max(maxP, quote.ask)
                }

                openTrades.forEach { t ->
                    minP = min(minP, t.entryPrice)
                    maxP = max(maxP, t.entryPrice)
                    val isDraggingSl = activeDrag?.tradeId == t.id && activeDrag?.kind == DragLineKind.SL
                    val isDraggingTp = activeDrag?.tradeId == t.id && activeDrag?.kind == DragLineKind.TP
                    if (!isDraggingSl && t.stopLoss > 0.0) {
                        minP = min(minP, t.stopLoss)
                        maxP = max(maxP, t.stopLoss)
                    }
                    if (!isDraggingTp && t.takeProfit > 0.0) {
                        minP = min(minP, t.takeProfit)
                        maxP = max(maxP, t.takeProfit)
                    }
                }

                stagedOrder?.let { staged ->
                    minP = min(minP, staged.entryPrice)
                    maxP = max(maxP, staged.entryPrice)
                    val isDraggingStagedSl = activeDrag?.kind == DragLineKind.STAGED_SL
                    val isDraggingStagedTp = activeDrag?.kind == DragLineKind.STAGED_TP
                    if (!isDraggingStagedSl && staged.stopLoss > 0.0) {
                        minP = min(minP, staged.stopLoss)
                        maxP = max(maxP, staged.stopLoss)
                    }
                    if (!isDraggingStagedTp && staged.takeProfit > 0.0) {
                        minP = min(minP, staged.takeProfit)
                        maxP = max(maxP, staged.takeProfit)
                    }
                }

                val center = (maxP + minP) / 2.0
                val halfSpan = ((maxP - minP) / 2.0)
                    .coerceAtLeast(instrument.tickStep * 6.0) * 1.16
                Pair(center, halfSpan)
            }

            val bounds = remember(
                autoScalePrice,
                autoBounds,
                manualCenterPrice,
                manualHalfSpan,
                chartWidth,
                chartHeight,
                yAxisWidth,
                xAxisHeight,
                startIdx,
                endIdx,
            ) {
                val (center, halfSpan) = if (autoScalePrice) {
                    autoBounds
                } else {
                    Pair(
                        manualCenterPrice,
                        manualHalfSpan.coerceAtLeast(instrument.tickStep * 4.0),
                    )
                }
                val vMin = center - halfSpan
                val vMax = center + halfSpan
                ChartPriceBounds(
                    viewMin = vMin,
                    viewMax = vMax,
                    priceRange = (vMax - vMin).coerceAtLeast(instrument.tickStep),
                    centerPrice = center,
                    halfSpan = halfSpan,
                    chartWidth = chartWidth,
                    chartHeight = chartHeight,
                    yAxisWidth = yAxisWidth,
                    xAxisHeight = xAxisHeight,
                    startIdx = startIdx,
                    endIdx = endIdx,
                )
            }
            val currentBounds by rememberUpdatedState(bounds)

            val levelHitRadiusPx = with(density) { 28.dp.toPx() }
            val lineHitRadiusPx = with(density) { 22.dp.toPx() }
            val touchSlop = with(density) { 8.dp.toPx() }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("candlestick_chart_canvas")
                    .pointerInput(instrument.symbol) {
                        awaitEachGesture {
                            activeFlingJob?.cancel()
                            activeZoomJob?.cancel()

                            val firstDown = awaitFirstDown(requireUnconsumed = false)
                            val downPos = firstDown.position
                            val b = currentBounds
                            val tradesSnapshot = currentOpenTrades
                            val stagedSnapshot = currentStagedOrder

                            val velocityTracker = VelocityTracker().apply {
                                addPosition(firstDown.uptimeMillis, downPos)
                            }

                            var mode = when {
                                downPos.x >= b.chartWidth -> GestureMode.Y_AXIS_ZOOM
                                downPos.y >= b.chartHeight -> GestureMode.X_AXIS_ZOOM
                                else -> GestureMode.NONE
                            }

                            // Priority 1: Drag Staged SL/TP or Open Trade SL/TP handles
                            if (mode == GestureMode.NONE) {
                                var matchedDrag: ActiveDragLevel? = null
                                var bestDist = Float.MAX_VALUE

                                if (stagedSnapshot != null) {
                                    if (stagedSnapshot.stopLoss > 0.0) {
                                        val slY = b.priceToY(stagedSnapshot.stopLoss)
                                        val d = abs(downPos.y - slY)
                                        if (d <= levelHitRadiusPx && d < bestDist) {
                                            bestDist = d
                                            matchedDrag = ActiveDragLevel(
                                                STAGED_TRADE_ID,
                                                DragLineKind.STAGED_SL,
                                                stagedSnapshot.stopLoss,
                                            )
                                        }
                                    }
                                    if (stagedSnapshot.takeProfit > 0.0) {
                                        val tpY = b.priceToY(stagedSnapshot.takeProfit)
                                        val d = abs(downPos.y - tpY)
                                        if (d <= levelHitRadiusPx && d < bestDist) {
                                            bestDist = d
                                            matchedDrag = ActiveDragLevel(
                                                STAGED_TRADE_ID,
                                                DragLineKind.STAGED_TP,
                                                stagedSnapshot.takeProfit,
                                            )
                                        }
                                    }
                                }

                                if (matchedDrag == null) {
                                    for (trade in tradesSnapshot) {
                                        if (trade.stopLoss > 0.0) {
                                            val slY = b.priceToY(trade.stopLoss)
                                            val d = abs(downPos.y - slY)
                                            if (d <= levelHitRadiusPx && d < bestDist) {
                                                bestDist = d
                                                matchedDrag = ActiveDragLevel(
                                                    trade.id,
                                                    DragLineKind.SL,
                                                    trade.stopLoss,
                                                )
                                            }
                                        }
                                        if (trade.takeProfit > 0.0) {
                                            val tpY = b.priceToY(trade.takeProfit)
                                            val d = abs(downPos.y - tpY)
                                            if (d <= levelHitRadiusPx && d < bestDist) {
                                                bestDist = d
                                                matchedDrag = ActiveDragLevel(
                                                    trade.id,
                                                    DragLineKind.TP,
                                                    trade.takeProfit,
                                                )
                                            }
                                        }
                                    }
                                }

                                if (matchedDrag != null) {
                                    mode = GestureMode.DRAG_LEVEL
                                    activeDrag = matchedDrag
                                } else {
                                    mode = GestureMode.PAN_OR_TAP
                                }
                            }

                            var totalDistance = 0f
                            var prevMultiCount = 1
                            var prevMultiSpanY = 0f

                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break

                                if (pressed.size >= 2 && mode != GestureMode.DRAG_LEVEL) {
                                    mode = GestureMode.PINCH_ZOOM
                                    val p0 = pressed[0].position
                                    val p1 = pressed[1].position
                                    val currSpanX = abs(p1.x - p0.x)
                                    val currSpanY = abs(p1.y - p0.y)

                                    if (prevMultiCount >= 2) {
                                        val zoomFactor = event.calculateZoom()
                                        val centroid = event.calculateCentroid(useCurrent = true)
                                        val pan = event.calculatePan()

                                        if (zoomFactor.isFinite() && zoomFactor != 1f && centroid.x.isFinite()) {
                                            val maxC = max(currentCandles.size.toFloat(), 120f)
                                            val oldVisible = visibleCandleCount
                                            val isPredominantlyVertical = currSpanY > currSpanX * 1.45f &&
                                                prevMultiSpanY > 18f && currSpanY > 18f

                                            if (isPredominantlyVertical) {
                                                if (autoScalePrice) {
                                                    manualCenterPrice = currentBounds.centerPrice
                                                    manualHalfSpan = currentBounds.halfSpan
                                                    autoScalePrice = false
                                                }
                                                val yScale = (currSpanY / prevMultiSpanY).coerceIn(0.84f, 1.18f)
                                                val minSpan = currentInstrument.tickStep * 4.0
                                                val maxSpan = max(currentBounds.halfSpan * 12.0, minSpan * 50.0)
                                                manualHalfSpan = (manualHalfSpan / yScale.toDouble())
                                                    .coerceIn(minSpan, maxSpan)
                                            } else {
                                                val clampedZoom = zoomFactor.coerceIn(0.82f, 1.22f)
                                                val newVisible = (oldVisible / clampedZoom)
                                                    .coerceIn(MIN_VISIBLE_CANDLES, maxC)
                                                val rightRatio = ((b.chartWidth - centroid.x) / b.chartWidth)
                                                    .coerceIn(0f, 1f)
                                                visibleCandleCount = newVisible
                                                scrollOffsetCandles += rightRatio * (oldVisible - newVisible)
                                            }
                                        }

                                        if (pan.x.isFinite() && pan.y.isFinite()) {
                                            val slotW = (b.chartWidth / max(visibleCandleCount, MIN_VISIBLE_CANDLES))
                                                .coerceAtLeast(3f)
                                            val minS = minScrollOffset(visibleCandleCount)
                                            val maxS = maxScrollOffset(currentCandles.size)
                                            scrollOffsetCandles = (scrollOffsetCandles + pan.x / slotW)
                                                .coerceIn(minS, maxS)
                                            if (!autoScalePrice) {
                                                val pricePerPx = (manualHalfSpan * 2.0) /
                                                    b.chartHeight.toDouble().coerceAtLeast(1.0)
                                                manualCenterPrice += pan.y.toDouble() * pricePerPx
                                            }
                                        }
                                    }

                                    prevMultiCount = pressed.size
                                    prevMultiSpanY = currSpanY
                                    pressed.forEach { if (it.positionChanged()) it.consume() }
                                    continue
                                } else {
                                    // Smooth recovery when user lifts 1 finger from a 2-finger pinch back to 1 finger
                                    if (prevMultiCount >= 2 && mode == GestureMode.PINCH_ZOOM) {
                                        prevMultiCount = 1
                                        prevMultiSpanY = 0f
                                        mode = GestureMode.PAN_OR_TAP
                                        velocityTracker.resetTracking()
                                        pressed.firstOrNull()?.let {
                                            velocityTracker.addPosition(it.uptimeMillis, it.position)
                                        }
                                        continue
                                    }
                                    prevMultiCount = 1
                                    prevMultiSpanY = 0f
                                }

                                val change = pressed.first()
                                val delta = change.position - change.previousPosition
                                totalDistance += delta.getDistance()

                                when (mode) {
                                    GestureMode.DRAG_LEVEL -> {
                                        val drag = activeDrag
                                        if (drag != null) {
                                            val rawPrice = b.yToPrice(change.position.y.coerceIn(0f, b.chartHeight))
                                            val snapped = snapPrice(rawPrice, currentInstrument.decimals)
                                                .coerceAtLeast(currentInstrument.tickStep)
                                            val staged = currentStagedOrder
                                            val constrained = when {
                                                drag.kind == DragLineKind.STAGED_SL && staged != null -> {
                                                    if (staged.side == "BUY") {
                                                        val maxAllowed = snapPrice(
                                                            (staged.entryPrice - currentInstrument.tickStep)
                                                                .coerceAtLeast(currentInstrument.tickStep),
                                                            currentInstrument.decimals,
                                                        )
                                                        min(snapped, maxAllowed)
                                                    } else {
                                                        val minAllowed = snapPrice(
                                                            staged.entryPrice + currentInstrument.tickStep,
                                                            currentInstrument.decimals,
                                                        )
                                                        max(snapped, minAllowed)
                                                    }
                                                }
                                                drag.kind == DragLineKind.STAGED_TP && staged != null -> {
                                                    if (staged.side == "BUY") {
                                                        val minAllowed = snapPrice(
                                                            staged.entryPrice + currentInstrument.tickStep,
                                                            currentInstrument.decimals,
                                                        )
                                                        max(snapped, minAllowed)
                                                    } else {
                                                        val maxAllowed = snapPrice(
                                                            (staged.entryPrice - currentInstrument.tickStep)
                                                                .coerceAtLeast(currentInstrument.tickStep),
                                                            currentInstrument.decimals,
                                                        )
                                                        min(snapped, maxAllowed)
                                                    }
                                                }
                                                else -> snapped
                                            }
                                            activeDrag = drag.copy(previewPrice = constrained)
                                            if (drag.kind == DragLineKind.STAGED_SL) {
                                                currentOnStagedSlChange(constrained)
                                            } else if (drag.kind == DragLineKind.STAGED_TP) {
                                                currentOnStagedTpChange(constrained)
                                            }
                                            change.consume()
                                        }
                                    }

                                    GestureMode.Y_AXIS_ZOOM -> {
                                        if (abs(delta.y) > 0.3f) {
                                            if (autoScalePrice) {
                                                manualCenterPrice = currentBounds.centerPrice
                                                manualHalfSpan = currentBounds.halfSpan
                                                autoScalePrice = false
                                            }
                                            val scaleFactor = exp(-delta.y * 0.0065f).coerceIn(0.85f, 1.18f)
                                            val minSpan = currentInstrument.tickStep * 4.0
                                            val maxSpan = max(currentBounds.halfSpan * 15.0, minSpan * 60.0)
                                            manualHalfSpan = (manualHalfSpan / scaleFactor.toDouble())
                                                .coerceIn(minSpan, maxSpan)
                                            change.consume()
                                        }
                                    }

                                    GestureMode.X_AXIS_ZOOM -> {
                                        if (abs(delta.x) > 0.3f) {
                                            val maxC = max(currentCandles.size.toFloat(), 120f)
                                            val factor = exp(-delta.x * 0.0055f).coerceIn(0.85f, 1.18f)
                                            val nextVisible = (visibleCandleCount * factor)
                                                .coerceIn(MIN_VISIBLE_CANDLES, maxC)
                                            visibleCandleCount = nextVisible
                                            scrollOffsetCandles = scrollOffsetCandles.coerceIn(
                                                minScrollOffset(nextVisible),
                                                maxScrollOffset(currentCandles.size),
                                            )
                                            change.consume()
                                        }
                                    }

                                    GestureMode.PAN_OR_TAP -> {
                                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                                        if (totalDistance > touchSlop) {
                                            val slotW = (b.chartWidth / max(visibleCandleCount, MIN_VISIBLE_CANDLES))
                                                .coerceAtLeast(3f)
                                            val minS = minScrollOffset(visibleCandleCount)
                                            val maxS = maxScrollOffset(currentCandles.size)
                                            scrollOffsetCandles = (scrollOffsetCandles + delta.x / slotW)
                                                .coerceIn(minS, maxS)
                                            if (!autoScalePrice) {
                                                val pricePerPx = (manualHalfSpan * 2.0) /
                                                    b.chartHeight.toDouble().coerceAtLeast(1.0)
                                                manualCenterPrice += delta.y.toDouble() * pricePerPx
                                            }
                                            change.consume()
                                        }
                                    }

                                    GestureMode.PINCH_ZOOM, GestureMode.NONE -> Unit
                                }
                            }

                            // Gesture finished
                            when (mode) {
                                GestureMode.DRAG_LEVEL -> {
                                    val committed = activeDrag
                                    activeDrag = null
                                    if (committed != null) {
                                        when (committed.kind) {
                                            DragLineKind.STAGED_SL -> {
                                                currentOnStagedSlChange(committed.previewPrice)
                                            }
                                            DragLineKind.STAGED_TP -> {
                                                currentOnStagedTpChange(committed.previewPrice)
                                            }
                                            DragLineKind.SL, DragLineKind.TP -> {
                                                if (totalDistance > touchSlop) {
                                                    val trade = currentOpenTrades.firstOrNull { it.id == committed.tradeId }
                                                    if (trade != null) {
                                                        val newSl = if (committed.kind == DragLineKind.SL) {
                                                            committed.previewPrice
                                                        } else {
                                                            trade.stopLoss
                                                        }
                                                        val newTp = if (committed.kind == DragLineKind.TP) {
                                                            committed.previewPrice
                                                        } else {
                                                            trade.takeProfit
                                                        }
                                                        currentOnUpdateLevels(trade.id, newSl, newTp, trade.notes)
                                                    }
                                                } else {
                                                    selectedTradeId = if (selectedTradeId == committed.tradeId) null else committed.tradeId
                                                }
                                            }
                                        }
                                    }
                                }

                                GestureMode.Y_AXIS_ZOOM -> {
                                    if (totalDistance <= touchSlop) {
                                        val nowMs = System.currentTimeMillis()
                                        if (nowMs - lastYAxisTapMs < 350L) {
                                            autoScalePrice = true
                                            lastYAxisTapMs = 0L
                                        } else {
                                            lastYAxisTapMs = nowMs
                                        }
                                    }
                                }

                                GestureMode.PAN_OR_TAP -> {
                                    if (totalDistance <= touchSlop) {
                                        val hitTrade = currentOpenTrades.minByOrNull { trade ->
                                            abs(downPos.y - b.priceToY(trade.entryPrice))
                                        }?.takeIf { trade ->
                                            abs(downPos.y - b.priceToY(trade.entryPrice)) <= lineHitRadiusPx
                                        }
                                        selectedTradeId = if (hitTrade != null) {
                                            if (selectedTradeId == hitTrade.id) null else hitTrade.id
                                        } else {
                                            null
                                        }
                                    } else {
                                        // Smooth TradingView-style horizontal inertia fling
                                        val velocity = velocityTracker.calculateVelocity()
                                        var vx = velocity.x
                                        var vy = if (!autoScalePrice) velocity.y else 0f
                                        if (abs(vx) > 140f || abs(vy) > 140f) {
                                            activeFlingJob = coroutineScope.launch {
                                                var lastFrameNs = 0L
                                                var frames = 0
                                                while (frames < 75 && (abs(vx) > 22f || abs(vy) > 22f)) {
                                                    withFrameNanos { frameNs ->
                                                        if (lastFrameNs != 0L) {
                                                            val dt = ((frameNs - lastFrameNs) / 1_000_000_000f)
                                                                .coerceIn(0f, 0.05f)
                                                            val decay = exp(-dt * 4.8f)
                                                            val slotW = (b.chartWidth / max(visibleCandleCount, MIN_VISIBLE_CANDLES))
                                                                .coerceAtLeast(3f)
                                                            val minS = minScrollOffset(visibleCandleCount)
                                                            val maxS = maxScrollOffset(currentCandles.size)
                                                            val nextScroll = scrollOffsetCandles + (vx * dt) / slotW
                                                            if (nextScroll <= minS || nextScroll >= maxS) {
                                                                scrollOffsetCandles = nextScroll.coerceIn(minS, maxS)
                                                                vx = 0f
                                                            } else {
                                                                scrollOffsetCandles = nextScroll
                                                                vx *= decay
                                                            }
                                                            if (!autoScalePrice && abs(vy) > 22f) {
                                                                val pricePerPx = (manualHalfSpan * 2.0) /
                                                                    b.chartHeight.toDouble().coerceAtLeast(1.0)
                                                                manualCenterPrice += (vy * dt).toDouble() * pricePerPx
                                                                vy *= decay
                                                            } else {
                                                                vy = 0f
                                                            }
                                                            frames++
                                                        }
                                                        lastFrameNs = frameNs
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                else -> Unit
                            }
                        }
                    },
            ) {
                if (candles.isEmpty()) return@Canvas

                val slotWidth = bounds.chartWidth / clampedVisible

                // Horizontal grid + Y-axis labels
                val gridRows = 8
                for (i in 0..gridRows) {
                    val frac = i / gridRows.toFloat()
                    val y = frac * bounds.chartHeight
                    drawLine(
                        color = ChartGridLine,
                        start = Offset(0f, y),
                        end = Offset(bounds.chartWidth, y),
                        strokeWidth = 1.5f,
                    )
                    val priceAtRow = bounds.viewMax - frac * bounds.priceRange
                    val label = MarketSimulator.formatPrice(priceAtRow, instrument.decimals)
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        bounds.chartWidth + 12f,
                        (y + 10f).coerceIn(24f, bounds.chartHeight - 6f),
                        labelPaint,
                    )
                }

                // Vertical axis divider & bottom time axis divider
                drawLine(
                    color = Color(0xFF1F2735),
                    start = Offset(bounds.chartWidth, 0f),
                    end = Offset(bounds.chartWidth, bounds.chartHeight),
                    strokeWidth = 1.5f,
                )
                drawLine(
                    color = Color(0xFF1F2735),
                    start = Offset(0f, bounds.chartHeight),
                    end = Offset(bounds.chartWidth, bounds.chartHeight),
                    strokeWidth = 1.5f,
                )

                // Continuous vertical time grid + X-axis labels (including future right-margin space)
                val barIntervalSec = if (candles.size >= 2) {
                    (candles.last().timeEpochSec - candles[candles.lastIndex - 1].timeEpochSec)
                        .coerceAtLeast(60L)
                } else {
                    60L
                }
                val gridStep = max(1, (clampedVisible / 4.2f).roundToInt())
                val minGridIdx = floor(rightEdgeCandleFloatIdx - clampedVisible).toInt() - gridStep
                val maxGridIdx = ceil(rightEdgeCandleFloatIdx).toInt() + gridStep
                val alignedStartGridIdx = (minGridIdx / gridStep) * gridStep

                for (gridIdx in alignedStartGridIdx..maxGridIdx step gridStep) {
                    val x = bounds.chartWidth - slotWidth / 2f -
                        (rightEdgeCandleFloatIdx - gridIdx.toFloat()) * slotWidth
                    if (x in 16f..(bounds.chartWidth - 16f)) {
                        drawLine(
                            color = ChartGridLine,
                            start = Offset(x, 0f),
                            end = Offset(x, bounds.chartHeight),
                            strokeWidth = 1.5f,
                        )
                        val epochSec = when {
                            gridIdx in candles.indices -> candles[gridIdx].timeEpochSec
                            gridIdx > candles.lastIndex ->
                                candles.last().timeEpochSec + (gridIdx - candles.lastIndex) * barIntervalSec
                            else ->
                                candles.first().timeEpochSec + gridIdx * barIntervalSec
                        }
                        val timeLabel = timeFormatter.format(Date(epochSec * 1000L))
                        drawContext.canvas.nativeCanvas.drawText(
                            timeLabel,
                            (x - 36f).coerceIn(8f, bounds.chartWidth - 82f),
                            bounds.chartHeight + 36f,
                            labelPaint,
                        )
                    }
                }

                // Clip chart plot area cleanly so scrolled candles & badges never overlap the Y-axis
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                val fineDash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                val rrDash = PathEffect.dashPathEffect(floatArrayOf(6f, 8f), 0f)
                val pillWidth = (bounds.yAxisWidth - 10f).coerceAtLeast(90f)
                val pillHeight = 36f

                clipRect(
                    left = 0f,
                    top = 0f,
                    right = bounds.chartWidth,
                    bottom = bounds.chartHeight,
                ) {
                    // Candlesticks rendered at continuous float X offsets
                    val bodyWidth = (slotWidth * 0.64f).coerceIn(2.5f, 30f)
                    val firstDraw = (startIdx - 1).coerceAtLeast(0)
                    val lastDraw = min(endIdx, candles.size)
                    for (idx in firstDraw until lastDraw) {
                        val candle = candles[idx]
                        val centerX = bounds.chartWidth - slotWidth / 2f -
                            (rightEdgeCandleFloatIdx - idx.toFloat()) * slotWidth
                        if (centerX < -slotWidth || centerX > bounds.chartWidth + slotWidth) continue

                        val isBull = candle.close >= candle.open
                        val candleColor = if (isBull) BullGreen else BearOrangeRed

                        val highY = bounds.priceToY(candle.high)
                        val lowY = bounds.priceToY(candle.low)
                        val openY = bounds.priceToY(candle.open)
                        val closeY = bounds.priceToY(candle.close)

                        if (lowY >= 0f && highY <= bounds.chartHeight) {
                            drawLine(
                                color = candleColor,
                                start = Offset(centerX, highY.coerceIn(0f, bounds.chartHeight)),
                                end = Offset(centerX, lowY.coerceIn(0f, bounds.chartHeight)),
                                strokeWidth = (slotWidth * 0.14f).coerceIn(1.5f, 3.2f),
                            )
                            val rawTop = min(openY, closeY)
                            val rawBottom = max(openY, closeY)
                            val topY = rawTop.coerceIn(0f, bounds.chartHeight)
                            val bottomY = max(rawBottom, rawTop + 3f).coerceIn(0f, bounds.chartHeight)
                            if (bottomY > topY) {
                                drawRect(
                                    color = candleColor,
                                    topLeft = Offset(centerX - bodyWidth / 2f, topY),
                                    size = Size(bodyWidth, max(bottomY - topY, 2f)),
                                )
                            }
                        }
                    }

                    // Live ASK & BID horizontal lines inside plot
                    val askY = bounds.priceToY(quote.ask)
                    val bidY = bounds.priceToY(quote.bid)
                    if (askY in -4f..(bounds.chartHeight + 4f)) {
                        drawLine(
                            color = BullGreen,
                            start = Offset(0f, askY),
                            end = Offset(bounds.chartWidth, askY),
                            strokeWidth = 2.5f,
                        )
                    }
                    if (bidY in -4f..(bounds.chartHeight + 4f)) {
                        drawLine(
                            color = BearOrangeRed,
                            start = Offset(0f, bidY),
                            end = Offset(bounds.chartWidth, bidY),
                            strokeWidth = 2.5f,
                        )
                    }

                    // Open Position Entry, 1:1 RR (50%) Target, SL, and TP lines inside plot
                    openTrades.forEach { trade ->
                        val isSelected = trade.id == selectedTradeId
                        val entryColor = if (trade.side == "BUY") Color(0xFF38BDF8) else Color(0xFFFBBF24)
                        val entryY = bounds.priceToY(trade.entryPrice)

                        if (entryY in -20f..(bounds.chartHeight + 20f)) {
                            val clampedEntryY = entryY.coerceIn(0f, bounds.chartHeight)
                            drawLine(
                                color = entryColor,
                                start = Offset(0f, clampedEntryY),
                                end = Offset(bounds.chartWidth, clampedEntryY),
                                strokeWidth = if (isSelected) 3.5f else 2.5f,
                                pathEffect = dashEffect,
                            )

                            val floatingPnl = MarketSimulator.calculateFloatingPnl(trade, quote)
                            val entryTag = "${trade.side} ${
                                MarketSimulator.formatLots(trade.lots, instrument.stepLots)
                            } • ${MarketSimulator.formatSignedCurrency(floatingPnl)}"
                            val tagWidth = tagTextPaint.measureText(entryTag) + 34f
                            drawRoundRect(
                                color = if (isSelected) RoyalBlueBright else Color(0xFF152238),
                                topLeft = Offset(10f, clampedEntryY - pillHeight / 2f),
                                size = Size(tagWidth, pillHeight),
                                cornerRadius = CornerRadius(10f, 10f),
                            )
                            drawRoundRect(
                                color = entryColor,
                                topLeft = Offset(10f, clampedEntryY - pillHeight / 2f),
                                size = Size(6f, pillHeight),
                                cornerRadius = CornerRadius(4f, 4f),
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                entryTag,
                                24f,
                                clampedEntryY + 9f,
                                tagTextPaint,
                            )
                        }

                        val effectiveSl = if (activeDrag?.tradeId == trade.id && activeDrag?.kind == DragLineKind.SL) {
                            activeDrag!!.previewPrice
                        } else {
                            trade.stopLoss
                        }

                        if (effectiveSl > 0.0) {
                            val slY = bounds.priceToY(effectiveSl)
                            if (slY in -20f..(bounds.chartHeight + 20f)) {
                                val clampedSlY = slY.coerceIn(0f, bounds.chartHeight)
                                val isDraggingThis = activeDrag?.tradeId == trade.id && activeDrag?.kind == DragLineKind.SL
                                drawLine(
                                    color = BearOrangeRed,
                                    start = Offset(0f, clampedSlY),
                                    end = Offset(bounds.chartWidth, clampedSlY),
                                    strokeWidth = if (isDraggingThis) 4f else 2.5f,
                                    pathEffect = if (isDraggingThis) null else fineDash,
                                )
                                val slPnl = projectedPnlAtLevel(trade, effectiveSl, instrument)
                                val slLabel = "⇅ SL ${MarketSimulator.formatPrice(effectiveSl, instrument.decimals)} (${
                                    MarketSimulator.formatSignedCurrency(slPnl)
                                })"
                                val slBadgeW = tagTextPaint.measureText(slLabel) + 28f
                                drawRoundRect(
                                    color = if (isDraggingThis) BearOrangeRed else Color(0xFF3B151B),
                                    topLeft = Offset(10f, clampedSlY - pillHeight / 2f),
                                    size = Size(slBadgeW, pillHeight),
                                    cornerRadius = CornerRadius(10f, 10f),
                                )
                                drawContext.canvas.nativeCanvas.drawText(
                                    slLabel,
                                    22f,
                                    clampedSlY + 9f,
                                    tagTextPaint,
                                )
                            }
                        }

                        val effectiveTp = if (activeDrag?.tradeId == trade.id && activeDrag?.kind == DragLineKind.TP) {
                            activeDrag!!.previewPrice
                        } else {
                            trade.takeProfit
                        }
                        if (effectiveTp > 0.0) {
                            val tpY = bounds.priceToY(effectiveTp)
                            if (tpY in -20f..(bounds.chartHeight + 20f)) {
                                val clampedTpY = tpY.coerceIn(0f, bounds.chartHeight)
                                val isDraggingThis = activeDrag?.tradeId == trade.id && activeDrag?.kind == DragLineKind.TP
                                drawLine(
                                    color = BullGreenBright,
                                    start = Offset(0f, clampedTpY),
                                    end = Offset(bounds.chartWidth, clampedTpY),
                                    strokeWidth = if (isDraggingThis) 4f else 2.5f,
                                    pathEffect = if (isDraggingThis) null else fineDash,
                                )
                                val tpPnl = projectedPnlAtLevel(trade, effectiveTp, instrument)
                                val tpLabel = "⇅ TP ${MarketSimulator.formatPrice(effectiveTp, instrument.decimals)} (${
                                    MarketSimulator.formatSignedCurrency(tpPnl)
                                })"
                                val tpBadgeW = tagTextPaint.measureText(tpLabel) + 28f
                                drawRoundRect(
                                    color = if (isDraggingThis) BullGreen else Color(0xFF0E3325),
                                    topLeft = Offset(10f, clampedTpY - pillHeight / 2f),
                                    size = Size(tpBadgeW, pillHeight),
                                    cornerRadius = CornerRadius(10f, 10f),
                                )
                                drawContext.canvas.nativeCanvas.drawText(
                                    tpLabel,
                                    22f,
                                    clampedTpY + 9f,
                                    tagTextPaint,
                                )
                            }
                        }
                    }

                    // Staged Pre-Trade Risk Order Preview inside plot
                    if (stagedOrder != null) {
                        val effectiveSl = if (activeDrag?.kind == DragLineKind.STAGED_SL) {
                            activeDrag!!.previewPrice
                        } else {
                            stagedOrder.stopLoss
                        }
                        val effectiveTp = if (activeDrag?.kind == DragLineKind.STAGED_TP) {
                            activeDrag!!.previewPrice
                        } else {
                            stagedOrder.takeProfit
                        }
                        val entryY = bounds.priceToY(stagedOrder.entryPrice).coerceIn(0f, bounds.chartHeight)
                        val slY = bounds.priceToY(effectiveSl).coerceIn(0f, bounds.chartHeight)

                        val riskTop = min(entryY, slY)
                        val riskHeight = abs(slY - entryY)
                        if (riskHeight > 1f) {
                            drawRect(
                                color = BearOrangeRed.copy(alpha = 0.13f),
                                topLeft = Offset(0f, riskTop),
                                size = Size(bounds.chartWidth, riskHeight),
                            )
                        }

                        if (effectiveTp > 0.0) {
                            val tpY = bounds.priceToY(effectiveTp).coerceIn(0f, bounds.chartHeight)
                            val rewardTop = min(entryY, tpY)
                            val rewardHeight = abs(tpY - entryY)
                            if (rewardHeight > 1f) {
                                drawRect(
                                    color = BullGreenBright.copy(alpha = 0.11f),
                                    topLeft = Offset(0f, rewardTop),
                                    size = Size(bounds.chartWidth, rewardHeight),
                                )
                            }

                            val isDraggingTp = activeDrag?.kind == DragLineKind.STAGED_TP
                            drawLine(
                                color = BullGreenBright,
                                start = Offset(0f, tpY),
                                end = Offset(bounds.chartWidth, tpY),
                                strokeWidth = if (isDraggingTp) 4.5f else 3f,
                                pathEffect = if (isDraggingTp) null else fineDash,
                            )
                            val tpDist = abs(effectiveTp - stagedOrder.entryPrice)
                            val slDist = abs(stagedOrder.entryPrice - effectiveSl).coerceAtLeast(instrument.tickStep)
                            val rrRatio = tpDist / slDist
                            val tpReward = tpDist * stagedOrder.calculatedLots *
                                instrument.contractSize * stagedOrder.quoteToDepositRate
                            val tpText = "⇅ TP ${MarketSimulator.formatPrice(effectiveTp, instrument.decimals)} (${
                                MarketSimulator.formatSignedCurrency(tpReward, stagedOrder.depositCurrency)
                            } • 1:${String.format(Locale.US, "%.1f", rrRatio)})"
                            val tpTagW = tagTextPaint.measureText(tpText) + 28f
                            drawRoundRect(
                                color = if (isDraggingTp) BullGreen else Color(0xFF0E3B2A),
                                topLeft = Offset(10f, tpY - pillHeight / 2f),
                                size = Size(tpTagW, pillHeight),
                                cornerRadius = CornerRadius(10f, 10f),
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                tpText,
                                22f,
                                tpY + 9f,
                                tagTextPaint,
                            )
                        }

                        val stagedEntryColor = if (stagedOrder.side == "BUY") Color(0xFF38BDF8) else Color(0xFFFBBF24)
                        drawLine(
                            color = stagedEntryColor,
                            start = Offset(0f, entryY),
                            end = Offset(bounds.chartWidth, entryY),
                            strokeWidth = 3f,
                            pathEffect = dashEffect,
                        )
                        val formattedStagedLots = MarketSimulator.formatLots(
                            stagedOrder.calculatedLots,
                            stagedOrder.stepLots,
                        )
                        val entryBadge = "${stagedOrder.side} PLAN • $formattedStagedLots lots"
                        val entryBadgeW = tagTextPaint.measureText(entryBadge) + 28f
                        drawRoundRect(
                            color = Color(0xFF15253E),
                            topLeft = Offset(10f, entryY - pillHeight / 2f),
                            size = Size(entryBadgeW, pillHeight),
                            cornerRadius = CornerRadius(10f, 10f),
                        )
                        drawRoundRect(
                            color = stagedEntryColor,
                            topLeft = Offset(10f, entryY - pillHeight / 2f),
                            size = Size(6f, pillHeight),
                            cornerRadius = CornerRadius(4f, 4f),
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            entryBadge,
                            22f,
                            entryY + 9f,
                            tagTextPaint,
                        )

                        val isDraggingSl = activeDrag?.kind == DragLineKind.STAGED_SL
                        drawLine(
                            color = BearOrangeRed,
                            start = Offset(0f, slY),
                            end = Offset(bounds.chartWidth, slY),
                            strokeWidth = if (isDraggingSl) 5f else 3.5f,
                            pathEffect = if (isDraggingSl) null else dashEffect,
                        )
                        val activeSlPips = if (instrument.pipSize > 0.0) {
                            abs(stagedOrder.entryPrice - effectiveSl) / instrument.pipSize
                        } else {
                            stagedOrder.stopDistancePips
                        }
                        val activeSlLoss = abs(stagedOrder.entryPrice - effectiveSl) *
                            stagedOrder.calculatedLots * instrument.contractSize * stagedOrder.quoteToDepositRate
                        val slRiskText = "⇅ SL ${MarketSimulator.formatPrice(effectiveSl, instrument.decimals)} • ${
                            String.format(Locale.US, "%.1fp", activeSlPips)
                        } • $formattedStagedLots lots (${
                            MarketSimulator.formatSignedCurrency(-activeSlLoss, stagedOrder.depositCurrency)
                        })"
                        val slBadgeW = tagTextPaint.measureText(slRiskText) + 30f
                        drawRoundRect(
                            color = BearOrangeRed,
                            topLeft = Offset(10f, slY - pillHeight / 2f),
                            size = Size(slBadgeW, pillHeight),
                            cornerRadius = CornerRadius(10f, 10f),
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            slRiskText,
                            24f,
                            slY + 9f,
                            tagTextPaint,
                        )
                    }

                    // Fibonacci-style Risk:Reward Ladder (drawn for active staged order, selected trade, or first trade with SL)
                    val fibSide: String?
                    val fibEntry: Double
                    val fibSl: Double
                    val fibExecutedCsv: String
                    val fibLegacyExecuted: Boolean
                    val fibDrawRiskBand: Boolean

                    if (stagedOrder != null) {
                        val effectiveStagedSl = if (activeDrag?.kind == DragLineKind.STAGED_SL) {
                            activeDrag!!.previewPrice
                        } else {
                            stagedOrder.stopLoss
                        }
                        fibSide = if (effectiveStagedSl > 0.0) stagedOrder.side else null
                        fibEntry = stagedOrder.entryPrice
                        fibSl = effectiveStagedSl
                        fibExecutedCsv = ""
                        fibLegacyExecuted = false
                        fibDrawRiskBand = false
                    } else {
                        val targetTrade = openTrades.firstOrNull { it.id == selectedTradeId && it.stopLoss > 0.0 }
                            ?: openTrades.firstOrNull { it.stopLoss > 0.0 }
                        if (targetTrade != null) {
                            val effectiveTradeSl = if (activeDrag?.tradeId == targetTrade.id && activeDrag?.kind == DragLineKind.SL) {
                                activeDrag!!.previewPrice
                            } else {
                                targetTrade.stopLoss
                            }
                            fibSide = if (effectiveTradeSl > 0.0) targetTrade.side else null
                            fibEntry = targetTrade.entryPrice
                            fibSl = effectiveTradeSl
                            fibExecutedCsv = targetTrade.executedPartialTiers
                            fibLegacyExecuted = targetTrade.partialCloseExecuted
                            fibDrawRiskBand = true
                        } else {
                            fibSide = null
                            fibEntry = 0.0
                            fibSl = 0.0
                            fibExecutedCsv = ""
                            fibLegacyExecuted = false
                            fibDrawRiskBand = false
                        }
                    }

                    if (fibSide != null && (showFibonacciRrOverlay || autoPartialCloseEnabled)) {
                        val fibStartX = bounds.chartWidth * 0.34f
                        val tiersByKey = if (autoPartialCloseEnabled) {
                            partialTpTiers.associateBy { MarketSimulator.tierKey(it.rrMultiple) }
                        } else {
                            emptyMap()
                        }
                        val executedSet = fibExecutedCsv.split(',')
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .toMutableSet().apply {
                                if (fibLegacyExecuted && isEmpty()) add("1.0")
                            }

                        val fibMultiples = buildList {
                            if (showFibonacciRrOverlay) {
                                add(1.0)
                                add(2.0)
                                add(3.0)
                            }
                            if (autoPartialCloseEnabled) {
                                partialTpTiers.forEach { add(it.rrMultiple) }
                            }
                        }
                            .sorted()
                            .distinctBy { MarketSimulator.tierKey(it) }

                        val fibEntryY = bounds.priceToY(fibEntry)
                        val fibSlY = bounds.priceToY(fibSl)

                        if (showFibonacciRrOverlay) {
                            // Fibonacci Risk Band (-1R .. 0R) for open trade
                            if (fibDrawRiskBand) {
                                val rTop = min(fibEntryY, fibSlY).coerceIn(0f, bounds.chartHeight)
                                val rBottom = max(fibEntryY, fibSlY).coerceIn(0f, bounds.chartHeight)
                                if (rBottom - rTop > 1f) {
                                    drawRect(
                                        color = BearOrangeRed.copy(alpha = 0.075f),
                                        topLeft = Offset(fibStartX, rTop),
                                        size = Size(bounds.chartWidth - fibStartX, rBottom - rTop),
                                    )
                                }
                            }

                            // Alternating translucent Fibonacci R:R zone bands (0->1R, 1R->2R, 2R->3R)
                            val bandAnchors = listOf(0.0, 1.0, 2.0, 3.0)
                            val bandColors = listOf(
                                Color(0xFF0EA5E9).copy(alpha = 0.07f),
                                BullGreenBright.copy(alpha = 0.07f),
                                Color(0xFFF59E0B).copy(alpha = 0.055f),
                            )
                            for (bIdx in 0 until bandColors.size) {
                                val p0 = if (bandAnchors[bIdx] == 0.0) {
                                    fibEntry
                                } else {
                                    MarketSimulator.rrTargetPrice(fibSide, fibEntry, fibSl, bandAnchors[bIdx])
                                }
                                val p1 = MarketSimulator.rrTargetPrice(fibSide, fibEntry, fibSl, bandAnchors[bIdx + 1])
                                if (p0 != null && p1 != null) {
                                    val y0 = bounds.priceToY(p0)
                                    val y1 = bounds.priceToY(p1)
                                    val topB = min(y0, y1).coerceIn(0f, bounds.chartHeight)
                                    val botB = max(y0, y1).coerceIn(0f, bounds.chartHeight)
                                    if (botB - topB > 1f) {
                                        drawRect(
                                            color = bandColors[bIdx],
                                            topLeft = Offset(fibStartX, topB),
                                            size = Size(bounds.chartWidth - fibStartX, botB - topB),
                                        )
                                    }
                                }
                            }

                            // Left anchor vertical spine for the Fibonacci ladder
                            val topLevelPrice = fibMultiples.lastOrNull()?.let {
                                MarketSimulator.rrTargetPrice(fibSide, fibEntry, fibSl, it)
                            } ?: fibEntry
                            val topY = bounds.priceToY(topLevelPrice)
                            val spineTop = minOf(fibSlY, fibEntryY, topY).coerceIn(0f, bounds.chartHeight)
                            val spineBot = maxOf(fibSlY, fibEntryY, topY).coerceIn(0f, bounds.chartHeight)
                            if (spineBot - spineTop > 4f) {
                                drawLine(
                                    color = Color(0xFF475569).copy(alpha = 0.65f),
                                    start = Offset(fibStartX, spineTop),
                                    end = Offset(fibStartX, spineBot),
                                    strokeWidth = 1.8f,
                                    pathEffect = rrDash,
                                )
                            }
                        }

                        // Draw each Fibonacci R:R horizontal line + pill badge
                        fibMultiples.forEach { rr ->
                            val targetPrice = MarketSimulator.rrTargetPrice(fibSide, fibEntry, fibSl, rr)
                                ?: return@forEach
                            val y = bounds.priceToY(targetPrice)
                            if (y < -14f || y > bounds.chartHeight + 14f) return@forEach

                            val clampedY = y.coerceIn(14f, bounds.chartHeight - 14f)
                            val key = MarketSimulator.tierKey(rr)
                            val tier = tiersByKey[key]
                            val isExecuted = executedSet.contains(key)
                            val rrStr = "1:${MarketSimulator.formatRrMultiple(rr)}"
                            val priceStr = MarketSimulator.formatPrice(targetPrice, instrument.decimals)

                            val lineColor = when {
                                tier != null && isExecuted -> BullGreenBright.copy(alpha = 0.65f)
                                tier != null -> Color(0xFF2DD4BF)
                                rr <= 1.05 -> Color(0xFF38BDF8).copy(alpha = 0.65f)
                                rr <= 2.05 -> BullGreenBright.copy(alpha = 0.65f)
                                else -> Color(0xFFFBBF24).copy(alpha = 0.60f)
                            }
                            val lineStartX = if (showFibonacciRrOverlay) fibStartX else bounds.chartWidth * 0.45f
                            drawLine(
                                color = lineColor,
                                start = Offset(lineStartX, clampedY),
                                end = Offset(bounds.chartWidth, clampedY),
                                strokeWidth = if (tier != null && !isExecuted) 2.4f else 1.8f,
                                pathEffect = rrDash,
                            )

                            val badgeText = when {
                                tier != null && isExecuted -> "✓ $rrStr (${tier.closePercent.toInt()}%) • $priceStr"
                                tier != null -> "$rrStr (${tier.closePercent.toInt()}%) • $priceStr"
                                else -> "$rrStr • $priceStr"
                            }
                            val badgeBg = when {
                                tier != null && isExecuted -> Color(0xFF0E3325).copy(alpha = 0.92f)
                                tier != null -> Color(0xFF0F2F2E).copy(alpha = 0.95f)
                                else -> Color(0xFF111827).copy(alpha = 0.84f)
                            }
                            val badgeW = tagTextPaint.measureText(badgeText) + 20f
                            val badgeLeft = (bounds.chartWidth - badgeW - 8f).coerceAtLeast(lineStartX + 4f)
                            drawRoundRect(
                                color = badgeBg,
                                topLeft = Offset(badgeLeft, clampedY - 14f),
                                size = Size(badgeW, 28f),
                                cornerRadius = CornerRadius(7f, 7f),
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                badgeText,
                                badgeLeft + 10f,
                                clampedY + 7f,
                                tagTextPaint,
                            )
                        }
                    }
                }

                // Right Y-axis pills for Open Trades SL/TP, Staged SL/TP, and Live ASK/BID
                openTrades.forEach { trade ->
                    val effectiveSl = if (activeDrag?.tradeId == trade.id && activeDrag?.kind == DragLineKind.SL) {
                        activeDrag!!.previewPrice
                    } else {
                        trade.stopLoss
                    }
                    if (effectiveSl > 0.0) {
                        val slY = bounds.priceToY(effectiveSl)
                        if (slY in -20f..(bounds.chartHeight + 20f)) {
                            val clampedSlY = slY.coerceIn(0f, bounds.chartHeight)
                            drawRoundRect(
                                color = BearOrangeRed.copy(alpha = 0.9f),
                                topLeft = Offset(bounds.chartWidth + 6f, clampedSlY - pillHeight / 2f),
                                size = Size(pillWidth, pillHeight),
                                cornerRadius = CornerRadius(10f, 10f),
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                MarketSimulator.formatPrice(effectiveSl, instrument.decimals),
                                bounds.chartWidth + 12f,
                                clampedSlY + 9f,
                                pillTextPaint,
                            )
                        }
                    }

                    val effectiveTp = if (activeDrag?.tradeId == trade.id && activeDrag?.kind == DragLineKind.TP) {
                        activeDrag!!.previewPrice
                    } else {
                        trade.takeProfit
                    }
                    if (effectiveTp > 0.0) {
                        val tpY = bounds.priceToY(effectiveTp)
                        if (tpY in -20f..(bounds.chartHeight + 20f)) {
                            val clampedTpY = tpY.coerceIn(0f, bounds.chartHeight)
                            drawRoundRect(
                                color = BullGreenBright.copy(alpha = 0.9f),
                                topLeft = Offset(bounds.chartWidth + 6f, clampedTpY - pillHeight / 2f),
                                size = Size(pillWidth, pillHeight),
                                cornerRadius = CornerRadius(10f, 10f),
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                MarketSimulator.formatPrice(effectiveTp, instrument.decimals),
                                bounds.chartWidth + 12f,
                                clampedTpY + 9f,
                                pillTextPaint,
                            )
                        }
                    }
                }

                if (stagedOrder != null) {
                    val effectiveSl = if (activeDrag?.kind == DragLineKind.STAGED_SL) {
                        activeDrag!!.previewPrice
                    } else {
                        stagedOrder.stopLoss
                    }
                    val effectiveTp = if (activeDrag?.kind == DragLineKind.STAGED_TP) {
                        activeDrag!!.previewPrice
                    } else {
                        stagedOrder.takeProfit
                    }
                    if (effectiveTp > 0.0) {
                        val tpY = bounds.priceToY(effectiveTp).coerceIn(0f, bounds.chartHeight)
                        drawRoundRect(
                            color = BullGreenBright,
                            topLeft = Offset(bounds.chartWidth + 6f, tpY - pillHeight / 2f),
                            size = Size(pillWidth, pillHeight),
                            cornerRadius = CornerRadius(10f, 10f),
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            MarketSimulator.formatPrice(effectiveTp, instrument.decimals),
                            bounds.chartWidth + 12f,
                            tpY + 9f,
                            pillTextPaint,
                        )
                    }
                    val slY = bounds.priceToY(effectiveSl).coerceIn(0f, bounds.chartHeight)
                    drawRoundRect(
                        color = BearOrangeRed,
                        topLeft = Offset(bounds.chartWidth + 6f, slY - pillHeight / 2f),
                        size = Size(pillWidth, pillHeight),
                        cornerRadius = CornerRadius(10f, 10f),
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        MarketSimulator.formatPrice(effectiveSl, instrument.decimals),
                        bounds.chartWidth + 12f,
                        slY + 9f,
                        pillTextPaint,
                    )
                }

                // Live ASK & BID Y-axis pills
                val askPillY = bounds.priceToY(quote.ask).coerceIn(pillHeight / 2f, bounds.chartHeight - pillHeight / 2f)
                val rawBidY = bounds.priceToY(quote.bid).coerceIn(pillHeight / 2f, bounds.chartHeight - pillHeight / 2f)
                drawRoundRect(
                    color = BullGreen,
                    topLeft = Offset(bounds.chartWidth + 6f, askPillY - pillHeight / 2f),
                    size = Size(pillWidth, pillHeight),
                    cornerRadius = CornerRadius(10f, 10f),
                )
                drawContext.canvas.nativeCanvas.drawText(
                    MarketSimulator.formatPrice(quote.ask, instrument.decimals),
                    bounds.chartWidth + 12f,
                    askPillY + 9f,
                    pillTextPaint,
                )

                val effectiveBidY = if (abs(rawBidY - askPillY) < pillHeight + 4f) {
                    (askPillY + pillHeight + 4f).coerceAtMost(bounds.chartHeight - pillHeight / 2f)
                } else {
                    rawBidY
                }
                drawRoundRect(
                    color = BearOrangeRed,
                    topLeft = Offset(bounds.chartWidth + 6f, effectiveBidY - pillHeight / 2f),
                    size = Size(pillWidth, pillHeight),
                    cornerRadius = CornerRadius(10f, 10f),
                )
                drawContext.canvas.nativeCanvas.drawText(
                    MarketSimulator.formatPrice(quote.bid, instrument.decimals),
                    bounds.chartWidth + 12f,
                    effectiveBidY + 9f,
                    pillTextPaint,
                )
            }

            // Selected Open Trade Action Bar (Close in 1 click, +SL, +TP) anchored near the tapped line
            val selectedTrade = openTrades.firstOrNull { it.id == selectedTradeId }
            AnimatedVisibility(
                visible = selectedTrade != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        val trade = selectedTrade ?: return@offset IntOffset.Zero
                        val lineY = bounds.priceToY(trade.entryPrice).roundToInt()
                        val targetY = if (lineY > 140) lineY - 126 else lineY + 34
                        IntOffset(
                            x = with(density) { 12.dp.roundToPx() },
                            y = targetY.coerceIn(
                                with(density) { 64.dp.roundToPx() },
                                (bounds.chartHeight - with(density) { 60.dp.roundToPx() }).roundToInt()
                                    .coerceAtLeast(with(density) { 68.dp.roundToPx() }),
                            ),
                        )
                    },
            ) {
                if (selectedTrade != null) {
                    val pnl = MarketSimulator.calculateFloatingPnl(selectedTrade, quote)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = CardSurface.copy(alpha = 0.96f),
                        border = BorderStroke(1.dp, RoyalBlueBright),
                        shadowElevation = 6.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "${selectedTrade.side} ${
                                    MarketSimulator.formatLots(selectedTrade.lots, instrument.stepLots)
                                } • ${MarketSimulator.formatSignedCurrency(pnl)}",
                                style = MaterialTheme.typography.labelLarge.merge(MonoPriceStyle),
                                color = if (pnl >= 0) BullGreenBright else BearOrangeRed,
                                fontWeight = FontWeight.Bold,
                            )

                            if (selectedTrade.stopLoss <= 0.0) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = BearOrangeRed.copy(alpha = 0.18f),
                                    border = BorderStroke(1.dp, BearOrangeRed.copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            val defaultSl = defaultStopLoss(selectedTrade, instrument)
                                            onUpdateTradeLevels(
                                                selectedTrade.id,
                                                defaultSl,
                                                selectedTrade.takeProfit,
                                                selectedTrade.notes,
                                            )
                                        }
                                        .testTag("chart_add_sl_button"),
                                ) {
                                    Text(
                                        text = "+SL",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = BearOrangeRed,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                }
                            }

                            if (selectedTrade.takeProfit <= 0.0) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = BullGreenBright.copy(alpha = 0.18f),
                                    border = BorderStroke(1.dp, BullGreenBright.copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            val defaultTp = defaultTakeProfit(selectedTrade, instrument)
                                            onUpdateTradeLevels(
                                                selectedTrade.id,
                                                selectedTrade.stopLoss,
                                                defaultTp,
                                                selectedTrade.notes,
                                            )
                                        }
                                        .testTag("chart_add_tp_button"),
                                ) {
                                    Text(
                                        text = "+TP",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = BullGreenBright,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                }
                            }

                            // 1-Click Close Trade Button
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = BearOrangeRed,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        selectedTradeId = null
                                        onCloseTrade(selectedTrade)
                                    }
                                    .testTag("chart_close_trade_button"),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Close trade",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = "Close",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // TradingView-style Zoom Controls & Auto/Free Y-Scale Toggle (Top-Right inside chart)
            val isViewportModified = !autoScalePrice ||
                abs(scrollOffsetCandles - DEFAULT_SCROLL_OFFSET_CANDLES) > 0.5f ||
                abs(visibleCandleCount - DEFAULT_VISIBLE_CANDLES) > 1.5f

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 80.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Auto / Free Y-Scale toggle button
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (autoScalePrice) {
                        Color(0xFF141820).copy(alpha = 0.86f)
                    } else {
                        RoyalBlueBright.copy(alpha = 0.25f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (autoScalePrice) CardBorder else RoyalBlueBright,
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (autoScalePrice) {
                                manualCenterPrice = bounds.centerPrice
                                manualHalfSpan = bounds.halfSpan
                                autoScalePrice = false
                            } else {
                                autoScalePrice = true
                            }
                        }
                        .testTag("chart_autoscale_toggle_button"),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (autoScalePrice) "Auto" else "Free",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (autoScalePrice) TextSecondary else Color(0xFF93C5FD),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF141820).copy(alpha = 0.86f),
                    border = BorderStroke(1.dp, CardBorder),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(44.dp)
                                .clickable { animateZoomTo(visibleCandleCount * 1.28f) }
                                .testTag("chart_zoom_out_button"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Remove,
                                contentDescription = "Zoom out chart",
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(22.dp)
                                .background(CardBorder),
                        )

                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(44.dp)
                                .clickable { animateZoomTo(visibleCandleCount * 0.78f) }
                                .testTag("chart_zoom_in_button"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Zoom in chart",
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                if (isViewportModified) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = RoyalBlueBright.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, RoyalBlueBright),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { resetViewportSmoothly() }
                            .testTag("chart_reset_zoom_button"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = "Reset chart zoom",
                                tint = Color(0xFF93C5FD),
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "Reset",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF93C5FD),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            // Floating Pre-Trade Risk Staging HUD (above bottom Symbol/Timeframe pills)
            if (stagedOrder != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF141B29).copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, RoyalBlueBright),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, end = 82.dp, bottom = 76.dp)
                        .testTag("chart_staged_risk_hud"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val lotsLabel = MarketSimulator.formatLots(
                            stagedOrder.calculatedLots,
                            stagedOrder.stepLots,
                        )
                        val pipsLabel = String.format(Locale.US, "%.1fp", stagedOrder.stopDistancePips)
                        val actualRiskLabel = MarketSimulator.formatCurrency(
                            stagedOrder.actualRiskAmount,
                            stagedOrder.depositCurrency,
                        )
                        Text(
                            text = "SL $pipsLabel • $lotsLabel lots • ${
                                String.format(Locale.US, "%.1f", stagedOrder.riskPercent)
                            }% ($actualRiskLabel)",
                            style = MaterialTheme.typography.labelMedium.merge(MonoPriceStyle),
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (stagedOrder.isBelowMinVolume) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.22f),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                            ) {
                                Text(
                                    text = "Min ${MarketSimulator.formatLots(instrument.minLots, stagedOrder.stepLots)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFBBF24),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (stagedOrder.takeProfit > 0.0) {
                                BullGreenBright.copy(alpha = 0.22f)
                            } else {
                                Color(0xFF1E293B)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (stagedOrder.takeProfit > 0.0) BullGreenBright else CardBorder,
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onToggleStagedTakeProfit)
                                .testTag("chart_staged_tp_toggle"),
                        ) {
                            Text(
                                text = if (stagedOrder.takeProfit > 0.0) "TP ×" else "+TP 1:2",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (stagedOrder.takeProfit > 0.0) BullGreenBright else Color(0xFF93C5FD),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun snapPrice(price: Double, decimals: Int): Double {
    val factor = 10.0.pow(decimals.coerceIn(0, 8))
    return (price * factor).roundToLong() / factor
}

private fun projectedPnlAtLevel(
    trade: TradeRecord,
    targetPrice: Double,
    instrument: MarketInstrument,
): Double {
    val diff = if (trade.side == "BUY") {
        targetPrice - trade.entryPrice
    } else {
        trade.entryPrice - targetPrice
    }
    return diff * trade.lots * instrument.contractSize
}

private fun defaultStopLoss(trade: TradeRecord, instrument: MarketInstrument): Double {
    val step = max(instrument.tickStep * 25.0, trade.entryPrice * 0.0025)
    val raw = if (trade.side == "BUY") trade.entryPrice - step else trade.entryPrice + step
    return snapPrice(raw.coerceAtLeast(instrument.tickStep), instrument.decimals)
}

private fun defaultTakeProfit(trade: TradeRecord, instrument: MarketInstrument): Double {
    val step = max(instrument.tickStep * 40.0, trade.entryPrice * 0.004)
    val raw = if (trade.side == "BUY") trade.entryPrice + step else trade.entryPrice - step
    return snapPrice(raw.coerceAtLeast(instrument.tickStep), instrument.decimals)
}
