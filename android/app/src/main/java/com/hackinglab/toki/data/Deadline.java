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
    @NonNull @ColumnInfo(defaultValue = "'deadline'") public String kind = "deadline";
    @ColumnInfo(defaultValue = "0") public int birthdayMonth;
    @ColumnInfo(defaultValue = "0") public int birthdayDay;
    @ColumnInfo(defaultValue = "0") public int birthYear;
    @ColumnInfo(defaultValue = "9") public int birthdayHour = 9;
    @ColumnInfo(defaultValue = "0") public int birthdayMinute;
    @NonNull @ColumnInfo(defaultValue = "'feb28'") public String leapDayPolicy = "feb28";
    @NonNull @ColumnInfo(defaultValue = "''") public String sourceNames = "";
    @ColumnInfo(defaultValue = "0") public long endMillis;
    @NonNull @ColumnInfo(defaultValue = "''") public String externalKey = "";
    @ColumnInfo(defaultValue = "0") public boolean hidden;
    public long startMillis;
    public int reminderMinutes;
    public boolean enabled;
    public boolean allDay;
}
