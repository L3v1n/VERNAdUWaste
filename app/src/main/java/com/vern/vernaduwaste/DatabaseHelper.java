package com.vern.vernaduwaste;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "waste_db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_WASTE_COUNT = "waste_count";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_COUNT = "count";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_TABLE = "CREATE TABLE " + TABLE_WASTE_COUNT + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_COUNT + " INTEGER)";
        db.execSQL(CREATE_TABLE);

        // Insert initial count of 0
        ContentValues values = new ContentValues();
        values.put(COLUMN_COUNT, 0);
        db.insert(TABLE_WASTE_COUNT, null, values);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_WASTE_COUNT);
        onCreate(db);
    }

    public int getWasteCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_WASTE_COUNT, new String[]{COLUMN_COUNT}, null, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int count = cursor.getInt(cursor.getColumnIndex(COLUMN_COUNT));
            cursor.close();
            return count;
        }
        return 0;
    }

    public void incrementWasteCount() {
        SQLiteDatabase db = this.getWritableDatabase();
        int currentCount = getWasteCount();
        ContentValues values = new ContentValues();
        values.put(COLUMN_COUNT, currentCount + 1);
        db.update(TABLE_WASTE_COUNT, values, null, null);
    }
}