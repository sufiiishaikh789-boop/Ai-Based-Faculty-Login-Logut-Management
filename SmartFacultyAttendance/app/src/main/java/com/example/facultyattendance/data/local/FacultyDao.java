package com.example.facultyattendance.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface FacultyDao {
    /** ABORT makes a duplicate UID throw SQLiteConstraintException instead of silently replacing a profile. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(FacultyEntity entity);

    @Update
    int update(FacultyEntity entity);

    @Query("SELECT * FROM faculty WHERE uid = :uid")
    FacultyEntity get(String uid);

    @Query("SELECT * FROM faculty ORDER BY name COLLATE NOCASE ASC")
    List<FacultyEntity> all();

    @Query("SELECT * FROM faculty WHERE active = 1 AND encryptedTemplate IS NOT NULL AND templateModelId = :modelId")
    List<FacultyEntity> activeWithTemplate(String modelId);

    @Query("SELECT COUNT(*) FROM faculty")
    int count();

    @Query("DELETE FROM faculty WHERE templateDemo = 1 AND uid NOT IN (SELECT facultyUid FROM attendance)")
    int deleteDemoFacultyWithoutAttendance();
}
