package com.example.smartdocs.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.smartdocs.R;
import com.example.smartdocs.data.QLTaiKhoan;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

public class SettingsFragment extends Fragment {

    // Ten file SharedPreferences dung chung toan app
    public static final String PREFS_NAME = "smartdocs_prefs";

    // Key luu Gemini API Key (cho tuong thich he thong)
    public static final String KEY_API_KEY = "gemini_api_key";
    // Key dem so lan goi AI trong thang
    public static final String KEY_AI_COUNT = "ai_count";
    // Key luu thang hien tai de reset counter
    public static final String KEY_AI_MONTH = "ai_month";
    // Gioi han so lan dung AI mac dinh (50 luot)
    public static final int AI_MAX_LIMIT = 50;

    // Key luu che do giao dien (0: He thong, 1: Sang, 2: Toi)
    public static final String KEY_THEME_MODE = "theme_mode";
    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    // Views
    ImageView imgBackSettings, imgSaveSettings;
    TextView txtUserEmail, txtUserUid;
    TextView txtSettingsAvatarChar;
    MaterialCardView cardProfileHeader;
    TextView txtAiPlan;
    View btnAuthAction, btnMoProfile, btnXoaTaiKhoan;
    TextView txtAuthActionText;
    View cardLanguageSetting;
    androidx.appcompat.widget.SwitchCompat swBiometric;
    boolean dangCapNhatSwitchBio = false;

    // Phan hoi & Thong tin
    View btnFeedback;
    TextView txtAppVersion;

    SharedPreferences prefs;
    QLTaiKhoan qlTaiKhoan;

    ActivityResultLauncher<Intent> googleReauthLauncher;
    GoogleSignInClient mGoogleSignInClient;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Khoi tao Google Sign In Client phuc vu xac thuc lai khi xoa tai khoan Google
        String webClientId = getString(R.string.default_web_client_id);
        GoogleSignInOptions.Builder gsoBuilder = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail();
        if (webClientId != null && !webClientId.trim().isEmpty()) {
            gsoBuilder.requestIdToken(webClientId);
        }
        if (getContext() != null) {
            mGoogleSignInClient = GoogleSignIn.getClient(requireContext(), gsoBuilder.build());
        }

        // Launcher nhan ket qua xac thuc lai bang Google
        googleReauthLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                            try {
                                GoogleSignInAccount account = task.getResult(ApiException.class);
                                if (account != null && account.getIdToken() != null) {
                                    thucHienXoaGoogleVoiToken(account.getIdToken());
                                } else {
                                    Toast.makeText(requireContext(), "Không lấy được mã xác thực Google.", Toast.LENGTH_SHORT).show();
                                }
                            } catch (ApiException e) {
                                Toast.makeText(requireContext(), "Xác thực Google thất bại: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1. Khoi tao QLTaiKhoan
        qlTaiKhoan = QLTaiKhoan.getInstance(requireContext());

        // 2. Anh xa views
        anhXaView(view);

        // 3. Lay SharedPreferences
        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // 4. Nap du lieu da luu vao UI
        napdDuLieuDaLuu();

        // 5. Gan su kien
        ganSuKien();
    }

    void anhXaView(View view) {
        imgBackSettings = (ImageView) view.findViewById(R.id.imgBackSettings);
        imgSaveSettings = (ImageView) view.findViewById(R.id.imgSaveSettings);

        cardProfileHeader = (MaterialCardView) view.findViewById(R.id.cardProfileHeader);
        txtSettingsAvatarChar = (TextView) view.findViewById(R.id.txtSettingsAvatarChar);
        txtUserEmail = (TextView) view.findViewById(R.id.txtUserEmail);
        txtUserUid = (TextView) view.findViewById(R.id.txtUserUid);
        btnAuthAction = view.findViewById(R.id.btnAuthAction);
        txtAuthActionText = (TextView) view.findViewById(R.id.txtAuthActionText);
        btnMoProfile = view.findViewById(R.id.btnMoProfile);

        txtAiPlan = (TextView) view.findViewById(R.id.txtAiPlan);
        btnXoaTaiKhoan = view.findViewById(R.id.btnXoaTaiKhoan);

        cardLanguageSetting = view.findViewById(R.id.cardLanguageSetting);
        swBiometric = (androidx.appcompat.widget.SwitchCompat) view.findViewById(R.id.swBiometric);

        btnFeedback = view.findViewById(R.id.btnFeedback);
        txtAppVersion = (TextView) view.findViewById(R.id.txtAppVersion);
    }

    void napdDuLieuDaLuu() {
        if (!isAdded() || getContext() == null) return;

        // Cap nhat thong tin tai khoan & goi AI
        capNhatGiaoDienTaiKhoan();

        // Nap trang thai khoa sinh trac hoc da luu
        dangCapNhatSwitchBio = true;
        if (swBiometric != null) {
            swBiometric.setChecked(BiometricHelper.daBatSinhTracHoc(requireContext()));
        }
        dangCapNhatSwitchBio = false;
    }

    public void capNhatGiaoDienTaiKhoan() {
        if (!isAdded() || getContext() == null || qlTaiKhoan == null) return;

        if (qlTaiKhoan.daDangNhap()) {
            txtUserEmail.setText(qlTaiKhoan.getEmail());
            txtUserUid.setText("Mã nạp: " + qlTaiKhoan.getShortUid());
            if (txtAuthActionText != null) txtAuthActionText.setText("Đăng xuất");
            if (txtSettingsAvatarChar != null && qlTaiKhoan.getEmail() != null && !qlTaiKhoan.getEmail().isEmpty()) {
                txtSettingsAvatarChar.setText(qlTaiKhoan.getEmail().substring(0, 1).toUpperCase());
            }
            if (btnXoaTaiKhoan != null) btnXoaTaiKhoan.setVisibility(View.VISIBLE);
        } else {
            txtUserEmail.setText("Tài khoản khách");
            txtUserUid.setText("Mã nạp: Chưa đăng nhập");
            if (txtAuthActionText != null) txtAuthActionText.setText("Đăng nhập");
            if (txtSettingsAvatarChar != null) {
                txtSettingsAvatarChar.setText("K");
            }
            if (btnXoaTaiKhoan != null) btnXoaTaiKhoan.setVisibility(View.GONE);
        }

        String loaiGoi = qlTaiKhoan.getLoaiGoi();
        if ("VIP".equalsIgnoreCase(loaiGoi)) {
            txtAiPlan.setText("Gói VIP (Không giới hạn)");
        } else if ("PRO".equalsIgnoreCase(loaiGoi)) {
            txtAiPlan.setText("Pro");
        } else if ("PLUS".equalsIgnoreCase(loaiGoi)) {
            txtAiPlan.setText("Plus");
        } else {
            txtAiPlan.setText("Miễn phí");
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

        // Su kien nut Dang nhap / Dang xuat
        if (btnAuthAction != null) {
            btnAuthAction.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (qlTaiKhoan.daDangNhap()) {
                        DialogHelper.showConfirm(requireContext(), "Đăng xuất", "Bạn có chắc chắn muốn đăng xuất tài khoản AI?", "Đăng xuất", new DialogHelper.OnConfirm() {
                            @Override
                            public void onConfirm() {
                                qlTaiKhoan.dangXuat();
                                capNhatGiaoDienTaiKhoan();
                                Toast.makeText(requireContext(), "Đã đăng xuất tài khoản.", Toast.LENGTH_SHORT).show();
                                if (getActivity() instanceof MainActivity) {
                                    ((MainActivity) getActivity()).chonTab(R.id.nav_home);
                                }
                            }
                        });
                    } else {
                        hienDialogDangNhap();
                    }
                }
            });
        }

        // Su kien mo Profile khi cham vao Profile Card hoac nut mui ten
        View.OnClickListener moProfileListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(requireContext(), ProfileActivity.class);
                startActivity(i);
            }
        };

        if (cardProfileHeader != null) {
            cardProfileHeader.setOnClickListener(moProfileListener);
        }

        if (btnMoProfile != null) {
            btnMoProfile.setOnClickListener(moProfileListener);
        }

        // Su kien ngon ngu
        if (cardLanguageSetting != null) {
            cardLanguageSetting.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(requireContext(), "Ứng dụng đang hỗ trợ Tiếng Việt", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Su kien toggle sinh trac hoc
        if (swBiometric != null) {
            swBiometric.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(final android.widget.CompoundButton buttonView, boolean isChecked) {
                    if (dangCapNhatSwitchBio) return;

                    if (isChecked) {
                        // Kiem tra thiet bi co ho tro sinh trac hoc khong
                        if (!BiometricHelper.coHoTroSinhTracHoc(requireContext())) {
                            Toast.makeText(requireContext(), "Thiết bị chưa cài đặt hoặc không hỗ trợ vân tay / khuôn mặt.", Toast.LENGTH_LONG).show();
                            dangCapNhatSwitchBio = true;
                            buttonView.setChecked(false);
                            dangCapNhatSwitchBio = false;
                            return;
                        }

                        // Yeu cau xac thuc truoc khi bat
                        BiometricHelper.xacThuc(requireActivity(), "Kích hoạt khóa bảo vệ", "Xác thực vân tay hoặc khuôn mặt để kích hoạt", new BiometricHelper.BiometricCallback() {
                            @Override
                            public void onSuccess() {
                                BiometricHelper.datTrangThaiSinhTracHoc(requireContext(), true);
                                Toast.makeText(requireContext(), "Đã bật khóa bằng vân tay / khuôn mặt.", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onError(String message) {
                                Toast.makeText(requireContext(), "Kích hoạt thất bại: " + message, Toast.LENGTH_SHORT).show();
                                dangCapNhatSwitchBio = true;
                                buttonView.setChecked(false);
                                dangCapNhatSwitchBio = false;
                            }

                            @Override
                            public void onCancel() {
                                dangCapNhatSwitchBio = true;
                                buttonView.setChecked(false);
                                dangCapNhatSwitchBio = false;
                            }
                        });
                    } else {
                        // Tat khoa sinh trac hoc
                        BiometricHelper.datTrangThaiSinhTracHoc(requireContext(), false);
                        Toast.makeText(requireContext(), "Đã tắt khóa bằng sinh trắc học.", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        // Nut Xoa tai khoan vinh vien
        if (btnXoaTaiKhoan != null) {
            btnXoaTaiKhoan.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!qlTaiKhoan.daDangNhap()) {
                        Toast.makeText(requireContext(), "Bạn chưa đăng nhập tài khoản.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    xacNhanXoaTaiKhoan();
                }
            });
        }

        btnFeedback.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                guiFeedbackGmail();
            }
        });

        // Easter egg: bam 5 lan vao phien ban de reset bo dem
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
                        qlTaiKhoan.nangCapDevMode("FREE", 50, null);
                        capNhatGiaoDienTaiKhoan();
                        Toast.makeText(requireContext(), "Developer: Đã reset bộ đếm AI!", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    clickCount = 1;
                }
                lastClickTime = now;
            }
        });
    }

    void luuCaiDat() {
        Toast.makeText(requireContext(), "Đã lưu cài đặt!", Toast.LENGTH_SHORT).show();
    }

    // Dialog Dang nhap
    // Mo man hinh dang nhap (Activity rieng thay the dialog cu)
    public void hienDialogDangNhap() {
        if (!isAdded() || getContext() == null) return;
        Intent i = new Intent(requireContext(), DangNhapActivity.class);
        startActivityForResult(i, 1001);
    }

    // Mo man hinh dang ky (chuyen thang sang tab dang ky)
    public void hienDialogDangKy() {
        if (!isAdded() || getContext() == null) return;
        Intent i = new Intent(requireContext(), DangNhapActivity.class);
        i.putExtra(DangNhapActivity.EXTRA_MODE, DangNhapActivity.MODE_DANG_KY);
        startActivityForResult(i, 1001);
    }

    // Nhan ket qua tu DangNhapActivity khi dang nhap / dang ky thanh cong
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == android.app.Activity.RESULT_OK) {
            // Cap nhat lai giao dien sau khi dang nhap / dang ky thanh cong
            capNhatGiaoDienTaiKhoan();
        }
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

    // Dialog xac nhan va thuc hien xoa vinh vien tai khoan tren Firebase
    void xacNhanXoaTaiKhoan() {
        if (getContext() == null || !isAdded()) return;

        final String userEmail = qlTaiKhoan.getEmail();

        // 1. Neu la tai khoan dang nhap bang Email va Mat khau
        if (qlTaiKhoan.laTaiKhoanMatKhau()) {
            String message = "Tài khoản (" + userEmail + ") và toàn bộ dữ liệu gói AI trên hệ thống Firebase sẽ bị xóa vĩnh viễn và không thể khôi phục.";
            DialogHelper.showPasswordDelete(requireContext(), "Xóa tài khoản vĩnh viễn", message, "Nhập mật khẩu tài khoản", new DialogHelper.OnInputConfirm() {
                @Override
                public void onConfirm(String matKhau) {
                    if (matKhau.isEmpty()) {
                        Toast.makeText(requireContext(), "Vui lòng nhập mật khẩu để xác nhận xóa tài khoản.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Toast.makeText(requireContext(), "Đang xác thực và xóa tài khoản trên Firebase...", Toast.LENGTH_SHORT).show();
                    qlTaiKhoan.xoaTaiKhoanVinhVienVoiMatKhau(matKhau, new QLTaiKhoan.AuthCallback() {
                        @Override
                        public void onSuccess(final String thongBao) {
                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        capNhatGiaoDienTaiKhoan();
                                        Toast.makeText(requireContext(), thongBao, Toast.LENGTH_LONG).show();
                                        if (getActivity() instanceof MainActivity) {
                                            ((MainActivity) getActivity()).chonTab(R.id.nav_home);
                                        }
                                    }
                                });
                            }
                        }

                        @Override
                        public void onError(final String loi) {
                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(requireContext(), "Lỗi xóa tài khoản: " + loi, Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        }
                    });
                }
            });
            return;
        }

        // 2. Neu la tai khoan dang nhap bang Google hoac cac phuong thuc khac
        String message = "Bạn có chắc chắn muốn xóa vĩnh viễn tài khoản (" + userEmail + ") khỏi hệ thống Firebase?\n\n"
                + "Toàn bộ dữ liệu gói dịch vụ AI, số lượt sử dụng và hồ sơ sẽ bị xóa vĩnh viễn và không thể khôi phục.";

        DialogHelper.showDelete(requireContext(), "Xóa tài khoản vĩnh viễn", message, new DialogHelper.OnConfirm() {
            @Override
            public void onConfirm() {
                Toast.makeText(requireContext(), "Đang tiến hành xóa tài khoản trên Firebase...", Toast.LENGTH_SHORT).show();

                qlTaiKhoan.xoaTaiKhoanVinhVien(new QLTaiKhoan.AuthCallback() {
                    @Override
                    public void onSuccess(final String thongBao) {
                        if (getActivity() != null && isAdded()) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    capNhatGiaoDienTaiKhoan();
                                    Toast.makeText(requireContext(), thongBao, Toast.LENGTH_LONG).show();
                                    if (getActivity() instanceof MainActivity) {
                                        ((MainActivity) getActivity()).chonTab(R.id.nav_home);
                                    }
                                }
                            });
                        }
                    }

                    @Override
                    public void onError(final String loi) {
                        if (getActivity() != null && isAdded()) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if ("REQUIRES_RECENT_LOGIN".equals(loi)) {
                                        if (qlTaiKhoan.laTaiKhoanGoogle()) {
                                            yeuCauXacThucLaiGoogle();
                                        } else {
                                            Toast.makeText(requireContext(), "Vì lý do bảo mật, vui lòng đăng nhập lại trước khi xóa tài khoản.", Toast.LENGTH_LONG).show();
                                        }
                                    } else {
                                        Toast.makeText(requireContext(), "Lỗi xóa tài khoản: " + loi, Toast.LENGTH_LONG).show();
                                    }
                                }
                            });
                        }
                    }
                });
            }
        });
    }

    // Yeu cau xac thuc lai bang tai khoan Google truoc khi xoa
    void yeuCauXacThucLaiGoogle() {
        if (mGoogleSignInClient == null) {
            String webClientId = getString(R.string.default_web_client_id);
            GoogleSignInOptions.Builder gsoBuilder = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail();
            if (webClientId != null && !webClientId.trim().isEmpty()) {
                gsoBuilder.requestIdToken(webClientId);
            }
            mGoogleSignInClient = GoogleSignIn.getClient(requireContext(), gsoBuilder.build());
        }

        Toast.makeText(requireContext(), "Vui lòng chọn tài khoản Google để xác thực xóa vĩnh viễn.", Toast.LENGTH_SHORT).show();
        mGoogleSignInClient.signOut().addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                Intent signInIntent = mGoogleSignInClient.getSignInIntent();
                googleReauthLauncher.launch(signInIntent);
            }
        });
    }

    // Thuc hien xoa tai khoan Google sau khi da xac thuc lai
    void thucHienXoaGoogleVoiToken(String idToken) {
        Toast.makeText(requireContext(), "Đang tiến hành xóa tài khoản Google trên Firebase...", Toast.LENGTH_SHORT).show();
        qlTaiKhoan.xoaTaiKhoanVinhVienVoiGoogle(idToken, new QLTaiKhoan.AuthCallback() {
            @Override
            public void onSuccess(final String thongBao) {
                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            capNhatGiaoDienTaiKhoan();
                            Toast.makeText(requireContext(), thongBao, Toast.LENGTH_LONG).show();
                            if (getActivity() instanceof MainActivity) {
                                ((MainActivity) getActivity()).chonTab(R.id.nav_home);
                            }
                        }
                    });
                }
            }

            @Override
            public void onError(final String loi) {
                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(requireContext(), "Lỗi xóa tài khoản Google: " + loi, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        capNhatGiaoDienTaiKhoan();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            capNhatGiaoDienTaiKhoan();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    // ─────────────────────────────────────────────────────────────
    // Static Helper Methods (Quan ly Theme & Quota AI)
    // ─────────────────────────────────────────────────────────────

    public static void apDungTheme(int themeMode) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
    }

    public static void khoiTaoTheme(Context context) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
    }

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

    public static String getApiKey(Context context) {
        return com.example.smartdocs.BuildConfig.GEMINI_API_KEY;
    }

    public static boolean hasCustomApiKey(Context context) {
        return false;
    }

    public static int getAiCount(Context context) {
        return QLTaiKhoan.getInstance(context).getDaNiemDung();
    }

    public static boolean conLuotDungAi(Context context) {
        QLTaiKhoan ql = QLTaiKhoan.getInstance(context);
        return ql.daDangNhap() && ql.kiemTraConLuotAi();
    }
}
