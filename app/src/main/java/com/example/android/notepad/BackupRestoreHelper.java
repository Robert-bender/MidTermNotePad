package com.example.android.notepad;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.example.android.notepad.net.NoteDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 把云端拉回来的 NoteDto 列表写进本地 Provider。
 * 合并：按 uuid 比对，本地没有→insert；有且云端 updatedAt 更新→update；否则跳过
 * 覆盖：先清空本地全部笔记，再全部 insert
 */
public class BackupRestoreHelper {

    public static class Result {
        public int inserted;
        public int updated;
        public int skipped;
    }

    public static Result restoreMerge(Context ctx, List<NoteDto> remote) {
        Result r = new Result();
        ContentResolver cr = ctx.getContentResolver();

        // 拉本地 uuid → (_id, modified)
        Map<String, long[]> local = new HashMap<>(); // uuid -> [_id, modified]
        Cursor c = null;
        try {
            c = cr.query(NotePad.Notes.CONTENT_URI,
                    new String[]{NotePad.Notes._ID,
                            NotePad.Notes.COLUMN_NAME_UUID,
                            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE},
                    null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    long id = c.getLong(0);
                    String uuid = c.getString(1);
                    long mod = c.getLong(2);
                    if (uuid != null && uuid.length() > 0) {
                        local.put(uuid, new long[]{id, mod});
                    }
                }
            }
        } finally {
            if (c != null) c.close();
        }

        for (NoteDto dto : remote) {
            if (dto == null || dto.uuid == null || dto.uuid.length() == 0) {
                r.skipped++;
                continue;
            }
            long[] exist = local.get(dto.uuid);
            if (exist == null) {
                // 本地没有 → insert（显式带 uuid）
                ContentValues v = toValues(dto);
                Uri newUri = cr.insert(NotePad.Notes.CONTENT_URI, v);
                if (newUri != null) r.inserted++;
                else r.skipped++;
            } else {
                long localId = exist[0];
                long localMod = exist[1];
                if (dto.updatedAt > localMod) {
                    Uri u = Uri.withAppendedPath(
                            NotePad.Notes.CONTENT_URI, String.valueOf(localId));
                    ContentValues v = toValues(dto);
                    // 避免 update 覆盖时间戳为「当前时间」，Provider 的 update 会强制改 modified，
                    // 这里为了简单接受它（服务端 updatedAt 已记录，下次备份仍以本地为准）。
                    int n = cr.update(u, v, null, null);
                    if (n > 0) r.updated++;
                    else r.skipped++;
                } else {
                    r.skipped++;
                }
            }
        }
        return r;
    }

    public static Result restoreOverwrite(Context ctx, List<NoteDto> remote) {
        Result r = new Result();
        ContentResolver cr = ctx.getContentResolver();

        // 清空本地
        cr.delete(NotePad.Notes.CONTENT_URI, null, null);

        for (NoteDto dto : remote) {
            if (dto == null || dto.uuid == null || dto.uuid.length() == 0) {
                r.skipped++;
                continue;
            }
            ContentValues v = toValues(dto);
            Uri newUri = cr.insert(NotePad.Notes.CONTENT_URI, v);
            if (newUri != null) r.inserted++;
            else r.skipped++;
        }
        return r;
    }

    private static ContentValues toValues(NoteDto dto) {
        ContentValues v = new ContentValues();
        v.put(NotePad.Notes.COLUMN_NAME_UUID, dto.uuid);
        v.put(NotePad.Notes.COLUMN_NAME_TITLE, dto.title == null ? "" : dto.title);
        v.put(NotePad.Notes.COLUMN_NAME_NOTE, dto.content == null ? "" : dto.content);
        v.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE,
                dto.createdAt > 0 ? dto.createdAt : System.currentTimeMillis());
        v.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                dto.updatedAt > 0 ? dto.updatedAt : System.currentTimeMillis());
        return v;
    }
}