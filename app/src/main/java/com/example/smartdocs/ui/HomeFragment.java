package com.example.smartdocs.ui;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.HapticFeedbackConstants;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartdocs.R;
import com.example.smartdocs.data.model.DocumentPage;
import com.example.smartdocs.data.model.ScanDocument;
import com.example.smartdocs.ocr.OcrManager;
import com.example.smartdocs.ui.adapter.SelectedImageAdapter;
import com.example.smartdocs.ui.viewmodel.DocumentViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {

    // Views — ánh xạ từ fragment_home.xml
    TextView txtGreeting;
    MaterialButton btnModeOffline, btnModeAi;
    LinearLayout layoutHomeContent;

    // AI Limit bar
    MaterialCardView cardAiLimit;
    TextView txtAiLimit, txtUpgrade;
    ProgressBar pbAiLimit;

    // Trạng thái mode: true = AI mode, false = Offline mode
    boolean isAiMode = true;
    boolean isModeKhoiTao = false;

    // 2 card lớn Home
    MaterialCardView cardGallery, cardCamera;

    // Preview ảnh
    MaterialCardView cardPreviewWrapper;
    ImageView imgPreview;
    LinearLayout layoutPlaceholder;

    // Scan flow
    MaterialButton btnScan;
    LinearLayout layoutLoading;
    TextView txtLoadingStatus;

    // Recent Documents
    LinearLayout layoutNoRecent;
    LinearLayout layoutRecentList;
    TextView txtXemTatCa;
    List<ScanDocument> listRecentDocs;

    // ViewModel
    DocumentViewModel viewModel;

    // Launchers
    ActivityResultLauncher<Uri> cameraLauncher;
    ActivityResultLauncher<PickVisualMediaRequest> galleryLauncher;
    ActivityResultLauncher<String> cameraPermissionLauncher;
    ActivityResultLauncher<Intent> imagePreviewLauncher;

    Uri cameraImageUri;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dangKyLaunchers();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1. Ánh xạ views
        anhXaView(view);

        // 2. Khởi tạo ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(DocumentViewModel.class);

        // 3. Observe LiveData
        observeViewModel();

        // 4. Gán sự kiện
        ganSuKien();

        // 5. Khởi tạo Recent Documents
        khaiTaoRecentDocs();
    }

    void anhXaView(View view) {
        txtGreeting = (TextView) view.findViewById(R.id.txtGreeting);
        btnModeOffline = (MaterialButton) view.findViewById(R.id.btnModeOffline);
        btnModeAi = (MaterialButton) view.findViewById(R.id.btnModeAi);
        layoutHomeContent = (LinearLayout) view.findViewById(R.id.layoutHomeContent);

        cardAiLimit = (MaterialCardView) view.findViewById(R.id.cardAiLimit);
        txtAiLimit = (TextView) view.findViewById(R.id.txtAiLimit);
        txtUpgrade = (TextView) view.findViewById(R.id.txtUpgrade);
        pbAiLimit = (ProgressBar) view.findViewById(R.id.pbAiLimit);

        cardGallery = (MaterialCardView) view.findViewById(R.id.cardGallery);
        cardCamera = (MaterialCardView) view.findViewById(R.id.cardCamera);

        cardPreviewWrapper = (MaterialCardView) view.findViewById(R.id.cardPreviewWrapper);
        imgPreview = (ImageView) view.findViewById(R.id.imgPreview);
        layoutPlaceholder = (LinearLayout) view.findViewById(R.id.layoutPlaceholder);

        btnScan = (MaterialButton) view.findViewById(R.id.btnScan);
        layoutLoading = (LinearLayout) view.findViewById(R.id.layoutLoading);
        txtLoadingStatus = (TextView) view.findViewById(R.id.txtLoadingStatus);

        layoutNoRecent = (LinearLayout) view.findViewById(R.id.layoutNoRecent);
        layoutRecentList = (LinearLayout) view.findViewById(R.id.layoutRecentList);
        txtXemTatCa = (TextView) view.findViewById(R.id.txtXemTatCa);
    }

    void dangKyLaunchers() {
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success && cameraImageUri != null) {
                        moImagePreview(cameraImageUri);
                    } else {
                        hienThongBao("Đã hủy chụp ảnh.");
                    }
                }
        );

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(),
                new ActivityResultCallback<java.util.List<Uri>>() {
                    @Override
                    public void onActivityResult(java.util.List<Uri> uris) {
                        if (uris != null && !uris.isEmpty()) {
                            if (uris.size() == 1) {
                                moImagePreview(uris.get(0));
                            } else {
                                hienDialogDanhSachAnh(uris);
                            }
                        } else {
                            hienThongBao("Không có ảnh nào được chọn.");
                        }
                    }
                }
        );

        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        moCamera();
                    } else {
                        hienThongBao("Cần cấp quyền Camera để chụp ảnh.");
                    }
                }
        );

        imagePreviewLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                        Uri uriXacNhan = result.getData().getParcelableExtra(
                                ImagePreviewActivity.KEY_RESULT_URI);
                        if (uriXacNhan != null) {
                            viewModel.setSelectedImageUri(uriXacNhan);
                            hienThiAnh(uriXacNhan);
                        }
                    }
                }
        );
    }

    void observeViewModel() {
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (layoutLoading != null) {
                layoutLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            }
            if (btnScan != null) {
                btnScan.setEnabled(!isLoading);
            }
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                if (getActivity() != null) {
                    Snackbar.make(getActivity().findViewById(android.R.id.content), error, Snackbar.LENGTH_LONG)
                            .setAction("Đóng", v -> viewModel.setError(null))
                            .show();
                }
            }
        });

        viewModel.getNewlySavedDocument().observe(getViewLifecycleOwner(), savedDoc -> {
            if (savedDoc != null) {
                viewModel.resetSaveSuccess();
                Intent iPreview = DocumentPreviewActivity.taoIntent(requireContext(), savedDoc);
                startActivity(iPreview);
                datLaiTrangThaiPreview();
            }
        });

        viewModel.getLoadingStatusText().observe(getViewLifecycleOwner(), statusText -> {
            if (statusText != null && !statusText.isEmpty() && txtLoadingStatus != null) {
                txtLoadingStatus.setText(statusText);
            }
        });
    }

    void ganSuKien() {
        // Đọc chế độ đã lưu gần nhất (mặc định là AI nếu chưa lưu)
        SharedPreferences prefs = requireContext().getSharedPreferences(
                SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
        boolean cheDoLuu = prefs.getBoolean("is_ai_mode", true);
        capNhatAiMode(cheDoLuu, false);

        btnModeAi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                capNhatAiMode(true, true);
            }
        });

        btnModeOffline.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                capNhatAiMode(false, true);
            }
        });

        cardAiLimit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hienDialogThongTinAiLimit();
            }
        });
        txtUpgrade.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hienDialogThongTinAiLimit();
            }
        });

        cardGallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                kiemTraQuyenGallery();
            }
        });

        cardCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                kiemTraQuyenCamera();
            }
        });

        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                thucHienOcr();
            }
        });

        txtXemTatCa.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).chonTab(R.id.nav_history);
                }
            }
        });
    }

    void kiemTraQuyenCamera() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            moCamera();
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Cần quyền Camera")
                    .setMessage("SmartDocs cần quyền Camera để chụp ảnh tài liệu.\n\nVui lòng cho phép quyền Camera để sử dụng tính năng này.")
                    .setPositiveButton("Cấp quyền", (dialog, which) -> {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    void kiemTraQuyenGallery() {
        // Photo Picker API không cần xin quyền, hệ thống tự xử lý
        galleryLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    void moCamera() {
        try {
            File photoFile = taoFileAnhTam();
            cameraImageUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    photoFile
            );
            cameraLauncher.launch(cameraImageUri);
        } catch (IOException e) {
            hienThongBao("Không thể tạo file ảnh: " + e.getMessage());
        }
    }

    File taoFileAnhTam() throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
        String tenFile = "IMG_" + timestamp + "_";
        File thuMuc = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(tenFile, ".jpg", thuMuc);
    }

    void moImagePreview(Uri uri) {
        Intent intent = new Intent(requireContext(), ImagePreviewActivity.class);
        intent.putExtra(ImagePreviewActivity.KEY_IMAGE_URI, uri);
        imagePreviewLauncher.launch(intent);
    }

    // Hien thi hop thoai danh sach cac trang anh da chon truoc khi quet
    void hienDialogDanhSachAnh(java.util.List<Uri> uris) {
        if (uris == null || uris.isEmpty() || getContext() == null) {
            return;
        }

        ArrayList<Uri> danhSachChon = new ArrayList<>(uris);

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_danh_sach_anh_quet, null);
        TextView txtTieuDe = (TextView) dialogView.findViewById(R.id.txtTieuDeDialog);
        RecyclerView rv = (RecyclerView) dialogView.findViewById(R.id.rvDanhSachAnh);
        MaterialButton btnHuy = (MaterialButton) dialogView.findViewById(R.id.btnHuy);
        MaterialButton btnBatDau = (MaterialButton) dialogView.findViewById(R.id.btnBatDauQuet);

        txtTieuDe.setText("Danh sách trang đã chọn (" + danhSachChon.size() + " trang)");
        btnBatDau.setText("Bắt đầu quét (" + danhSachChon.size() + " trang)");

        rv.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();

        SelectedImageAdapter[] adapterArr = new SelectedImageAdapter[1];
        adapterArr[0] = new SelectedImageAdapter(requireContext(), danhSachChon, new SelectedImageAdapter.OnItemRemoveListener() {
            @Override
            public void onRemove(int position) {
                if (position >= 0 && position < danhSachChon.size()) {
                    danhSachChon.remove(position);
                    adapterArr[0].notifyDataSetChanged();
                    txtTieuDe.setText("Danh sách trang đã chọn (" + danhSachChon.size() + " trang)");
                    btnBatDau.setText("Bắt đầu quét (" + danhSachChon.size() + " trang)");

                    if (danhSachChon.isEmpty()) {
                        dialog.dismiss();
                        hienThongBao("Đã hủy do không còn ảnh nào được chọn.");
                    }
                }
            }
        });
        rv.setAdapter(adapterArr[0]);

        btnHuy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        btnBatDau.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                batDauQuetNhieuTrang(danhSachChon);
            }
        });

        dialog.show();
    }

    // Bat dau tien trinh quet nhieu trang tuan tu
    void batDauQuetNhieuTrang(java.util.List<Uri> danhSachUri) {
        if (danhSachUri == null || danhSachUri.isEmpty()) {
            hienThongBao("Không có ảnh nào để quét.");
            return;
        }

        // Hien thi anh dau tien len khung preview
        hienThiAnh(danhSachUri.get(0));
        viewModel.setSelectedImageUri(danhSachUri.get(0));

        txtLoadingStatus.setText("Đang chuẩn bị nhận diện " + danhSachUri.size() + " trang...");
        viewModel.setLoading(true);

        ArrayList<DocumentPage> danhSachPages = new ArrayList<>();
        StringBuilder sbTongHop = new StringBuilder();

        thucHienOcrTuanTu(danhSachUri, 0, danhSachPages, sbTongHop);
    }

    // Nhan dien OCR tuan tu tung trang mot de tranh tran bo nho RAM
    void thucHienOcrTuanTu(java.util.List<Uri> danhSachUri, int index, java.util.List<DocumentPage> danhSachPages, StringBuilder sbTongHop) {
        if (!isAdded() || getContext() == null) {
            return;
        }

        if (index >= danhSachUri.size()) {
            // Da hoan thanh toan bo cac trang
            String textTongHop = sbTongHop.toString().trim();
            if (textTongHop.isEmpty()) {
                viewModel.setError("Không nhận diện được nội dung chữ từ các trang đã chọn.");
                return;
            }

            if (isAiMode) {
                if (SettingsFragment.conLuotDungAi(requireContext())) {
                    viewModel.ocrSuaLoiVaLuuNhieuTrang(textTongHop, danhSachPages, danhSachUri.get(0));
                } else {
                    hienThongBao("Đã hết lượt AI (" + SettingsFragment.AI_MAX_LIMIT + " lần/tháng). Đang lưu kết quả Offline...");
                    viewModel.ocrLuuTrucTiepNhieuTrang(textTongHop, danhSachPages, danhSachUri.get(0));
                }
            } else {
                viewModel.ocrLuuTrucTiepNhieuTrang(textTongHop, danhSachPages, danhSachUri.get(0));
            }
            return;
        }

        int trangHienTai = index + 1;
        int tongSoTrang = danhSachUri.size();
        txtLoadingStatus.setText("Đang nhận diện trang " + trangHienTai + "/" + tongSoTrang + "...");

        Uri uriTrang = danhSachUri.get(index);
        OcrManager.getInstance().recognizeFromUri(requireContext(), uriTrang, new OcrManager.OcrCallback() {
            @Override
            public void onSuccess(String fullText, com.google.mlkit.vision.text.Text result) {
                String formattedText = OcrManager.getInstance().extractFormattedText(result);
                String textTrang = (formattedText != null && !formattedText.trim().isEmpty()) ? formattedText : fullText;

                DocumentPage page = new DocumentPage(0, trangHienTai, uriTrang.toString(), textTrang);
                danhSachPages.add(page);

                if (sbTongHop.length() > 0) {
                    sbTongHop.append("\n\n");
                }
                sbTongHop.append("--- Trang ").append(trangHienTai).append(" ---\n");
                sbTongHop.append(textTrang);

                // Chuyen sang nhan dien trang tiep theo
                thucHienOcrTuanTu(danhSachUri, index + 1, danhSachPages, sbTongHop);
            }

            @Override
            public void onError(String errorMessage) {
                // Neu 1 trang loi thi van ghi nhan va tiep tuc cac trang sau
                DocumentPage page = new DocumentPage(0, trangHienTai, uriTrang.toString(), "");
                danhSachPages.add(page);

                if (sbTongHop.length() > 0) {
                    sbTongHop.append("\n\n");
                }
                sbTongHop.append("--- Trang ").append(trangHienTai).append(" ---\n");
                sbTongHop.append("[Không nhận diện được chữ]");

                thucHienOcrTuanTu(danhSachUri, index + 1, danhSachPages, sbTongHop);
            }
        });
    }

    void thucHienOcr() {
        Uri imageUri = viewModel.getSelectedImageUri().getValue();
        if (imageUri == null) {
            hienThongBao("Vui lòng chọn hoặc chụp ảnh trước.");
            return;
        }

        txtLoadingStatus.setText("ML Kit đang nhận diện văn bản...");
        viewModel.setLoading(true);

        OcrManager.getInstance().recognizeFromUri(requireContext(), imageUri, new OcrManager.OcrCallback() {
            @Override
            public void onSuccess(String fullText, com.google.mlkit.vision.text.Text result) {
                String formattedText = OcrManager.getInstance().extractFormattedText(result);
                String textTho = formattedText.isEmpty() ? fullText : formattedText;

                if (isAiMode) {
                    if (SettingsFragment.conLuotDungAi(requireContext())) {
                        viewModel.ocrSuaLoiVaLuu(textTho);
                    } else {
                        hienThongBao("Đã hết lượt AI (" + SettingsFragment.AI_MAX_LIMIT + " lần/tháng). Đang lưu kết quả Offline...");
                        viewModel.ocrLuuTrucTiep(textTho);
                    }
                } else {
                    viewModel.ocrLuuTrucTiep(textTho);
                }
            }

            @Override
            public void onError(String errorMessage) {
                viewModel.setError(errorMessage);
            }
        });
    }

    void hienThiAnh(Uri uri) {
        imgPreview.setImageURI(uri);
        imgPreview.setVisibility(View.VISIBLE);
        layoutPlaceholder.setVisibility(View.GONE);
        cardPreviewWrapper.setVisibility(View.VISIBLE);
        btnScan.setVisibility(View.VISIBLE);
        btnScan.setEnabled(true);
    }

    void datLaiTrangThaiPreview() {
        if (cardPreviewWrapper != null) cardPreviewWrapper.setVisibility(View.GONE);
        if (imgPreview != null) imgPreview.setImageURI(null);
        if (layoutPlaceholder != null) layoutPlaceholder.setVisibility(View.VISIBLE);
        if (btnScan != null) btnScan.setVisibility(View.GONE);
        viewModel.reset();
    }

    void hienThongBao(String message) {
        if (getActivity() != null) {
            Snackbar.make(getActivity().findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show();
        }
    }

    void khaiTaoRecentDocs() {
        listRecentDocs = new ArrayList<>();

        viewModel.getAllDocuments().observe(getViewLifecycleOwner(), documents -> {
            listRecentDocs.clear();
            if (layoutRecentList != null) {
                layoutRecentList.removeAllViews();
            }
            if (documents == null || documents.isEmpty()) {
                if (layoutRecentList != null) layoutRecentList.setVisibility(View.GONE);
                if (layoutNoRecent != null) layoutNoRecent.setVisibility(View.VISIBLE);
            } else {
                if (layoutNoRecent != null) layoutNoRecent.setVisibility(View.GONE);
                if (layoutRecentList != null) layoutRecentList.setVisibility(View.VISIBLE);
                int max = Math.min(5, documents.size());
                LayoutInflater inflater = LayoutInflater.from(getContext());
                for (int i = 0; i < max; i++) {
                    ScanDocument doc = documents.get(i);
                    listRecentDocs.add(doc);
                    View itemView = inflater.inflate(R.layout.item_document, layoutRecentList, false);
                    hienThiItemDocument(itemView, doc);
                    layoutRecentList.addView(itemView);
                }
            }
        });
    }

    void hienThiItemDocument(View itemView, ScanDocument doc) {
        TextView txtTitle = (TextView) itemView.findViewById(R.id.txtItemTitle);
        TextView txtPreview = (TextView) itemView.findViewById(R.id.txtItemOcrPreview);
        TextView txtDate = (TextView) itemView.findViewById(R.id.txtItemDate);
        //ImageView imgStar = (ImageView) itemView.findViewById(R.id.imgStar);
        ImageView imgMenu = (ImageView) itemView.findViewById(R.id.imgMenu);
        com.google.android.material.chip.Chip chipAi = (com.google.android.material.chip.Chip) itemView.findViewById(R.id.chipAi);

        txtTitle.setText(doc.title != null && !doc.title.isEmpty() ? doc.title : "Tài liệu không tiêu đề");

        if (doc.ocrText != null && !doc.ocrText.isEmpty()) {
            String preview = doc.ocrText.trim();
            if (preview.length() > 80) preview = preview.substring(0, 80) + "...";
            txtPreview.setText(preview);
            txtPreview.setVisibility(View.VISIBLE);
        } else {
            txtPreview.setVisibility(View.GONE);
        }

        boolean coAi = doc.aiSummary != null && !doc.aiSummary.trim().isEmpty();
        chipAi.setVisibility(coAi ? View.VISIBLE : View.GONE);

        String ngay = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                .format(new Date(doc.createdAt));
        String soTrang = doc.pageCount + " trang";
        txtDate.setText(ngay + " • " + soTrang);
        imgMenu.setVisibility(View.GONE);

        itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = DocumentPreviewActivity.taoIntent(requireContext(), doc);
                startActivity(i);
            }
        });
    }

    void kiemTraCapNhatUiTheoQuyenCamera() {
        if (getContext() == null || cardCamera == null) return;
        boolean coQuyenCamera = ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED;

        if (coQuyenCamera) {
            cardCamera.setAlpha(1.0f);
        } else {
            cardCamera.setAlpha(0.5f);
        }
    }

    // Chuyển đổi giữa chế độ AI và Offline mượt mà
    void capNhatAiMode(boolean aiMode) {
        capNhatAiMode(aiMode, true);
    }

    void capNhatAiMode(boolean aiMode, boolean coAnimation) {
        if (isModeKhoiTao && this.isAiMode == aiMode) {
            return; // Đang ở đúng chế độ rồi, không lặp lại
        }
        this.isAiMode = aiMode;
        this.isModeKhoiTao = true;

        if (!isAdded() || getContext() == null) return;

        // Lưu lựa chọn vào SharedPreferences
        SharedPreferences prefs = requireContext().getSharedPreferences(
                SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean("is_ai_mode", aiMode).apply();

        // Chạy hiệu ứng co giãn mượt mà cho layout
        if (coAnimation && layoutHomeContent != null) {
            AutoTransition transition = new AutoTransition();
            transition.setDuration(220);
            TransitionManager.beginDelayedTransition(layoutHomeContent, transition);
        }

        if (aiMode) {
            if (coAnimation) {
                animateNutMode(btnModeAi, btnModeOffline);
            } else {
                datMauNutKhongAnimation(btnModeAi, btnModeOffline);
            }
            cardAiLimit.setVisibility(View.VISIBLE);
            capNhatAiLimit(coAnimation);
        } else {
            if (coAnimation) {
                animateNutMode(btnModeOffline, btnModeAi);
            } else {
                datMauNutKhongAnimation(btnModeOffline, btnModeAi);
            }
            cardAiLimit.setVisibility(View.GONE);
        }
    }

    // Hiệu ứng chuyển màu mượt mà giữa nút Active và Inactive
    void animateNutMode(final MaterialButton btnBat, final MaterialButton btnTat) {
        if (getContext() == null) return;
        final int mauChinh = ContextCompat.getColor(requireContext(), R.color.primary);
        final int mauPhu = ContextCompat.getColor(requireContext(), R.color.text_secondary);
        final int mauTrang = Color.WHITE;
        final int mauTrongSuot = Color.TRANSPARENT;

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(220);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            ArgbEvaluator evaluator = new ArgbEvaluator();

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float f = animation.getAnimatedFraction();

                // Nút được chọn (Active)
                int bgBat = (int) evaluator.evaluate(f, mauTrongSuot, mauChinh);
                int textBat = (int) evaluator.evaluate(f, mauPhu, mauTrang);
                btnBat.setBackgroundTintList(ColorStateList.valueOf(bgBat));
                btnBat.setTextColor(textBat);
                btnBat.setIconTint(ColorStateList.valueOf(textBat));

                // Nút không chọn (Inactive)
                int bgTat = (int) evaluator.evaluate(f, mauChinh, mauTrongSuot);
                int textTat = (int) evaluator.evaluate(f, mauTrang, mauPhu);
                btnTat.setBackgroundTintList(ColorStateList.valueOf(bgTat));
                btnTat.setTextColor(textTat);
                btnTat.setIconTint(ColorStateList.valueOf(textTat));
            }
        });
        animator.start();
    }

    // Đặt màu nút trực tiếp khi khởi tạo (không animation)
    void datMauNutKhongAnimation(MaterialButton btnBat, MaterialButton btnTat) {
        if (getContext() == null) return;
        int mauChinh = ContextCompat.getColor(requireContext(), R.color.primary);
        int mauPhu = ContextCompat.getColor(requireContext(), R.color.text_secondary);

        btnBat.setBackgroundTintList(ColorStateList.valueOf(mauChinh));
        btnBat.setTextColor(Color.WHITE);
        btnBat.setIconTint(ColorStateList.valueOf(Color.WHITE));

        btnTat.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        btnTat.setTextColor(mauPhu);
        btnTat.setIconTint(ColorStateList.valueOf(mauPhu));
    }

    void capNhatAiLimit() {
        capNhatAiLimit(false);
    }

    void capNhatAiLimit(boolean coAnimation) {
        if (!isAdded() || getContext() == null) return;
        boolean coKey = SettingsFragment.hasCustomApiKey(requireContext());
        int aiCount = SettingsFragment.getAiCount(requireContext());

        if (coKey) {
            String text = "AI Limit: Không giới hạn (Custom Key)";
            txtAiLimit.setText(text);
            txtAiLimit.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_green_dark));
            if (txtUpgrade != null) {
                txtUpgrade.setText("Key riêng");
                txtUpgrade.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_green));
            }
            if (pbAiLimit != null) {
                pbAiLimit.setMax(100);
                if (coAnimation) {
                    ObjectAnimator.ofInt(pbAiLimit, "progress", 0, 100)
                            .setDuration(350)
                            .start();
                } else {
                    pbAiLimit.setProgress(100);
                }
                pbAiLimit.setProgressTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.accent_green)));
            }
            return;
        }

        if (txtUpgrade != null) {
            txtUpgrade.setText("Chi tiết");
            txtUpgrade.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary));
        }
        txtAiLimit.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));

        int conLai = Math.max(0, SettingsFragment.AI_MAX_LIMIT - aiCount);
        String text = "Lượt AI: Còn " + conLai + " / " + SettingsFragment.AI_MAX_LIMIT + " lượt";
        android.text.SpannableString span = new android.text.SpannableString(text);
        String numStr = String.valueOf(conLai);
        int start = text.indexOf(numStr);
        if (start >= 0) {
            int mauHighlight = (aiCount >= SettingsFragment.AI_MAX_LIMIT)
                    ? ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
                    : (aiCount >= SettingsFragment.AI_MAX_LIMIT * 0.8)
                    ? ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark)
                    : ContextCompat.getColor(requireContext(), R.color.primary);

            span.setSpan(
                    new android.text.style.ForegroundColorSpan(mauHighlight),
                    start, start + numStr.length(),
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        txtAiLimit.setText(span);

        if (pbAiLimit != null) {
            pbAiLimit.setMax(SettingsFragment.AI_MAX_LIMIT);
            int targetProgress = Math.min(aiCount, SettingsFragment.AI_MAX_LIMIT);
            if (coAnimation) {
                ObjectAnimator.ofInt(pbAiLimit, "progress", 0, targetProgress)
                        .setDuration(350)
                        .start();
            } else {
                pbAiLimit.setProgress(targetProgress);
            }
            int mauProgress = (aiCount >= SettingsFragment.AI_MAX_LIMIT)
                    ? ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
                    : (aiCount >= SettingsFragment.AI_MAX_LIMIT * 0.8)
                    ? ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark)
                    : ContextCompat.getColor(requireContext(), R.color.primary);

            pbAiLimit.setProgressTintList(ColorStateList.valueOf(mauProgress));
        }
    }

    void hienDialogThongTinAiLimit() {
        if (!isAdded() || getContext() == null) return;
        boolean coKey = SettingsFragment.hasCustomApiKey(requireContext());
        int count = SettingsFragment.getAiCount(requireContext());

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());

        if (coKey) {
            builder.setTitle("Gói AI: Cá nhân");
            builder.setMessage("Bạn đang sử dụng Gemini API Key riêng.\n\n• Trạng thái: Không giới hạn số lượt sử dụng\n• Đã dùng tháng này: " + count + " lần\n\nBạn có thể quản lý hoặc thay đổi key trong phần Cài đặt.");
            builder.setPositiveButton("Cài đặt", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).chonTab(R.id.nav_settings);
                    }
                }
            });
        } else {
            int conLai = Math.max(0, SettingsFragment.AI_MAX_LIMIT - count);
            builder.setTitle("Gói AI: Miễn phí");
            builder.setMessage("Mỗi tháng bạn có " + SettingsFragment.AI_MAX_LIMIT + " lượt hỗ trợ AI thông minh hoàn toàn miễn phí.\n\n• Lượt khả dụng: Còn " + conLai + " / " + SettingsFragment.AI_MAX_LIMIT + " lượt\n• Đã sử dụng: " + count + " lượt\n• Làm mới tự động: Ngày 01 hàng tháng\n\nKhi hết lượt, bạn vẫn quét tài liệu Offline bình thường mà không bị gián đoạn.");
            builder.setPositiveButton("Cài đặt nâng cao", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).chonTab(R.id.nav_settings);
                    }
                }
            });
        }

        builder.setNegativeButton("Đóng", null);
        builder.show();
    }

    @Override
    public void onResume() {
        super.onResume();
        kiemTraCapNhatUiTheoQuyenCamera();
        if (isAiMode) {
            capNhatAiLimit();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            kiemTraCapNhatUiTheoQuyenCamera();
            if (isAiMode) {
                capNhatAiLimit();
            }
        }
    }
}
