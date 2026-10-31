package com.example.smartdocs.ui;

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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.smartdocs.R;
import com.example.smartdocs.data.QLTaiKhoan;
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
    TextView txtAiPlan, txtAiPlanDesc, txtAiUsage, txtAiExpiry;
    ProgressBar pbAiUsage;
    MaterialButton btnAuthAction, btnNangCapGoi, btnDongBoAi;
    MaterialButton btnSaveSettings;

    // Che do giao dien (RadioGroup & RadioButton)
    RadioGroup rdgGiaoDien;
    RadioButton rdSang, rdHeThong, rdToi;

    // Phan hoi & Thong tin
    MaterialButton btnFeedback;
    TextView txtAppVersion;

    SharedPreferences prefs;
    QLTaiKhoan qlTaiKhoan;
    Dialog dialogNangCapHienTai;

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

        // 6. Lang nghe real-time tu Firestore: khi webhook nang cap thi tu dong cap nhat UI
        batDauLangNgheNangCap();
    }

    // Khoi dong lang nghe real-time Firestore sau khi dang nhap
    void batDauLangNgheNangCap() {
        if (!qlTaiKhoan.daDangNhap()) return;

        qlTaiKhoan.batDauLangNgheNangCap(new QLTaiKhoan.UpgradeCallback() {
            @Override
            public void onUpgradeDetected(final String loaiGoiMoi, final int tongLuotMoi) {
                if (getActivity() == null || !isAdded()) return;
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        capNhatGiaoDienTaiKhoan();
                        // Dong trang QR neu dang mo va mo Dialog thong bao thanh cong
                        if (dialogNangCapHienTai != null && dialogNangCapHienTai.isShowing()) {
                            dialogNangCapHienTai.dismiss();
                            dialogNangCapHienTai = null;
                        }
                        hienDialogNangCapThanhCong(loaiGoiMoi, tongLuotMoi);
                    }
                });
            }
        });
    }

    void anhXaView(View view) {
        imgBackSettings = (ImageView) view.findViewById(R.id.imgBackSettings);
        imgSaveSettings = (ImageView) view.findViewById(R.id.imgSaveSettings);

        txtUserEmail = (TextView) view.findViewById(R.id.txtUserEmail);
        txtUserUid = (TextView) view.findViewById(R.id.txtUserUid);
        btnAuthAction = (MaterialButton) view.findViewById(R.id.btnAuthAction);

        txtAiPlan = (TextView) view.findViewById(R.id.txtAiPlan);
        txtAiPlanDesc = (TextView) view.findViewById(R.id.txtAiPlanDesc);
        txtAiUsage = (TextView) view.findViewById(R.id.txtAiUsage);
        txtAiExpiry = (TextView) view.findViewById(R.id.txtAiExpiry);
        pbAiUsage = (ProgressBar) view.findViewById(R.id.pbAiUsage);
        btnNangCapGoi = (MaterialButton) view.findViewById(R.id.btnNangCapGoi);
        btnDongBoAi = (MaterialButton) view.findViewById(R.id.btnDongBoAi);

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

        // Cap nhat thong tin tai khoan & goi AI
        capNhatGiaoDienTaiKhoan();

        int themeMode = prefs.getInt(KEY_THEME_MODE, THEME_SYSTEM);
        if (themeMode == THEME_LIGHT) {
            rdSang.setChecked(true);
        } else if (themeMode == THEME_DARK) {
            rdToi.setChecked(true);
        } else {
            rdHeThong.setChecked(true);
        }
    }

    public void capNhatGiaoDienTaiKhoan() {
        if (!isAdded() || getContext() == null || qlTaiKhoan == null) return;

        if (qlTaiKhoan.daDangNhap()) {
            txtUserEmail.setText(qlTaiKhoan.getEmail());
            txtUserUid.setText("Mã nạp: " + qlTaiKhoan.getShortUid());
            btnAuthAction.setText("Đăng xuất");
        } else {
            txtUserEmail.setText("Tài khoản khách");
            txtUserUid.setText("Mã nạp: Chưa đăng nhập");
            btnAuthAction.setText("Đăng nhập");
        }

        String loaiGoi = qlTaiKhoan.getLoaiGoi();
        int daDung = qlTaiKhoan.getDaNiemDung();
        int tongLuot = qlTaiKhoan.getTongLuot();
        int conLai = qlTaiKhoan.getLuotConLai();
        String chuoiNgay = qlTaiKhoan.getChuoiNgayConLai();

        if (txtAiExpiry != null) {
            txtAiExpiry.setText(chuoiNgay);
        }

        if ("VIP".equalsIgnoreCase(loaiGoi)) {
            txtAiPlan.setText("Gói VIP (Không giới hạn)");
            txtAiPlanDesc.setText("Bạn đang sở hữu gói VIP. Sử dụng AI không giới hạn số lượt.");
            txtAiUsage.setText("Không giới hạn (" + chuoiNgay + ")");
            pbAiUsage.setMax(100);
            pbAiUsage.setProgress(100);
        } else if ("PRO".equalsIgnoreCase(loaiGoi)) {
            txtAiPlan.setText("Siêu Cấp Pro");
            txtAiPlanDesc.setText("Gói nâng cấp AI tốc độ cao.");
            txtAiUsage.setText(conLai + " / " + tongLuot + " lượt (" + chuoiNgay + ")");
            pbAiUsage.setMax(tongLuot > 0 ? tongLuot : 500);
            pbAiUsage.setProgress(Math.min(daDung, pbAiUsage.getMax()));
        } else if ("PLUS".equalsIgnoreCase(loaiGoi)) {
            txtAiPlan.setText("Nâng Cấp Plus");
            txtAiPlanDesc.setText("Gói nâng cấp lượt AI cộng thêm.");
            txtAiUsage.setText(conLai + " / " + tongLuot + " lượt (" + chuoiNgay + ")");
            pbAiUsage.setMax(tongLuot > 0 ? tongLuot : 200);
            pbAiUsage.setProgress(Math.min(daDung, pbAiUsage.getMax()));
        } else {
            txtAiPlan.setText("Miễn phí (Cơ bản)");
            txtAiPlanDesc.setText("Sẵn sàng sử dụng ngay, tự động làm mới vào đầu mỗi tháng.");
            txtAiUsage.setText(conLai + " / " + tongLuot + " lượt (" + chuoiNgay + ")");
            pbAiUsage.setMax(tongLuot > 0 ? tongLuot : 50);
            pbAiUsage.setProgress(Math.min(daDung, pbAiUsage.getMax()));
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

        btnSaveSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                luuCaiDat();
            }
        });

        // Su kien nut Dang nhap / Dang xuat
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
                        }
                    });
                } else {
                    hienDialogDangNhap();
                }
            }
        });

        // Nut Nang cap goi
        btnNangCapGoi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!qlTaiKhoan.daDangNhap()) {
                    Toast.makeText(requireContext(), "Vui lòng đăng nhập tài khoản trước khi nâng cấp gói AI.", Toast.LENGTH_SHORT).show();
                    hienDialogDangNhap();
                    return;
                }
                hienDialogNangCapGoi();
            }
        });

        // Nut Dong bo tu may chu Firestore
        btnDongBoAi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!qlTaiKhoan.daDangNhap()) {
                    Toast.makeText(requireContext(), "Vui lòng đăng nhập để đồng bộ dữ liệu từ máy chủ.", Toast.LENGTH_SHORT).show();
                    hienDialogDangNhap();
                    return;
                }

                final String oldGoi = qlTaiKhoan.getLoaiGoi();
                final int oldTongLuot = qlTaiKhoan.getTongLuot();

                Toast.makeText(requireContext(), "Đang kiểm tra dữ liệu từ máy chủ...", Toast.LENGTH_SHORT).show();
                qlTaiKhoan.dongBoTuFirestore(new QLTaiKhoan.SyncCallback() {
                    @Override
                    public void onSynced() {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    capNhatGiaoDienTaiKhoan();
                                    boolean daNangCap = !oldGoi.equalsIgnoreCase(qlTaiKhoan.getLoaiGoi())
                                            || qlTaiKhoan.getTongLuot() > oldTongLuot;
                                    if (daNangCap) {
                                        if (dialogNangCapHienTai != null && dialogNangCapHienTai.isShowing()) {
                                            dialogNangCapHienTai.dismiss();
                                            dialogNangCapHienTai = null;
                                        }
                                        hienDialogNangCapThanhCong(qlTaiKhoan.getLoaiGoi(), qlTaiKhoan.getTongLuot());
                                    } else {
                                        Toast.makeText(requireContext(), "Đã đồng bộ số lượt AI mới nhất thành công!", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                        }
                    }

                    @Override
                    public void onError(final String loi) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(requireContext(), "Lỗi đồng bộ: " + loi, Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }
                });
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
        int themeMode = THEME_SYSTEM;
        if (rdSang.isChecked()) {
            themeMode = THEME_LIGHT;
        } else if (rdToi.isChecked()) {
            themeMode = THEME_DARK;
        }

        prefs.edit().putInt(KEY_THEME_MODE, themeMode).apply();
        apDungTheme(themeMode);
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
            // Khoi dong listener real-time sau khi dang nhap
            batDauLangNgheNangCap();
        }
    }

    // Dialog Nang cap goi & Ma VietQR
    public void hienDialogNangCapGoi() {
        if (!isAdded() || getContext() == null) return;

        // Chi cho phep nguoi dung da dang nhap nang cap goi AI
        if (!qlTaiKhoan.daDangNhap()) {
            Toast.makeText(requireContext(), "Vui lòng đăng nhập tài khoản trước khi nâng cấp gói AI.", Toast.LENGTH_SHORT).show();
            hienDialogDangNhap();
            return;
        }

        final Dialog dialog = DialogHelper.taoDialog(requireContext());
        dialogNangCapHienTai = dialog;
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_nang_cap_goi, null);
        dialog.setContentView(view);

        // [RF-3 FIX]: Co kiem soat vong doi de cancel download khi dong dialog
        final AtomicBoolean isDialogActive = new AtomicBoolean(true);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                isDialogActive.set(false);
                if (dialogNangCapHienTai == dialog) {
                    dialogNangCapHienTai = null;
                }
            }
        });

        // Phai set lai layout sau setContentView vi setContentView reset kich thuoc window
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                (int) (requireContext().getResources().getDisplayMetrics().widthPixels * 0.92f),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        final MaterialCardView cardGoiPlus = (MaterialCardView) view.findViewById(R.id.cardGoiPlus);
        final MaterialCardView cardGoiPro = (MaterialCardView) view.findViewById(R.id.cardGoiPro);
        final MaterialCardView cardGoiVip = (MaterialCardView) view.findViewById(R.id.cardGoiVip);

        final TextView tabKy1Thang = (TextView) view.findViewById(R.id.tabKy1Thang);
        final TextView tabKy3Thang = (TextView) view.findViewById(R.id.tabKy3Thang);
        final TextView tabKy6Thang = (TextView) view.findViewById(R.id.tabKy6Thang);
        final TextView tabKy1Nam = (TextView) view.findViewById(R.id.tabKy1Nam);

        final TextView txtGoiPlusGia = (TextView) view.findViewById(R.id.txtGoiPlusGia);
        final TextView txtGoiPlusLuot = (TextView) view.findViewById(R.id.txtGoiPlusLuot);
        final TextView txtGoiProGia = (TextView) view.findViewById(R.id.txtGoiProGia);
        final TextView txtGoiProLuot = (TextView) view.findViewById(R.id.txtGoiProLuot);
        final TextView txtGoiVipGia = (TextView) view.findViewById(R.id.txtGoiVipGia);
        final TextView txtGoiVipLuot = (TextView) view.findViewById(R.id.txtGoiVipLuot);

        final ImageView imgVietQr = (ImageView) view.findViewById(R.id.imgVietQr);
        final ProgressBar pbQrLoading = (ProgressBar) view.findViewById(R.id.pbQrLoading);

        final TextView txtNganHang = (TextView) view.findViewById(R.id.txtNganHang);
        final TextView txtStk = (TextView) view.findViewById(R.id.txtStk);
        final TextView txtChuTk = (TextView) view.findViewById(R.id.txtChuTk);
        final TextView txtSoTien = (TextView) view.findViewById(R.id.txtSoTien);
        final TextView txtNoiDung = (TextView) view.findViewById(R.id.txtNoiDung);

        ImageView btnCopyStk = (ImageView) view.findViewById(R.id.btnCopyStk);
        ImageView btnCopySoTien = (ImageView) view.findViewById(R.id.btnCopySoTien);
        ImageView btnCopyNoiDung = (ImageView) view.findViewById(R.id.btnCopyNoiDung);

        MaterialButton btnKiemTraThanhToan = (MaterialButton) view.findViewById(R.id.btnKiemTraThanhToan);
        MaterialButton btnDevTest = (MaterialButton) view.findViewById(R.id.btnDevTest);
        TextView btnDong = (TextView) view.findViewById(R.id.btnDongNangCap);

        // Gia tri mac dinh
        final String shortUid = qlTaiKhoan.getShortUid();
        final int[] currentChuKy = {1}; // 1 = 1 thang, 3 = 3 thang, 6 = 6 thang, 12 = 1 nam
        final String[] currentLoaiGoi = {"PLUS"};
        final long[] currentGia = {29000};
        final int[] currentLuot = {200};

        // Ham cap nhat QR khi chon goi
        final Runnable capNhatQr = new Runnable() {
            @Override
            public void run() {
                txtSoTien.setText(String.format("%,d đ", currentGia[0]));
                String noiDungCk = "VIP".equals(currentLoaiGoi[0]) ? "NAP VIP " + shortUid : "NAP AI " + shortUid;
                txtNoiDung.setText(noiDungCk);

                String urlQr = QLTaiKhoan.taoUrlVietQr(
                        QLTaiKhoan.NGAN_HANG_DEFAULT,
                        QLTaiKhoan.STK_DEFAULT,
                        QLTaiKhoan.CHU_TK_DEFAULT,
                        currentGia[0],
                        noiDungCk
                );
                taiAnhVietQr(imgVietQr, pbQrLoading, urlQr, isDialogActive);
            }
        };

        // Mau sac va do day vien cho trang thai chon goi
        final int mauPrimary = ContextCompat.getColor(requireContext(), R.color.primary);
        final int mauDivider = ContextCompat.getColor(requireContext(), R.color.divider);
        final int mauNenChon = ContextCompat.getColor(requireContext(), R.color.badge_ai_bg);
        final int mauNenTrang = ContextCompat.getColor(requireContext(), R.color.surface);
        final float density = requireContext().getResources().getDisplayMetrics().density;
        final int strokeChon = (int) (2 * density);
        final int strokeThuong = (int) (1 * density);

        // Ham cap nhat hieu ung sang the goi duoc chon
        final Runnable capNhatGiaoDienChonGoi = new Runnable() {
            @Override
            public void run() {
                boolean isPlus = "PLUS".equals(currentLoaiGoi[0]);
                boolean isPro = "PRO".equals(currentLoaiGoi[0]);
                boolean isVip = "VIP".equals(currentLoaiGoi[0]);

                // Goi Plus
                cardGoiPlus.setStrokeColor(isPlus ? mauPrimary : mauDivider);
                cardGoiPlus.setStrokeWidth(isPlus ? strokeChon : strokeThuong);
                cardGoiPlus.setCardBackgroundColor(isPlus ? mauNenChon : mauNenTrang);

                // Goi Pro
                cardGoiPro.setStrokeColor(isPro ? mauPrimary : mauDivider);
                cardGoiPro.setStrokeWidth(isPro ? strokeChon : strokeThuong);
                cardGoiPro.setCardBackgroundColor(isPro ? mauNenChon : mauNenTrang);

                // Goi VIP
                cardGoiVip.setStrokeColor(isVip ? mauPrimary : mauDivider);
                cardGoiVip.setStrokeWidth(isVip ? strokeChon : strokeThuong);
                cardGoiVip.setCardBackgroundColor(isVip ? mauNenChon : mauNenTrang);
            }
        };

        // Ham cap nhat chu ky va thong so cac goi tuong ung
        final Runnable capNhatChuKyVaGia = new Runnable() {
            @Override
            public void run() {
                int ky = currentChuKy[0];

                // Cap nhat visual 4 tab chu ky
                int mauTabActive = ContextCompat.getColor(requireContext(), R.color.primary);
                int mauTabInactive = ContextCompat.getColor(requireContext(), R.color.text_secondary);

                if (tabKy1Thang != null) {
                    tabKy1Thang.setBackgroundResource(ky == 1 ? R.drawable.bg_segmented_active : 0);
                    tabKy1Thang.setTextColor(ky == 1 ? mauTabActive : mauTabInactive);
                    tabKy1Thang.setTypeface(null, ky == 1 ? Typeface.BOLD : Typeface.NORMAL);
                }

                if (tabKy3Thang != null) {
                    tabKy3Thang.setBackgroundResource(ky == 3 ? R.drawable.bg_segmented_active : 0);
                    tabKy3Thang.setTextColor(ky == 3 ? mauTabActive : mauTabInactive);
                    tabKy3Thang.setTypeface(null, ky == 3 ? Typeface.BOLD : Typeface.NORMAL);
                }

                if (tabKy6Thang != null) {
                    tabKy6Thang.setBackgroundResource(ky == 6 ? R.drawable.bg_segmented_active : 0);
                    tabKy6Thang.setTextColor(ky == 6 ? mauTabActive : mauTabInactive);
                    tabKy6Thang.setTypeface(null, ky == 6 ? Typeface.BOLD : Typeface.NORMAL);
                }

                if (tabKy1Nam != null) {
                    tabKy1Nam.setBackgroundResource(ky == 12 ? R.drawable.bg_segmented_active : 0);
                    tabKy1Nam.setTextColor(ky == 12 ? mauTabActive : mauTabInactive);
                    tabKy1Nam.setTypeface(null, ky == 12 ? Typeface.BOLD : Typeface.NORMAL);
                }

                // Bang gia va so luot theo tung chu ky
                long giaPlus = 29000;
                int luotPlus = 200;
                String textPlusLuot = "+200 lượt";

                long giaPro = 69000;
                int luotPro = 500;
                String textProLuot = "+500 lượt";

                long giaVip = 99000;
                int luotVip = 999999;
                String textVipLuot = "Không giới hạn";

                if (ky == 1) {
                    giaPlus = 29000; luotPlus = 200; textPlusLuot = "+200 lượt";
                    giaPro = 69000; luotPro = 500; textProLuot = "+500 lượt";
                    giaVip = 99000; luotVip = 999999; textVipLuot = "Không giới hạn / 1T";
                } else if (ky == 3) {
                    giaPlus = 79000; luotPlus = 650; textPlusLuot = "+650 lượt";
                    giaPro = 189000; luotPro = 1600; textProLuot = "+1.600 lượt";
                    giaVip = 279000; luotVip = 999999; textVipLuot = "Không giới hạn / 3T";
                } else if (ky == 6) {
                    giaPlus = 149000; luotPlus = 1400; textPlusLuot = "+1.400 lượt";
                    giaPro = 349000; luotPro = 3500; textProLuot = "+3.500 lượt";
                    giaVip = 499000; luotVip = 999999; textVipLuot = "Không giới hạn / 6T";
                } else if (ky == 12) {
                    giaPlus = 259000; luotPlus = 3000; textPlusLuot = "+3.000 lượt";
                    giaPro = 599000; luotPro = 8000; textProLuot = "+8.000 lượt";
                    giaVip = 899000; luotVip = 999999; textVipLuot = "Không giới hạn / 1N";
                }

                if (txtGoiPlusGia != null) txtGoiPlusGia.setText(String.format("%,dđ", giaPlus));
                if (txtGoiPlusLuot != null) txtGoiPlusLuot.setText(textPlusLuot);

                if (txtGoiProGia != null) txtGoiProGia.setText(String.format("%,dđ", giaPro));
                if (txtGoiProLuot != null) txtGoiProLuot.setText(textProLuot);

                if (txtGoiVipGia != null) txtGoiVipGia.setText(String.format("%,dđ", giaVip));
                if (txtGoiVipLuot != null) txtGoiVipLuot.setText(textVipLuot);

                // Cap nhat so tien va luot dang chon
                if ("PRO".equals(currentLoaiGoi[0])) {
                    currentGia[0] = giaPro;
                    currentLuot[0] = luotPro;
                } else if ("VIP".equals(currentLoaiGoi[0])) {
                    currentGia[0] = giaVip;
                    currentLuot[0] = luotVip;
                } else {
                    currentGia[0] = giaPlus;
                    currentLuot[0] = luotPlus;
                }

                capNhatQr.run();
            }
        };

        // Khoi tao trang thai ban dau
        capNhatGiaoDienChonGoi.run();
        capNhatChuKyVaGia.run();

        // Su kien chuyen tab chu ky
        if (tabKy1Thang != null) {
            tabKy1Thang.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentChuKy[0] = 1;
                    capNhatChuKyVaGia.run();
                }
            });
        }

        if (tabKy3Thang != null) {
            tabKy3Thang.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentChuKy[0] = 3;
                    capNhatChuKyVaGia.run();
                }
            });
        }

        if (tabKy6Thang != null) {
            tabKy6Thang.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentChuKy[0] = 6;
                    capNhatChuKyVaGia.run();
                }
            });
        }

        if (tabKy1Nam != null) {
            tabKy1Nam.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentChuKy[0] = 12;
                    capNhatChuKyVaGia.run();
                }
            });
        }

        // Chon goi Plus
        cardGoiPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentLoaiGoi[0] = "PLUS";
                capNhatGiaoDienChonGoi.run();
                capNhatChuKyVaGia.run();
            }
        });

        // Chon goi Pro
        cardGoiPro.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentLoaiGoi[0] = "PRO";
                capNhatGiaoDienChonGoi.run();
                capNhatChuKyVaGia.run();
            }
        });

        // Chon goi VIP
        cardGoiVip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentLoaiGoi[0] = "VIP";
                capNhatGiaoDienChonGoi.run();
                capNhatChuKyVaGia.run();
            }
        });

        // Sao chep thong tin
        btnCopyStk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saoChepVaoClipboard("Số tài khoản", txtStk.getText().toString());
            }
        });

        btnCopySoTien.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saoChepVaoClipboard("Số tiền", String.valueOf(currentGia[0]));
            }
        });

        btnCopyNoiDung.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saoChepVaoClipboard("Nội dung chuyển khoản", txtNoiDung.getText().toString());
            }
        });

        // Kiem tra thanh toan tu dong (co co che chong spam click va chi bao chuc mung khi goi thuc su tang)
        btnKiemTraThanhToan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnKiemTraThanhToan.setEnabled(false);
                Toast.makeText(requireContext(), "Đang kiểm tra biến động giao dịch từ máy chủ...", Toast.LENGTH_SHORT).show();

                final String oldGoi = qlTaiKhoan.getLoaiGoi();
                final int oldTongLuot = qlTaiKhoan.getTongLuot();

                qlTaiKhoan.dongBoTuFirestore(new QLTaiKhoan.SyncCallback() {
                    @Override
                    public void onSynced() {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    btnKiemTraThanhToan.setEnabled(true);
                                    capNhatGiaoDienTaiKhoan();

                                    boolean daNangCap = !oldGoi.equalsIgnoreCase(qlTaiKhoan.getLoaiGoi())
                                            || qlTaiKhoan.getTongLuot() > oldTongLuot;

                                    if (daNangCap) {
                                        dialog.dismiss();
                                        dialogNangCapHienTai = null;
                                        hienDialogNangCapThanhCong(qlTaiKhoan.getLoaiGoi(), qlTaiKhoan.getTongLuot());
                                    } else {
                                        Toast.makeText(requireContext(), "Đã đồng bộ dữ liệu. Chưa phát hiện giao dịch nâng cấp mới.", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                        }
                    }

                    @Override
                    public void onError(final String loi) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    btnKiemTraThanhToan.setEnabled(true);
                                    Toast.makeText(requireContext(), "Chưa ghi nhận biến động số dư. Vui lòng thử lại sau 1 phút.", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }
                });
            }
        });

        // Che do thu nghiem (Dev Mode)
        btnDevTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                qlTaiKhoan.nangCapDevMode(currentLoaiGoi[0], currentLuot[0], currentChuKy[0], new QLTaiKhoan.AuthCallback() {
                    @Override
                    public void onSuccess(final String thongBao) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    capNhatGiaoDienTaiKhoan();
                                    dialog.dismiss();
                                    dialogNangCapHienTai = null;
                                    hienDialogNangCapThanhCong(currentLoaiGoi[0], qlTaiKhoan.getTongLuot());
                                }
                            });
                        }
                    }

                    @Override
                    public void onError(String loi) { }
                });
            }
        });

        btnDong.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    // Hien thi Dialog thong bao nang cap goi thanh cong
    public void hienDialogNangCapThanhCong(final String loaiGoi, final int tongLuot) {
        if (!isAdded() || getContext() == null) return;

        // Dong dialog QR neu con dang hien thi tren man hinh
        if (dialogNangCapHienTai != null && dialogNangCapHienTai.isShowing()) {
            dialogNangCapHienTai.dismiss();
            dialogNangCapHienTai = null;
        }

        final Dialog dialog = DialogHelper.taoDialog(requireContext());
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_nang_cap_thanh_cong, null);
        dialog.setContentView(view);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                (int) (requireContext().getResources().getDisplayMetrics().widthPixels * 0.88f),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        TextView txtSuccessTitle = (TextView) view.findViewById(R.id.txtSuccessTitle);
        TextView txtSuccessPlanBadge = (TextView) view.findViewById(R.id.txtSuccessPlanBadge);
        TextView txtSuccessDesc = (TextView) view.findViewById(R.id.txtSuccessDesc);
        MaterialButton btnSuccessXacNhan = (MaterialButton) view.findViewById(R.id.btnSuccessXacNhan);

        String tenGoiHienThi;
        String moTaChiTiet;

        if ("VIP".equalsIgnoreCase(loaiGoi)) {
            tenGoiHienThi = "GÓI VIP (KHÔNG GIỚI HẠN)";
            moTaChiTiet = "Chúc mừng bạn đã nâng cấp thành công gói VIP! Bạn có thể sử dụng tất cả tính năng AI không giới hạn số lượt.";
        } else if ("PRO".equalsIgnoreCase(loaiGoi)) {
            tenGoiHienThi = "GÓI SIÊU CẤP PRO (500 LƯỢT)";
            moTaChiTiet = "Chúc mừng bạn đã nâng cấp thành công gói Siêu Cấp Pro! Tài khoản của bạn hiện có " + tongLuot + " lượt AI tốc độ cao.";
        } else {
            tenGoiHienThi = "GÓI NÂNG CẤP PLUS (200 LƯỢT)";
            moTaChiTiet = "Chúc mừng bạn đã nâng cấp thành công gói Nâng Cấp Plus! Tài khoản của bạn hiện có " + tongLuot + " lượt AI khả dụng.";
        }

        txtSuccessPlanBadge.setText(tenGoiHienThi);
        txtSuccessDesc.setText(moTaChiTiet);

        btnSuccessXacNhan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                capNhatGiaoDienTaiKhoan();
            }
        });

        dialog.show();
    }

    // Ham tai anh VietQR tu mang vao ImageView (co kiem tra co cancel chong memory leak va crash)
    void taiAnhVietQr(final ImageView imgView, final ProgressBar pb, final String urlStr, final AtomicBoolean isDialogActive) {
        pb.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (isDialogActive != null && !isDialogActive.get()) return;
                    URL url = new URL(urlStr);
                    InputStream inputStream = url.openStream();
                    final Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

                    // Neu nguoi dung da dong dialog truoc khi anh tai xong -> huy ngay bitmap
                    if (isDialogActive != null && !isDialogActive.get()) {
                        if (bitmap != null && !bitmap.isRecycled()) {
                            bitmap.recycle();
                        }
                        return;
                    }

                    if (getActivity() != null && isAdded()) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (isDialogActive != null && !isDialogActive.get()) return;
                                pb.setVisibility(View.GONE);
                                if (bitmap != null) {
                                    imgView.setImageBitmap(bitmap);
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    if (getActivity() != null && isAdded()) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (isDialogActive != null && !isDialogActive.get()) return;
                                pb.setVisibility(View.GONE);
                            }
                        });
                    }
                }
            }
        }).start();
    }

    // Sao chep van ban vao Clipboard
    void saoChepVaoClipboard(String nhan, String text) {
        if (getContext() == null) return;
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(nhan, text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(requireContext(), "Đã sao chép: " + text, Toast.LENGTH_SHORT).show();
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

    @Override
    public void onResume() {
        super.onResume();
        capNhatGiaoDienTaiKhoan();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            // Khi fragment hien thi lai, khoi dong lai listener neu dang nhap
            batDauLangNgheNangCap();
            capNhatGiaoDienTaiKhoan();
        } else {
            // Khi an fragment, huy listener de tiet kiem tai nguyen
            if (qlTaiKhoan != null) qlTaiKhoan.dungLangNgheNangCap();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Dong dialog QR neu con dang hien thi de tranh window leak
        if (dialogNangCapHienTai != null && dialogNangCapHienTai.isShowing()) {
            dialogNangCapHienTai.dismiss();
            dialogNangCapHienTai = null;
        }
        // Huy lang nghe Firestore khi fragment bi huy de tranh memory leak
        if (qlTaiKhoan != null) qlTaiKhoan.dungLangNgheNangCap();
    }

    // ─────────────────────────────────────────────────────────────
    // Static Helper Methods (Quan ly Theme & Quota AI)
    // ─────────────────────────────────────────────────────────────

    public static void apDungTheme(int themeMode) {
        if (themeMode == THEME_LIGHT) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        } else if (themeMode == THEME_DARK) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    public static void khoiTaoTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int themeMode = prefs.getInt(KEY_THEME_MODE, THEME_SYSTEM);
        apDungTheme(themeMode);
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
        return QLTaiKhoan.getInstance(context).kiemTraConLuotAi();
    }
}
