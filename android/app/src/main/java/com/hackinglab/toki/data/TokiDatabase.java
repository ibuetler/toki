package com.hackinglab.toki.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {Deadline.class, TokiTask.class}, version = 4, exportSchema = false)
public abstract class TokiDatabase extends RoomDatabase {
    public abstract DeadlineDao deadlines();
    public abstract TaskDao tasks();
}
