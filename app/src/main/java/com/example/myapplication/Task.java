package com.example.myapplication;

public class Task {
    public int id;
    public String content;
    public String date;
    public int isDone;
    public String finishDate; // Thời điểm hoàn thành (dưới dạng timestamp string)

    public Task(int id, String content, String date, int isDone, String finishDate) {
        this.id = id;
        this.content = content;
        this.date = date;
        this.isDone = isDone;
        this.finishDate = finishDate;
    }
}
