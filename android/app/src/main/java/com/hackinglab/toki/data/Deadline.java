package com.hackinglab.toki.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;

@Entity(tableName = "deadlines")
public class Deadline {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String title = "";
    @NonNull public String description = "";
    @NonNull public String groupName = "";
    @NonNull public String timezone = "UTC";
    @NonNull public String rrule = "";
    @NonNull @ColumnInfo(defaultValue = "''") public String exdates = "";
    @NonNull @ColumnInfo(defaultValue = "''") public String rdates = "";
    @ColumnInfo(defaultValue = "0") public boolean recurrenceLoaded;
    public long startMillis;
    public int reminderMinutes;
    public boolean enabled;
    public boolean allDay;
}
