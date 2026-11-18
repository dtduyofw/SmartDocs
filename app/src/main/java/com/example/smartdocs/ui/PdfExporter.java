package com.example.smartdocs.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Environment;

import androidx.core.content.FileProvider;

import com.example.smartdocs.data.dao.AppDatabase;
import com.example.smartdocs.data.model.DocumentPage;
import com.example.smartdocs.data.model.ScanDocument;
import com.example.smartdocs.ui.adapter.DocumentAdapter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/*
  PdfExporter — tạo file PDF từ ScanDocument dùng Android PdfDocument API (native, offline).
  Cấu trúc PDF:
    Trang 1: Ảnh gốc (nếu có)
    Trang 2+: Văn bản OCR (tự xuống dòng và phân trang)
    Trang cuối: Tóm tắt AI (nếu có)

  Không cần thư viện bên thứ 3.
  File lưu vào Downloads/ hoặc thư mục riêng của app.
 */
public class PdfExporter {

    // Kích thước trang A4 ở 72dpi (đơn vị point)
    static final int PAGE_WIDTH = 595;
    static final int PAGE_HEIGHT = 842;

    // Lề trang
    static final int MARGIN_LEFT = 40;
    static final int MARGIN_RIGHT = 40;
    static final int MARGIN_TOP = 50;
    static final int MARGIN_BOTTOM = 50;

    // Chiều rộng nội dung thực sự (trừ lề)
    static final int CONTENT_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT;

    // Khoảng cách dòng cho text thường
    static final float LINE_HEIGHT_BODY = 18f;
    static final float LINE_HEIGHT_TITLE = 28f;
    static final float LINE_HEIGHT_SECTION = 22f;

    /*
      Tạo file PDF từ ScanDocument và trả về Uri để share.
      @param context  Context để đọc ảnh và truy cập file system
      @param document Tài liệu cần xuất
      @return Uri của file PDF vừa tạo (qua FileProvider), hoặc null nếu lỗi
      @throws IOException Nếu không thể ghi file
     */
    public static Uri exportToPdf(Context context, ScanDocument document) throws IOException {
        PdfDocument pdfDoc = new PdfDocument();

        int soTrang = 1;

        // Trang anh goc (ho tro ca tai lieu 1 trang va nhieu trang)
        List<DocumentPage> pages = null;
        try {
            if (document.id > 0) {
                pages = AppDatabase.getInstance(context).documentPageDao().getPagesByDocumentId(document.id);
            }
        } catch (Exception e) {
            // Bo qua neu loi truy van DB
        }

        if (pages != null && !pages.isEmpty()) {
            for (DocumentPage p : pages) {
                if (p.imageUri != null && !p.imageUri.isEmpty()) {
                    Bitmap bitmap = docBitmap(context, p.imageUri);
                    if (bitmap != null) {
                        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                                PAGE_WIDTH, PAGE_HEIGHT, soTrang++).create();
                        PdfDocument.Page page = pdfDoc.startPage(pageInfo);
                        veAnhLenTrang(page.getCanvas(), bitmap);
                        pdfDoc.finishPage(page);
                        bitmap.recycle();
                    }
                }
            }
        } else if (document.imageUri != null && !document.imageUri.isEmpty()) {
            Bitmap bitmap = docBitmap(context, document.imageUri);
            if (bitmap != null) {
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                        PAGE_WIDTH, PAGE_HEIGHT, soTrang++).create();
                PdfDocument.Page page = pdfDoc.startPage(pageInfo);
                veAnhLenTrang(page.getCanvas(), bitmap);
                pdfDoc.finishPage(page);
                bitmap.recycle();
            }
        }

        //TRANG OCR: Văn bản nhận diện
        String cleanTitle = DocumentAdapter.chuanHoaTieuDe(document.title);
        if (document.ocrText != null && !document.ocrText.isEmpty()) {
            soTrang = veVanBanDaPhanTrang(pdfDoc, soTrang,
                    "VĂN BẢN NHẬN DIỆN", document.ocrText, cleanTitle);
        }

        // ── TRANG AI: Tóm tắt AI (nếu có) ──────────────────────────
        if (document.aiSummary != null && !document.aiSummary.trim().isEmpty()) {
            veVanBanDaPhanTrang(pdfDoc, soTrang,
                    "KẾT QUẢ PHÂN TÍCH AI", document.aiSummary, null);
        }

        // ── Lưu file PDF ─────────────────────────────────────────────
        File pdfFile = taoFilePdf(context, cleanTitle);
        try (FileOutputStream fos = new FileOutputStream(pdfFile)) {
            pdfDoc.writeTo(fos);
        } finally {
            pdfDoc.close();
        }

        // Trả về Uri an toàn qua FileProvider để share được với các app khác
        return FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                pdfFile
        );
    }

    // ─────────────────────────────────────────
    // Vẽ ảnh lên trang
    // ─────────────────────────────────────────

    /**
     * Vẽ bitmap lên canvas của trang PDF, co/giãn vừa với kích thước trang
     * và giữ nguyên tỷ lệ khung hình (aspect ratio).
     */
    static void veAnhLenTrang(Canvas canvas, Bitmap bitmap) {
        // Vẽ nền trắng
        Paint bgPaint = new Paint();
        bgPaint.setColor(Color.WHITE);
        canvas.drawRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT, bgPaint);

        // Tính kích thước tối đa giữ tỷ lệ
        int maxW = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT;
        int maxH = PAGE_HEIGHT - MARGIN_TOP - MARGIN_BOTTOM - 20; // -20 cho footer

        float scaleX = (float) maxW / bitmap.getWidth();
        float scaleY = (float) maxH / bitmap.getHeight();
        float scale = Math.min(scaleX, scaleY);

        int scaledW = (int) (bitmap.getWidth() * scale);
        int scaledH = (int) (bitmap.getHeight() * scale);

        // Căn giữa ảnh
        int startX = MARGIN_LEFT + (maxW - scaledW) / 2;
        int startY = MARGIN_TOP;

        // Scale và vẽ ảnh
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true);
        Paint imgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        canvas.drawBitmap(scaledBitmap, startX, startY, imgPaint);
        scaledBitmap.recycle();

        // Footer nhỏ
        Paint footerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        footerPaint.setColor(Color.GRAY);
        footerPaint.setTextSize(9f);
        canvas.drawText("Smart Document Scanner — Ảnh tài liệu gốc",
                MARGIN_LEFT, PAGE_HEIGHT - 20, footerPaint);
    }

    // ─────────────────────────────────────────
    // Vẽ văn bản có phân trang tự động
    // ─────────────────────────────────────────

    /*
      Vẽ văn bản dài lên nhiều trang PDF, tự xuống trang khi hết chỗ.
      Hỗ trợ render bảng biểu dạng lưới và tiêu đề/đoạn văn sạch không chứa thẻ HTML.
      @param pdfDoc    PdfDocument đang xây dựng
      @param soTrang   Số trang bắt đầu
      @param tieuDe    Tiêu đề section (VD: "VĂN BẢN NHẬN DIỆN")
      @param vanBan    Nội dung văn bản cần vẽ
      @param subTitle  Tiêu đề phụ (tên tài liệu), có thể null
      @return Số trang tiếp theo sau khi vẽ xong
     */
    static int veVanBanDaPhanTrang(PdfDocument pdfDoc, int soTrang,
                                    String tieuDe, String vanBan, String subTitle) {
        if (subTitle != null) {
            subTitle = DocumentAdapter.chuanHoaTieuDe(subTitle);
        }

        Paint paintTieuDe = taoTieuDePaint();
        Paint paintSubTitle = taoSubTitlePaint();
        Paint paintHeading = taoHeadingPaint();
        Paint paintBody = taoBodyPaint();
        Paint paintFooter = taoFooterPaint();

        Paint borderPaint = new Paint();
        borderPaint.setColor(Color.parseColor("#CCCCCC"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(0.8f);

        Paint headerBgPaint = new Paint();
        headerBgPaint.setColor(Color.parseColor("#EBF3FB"));
        headerBgPaint.setStyle(Paint.Style.FILL);

        Paint cellTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cellTextPaint.setColor(Color.parseColor("#212121"));

        Paint cellHeaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cellHeaderPaint.setColor(Color.parseColor("#0D47A1"));
        cellHeaderPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        Paint paintBg = new Paint();
        paintBg.setColor(Color.WHITE);

        List<ExportHelper.DocBlock> blocks = ExportHelper.phanTichNoiDung(vanBan);
        if (blocks.isEmpty()) {
            return soTrang;
        }

        // Bat dau trang dau tien cua section
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                PAGE_WIDTH, PAGE_HEIGHT, soTrang++).create();
        PdfDocument.Page page = pdfDoc.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        canvas.drawRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT, paintBg);

        float y = MARGIN_TOP;

        // Vẽ tiêu đề section ở đầu trang đầu tiên
        canvas.drawText(tieuDe, MARGIN_LEFT, y, paintTieuDe);
        y += LINE_HEIGHT_TITLE;

        if (subTitle != null && !subTitle.isEmpty()) {
            canvas.drawText(subTitle, MARGIN_LEFT, y, paintSubTitle);
            y += LINE_HEIGHT_SECTION;
        }

        // Kẻ đường ngang phân cách
        Paint linePaint = new Paint();
        linePaint.setColor(Color.LTGRAY);
        linePaint.setStrokeWidth(1f);
        canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, linePaint);
        y += 16f;

        float maxY = PAGE_HEIGHT - MARGIN_BOTTOM - 20; // -20 cho footer

        for (ExportHelper.DocBlock block : blocks) {
            if (block.type == ExportHelper.DocBlock.TYPE_TEXT) {
                Paint p = block.isHeading ? paintHeading : paintBody;
                float lineHeight = block.isHeading ? LINE_HEIGHT_SECTION : LINE_HEIGHT_BODY;

                List<String> wrapped = wrapText(block.text, p, CONTENT_WIDTH);
                for (String line : wrapped) {
                    if (y + lineHeight > maxY) {
                        canvas.drawText("Trang " + (soTrang - 1) + " | Smart Document Scanner",
                                MARGIN_LEFT, PAGE_HEIGHT - 20, paintFooter);
                        pdfDoc.finishPage(page);

                        pageInfo = new PdfDocument.PageInfo.Builder(
                                PAGE_WIDTH, PAGE_HEIGHT, soTrang++).create();
                        page = pdfDoc.startPage(pageInfo);
                        canvas = page.getCanvas();
                        canvas.drawRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT, paintBg);
                        y = MARGIN_TOP;
                    }

                    canvas.drawText(line, MARGIN_LEFT, y, p);
                    y += lineHeight;
                }

                if (block.isHeading) {
                    y += 4f;
                }
            } else if (block.type == ExportHelper.DocBlock.TYPE_TABLE) {
                int maxCols = 0;
                for (List<String> row : block.tableRows) {
                    if (row.size() > maxCols) maxCols = row.size();
                }
                if (maxCols == 0) continue;

                float colWidth = (float) CONTENT_WIDTH / maxCols;
                float rowHeight = 22f;
                float fontSize = (maxCols > 8) ? 7.5f : (maxCols > 5 ? 8.5f : 9.5f);
                cellTextPaint.setTextSize(fontSize);
                cellHeaderPaint.setTextSize(fontSize);

                for (int r = 0; r < block.tableRows.size(); r++) {
                    List<String> row = block.tableRows.get(r);

                    if (y + rowHeight > maxY) {
                        canvas.drawText("Trang " + (soTrang - 1) + " | Smart Document Scanner",
                                MARGIN_LEFT, PAGE_HEIGHT - 20, paintFooter);
                        pdfDoc.finishPage(page);

                        pageInfo = new PdfDocument.PageInfo.Builder(
                                PAGE_WIDTH, PAGE_HEIGHT, soTrang++).create();
                        page = pdfDoc.startPage(pageInfo);
                        canvas = page.getCanvas();
                        canvas.drawRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT, paintBg);
                        y = MARGIN_TOP;
                    }

                    for (int c = 0; c < maxCols; c++) {
                        float cellLeft = MARGIN_LEFT + c * colWidth;
                        float cellRight = (c == maxCols - 1) ? (PAGE_WIDTH - MARGIN_RIGHT) : (cellLeft + colWidth);
                        float cellTop = y;
                        float cellBottom = y + rowHeight;

                        if (r == 0) {
                            canvas.drawRect(cellLeft, cellTop, cellRight, cellBottom, headerBgPaint);
                        }
                        canvas.drawRect(cellLeft, cellTop, cellRight, cellBottom, borderPaint);

                        String val = c < row.size() ? row.get(c) : "";
                        if (!val.isEmpty()) {
                            Paint p = (r == 0) ? cellHeaderPaint : cellTextPaint;
                            float tw = p.measureText(val);
                            float tx;
                            if (tw < colWidth - 4) {
                                tx = cellLeft + (colWidth - tw) / 2f;
                            } else {
                                tx = cellLeft + 2f;
                                while (val.length() > 1 && p.measureText(val + "..") > colWidth - 3) {
                                    val = val.substring(0, val.length() - 1);
                                }
                                val = val + "..";
                            }
                            float ty = cellTop + rowHeight / 2f + p.getTextSize() / 3f;
                            canvas.drawText(val, tx, ty, p);
                        }
                    }

                    y += rowHeight;
                }

                y += 10f;
            }
        }

        // Footer trang cuoi cung cua section
        canvas.drawText("Trang " + (soTrang - 1) + " | Smart Document Scanner",
                MARGIN_LEFT, PAGE_HEIGHT - 20, paintFooter);
        pdfDoc.finishPage(page);

        return soTrang;
    }

    // ─────────────────────────────────────────
    // Word-wrap helper
    // ─────────────────────────────────────────

    /*
      Chia một dòng text dài thành nhiều dòng con vừa với maxWidth.
      Cắt theo từ (word boundary) thay vì cắt ký tự để tránh vỡ từ.
     */
    static java.util.List<String> wrapText(String text, Paint paint, int maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }

        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            float width = paint.measureText(testLine);
            if (width > maxWidth && currentLine.length() > 0) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            } else {
                currentLine = new StringBuilder(testLine);
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines;
    }

    // ─────────────────────────────────────────
    // Paint factories
    // ─────────────────────────────────────────

    static Paint taoTieuDePaint() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.rgb(25, 118, 210)); // Material Blue
        p.setTextSize(16f);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        return p;
    }

    static Paint taoSubTitlePaint() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.DKGRAY);
        p.setTextSize(12f);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
        return p;
    }

    static Paint taoHeadingPaint() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.rgb(33, 33, 33));
        p.setTextSize(13f);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        return p;
    }

    static Paint taoBodyPaint() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.BLACK);
        p.setTextSize(11f);
        return p;
    }

    static Paint taoFooterPaint() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.GRAY);
        p.setTextSize(8f);
        return p;
    }

    // ─────────────────────────────────────────
    // File & Bitmap utilities
    // ─────────────────────────────────────────

    /**
     * Tạo file PDF trong thư mục Downloads/SmartDocs/ của app.
     * Tên file: SmartDocs_[title]_[timestamp].pdf
     */
    static File taoFilePdf(Context context, String title) throws IOException {
        // Thư mục lưu PDF: Downloads/SmartDocs/
        File thuMuc = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "SmartDocs");
        if (!thuMuc.exists()) {
            thuMuc.mkdirs();
        }

        // Tên file an toàn (loại bỏ ký tự đặc biệt)
        String cleanTitle = DocumentAdapter.chuanHoaTieuDe(title);
        String tenSach = (cleanTitle != null ? cleanTitle : "TaiLieu")
                .replaceAll("[^a-zA-Z0-9À-ỹ\\s]", "")
                .trim()
                .replaceAll("\\s+", "_");
        if (tenSach.isEmpty()) tenSach = "TaiLieu";
        if (tenSach.length() > 30) tenSach = tenSach.substring(0, 30);

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
        String tenFile = "SmartDocs_" + tenSach + "_" + timestamp + ".pdf";

        return new File(thuMuc, tenFile);
    }

    /**
     * Đọc Bitmap từ URI (Camera hoặc Gallery) với scale để tránh OOM.
     * Scale về tối đa 800px cạnh dài (đủ cho trang A4 PDF 72dpi).
     *
     * @return Bitmap đã scale, hoặc null nếu không đọc được
     */
    static Bitmap docBitmap(Context context, String uriString) {
        try {
            Uri uri = Uri.parse(uriString);
            InputStream is = context.getContentResolver().openInputStream(uri);
            if (is == null) return null;

            // Đọc kích thước trước, không đọc pixel
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, opts);
            is.close();

            // Tính inSampleSize để scale về ~800px
            int targetMaxDim = 800;
            int maxDim = Math.max(opts.outWidth, opts.outHeight);
            opts.inSampleSize = Math.max(1, (int) Math.ceil((double) maxDim / targetMaxDim));
            opts.inJustDecodeBounds = false;

            InputStream is2 = context.getContentResolver().openInputStream(uri);
            Bitmap bm = BitmapFactory.decodeStream(is2, null, opts);
            if (is2 != null) is2.close();
            return bm;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Tạo Intent để share file PDF qua các app khác (Gmail, Drive, WhatsApp...).
     *
     * @param pdfUri Uri của file PDF (từ FileProvider)
     * @return Intent ACTION_SEND sẵn sàng để startActivity()
     */
    public static Intent taoIntentShare(Uri pdfUri) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, pdfUri);
        intent.putExtra(Intent.EXTRA_SUBJECT, "Tài liệu từ Smart Document Scanner");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return Intent.createChooser(intent, "Chia sẻ PDF qua...");
    }
}
