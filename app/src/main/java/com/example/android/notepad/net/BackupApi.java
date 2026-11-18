package com.example.android.notepad.net;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.JsonSyntaxException;

import java.util.List;

/** 备份/恢复 API 封装：子线程请求，主线程回调 */
public class BackupApi {

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** 上传备份 */
    public static void backup(final String deviceId, final List<NoteDto> notes,
                              final Callback<BackupResponse> cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BackupRequest req = new BackupRequest(deviceId, notes);
                    String respStr = ApiClient.postJson("api/notes/backup",
                            JsonUtil.GSON.toJson(req));
                    final BackupResponse resp = JsonUtil.GSON.fromJson(respStr, BackupResponse.class);
                    MAIN.post(new Runnable() {
                        @Override public void run() { cb.onSuccess(resp); }
                    });
                } catch (final Exception e) {
                    MAIN.post(new Runnable() {
                        @Override public void run() { cb.onError(e); }
                    });
                }
            }
        }, "backup-thread").start();
    }

    /** 拉取云端备份 */
    public static void restore(final String deviceId,
                               final Callback<RestoreResponse> cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String respStr = ApiClient.get("api/notes/restore?deviceId=" + deviceId);
                    final RestoreResponse resp = JsonUtil.GSON.fromJson(respStr, RestoreResponse.class);
                    MAIN.post(new Runnable() {
                        @Override public void run() { cb.onSuccess(resp); }
                    });
                } catch (final Exception e) {
                    MAIN.post(new Runnable() {
                        @Override public void run() { cb.onError(e); }
                    });
                }
            }
        }, "restore-thread").start();
    }

    /** 删除云端单条备份（按 uuid）。无论服务器有没有这条都会返回成功语义。 */
    public static void deleteRemote(final String deviceId, final String uuid,
                                    final Callback<DeleteResponse> cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String path = "api/notes/backup/" + uuid + "?deviceId=" + deviceId;
                    String respStr = ApiClient.delete(path);
                    final DeleteResponse resp = JsonUtil.GSON.fromJson(respStr, DeleteResponse.class);
                    MAIN.post(new Runnable() {
                        @Override public void run() { cb.onSuccess(resp); }
                    });
                } catch (final Exception e) {
                    MAIN.post(new Runnable() {
                        @Override public void run() { cb.onError(e); }
                    });
                }
            }
        }, "delete-thread").start();
    }
}