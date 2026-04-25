package com.wrongbook.app.data.local

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [QuestionEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE questions ADD COLUMN contentUpdatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE questions ADD COLUMN analysisContentUpdatedAt INTEGER")
                db.execSQL("ALTER TABLE questions ADD COLUMN explanationContentUpdatedAt INTEGER")
                db.execSQL("ALTER TABLE questions ADD COLUMN hintContentUpdatedAt INTEGER")
                db.execSQL("ALTER TABLE questions ADD COLUMN followUpContentUpdatedAt INTEGER")

                db.execSQL(
                    "UPDATE questions SET contentUpdatedAt = CASE WHEN updatedAt > 0 THEN updatedAt ELSE createdAt END"
                )
                db.execSQL(
                    "UPDATE questions SET analysisContentUpdatedAt = contentUpdatedAt WHERE analysis IS NOT NULL"
                )
                db.execSQL(
                    "UPDATE questions SET explanationContentUpdatedAt = contentUpdatedAt WHERE detailedExplanation IS NOT NULL"
                )
                db.execSQL(
                    "UPDATE questions SET hintContentUpdatedAt = contentUpdatedAt WHERE hint IS NOT NULL"
                )
                db.execSQL(
                    "UPDATE questions SET followUpContentUpdatedAt = contentUpdatedAt WHERE followUpChats IS NOT NULL"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE questions ADD COLUMN grade TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE questions ADD COLUMN questionType TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE questions ADD COLUMN source TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE questions ADD COLUMN errorCause TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE questions ADD COLUMN tags TEXT")
                db.execSQL("ALTER TABLE questions ADD COLUMN masteryLevel INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE questions ADD COLUMN detailedExplanationUpdatedAt INTEGER")
                db.execSQL("ALTER TABLE questions ADD COLUMN hintUpdatedAt INTEGER")
                db.execSQL(
                    "UPDATE questions SET detailedExplanationUpdatedAt = COALESCE(explanationContentUpdatedAt, contentUpdatedAt) WHERE detailedExplanation IS NOT NULL"
                )
                db.execSQL(
                    "UPDATE questions SET hintUpdatedAt = COALESCE(hintContentUpdatedAt, contentUpdatedAt) WHERE hint IS NOT NULL"
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wrongbook.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
