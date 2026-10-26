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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartdocs.R;
import com.example.smartdocs.data.model.ScanDocument;
import com.example.smartdocs.ui.adapter.DocumentAdapter;
import com.example.smartdocs.ui.viewmodel.DocumentViewModel;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

public class HistoryFragment extends Fragment {

    // Views
    ImageView imgBackHistory;
    TextInputEditText edtSearch;
    TextView txtDocCount;
    RecyclerView recyclerHistory;
    LinearLayout layoutEmpty;

    // ViewModel & Adapter
    DocumentViewModel viewModel;
    DocumentAdapter adapter;

    // Biến lưu tài liệu vừa xóa để phục vụ Undo
    ScanDocument documentVuaXoa;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1. Ánh xạ view
        anhXaView(view);

        // 2. Nút Back về Home tab
        imgBackHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).chonTab(R.id.nav_home);
                }
            }
        });

        // 3. Khởi tạo ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(DocumentViewModel.class);

        // 4. Thiết lập RecyclerView + Adapter
        thietLapRecyclerView();

        // 5. Observe dữ liệu từ Room
        observeViewModel();

        // 6. Thiết lập tìm kiếm realtime
        thietLapTimKiem();

        // 7. Thiết lập vuốt xóa
        thietLapVuotXoa();
    }

    void anhXaView(View view) {
        imgBackHistory = (ImageView) view.findViewById(R.id.imgBackHistory);
        edtSearch = (TextInputEditText) view.findViewById(R.id.edtSearch);
        txtDocCount = (TextView) view.findViewById(R.id.txtDocCount);
        recyclerHistory = (RecyclerView) view.findViewById(R.id.recyclerHistory);
        layoutEmpty = (LinearLayout) view.findViewById(R.id.layoutEmpty);
    }

    void thietLapRecyclerView() {
        adapter = new DocumentAdapter();
        recyclerHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerHistory.setAdapter(adapter);
        recyclerHistory.setHasFixedSize(false);

        // Click mở xem chi tiết
        adapter.setOnItemClickListener(new DocumentAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(ScanDocument document, int position) {
                Intent i = DocumentPreviewActivity.taoIntent(requireContext(), document);
                startActivity(i);
            }
        });

        // Long click: xác nhận xóa
        adapter.setOnItemLongClickListener(new DocumentAdapter.OnItemLongClickListener() {
            @Override
            public void onItemLongClick(ScanDocument document, int position) {
                xacNhanXoa(document);
            }
        });

        // Menu ⋮
        adapter.setOnMenuClickListener(new DocumentAdapter.OnMenuClickListener() {
            @Override
            public void onMenuClick(ScanDocument document, int position, View anchorView) {
                hienPopupMenu(document, anchorView);
            }
        });
    }

    void observeViewModel() {
        viewModel.getAllDocuments().observe(getViewLifecycleOwner(), documents -> {
            if (documents == null || documents.isEmpty()) {
                layoutEmpty.setVisibility(View.VISIBLE);
                recyclerHistory.setVisibility(View.GONE);
                txtDocCount.setText("Chưa có tài liệu nào");
            } else {
                layoutEmpty.setVisibility(View.GONE);
                recyclerHistory.setVisibility(View.VISIBLE);
                adapter.submitList(documents);
                int soLuong = documents.size();
                txtDocCount.setText(soLuong + " tài liệu");
            }
        });
    }

    void thietLapTimKiem() {
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String keyword = s.toString().trim();

                if (keyword.isEmpty()) {
                    viewModel.getAllDocuments().observe(getViewLifecycleOwner(), documents -> {
                        if (documents != null) {
                            adapter.submitList(documents);
                            capNhatSoLuong(documents);
                        }
                    });
                } else {
                    viewModel.search(keyword).observe(getViewLifecycleOwner(), documents -> {
                        if (documents != null) {
                            adapter.submitList(documents);
                            capNhatSoLuong(documents);
                            if (documents.isEmpty()) {
                                layoutEmpty.setVisibility(View.VISIBLE);
                                recyclerHistory.setVisibility(View.GONE);
                                txtDocCount.setText("Không tìm thấy kết quả");
                            } else {
                                layoutEmpty.setVisibility(View.GONE);
                                recyclerHistory.setVisibility(View.VISIBLE);
                            }
                        }
                    });
                }
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });
    }

    void thietLapVuotXoa() {
        ItemTouchHelper.SimpleCallback swipeCallback =
                new ItemTouchHelper.SimpleCallback(0,
                        ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

                    @Override
                    public boolean onMove(@NonNull RecyclerView recyclerView,
                                          @NonNull RecyclerView.ViewHolder viewHolder,
                                          @NonNull RecyclerView.ViewHolder target) {
                        return false;
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder,
                                         int direction) {
                        int position = viewHolder.getAdapterPosition();
                        documentVuaXoa = adapter.getCurrentList().get(position);
                        viewModel.deleteDocument(documentVuaXoa);

                        if (getView() != null) {
                            Snackbar.make(
                                    getView(),
                                    "Đã xóa: " + documentVuaXoa.title,
                                    Snackbar.LENGTH_LONG
                            ).setAction("Hoàn tác", new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    viewModel.getRepository().insert(documentVuaXoa, null);
                                }
                            }).show();
                        }
                    }
                };

        new ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerHistory);
    }

    void hienDialogChiTiet(ScanDocument document) {
        StringBuilder sb = new StringBuilder();
        sb.append("VĂN BẢN:\n");
        sb.append("─────────────────\n");
        sb.append(document.ocrText != null ? document.ocrText : "(Không có)");

        if (document.aiSummary != null && !document.aiSummary.trim().isEmpty()) {
            sb.append("\n\nTÓM TẮT AI:\n");
            sb.append("─────────────────\n");
            sb.append(document.aiSummary);
        }

        android.widget.ScrollView scrollView = new android.widget.ScrollView(requireContext());
        TextView txtContent = new TextView(requireContext());
        txtContent.setText(sb.toString());
        txtContent.setTextSize(14f);
        txtContent.setLineSpacing(4f, 1f);
        txtContent.setTextIsSelectable(true);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        txtContent.setPadding(padding, padding, padding, padding);
        scrollView.addView(txtContent);

        new AlertDialog.Builder(requireContext())
                .setTitle(document.title)
                .setView(scrollView)
                .setPositiveButton("Đóng", null)
                .setNeutralButton("Xuất PDF", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        xuatPdf(document);
                    }
                })
                .setNegativeButton("Xóa", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        xacNhanXoa(document);
                    }
                })
                .show();
    }

    void xuatPdf(ScanDocument document) {
        Toast.makeText(requireContext(), "Đang tạo PDF...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                Uri pdfUri = PdfExporter.exportToPdf(requireContext(), document);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(),
                                "Tạo PDF thành công!", Toast.LENGTH_SHORT).show();
                        Intent shareIntent = PdfExporter.taoIntentShare(pdfUri);
                        startActivity(shareIntent);
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(),
                                "Lỗi tạo PDF: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    });
                }
            }
        }).start();
    }

    void xacNhanXoa(ScanDocument document) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Xóa tài liệu?")
                .setMessage("Bạn có chắc muốn xóa \"" + document.title + "\" không?")
                .setPositiveButton("Xóa", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        viewModel.deleteDocument(document);
                        if (getView() != null) {
                            Snackbar.make(recyclerHistory,
                                    "Đã xóa tài liệu.", Snackbar.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    void capNhatSoLuong(List<ScanDocument> documents) {
        int soLuong = documents.size();
        if (soLuong == 0) return;
        txtDocCount.setText(soLuong + " tài liệu");
    }

    void hienPopupMenu(ScanDocument document, View anchorView) {
        String[] options = {
                "Xem chi tiết",
                "Xuất PDF",
                "Xóa"
        };

        new AlertDialog.Builder(requireContext())
                .setTitle(document.title != null ? document.title : "Tùy chọn")
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            hienDialogChiTiet(document);
                        } else if (which == 1) {
                            xuatPdf(document);
                        } else if (which == 2) {
                            xacNhanXoa(document);
                        }
                    }
                })
                .show();
    }
}
