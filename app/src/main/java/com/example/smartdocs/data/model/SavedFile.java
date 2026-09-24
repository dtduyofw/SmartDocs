package com.example.smartdocs.data.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/*
  Enity lưu file yêu thích trong FilesFragment.
  Thay thế SharedPreferences → tránh "stale path" khi file đổi tên / di chuyển.
  Unique index trên file_path đảm bảo mỗi file chỉ xuất hiện một lần.
 */
@Entity(tableName = "saved_files",
        indices = {@Index(value = "file_path", unique = true)})
public class SavedFile {

    @PrimaryKey(autoGenerate = true)
    public int id;

    // Đường dẫn tuyệt đối của file trên thiết bị
    @ColumnInfo(name = "file_path")
    public String filePath;

    // Tên hiển thị của file (cập nhật khi đổi tên)
    @ColumnInfo(name = "file_name")
    public String fileName;

    // Loại file: pdf, docx, xlsx, txt...
    @ColumnInfo(name = "file_type")
    public String fileType;

    // Timestamp thêm vào yêu thích
    @ColumnInfo(name = "added_at")
    public long addedAt;

    public SavedFile(String filePath, String fileName, String fileType, long addedAt) {
        this.filePath = filePath;
        this.fileName = fileName;
        this.fileType = fileType;
        this.addedAt = addedAt;
    }
}
