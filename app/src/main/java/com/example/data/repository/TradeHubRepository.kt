package com.example.data.repository

import android.content.Context
import com.example.data.local.TradeHubDao
import com.example.data.local.TradeHubDatabase
import com.example.data.model.TradeRecord
import com.example.data.model.TradingAccount
import com.example.data.model.UserSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class TradeHubRepository(
    private val dao: TradeHubDao,
) {
    constructor(context: Context) : this(
        TradeHubDatabase.getInstance(context).tradeHubDao()
    )

    fun observeSettings(): Flow<UserSettings?> = dao.observeSettings(PREFERENCES_ID)

    fun observeAccounts(): Flow<List<TradingAccount>> = dao.observeAccounts()

    fun observeTrades(): Flow<List<TradeRecord>> = dao.observeTrades()

    suspend fun ensureDefaultSettings(): Result<UserSettings> {
        return try {
            val existing = dao.getSettings(PREFERENCES_ID)
            if (existing != null) {
                Result.success(existing)
            } else {
                val now = System.currentTimeMillis()
                val defaults = UserSettings(
                    id = PREFERENCES_ID,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                )
                dao.upsertSettings(defaults)
                Result.success(defaults)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateSettings(
        demoModeEnabled: Boolean? = null,
        defaultRiskPercent: Double? = null,
        riskBasis: String? = null,
        biometricAuthEnabled: Boolean? = null,
        tradeConfirmationAlwaysOn: Boolean? = null,
        activeAccountId: String? = null,
        selectedSymbol: String? = null,
        selectedTimeframe: String? = null,
        ctraderEnv: String? = null,
        ctraderClientId: String? = null,
        ctraderClientSecret: String? = null,
        ctraderAccessToken: String? = null,
        ctraderRefreshToken: String? = null,
        ctraderTokenExpiresAtMillis: Long? = null,
        autoPartialCloseAtOneToOne: Boolean? = null,
        partialTpTiersSpec: String? = null,
        showFibonacciRrOnChart: Boolean? = null,
    ): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val current = dao.getSettings(PREFERENCES_ID)
                ?: UserSettings(id = PREFERENCES_ID, createdAtMillis = now, updatedAtMillis = now)
            val updated = current.copy(
                demoModeEnabled = demoModeEnabled ?: current.demoModeEnabled,
                defaultRiskPercent = defaultRiskPercent?.coerceIn(0.1, 100.0)
                    ?: current.defaultRiskPercent,
                riskBasis = riskBasis ?: current.riskBasis,
                biometricAuthEnabled = biometricAuthEnabled ?: current.biometricAuthEnabled,
                tradeConfirmationAlwaysOn = tradeConfirmationAlwaysOn
                    ?: current.tradeConfirmationAlwaysOn,
                activeAccountId = activeAccountId ?: current.activeAccountId,
                selectedSymbol = selectedSymbol ?: current.selectedSymbol,
                selectedTimeframe = selectedTimeframe ?: current.selectedTimeframe,
                ctraderEnv = ctraderEnv ?: current.ctraderEnv,
                ctraderClientId = ctraderClientId ?: current.ctraderClientId,
                ctraderClientSecret = ctraderClientSecret ?: current.ctraderClientSecret,
                ctraderAccessToken = ctraderAccessToken ?: current.ctraderAccessToken,
                ctraderRefreshToken = ctraderRefreshToken ?: current.ctraderRefreshToken,
                ctraderTokenExpiresAtMillis = ctraderTokenExpiresAtMillis
                    ?: current.ctraderTokenExpiresAtMillis,
                autoPartialCloseAtOneToOne = autoPartialCloseAtOneToOne
                    ?: current.autoPartialCloseAtOneToOne,
                partialTpTiersSpec = partialTpTiersSpec ?: current.partialTpTiersSpec,
                showFibonacciRrOnChart = showFibonacciRrOnChart ?: current.showFibonacciRrOnChart,
                updatedAtMillis = now,
            )
            dao.upsertSettings(updated)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearCTraderSession(): Result<Unit> {
        return try {
            dao.deleteCTraderAccounts()
            dao.deleteAllCTraderTrades()
            updateSettings(
                ctraderAccessToken = "",
                ctraderRefreshToken = "",
                ctraderTokenExpiresAtMillis = 0L,
                activeAccountId = "",
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncCTraderAccounts(accounts: List<TradingAccount>, activeId: String): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val existingById = dao.getAllAccounts().associateBy { it.id }
            val merged = accounts.map { remote ->
                val old = existingById[remote.id]
                remote.copy(
                    balance = if (remote.balance == 0.0 && old != null) old.balance else remote.balance,
                    initialBalance = if (remote.initialBalance == 0.0 && old != null) old.initialBalance else remote.initialBalance,
                    isActive = remote.id == activeId,
                    createdAtMillis = old?.createdAtMillis ?: now,
                    updatedAtMillis = now,
                )
            }
            dao.deleteCTraderAccounts()
            if (merged.isNotEmpty()) {
                dao.upsertAccounts(merged)
                dao.setActiveAccountTransaction(activeId, now)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateCTraderAccountBalance(
        accountId: String,
        balance: Double,
        currency: String? = null,
    ): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val existing = dao.getAccountById(accountId)
            if (existing != null) {
                dao.upsertAccount(
                    existing.copy(
                        balance = balance,
                        initialBalance = if (existing.initialBalance <= 0.0) balance else existing.initialBalance,
                        currency = currency ?: existing.currency,
                        updatedAtMillis = now,
                    )
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncCTraderOpenPositions(
        accountId: String,
        openPositions: List<TradeRecord>,
    ): Result<Unit> {
        return try {
            dao.syncCTraderOpenPositionsTransaction(accountId, openPositions)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun ensureDemoAccount(): Result<String> {
        return try {
            val existing = dao.getAccountById(DEMO_ACCOUNT_ID)
            if (existing == null) {
                val now = System.currentTimeMillis()
                dao.upsertAccount(
                    TradingAccount(
                        id = DEMO_ACCOUNT_ID,
                        name = "Demo Practice",
                        broker = "Local Demo",
                        accountType = "demo",
                        currency = "USD",
                        initialBalance = 10000.0,
                        balance = 10000.0,
                        apiKeyReference = "",
                        isActive = true,
                        createdAtMillis = now,
                        updatedAtMillis = now,
                    )
                )
            }
            Result.success(DEMO_ACCOUNT_ID)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetDemoBalance(): Result<Unit> {
        return try {
            val existing = dao.getAccountById(DEMO_ACCOUNT_ID)
            if (existing == null) {
                ensureDemoAccount().getOrThrow()
            } else {
                dao.updateAccountBalance(
                    accountId = DEMO_ACCOUNT_ID,
                    newBalance = 10000.0,
                    updatedAt = System.currentTimeMillis(),
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createAccount(
        name: String,
        broker: String,
        accountType: String,
        currency: String,
        initialBalance: Double,
        apiKeyReference: String,
        setAsActive: Boolean = true,
    ): Result<String> {
        return try {
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val sanitizedBalance = initialBalance.coerceAtLeast(0.0)
            dao.upsertAccount(
                TradingAccount(
                    id = id,
                    name = name.trim().take(60),
                    broker = broker.trim().take(40),
                    accountType = accountType,
                    currency = currency.trim().uppercase().take(10),
                    initialBalance = sanitizedBalance,
                    balance = sanitizedBalance,
                    apiKeyReference = apiKeyReference.trim().take(80),
                    isActive = setAsActive,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                )
            )
            if (setAsActive) {
                dao.setActiveAccountTransaction(id, now)
            }
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setActiveAccount(accountId: String): Result<Unit> {
        return try {
            dao.setActiveAccountTransaction(accountId, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAccount(accountId: String): Result<Unit> {
        return try {
            dao.deleteAccountById(accountId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun openTrade(
        account: TradingAccount,
        symbol: String,
        side: String,
        lots: Double,
        entryPrice: Double,
        stopLoss: Double = 0.0,
        takeProfit: Double = 0.0,
        notes: String = "",
    ): Result<String> {
        return try {
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val clampedLots = lots.coerceIn(0.01, 1000.0)
            dao.upsertTrade(
                TradeRecord(
                    id = id,
                    accountId = account.id,
                    accountName = account.name.ifBlank { "Trading Account" }.take(60),
                    symbol = symbol.take(20),
                    side = side,
                    lots = clampedLots,
                    initialLots = clampedLots,
                    entryPrice = entryPrice,
                    exitPrice = 0.0,
                    stopLoss = stopLoss.coerceAtLeast(0.0),
                    takeProfit = takeProfit.coerceAtLeast(0.0),
                    realizedPnl = 0.0,
                    status = "OPEN",
                    notes = notes.take(300),
                    isDemo = account.accountType == "demo" || account.accountType == "ctrader_demo",
                    openedAtMillis = now,
                    closedAtMillis = null,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                )
            )
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logClosedJournalTrade(
        account: TradingAccount,
        symbol: String,
        side: String,
        lots: Double,
        entryPrice: Double,
        exitPrice: Double,
        realizedPnl: Double,
        notes: String = "",
    ): Result<String> {
        return try {
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val newBalance = (account.balance + realizedPnl).coerceAtLeast(0.0)
            dao.logClosedJournalTradeTransaction(
                trade = TradeRecord(
                    id = id,
                    accountId = account.id,
                    accountName = account.name.ifBlank { "Journal Account" }.take(60),
                    symbol = symbol.take(20),
                    side = side,
                    lots = lots.coerceIn(0.01, 1000.0),
                    entryPrice = entryPrice,
                    exitPrice = exitPrice,
                    stopLoss = 0.0,
                    takeProfit = 0.0,
                    realizedPnl = realizedPnl,
                    status = "CLOSED",
                    notes = notes.take(300),
                    isDemo = account.accountType == "demo" || account.accountType == "ctrader_demo",
                    openedAtMillis = now,
                    closedAtMillis = now,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                ),
                accountId = account.id,
                newBalance = newBalance,
                now = now,
            )
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateOpenTradeLevels(
        tradeId: String,
        stopLoss: Double,
        takeProfit: Double,
        notes: String,
    ): Result<Unit> {
        return try {
            dao.updateOpenTradeLevels(
                tradeId = tradeId,
                stopLoss = stopLoss.coerceAtLeast(0.0),
                takeProfit = takeProfit.coerceAtLeast(0.0),
                notes = notes.take(300),
                updatedAt = System.currentTimeMillis(),
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun closeTrade(
        trade: TradeRecord,
        exitPrice: Double,
        realizedPnl: Double,
        currentAccountBalance: Double?,
    ): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val newBalance = currentAccountBalance?.let { (it + realizedPnl).coerceAtLeast(0.0) }
            dao.closeTradeTransaction(
                tradeId = trade.id,
                exitPrice = exitPrice,
                realizedPnl = realizedPnl,
                accountId = trade.accountId,
                newBalance = newBalance,
                now = now,
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun partialCloseTrade(
        trade: TradeRecord,
        closedLots: Double,
        remainingLots: Double,
        exitPrice: Double,
        realizedPnl: Double,
        currentAccountBalance: Double?,
        tierKey: String = "1.0",
        tierLabel: String = "50% @ 1:1 RR",
    ): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val newBalance = currentAccountBalance?.let { (it + realizedPnl).coerceAtLeast(0.0) }
            val updatedExecutedTiers = (
                trade.executedPartialTiers.split(',').filter { it.isNotBlank() } + tierKey
                ).distinct().joinToString(",")
            val updatedNotes = listOf(trade.notes.trim(), tierLabel)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" • ")
                .take(300)
            val closedSlice = TradeRecord(
                id = UUID.randomUUID().toString(),
                accountId = trade.accountId,
                accountName = trade.accountName,
                symbol = trade.symbol,
                side = trade.side,
                lots = closedLots,
                initialLots = closedLots,
                entryPrice = trade.entryPrice,
                exitPrice = exitPrice,
                stopLoss = trade.stopLoss,
                takeProfit = exitPrice,
                realizedPnl = realizedPnl,
                status = "CLOSED",
                notes = tierLabel,
                isDemo = trade.isDemo,
                ctraderPositionId = 0L,
                symbolId = trade.symbolId,
                partialCloseExecuted = true,
                executedPartialTiers = tierKey,
                openedAtMillis = trade.openedAtMillis,
                closedAtMillis = now,
                createdAtMillis = now,
                updatedAtMillis = now,
            )
            dao.partialCloseTradeTransaction(
                openTradeId = trade.id,
                remainingLots = remainingLots,
                executedTiers = updatedExecutedTiers,
                updatedNotes = updatedNotes,
                closedSliceTrade = closedSlice,
                accountId = trade.accountId,
                newBalance = newBalance,
                now = now,
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markPartialCloseExecuted(
        tradeId: String,
        tierKey: String = "1.0",
        existingTiersCsv: String = "",
    ): Result<Unit> {
        return try {
            val merged = (existingTiersCsv.split(',').filter { it.isNotBlank() } + tierKey)
                .distinct()
                .joinToString(",")
            dao.markTradePartialCloseExecuted(tradeId, merged, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTrade(tradeId: String): Result<Unit> {
        return try {
            dao.deleteTradeById(tradeId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val PREFERENCES_ID = "preferences"
        const val DEMO_ACCOUNT_ID = "demo_practice"
    }
}
