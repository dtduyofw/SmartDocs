package com.example.smartdocs.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.smartdocs.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class SettingsFragment extends Fragment {

    // Tên file SharedPreferences dùng chung toàn app
    public static final String PREFS_NAME = "smartdocs_prefs";

    // Key lưu Gemini API Key
    public static final String KEY_API_KEY = "gemini_api_key";
    // Key đếm số lần gọi AI trong tháng
    public static final String KEY_AI_COUNT = "ai_count";
    // Key lưu tháng hiện tại để reset counter
    public static final String KEY_AI_MONTH = "ai_month";
    // Giới hạn số lần dùng AI miễn phí mỗi tháng (50 lượt)
    public static final int AI_MAX_LIMIT = 50;

    // Key lưu chế độ giao diện (0: Hệ thống, 1: Sáng, 2: Tối)
    public static final String KEY_THEME_MODE = "theme_mode";
    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;



    // Views
    // Views
    ImageView imgBackSettings, imgSaveSettings;
    TextInputEditText edtApiKey;
    TextView txtAiPlan, txtAiPlanDesc, txtAiUsage;
    ProgressBar pbAiUsage;
    TextView txtGetApiKey;
    LinearLayout layoutToggleAdvanced, layoutAdvancedSettings;
    ImageView imgToggleAdvanced;
    MaterialButton btnSaveSettings;

    // Chế độ giao diện (RadioGroup & RadioButton)
    RadioGroup rdgGiaoDien;
    RadioButton rdSang, rdHeThong, rdToi;

    // Phản hồi & Thông tin
    MaterialButton btnFeedback;
    TextView txtAppVersion;

    SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1. Ánh xạ views
        anhXaView(view);

        // 2. Lấy SharedPreferences
        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // 3. Nạp dữ liệu đã lưu vào UI
        napdDuLieuDaLuu();

        // 4. Gán sự kiện
        ganSuKien();
    }

    void anhXaView(View view) {
        imgBackSettings = (ImageView) view.findViewById(R.id.imgBackSettings);
        imgSaveSettings = (ImageView) view.findViewById(R.id.imgSaveSettings);
        edtApiKey = (TextInputEditText) view.findViewById(R.id.edtApiKey);
        txtAiPlan = (TextView) view.findViewById(R.id.txtAiPlan);
        txtAiPlanDesc = (TextView) view.findViewById(R.id.txtAiPlanDesc);
        txtAiUsage = (TextView) view.findViewById(R.id.txtAiUsage);
        pbAiUsage = (ProgressBar) view.findViewById(R.id.pbAiUsage);
        txtGetApiKey = (TextView) view.findViewById(R.id.txtGetApiKey);
        layoutToggleAdvanced = (LinearLayout) view.findViewById(R.id.layoutToggleAdvanced);
        layoutAdvancedSettings = (LinearLayout) view.findViewById(R.id.layoutAdvancedSettings);
        imgToggleAdvanced = (ImageView) view.findViewById(R.id.imgToggleAdvanced);
        btnSaveSettings = (MaterialButton) view.findViewById(R.id.btnSaveSettings);

        rdgGiaoDien = (RadioGroup) view.findViewById(R.id.rdgGiaoDien);
        rdSang = (RadioButton) view.findViewById(R.id.rdSang);
        rdHeThong = (RadioButton) view.findViewById(R.id.rdHeThong);
        rdToi = (RadioButton) view.findViewById(R.id.rdToi);

        btnFeedback = (MaterialButton) view.findViewById(R.id.btnFeedback);
        txtAppVersion = (TextView) view.findViewById(R.id.txtAppVersion);
    }

    void napdDuLieuDaLuu() {
        if (!isAdded() || getContext() == null) return;
        String apiKey = prefs.getString(KEY_API_KEY, "");
        int aiCount = prefs.getInt(KEY_AI_COUNT, 0);

        edtApiKey.setText(apiKey);
        capNhatTrangThaiAi(apiKey, aiCount);

        int themeMode = prefs.getInt(KEY_THEME_MODE, THEME_SYSTEM);
        if (themeMode == THEME_LIGHT) {
            rdSang.setChecked(true);
        } else if (themeMode == THEME_DARK) {
            rdToi.setChecked(true);
        } else {
            rdHeThong.setChecked(true);
        }
    }

    void capNhatTrangThaiAi(String apiKey, int aiCount) {
        if (!apiKey.isEmpty()) {
            txtAiPlan.setText("Cá nhân (Không giới hạn)");
            txtAiPlanDesc.setText("Đang sử dụng Gemini API Key riêng của bạn. Không giới hạn số lượt gọi AI.");
            txtAiUsage.setText(aiCount + " lần gọi (Vô hạn)");
            pbAiUsage.setMax(100);
            pbAiUsage.setProgress(100);
            layoutAdvancedSettings.setVisibility(View.VISIBLE);
            imgToggleAdvanced.setRotation(180f);
        } else {
            txtAiPlan.setText("Miễn phí (Cơ bản)");
            txtAiPlanDesc.setText("Sẵn sàng sử dụng ngay, tự động làm mới vào đầu mỗi tháng.");
            int conLai = Math.max(0, AI_MAX_LIMIT - aiCount);
            txtAiUsage.setText(conLai + " / " + AI_MAX_LIMIT + " lượt khả dụng");
            pbAiUsage.setMax(AI_MAX_LIMIT);
            pbAiUsage.setProgress(aiCount);
            layoutAdvancedSettings.setVisibility(View.GONE);
            imgToggleAdvanced.setRotation(0f);
        }
    }

    void ganSuKien() {
        imgBackSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).chonTab(R.id.nav_home);
                }
            }
        });

        imgSaveSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                luuCaiDat();
            }
        });

        rdgGiaoDien.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                int modeMoi = THEME_SYSTEM;
                if (checkedId == R.id.rdSang) {
                    modeMoi = THEME_LIGHT;
                } else if (checkedId == R.id.rdToi) {
                    modeMoi = THEME_DARK;
                } else {
                    modeMoi = THEME_SYSTEM;
                }

                int modeHienTai = prefs.getInt(KEY_THEME_MODE, THEME_SYSTEM);
                if (modeMoi != modeHienTai) {
                    prefs.edit().putInt(KEY_THEME_MODE, modeMoi).apply();
                    apDungTheme(modeMoi);
                    if (modeMoi == THEME_LIGHT) {
                        Toast.makeText(requireContext(), "Đã chuyển sang Giao diện Sáng", Toast.LENGTH_SHORT).show();
                    } else if (modeMoi == THEME_DARK) {
                        Toast.makeText(requireContext(), "Đã chuyển sang Giao diện Tối", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "Đã chuyển sang Giao diện Theo hệ thống", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        btnFeedback.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                guiFeedbackGmail();
            }
        });

        layoutToggleAdvanced.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (layoutAdvancedSettings.getVisibility() == View.VISIBLE) {
                    layoutAdvancedSettings.setVisibility(View.GONE);
                    imgToggleAdvanced.setRotation(0f);
                } else {
                    layoutAdvancedSettings.setVisibility(View.VISIBLE);
                    imgToggleAdvanced.setRotation(180f);
                }
            }
        });

        txtGetApiKey.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"));
                    startActivity(browserIntent);
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "Không thể mở trình duyệt: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnSaveSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                luuCaiDat();
            }
        });

        // Easter egg: bấm liên tục 5 lần vào số phiên bản để reset lượt AI (cho Developer/Tester)
        txtAppVersion.setOnClickListener(new View.OnClickListener() {
            int clickCount = 0;
            long lastClickTime = 0;

            @Override
            public void onClick(View v) {
                long now = System.currentTimeMillis();
                if (now - lastClickTime < 1000) {
                    clickCount++;
                    if (clickCount >= 5) {
                        clickCount = 0;
                        prefs.edit().putInt(KEY_AI_COUNT, 0).apply();
                        String apiKey = prefs.getString(KEY_API_KEY, "");
                        capNhatTrangThaiAi(apiKey, 0);
                        Toast.makeText(requireContext(), "Developer: Đã reset bộ đếm AI về 0!", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    clickCount = 1;
                }
                lastClickTime = now;
            }
        });
    }

    void luuCaiDat() {
        String apiKey = edtApiKey.getText() != null ? edtApiKey.getText().toString().trim() : "";

        if (!apiKey.isEmpty() && apiKey.length() < 10) {
            Toast.makeText(requireContext(), "API Key không hợp lệ. Vui lòng kiểm tra lại.", Toast.LENGTH_SHORT).show();
            return;
        }

        int themeMode = THEME_SYSTEM;
        if (rdSang.isChecked()) {
            themeMode = THEME_LIGHT;
        } else if (rdToi.isChecked()) {
            themeMode = THEME_DARK;
        }

        prefs.edit()
                .putString(KEY_API_KEY, apiKey)
                .putInt(KEY_THEME_MODE, themeMode)
                .apply();

        // Áp dụng theme ngay sau khi lưu
        apDungTheme(themeMode);
        Toast.makeText(requireContext(), "Đã lưu cài đặt!", Toast.LENGTH_SHORT).show();
    }

    public void guiFeedbackGmail() {
        String tieuDe = "[SmartDocs] Đóng góp ý kiến & Hỗ trợ";
        String noiDung = "Xin chào nhà phát triển SmartDocs,\n\n"
                + "Tôi muốn gửi phản hồi / đóng góp ý kiến về nội dung sau:\n"
                + "------------------------------------\n"
                + "[Nhập ý kiến đóng góp hoặc mô tả lỗi tại đây...]\n\n"
                + "------------------------------------\n"
                + "Thông tin thiết bị:\n"
                + "- Thiết bị: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + "\n"
                + "- Phiên bản Android: " + android.os.Build.VERSION.RELEASE + " (SDK " + android.os.Build.VERSION.SDK_INT + ")\n"
                + "- Phiên bản ứng dụng: SmartDocs v1.0.0\n";

        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:duydo4857@gmail.com"));
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"duydo4857@gmail.com"});
        intent.putExtra(Intent.EXTRA_SUBJECT, tieuDe);
        intent.putExtra(Intent.EXTRA_TEXT, noiDung);
        intent.setPackage("com.google.android.gm");

        try {
            startActivity(intent);
        } catch (Exception e) {
            Intent fallbackIntent = new Intent(Intent.ACTION_SENDTO);
            fallbackIntent.setData(Uri.parse("mailto:duydo4857@gmail.com"));
            fallbackIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"duydo4857@gmail.com"});
            fallbackIntent.putExtra(Intent.EXTRA_SUBJECT, tieuDe);
            fallbackIntent.putExtra(Intent.EXTRA_TEXT, noiDung);
            try {
                startActivity(Intent.createChooser(fallbackIntent, "Chọn ứng dụng gửi email:"));
            } catch (Exception ex) {
                Toast.makeText(requireContext(), "Không tìm thấy ứng dụng gửi email trên thiết bị!", Toast.LENGTH_SHORT).show();
            }
        }
    }


    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            napdDuLieuDaLuu();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Static Helper Methods (Quản lý Theme & SharedPreferences)
    // ─────────────────────────────────────────────────────────────

    /*
      Áp dụng chế độ giao diện: Sáng, Tối, hoặc Theo hệ thống.
     */
    public static void apDungTheme(int themeMode) {
        if (themeMode == THEME_LIGHT) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        } else if (themeMode == THEME_DARK) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    /*
      Khởi tạo theme khi ứng dụng mở .
     */
    public static void khoiTaoTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int themeMode = prefs.getInt(KEY_THEME_MODE, THEME_SYSTEM);
        apDungTheme(themeMode);
    }

    /**
     * Tăng số lần gọi AI lên 1 (gọi mỗi khi Gemini API trả kết quả thành công).
     * Tự động reset bộ đếm nếu bước sang tháng mới.
     */
    public static void tangAiCount(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        String thangHienTai = cal.get(java.util.Calendar.YEAR) + "-" + cal.get(java.util.Calendar.MONTH);
        String thangLuu = prefs.getString(KEY_AI_MONTH, "");

        int count = prefs.getInt(KEY_AI_COUNT, 0);
        if (!thangHienTai.equals(thangLuu)) {
            count = 0;
        }

        prefs.edit()
                .putInt(KEY_AI_COUNT, count + 1)
                .putString(KEY_AI_MONTH, thangHienTai)
                .apply();
    }

    /**
     * Lấy Gemini API Key đã lưu (nếu chưa cấu hình sẽ fallback về BuildConfig.GEMINI_API_KEY).
     */
    public static String getApiKey(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_API_KEY, "");
        if (saved.isEmpty()) {
            return com.example.smartdocs.BuildConfig.GEMINI_API_KEY;
        }
        return saved;
    }

    /**
     * Kiểm tra người dùng có nhập Custom Gemini API Key hay không.
     */
    public static boolean hasCustomApiKey(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_API_KEY, "");
        return saved != null && !saved.trim().isEmpty();
    }

    /**
     * Lấy số lượt gọi AI đã dùng trong tháng hiện tại.
     */
    public static int getAiCount(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_AI_COUNT, 0);
    }

    /**
     * Kiểm tra còn lượt dùng AI miễn phí không (hoặc không giới hạn nếu có Custom Key).
     */
    public static boolean conLuotDungAi(Context context) {
        if (hasCustomApiKey(context)) {
            return true;
        }
        return getAiCount(context) < AI_MAX_LIMIT;
    }
}
