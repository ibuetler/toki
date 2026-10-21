package com.hackinglab.toki.data;

import androidx.room.*;
import java.util.List;

@Dao
public interface TaskDao {
    @Query("SELECT * FROM tasks") List<TokiTask> all();
    @Query("SELECT * FROM tasks WHERE id = :id") TokiTask find(String id);
    @Query("DELETE FROM tasks") void clear();
    @Query("DELETE FROM tasks WHERE id = :id") void delete(String id);
    @Insert(onConflict = OnConflictStrategy.REPLACE) void insert(List<TokiTask> records);
}
