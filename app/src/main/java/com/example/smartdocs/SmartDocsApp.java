package com.example.smartdocs;

import android.app.Application;

import com.example.smartdocs.ui.SettingsFragment;

public class SmartDocsApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Khoi tao che do toi mac dinh cho toan bo ung dung
        SettingsFragment.khoiTaoTheme(this);
    }
}
