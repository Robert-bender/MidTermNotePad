package com.example.android.notepad;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.content.FileProvider;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * TXT 导出：把笔记写到 cache/export/ 下，再用 FileProvider 分享。
 * 单条文件名：[记事本备份]标题_yyyyMMdd_HHmmss.txt
 * 全部文件名：[记事本备份]全部_yyyyMMdd_HHmmss.txt
 */
public class ExportManager {

    private static final String DIR = "export";

    /** 导出单条笔记并分享 */
    public static void exportSingle(Context ctx, Uri noteUri) {
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(noteUri,
                    new String[]{
                            NotePad.Notes.COLUMN_NAME_TITLE,
                            NotePad.Notes.COLUMN_NAME_NOTE
                    }, null, null, null);
            if (c == null || !c.moveToFirst()) {
                toast(ctx, "没有可导出的内容");
                return;
            }
            String title = safe(c.getString(0));
            String content = safe(c.getString(1));
            String body = title + "\n\n" + content + "\n";

            String fileName = "[记事本备份]" + sanitize(title) + "_" + ts() + ".txt";
            File f = writeFile(ctx, fileName, body);
            share(ctx, f);
        } catch (Exception e) {
            toast(ctx, "导出失败: " + e.getMessage());
        } finally {
            if (c != null) c.close();
        }
    }

    /** 导出全部笔记并分享：标题\n\n内容\n\n----------\n\n */
    public static void exportAll(Context ctx) {
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(
                    NotePad.Notes.CONTENT_URI,
                    new String[]{
                            NotePad.Notes.COLUMN_NAME_TITLE,
                            NotePad.Notes.COLUMN_NAME_NOTE
                    },
                    null, null,
                    NotePad.Notes.DEFAULT_SORT_ORDER);

            if (c == null || c.getCount() == 0) {
                toast(ctx, "没有可导出的笔记");
                return;
            }

            StringBuilder sb = new StringBuilder();
            while (c.moveToNext()) {
                String title = safe(c.getString(0));
                String content = safe(c.getString(1));
                sb.append(title).append("\n\n")
                        .append(content).append("\n\n")
                        .append("----------\n\n");
            }

            String fileName = "[记事本备份]全部_" + ts() + ".txt";
            File f = writeFile(ctx, fileName, sb.toString());
            share(ctx, f);
        } catch (Exception e) {
            toast(ctx, "导出失败: " + e.getMessage());
        } finally {
            if (c != null) c.close();
        }
    }

    // ---------- 内部工具 ----------

    private static File writeFile(Context ctx, String fileName, String content) throws Exception {
        File dir = new File(ctx.getCacheDir(), DIR);
//        File dir = new File(ctx.getExternalCacheDir(), DIR);
        if (!dir.exists()) dir.mkdirs();
        File f = new File(dir, fileName);
        FileOutputStream fos = new FileOutputStream(f);
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(fos, "UTF-8"));
        pw.write(content);
        pw.flush();
        pw.close();
        return f;
    }

    private static void share(Context ctx, File f) {
        Uri uri = FileProvider.getUriForFile(
                ctx, "com.example.android.notepad.fileprovider", f);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent chooser = Intent.createChooser(intent, "分享笔记");
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(chooser);
    }

    private static String ts() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
    }

    /** 文件名里去掉非法字符 */
    private static String sanitize(String s) {
        if (s == null || s.length() == 0) return "无标题";
        return s.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static void toast(Context ctx, String msg) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
    }
}