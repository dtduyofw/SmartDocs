package com.example.smartdocs.ui;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.smartdocs.R;
import com.example.smartdocs.data.dao.AppDatabase;
import com.example.smartdocs.data.model.DocumentPage;
import com.example.smartdocs.data.model.QaHistory;
import com.example.smartdocs.data.model.QaPair;
import com.example.smartdocs.data.model.ScanDocument;

import java.util.List;
import com.example.smartdocs.api.GeminiRepository;
import com.example.smartdocs.ui.adapter.QaAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

/*
  DocumentPreviewActivity — màn hình xem chi tiết và tương tác tài liệu sau khi quét:
  - Đối chiếu Ảnh gốc / Văn bản trích xuất
  - Trợ lý AI: Tóm tắt, Dịch tiếng Việt, Trích xuất thông tin
  - Smart Q&A: Hỏi đáp thông minh với Gemini về nội dung tài liệu
  - Chỉnh sửa nhanh văn bản
  - Xuất file đa định dạng: Word / PDF / Excel / Text
 */
public class DocumentPreviewActivity extends AppCompatActivity {

    // Keys nhận dữ liệu từ Intent
    public static final String KEY_DOC_ID = "doc_id";
    public static final String KEY_DOC_TITLE = "doc_title";
    public static final String KEY_DOC_OCR = "doc_ocr";
    public static final String KEY_DOC_AI = "doc_ai";
    public static final String KEY_DOC_IMAGE_URI = "doc_image_uri";
    public static final String KEY_DOC_CREATED_AT = "doc_created_at";
    public static final String KEY_DOC_PAGE_COUNT = "doc_page_count";

    // ─────────────────────────────────────────
    // Views
    // ─────────────────────────────────────────
    ImageView imgBackPreview;
    ImageView btnHeaderShare, btnHeaderCopy, btnHeaderDownload;
    TextView tabOriginal, tabExtracted, tabAi;
    TextView txtStatusBadge;
    TextView txtExtractedContent;
    ImageView imgOriginal;
    MaterialCardView cardExtractedText, cardOriginalImage;
    ScrollView scrollViewContent;

    // Dieu huong trang cho tai lieu nhieu trang
    LinearLayout layoutDieuHuongTrang;
    MaterialButton btnTrangTruoc, btnTrangSau;
    TextView txtChiSoTrang;

    // AI Views (mới — tab chat full-screen)
    LinearLayout layoutAiTab, layoutAiLoading;
    TextView txtAiLoading;
    LinearLayout layoutQaPlaceholder;
    Chip chipTomTat, chipDichTV, chipTrichXuat, chipNoiDungChinh, chipSoLieu;

    // Q&A Views
    ListView lvQaHistory;
    TextView txtQaPlaceholder;
    TextInputEditText edtCauHoi;
    MaterialButton btnGuiCauHoi, btnClearQa;
    QaAdapter qaAdapter;
    ArrayList<QaPair> listQa;

    // Toolbar hành động
    LinearLayout layoutBottomToolbar;
    View viewDividerBottom;
    LinearLayout btnEdit, btnNew, btnShare, btnExport;

    // Section Export As
    LinearLayout layoutExportAs;
    LinearLayout exportWord, exportPdf, exportExcel, exportText;

    // ─────────────────────────────────────────
    // Data
    // ─────────────────────────────────────────
    ScanDocument document;
    List<DocumentPage> danhSachPages;
    int viTriTrangHienTai = 0;
    boolean dangHienExtracted = true; // Tab đang active

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_document_preview);

        // 1. Lấy dữ liệu từ Intent
        nhanDuLieuTuIntent();

        // 2. Ánh xạ view
        anhXaView();

        // 3. Hiển thị dữ liệu
        hienThiDuLieu();

        // 4. Gán sự kiện
        ganSuKien();

        // 5. Tải lịch sử Q&A đã lưu từ Room Database
        taiLichSuQa();
    }

    // ─────────────────────────────────────────
    // Setup
    // ─────────────────────────────────────────

    /**
     * Nhận các field của ScanDocument từ Intent Bundle.
     */
    void nhanDuLieuTuIntent() {
        Bundle bundle = getIntent().getExtras();
        if (bundle == null) {
            Toast.makeText(this, "Lỗi: không có dữ liệu tài liệu.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Xây dựng lại ScanDocument từ các key riêng lẻ
        document = new ScanDocument(
                bundle.getString(KEY_DOC_IMAGE_URI, ""),
                bundle.getString(KEY_DOC_OCR, ""),
                bundle.getString(KEY_DOC_AI, ""),
                bundle.getLong(KEY_DOC_CREATED_AT, System.currentTimeMillis()),
                bundle.getString(KEY_DOC_TITLE, "Tài liệu")
        );
        document.id = bundle.getInt(KEY_DOC_ID, 0);
        document.pageCount = bundle.getInt(KEY_DOC_PAGE_COUNT, 1);
    }

    void anhXaView() {
        imgBackPreview = (ImageView) findViewById(R.id.imgBackPreview);
        btnHeaderShare = (ImageView) findViewById(R.id.btnHeaderShare);
        btnHeaderCopy = (ImageView) findViewById(R.id.btnHeaderCopy);
        btnHeaderDownload = (ImageView) findViewById(R.id.btnHeaderDownload);
        tabOriginal = (TextView) findViewById(R.id.tabOriginal);
        tabExtracted = (TextView) findViewById(R.id.tabExtracted);
        tabAi = (TextView) findViewById(R.id.tabAi);
        txtStatusBadge = (TextView) findViewById(R.id.txtStatusBadge);
        txtExtractedContent = (TextView) findViewById(R.id.txtExtractedContent);
        imgOriginal = (ImageView) findViewById(R.id.imgOriginal);
        cardExtractedText = (MaterialCardView) findViewById(R.id.cardExtractedText);
        cardOriginalImage = (MaterialCardView) findViewById(R.id.cardOriginalImage);
        scrollViewContent = (ScrollView) findViewById(R.id.scrollViewContent);
        layoutDieuHuongTrang = (LinearLayout) findViewById(R.id.layoutDieuHuongTrang);
        btnTrangTruoc = (MaterialButton) findViewById(R.id.btnTrangTruoc);
        btnTrangSau = (MaterialButton) findViewById(R.id.btnTrangSau);
        txtChiSoTrang = (TextView) findViewById(R.id.txtChiSoTrang);

        // Ánh xạ layoutAiTab và các view bên trong
        layoutAiTab = (LinearLayout) findViewById(R.id.layoutAiTab);
        layoutAiLoading = (LinearLayout) findViewById(R.id.layoutAiLoading);
        txtAiLoading = (TextView) findViewById(R.id.txtAiLoading);
        layoutQaPlaceholder = (LinearLayout) findViewById(R.id.layoutQaPlaceholder);
        chipTomTat = (Chip) findViewById(R.id.chipTomTat);
        chipDichTV = (Chip) findViewById(R.id.chipDichTV);
        chipTrichXuat = (Chip) findViewById(R.id.chipTrichXuat);
        chipNoiDungChinh = (Chip) findViewById(R.id.chipNoiDungChinh);
        chipSoLieu = (Chip) findViewById(R.id.chipSoLieu);

        // Ánh xạ Smart Q&A Views
        lvQaHistory = (ListView) findViewById(R.id.lvQaHistory);
        txtQaPlaceholder = (TextView) findViewById(R.id.txtQaPlaceholder);
        edtCauHoi = (TextInputEditText) findViewById(R.id.edtCauHoi);
        btnGuiCauHoi = (MaterialButton) findViewById(R.id.btnGuiCauHoi);
        btnClearQa = (MaterialButton) findViewById(R.id.btnClearQa);

        // Khởi tạo adapter cho chat
        listQa = new ArrayList<>();
        qaAdapter = new QaAdapter(this, listQa);
        lvQaHistory.setAdapter(qaAdapter);

        // Toolbar hành động
        layoutBottomToolbar = (LinearLayout) findViewById(R.id.layoutBottomToolbar);
        viewDividerBottom = findViewById(R.id.viewDividerBottom);
        btnEdit = (LinearLayout) findViewById(R.id.btnEdit);
        btnNew = (LinearLayout) findViewById(R.id.btnNew);
        btnShare = (LinearLayout) findViewById(R.id.btnShare);
        btnExport = (LinearLayout) findViewById(R.id.btnExport);
        layoutExportAs = (LinearLayout) findViewById(R.id.layoutExportAs);
        exportWord = (LinearLayout) findViewById(R.id.exportWord);
        exportPdf = (LinearLayout) findViewById(R.id.exportPdf);
        exportExcel = (LinearLayout) findViewById(R.id.exportExcel);
        exportText = (LinearLayout) findViewById(R.id.exportText);
    }

    void hienThiDuLieu() {
        if (document == null) return;

        // Tab Extracted mặc định
        hienTabExtracted();

        // Tai danh sach cac trang neu co
        taiDanhSachTrang();

        // Badge status — chỉ cho biết trạng thái OCR/AI
        boolean coAi = document.aiSummary != null && !document.aiSummary.trim().isEmpty();
        if (coAi) {
            txtStatusBadge.setText("Đã trích xuất AI");
            txtStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_badge_green));
        } else {
            txtStatusBadge.setText("Đã trích xuất văn bản");
            txtStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_badge_blue));
        }
    }

    void ganSuKien() {
        // Nut chuyen trang truoc / sau
        btnTrangTruoc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                capNhatHienThiTrang(viTriTrangHienTai - 1);
            }
        });

        btnTrangSau.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                capNhatHienThiTrang(viTriTrangHienTai + 1);
            }
        });

        // ── Nút Back ──────────────────────────────────────
        imgBackPreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        // ── Cụm icon Header: Chia sẻ, Sao chép, Tải xuống ──
        if (btnHeaderShare != null) {
            btnHeaderShare.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    chiaSePlainText();
                }
            });
        }

        if (btnHeaderCopy != null) {
            btnHeaderCopy.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String text = (document != null && document.ocrText != null) ? document.ocrText : "";
                    if (text.isEmpty()) {
                        Toast.makeText(DocumentPreviewActivity.this, "Chưa có văn bản để sao chép.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Document Text", text);
                    if (cm != null) {
                        cm.setPrimaryClip(clip);
                        Toast.makeText(DocumentPreviewActivity.this, "Đã sao chép toàn bộ văn bản.", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        if (btnHeaderDownload != null) {
            btnHeaderDownload.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (layoutExportAs != null) {
                        if (layoutExportAs.getVisibility() == View.VISIBLE) {
                            layoutExportAs.setVisibility(View.GONE);
                        } else {
                            layoutExportAs.setVisibility(View.VISIBLE);
                        }
                    }
                }
            });
        }

        // ── Tab Original ──────────────────────────────────
        tabOriginal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hienTabOriginal();
            }
        });

        // ── Tab Extracted ─────────────────────────────────
        tabExtracted.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hienTabExtracted();
            }
        });

        // ── Tab Trợ lý AI ─────────────────────────────────
        tabAi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hienTabAi();
            }
        });

        // ── Chip nhanh: Tóm tắt ─────────────────────────
        chipTomTat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goiAiNhanhVaoChat("summarize");
            }
        });

        // ── Chip nhanh: Dịch TV ──────────────────────────
        chipDichTV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goiAiNhanhVaoChat("translate");
            }
        });

        // ── Chip nhanh: Trích xuất ───────────────────────
        chipTrichXuat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goiAiNhanhVaoChat("extract");
            }
        });

        // ── Chip gợi ý: Nội dung chính ───────────────────
        chipNoiDungChinh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                edtCauHoi.setText("Nội dung chính là gì?");
                guiCauHoiQa();
            }
        });

        // ── Chip gợi ý: Số liệu quan trọng ──────────────
        chipSoLieu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                edtCauHoi.setText("Trích xuất các số liệu quan trọng");
                guiCauHoiQa();
            }
        });

        // ── Q&A: Gửi câu hỏi (nút Gửi) ──────────────────
        btnGuiCauHoi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                guiCauHoiQa();
            }
        });

        // ── Q&A: Enter trên bàn phím ──────────────────────
        edtCauHoi.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    guiCauHoiQa();
                    return true;
                }
                return false;
            }
        });

        // ── Q&A: Xóa lịch sử chat ────────────────────────
        btnClearQa.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listQa.clear();
                qaAdapter.notifyDataSetChanged();
                lvQaHistory.setVisibility(View.GONE);
                layoutQaPlaceholder.setVisibility(View.VISIBLE);
                Toast.makeText(DocumentPreviewActivity.this, "Đã xóa lịch sử hỏi đáp.", Toast.LENGTH_SHORT).show();
                // Xóa lịch sử Q&A của tài liệu này khỏi Room Database
                if (document != null && document.id > 0) {
                    Executors.newSingleThreadExecutor().execute(new Runnable() {
                        @Override
                        public void run() {
                            AppDatabase.getInstance(DocumentPreviewActivity.this)
                                    .qaHistoryDao()
                                    .deleteByDocumentId(document.id);
                        }
                    });
                }
            }
        });

        btnEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hienDialogEdit();
            }
        });

        View btnQuickEditCard = findViewById(R.id.btnQuickEditCard);
        if (btnQuickEditCard != null) {
            btnQuickEditCard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hienDialogEdit();
                }
            });
        }

        // ── New — quay về Home để tạo scan mới ───────────
        btnNew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(DocumentPreviewActivity.this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(i);
                finish();
            }
        });

        // ── Share — chia sẻ text OCR ──────────────────────
        btnShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chiaSePlainText();
            }
        });

        // ── Export — toggle/ẩn hiện section Export As ────
        btnExport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Toggle section Export As
                if (layoutExportAs.getVisibility() == View.VISIBLE) {
                    layoutExportAs.setVisibility(View.GONE);
                } else {
                    layoutExportAs.setVisibility(View.VISIBLE);
                }
            }
        });

        // ── Export Word ───────────────────────────────────
        exportWord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                xuatFile("word");
            }
        });

        // ── Export PDF ────────────────────────────────────
        exportPdf.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                xuatFile("pdf");
            }
        });

        // ── Export Excel ──────────────────────────────────
        exportExcel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                xuatFile("excel");
            }
        });

        // ── Export Text ───────────────────────────────────
        exportText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                xuatFile("text");
            }
        });
    }

    // ─────────────────────────────────────────
    // Tab logic
    // ─────────────────────────────────────────

    /**
     * Hiện tab Extracted: text OCR, ẩn ảnh gốc.
     */
    void hienTabExtracted() {
        dangHienExtracted = true;

        // Cập nhật style tab — tab Văn bản active
        tabExtracted.setBackgroundColor(ContextCompat.getColor(this, R.color.primary));
        tabExtracted.setTextColor(ContextCompat.getColor(this, R.color.white));
        tabOriginal.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        tabOriginal.setTextColor(ContextCompat.getColor(this, R.color.text_hint));
        tabAi.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        tabAi.setTextColor(ContextCompat.getColor(this, R.color.text_hint));

        // Hiện text, ẩn ảnh, ẩn khu vực AI
        if (scrollViewContent != null) scrollViewContent.setVisibility(View.VISIBLE);
        cardExtractedText.setVisibility(View.VISIBLE);
        cardOriginalImage.setVisibility(View.GONE);
        if (layoutAiTab != null) {
            layoutAiTab.setVisibility(View.GONE);
        }
        if (layoutBottomToolbar != null) layoutBottomToolbar.setVisibility(View.VISIBLE);
        if (viewDividerBottom != null) viewDividerBottom.setVisibility(View.VISIBLE);
        if (txtStatusBadge != null) txtStatusBadge.setVisibility(View.VISIBLE);

        // Điền nội dung
        if (document != null) {
            String noidung = (document.ocrText != null && !document.ocrText.isEmpty())
                    ? document.ocrText : "(Không có nội dung OCR)";
            txtExtractedContent.setText(noidung);
        }
    }

    /**
     * Hiện tab Original: ảnh gốc, ẩn text OCR.
     */
    void hienTabOriginal() {
        dangHienExtracted = false;

        // Cập nhật style tab — tab Ảnh gốc active
        tabOriginal.setBackgroundColor(ContextCompat.getColor(this, R.color.primary));
        tabOriginal.setTextColor(ContextCompat.getColor(this, R.color.white));
        tabExtracted.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        tabExtracted.setTextColor(ContextCompat.getColor(this, R.color.text_hint));
        tabAi.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        tabAi.setTextColor(ContextCompat.getColor(this, R.color.text_hint));

        // Hiện ảnh, ẩn text và khu vực AI
        if (scrollViewContent != null) scrollViewContent.setVisibility(View.VISIBLE);
        cardExtractedText.setVisibility(View.GONE);
        cardOriginalImage.setVisibility(View.VISIBLE);
        if (layoutAiTab != null) {
            layoutAiTab.setVisibility(View.GONE);
        }
        if (layoutBottomToolbar != null) layoutBottomToolbar.setVisibility(View.VISIBLE);
        if (viewDividerBottom != null) viewDividerBottom.setVisibility(View.VISIBLE);
        if (txtStatusBadge != null) txtStatusBadge.setVisibility(View.VISIBLE);

        // Load ảnh gốc
        if (danhSachPages != null && danhSachPages.size() > 1) {
            layoutDieuHuongTrang.setVisibility(View.VISIBLE);
            capNhatHienThiTrang(viTriTrangHienTai);
        } else {
            layoutDieuHuongTrang.setVisibility(View.GONE);
            if (document != null && document.imageUri != null && !document.imageUri.isEmpty()) {
                hienThiAnhTrang(document.imageUri);
            } else {
                imgOriginal.setImageResource(R.drawable.ic_gallery);
            }
        }
    }

    // Hiện tab Trợ lý AI: ẩn văn bản + ảnh, chỉ hiện khu vực AI + Q&A
    void hienTabAi() {
        dangHienExtracted = false;

        // Cập nhật style tab — tab Trợ lý AI active
        tabAi.setBackgroundColor(ContextCompat.getColor(this, R.color.primary));
        tabAi.setTextColor(ContextCompat.getColor(this, R.color.white));
        tabOriginal.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        tabOriginal.setTextColor(ContextCompat.getColor(this, R.color.text_hint));
        tabExtracted.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        tabExtracted.setTextColor(ContextCompat.getColor(this, R.color.text_hint));

        // Ẩn văn bản và ảnh, hiện khu vực AI + Q&A
        if (scrollViewContent != null) scrollViewContent.setVisibility(View.GONE);
        cardExtractedText.setVisibility(View.GONE);
        cardOriginalImage.setVisibility(View.GONE);
        layoutDieuHuongTrang.setVisibility(View.GONE);
        if (layoutAiTab != null) {
            layoutAiTab.setVisibility(View.VISIBLE);
        }
        if (layoutBottomToolbar != null) layoutBottomToolbar.setVisibility(View.GONE);
        if (viewDividerBottom != null) viewDividerBottom.setVisibility(View.GONE);
        if (layoutExportAs != null) layoutExportAs.setVisibility(View.GONE);
        if (txtStatusBadge != null) txtStatusBadge.setVisibility(View.GONE);
    }

    // Tai danh sach cac trang cua tai lieu tu co so du lieu
    void taiDanhSachTrang() {
        if (document == null || document.id <= 0) return;
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                List<DocumentPage> pages = AppDatabase.getInstance(DocumentPreviewActivity.this)
                        .documentPageDao().getPagesByDocumentId(document.id);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        danhSachPages = pages;
                        if (danhSachPages != null && danhSachPages.size() > 1) {
                            if (!dangHienExtracted) {
                                layoutDieuHuongTrang.setVisibility(View.VISIBLE);
                            }
                            capNhatHienThiTrang(0);
                        } else {
                            layoutDieuHuongTrang.setVisibility(View.GONE);
                        }
                    }
                });
            }
        });
    }

    // Cap nhat vi tri trang dang xem va hien thi anh tuong ung
    void capNhatHienThiTrang(int viTri) {
        if (danhSachPages == null || danhSachPages.isEmpty()) return;
        if (viTri < 0) viTri = 0;
        if (viTri >= danhSachPages.size()) viTri = danhSachPages.size() - 1;
        viTriTrangHienTai = viTri;

        txtChiSoTrang.setText("Trang " + (viTriTrangHienTai + 1) + " / " + danhSachPages.size());
        btnTrangTruoc.setEnabled(viTriTrangHienTai > 0);
        btnTrangSau.setEnabled(viTriTrangHienTai < danhSachPages.size() - 1);

        DocumentPage page = danhSachPages.get(viTriTrangHienTai);
        hienThiAnhTrang(page.imageUri);
    }

    // Load va hien thi anh bitmap tu duong dan uri
    void hienThiAnhTrang(String uriString) {
        if (uriString != null && !uriString.isEmpty()) {
            try {
                Uri uri = Uri.parse(uriString);
                android.graphics.Bitmap bitmap = com.example.smartdocs.ocr.BitmapUtils.layBitmapTuUri(this, uri);
                if (bitmap != null) {
                    bitmap = com.example.smartdocs.ocr.BitmapUtils.xoayTheoExif(this, uri, bitmap);
                    imgOriginal.setImageBitmap(bitmap);
                } else {
                    imgOriginal.setImageURI(uri);
                }
            } catch (Exception e) {
                imgOriginal.setImageResource(R.drawable.ic_gallery);
            }
        } else {
            imgOriginal.setImageResource(R.drawable.ic_gallery);
        }
    }

    // ─────────────────────────────────────────
    // Actions
    // ─────────────────────────────────────────

    /**
     * Dialog chỉnh sửa nhanh nội dung OCR (giao diện custom).
     */
    void hienDialogEdit() {
        Dialog dialog = DialogHelper.taoDialog(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_chinh_sua_van_ban, null);
        dialog.setContentView(dialogView);

        TextInputEditText edt = (TextInputEditText) dialogView.findViewById(R.id.edtNoiDungOcr);
        edt.setText(document.ocrText);
        if (document.ocrText != null) {
            edt.setSelection(document.ocrText.length());
        }

        dialogView.findViewById(R.id.btnHuyEdit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialogView.findViewById(R.id.btnCapNhatEdit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String vanBanMoi = edt.getText() != null ? edt.getText().toString() : "";
                document.ocrText = vanBanMoi;
                txtExtractedContent.setText(vanBanMoi);

                // Cập nhật vào Room Database trên background thread
                Executors.newSingleThreadExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        AppDatabase.getInstance(DocumentPreviewActivity.this)
                                .documentDao().updateOcrText(document.id, vanBanMoi, System.currentTimeMillis());
                    }
                });

                dialog.dismiss();
                Toast.makeText(DocumentPreviewActivity.this, "Đã cập nhật và lưu văn bản.", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    // ─────────────────────────────────────────
    // AI & Smart Q&A Handlers
    // ─────────────────────────────────────────

    // Vô hiệu hoá/kích hoạt tất cả chip và nút gửi khi AI đang chạy
    void setChipsEnabled(boolean enabled) {
        chipTomTat.setEnabled(enabled);
        chipDichTV.setEnabled(enabled);
        chipTrichXuat.setEnabled(enabled);
        chipNoiDungChinh.setEnabled(enabled);
        chipSoLieu.setEnabled(enabled);
        btnGuiCauHoi.setEnabled(enabled);
    }

    // Gọi API AI chuyên dụng (tóm tắt/dịch/trích xuất) và đổ kết quả vào luồng chat
    void goiAiNhanhVaoChat(String loai) {
        com.example.smartdocs.data.QLTaiKhoan ql = com.example.smartdocs.data.QLTaiKhoan.getInstance(this);
        if (!ql.daDangNhap()) {
            DialogHelper.showConfirm(this, "Yêu cầu đăng nhập", "Bạn cần đăng nhập tài khoản để sử dụng tính năng AI. Vui lòng đăng nhập để tiếp tục.", "Đăng nhập ngay", new DialogHelper.OnConfirm() {
                @Override
                public void onConfirm() {
                    Intent i = new Intent(DocumentPreviewActivity.this, DangNhapActivity.class);
                    startActivity(i);
                }
            });
            return;
        }
        if (!ql.kiemTraConLuotAi()) {
            Toast.makeText(this, "Bạn đã dùng hết lượt AI (" + ql.getTongLuot() + " lượt). Vui lòng nâng cấp gói để tiếp tục.", Toast.LENGTH_LONG).show();
            return;
        }

        String vanBan = document.ocrText;
        if (vanBan == null || vanBan.trim().isEmpty()) {
            Toast.makeText(this, "Tài liệu không có văn bản để AI xử lý.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Xác định nhãn hiển thị trong chat
        String tenHanhDong;
        String loadingText;
        if ("summarize".equals(loai)) {
            tenHanhDong = "Tóm tắt nội dung tài liệu";
            loadingText = "Gemini đang tóm tắt...";
        } else if ("translate".equals(loai)) {
            tenHanhDong = "Dịch toàn bộ sang tiếng Việt";
            loadingText = "Gemini đang dịch...";
        } else {
            tenHanhDong = "Trích xuất thông tin chính";
            loadingText = "Gemini đang trích xuất...";
        }

        // Thêm bubble user + bubble AI "đang xử lý" vào chat
        listQa.add(new QaPair(tenHanhDong, true));
        final QaPair aiPair = new QaPair("Đang xử lý...", false);
        listQa.add(aiPair);
        qaAdapter.notifyDataSetChanged();
        lvQaHistory.setVisibility(View.VISIBLE);
        layoutQaPlaceholder.setVisibility(View.GONE);
        lvQaHistory.setSelection(listQa.size() - 1);

        layoutAiLoading.setVisibility(View.VISIBLE);
        txtAiLoading.setText(loadingText);
        setChipsEnabled(false);

        String apiKey = SettingsFragment.getApiKey(this);
        GeminiRepository.GeminiCallback callback = new GeminiRepository.GeminiCallback() {
            @Override
            public void onSuccess(String ketQua) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        layoutAiLoading.setVisibility(View.GONE);
                        setChipsEnabled(true);
                        // Tru 1 luot AI qua QLTaiKhoan
                        com.example.smartdocs.data.QLTaiKhoan.getInstance(DocumentPreviewActivity.this).truMotLuotAi();

                        aiPair.noiDung = ketQua;
                        qaAdapter.notifyDataSetChanged();
                        lvQaHistory.setSelection(listQa.size() - 1);

                        // Cập nhật badge và lưu aiSummary vào DB
                        document.aiSummary = ketQua;
                        txtStatusBadge.setText("Đã trích xuất AI");
                        txtStatusBadge.setTextColor(ContextCompat.getColor(DocumentPreviewActivity.this, R.color.status_badge_green));
                        Executors.newSingleThreadExecutor().execute(new Runnable() {
                            @Override
                            public void run() {
                                AppDatabase.getInstance(DocumentPreviewActivity.this)
                                        .documentDao().updateAiSummary(document.id, ketQua, System.currentTimeMillis());
                            }
                        });
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        layoutAiLoading.setVisibility(View.GONE);
                        setChipsEnabled(true);
                        aiPair.noiDung = "Lỗi: " + errorMessage;
                        qaAdapter.notifyDataSetChanged();
                    }
                });
            }
        };

        if ("summarize".equals(loai)) {
            GeminiRepository.getInstance().summarizeText(apiKey, vanBan, callback);
        } else if ("translate".equals(loai)) {
            GeminiRepository.getInstance().translateToVietnamese(apiKey, vanBan, callback);
        } else {
            GeminiRepository.getInstance().extractKeyInfo(apiKey, vanBan, callback);
        }
    }

    void guiCauHoiQa() {
        com.example.smartdocs.data.QLTaiKhoan ql = com.example.smartdocs.data.QLTaiKhoan.getInstance(this);
        if (!ql.daDangNhap()) {
            DialogHelper.showConfirm(this, "Yêu cầu đăng nhập", "Bạn cần đăng nhập tài khoản để đặt câu hỏi cho AI. Vui lòng đăng nhập để tiếp tục.", "Đăng nhập ngay", new DialogHelper.OnConfirm() {
                @Override
                public void onConfirm() {
                    Intent i = new Intent(DocumentPreviewActivity.this, DangNhapActivity.class);
                    startActivity(i);
                }
            });
            return;
        }
        if (!ql.kiemTraConLuotAi()) {
            Toast.makeText(this, "Bạn đã dùng hết lượt AI (" + ql.getTongLuot() + " lượt). Vui lòng nâng cấp gói để tiếp tục.", Toast.LENGTH_LONG).show();
            return;
        }

        String cauHoi = edtCauHoi.getText() != null ? edtCauHoi.getText().toString().trim() : "";
        if (cauHoi.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập câu hỏi.", Toast.LENGTH_SHORT).show();
            return;
        }

        String vanBan = document.ocrText;
        if (vanBan == null || vanBan.trim().isEmpty()) {
            Toast.makeText(this, "Tài liệu không có văn bản để trả lời.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Xây dựng lịch sử ngữ cảnh từ 2-3 cặp Q&A gần nhất (chỉ lấy tin nhắn đã hoàn thành)
        String chatHistory = xayDungLichSuNguCanh(2);

        edtCauHoi.setText("");
        listQa.add(new QaPair(cauHoi, true));
        final QaPair aiPair = new QaPair("Đang xử lý...", false);
        listQa.add(aiPair);
        qaAdapter.notifyDataSetChanged();
        lvQaHistory.setVisibility(View.VISIBLE);
        layoutQaPlaceholder.setVisibility(View.GONE);
        lvQaHistory.setSelection(listQa.size() - 1);

        String apiKey = SettingsFragment.getApiKey(this);
        // Vô hiệu hoá tất cả chip và nút gửi trong lúc chờ phản hồi
        setChipsEnabled(false);

        GeminiRepository.getInstance().askQuestion(apiKey, vanBan, cauHoi, chatHistory, new GeminiRepository.GeminiCallback() {
            @Override
            public void onSuccess(String answer) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setChipsEnabled(true);
                        // Tru 1 luot AI qua QLTaiKhoan
                        com.example.smartdocs.data.QLTaiKhoan.getInstance(DocumentPreviewActivity.this).truMotLuotAi();
                        aiPair.noiDung = answer;
                        qaAdapter.notifyDataSetChanged();
                        lvQaHistory.setSelection(listQa.size() - 1);
                        // Lưu cặp Q&A vào Room Database để tải lại khi mở màn hình sau
                        if (document != null && document.id > 0) {
                            String cauHoiLuu = cauHoi;
                            String answerLuu = answer;
                            Executors.newSingleThreadExecutor().execute(new Runnable() {
                                @Override
                                public void run() {
                                    QaHistory qaHistory = new QaHistory(
                                            document.id, cauHoiLuu, answerLuu,
                                            System.currentTimeMillis());
                                    AppDatabase.getInstance(DocumentPreviewActivity.this)
                                            .qaHistoryDao()
                                            .insert(qaHistory);
                                }
                            });
                        }
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setChipsEnabled(true);
                        aiPair.noiDung = "Lỗi: " + errorMessage;
                        qaAdapter.notifyDataSetChanged();
                    }
                });
            }
        });
    }

    /*
      Xây dựng chuỗi lịch sử hội thoại từ N cặp Q&A gần nhất (bỏ qua cặp đang chờ trả lời).
      Mỗi cặp gồm câu hỏi người dùng và câu trả lời AI đã hoàn thành.
      Dùng để đưa vào prompt giúp Gemini hiểu ngữ cảnh câu hỏi tiếp nối.
      @param soCapQa số cặp Q&A tối đa muốn lấy (khuyến nghị 2-3)
      @return chuỗi lịch sử dạng "Người dùng: ...\nAI: ..." hoặc null nếu không có
     */
    String xayDungLichSuNguCanh(int soCapQa) {
        if (listQa == null || listQa.isEmpty()) return null;

        // Thu thập các cặp Q&A hoàn chỉnh (bỏ qua "Đang xử lý...")
        ArrayList<String[]> capHoanChinh = new ArrayList<>();
        for (int i = 0; i + 1 < listQa.size(); i++) {
            QaPair q = listQa.get(i);
            QaPair a = listQa.get(i + 1);
            if (q.isUser && !a.isUser
                    && a.noiDung != null && !a.noiDung.startsWith("Đang xử lý")
                    && !a.noiDung.startsWith("Lỗi")) {
                capHoanChinh.add(new String[]{q.noiDung, a.noiDung});
                i++; // bước 2 vì đã xử lý cả cặp
            }
        }

        if (capHoanChinh.isEmpty()) return null;

        // Lấy N cặp gần nhất
        int batDau = Math.max(0, capHoanChinh.size() - soCapQa);
        StringBuilder sb = new StringBuilder();
        for (int i = batDau; i < capHoanChinh.size(); i++) {
            sb.append("Người dùng: ").append(capHoanChinh.get(i)[0]).append("\n");
            sb.append("AI: ").append(capHoanChinh.get(i)[1]).append("\n\n");
        }
        return sb.toString().trim();
    }

    /*
      Tải lịch sử Q&A đã lưu trong Room Database khi mở màn hình.
      Chạy trên background thread, cập nhật UI trên main thread.
     */
    void taiLichSuQa() {
        if (document == null || document.id <= 0) return;

        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                List<QaHistory> lichSu = AppDatabase.getInstance(DocumentPreviewActivity.this)
                        .qaHistoryDao()
                        .getByDocumentId(document.id);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!lichSu.isEmpty()) {
                            for (QaHistory qa : lichSu) {
                                listQa.add(new QaPair(qa.question, true));
                                listQa.add(new QaPair(qa.answer, false));
                            }
                            qaAdapter.notifyDataSetChanged();
                            lvQaHistory.setVisibility(View.VISIBLE);
                            if (layoutQaPlaceholder != null) {
                                layoutQaPlaceholder.setVisibility(View.GONE);
                            }
                            if (txtQaPlaceholder != null) {
                                txtQaPlaceholder.setVisibility(View.GONE);
                            }
                            lvQaHistory.setSelection(listQa.size() - 1);
                        }
                    }
                });
            }
        });
    }

    // Sao chep ket qua AI (da tich hop thang vao tin nhan chat)
    // void saoChepKetQuaAi() {
    //     String ketQua = txtAiResult.getText().toString();
    //     if (ketQua.isEmpty()) {
    //         Toast.makeText(this, "Chưa có kết quả để sao chép.", Toast.LENGTH_SHORT).show();
    //         return;
    //     }
    //     ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    //     ClipData clip = ClipData.newPlainText("AI Result", ketQua);
    //     if (clipboard != null) {
    //         clipboard.setPrimaryClip(clip);
    //         Toast.makeText(this, "Đã sao chép kết quả AI vào bộ nhớ tạm.", Toast.LENGTH_SHORT).show();
    //     }
    // }

    //Chia sẻ văn bản plain text qua các app khác.
    void chiaSePlainText() {
        String vanBan = (document.ocrText != null && !document.ocrText.isEmpty())
                ? document.ocrText : "(Không có nội dung)";
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, vanBan);
        intent.putExtra(Intent.EXTRA_SUBJECT, document.title);
        startActivity(Intent.createChooser(intent, "Chia sẻ văn bản qua..."));
    }

    /*
      Xuất file theo định dạng chỉ định, chạy trên background thread.
      @param loai "word" | "pdf" | "excel" | "text"
     */
    void xuatFile(String loai) {
        String tenLoai;
        switch (loai) {
            case "word": tenLoai = "Word (.docx)"; break;
            case "pdf": tenLoai = "PDF (.pdf)"; break;
            case "excel": tenLoai = "Excel (.xlsx)"; break;
            default: tenLoai = "Text (.txt)"; break;
        }
        Toast.makeText(this, "Đang tạo file " + tenLoai + "...", Toast.LENGTH_SHORT).show();

        // Chạy export trên background thread (IO-bound)
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Uri uri;
                    String tenFile;

                    switch (loai) {
                        case "word":
                            uri = ExportHelper.exportToDocx(DocumentPreviewActivity.this, document);
                            tenFile = "file.docx";
                            break;
                        case "pdf":
                            uri = PdfExporter.exportToPdf(DocumentPreviewActivity.this, document);
                            tenFile = "file.pdf";
                            break;
                        case "excel":
                            uri = ExportHelper.exportToXlsx(DocumentPreviewActivity.this, document);
                            tenFile = "file.xlsx";
                            break;
                        default:
                            uri = ExportHelper.exportToTxt(DocumentPreviewActivity.this, document);
                            tenFile = "file.txt";
                            break;
                    }

                    final Uri uriResult = uri;
                    final String tenFileResult = tenFile;

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(DocumentPreviewActivity.this,
                                    "Tạo file thành công!", Toast.LENGTH_SHORT).show();
                            // Mở dialog share
                            Intent shareIntent = ExportHelper.taoIntentShare(uriResult, tenFileResult);
                            startActivity(shareIntent);
                            // Ẩn section Export As sau khi chọn
                            layoutExportAs.setVisibility(View.GONE);
                        }
                    });

                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(DocumentPreviewActivity.this,
                                    "Lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    // ─────────────────────────────────────────
    // Static helper — mở màn hình này từ Activity khác
    // ─────────────────────────────────────────

    /*
      Tạo Intent để mở DocumentPreviewActivity với một ScanDocument.
      Dùng ở MainActivity, HistoryActivity.
     */
    public static Intent taoIntent(android.content.Context context, ScanDocument doc) {
        Intent intent = new Intent(context, DocumentPreviewActivity.class);
        Bundle bundle = new Bundle();
        bundle.putInt(KEY_DOC_ID, doc.id);
        bundle.putString(KEY_DOC_TITLE, doc.title);
        bundle.putString(KEY_DOC_OCR, doc.ocrText);
        bundle.putString(KEY_DOC_AI, doc.aiSummary);
        bundle.putString(KEY_DOC_IMAGE_URI, doc.imageUri);
        bundle.putLong(KEY_DOC_CREATED_AT, doc.createdAt);
        bundle.putInt(KEY_DOC_PAGE_COUNT, doc.pageCount);
        intent.putExtras(bundle);
        return intent;
    }
}
