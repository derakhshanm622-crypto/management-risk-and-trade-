package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

data class PartialTpTier(
    val rrMultiple: Double,
    val closePercent: Double,
)

@Entity(tableName = "user_settings")
data class UserSettings(
    @PrimaryKey val id: String = "preferences",
    val demoModeEnabled: Boolean = false,
    val defaultRiskPercent: Double = 1.0,
    val riskBasis: String = "balance", // "balance" or "equity"
    val biometricAuthEnabled: Boolean = true,
    val tradeConfirmationAlwaysOn: Boolean = true,
    val activeAccountId: String = "",
    val selectedSymbol: String = "XAUUSD",
    val selectedTimeframe: String = "m1",
    val ctraderEnv: String = "demo", // "demo" or "live"
    val ctraderClientId: String = "",
    val ctraderClientSecret: String = "",
    val ctraderAccessToken: String = "",
    val ctraderRefreshToken: String = "",
    val ctraderTokenExpiresAtMillis: Long = 0L,
    val autoPartialCloseAtOneToOne: Boolean = false,
    val partialTpTiersSpec: String = "1.0:50", // Comma-separated rr:percent e.g. "1.0:50,2.0:25"
    val showFibonacciRrOnChart: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

@Entity(tableName = "trading_accounts")
data class TradingAccount(
    @PrimaryKey val id: String = "",
    val name: String = "",
    val broker: String = "",
    val accountType: String = "journal", // "ctrader_demo", "ctrader_live", "demo", "journal", "broker_ref"
    val currency: String = "USD",
    val initialBalance: Double = 0.0,
    val balance: Double = 0.0,
    val apiKeyReference: String = "",
    val ctidTraderAccountId: Long = 0L,
    val isLive: Boolean = false,
    val isActive: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

@Entity(tableName = "trade_records")
data class TradeRecord(
    @PrimaryKey val id: String = "",
    val accountId: String = "",
    val accountName: String = "",
    val symbol: String = "XAUUSD",
    val side: String = "BUY", // "BUY" or "SELL"
    val lots: Double = 0.1,
    val initialLots: Double = lots,
    val entryPrice: Double = 0.0,
    val exitPrice: Double = 0.0,
    val stopLoss: Double = 0.0,
    val takeProfit: Double = 0.0,
    val realizedPnl: Double = 0.0,
    val status: String = "OPEN", // "OPEN" or "CLOSED"
    val notes: String = "",
    val isDemo: Boolean = false,
    val ctraderPositionId: Long = 0L,
    val symbolId: Long = 0L,
    val partialCloseExecuted: Boolean = false,
    val executedPartialTiers: String = "",
    val openedAtMillis: Long = System.currentTimeMillis(),
    val closedAtMillis: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

data class Candle(
    val timeEpochSec: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
)

data class MarketInstrument(
    val symbol: String,
    val displayName: String,
    val category: String,
    val basePrice: Double,
    val spread: Double,
    val decimals: Int,
    val contractSize: Double,
    val tickStep: Double,
    val pipPosition: Int = (decimals - 1).coerceAtLeast(0),
    val lotSizeInCents: Long = kotlin.math.round(contractSize * 100.0).toLong().coerceAtLeast(100L),
    val minVolumeInCents: Long = (kotlin.math.round(contractSize * 100.0).toLong() / 100L).coerceAtLeast(1L),
    val maxVolumeInCents: Long = (kotlin.math.round(contractSize * 100.0).toLong() * 1000L).coerceAtLeast(100L),
    val stepVolumeInCents: Long = (kotlin.math.round(contractSize * 100.0).toLong() / 100L).coerceAtLeast(1L),
    val quoteCurrency: String = "USD",
) {
    val pipSize: Double
        get() = 1.0 / Math.pow(10.0, pipPosition.toDouble())
    val minLots: Double
        get() = minVolumeInCents.toDouble() / lotSizeInCents.toDouble()
    val maxLots: Double
        get() = maxVolumeInCents.toDouble() / lotSizeInCents.toDouble()
    val stepLots: Double
        get() = stepVolumeInCents.toDouble() / lotSizeInCents.toDouble()
}

data class LiveQuote(
    val symbol: String,
    val bid: Double,
    val ask: Double,
    val changePercent: Double,
    val secondsRemainingInCandle: Int,
    val candles: List<Candle>,
)
