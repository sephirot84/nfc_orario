package it.nfctimetracker

import android.app.Application
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import it.nfctimetracker.data.TrackerDatabase
import it.nfctimetracker.data.TrackerRepository

class TrackerApplication : Application() {
    val database by lazy {
        Room.databaseBuilder(this, TrackerDatabase::class.java, "nfc-time-tracker.db")
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) { db.execSQL("PRAGMA synchronous=FULL") }
            })
            .addMigrations(TrackerDatabase.MIGRATION_1_2).build()
    }
    val repository by lazy { TrackerRepository(database) }
}
