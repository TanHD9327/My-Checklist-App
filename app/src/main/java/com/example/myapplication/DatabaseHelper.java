package com.example.myapplication;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "checklist.db";
    private static final int DB_VERSION = 3;

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, content TEXT, date TEXT, is_done INTEGER DEFAULT 0, finish_date TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN finish_date TEXT");
        }
    }

    // --- HÀM THÊM: Dùng cái này để lưu cả nội dung và ngày ---
    public void addTask(String content, String date) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("content", content);
        values.put("date", date);
        values.put("is_done", 0);
        db.insert("tasks", null, values);
        db.close();
    }

    // --- HÀM LẤY DỮ LIỆU: Trả về danh sách đối tượng Task ---
    public ArrayList<Task> getAllTasksAsObjects() {
        ArrayList<Task> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT id, content, date, is_done, finish_date FROM tasks ORDER BY id DESC", null);

        if (cursor.moveToFirst()) {
            int idIndex = cursor.getColumnIndexOrThrow("id");
            int contentIndex = cursor.getColumnIndexOrThrow("content");
            int dateIndex = cursor.getColumnIndexOrThrow("date");
            int isDoneIndex = cursor.getColumnIndexOrThrow("is_done");
            int finishDateIndex = cursor.getColumnIndexOrThrow("finish_date");

            do {
                list.add(new Task(
                        cursor.getInt(idIndex),
                        cursor.getString(contentIndex),
                        cursor.getString(dateIndex),
                        cursor.getInt(isDoneIndex),
                        cursor.getString(finishDateIndex)
                ));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    // --- HÀM CẬP NHẬT: Tích chọn checkbox ---
    public void updateStatus(int id, int isDone, String finishDate) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("is_done", isDone);
        values.put("finish_date", finishDate);
        db.update("tasks", values, "id = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    public void updateTask(int id, String content, String date) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("content", content);
        values.put("date", date);
        db.update("tasks", values, "id = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    public void deleteOldFinishedTasks(long sevenDaysAgoMillis) {
        SQLiteDatabase db = this.getWritableDatabase();
        // Chuyển finish_date sang kiểu số để so sánh chính xác hơn
        db.delete("tasks", "is_done = 1 AND CAST(finish_date AS INTEGER) < ?", new String[]{String.valueOf(sevenDaysAgoMillis)});
        db.close();
    }

    // --- HÀM XÓA: Xóa bằng ID ---
    public void deleteTask(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete("tasks", "id = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    public void deleteTasks(List<Integer> ids) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            for (Integer id : ids) {
                db.delete("tasks", "id = ?", new String[]{String.valueOf(id)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    public void updateTasksStatus(List<Integer> ids, int isDone, String finishDate) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("is_done", isDone);
            values.put("finish_date", finishDate);
            for (Integer id : ids) {
                db.update("tasks", values, "id = ?", new String[]{String.valueOf(id)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    public JSONArray exportTasksToJSON() {
        JSONArray array = new JSONArray();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT content, date, is_done, finish_date FROM tasks", null);
        if (cursor.moveToFirst()) {
            do {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("content", cursor.getString(0));
                    obj.put("date", cursor.getString(1));
                    obj.put("is_done", cursor.getInt(2));
                    obj.put("finish_date", cursor.getString(3));
                    array.put(obj);
                } catch (Exception ignored) {}
            } while (cursor.moveToNext());
        }
        cursor.close();
        return array;
    }

    public void importTasksFromJSON(JSONArray array) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                ContentValues values = new ContentValues();
                values.put("content", obj.getString("content"));
                values.put("date", obj.optString("date", ""));
                values.put("is_done", obj.optInt("is_done", 0));
                values.put("finish_date", obj.optString("finish_date", ""));
                db.insert("tasks", null, values);
            }
            db.setTransactionSuccessful();
        } catch (Exception ignored) {
        } finally {
            db.endTransaction();
            db.close();
        }
    }
}