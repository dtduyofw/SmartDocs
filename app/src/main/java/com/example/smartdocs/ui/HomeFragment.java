package com.example.smartdocs.ui;

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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.smartdocs.R;
import com.example.smartdocs.data.model.ScanDocument;
import com.example.smartdocs.ocr.OcrManager;
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

    // AI Limit bar
    MaterialCardView cardAiLimit;
    TextView txtAiLimit, txtUpgrade;
    ProgressBar pbAiLimit;

    // Trạng thái mode: true = AI mode, false = Offline mode
    boolean isAiMode = true;

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
    ListView lvRecent;
    TextView txtXemTatCa;
    ArrayAdapter<ScanDocument> adapterRecent;
    List<ScanDocument> listRecentDocs;

    // ViewModel
    DocumentViewModel viewModel;

    // Launchers
    ActivityResultLauncher<Uri> cameraLauncher;
    ActivityResultLauncher<String> galleryLauncher;
    ActivityResultLauncher<String> cameraPermissionLauncher;
    ActivityResultLauncher<String> storagePermissionLauncher;
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
        lvRecent = (ListView) view.findViewById(R.id.lvRecent);
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
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        try {
                            requireContext().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException e) {
                            // Ignored if provider doesn't support it
                        }
                        moImagePreview(uri);
                    } else {
                        hienThongBao("Không có ảnh nào được chọn.");
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

        storagePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        galleryLauncher.launch("image/*");
                    } else {
                        hienThongBao("Cần cấp quyền truy cập ảnh để chọn từ thư viện.");
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
        capNhatAiMode(true);

        btnModeAi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                capNhatAiMode(true);
            }
        });

        btnModeOffline.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                capNhatAiMode(false);
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
        String permission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission = Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }

        if (ContextCompat.checkSelfPermission(requireContext(), permission)
                == PackageManager.PERMISSION_GRANTED) {
            galleryLauncher.launch("image/*");
        } else {
            storagePermissionLauncher.launch(permission);
        }
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

        adapterRecent = new ArrayAdapter<ScanDocument>(requireContext(), R.layout.item_document, listRecentDocs) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_document, parent, false);
                }
                ScanDocument doc = getItem(position);
                if (doc == null) return convertView;

                TextView txtTitle = (TextView) convertView.findViewById(R.id.txtItemTitle);
                TextView txtPreview = (TextView) convertView.findViewById(R.id.txtItemOcrPreview);
                TextView txtDate = (TextView) convertView.findViewById(R.id.txtItemDate);
                ImageView imgStar = (ImageView) convertView.findViewById(R.id.imgStar);
                ImageView imgMenu = (ImageView) convertView.findViewById(R.id.imgMenu);
                com.google.android.material.chip.Chip chipAi = (com.google.android.material.chip.Chip) convertView.findViewById(R.id.chipAi);

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

                if (doc.isFavorite == 1) {
                    imgStar.setImageResource(R.drawable.ic_star_on);
                    imgStar.setAlpha(1.0f);
                } else {
                    imgStar.setImageResource(R.drawable.ic_star_off);
                    imgStar.setAlpha(0.4f);
                }

                imgStar.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        viewModel.toggleFavorite(doc);
                        String msg = (doc.isFavorite == 0) ? "Đã thêm vào yêu thích" : "Đã bỏ yêu thích";
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                    }
                });

                imgMenu.setVisibility(View.GONE);
                return convertView;
            }
        };
        lvRecent.setAdapter(adapterRecent);

        viewModel.getAllDocuments().observe(getViewLifecycleOwner(), documents -> {
            listRecentDocs.clear();
            if (documents == null || documents.isEmpty()) {
                lvRecent.setVisibility(View.GONE);
                layoutNoRecent.setVisibility(View.VISIBLE);
            } else {
                layoutNoRecent.setVisibility(View.GONE);
                lvRecent.setVisibility(View.VISIBLE);
                int max = Math.min(5, documents.size());
                for (int i = 0; i < max; i++) {
                    listRecentDocs.add(documents.get(i));
                }
                adapterRecent.notifyDataSetChanged();
            }
        });

        lvRecent.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < listRecentDocs.size()) {
                    ScanDocument doc = listRecentDocs.get(position);
                    Intent i = DocumentPreviewActivity.taoIntent(requireContext(), doc);
                    startActivity(i);
                }
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

    void capNhatAiMode(boolean aiMode) {
        isAiMode = aiMode;
        if (!isAdded()) return;

        if (aiMode) {
            btnModeAi.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            getResources().getColor(R.color.primary, null)));
            btnModeAi.setTextColor(getResources().getColor(R.color.white, null));

            btnModeOffline.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.TRANSPARENT));
            btnModeOffline.setTextColor(getResources().getColor(R.color.text_secondary, null));

            cardAiLimit.setVisibility(View.VISIBLE);
            capNhatAiLimit();
        } else {
            btnModeOffline.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            getResources().getColor(R.color.primary, null)));
            btnModeOffline.setTextColor(getResources().getColor(R.color.white, null));

            btnModeAi.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.TRANSPARENT));
            btnModeAi.setTextColor(getResources().getColor(R.color.text_secondary, null));

            cardAiLimit.setVisibility(View.GONE);
        }
    }

    void capNhatAiLimit() {
        if (!isAdded() || getContext() == null) return;
        boolean coKey = SettingsFragment.hasCustomApiKey(requireContext());
        int aiCount = SettingsFragment.getAiCount(requireContext());

        if (coKey) {
            String text = "AI Limit: Không giới hạn (Custom Key)";
            txtAiLimit.setText(text);
            txtAiLimit.setTextColor(getResources().getColor(R.color.accent_green_dark, null));
            if (txtUpgrade != null) {
                txtUpgrade.setText("Key riêng");
                txtUpgrade.setTextColor(getResources().getColor(R.color.accent_green, null));
            }
            if (pbAiLimit != null) {
                pbAiLimit.setMax(100);
                pbAiLimit.setProgress(100);
                pbAiLimit.setProgressTintList(
                        android.content.res.ColorStateList.valueOf(
                                getResources().getColor(R.color.accent_green, null)));
            }
            return;
        }

        if (txtUpgrade != null) {
            txtUpgrade.setText("Chi tiết");
            txtUpgrade.setTextColor(getResources().getColor(R.color.primary, null));
        }
        txtAiLimit.setTextColor(getResources().getColor(R.color.text_secondary, null));

        int conLai = Math.max(0, SettingsFragment.AI_MAX_LIMIT - aiCount);
        String text = "Lượt AI: Còn " + conLai + " / " + SettingsFragment.AI_MAX_LIMIT + " lượt";
        android.text.SpannableString span = new android.text.SpannableString(text);
        String numStr = String.valueOf(conLai);
        int start = text.indexOf(numStr);
        if (start >= 0) {
            int mauHighlight = (aiCount >= SettingsFragment.AI_MAX_LIMIT)
                    ? getResources().getColor(android.R.color.holo_red_dark, null)
                    : (aiCount >= SettingsFragment.AI_MAX_LIMIT * 0.8)
                    ? getResources().getColor(android.R.color.holo_orange_dark, null)
                    : getResources().getColor(R.color.primary, null);

            span.setSpan(
                    new android.text.style.ForegroundColorSpan(mauHighlight),
                    start, start + numStr.length(),
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        txtAiLimit.setText(span);

        if (pbAiLimit != null) {
            pbAiLimit.setMax(SettingsFragment.AI_MAX_LIMIT);
            pbAiLimit.setProgress(Math.min(aiCount, SettingsFragment.AI_MAX_LIMIT));
            int mauProgress = (aiCount >= SettingsFragment.AI_MAX_LIMIT)
                    ? getResources().getColor(android.R.color.holo_red_dark, null)
                    : (aiCount >= SettingsFragment.AI_MAX_LIMIT * 0.8)
                    ? getResources().getColor(android.R.color.holo_orange_dark, null)
                    : getResources().getColor(R.color.primary, null);

            pbAiLimit.setProgressTintList(
                    android.content.res.ColorStateList.valueOf(mauProgress));
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
