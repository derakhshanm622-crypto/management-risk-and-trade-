package com.example.market

import com.example.ctrader.CTraderSymbolInfo
import com.example.data.model.Candle
import com.example.data.model.LiveQuote
import com.example.data.model.MarketInstrument
import com.example.data.model.PartialTpTier
import com.example.data.model.TradeRecord
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

data class RiskPositionPlan(
    val riskCapital: Double,
    val riskPercent: Double,
    val riskAmount: Double,
    val actualRiskAmount: Double = riskAmount,
    val stopDistance: Double,
    val stopDistancePips: Double = 0.0,
    val calculatedLots: Double,
    val lossPerLot: Double,
    val pipValuePerLot: Double = 0.0,
    val alignedVolumeInCents: Long = 0L,
    val isBelowMinVolume: Boolean = false,
    val stepLots: Double = 0.01,
    val minLots: Double = 0.01,
    val maxLots: Double = 100.0,
)

data class PartialClosePlan(
    val triggerPrice: Double,
    val closeLots: Double,
    val remainingLots: Double,
    val realizedPnlEstimate: Double,
    val rrMultiple: Double = 1.0,
    val closePercent: Double = 50.0,
    val tierKey: String = "1.0",
    val isFullClose: Boolean = false,
)

object MarketSimulator {

    val instruments: List<MarketInstrument> = listOf(
        MarketInstrument(
            symbol = "XAUUSD",
            displayName = "Gold / US Dollar",
            category = "Metals",
            basePrice = 4164.28,
            spread = 0.13,
            decimals = 2,
            contractSize = 100.0,
            tickStep = 0.12,
            pipPosition = 1,
            lotSizeInCents = 10_000L,
            minVolumeInCents = 100L,
            maxVolumeInCents = 10_000_000L,
            stepVolumeInCents = 100L,
            quoteCurrency = "USD",
        ),
        MarketInstrument(
            symbol = "EURUSD",
            displayName = "Euro / US Dollar",
            category = "Forex",
            basePrice = 1.08420,
            spread = 0.00012,
            decimals = 5,
            contractSize = 100_000.0,
            tickStep = 0.00006,
            pipPosition = 4,
            lotSizeInCents = 10_000_000L,
            minVolumeInCents = 100_000L,
            maxVolumeInCents = 10_000_000_000L,
            stepVolumeInCents = 100_000L,
            quoteCurrency = "USD",
        ),
        MarketInstrument(
            symbol = "GBPUSD",
            displayName = "British Pound / USD",
            category = "Forex",
            basePrice = 1.26740,
            spread = 0.00015,
            decimals = 5,
            contractSize = 100_000.0,
            tickStep = 0.00008,
            pipPosition = 4,
            lotSizeInCents = 10_000_000L,
            minVolumeInCents = 100_000L,
            maxVolumeInCents = 10_000_000_000L,
            stepVolumeInCents = 100_000L,
            quoteCurrency = "USD",
        ),
        MarketInstrument(
            symbol = "BTCUSD",
            displayName = "Bitcoin / US Dollar",
            category = "Crypto",
            basePrice = 91420.50,
            spread = 18.50,
            decimals = 2,
            contractSize = 1.0,
            tickStep = 14.0,
            pipPosition = 0,
            lotSizeInCents = 100L,
            minVolumeInCents = 1L,
            maxVolumeInCents = 100_000L,
            stepVolumeInCents = 1L,
            quoteCurrency = "USD",
        ),
        MarketInstrument(
            symbol = "NAS100",
            displayName = "US Tech 100 Index",
            category = "Indices",
            basePrice = 21145.25,
            spread = 1.50,
            decimals = 2,
            contractSize = 20.0,
            tickStep = 2.40,
            pipPosition = 1,
            lotSizeInCents = 2_000L,
            minVolumeInCents = 20L,
            maxVolumeInCents = 2_000_000L,
            stepVolumeInCents = 20L,
            quoteCurrency = "USD",
        ),
    )

    val timeframes: List<Pair<String, Int>> = listOf(
        "m1" to 60,
        "m5" to 300,
        "m15" to 900,
        "h1" to 3600,
        "h4" to 14400,
        "d1" to 86400,
    )

    fun getInstrument(symbol: String): MarketInstrument {
        val upper = symbol.uppercase()
        instruments.firstOrNull { it.symbol == upper }?.let { return it }
        val letters = upper.filter { it.isLetter() }
        val inferredQuote = if (letters.length >= 6) letters.takeLast(3) else "USD"
        return when {
            upper.contains("XAU") -> MarketInstrument(
                symbol = upper,
                displayName = "$upper Gold",
                category = "Metals",
                basePrice = 4164.0,
                spread = 0.20,
                decimals = 2,
                contractSize = 100.0,
                tickStep = 0.10,
                pipPosition = 1,
                lotSizeInCents = 10_000L,
                minVolumeInCents = 100L,
                maxVolumeInCents = 10_000_000L,
                stepVolumeInCents = 100L,
                quoteCurrency = "USD",
            )
            upper.contains("XAG") -> MarketInstrument(
                symbol = upper,
                displayName = "$upper Silver",
                category = "Metals",
                basePrice = 32.5,
                spread = 0.03,
                decimals = 3,
                contractSize = 5000.0,
                tickStep = 0.01,
                pipPosition = 2,
                lotSizeInCents = 500_000L,
                minVolumeInCents = 5_000L,
                maxVolumeInCents = 500_000_000L,
                stepVolumeInCents = 5_000L,
                quoteCurrency = "USD",
            )
            upper.contains("JPY") -> MarketInstrument(
                symbol = upper,
                displayName = upper,
                category = "Forex",
                basePrice = 154.0,
                spread = 0.015,
                decimals = 3,
                contractSize = 100_000.0,
                tickStep = 0.01,
                pipPosition = 2,
                lotSizeInCents = 10_000_000L,
                minVolumeInCents = 100_000L,
                maxVolumeInCents = 10_000_000_000L,
                stepVolumeInCents = 100_000L,
                quoteCurrency = "JPY",
            )
            upper.contains("BTC") || upper.contains("ETH") -> MarketInstrument(
                symbol = upper,
                displayName = upper,
                category = "Crypto",
                basePrice = 91420.0,
                spread = 18.0,
                decimals = 2,
                contractSize = 1.0,
                tickStep = 10.0,
                pipPosition = 0,
                lotSizeInCents = 100L,
                minVolumeInCents = 1L,
                maxVolumeInCents = 100_000L,
                stepVolumeInCents = 1L,
                quoteCurrency = "USD",
            )
            upper.contains("US30") || upper.contains("NAS") || upper.contains("SPX") || upper.contains("GER") ->
                MarketInstrument(
                    symbol = upper,
                    displayName = upper,
                    category = "Indices",
                    basePrice = 21000.0,
                    spread = 1.5,
                    decimals = 2,
                    contractSize = 1.0,
                    tickStep = 1.0,
                    pipPosition = 1,
                    lotSizeInCents = 100L,
                    minVolumeInCents = 10L,
                    maxVolumeInCents = 100_000L,
                    stepVolumeInCents = 10L,
                    quoteCurrency = if (upper.contains("GER")) "EUR" else "USD",
                )
            else -> MarketInstrument(
                symbol = upper,
                displayName = upper,
                category = "Forex",
                basePrice = 1.10000,
                spread = 0.00015,
                decimals = 5,
                contractSize = 100_000.0,
                tickStep = 0.00008,
                pipPosition = 4,
                lotSizeInCents = 10_000_000L,
                minVolumeInCents = 100_000L,
                maxVolumeInCents = 10_000_000_000L,
                stepVolumeInCents = 100_000L,
                quoteCurrency = inferredQuote,
            )
        }
    }

    fun resolveInstrument(symbol: String, ctraderSpec: CTraderSymbolInfo?): MarketInstrument {
        val base = getInstrument(symbol)
        if (ctraderSpec == null) return base
        val resolvedDigits = ctraderSpec.digits.coerceIn(0, 8)
        val resolvedPipPos = ctraderSpec.pipPosition.coerceIn(0, resolvedDigits)
        val lotCents = ctraderSpec.lotSizeInCents.coerceAtLeast(100L)
        val minCents = ctraderSpec.minVolumeInCents.coerceAtLeast(1L)
        val maxCents = ctraderSpec.maxVolumeInCents.coerceAtLeast(minCents)
        val stepCents = ctraderSpec.stepVolumeInCents.coerceAtLeast(1L)
        val resolvedContractSize = lotCents.toDouble() / 100.0
        val resolvedTickStep = 1.0 / 10.0.pow(resolvedDigits.toDouble())
        return base.copy(
            decimals = resolvedDigits,
            contractSize = resolvedContractSize,
            tickStep = resolvedTickStep.coerceAtLeast(base.tickStep * 0.1),
            pipPosition = resolvedPipPos,
            lotSizeInCents = lotCents,
            minVolumeInCents = minCents,
            maxVolumeInCents = maxCents,
            stepVolumeInCents = stepCents,
            quoteCurrency = ctraderSpec.quoteCurrency.ifBlank { base.quoteCurrency },
        )
    }

    fun timeframeSeconds(timeframe: String): Int =
        timeframes.firstOrNull { it.first == timeframe }?.second ?: 60

    fun formatPrice(price: Double, decimals: Int): String =
        String.format(Locale.US, "%.${decimals.coerceIn(0, 8)}f", price)

    fun formatLots(lots: Double, stepLots: Double = 0.01): String {
        val safeStep = stepLots.coerceAtLeast(0.0001)
        val precision = ceil(max(0.0, -log10(safeStep))).toInt().coerceIn(1, 4)
        val formatted = String.format(Locale.US, "%.${precision}f", lots)
            .trimEnd('0')
            .trimEnd('.')
        return if (!formatted.contains('.')) "$formatted.0" else formatted
    }

    fun formatCurrency(amount: Double, currency: String = "USD"): String {
        val symbol = when (currency.uppercase()) {
            "EUR" -> "€"
            "GBP" -> "£"
            "JPY" -> "¥"
            else -> "$"
        }
        val absStr = String.format(Locale.US, "%,.2f", abs(amount))
        return if (amount < 0) "-$symbol$absStr" else "$symbol$absStr"
    }

    fun formatSignedCurrency(amount: Double, currency: String = "USD"): String {
        val symbol = when (currency.uppercase()) {
            "EUR" -> "€"
            "GBP" -> "£"
            "JPY" -> "¥"
            else -> "$"
        }
        val absStr = String.format(Locale.US, "%,.2f", abs(amount))
        return if (amount < 0) "-$symbol$absStr" else "+$symbol$absStr"
    }

    fun generateInitialQuotes(nowEpochSec: Long): Map<String, LiveQuote> {
        return instruments.associate { inst ->
            inst.symbol to buildQuoteForInstrument(inst, "m1", nowEpochSec)
        }
    }

    fun buildQuoteForInstrument(
        instrument: MarketInstrument,
        timeframe: String,
        nowEpochSec: Long,
        anchorClose: Double? = null,
    ): LiveQuote {
        val tfSec = timeframeSeconds(timeframe)
        val count = 72
        val currentBucket = (nowEpochSec / tfSec) * tfSec
        val seed = instrument.symbol.hashCode().toLong() * 31L + timeframe.hashCode().toLong()
        val rng = Random(seed)

        val volatilityScale = when (timeframe) {
            "m1" -> 1.0
            "m5" -> 1.8
            "m15" -> 2.8
            "h1" -> 4.6
            "h4" -> 7.5
            else -> 12.0
        }
        val step = instrument.tickStep * volatilityScale

        val rawCandles = mutableListOf<Candle>()
        var price = (anchorClose ?: instrument.basePrice) - step * 3.0

        for (i in 0 until count) {
            val time = currentBucket - (count - 1 - i) * tfSec.toLong()
            val drift = if (i in 38..48) -step * 1.4 else if (i < 38) step * 0.45 else step * 0.35
            val delta = (rng.nextDouble() - 0.48) * step * 2.4 + drift * 0.35
            val open = price
            val close = max(instrument.tickStep * 10, open + delta)
            val upperWick = rng.nextDouble() * step * 1.35
            val lowerWick = rng.nextDouble() * step * 1.35
            val high = max(open, close) + upperWick
            val low = min(open, close) - lowerWick
            rawCandles.add(
                Candle(
                    timeEpochSec = time,
                    open = roundToDecimals(open, instrument.decimals),
                    high = roundToDecimals(high, instrument.decimals),
                    low = roundToDecimals(low, instrument.decimals),
                    close = roundToDecimals(close, instrument.decimals),
                )
            )
            price = close
        }

        val targetClose = anchorClose ?: instrument.basePrice
        val offset = targetClose - rawCandles.last().close
        val shifted = rawCandles.map { c ->
            Candle(
                timeEpochSec = c.timeEpochSec,
                open = roundToDecimals(c.open + offset, instrument.decimals),
                high = roundToDecimals(c.high + offset, instrument.decimals),
                low = roundToDecimals(c.low + offset, instrument.decimals),
                close = roundToDecimals(c.close + offset, instrument.decimals),
            )
        }

        val lastClose = shifted.last().close
        val halfSpread = instrument.spread / 2.0
        val bid = roundToDecimals(lastClose - halfSpread, instrument.decimals)
        val ask = roundToDecimals(lastClose + halfSpread, instrument.decimals)
        val firstOpen = shifted.first().open
        val changePct = if (firstOpen > 0) ((lastClose - firstOpen) / firstOpen) * 100.0 else 0.0
        val secondsRemaining = (tfSec - (nowEpochSec % tfSec)).toInt().coerceAtLeast(1)

        return LiveQuote(
            symbol = instrument.symbol,
            bid = bid,
            ask = ask,
            changePercent = changePct,
            secondsRemainingInCandle = secondsRemaining,
            candles = shifted,
        )
    }

    fun tickQuote(
        current: LiveQuote,
        timeframe: String,
        nowEpochSec: Long,
        rng: Random = Random.Default,
    ): LiveQuote {
        val instrument = getInstrument(current.symbol)
        val tfSec = timeframeSeconds(timeframe)
        val currentBucket = (nowEpochSec / tfSec) * tfSec
        val secondsRemaining = (tfSec - (nowEpochSec % tfSec)).toInt().coerceAtLeast(1)

        val candles = current.candles.toMutableList()
        val last = candles.lastOrNull() ?: return buildQuoteForInstrument(instrument, timeframe, nowEpochSec)

        val move = (rng.nextDouble() - 0.495) * instrument.tickStep * 1.9
        val nextClose = roundToDecimals(
            max(instrument.tickStep * 10, last.close + move),
            instrument.decimals
        )

        if (currentBucket > last.timeEpochSec) {
            candles.add(
                Candle(
                    timeEpochSec = currentBucket,
                    open = last.close,
                    high = max(last.close, nextClose),
                    low = min(last.close, nextClose),
                    close = nextClose,
                )
            )
            if (candles.size > 90) {
                candles.removeAt(0)
            }
        } else {
            candles[candles.lastIndex] = last.copy(
                high = max(last.high, nextClose),
                low = min(last.low, nextClose),
                close = nextClose,
            )
        }

        val halfSpread = instrument.spread / 2.0
        val bid = roundToDecimals(nextClose - halfSpread, instrument.decimals)
        val ask = roundToDecimals(nextClose + halfSpread, instrument.decimals)
        val firstOpen = candles.first().open
        val changePct = if (firstOpen > 0) ((nextClose - firstOpen) / firstOpen) * 100.0 else 0.0

        return LiveQuote(
            symbol = instrument.symbol,
            bid = bid,
            ask = ask,
            changePercent = changePct,
            secondsRemainingInCandle = secondsRemaining,
            candles = candles,
        )
    }

    fun calculateFloatingPnl(trade: TradeRecord, quote: LiveQuote?): Double {
        if (trade.status != "OPEN" || quote == null) return trade.realizedPnl
        val instrument = getInstrument(trade.symbol)
        val priceDiff = if (trade.side == "BUY") {
            quote.bid - trade.entryPrice
        } else {
            trade.entryPrice - quote.ask
        }
        return priceDiff * trade.lots * instrument.contractSize
    }

    fun resolveQuoteToDepositRate(
        quoteCurrency: String,
        depositCurrency: String = "USD",
        quotes: Map<String, LiveQuote> = emptyMap(),
        symbolHint: String = "",
        referencePrice: Double = 0.0,
        chainRateOverride: Double? = null,
    ): Double {
        if (chainRateOverride != null && chainRateOverride > 0.0) {
            return chainRateOverride
        }
        val q = quoteCurrency.uppercase().ifBlank { "USD" }
        val d = depositCurrency.uppercase().ifBlank { "USD" }
        if (q == d) return 1.0

        directPairRate(q, d, quotes, symbolHint, referencePrice)?.let { return it }

        if (q != "USD" && d != "USD") {
            val qToUsd = directPairRate(q, "USD", quotes, symbolHint, referencePrice) ?: fallbackToUsdRate(q)
            val usdToD = directPairRate("USD", d, quotes, symbolHint, referencePrice) ?: (1.0 / fallbackToUsdRate(d))
            return (qToUsd * usdToD).coerceAtLeast(1e-6)
        }

        return if (d == "USD") {
            fallbackToUsdRate(q)
        } else {
            1.0 / fallbackToUsdRate(d)
        }
    }

    private fun directPairRate(
        from: String,
        to: String,
        quotes: Map<String, LiveQuote>,
        symbolHint: String,
        referencePrice: Double,
    ): Double? {
        val directSymbol = "$from$to"
        val directMid = quotes[directSymbol]?.let { (it.bid + it.ask) / 2.0 }
            ?: if (symbolHint.equals(directSymbol, ignoreCase = true) && referencePrice > 0.0) referencePrice else null
        if (directMid != null && directMid > 0.0) return directMid

        val inverseSymbol = "$to$from"
        val inverseMid = quotes[inverseSymbol]?.let { (it.bid + it.ask) / 2.0 }
            ?: if (symbolHint.equals(inverseSymbol, ignoreCase = true) && referencePrice > 0.0) referencePrice else null
        if (inverseMid != null && inverseMid > 0.0) return 1.0 / inverseMid

        return null
    }

    private fun fallbackToUsdRate(currency: String): Double = when (currency.uppercase()) {
        "USD" -> 1.0
        "EUR" -> 1.084
        "GBP" -> 1.267
        "AUD" -> 0.66
        "NZD" -> 0.60
        "CAD" -> 1.0 / 1.38
        "CHF" -> 1.0 / 0.88
        "JPY" -> 1.0 / 154.0
        else -> 1.0
    }

    fun estimateUsdValuePerUnit(symbol: String, referencePrice: Double): Double {
        val inst = getInstrument(symbol)
        val rate = resolveQuoteToDepositRate(
            quoteCurrency = inst.quoteCurrency,
            depositCurrency = "USD",
            symbolHint = inst.symbol,
            referencePrice = referencePrice,
        )
        return inst.contractSize * rate
    }

    fun calculateRiskSizedLots(
        accountCapital: Double,
        riskPercent: Double,
        entryPrice: Double,
        stopLossPrice: Double,
        instrument: MarketInstrument,
        quoteToDepositRate: Double = 1.0,
        fallbackLots: Double = instrument.minLots,
    ): RiskPositionPlan {
        val clampedRiskPct = riskPercent.coerceIn(0.05, 100.0)
        val safeCapital = accountCapital.coerceAtLeast(0.0)
        val riskAmount = safeCapital * (clampedRiskPct / 100.0)
        val stopDistance = abs(entryPrice - stopLossPrice)

        val lotSizeCents = instrument.lotSizeInCents.coerceAtLeast(100L)
        val minVolCents = instrument.minVolumeInCents.coerceAtLeast(1L)
        val maxVolCents = instrument.maxVolumeInCents.coerceAtLeast(minVolCents)
        val stepVolCents = instrument.stepVolumeInCents.coerceAtLeast(1L)

        val pipSize = instrument.pipSize
        val stopDistancePips = if (pipSize > 0.0) stopDistance / pipSize else 0.0
        val safeRate = quoteToDepositRate.takeIf { it > 0.0 } ?: 1.0
        val lossPerLot = stopDistance * instrument.contractSize * safeRate
        val pipValuePerLot = pipSize * instrument.contractSize * safeRate

        val alignedVolCents: Long
        val isBelowMin: Boolean

        if (safeCapital <= 0.0 || lossPerLot <= 0.0) {
            val rawFallback = floor(fallbackLots * lotSizeCents.toDouble() + 1e-3).toLong()
            isBelowMin = rawFallback < minVolCents
            alignedVolCents = alignVolumeDown(rawFallback, minVolCents, maxVolCents, stepVolCents)
        } else {
            val rawLots = riskAmount / lossPerLot
            val rawVolCents = floor(rawLots * lotSizeCents.toDouble() + 1e-3).toLong()
            isBelowMin = rawVolCents < minVolCents
            alignedVolCents = alignVolumeDown(rawVolCents, minVolCents, maxVolCents, stepVolCents)
        }

        val calculatedLots = alignedVolCents.toDouble() / lotSizeCents.toDouble()
        val actualRiskAmount = if (lossPerLot > 0.0) calculatedLots * lossPerLot else riskAmount

        return RiskPositionPlan(
            riskCapital = safeCapital,
            riskPercent = clampedRiskPct,
            riskAmount = riskAmount,
            actualRiskAmount = actualRiskAmount,
            stopDistance = stopDistance,
            stopDistancePips = stopDistancePips,
            calculatedLots = calculatedLots,
            lossPerLot = lossPerLot,
            pipValuePerLot = pipValuePerLot,
            alignedVolumeInCents = alignedVolCents,
            isBelowMinVolume = isBelowMin,
            stepLots = stepVolCents.toDouble() / lotSizeCents.toDouble(),
            minLots = minVolCents.toDouble() / lotSizeCents.toDouble(),
            maxLots = maxVolCents.toDouble() / lotSizeCents.toDouble(),
        )
    }

    fun calculateRiskSizedLots(
        accountCapital: Double,
        riskPercent: Double,
        entryPrice: Double,
        stopLossPrice: Double,
        symbol: String,
        fallbackLots: Double = 0.01,
        lotStep: Double? = null,
        minLots: Double? = null,
        maxLots: Double? = null,
    ): RiskPositionPlan {
        val base = getInstrument(symbol)
        val lotSizeCents = base.lotSizeInCents.coerceAtLeast(100L)
        val effectiveInstrument = if (lotStep != null || minLots != null || maxLots != null) {
            val stepC = lotStep?.let { kotlin.math.round(it * lotSizeCents).toLong().coerceAtLeast(1L) }
                ?: base.stepVolumeInCents
            val minC = minLots?.let { kotlin.math.round(it * lotSizeCents).toLong().coerceAtLeast(1L) }
                ?: base.minVolumeInCents
            val maxC = maxLots?.let { kotlin.math.round(it * lotSizeCents).toLong().coerceAtLeast(minC) }
                ?: base.maxVolumeInCents
            base.copy(
                stepVolumeInCents = stepC,
                minVolumeInCents = minC,
                maxVolumeInCents = maxC,
            )
        } else {
            base
        }
        val convRate = resolveQuoteToDepositRate(
            quoteCurrency = effectiveInstrument.quoteCurrency,
            depositCurrency = "USD",
            symbolHint = effectiveInstrument.symbol,
            referencePrice = entryPrice,
        )
        return calculateRiskSizedLots(
            accountCapital = accountCapital,
            riskPercent = riskPercent,
            entryPrice = entryPrice,
            stopLossPrice = stopLossPrice,
            instrument = effectiveInstrument,
            quoteToDepositRate = convRate,
            fallbackLots = fallbackLots,
        )
    }

    private fun alignVolumeDown(
        rawVolumeInCents: Long,
        minVolumeInCents: Long,
        maxVolumeInCents: Long,
        stepVolumeInCents: Long,
    ): Long {
        if (rawVolumeInCents <= minVolumeInCents) return minVolumeInCents
        val clamped = rawVolumeInCents.coerceAtMost(maxVolumeInCents)
        val steps = (clamped - minVolumeInCents) / stepVolumeInCents
        return (minVolumeInCents + steps * stepVolumeInCents).coerceIn(minVolumeInCents, maxVolumeInCents)
    }

    fun calculateRecommendedLots(
        equity: Double,
        riskPercent: Double,
        symbol: String,
    ): Double {
        if (equity <= 0.0) return 0.1
        val instrument = getInstrument(symbol)
        val stopDistance = instrument.tickStep * 25.0
        return calculateRiskSizedLots(
            accountCapital = equity,
            riskPercent = riskPercent,
            entryPrice = instrument.basePrice,
            stopLossPrice = instrument.basePrice - stopDistance,
            instrument = instrument,
            quoteToDepositRate = resolveQuoteToDepositRate(
                quoteCurrency = instrument.quoteCurrency,
                depositCurrency = "USD",
                symbolHint = instrument.symbol,
                referencePrice = instrument.basePrice,
            ),
            fallbackLots = 0.1,
        ).calculatedLots
    }

    fun rrTargetPrice(
        side: String,
        entryPrice: Double,
        stopLoss: Double,
        rrMultiple: Double,
    ): Double? {
        if (entryPrice <= 0.0 || stopLoss <= 0.0 || rrMultiple <= 0.0) return null
        val slDist = abs(entryPrice - stopLoss)
        if (slDist <= 0.0) return null
        return when {
            side.equals("BUY", ignoreCase = true) && stopLoss < entryPrice ->
                entryPrice + slDist * rrMultiple
            side.equals("SELL", ignoreCase = true) && stopLoss > entryPrice -> {
                val target = entryPrice - slDist * rrMultiple
                if (target > 0.0) target else null
            }
            else -> null
        }
    }

    fun oneToOneTargetPrice(
        side: String,
        entryPrice: Double,
        stopLoss: Double,
    ): Double? = rrTargetPrice(side, entryPrice, stopLoss, 1.0)

    fun formatRrMultiple(rr: Double): String {
        val rounded = kotlin.math.round(rr * 10.0) / 10.0
        return if (abs(rounded - rounded.toInt().toDouble()) < 0.05) {
            "${rounded.toInt()}"
        } else {
            String.format(Locale.US, "%.1f", rounded)
        }
    }

    fun tierKey(rrMultiple: Double): String {
        val snapped = (kotlin.math.round(rrMultiple * 10.0) / 10.0).coerceIn(0.5, 20.0)
        return String.format(Locale.US, "%.1f", snapped)
    }

    fun parsePartialTpTiers(spec: String): List<PartialTpTier> {
        val parsed = spec.split(',')
            .mapNotNull { token ->
                val parts = token.trim().split(':')
                if (parts.size != 2) return@mapNotNull null
                val rr = parts[0].toDoubleOrNull()?.coerceIn(0.5, 20.0) ?: return@mapNotNull null
                val pct = parts[1].toDoubleOrNull()?.coerceIn(5.0, 100.0) ?: return@mapNotNull null
                PartialTpTier(
                    rrMultiple = (kotlin.math.round(rr * 10.0) / 10.0),
                    closePercent = kotlin.math.round(pct),
                )
            }
            .sortedBy { it.rrMultiple }
            .distinctBy { tierKey(it.rrMultiple) }
            .take(4)

        val fallback = if (parsed.isEmpty()) listOf(PartialTpTier(1.0, 50.0)) else parsed
        var remainingPct = 100.0
        return buildList {
            for (tier in fallback) {
                if (remainingPct <= 0.0) break
                val effectivePct = tier.closePercent.coerceAtMost(remainingPct)
                if (effectivePct >= 5.0) {
                    add(tier.copy(closePercent = effectivePct))
                    remainingPct -= effectivePct
                }
            }
        }
    }

    fun encodePartialTpTiers(tiers: List<PartialTpTier>): String {
        if (tiers.isEmpty()) return "1.0:50"
        var remainingPct = 100.0
        return tiers
            .sortedBy { it.rrMultiple }
            .distinctBy { tierKey(it.rrMultiple) }
            .take(4)
            .mapNotNull { t ->
                if (remainingPct <= 0.0) return@mapNotNull null
                val rr = (kotlin.math.round(t.rrMultiple.coerceIn(0.5, 20.0) * 10.0) / 10.0)
                val pct = kotlin.math.round(t.closePercent).coerceIn(5.0, remainingPct)
                remainingPct -= pct
                "${String.format(Locale.US, "%.1f", rr)}:${pct.toInt()}"
            }
            .joinToString(",")
            .ifBlank { "1.0:50" }
    }

    fun evaluatePartialTpTiers(
        trade: TradeRecord,
        quote: LiveQuote?,
        instrument: MarketInstrument,
        tiers: List<PartialTpTier>,
        quoteToDepositRate: Double = 1.0,
    ): PartialClosePlan? {
        if (trade.status != "OPEN" || quote == null || trade.stopLoss <= 0.0 || tiers.isEmpty()) {
            return null
        }

        val parsedExecuted = trade.executedPartialTiers
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableSet()
        // Backward compatibility with legacy v3 single-tier flag
        if (trade.partialCloseExecuted && parsedExecuted.isEmpty()) {
            parsedExecuted.add("1.0")
        }

        val lotCents = instrument.lotSizeInCents.coerceAtLeast(100L)
        val minCents = instrument.minVolumeInCents.coerceAtLeast(1L)
        val stepCents = instrument.stepVolumeInCents.coerceAtLeast(1L)
        val currentCents = kotlin.math.round(trade.lots * lotCents.toDouble()).toLong()
        if (currentCents < minCents) return null

        val baseInitialLots = if (trade.initialLots > 0.0) max(trade.initialLots, trade.lots) else trade.lots
        val initialCents = kotlin.math.round(baseInitialLots * lotCents.toDouble()).toLong()
            .coerceAtLeast(currentCents)

        val sortedTiers = tiers.sortedBy { it.rrMultiple }
        for (tier in sortedTiers) {
            val key = tierKey(tier.rrMultiple)
            if (parsedExecuted.contains(key)) continue

            val targetPrice = rrTargetPrice(
                side = trade.side,
                entryPrice = trade.entryPrice,
                stopLoss = trade.stopLoss,
                rrMultiple = tier.rrMultiple,
            ) ?: continue

            val hitTarget = if (trade.side.equals("BUY", ignoreCase = true)) {
                quote.bid >= targetPrice - 1e-9
            } else {
                quote.ask <= targetPrice + 1e-9
            }
            if (!hitTarget) continue

            val desiredRawCents = floor(initialCents * (tier.closePercent / 100.0) + 1e-3).toLong()
            val closeCents: Long
            val remainingCents: Long
            val isFullClose: Boolean

            if (desiredRawCents >= currentCents) {
                closeCents = currentCents
                remainingCents = 0L
                isFullClose = true
            } else {
                if (currentCents < minCents * 2L) return null
                closeCents = alignVolumeDown(
                    rawVolumeInCents = desiredRawCents,
                    minVolumeInCents = minCents,
                    maxVolumeInCents = currentCents - minCents,
                    stepVolumeInCents = stepCents,
                )
                remainingCents = currentCents - closeCents
                isFullClose = remainingCents <= 0L
                if (closeCents < minCents || (!isFullClose && remainingCents < minCents)) return null
            }

            val closeLots = closeCents.toDouble() / lotCents.toDouble()
            val remainingLots = remainingCents.toDouble() / lotCents.toDouble()
            val safeRate = quoteToDepositRate.takeIf { it > 0.0 } ?: 1.0
            val priceDiff = if (trade.side.equals("BUY", ignoreCase = true)) {
                quote.bid - trade.entryPrice
            } else {
                trade.entryPrice - quote.ask
            }
            val pnlEstimate = priceDiff * closeLots * instrument.contractSize * safeRate

            return PartialClosePlan(
                triggerPrice = targetPrice,
                closeLots = closeLots,
                remainingLots = remainingLots,
                realizedPnlEstimate = pnlEstimate,
                rrMultiple = tier.rrMultiple,
                closePercent = tier.closePercent,
                tierKey = key,
                isFullClose = isFullClose,
            )
        }
        return null
    }

    fun evaluateOneToOnePartialClose(
        trade: TradeRecord,
        quote: LiveQuote?,
        instrument: MarketInstrument,
        quoteToDepositRate: Double = 1.0,
    ): PartialClosePlan? = evaluatePartialTpTiers(
        trade = trade,
        quote = quote,
        instrument = instrument,
        tiers = listOf(PartialTpTier(rrMultiple = 1.0, closePercent = 50.0)),
        quoteToDepositRate = quoteToDepositRate,
    )

    private fun roundToDecimals(value: Double, decimals: Int): Double {
        val factor = 10.0.pow(decimals.coerceIn(0, 8))
        return (value * factor).roundToInt() / factor
    }
}

