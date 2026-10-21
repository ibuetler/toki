package com.hackinglab.toki.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tasks")
public class TokiTask {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String title = "";
    @NonNull public String notes = "";
    @NonNull public String priority = "normal";
    public boolean completed;
    public long reminderMillis;
    public long createdMillis;
}
