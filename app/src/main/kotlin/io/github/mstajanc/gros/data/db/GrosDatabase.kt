package io.github.mstajanc.gros.data.db

import android.content.Context
import androidx.room3.ColumnTypeConverter
import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver
import java.time.LocalDate

/** `LocalDate` sa ukladá ako ISO text `RRRR-MM-DD`, ktorý sa dá porovnávať aj radiť ako text. */
object Converters {
    @ColumnTypeConverter
    fun localDateToText(date: LocalDate?): String? = date?.toString()

    @ColumnTypeConverter
    fun textToLocalDate(text: String?): LocalDate? = text?.let(LocalDate::parse)
}

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        RecurringRuleEntity::class,
        BudgetEntity::class,
    ],
    version = GrosDatabase.VERSION,
    exportSchema = true,
)
@ColumnTypeConverters(Converters::class)
abstract class GrosDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        const val NAME = "gros.db"

        /** Verzia schémy; každé zvýšenie potrebuje migráciu a test. */
        const val VERSION = 1

        /**
         * Databáza v súkromnom úložisku appky nad systémovým SQLite (bez pribalenej natívnej knižnice).
         * Šifrovanie v pokoji zabezpečuje systém.
         */
        fun create(context: Context): GrosDatabase =
            Room.databaseBuilder<GrosDatabase>(context.applicationContext, NAME)
                .setDriver(AndroidSQLiteDriver())
                .build()
    }
}
