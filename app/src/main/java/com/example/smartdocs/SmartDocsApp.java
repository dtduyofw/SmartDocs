package com.example.smartdocs;

import android.app.Application;

import com.example.smartdocs.ui.SettingsFragment;

public class SmartDocsApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Khởi tạo chế độ giao diện (Sáng / Tối / Theo hệ thống) từ cài đặt đã lưu
        SettingsFragment.khoiTaoTheme(this);
    }
}
