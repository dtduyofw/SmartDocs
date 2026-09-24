package com.example.smartdocs.data.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/*
  Entity đại diện cho một tài liệu đã quét.
  Mỗi bản ghi lưu: ảnh gốc, văn bản OCR, tóm tắt AI, và thời điểm tạo.
  Index trên created_at giúp query ORDER BY nhanh hơn khi dữ liệu lớn.
 */
@Entity(tableName = "scan_documents",
        indices = {@Index(value = "created_at")})
public class ScanDocument {

    @PrimaryKey(autoGenerate = true)
    public int id;

    // Đường dẫn ảnh gốc trên thiết bị (URI dạng String)
    @ColumnInfo(name = "image_uri")
    public String imageUri;

    // Văn bản nhận diện được từ ML Kit OCR
    @ColumnInfo(name = "ocr_text")
    public String ocrText;

    // Văn bản tóm tắt / xử lý từ API
    @ColumnInfo(name = "ai_summary")
    public String aiSummary;

    // Timestamp lưu tài liệu
    @ColumnInfo(name = "created_at")
    public long createdAt;

    // Tiêu đề ngắn của tài liệu
    @ColumnInfo(name = "title")
    public String title;

    // Tài liệu có được đánh dấu yêu thích không
    @ColumnInfo(name = "is_favorite")
    public int isFavorite; // 0 = false, 1 = true

    // Số trang của tài liệu (mặc định 1)
    @ColumnInfo(name = "page_count")
    public int pageCount;

    // Nhãn phân loại tài liệu (hóa đơn, hợp đồng, ghi chú... — nhiều tag cách nhau bằng dấu phẩy)
    @ColumnInfo(name = "tags")
    public String tags;

    // Ngôn ngữ gốc được nhận diện từ OCR
    @ColumnInfo(name = "language")
    public String language;

    // Timestamp lần cuối chỉnh sửa OCR hoặc AI , 0 = chưa chỉnh sửa
    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    // Constructor đầy đủ
    public ScanDocument(String imageUri, String ocrText, String aiSummary,
                        long createdAt, String title) {
        this.imageUri = imageUri;
        this.ocrText = ocrText;
        this.aiSummary = aiSummary;
        this.createdAt = createdAt;
        this.title = title;
        this.isFavorite = 0; // mặc định không yêu thích
        this.pageCount = 1;  // mặc định 1 trang
        this.tags = "";      // mặc định không có nhãn
        this.language = "auto"; // mặc định tự động nhận diện ngôn ngữ
        this.updatedAt = 0;  // 0 = chưa chỉnh sửa lần nào
    }
}
