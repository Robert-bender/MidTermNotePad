package com.example.android.notepad;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.text.TextUtils;

import java.util.UUID;

/** 获取稳定的 deviceId：优先 ANDROID_ID，退化到本地持久化 UUID */
public class DeviceIdUtil {

    private static final String PREF = "notepad_prefs";
    private static final String KEY = "device_id";

    public static String get(Context ctx) {
        // 1. 优先 ANDROID_ID
        try {
            String aid = Settings.Secure.getString(
                    ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (!TextUtils.isEmpty(aid)) {
                return aid;
            }
        } catch (Exception ignored) {
        }

        // 2. 退化：SharedPreferences 持久化 UUID
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String id = sp.getString(KEY, null);
        if (TextUtils.isEmpty(id)) {
            id = UUID.randomUUID().toString();
            sp.edit().putString(KEY, id).commit();
        }
        return id;
    }
}