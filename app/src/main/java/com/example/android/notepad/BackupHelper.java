package com.example.android.notepad;

import android.content.Context;
import android.database.Cursor;
import android.widget.Toast;

import com.example.android.notepad.net.BackupApi;
import com.example.android.notepad.net.NoteDto;

import java.util.ArrayList;
import java.util.List;

/** 备份/恢复的业务入口，供 NotesList 直接调用 */
public class BackupHelper {

    /** 读本地全部笔记 → 上传。回调在主线程。 */
    public static void doBackup(final Context ctx,
                                final BackupApi.Callback<com.example.android.notepad.net.BackupResponse> cb) {
        final List<NoteDto> notes = readAllLocal(ctx);
        if (notes == null || notes.isEmpty()) {
            Toast.makeText(ctx, "本地没有笔记可备份", Toast.LENGTH_SHORT).show();
            return;
        }
        String deviceId = DeviceIdUtil.get(ctx);
        BackupApi.backup(deviceId, notes, cb);
    }

    /** 从云端拉取。回调在主线程。 */
    public static void doFetchRemote(Context ctx,
                                     BackupApi.Callback<com.example.android.notepad.net.RestoreResponse> cb) {
        BackupApi.restore(DeviceIdUtil.get(ctx), cb);
    }

    private static List<NoteDto> readAllLocal(Context ctx) {
        List<NoteDto> list = new ArrayList<NoteDto>();
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(NotePad.Notes.CONTENT_URI,
                    new String[]{
                            NotePad.Notes.COLUMN_NAME_UUID,
                            NotePad.Notes.COLUMN_NAME_TITLE,
                            NotePad.Notes.COLUMN_NAME_NOTE,
                            NotePad.Notes.COLUMN_NAME_CREATE_DATE,
                            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE
                    },
                    null, null,
                    NotePad.Notes.DEFAULT_SORT_ORDER);
            if (c != null) {
                while (c.moveToNext()) {
                    NoteDto d = new NoteDto();
                    d.uuid = c.getString(0);
                    d.title = c.getString(1);
                    d.content = c.getString(2);
                    d.createdAt = c.getLong(3);
                    d.updatedAt = c.getLong(4);
                    if (d.uuid != null && d.uuid.length() > 0) {
                        list.add(d);
                    }
                }
            }
        } finally {
            if (c != null) c.close();
        }
        return list;
    }
}