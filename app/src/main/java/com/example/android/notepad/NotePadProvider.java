/*
 * Copyright (C) 2007 The Android Open Source Project
 * ... (版权头保留你原来的)
 */

package com.example.android.notepad;

import com.example.android.notepad.NotePad;

import android.content.ClipDescription;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.ContentProvider.PipeDataWriter;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.LiveFolders;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.UUID;

public class NotePadProvider extends ContentProvider implements PipeDataWriter<Cursor> {
    private static final String TAG = "NotePadProvider";

    private static final String DATABASE_NAME = "note_pad.db";

    // ★ 版本从 2 升到 3：新增 uuid 列
    private static final int DATABASE_VERSION = 3;

    private static HashMap<String, String> sNotesProjectionMap;
    private static HashMap<String, String> sLiveFolderProjectionMap;

    private static final String[] READ_NOTE_PROJECTION = new String[] {
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_NOTE,
            NotePad.Notes.COLUMN_NAME_TITLE,
    };
    private static final int READ_NOTE_NOTE_INDEX = 1;
    private static final int READ_NOTE_TITLE_INDEX = 2;

    private static final int NOTES = 1;
    private static final int NOTE_ID = 2;
    private static final int LIVE_FOLDER_NOTES = 3;

    private static final UriMatcher sUriMatcher;

    private DatabaseHelper mOpenHelper;

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(NotePad.AUTHORITY, "notes", NOTES);
        sUriMatcher.addURI(NotePad.AUTHORITY, "notes/#", NOTE_ID);
        sUriMatcher.addURI(NotePad.AUTHORITY, "live_folders/notes", LIVE_FOLDER_NOTES);

        sNotesProjectionMap = new HashMap<String, String>();
        sNotesProjectionMap.put(NotePad.Notes._ID, NotePad.Notes._ID);
        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_TITLE, NotePad.Notes.COLUMN_NAME_TITLE);
        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_NOTE, NotePad.Notes.COLUMN_NAME_NOTE);
        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE,
                NotePad.Notes.COLUMN_NAME_CREATE_DATE);
        sNotesProjectionMap.put(
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE);
        // ★ 新增 uuid 映射
        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_UUID, NotePad.Notes.COLUMN_NAME_UUID);

        sLiveFolderProjectionMap = new HashMap<String, String>();
        sLiveFolderProjectionMap.put(LiveFolders._ID, NotePad.Notes._ID + " AS " + LiveFolders._ID);
        sLiveFolderProjectionMap.put(LiveFolders.NAME, NotePad.Notes.COLUMN_NAME_TITLE + " AS " +
                LiveFolders.NAME);
    }

    static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        // ★ 建表加 uuid 列
        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + NotePad.Notes.TABLE_NAME + " ("
                    + NotePad.Notes._ID + " INTEGER PRIMARY KEY,"
                    + NotePad.Notes.COLUMN_NAME_TITLE + " TEXT,"
                    + NotePad.Notes.COLUMN_NAME_NOTE + " TEXT,"
                    + NotePad.Notes.COLUMN_NAME_CREATE_DATE + " INTEGER,"
                    + NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE + " INTEGER,"
                    + NotePad.Notes.COLUMN_NAME_UUID + " TEXT"
                    + ");");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_notes_uuid ON "
                    + NotePad.Notes.TABLE_NAME + "(" + NotePad.Notes.COLUMN_NAME_UUID + ")");
        }

        /**
         * ★ 升级策略改为增量，不再 DROP 重建，避免丢数据。
         * 从 2 → 3：ALTER TABLE ADD COLUMN uuid，并给存量数据回填 UUID。
         */
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);

            if (oldVersion < 3) {
                // 1. 加列（若已存在则忽略异常）
                try {
                    db.execSQL("ALTER TABLE " + NotePad.Notes.TABLE_NAME
                            + " ADD COLUMN " + NotePad.Notes.COLUMN_NAME_UUID + " TEXT");
                } catch (SQLException e) {
                    Log.w(TAG, "uuid column may already exist", e);
                }
                // 2. 回填存量行的 uuid
                backfillUuids(db);
                // 3. 建索引
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_notes_uuid ON "
                        + NotePad.Notes.TABLE_NAME + "(" + NotePad.Notes.COLUMN_NAME_UUID + ")");
            }
        }

        /** 给 uuid 为 NULL/空 的行生成 UUID */
        private void backfillUuids(SQLiteDatabase db) {
            Cursor c = null;
            try {
                c = db.query(NotePad.Notes.TABLE_NAME,
                        new String[]{NotePad.Notes._ID},
                        NotePad.Notes.COLUMN_NAME_UUID + " IS NULL OR "
                                + NotePad.Notes.COLUMN_NAME_UUID + " = ''",
                        null, null, null, null);
                while (c.moveToNext()) {
                    long id = c.getLong(0);
                    ContentValues v = new ContentValues();
                    v.put(NotePad.Notes.COLUMN_NAME_UUID, UUID.randomUUID().toString());
                    db.update(NotePad.Notes.TABLE_NAME, v,
                            NotePad.Notes._ID + "=?", new String[]{String.valueOf(id)});
                }
            } finally {
                if (c != null) c.close();
            }
        }
    }

    @Override
    public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(NotePad.Notes.TABLE_NAME);

        switch (sUriMatcher.match(uri)) {
            case NOTES:
                qb.setProjectionMap(sNotesProjectionMap);
                break;
            case NOTE_ID:
                qb.setProjectionMap(sNotesProjectionMap);
                qb.appendWhere(
                        NotePad.Notes._ID + "=" +
                                uri.getPathSegments().get(NotePad.Notes.NOTE_ID_PATH_POSITION));
                break;
            case LIVE_FOLDER_NOTES:
                qb.setProjectionMap(sLiveFolderProjectionMap);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        String orderBy;
        if (TextUtils.isEmpty(sortOrder)) {
            orderBy = NotePad.Notes.DEFAULT_SORT_ORDER;
        } else {
            orderBy = sortOrder;
        }

        SQLiteDatabase db = mOpenHelper.getReadableDatabase();

        Cursor c = qb.query(db, projection, selection, selectionArgs,
                null, null, orderBy);

        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case NOTES:
            case LIVE_FOLDER_NOTES:
                return NotePad.Notes.CONTENT_TYPE;
            case NOTE_ID:
                return NotePad.Notes.CONTENT_ITEM_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    static ClipDescription NOTE_STREAM_TYPES = new ClipDescription(null,
            new String[] { ClipDescription.MIMETYPE_TEXT_PLAIN });

    @Override
    public String[] getStreamTypes(Uri uri, String mimeTypeFilter) {
        switch (sUriMatcher.match(uri)) {
            case NOTES:
            case LIVE_FOLDER_NOTES:
                return null;
            case NOTE_ID:
                return NOTE_STREAM_TYPES.filterMimeTypes(mimeTypeFilter);
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeTypeFilter, Bundle opts)
            throws FileNotFoundException {
        String[] mimeTypes = getStreamTypes(uri, mimeTypeFilter);
        if (mimeTypes != null) {
            Cursor c = query(uri, READ_NOTE_PROJECTION, null, null, null);
            if (c == null || !c.moveToFirst()) {
                if (c != null) c.close();
                throw new FileNotFoundException("Unable to query " + uri);
            }
            return new AssetFileDescriptor(
                    openPipeHelper(uri, mimeTypes[0], opts, c, this), 0,
                    AssetFileDescriptor.UNKNOWN_LENGTH);
        }
        return super.openTypedAssetFile(uri, mimeTypeFilter, opts);
    }

    @Override
    public void writeDataToPipe(ParcelFileDescriptor output, Uri uri, String mimeType,
                                Bundle opts, Cursor c) {
        FileOutputStream fout = new FileOutputStream(output.getFileDescriptor());
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new OutputStreamWriter(fout, "UTF-8"));
            pw.println(c.getString(READ_NOTE_TITLE_INDEX));
            pw.println("");
            pw.println(c.getString(READ_NOTE_NOTE_INDEX));
        } catch (UnsupportedEncodingException e) {
            Log.w(TAG, "Ooops", e);
        } finally {
            c.close();
            if (pw != null) {
                pw.flush();
            }
            try {
                fout.close();
            } catch (IOException e) {
            }
        }
    }

    // ★ insert 时自动生成 uuid
    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        if (sUriMatcher.match(uri) != NOTES) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        ContentValues values;
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }

        Long now = Long.valueOf(System.currentTimeMillis());

        if (values.containsKey(NotePad.Notes.COLUMN_NAME_CREATE_DATE) == false) {
            values.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, now);
        }
        if (values.containsKey(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE) == false) {
            values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, now);
        }
        if (values.containsKey(NotePad.Notes.COLUMN_NAME_TITLE) == false) {
            Resources r = Resources.getSystem();
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, r.getString(android.R.string.untitled));
        }
        if (values.containsKey(NotePad.Notes.COLUMN_NAME_NOTE) == false) {
            values.put(NotePad.Notes.COLUMN_NAME_NOTE, "");
        }
        // ★ 新增：自动生成 uuid
        if (!values.containsKey(NotePad.Notes.COLUMN_NAME_UUID)
                || values.getAsString(NotePad.Notes.COLUMN_NAME_UUID) == null
                || values.getAsString(NotePad.Notes.COLUMN_NAME_UUID).length() == 0) {
            values.put(NotePad.Notes.COLUMN_NAME_UUID, UUID.randomUUID().toString());
        }

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        long rowId = db.insert(NotePad.Notes.TABLE_NAME, NotePad.Notes.COLUMN_NAME_NOTE, values);

        if (rowId > 0) {
            Uri noteUri = ContentUris.withAppendedId(NotePad.Notes.CONTENT_ID_URI_BASE, rowId);
            getContext().getContentResolver().notifyChange(noteUri, null);
            return noteUri;
        }

        throw new SQLException("Failed to insert row into " + uri);
    }

    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        String finalWhere;
        int count;

        switch (sUriMatcher.match(uri)) {
            case NOTES:
                count = db.delete(NotePad.Notes.TABLE_NAME, where, whereArgs);
                break;
            case NOTE_ID:
                finalWhere = NotePad.Notes._ID + " = " +
                        uri.getPathSegments().get(NotePad.Notes.NOTE_ID_PATH_POSITION);
                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }
                count = db.delete(NotePad.Notes.TABLE_NAME, finalWhere, whereArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        String finalWhere;

        Long now = Long.valueOf(System.currentTimeMillis());
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, now);

        switch (sUriMatcher.match(uri)) {
            case NOTES:
                count = db.update(NotePad.Notes.TABLE_NAME, values, where, whereArgs);
                break;
            case NOTE_ID:
                finalWhere = NotePad.Notes._ID + " = " +
                        uri.getPathSegments().get(NotePad.Notes.NOTE_ID_PATH_POSITION);
                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }
                count = db.update(NotePad.Notes.TABLE_NAME, values, finalWhere, whereArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    DatabaseHelper getOpenHelperForTest() {
        return mOpenHelper;
    }
}