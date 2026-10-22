package com.hackinglab.toki.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface DeadlineDao {
    @Query("SELECT * FROM deadlines ORDER BY startMillis") List<Deadline> all();
    @Query("SELECT * FROM deadlines WHERE id = :id") Deadline find(String id);
    @Query("DELETE FROM deadlines") void clear();
    @Query("DELETE FROM deadlines WHERE id = :id") void delete(String id);
    @Query("UPDATE deadlines SET hidden = :hidden WHERE id = :id") void setHidden(String id, boolean hidden);
    @Insert void insert(List<Deadline> records);
}
