package com.example.smartdocs.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;

import androidx.core.content.FileProvider;

import com.example.smartdocs.data.model.ScanDocument;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/*
  ExportHelper — xuất ScanDocument ra nhiều định dạng file:
    - Word (.docx): dùng kỹ thuật WordprocessingML XML, không cần thư viện ngoài
    - Excel (.xlsx): xuất dạng CSV đổi đuôi .xlsx (mở được trong Excel/Sheets)
    - Text (.txt): plain text đơn giản
  Tất cả file được lưu vào thư mục riêng của app (không cần quyền WRITE_EXTERNAL_STORAGE).
  Dùng FileProvider để share an toàn với các app khác.
 */
public class ExportHelper {

    // Thư mục con lưu file xuất
    static final String FOLDER_NAME = "SmartDocs";

    // ─────────────────────────────────────────
    // Xuất Word (.docx)
    // ─────────────────────────────────────────

    /*
      Xuất tài liệu ra file .docx (WordprocessingML XML format).
      Kỹ thuật: tạo XML theo chuẩn Open XML của Microsoft Word,
      không cần Apache POI hay thư viện nào.
      @return Uri của file .docx vừa tạo
     */
    public static Uri exportToDocx(Context context, ScanDocument document) throws IOException {
        File file = taoFile(context, document.title, ".docx");

        // Nội dung XML WordprocessingML đơn giản
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        xml.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">\n");
        xml.append("<w:body>\n");

        // Tiêu đề tài liệu
        String title = document.title != null ? document.title : "Tài liệu";
        xml.append(taoWParagraph(title, true, "28", "2B579A"));

        // Ngày tạo
        String ngay = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(new Date(document.createdAt));
        xml.append(taoWParagraph("Ngày tạo: " + ngay, false, "16", "666666"));

        // Dòng kẻ phân cách
        xml.append(taoWParagraph("─────────────────────────────────────────", false, "10", "AAAAAA"));

        // Văn bản OCR
        if (document.ocrText != null && !document.ocrText.isEmpty()) {
            xml.append(taoWParagraph("VĂN BẢN NHẬN DIỆN (OCR)", true, "18", "1976D2"));
            String[] dongOcr = document.ocrText.split("\n");
            for (String dong : dongOcr) {
                xml.append(taoWParagraph(dong.isEmpty() ? " " : dong, false, "22", "212121"));
            }
        }

        // Tóm tắt AI
        if (document.aiSummary != null && !document.aiSummary.trim().isEmpty()) {
            xml.append(taoWParagraph(" ", false, "14", "000000"));
            xml.append(taoWParagraph("KẾT QUẢ PHÂN TÍCH AI", true, "18", "388E3C"));
            String[] dongAi = document.aiSummary.split("\n");
            for (String dong : dongAi) {
                xml.append(taoWParagraph(dong.isEmpty() ? " " : dong, false, "22", "212121"));
            }
        }

        // Footer
        xml.append(taoWParagraph(" ", false, "14", "000000"));
        xml.append(taoWParagraph("Smart Document Scanner", false, "16", "9E9E9E"));

        xml.append("</w:body>\n");
        xml.append("</w:document>");

        // Ghi file
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), "UTF-8")) {
            writer.write(xml.toString());
        }

        return layUri(context, file);
    }

    /*
      Tạo một paragraph WordprocessingML XML.
     */
    static String taoWParagraph(String text, boolean bold, String fontSize, String color) {
        // Escape ký tự đặc biệt XML
        String escaped = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");

        StringBuilder p = new StringBuilder();
        p.append("<w:p>");
        p.append("<w:r>");
        p.append("<w:rPr>");
        if (bold) {
            p.append("<w:b/>");
        }
        p.append("<w:sz w:val=\"").append(fontSize).append("\"/>");
        p.append("<w:color w:val=\"").append(color).append("\"/>");
        p.append("</w:rPr>");
        p.append("<w:t xml:space=\"preserve\">").append(escaped).append("</w:t>");
        p.append("</w:r>");
        p.append("</w:p>\n");
        return p.toString();
    }

    // ─────────────────────────────────────────
    // Xuất Excel (.xlsx dạng CSV)
    // ─────────────────────────────────────────

    /*
      Xuất tài liệu ra file .xlsx.
      Kỹ thuật: tạo CSV chuẩn rồi lưu với đuôi .xlsx.
      Excel và Google Sheets đều mở được file này.
      @return Uri của file .xlsx vừa tạo
     */
    public static Uri exportToXlsx(Context context, ScanDocument document) throws IOException {
        File file = taoFile(context, document.title, ".xlsx");

        StringBuilder csv = new StringBuilder();

        // Header row
        csv.append("Trường,Nội dung\n");

        // Tiêu đề
        csv.append("Tiêu đề,").append(escapeCsv(document.title)).append("\n");

        // Ngày tạo
        String ngay = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(new Date(document.createdAt));
        csv.append("Ngày tạo,").append(escapeCsv(ngay)).append("\n");

        // Số trang
        csv.append("Số trang,").append(document.pageCount).append("\n");

        // Loại (AI/Offline)
        String loai = (document.aiSummary != null && !document.aiSummary.trim().isEmpty())
                ? "AI" : "OFFLINE";
        csv.append("Phương thức,").append(loai).append("\n");

        // Dòng trống
        csv.append(",\n");

        // Văn bản OCR — mỗi dòng là 1 row
        if (document.ocrText != null && !document.ocrText.isEmpty()) {
            csv.append("VĂN BẢN OCR,\n");
            String[] dongOcr = document.ocrText.split("\n");
            for (String dong : dongOcr) {
                csv.append(",").append(escapeCsv(dong)).append("\n");
            }
        }

        // Dòng trống
        csv.append(",\n");

        // Tóm tắt AI
        if (document.aiSummary != null && !document.aiSummary.trim().isEmpty()) {
            csv.append("KẾT QUẢ AI,\n");
            String[] dongAi = document.aiSummary.split("\n");
            for (String dong : dongAi) {
                csv.append(",").append(escapeCsv(dong)).append("\n");
            }
        }

        // Ghi file UTF-8 với BOM (để Excel nhận đúng tiếng Việt)
        try (FileOutputStream fos = new FileOutputStream(file)) {
            // BOM UTF-8: giúp Excel nhận đúng encoding
            fos.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            fos.write(csv.toString().getBytes("UTF-8"));
        }

        return layUri(context, file);
    }

    /*
      Escape nội dung CSV: bọc trong dấu nháy kép nếu có dấu phẩy, xuống dòng hoặc nháy.
     */
    static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // ─────────────────────────────────────────
    // Xuất Text (.txt)
    // ─────────────────────────────────────────

    /*
      Xuất tài liệu ra file plain text .txt.
      @return Uri của file .txt vừa tạo
     */
    public static Uri exportToTxt(Context context, ScanDocument document) throws IOException {
        File file = taoFile(context, document.title, ".txt");

        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("===========================================\n");
        sb.append("SMART DOCUMENT SCANNER\n");
        sb.append("===========================================\n\n");

        // Tiêu đề
        sb.append("Tiêu đề : ").append(document.title != null ? document.title : "Không có").append("\n");

        // Ngày
        String ngay = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                .format(new Date(document.createdAt));
        sb.append("Ngày tạo: ").append(ngay).append("\n");
        sb.append("Số trang : ").append(document.pageCount).append(" trang\n\n");

        // OCR
        if (document.ocrText != null && !document.ocrText.isEmpty()) {
            sb.append("-------------------------------------------\n");
            sb.append("VĂN BẢN NHẬN DIỆN (OCR)\n");
            sb.append("-------------------------------------------\n");
            sb.append(document.ocrText).append("\n\n");
        }

        // AI
        if (document.aiSummary != null && !document.aiSummary.trim().isEmpty()) {
            sb.append("-------------------------------------------\n");
            sb.append("KẾT QUẢ PHÂN TÍCH AI\n");
            sb.append("-------------------------------------------\n");
            sb.append(document.aiSummary).append("\n\n");
        }

        sb.append("===========================================\n");
        sb.append("Xuất bởi Smart Document Scanner\n");
        sb.append("===========================================\n");

        // Ghi file
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), "UTF-8")) {
            writer.write(sb.toString());
        }

        return layUri(context, file);
    }

    // ─────────────────────────────────────────
    // Intent Share
    // ─────────────────────────────────────────

    /*
      Tạo Intent share file với mime type tương ứng theo đuôi file.
     */
    public static Intent taoIntentShare(Uri uri, String tenFile) {
        String mimeType;
        String ten = tenFile.toLowerCase();
        if (ten.endsWith(".pdf")) {
            mimeType = "application/pdf";
        } else if (ten.endsWith(".docx")) {
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (ten.endsWith(".xlsx")) {
            mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else {
            mimeType = "text/plain";
        }

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_SUBJECT, "Tài liệu từ Smart Document Scanner");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return Intent.createChooser(intent, "Chia sẻ qua...");
    }

    // ─────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────

    /*
      Tạo file trong thư mục SmartDocs của app.
      Tên file: SmartDocs_[title]_[timestamp].[ext]
     */
    static File taoFile(Context context, String title, String duoiFile) throws IOException {
        File thuMuc = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), FOLDER_NAME);
        if (!thuMuc.exists()) {
            thuMuc.mkdirs();
        }

        // Tên file an toàn
        String tenSach = (title != null ? title : "TaiLieu")
                .replaceAll("[^a-zA-Z0-9À-ỹ\\s]", "")
                .trim()
                .replaceAll("\\s+", "_");
        if (tenSach.isEmpty()) tenSach = "TaiLieu";
        if (tenSach.length() > 30) tenSach = tenSach.substring(0, 30);

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
        String tenFile = "SmartDocs_" + tenSach + "_" + timestamp + duoiFile;

        return new File(thuMuc, tenFile);
    }

    //Chuyển File thành URI an toàn qua FileProvider.
    static Uri layUri(Context context, File file) {
        return FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                file
        );
    }
}
