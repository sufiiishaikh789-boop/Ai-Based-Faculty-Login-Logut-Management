package com.example.facultyattendance.data.local;

import androidx.room.migration.Migration;

/**
 * Registry of schema migrations. Version 1 is the first release, so the list is empty.
 *
 * When you change an entity: bump AppDatabase.version, add a Migration here, and keep the exported
 * schema JSON files in app/schemas under version control. The app never falls back to a
 * destructive migration because that would silently delete attendance history.
 *
 * Example for the future:
 *   static final Migration MIGRATION_1_2 = new Migration(1, 2) {
 *       public void migrate(SupportSQLiteDatabase db) { db.execSQL("ALTER TABLE faculty ADD COLUMN email TEXT"); }
 *   };
 */
public final class Migrations {
    public static final Migration[] ALL = new Migration[] { };

    private Migrations() { }
}
