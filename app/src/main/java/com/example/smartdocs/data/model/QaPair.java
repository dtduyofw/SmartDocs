package com.example.smartdocs.data.model;

/*
  Model đại diện cho một cặp câu hỏi - câu trả lời trong phiên Q&A.
  Lưu trong ArrayList trong ViewModel.
  Adapter dùng field isUser để phân biệt bubble bên trái / phải.
 */
public class QaPair {

    // Nội dung tin nhắn
    public String noiDung;

    // true = tin nhắn của người dùng
    // false = tin nhắn của AI
    public boolean isUser;

    public QaPair(String noiDung, boolean isUser) {
        this.noiDung = noiDung;
        this.isUser = isUser;
    }
}
