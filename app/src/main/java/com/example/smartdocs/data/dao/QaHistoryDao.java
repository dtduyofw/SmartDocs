package com.example.smartdocs.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.smartdocs.data.model.QaHistory;

import java.util.List;
@Dao
public interface QaHistoryDao {

    // Chèn một cặp Q&A mới
    @Insert
    void insert(QaHistory qaHistory);

    // Lấy toàn bộ Q&A của một tài liệu, sắp xếp cũ nhất trước
    @Query("SELECT * FROM qa_history WHERE document_id = :documentId ORDER BY created_at ASC")
    List<QaHistory> getByDocumentId(int documentId);

    // Xóa toàn bộ Q&A của một tài liệu
    @Query("DELETE FROM qa_history WHERE document_id = :documentId")
    void deleteByDocumentId(int documentId);
}
