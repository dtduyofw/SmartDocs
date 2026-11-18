package com.example.smartdocs.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;

import androidx.core.content.FileProvider;

import com.example.smartdocs.data.model.ScanDocument;
import com.example.smartdocs.ui.adapter.DocumentAdapter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // Mo hinh khoi noi dung phan tich tu OCR (text thuan hoac bang bieu)
    public static class DocBlock {
        public static final int TYPE_TEXT = 1;
        public static final int TYPE_TABLE = 2;

        public int type;
        public String text;
        public boolean isHeading;
        public List<List<String>> tableRows = new ArrayList<>();

        public DocBlock(int type) {
            this.type = type;
        }
    }

    // Phan tich van ban OCR thanh cac khoi noi dung (doan van, tieu de, bang bieu)
    public static List<DocBlock> phanTichNoiDung(String input) {
        List<DocBlock> blocks = new ArrayList<>();
        if (input == null || input.trim().isEmpty()) {
            return blocks;
        }

        String text = input.trim();
        if (text.startsWith("```html")) {
            text = text.substring(7);
        } else if (text.startsWith("```")) {
            text = text.substring(3);
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }
        text = text.trim();

        String lower = text.toLowerCase();
        int tableStart = lower.indexOf("<table");

        if (tableStart < 0) {
            if (text.contains("|") && text.contains("---")) {
                phanTichBangMarkdown(text, blocks);
                return blocks;
            }
            phanTichTextThuan(text, blocks);
            return blocks;
        }

        int currentPos = 0;
        while (currentPos < text.length()) {
            int startTbl = lower.indexOf("<table", currentPos);
            if (startTbl < 0) {
                String rest = text.substring(currentPos);
                phanTichTextThuan(rest, blocks);
                break;
            }

            if (startTbl > currentPos) {
                String beforeTbl = text.substring(currentPos, startTbl);
                phanTichTextThuan(beforeTbl, blocks);
            }

            int endTbl = lower.indexOf("</table>", startTbl);
            String tableHtml;
            if (endTbl >= 0) {
                tableHtml = text.substring(startTbl, endTbl + 8);
                currentPos = endTbl + 8;
            } else {
                tableHtml = text.substring(startTbl);
                currentPos = text.length();
            }

            DocBlock tableBlock = new DocBlock(DocBlock.TYPE_TABLE);
            tableBlock.tableRows = parseHtmlTable(tableHtml);
            if (!tableBlock.tableRows.isEmpty()) {
                blocks.add(tableBlock);
            }
        }

        return blocks;
    }

    static List<List<String>> parseHtmlTable(String tableHtml) {
        List<List<String>> rows = new ArrayList<>();
        if (tableHtml == null || tableHtml.isEmpty()) return rows;

        Pattern rowPattern = Pattern.compile("<tr[^>]*>(.*?)(?:</tr>|(?=<tr)|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher rowMatcher = rowPattern.matcher(tableHtml);

        Pattern cellPattern = Pattern.compile("<(td|th)[^>]*>(.*?)(?:</(?:td|th)>|(?=<(?:td|th))|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        while (rowMatcher.find()) {
            String rowContent = rowMatcher.group(1);
            if (rowContent == null || rowContent.trim().isEmpty()) continue;

            Matcher cellMatcher = cellPattern.matcher(rowContent);
            List<String> cells = new ArrayList<>();
            while (cellMatcher.find()) {
                String rawCell = cellMatcher.group(2);
                String cellText = DocumentAdapter.loaiBoHtml(rawCell);
                cells.add(cellText);
            }

            if (!cells.isEmpty()) {
                rows.add(cells);
            }
        }
        return rows;
    }

    static void phanTichTextThuan(String text, List<DocBlock> blocks) {
        if (text == null || text.trim().isEmpty()) return;
        String[] lines = text.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            String lower = trimmed.toLowerCase();
            boolean isHeading = (lower.startsWith("<h1") || lower.startsWith("<h2")
                    || lower.startsWith("<h3") || lower.startsWith("<h4")
                    || (lower.startsWith("<b>") && lower.endsWith("</b>")));

            String cleanText = DocumentAdapter.loaiBoHtml(trimmed);
            if (!cleanText.isEmpty()) {
                DocBlock block = new DocBlock(DocBlock.TYPE_TEXT);
                block.text = cleanText;
                block.isHeading = isHeading;
                blocks.add(block);
            }
        }
    }

    static void phanTichBangMarkdown(String text, List<DocBlock> blocks) {
        String[] lines = text.split("\n");
        DocBlock currentTable = null;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                if (trimmed.replaceAll("[\\s|:-]", "").isEmpty()) {
                    continue;
                }
                if (currentTable == null) {
                    currentTable = new DocBlock(DocBlock.TYPE_TABLE);
                    blocks.add(currentTable);
                }
                String[] cols = trimmed.substring(1, trimmed.length() - 1).split("\\|");
                List<String> cells = new ArrayList<>();
                for (String c : cols) {
                    cells.add(DocumentAdapter.loaiBoHtml(c.trim()));
                }
                if (!cells.isEmpty()) {
                    currentTable.tableRows.add(cells);
                }
            } else {
                currentTable = null;
                String clean = DocumentAdapter.loaiBoHtml(trimmed);
                if (!clean.isEmpty()) {
                    DocBlock b = new DocBlock(DocBlock.TYPE_TEXT);
                    b.text = clean;
                    blocks.add(b);
                }
            }
        }
    }

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
        String cleanTitle = DocumentAdapter.chuanHoaTieuDe(document.title);
        File file = taoFile(context, cleanTitle, ".docx");

        // Nội dung XML WordprocessingML đơn giản
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        xml.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">\n");
        xml.append("<w:body>\n");

        // Tiêu đề tài liệu
        xml.append(taoWParagraph(cleanTitle, true, "28", "2B579A"));

        // Ngày tạo
        String ngay = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(new Date(document.createdAt));
        xml.append(taoWParagraph("Ngày tạo: " + ngay, false, "16", "666666"));

        // Dòng kẻ phân cách
        xml.append(taoWParagraph("─────────────────────────────────────────", false, "10", "AAAAAA"));

        // Văn bản OCR
        if (document.ocrText != null && !document.ocrText.isEmpty()) {
            xml.append(taoWParagraph("VĂN BẢN NHẬN DIỆN (OCR)", true, "18", "1976D2"));
            List<DocBlock> blocks = phanTichNoiDung(document.ocrText);
            for (DocBlock block : blocks) {
                if (block.type == DocBlock.TYPE_TEXT) {
                    xml.append(taoWParagraph(block.text, block.isHeading, block.isHeading ? "24" : "22", block.isHeading ? "1976D2" : "212121"));
                } else if (block.type == DocBlock.TYPE_TABLE) {
                    xml.append(taoWTable(block.tableRows));
                }
            }
        }

        // Tóm tắt AI
        if (document.aiSummary != null && !document.aiSummary.trim().isEmpty()) {
            xml.append(taoWParagraph(" ", false, "14", "000000"));
            xml.append(taoWParagraph("KẾT QUẢ PHÂN TÍCH AI", true, "18", "388E3C"));
            List<DocBlock> blocksAi = phanTichNoiDung(document.aiSummary);
            for (DocBlock block : blocksAi) {
                if (block.type == DocBlock.TYPE_TEXT) {
                    xml.append(taoWParagraph(block.text, block.isHeading, "22", "212121"));
                } else if (block.type == DocBlock.TYPE_TABLE) {
                    xml.append(taoWTable(block.tableRows));
                }
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

    // Tao bang WordprocessingML XML tu danh sach hang cot
    static String taoWTable(List<List<String>> rows) {
        if (rows == null || rows.isEmpty()) return "";
        StringBuilder tbl = new StringBuilder();
        tbl.append("<w:tbl>\n");
        tbl.append("<w:tblPr>\n");
        tbl.append("<w:tblBorders>\n");
        tbl.append("<w:top w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"CCCCCC\"/>\n");
        tbl.append("<w:left w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"CCCCCC\"/>\n");
        tbl.append("<w:bottom w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"CCCCCC\"/>\n");
        tbl.append("<w:right w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"CCCCCC\"/>\n");
        tbl.append("<w:insideH w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"E0E0E0\"/>\n");
        tbl.append("<w:insideV w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"E0E0E0\"/>\n");
        tbl.append("</w:tblBorders>\n");
        tbl.append("</w:tblPr>\n");

        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            tbl.append("<w:tr>\n");
            for (String cell : row) {
                tbl.append("<w:tc>\n");
                tbl.append("<w:p>\n");
                tbl.append("<w:r>\n");
                if (r == 0) {
                    tbl.append("<w:rPr><w:b/></w:rPr>\n");
                }
                String escaped = (cell != null ? cell : "")
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;");
                tbl.append("<w:t xml:space=\"preserve\">").append(escaped).append("</w:t>\n");
                tbl.append("</w:r>\n");
                tbl.append("</w:p>\n");
                tbl.append("</w:tc>\n");
            }
            tbl.append("</w:tr>\n");
        }
        tbl.append("</w:tbl>\n");
        return tbl.toString();
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
        String cleanTitle = DocumentAdapter.chuanHoaTieuDe(document.title);
        File file = taoFile(context, cleanTitle, ".xlsx");

        StringBuilder csv = new StringBuilder();

        // Header row
        csv.append("Trường,Nội dung\n");

        // Tiêu đề
        csv.append("Tiêu đề,").append(escapeCsv(cleanTitle)).append("\n");

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

        // Văn bản OCR
        if (document.ocrText != null && !document.ocrText.isEmpty()) {
            csv.append("VĂN BẢN OCR,\n");
            List<DocBlock> blocks = phanTichNoiDung(document.ocrText);
            for (DocBlock block : blocks) {
                if (block.type == DocBlock.TYPE_TABLE) {
                    for (List<String> row : block.tableRows) {
                        StringBuilder rowSb = new StringBuilder();
                        for (int i = 0; i < row.size(); i++) {
                            if (i > 0) rowSb.append(",");
                            rowSb.append(escapeCsv(row.get(i)));
                        }
                        csv.append(rowSb.toString()).append("\n");
                    }
                } else {
                    csv.append(",").append(escapeCsv(block.text)).append("\n");
                }
            }
        }

        // Dòng trống
        csv.append(",\n");

        // Tóm tắt AI
        if (document.aiSummary != null && !document.aiSummary.trim().isEmpty()) {
            csv.append("KẾT QUẢ AI,\n");
            List<DocBlock> blocksAi = phanTichNoiDung(document.aiSummary);
            for (DocBlock block : blocksAi) {
                if (block.type == DocBlock.TYPE_TABLE) {
                    for (List<String> row : block.tableRows) {
                        StringBuilder rowSb = new StringBuilder();
                        for (int i = 0; i < row.size(); i++) {
                            if (i > 0) rowSb.append(",");
                            rowSb.append(escapeCsv(row.get(i)));
                        }
                        csv.append(rowSb.toString()).append("\n");
                    }
                } else {
                    csv.append(",").append(escapeCsv(block.text)).append("\n");
                }
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
        String cleanTitle = DocumentAdapter.chuanHoaTieuDe(document.title);
        File file = taoFile(context, cleanTitle, ".txt");

        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("===========================================\n");
        sb.append("SMART DOCUMENT SCANNER\n");
        sb.append("===========================================\n\n");

        // Tiêu đề
        sb.append("Tiêu đề : ").append(cleanTitle).append("\n");

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
            List<DocBlock> blocks = phanTichNoiDung(document.ocrText);
            for (DocBlock block : blocks) {
                if (block.type == DocBlock.TYPE_TABLE) {
                    for (List<String> row : block.tableRows) {
                        StringBuilder rowSb = new StringBuilder();
                        for (int i = 0; i < row.size(); i++) {
                            if (i > 0) rowSb.append("\t");
                            rowSb.append(row.get(i));
                        }
                        sb.append(rowSb.toString()).append("\n");
                    }
                    sb.append("\n");
                } else {
                    sb.append(block.text).append("\n");
                }
            }
            sb.append("\n");
        }

        // AI
        if (document.aiSummary != null && !document.aiSummary.trim().isEmpty()) {
            sb.append("-------------------------------------------\n");
            sb.append("KẾT QUẢ PHÂN TÍCH AI\n");
            sb.append("-------------------------------------------\n");
            List<DocBlock> blocksAi = phanTichNoiDung(document.aiSummary);
            for (DocBlock block : blocksAi) {
                if (block.type == DocBlock.TYPE_TABLE) {
                    for (List<String> row : block.tableRows) {
                        StringBuilder rowSb = new StringBuilder();
                        for (int i = 0; i < row.size(); i++) {
                            if (i > 0) rowSb.append("\t");
                            rowSb.append(row.get(i));
                        }
                        sb.append(rowSb.toString()).append("\n");
                    }
                    sb.append("\n");
                } else {
                    sb.append(block.text).append("\n");
                }
            }
            sb.append("\n");
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
        String cleanTitle = DocumentAdapter.chuanHoaTieuDe(title);
        String tenSach = (cleanTitle != null ? cleanTitle : "TaiLieu")
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
