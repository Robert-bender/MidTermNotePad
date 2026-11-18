package com.example.android.notepad.net;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.widget.Toast;

import com.example.android.notepad.DeviceIdUtil;
import com.example.android.notepad.NotePad;


/**
 * 统一删除入口：
 *  - 仅本地删除：只删本地
 *  - 云端同步删除：先按 uuid 删云端（失败/不存在都不影响本地），再删本地
 *
 * 用法：
 *   DeleteHelper.showDialog(activity, noteUri, new DeleteHelper.OnDeleted() { ... });
 */
public class DeleteHelper {

    public interface OnDeleted {
        /** 本地已删完，调用方可做 finish() 等收尾；cloudOk 表示云端是否删除成功（仅提示用） */
        void onDeleted(boolean cloudSynced);
    }

    /**
     * 弹三选对话框并执行删除。
     * @param noteUri 要删除的笔记 URI（必须有 _ID）
     * @param cb      本地删除完成后的回调，可为 null
     */
    public static void showDialog(final Activity act, final Uri noteUri, final OnDeleted cb) {
        // 先查出 uuid 和标题，用于提示
        String uuid = null;
        String title = "";
        Cursor c = null;
        try {
            c = act.getContentResolver().query(noteUri,
                    new String[]{NotePad.Notes.COLUMN_NAME_UUID,
                            NotePad.Notes.COLUMN_NAME_TITLE},
                    null, null, null);
            if (c != null && c.moveToFirst()) {
                uuid = c.getString(0);
                title = c.getString(1);
            }
        } finally {
            if (c != null) c.close();
        }

        if (uuid == null || uuid.length() == 0) {
            // 老数据没 uuid（理论上回填过，不会走到），直接本地删
            doLocalDelete(act, noteUri, cb);
            return;
        }

        final String finalUuid = uuid;
        final String displayTitle = (title == null || title.length() == 0) ? "该笔记" : title;

        new AlertDialog.Builder(act)
                .setTitle("删除笔记")
                .setMessage("「" + displayTitle + "」\n\n选择删除方式：")
                .setPositiveButton("云端同步删除",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                doCloudThenLocal(act, noteUri, finalUuid, cb);
                            }
                        })
                .setNegativeButton("仅本地删除",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                doLocalDelete(act, noteUri, cb);
                            }
                        })
                .setNeutralButton("取消", null)
                .show();
    }

    /** 先删云端，无论结果如何都接着删本地 */
    private static void doCloudThenLocal(final Activity act, final Uri noteUri,
                                         String uuid, final OnDeleted cb) {
        Toast.makeText(act, "正在同步删除云端...", Toast.LENGTH_SHORT).show();
        BackupApi.deleteRemote(DeviceIdUtil.get(act), uuid,
                new BackupApi.Callback<DeleteResponse>() {
                    @Override
                    public void onSuccess(DeleteResponse r) {
                        // 云端不存在（deleted=0）也算成功
                        boolean ok = (r != null && r.code == 0);
                        if (!ok) {
                            Toast.makeText(act,
                                    "云端删除返回异常，仍继续删本地",
                                    Toast.LENGTH_SHORT).show();
                        }
                        doLocalDelete(act, noteUri, cb, true);
                    }
                    @Override
                    public void onError(Exception e) {
                        // 网络失败也继续删本地，避免用户被卡住
                        Toast.makeText(act,
                                "云端删除失败（" + e.getMessage() + "），仍删除本地",
                                Toast.LENGTH_LONG).show();
                        doLocalDelete(act, noteUri, cb, false);
                    }
                });
    }

    private static void doLocalDelete(Activity act, Uri noteUri, OnDeleted cb) {
        doLocalDelete(act, noteUri, cb, false);
    }

    private static void doLocalDelete(Activity act, Uri noteUri, OnDeleted cb, boolean cloudSynced) {
        try {
            act.getContentResolver().delete(noteUri, null, null);
        } catch (Exception e) {
            Toast.makeText(act, "本地删除失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        if (cloudSynced) {
            Toast.makeText(act, "已同步删除云端与本地", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(act, "已删除本地笔记", Toast.LENGTH_SHORT).show();
        }
        if (cb != null) cb.onDeleted(cloudSynced);
    }
}