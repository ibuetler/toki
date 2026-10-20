package com.hackinglab.toki.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {Deadline.class}, version = 1, exportSchema = false)
public abstract class TokiDatabase extends RoomDatabase {
    public abstract DeadlineDao deadlines();
}
