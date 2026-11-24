package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.TradeRecord
import com.example.data.model.TradingAccount
import com.example.data.model.UserSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeHubDao {

    // Settings
    @Query("SELECT * FROM user_settings WHERE id = :id LIMIT 1")
    fun observeSettings(id: String = "preferences"): Flow<UserSettings?>

    @Query("SELECT * FROM user_settings WHERE id = :id LIMIT 1")
    suspend fun getSettings(id: String = "preferences"): UserSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: UserSettings)

    // Accounts
    @Query("SELECT * FROM trading_accounts ORDER BY createdAtMillis DESC")
    fun observeAccounts(): Flow<List<TradingAccount>>

    @Query("SELECT * FROM trading_accounts WHERE id = :accountId LIMIT 1")
    suspend fun getAccountById(accountId: String): TradingAccount?

    @Query("SELECT * FROM trading_accounts")
    suspend fun getAllAccounts(): List<TradingAccount>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(account: TradingAccount)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccounts(accounts: List<TradingAccount>)

    @Update
    suspend fun updateAccounts(accounts: List<TradingAccount>)

    @Query("DELETE FROM trading_accounts WHERE id = :accountId")
    suspend fun deleteAccountById(accountId: String)

    @Query("DELETE FROM trading_accounts WHERE ctidTraderAccountId > 0")
    suspend fun deleteCTraderAccounts()

    @Query("UPDATE trading_accounts SET balance = :newBalance, updatedAtMillis = :updatedAt WHERE id = :accountId")
    suspend fun updateAccountBalance(accountId: String, newBalance: Double, updatedAt: Long)

    // Trades
    @Query("SELECT * FROM trade_records ORDER BY openedAtMillis DESC")
    fun observeTrades(): Flow<List<TradeRecord>>

    @Query("SELECT * FROM trade_records WHERE id = :tradeId LIMIT 1")
    suspend fun getTradeById(tradeId: String): TradeRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrade(trade: TradeRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrades(trades: List<TradeRecord>)

    @Query("DELETE FROM trade_records WHERE accountId = :accountId AND ctraderPositionId > 0 AND status = 'OPEN'")
    suspend fun deleteOpenCTraderTradesForAccount(accountId: String)

    @Query("DELETE FROM trade_records WHERE ctraderPositionId > 0")
    suspend fun deleteAllCTraderTrades()

    @Query(
        "UPDATE trade_records SET stopLoss = :stopLoss, takeProfit = :takeProfit, notes = :notes, updatedAtMillis = :updatedAt WHERE id = :tradeId"
    )
    suspend fun updateOpenTradeLevels(
        tradeId: String,
        stopLoss: Double,
        takeProfit: Double,
        notes: String,
        updatedAt: Long,
    )

    @Query("SELECT * FROM trade_records WHERE accountId = :accountId AND status = 'OPEN'")
    suspend fun getOpenTradesForAccount(accountId: String): List<TradeRecord>

    @Query(
        "UPDATE trade_records SET status = 'CLOSED', exitPrice = :exitPrice, realizedPnl = :realizedPnl, closedAtMillis = :closedAt, updatedAtMillis = :closedAt WHERE id = :tradeId"
    )
    suspend fun markTradeClosed(
        tradeId: String,
        exitPrice: Double,
        realizedPnl: Double,
        closedAt: Long,
    )

    @Query(
        "UPDATE trade_records SET lots = :remainingLots, partialCloseExecuted = 1, executedPartialTiers = :executedTiers, notes = :updatedNotes, updatedAtMillis = :updatedAt WHERE id = :tradeId"
    )
    suspend fun reduceOpenTradeLots(
        tradeId: String,
        remainingLots: Double,
        executedTiers: String,
        updatedNotes: String,
        updatedAt: Long,
    )

    @Query(
        "UPDATE trade_records SET partialCloseExecuted = 1, executedPartialTiers = :executedTiers, updatedAtMillis = :updatedAt WHERE id = :tradeId"
    )
    suspend fun markTradePartialCloseExecuted(
        tradeId: String,
        executedTiers: String,
        updatedAt: Long,
    )

    @Query("DELETE FROM trade_records WHERE id = :tradeId")
    suspend fun deleteTradeById(tradeId: String)

    @Transaction
    suspend fun setActiveAccountTransaction(accountId: String, now: Long) {
        val all = getAllAccounts()
        val updated = all.map { account ->
            val shouldBeActive = account.id == accountId
            if (account.isActive != shouldBeActive) {
                account.copy(isActive = shouldBeActive, updatedAtMillis = now)
            } else {
                account
            }
        }
        updateAccounts(updated)
        val settings = getSettings() ?: UserSettings(createdAtMillis = now, updatedAtMillis = now)
        upsertSettings(settings.copy(activeAccountId = accountId, updatedAtMillis = now))
    }

    @Transaction
    suspend fun syncCTraderOpenPositionsTransaction(
        accountId: String,
        openPositions: List<TradeRecord>,
    ) {
        val existingById = getOpenTradesForAccount(accountId).associateBy { it.id }
        deleteOpenCTraderTradesForAccount(accountId)
        if (openPositions.isNotEmpty()) {
            val merged = openPositions.map { pos ->
                val old = existingById[pos.id]
                if (old != null) {
                    val preservedInitial = if (old.initialLots > 0.0) {
                        maxOf(old.initialLots, pos.lots)
                    } else {
                        pos.lots
                    }
                    pos.copy(
                        initialLots = preservedInitial,
                        partialCloseExecuted = old.partialCloseExecuted,
                        executedPartialTiers = old.executedPartialTiers,
                    )
                } else {
                    pos.copy(initialLots = pos.lots)
                }
            }
            upsertTrades(merged)
        }
    }

    @Transaction
    suspend fun logClosedJournalTradeTransaction(
        trade: TradeRecord,
        accountId: String,
        newBalance: Double,
        now: Long,
    ) {
        upsertTrade(trade)
        updateAccountBalance(accountId, newBalance, now)
    }

    @Transaction
    suspend fun closeTradeTransaction(
        tradeId: String,
        exitPrice: Double,
        realizedPnl: Double,
        accountId: String?,
        newBalance: Double?,
        now: Long,
    ) {
        markTradeClosed(tradeId, exitPrice, realizedPnl, now)
        if (!accountId.isNullOrBlank() && newBalance != null) {
            updateAccountBalance(accountId, newBalance, now)
        }
    }

    @Transaction
    suspend fun partialCloseTradeTransaction(
        openTradeId: String,
        remainingLots: Double,
        executedTiers: String,
        updatedNotes: String,
        closedSliceTrade: TradeRecord,
        accountId: String?,
        newBalance: Double?,
        now: Long,
    ) {
        reduceOpenTradeLots(openTradeId, remainingLots, executedTiers, updatedNotes, now)
        upsertTrade(closedSliceTrade)
        if (!accountId.isNullOrBlank() && newBalance != null) {
            updateAccountBalance(accountId, newBalance, now)
        }
    }
}
