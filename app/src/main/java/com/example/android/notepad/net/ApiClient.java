package com.example.android.notepad.net;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** 极简 OkHttp 封装：同步调用，调用方自行放到子线程 */
public class ApiClient {

    // ★ 模拟器用 10.0.2.2；真机改成你电脑的局域网 IP，如 http://192.168.1.100:3000/
    public static final String BASE_URL = "http://10.0.2.2:3000/";

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final OkHttpClient CLIENT = new OkHttpClient();

    /** POST JSON，返回响应体字符串（HTTP 非 2xx 抛 IOException） */
    public static String postJson(String path, String jsonBody) throws IOException {
        RequestBody body = RequestBody.create(JSON, jsonBody);
        Request req = new Request.Builder()
                .url(BASE_URL + path)
                .post(body)
                .build();
        Response resp = CLIENT.newCall(req).execute();
        try {
            if (!resp.isSuccessful()) {
                throw new IOException("HTTP " + resp.code() + " " + resp.message());
            }
            return resp.body().string();
        } finally {
            resp.close();
        }
    }

    /** GET，返回响应体字符串 */
    public static String get(String path) throws IOException {
        Request req = new Request.Builder()
                .url(BASE_URL + path)
                .get()
                .build();
        Response resp = CLIENT.newCall(req).execute();
        try {
            if (!resp.isSuccessful()) {
                throw new IOException("HTTP " + resp.code() + " " + resp.message());
            }
            return resp.body().string();
        } finally {
            resp.close();
        }
    }

    /** DELETE，返回响应体字符串 */
    public static String delete(String path) throws IOException {
        Request req = new Request.Builder()
                .url(BASE_URL + path)
                .delete()
                .build();
        Response resp = CLIENT.newCall(req).execute();
        try {
            if (!resp.isSuccessful()) {
                throw new IOException("HTTP " + resp.code() + " " + resp.message());
            }
            return resp.body().string();
        } finally {
            resp.close();
        }
    }
}