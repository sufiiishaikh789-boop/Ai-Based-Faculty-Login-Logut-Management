package com.example.facultyattendance.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AuditDao {
    @Insert
    long insert(AuditEntity entity);

    @Query("SELECT * FROM audit ORDER BY atMillis DESC, id DESC LIMIT :limit")
    List<AuditEntity> recent(int limit);
}
