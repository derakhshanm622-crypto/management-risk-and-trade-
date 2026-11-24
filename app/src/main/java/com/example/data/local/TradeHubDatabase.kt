package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.TradeRecord
import com.example.data.model.TradingAccount
import com.example.data.model.UserSettings

@Database(
    entities = [
        UserSettings::class,
        TradingAccount::class,
        TradeRecord::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class TradeHubDatabase : RoomDatabase() {
    abstract fun tradeHubDao(): TradeHubDao

    companion object {
        @Volatile
        private var INSTANCE: TradeHubDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE user_settings ADD COLUMN autoPartialCloseAtOneToOne INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE trade_records ADD COLUMN partialCloseExecuted INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE user_settings ADD COLUMN partialTpTiersSpec TEXT NOT NULL DEFAULT '1.0:50'"
                )
                db.execSQL(
                    "ALTER TABLE user_settings ADD COLUMN showFibonacciRrOnChart INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "ALTER TABLE trade_records ADD COLUMN initialLots REAL NOT NULL DEFAULT 0.0"
                )
                db.execSQL(
                    "ALTER TABLE trade_records ADD COLUMN executedPartialTiers TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "UPDATE trade_records SET initialLots = lots WHERE initialLots <= 0.0"
                )
            }
        }

        fun getInstance(context: Context): TradeHubDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TradeHubDatabase::class.java,
                    "tradehub_local.db",
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
