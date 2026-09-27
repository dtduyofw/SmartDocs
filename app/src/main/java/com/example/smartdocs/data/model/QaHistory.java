package com.example.smartdocs.data.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/*
  Entity lưu lịch sử Q&A của từng tài liệu.
  Liên kết với scan_documents qua document_id.
  Khi tài liệu bị xóa → toàn bộ Q&A của nó cũng bị xóa.
 */
@Entity(tableName = "qa_history",
        foreignKeys = @ForeignKey(
                entity = ScanDocument.class,
                parentColumns = "id",
                childColumns = "document_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("document_id")})
public class QaHistory {

    @PrimaryKey(autoGenerate = true)
    public int id;

    // ID tài liệu liên kết (khóa ngoại → scan_documents.id)
    @ColumnInfo(name = "document_id")
    public int documentId;

    // Câu hỏi của người dùng
    @ColumnInfo(name = "question")
    public String question;

    // Câu trả lời từ AI
    @ColumnInfo(name = "answer")
    public String answer;

    // Thời điểm hỏi
    @ColumnInfo(name = "created_at")
    public long createdAt;

    public QaHistory(int documentId, String question, String answer, long createdAt) {
        this.documentId = documentId;
        this.question = question;
        this.answer = answer;
        this.createdAt = createdAt;
    }
}
