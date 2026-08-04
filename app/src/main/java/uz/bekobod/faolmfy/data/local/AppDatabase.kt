package uz.bekobod.faolmfy.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PositionEntity::class,
        DeviceEventEntity::class,
        PhotoQueueEntity::class,
        NoteQueueEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun positions(): PositionDao
    abstract fun deviceEvents(): DeviceEventDao
    abstract fun photoQueue(): PhotoQueueDao
    abstract fun noteQueue(): NoteQueueDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "faolmfy.db"
            ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
