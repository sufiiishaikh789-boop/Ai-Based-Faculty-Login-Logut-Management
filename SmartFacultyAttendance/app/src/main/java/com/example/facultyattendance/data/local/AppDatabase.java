package com.example.facultyattendance.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {FacultyEntity.class, AttendanceEntity.class, AuditEntity.class},
        version = 1,
        exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    public static final String NAME = "faculty_attendance.db";

    public abstract FacultyDao facultyDao();
    public abstract AttendanceDao attendanceDao();
    public abstract AuditDao auditDao();

    /** Production database. No destructive fallback: missing migrations fail loudly instead of wiping data. */
    public static AppDatabase create(Context context) {
        return Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, NAME)
                .addMigrations(Migrations.ALL)
                .build();
    }
}
