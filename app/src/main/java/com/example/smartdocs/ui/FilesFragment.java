package com.example.smartdocs.ui;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import com.example.smartdocs.R;
import com.example.smartdocs.data.dao.AppDatabase;
import com.example.smartdocs.data.dao.SavedFileDao;
import com.example.smartdocs.data.model.SavedFile;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class FilesFragment extends Fragment {

    // Views
    ImageView imgBackFiles, imgDeleteSelected;
    TextInputEditText edtSearchFile;
    Chip chipFilterAll, chipFilterPdf, chipFilterDocx, chipFilterXlsx, chipFilterTxt;
    TextView txtSelectCount;
    ListView lvFiles;
    LinearLayout layoutFilesEmpty;

    // Danh sách file
    List<File> danhSachFileGoc;
    List<File> danhSachFileHienThi;
    ArrayAdapter<File> adapterFile;

    // Multi-select
    Set<String> selectedPaths = new HashSet<>();
    boolean dangChonNhieu = false;

    // Filter & search
    String filterHienTai = "";
    String tuKhoa = "";

    // Yêu thích file — lưu trong Room Database (thay SharedPreferences)
    ExecutorService executorFiles;
    SavedFileDao savedFileDao;
    Set<String> favoritePaths = new HashSet<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_files, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1. Ánh xạ view
        anhXaView(view);

        // 2. Khởi tạo Room DAO để quản lý file yêu thích
        executorFiles = Executors.newSingleThreadExecutor();
        savedFileDao = AppDatabase.getInstance(requireContext()).savedFileDao();
        taiYeuThichTuRoom();

        // 3. Nút back
        imgBackFiles.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (dangChonNhieu) {
                    thoatChonNhieu();
                } else {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).chonTab(R.id.nav_home);
                    }
                }
            }
        });

        // 4. Khởi tạo danh sách & adapter
        danhSachFileGoc = new ArrayList<>();
        danhSachFileHienThi = new ArrayList<>();
        khaiTaoAdapter();

        // 5. Tải file
        taiDanhSachFile();

        // 6. Tìm kiếm realtime
        ganSuKienTimKiem();

        // 7. Bộ lọc chip
        ganSuKienFilter();

        // 8. Double tap để mở file, single tap để chọn khi đang multi-select
        GestureDetector gestureDetector = new GestureDetector(requireContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        // Tính vị trí item được double tap
                        int pos = lvFiles.pointToPosition((int) e.getX(), (int) e.getY());
                        if (pos == AdapterView.INVALID_POSITION) return false;
                        File file = danhSachFileHienThi.get(pos);
                        if (dangChonNhieu) {
                            // Đang multi-select thì double tap vẫn toggle chọn
                            toggleChonItem(file, null);
                        } else {
                            // Mở thẳng bằng app ngoài, không hỏi
                            moFileBangAppNgoai(file);
                        }
                        return true;
                    }

                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        int pos = lvFiles.pointToPosition((int) e.getX(), (int) e.getY());
                        if (pos == AdapterView.INVALID_POSITION) return false;
                        File file = danhSachFileHienThi.get(pos);
                        if (dangChonNhieu) {
                            toggleChonItem(file, null);
                        }
                        // Single tap bình thường không mở file nữa — phải double tap
                        return true;
                    }
                });

        lvFiles.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gestureDetector.onTouchEvent(event);
                // Trả về false để ListView vẫn xử lý scroll bình thường
                return false;
            }
        });

        // 9. Long press
        lvFiles.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (!dangChonNhieu) {
                    batChonNhieu();
                }
                toggleChonItem(danhSachFileHienThi.get(position), view);
                return true;
            }
        });

        // 10. Nút xóa nhiều
        imgDeleteSelected.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                xacNhanXoaNhieu();
            }
        });
    }

    void anhXaView(View view) {
        imgBackFiles = (ImageView) view.findViewById(R.id.imgBackFiles);
        imgDeleteSelected = (ImageView) view.findViewById(R.id.imgDeleteSelected);
        edtSearchFile = (TextInputEditText) view.findViewById(R.id.edtSearchFile);
        chipFilterAll = (Chip) view.findViewById(R.id.chipFilterAll);
        chipFilterPdf = (Chip) view.findViewById(R.id.chipFilterPdf);
        chipFilterDocx = (Chip) view.findViewById(R.id.chipFilterDocx);
        chipFilterXlsx = (Chip) view.findViewById(R.id.chipFilterXlsx);
        chipFilterTxt = (Chip) view.findViewById(R.id.chipFilterTxt);
        txtSelectCount = (TextView) view.findViewById(R.id.txtSelectCount);
        lvFiles = (ListView) view.findViewById(R.id.lvFiles);
        layoutFilesEmpty = (LinearLayout) view.findViewById(R.id.layoutFilesEmpty);
    }

    void khaiTaoAdapter() {
        adapterFile = new ArrayAdapter<File>(requireContext(), R.layout.item_file, danhSachFileHienThi) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_file, parent, false);
                }
                File file = getItem(position);
                if (file == null) return convertView;

                ImageView imgIcon = (ImageView) convertView.findViewById(R.id.imgFileIcon);
                TextView txtTen = (TextView) convertView.findViewById(R.id.txtFileName);
                TextView txtInfo = (TextView) convertView.findViewById(R.id.txtFileInfo);
                ImageView imgMenu = (ImageView) convertView.findViewById(R.id.imgFileMenu);
                CheckBox checkbox = (CheckBox) convertView.findViewById(R.id.checkboxFile);

                txtTen.setText(file.getName());

                String ngay = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        .format(new Date(file.lastModified()));
                String kichThuoc = dinhDangKichThuoc(file.length());
                txtInfo.setText(ngay + " • " + kichThuoc);

                String ten = file.getName().toLowerCase();
                if (ten.endsWith(".pdf")) {
                    imgIcon.setImageResource(R.drawable.ic_pdf);
                    imgIcon.setColorFilter(null);
                } else if (ten.endsWith(".docx") || ten.endsWith(".doc")) {
                    imgIcon.setImageResource(R.drawable.ic_docx);
                    imgIcon.setColorFilter(null);
                } else if (ten.endsWith(".xlsx") || ten.endsWith(".xls")) {
                    imgIcon.setImageResource(R.drawable.ic_excel);
                    imgIcon.setColorFilter(null);
                } else if (ten.endsWith(".txt")) {
                    imgIcon.setImageResource(R.drawable.ic_txt);
                    imgIcon.setColorFilter(null);
                } else {
                    imgIcon.setImageResource(R.drawable.ic_folder);
                    imgIcon.setColorFilter(null);
                }

                // Hiển thị / ẩn checkbox theo trạng thái multi-select
                if (dangChonNhieu) {
                    checkbox.setVisibility(View.VISIBLE);
                    checkbox.setChecked(selectedPaths.contains(file.getAbsolutePath()));
                    // Ẩn menu khi đang multi-select để tránh nhầm
                    imgMenu.setVisibility(View.GONE);
                } else {
                    checkbox.setVisibility(View.GONE);
                    imgMenu.setVisibility(View.VISIBLE);
                }

                // Nền xanh nhạt khi item được chọn
                convertView.setBackgroundColor(
                        selectedPaths.contains(file.getAbsolutePath())
                                ? 0x220D6EDF : 0x00000000);

                // Gán listener cho nút menu — phải đặt ở đây vì getView() tái sử dụng view
                final File fileCuoiCung = file;
                imgMenu.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        hienMenuFile(fileCuoiCung);
                    }
                });

                return convertView;
            }
        };
        lvFiles.setAdapter(adapterFile);
    }

    void taiDanhSachFile() {
        if (getContext() == null) return;
        danhSachFileGoc.clear();

        List<File> danhSachThuMuc = new ArrayList<>();
        if (requireContext().getExternalFilesDir(null) != null)
            danhSachThuMuc.add(requireContext().getExternalFilesDir(null));
        danhSachThuMuc.add(requireContext().getFilesDir());

        File thuMucSmartDocs = new File(
                requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), ExportHelper.FOLDER_NAME);
        if (thuMucSmartDocs.exists())
            danhSachThuMuc.add(thuMucSmartDocs);

        for (File dir : danhSachThuMuc) {
            if (dir != null && dir.exists()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        String ten = f.getName().toLowerCase();
                        if (ten.endsWith(".pdf") || ten.endsWith(".docx")
                                || ten.endsWith(".xlsx") || ten.endsWith(".txt")
                                || ten.endsWith(".doc") || ten.endsWith(".xls")) {
                            danhSachFileGoc.add(f);
                        }
                    }
                }
            }
        }

        danhSachFileGoc.sort(new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });

        apDungFilterVaTimKiem();
    }

    void apDungFilterVaTimKiem() {
        danhSachFileHienThi.clear();
        for (File f : danhSachFileGoc) {
            String ten = f.getName().toLowerCase();
            boolean khopLoai = filterHienTai.isEmpty() || ten.endsWith(filterHienTai);
            boolean khopTen = tuKhoa.isEmpty() || ten.contains(tuKhoa.toLowerCase());
            if (khopLoai && khopTen) {
                danhSachFileHienThi.add(f);
            }
        }

        if (lvFiles != null && layoutFilesEmpty != null) {
            if (danhSachFileHienThi.isEmpty()) {
                lvFiles.setVisibility(View.GONE);
                layoutFilesEmpty.setVisibility(View.VISIBLE);
            } else {
                lvFiles.setVisibility(View.VISIBLE);
                layoutFilesEmpty.setVisibility(View.GONE);
            }
        }
        if (adapterFile != null) {
            adapterFile.notifyDataSetChanged();
        }
    }

    void ganSuKienTimKiem() {
        edtSearchFile.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                tuKhoa = s.toString().trim();
                apDungFilterVaTimKiem();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    void ganSuKienFilter() {
        capNhatChipUI(chipFilterAll);

        chipFilterAll.setOnClickListener(v -> { filterHienTai = ""; capNhatChipUI(chipFilterAll); apDungFilterVaTimKiem(); });
        chipFilterPdf.setOnClickListener(v -> { filterHienTai = ".pdf"; capNhatChipUI(chipFilterPdf); apDungFilterVaTimKiem(); });
        chipFilterDocx.setOnClickListener(v -> { filterHienTai = ".docx"; capNhatChipUI(chipFilterDocx); apDungFilterVaTimKiem(); });
        chipFilterXlsx.setOnClickListener(v -> { filterHienTai = ".xlsx"; capNhatChipUI(chipFilterXlsx); apDungFilterVaTimKiem(); });
        chipFilterTxt.setOnClickListener(v -> { filterHienTai = ".txt"; capNhatChipUI(chipFilterTxt); apDungFilterVaTimKiem(); });
    }

    void capNhatChipUI(Chip chipDuocChon) {
        if (!isAdded()) return;
        Chip[] tatCa = {chipFilterAll, chipFilterPdf, chipFilterDocx, chipFilterXlsx, chipFilterTxt};
        for (Chip chip : tatCa) {
            if (chip == chipDuocChon) {
                chip.setChipBackgroundColorResource(R.color.primary);
                chip.setTextColor(0xFFFFFFFF);
            } else {
                chip.setChipBackgroundColorResource(android.R.color.transparent);
                chip.setTextColor(getResources().getColor(R.color.text_hint));
            }
        }
    }

    void batChonNhieu() {
        dangChonNhieu = true;
        selectedPaths.clear();
        imgDeleteSelected.setVisibility(View.VISIBLE);
        txtSelectCount.setVisibility(View.VISIBLE);
        capNhatSoLuongChon();
        adapterFile.notifyDataSetChanged();
        Toast.makeText(requireContext(), "Chọn các file muốn xóa", Toast.LENGTH_SHORT).show();
    }

    void thoatChonNhieu() {
        dangChonNhieu = false;
        selectedPaths.clear();
        imgDeleteSelected.setVisibility(View.GONE);
        txtSelectCount.setVisibility(View.GONE);
        if (adapterFile != null) {
            adapterFile.notifyDataSetChanged();
        }
    }

    public boolean xuLyBack() {
        if (dangChonNhieu) {
            thoatChonNhieu();
            return true;
        }
        return false;
    }

    void toggleChonItem(File file, View itemView) {
        String path = file.getAbsolutePath();
        if (selectedPaths.contains(path)) {
            selectedPaths.remove(path);
        } else {
            selectedPaths.add(path);
        }
        capNhatSoLuongChon();
        adapterFile.notifyDataSetChanged();
    }

    void capNhatSoLuongChon() {
        int soLuong = selectedPaths.size();
        txtSelectCount.setText("Đã chọn " + soLuong + " file");
        imgDeleteSelected.setAlpha(soLuong > 0 ? 1.0f : 0.4f);
    }

    void xacNhanXoaNhieu() {
        if (selectedPaths.isEmpty()) {
            Toast.makeText(requireContext(), "Chưa chọn file nào.", Toast.LENGTH_SHORT).show();
            return;
        }
        int soLuong = selectedPaths.size();
        DialogHelper.showDelete(requireContext(),
                "Xóa " + soLuong + " file?",
                "Hành động này không thể hoàn tác.",
                new DialogHelper.OnConfirm() {
                    @Override
                    public void onConfirm() {
                        int daXoa = 0;
                        for (String path : selectedPaths) {
                            File f = new File(path);
                            if (f.delete()) daXoa++;
                        }
                        Toast.makeText(requireContext(),
                                "Đã xóa " + daXoa + " file.", Toast.LENGTH_SHORT).show();
                        thoatChonNhieu();
                        taiDanhSachFile();
                    }
                });
    }

    void moFile(File file) {
        String ten = file.getName().toLowerCase();
        if (ten.endsWith(".pdf")) {
            moFilePdfTrongApp(file);
        } else {
            moFileBangAppNgoai(file);
        }
    }

    void moFilePdfTrongApp(File file) {
        try {
            // Chọn cách mở PDF qua custom dialog
            DialogHelper.showConfirm(requireContext(),
                    file.getName(),
                    "Chọn cách xem file PDF này:",
                    "Xem trong app",
                    new DialogHelper.OnConfirm() {
                        @Override
                        public void onConfirm() {
                            hienWebViewPdf(file);
                        }
                    });
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Không thể mở file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    void hienWebViewPdf(File file) {
        WebView webView = new WebView(requireContext());
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        webView.getSettings().setSupportZoom(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(true);
        webView.setWebViewClient(new WebViewClient());

        String filePath = "file://" + file.getAbsolutePath();
        webView.loadUrl(filePath);

        // Custom dialog xem PDF WebView
        android.app.Dialog dialogWebView = DialogHelper.taoDialog(requireContext());
        android.view.ViewGroup.LayoutParams lp = new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        if (dialogWebView.getWindow() != null) {
            dialogWebView.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.95f),
                    (int) (getResources().getDisplayMetrics().heightPixels * 0.80f));
        }

        android.widget.LinearLayout wrapper = new android.widget.LinearLayout(requireContext());
        wrapper.setOrientation(android.widget.LinearLayout.VERTICAL);
        wrapper.setBackgroundResource(R.drawable.bg_dialog_rounded);

        // Header
        android.widget.LinearLayout header = new android.widget.LinearLayout(requireContext());
        header.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        header.setPadding(p, p, p / 2, p);

        TextView txtTitle = new TextView(requireContext());
        txtTitle.setText(file.getName());
        txtTitle.setTextSize(15f);
        txtTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        txtTitle.setTextColor(getResources().getColor(R.color.text_primary));
        txtTitle.setMaxLines(1);
        txtTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        android.widget.LinearLayout.LayoutParams titleLp = new android.widget.LinearLayout.LayoutParams(0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        txtTitle.setLayoutParams(titleLp);
        header.addView(txtTitle);

        ImageView btnClose = new ImageView(requireContext());
        btnClose.setImageResource(R.drawable.ic_close);
        android.widget.LinearLayout.LayoutParams closeLp = new android.widget.LinearLayout.LayoutParams(
                (int)(36 * getResources().getDisplayMetrics().density),
                (int)(36 * getResources().getDisplayMetrics().density));
        btnClose.setLayoutParams(closeLp);
        btnClose.setAlpha(0.5f);
        btnClose.setPadding(p / 3, p / 3, p / 3, p / 3);
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { dialogWebView.dismiss(); }
        });
        header.addView(btnClose);
        wrapper.addView(header);

        // Divider
        View divider = new View(requireContext());
        divider.setBackgroundColor(getResources().getColor(R.color.divider));
        android.widget.LinearLayout.LayoutParams divLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1);
        divider.setLayoutParams(divLp);
        wrapper.addView(divider);

        // WebView
        android.widget.LinearLayout.LayoutParams wvLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        webView.setLayoutParams(wvLp);
        wrapper.addView(webView);

        // Footer
        View dividerBot = new View(requireContext());
        dividerBot.setBackgroundColor(getResources().getColor(R.color.divider));
        dividerBot.setLayoutParams(divLp);
        wrapper.addView(dividerBot);

        android.widget.LinearLayout footer = new android.widget.LinearLayout(requireContext());
        footer.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        footer.setGravity(android.view.Gravity.END);
        footer.setPadding(p / 2, p / 2, p / 2, p / 2);

        TextView btnChiaSe = new TextView(requireContext());
        btnChiaSe.setText("Chia sẻ");
        btnChiaSe.setTextSize(14f);
        btnChiaSe.setTypeface(null, android.graphics.Typeface.BOLD);
        btnChiaSe.setTextColor(getResources().getColor(R.color.primary));
        btnChiaSe.setPadding(p, p / 2, p, p / 2);
        btnChiaSe.setClickable(true);
        btnChiaSe.setFocusable(true);
        btnChiaSe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { chiaSe(file); }
        });
        footer.addView(btnChiaSe);
        wrapper.addView(footer);

        dialogWebView.setContentView(wrapper);
        dialogWebView.show();
    }

    void moFileBangAppNgoai(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", file);
            String mimeType = layMimeType(file.getName());
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Mở bằng..."));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Không thể mở file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    void hienMenuFile(File file) {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        View sheetView = LayoutInflater.from(requireContext())
                .inflate(R.layout.bottom_sheet_menu_file, null);
        sheet.setContentView(sheetView);

        ((TextView) sheetView.findViewById(R.id.txtBsFileName)).setText(file.getName());

        sheetView.findViewById(R.id.itemBsMoFile).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sheet.dismiss();
                moFile(file);
            }
        });
        sheetView.findViewById(R.id.itemBsDoiTen).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sheet.dismiss();
                hienDialogDoiTen(file);
            }
        });
        sheetView.findViewById(R.id.itemBsChiaSe).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sheet.dismiss();
                chiaSe(file);
            }
        });
        sheetView.findViewById(R.id.itemBsXoaFile).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sheet.dismiss();
                xacNhanXoaMotFile(file);
            }
        });

        sheet.show();
    }

    void hienDialogDoiTen(File file) {
        String tenCu = file.getName();
        String duoiFile = "";
        int viTriDot = tenCu.lastIndexOf('.');
        if (viTriDot > 0) {
            duoiFile = tenCu.substring(viTriDot);
            tenCu = tenCu.substring(0, viTriDot);
        }
        final String duoiFinal = duoiFile;

        DialogHelper.showInput(requireContext(),
                "Đổi tên file",
                "Đuôi file sẽ giữ nguyên: " + duoiFinal,
                "Tên file mới",
                tenCu,
                "Đổi tên",
                new DialogHelper.OnInputConfirm() {
                    @Override
                    public void onConfirm(String input) {
                        String tenMoi = input != null ? input.trim() : "";
                        if (tenMoi.isEmpty()) {
                            Toast.makeText(requireContext(),
                                    "Tên file không được để trống.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        tenMoi = tenMoi.replaceAll("[\\\\/:*?\"<>|]", "_");
                        File fileMoi = new File(file.getParent(), tenMoi + duoiFinal);
                        if (fileMoi.exists()) {
                            Toast.makeText(requireContext(),
                                    "Tên file đã tồn tại!", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (file.renameTo(fileMoi)) {
                            if (favoritePaths.contains(file.getAbsolutePath())) {
                                String oldPath = file.getAbsolutePath();
                                String newPath = fileMoi.getAbsolutePath();
                                favoritePaths.remove(oldPath);
                                favoritePaths.add(newPath);
                                // Cập nhật đường dẫn trong Room (tránh stale path)
                                executorFiles.execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        savedFileDao.updatePath(oldPath, newPath, fileMoi.getName());
                                    }
                                });
                            }
                            Toast.makeText(requireContext(),
                                    "Đã đổi tên thành công!", Toast.LENGTH_SHORT).show();
                            taiDanhSachFile();
                        } else {
                            Toast.makeText(requireContext(),
                                    "Không thể đổi tên file.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    void chiaSe(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(layMimeType(file.getName()));
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Chia sẻ qua..."));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Không thể chia sẻ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    void xacNhanXoaMotFile(File file) {
        DialogHelper.showDelete(requireContext(),
                "Xóa file?",
                "Bạn có chắc muốn xóa \"" + file.getName() + "\" không?",
                new DialogHelper.OnConfirm() {
                    @Override
                    public void onConfirm() {
                        if (file.delete()) {
                            Toast.makeText(requireContext(), "Đã xóa file.", Toast.LENGTH_SHORT).show();
                            taiDanhSachFile();
                        } else {
                            Toast.makeText(requireContext(), "Không thể xóa file.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    String layMimeType(String tenFile) {
        String ten = tenFile.toLowerCase();
        if (ten.endsWith(".pdf")) return "application/pdf";
        if (ten.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (ten.endsWith(".doc")) return "application/msword";
        if (ten.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (ten.endsWith(".xls")) return "application/vnd.ms-excel";
        if (ten.endsWith(".txt")) return "text/plain";
        return "*/*";
    }

    /**
     * Tải danh sách đường dẫn file yêu thích từ Room Database vào bộ nhớ tạm (Set).
     * Chạy trên background thread, refresh adapter trên main thread khi xong.
     */
    void taiYeuThichTuRoom() {
        executorFiles.execute(new Runnable() {
            @Override
            public void run() {
                List<String> paths = savedFileDao.getAllFavoritePaths();
                favoritePaths.clear();
                favoritePaths.addAll(paths);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (adapterFile != null) {
                                adapterFile.notifyDataSetChanged();
                            }
                        }
                    });
                }
            }
        });
    }

    // Lấy đuôi file (pdf, docx, txt...) từ tên file
    String layDuoiFile(String tenFile) {
        if (tenFile == null) return "";
        int idx = tenFile.lastIndexOf('.');
        return idx > 0 ? tenFile.substring(idx + 1).toLowerCase() : "";
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Dọn dẹp executor khi fragment bị hủy view
        if (executorFiles != null && !executorFiles.isShutdown()) {
            executorFiles.shutdown();
        }
    }

    String dinhDangKichThuoc(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024));
    }


    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            taiDanhSachFile();
        }
    }
}
