package app.aaps.pump.omnipod.dash.history.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [HistoryRecordEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class DashHistoryDatabase : RoomDatabase() {

    abstract fun historyRecordDao(): HistoryRecordDao

    companion object {
        @Volatile
        private var INSTANCE: DashHistoryDatabase? = null

        fun getInstance(context: Context): DashHistoryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DashHistoryDatabase::class.java,
                    "dash_history_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
