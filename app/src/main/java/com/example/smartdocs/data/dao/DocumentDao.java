package com.example.smartdocs.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.smartdocs.data.model.ScanDocument;

import java.util.List;
@Dao
public interface DocumentDao {

    // Chèn một tài liệu mới, nếu trùng id thì thay thế
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(ScanDocument document);

    // Cập nhật tài liệu đã có (dùng id làm khóa)
    @Update
    void update(ScanDocument document);

    // Xóa một tài liệu
    @Delete
    void delete(ScanDocument document);

    // Lấy toàn bộ danh sách, sắp xếp mới nhất trước
    // LiveData giúp UI tự động cập nhật khi dữ liệu thay đổi
    @Query("SELECT * FROM scan_documents ORDER BY created_at DESC")
    LiveData<List<ScanDocument>> getAllDocuments();


    // Xóa tất cả tài liệu
    @Query("DELETE FROM scan_documents")
    void deleteAll();

    // Tìm kiếm theo nội dung OCR hoặc tiêu đề
    @Query("SELECT * FROM scan_documents WHERE ocr_text LIKE '%' || :keyword || '%' " +
           "OR title LIKE '%' || :keyword || '%' ORDER BY created_at DESC")
    LiveData<List<ScanDocument>> search(String keyword);

    // Lấy danh sách tài liệu yêu thích
    @Query("SELECT * FROM scan_documents WHERE is_favorite = 1 ORDER BY created_at DESC")
    LiveData<List<ScanDocument>> getFavorites();

    // Toggle yêu thích cho một tài liệu
    @Query("UPDATE scan_documents SET is_favorite = :isFavorite WHERE id = :id")
    void updateFavorite(int id, int isFavorite);

    // Cập nhật nội dung OCR theo ID, đồng thời ghi nhận thời gian chỉnh sửa
    @Query("UPDATE scan_documents SET ocr_text = :ocrText, updated_at = :updatedAt WHERE id = :id")
    void updateOcrText(int id, String ocrText, long updatedAt);

    // Cập nhật kết quả AI theo ID, đồng thời ghi nhận thời gian chỉnh sửa
    @Query("UPDATE scan_documents SET ai_summary = :aiSummary, updated_at = :updatedAt WHERE id = :id")
    void updateAiSummary(int id, String aiSummary, long updatedAt);
}
