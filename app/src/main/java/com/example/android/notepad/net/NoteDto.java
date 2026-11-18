package com.example.android.notepad.net;

/** 与服务器交互的笔记数据类（字段名必须与服务器 JSON 一致） */
public class NoteDto {
    public String uuid;
    public String title;
    public String content;
    public long createdAt;
    public long updatedAt;
    public long backupAt;

    public NoteDto() {
    }

    public NoteDto(String uuid, String title, String content, long createdAt, long updatedAt) {
        this.uuid = uuid;
        this.title = title;
        this.content = content;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}