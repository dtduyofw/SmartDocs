package com.example.smartdocs.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
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
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

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

        // 8. Click item
        lvFiles.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                File file = danhSachFileHienThi.get(position);
                if (dangChonNhieu) {
                    toggleChonItem(file, view);
                } else {
                    moFile(file);
                }
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
                ImageView imgStar = (ImageView) convertView.findViewById(R.id.imgFileStar);
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

                boolean isFav = favoritePaths.contains(file.getAbsolutePath());
                imgStar.setImageResource(isFav
                        ? R.drawable.ic_star_on
                        : R.drawable.ic_star_off);
                imgStar.setAlpha(isFav ? 1.0f : 0.4f);

                checkbox.setVisibility(dangChonNhieu ? View.VISIBLE : View.GONE);
                checkbox.setChecked(selectedPaths.contains(file.getAbsolutePath()));

                convertView.setBackgroundColor(
                        selectedPaths.contains(file.getAbsolutePath())
                                ? 0x220D6EDF : 0x00000000);

                final File fileFinal = file;
                final ImageView imgStarFinal = imgStar;
                imgStar.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String path = fileFinal.getAbsolutePath();
                        if (favoritePaths.contains(path)) {
                            favoritePaths.remove(path);
                            imgStarFinal.setImageResource(R.drawable.ic_star_off);
                            imgStarFinal.setAlpha(0.4f);
                            Toast.makeText(requireContext(), "Đã bỏ yêu thích", Toast.LENGTH_SHORT).show();
                            // Xóa khỏi Room trên background thread
                            executorFiles.execute(new Runnable() {
                                @Override
                                public void run() {
                                    savedFileDao.deleteByPath(path);
                                }
                            });
                        } else {
                            favoritePaths.add(path);
                            imgStarFinal.setImageResource(R.drawable.ic_star_on);
                            imgStarFinal.setAlpha(1.0f);
                            Toast.makeText(requireContext(), "Đã thêm vào yêu thích", Toast.LENGTH_SHORT).show();
                            // Lưu vào Room trên background thread
                            String tenFile = fileFinal.getName();
                            String duoiFile = layDuoiFile(tenFile);
                            executorFiles.execute(new Runnable() {
                                @Override
                                public void run() {
                                    SavedFile sf = new SavedFile(path, tenFile, duoiFile, System.currentTimeMillis());
                                    savedFileDao.insert(sf);
                                }
                            });
                        }
                    }
                });

                imgMenu.setVisibility(dangChonNhieu ? View.GONE : View.VISIBLE);
                imgMenu.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        hienMenuFile(fileFinal);
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
        new AlertDialog.Builder(requireContext())
                .setTitle("Xóa " + soLuong + " file?")
                .setMessage("Hành động này không thể hoàn tác.")
                .setPositiveButton("Xóa tất cả", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
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
                })
                .setNegativeButton("Hủy", null)
                .show();
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
            new AlertDialog.Builder(requireContext())
                .setTitle(file.getName())
                .setMessage("Chọn cách xem file PDF này:")
                .setPositiveButton("Xem trong app", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            hienWebViewPdf(file);
                        }
                    })
                    .setNeutralButton("Mở bằng app khác", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            moFileBangAppNgoai(file);
                        }
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
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

        new AlertDialog.Builder(requireContext())
                .setTitle(file.getName())
                .setView(webView)
                .setPositiveButton("Đóng", null)
                .setNeutralButton("Chia sẻ", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        chiaSe(file);
                    }
                })
                .show();
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
        new AlertDialog.Builder(requireContext())
                .setTitle(file.getName())
                .setItems(new String[]{
                        "Mở file",
                        "Đổi tên",
                        "Chia sẻ",
                        "Xóa"
                }, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            moFile(file);
                        } else if (which == 1) {
                            hienDialogDoiTen(file);
                        } else if (which == 2) {
                            chiaSe(file);
                        } else if (which == 3) {
                            xacNhanXoaMotFile(file);
                        }
                    }
                })
                .show();
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

        android.widget.EditText edtTenMoi = new android.widget.EditText(requireContext());
        edtTenMoi.setText(tenCu);
        edtTenMoi.setSelection(edtTenMoi.getText().length());
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        edtTenMoi.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(requireContext())
                .setTitle("Đổi tên file")
                .setMessage("Đuôi file sẽ giữ nguyên: " + duoiFinal)
                .setView(edtTenMoi)
                .setPositiveButton("Đổi tên", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String tenMoi = edtTenMoi.getText().toString().trim();
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
                })
                .setNegativeButton("Hủy", null)
                .show();
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
        new AlertDialog.Builder(requireContext())
                .setTitle("Xóa file?")
                .setMessage("Bạn có chắc muốn xóa \"" + file.getName() + "\" không?")
                .setPositiveButton("Xóa", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (file.delete()) {
                            Toast.makeText(requireContext(), "Đã xóa file.", Toast.LENGTH_SHORT).show();
                            taiDanhSachFile();
                        } else {
                            Toast.makeText(requireContext(), "Không thể xóa file.", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
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
