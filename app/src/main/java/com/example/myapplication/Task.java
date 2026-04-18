package com.example.myapplication;

import androidx.annotation.NonNull;

public class Task {
    public int id;
    public String content;
    public String date;
    public int isDone;
    public String finishDate;

    public Task(int id, String content, String date, int isDone, String finishDate) {
        this.id = id;
        this.content = content;
        this.date = date;
        this.isDone = isDone;
        this.finishDate = finishDate;
    }

    @NonNull
    @Override
    public String toString() {
        return content;
    }
}
