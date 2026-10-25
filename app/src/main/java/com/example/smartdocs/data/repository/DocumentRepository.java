package com.example.smartdocs.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.example.smartdocs.data.dao.AppDatabase;
import com.example.smartdocs.data.dao.DocumentDao;
import com.example.smartdocs.data.dao.DocumentPageDao;
import com.example.smartdocs.data.model.DocumentPage;
import com.example.smartdocs.data.model.ScanDocument;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/*
  Repository — nguồn dữ liệu duy nhất
  ViewModel chỉ tương tác qua Repository, không biết nguồn gốc dữ liệu
  (Room, Network, Cache...). Giúp dễ mở rộng và kiểm thử.
 */
public class DocumentRepository {

    DocumentDao documentDao;
    DocumentPageDao documentPageDao;

    // ExecutorService chạy các tác vụ DB trên background thread
    // Room cấm chạy write operations trên main thread
    ExecutorService executor;

    public DocumentRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        documentDao = db.documentDao();
        documentPageDao = db.documentPageDao();
        // Single thread để tránh race condition khi ghi DB
        executor = Executors.newSingleThreadExecutor();
    }
     // Trả về toàn bộ danh sách tài liệu, tự động cập nhật khi DB thay đổi.
    public LiveData<List<ScanDocument>> getAllDocuments() {
        return documentDao.getAllDocuments();
    }

     //Tìm kiếm tài liệu theo từ khóa.
    public LiveData<List<ScanDocument>> search(String keyword) {
        return documentDao.search(keyword);
    }

     //Lấy danh sách tài liệu yêu thích.
    public LiveData<List<ScanDocument>> getFavorites() {
        return documentDao.getFavorites();
    }

    /**
      Toggle trạng thái yêu thích của một tài liệu.
      @param id        ID của tài liệu cần toggle
      @param isFavorite 1 = yêu thích, 0 = bỏ yêu thích
     */
    public void updateFavorite(int id, int isFavorite, OnCompleteCallback callback) {
        executor.execute(() -> {
            documentDao.updateFavorite(id, isFavorite);
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(callback::onComplete);
            }
        });
    }

    // ─────────────────────────────────────────
    // WRITE — bắt buộc chạy trên background thread
    // ─────────────────────────────────────────

    public interface OnInsertCompleteCallback {
        void onComplete(long newId);
    }

    /**
      Lưu tài liệu mới vào DB.
      Callback onComplete được gọi trên main thread sau khi insert xong.
     */
    public void insert(ScanDocument document, OnInsertCompleteCallback callback) {
        executor.execute(() -> {
            long newId = documentDao.insert(document);
            if (callback != null) {
                // Thông báo thành công về main thread
                android.os.Handler mainHandler =
                        new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> callback.onComplete(newId));
            }
        });
    }

    // Luu tai lieu cung toan bo danh sach trang con vao DB
    public void insertDocumentWithPages(ScanDocument document, List<DocumentPage> pages, OnInsertCompleteCallback callback) {
        executor.execute(() -> {
            long newId = documentDao.insert(document);
            if (pages != null && !pages.isEmpty()) {
                for (DocumentPage p : pages) {
                    p.documentId = (int) newId;
                }
                documentPageDao.insertAll(pages);
            }
            if (callback != null) {
                android.os.Handler mainHandler =
                        new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> callback.onComplete(newId));
            }
        });
    }

    // Callback khi load xong danh sach trang con
    public interface OnPagesLoadedCallback {
        void onLoaded(List<DocumentPage> pages);
    }

    // Lay danh sach cac trang theo id tai lieu
    public void getPagesByDocumentId(int documentId, OnPagesLoadedCallback callback) {
        executor.execute(() -> {
            List<DocumentPage> pages = documentPageDao.getPagesByDocumentId(documentId);
            if (callback != null) {
                android.os.Handler mainHandler =
                        new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> callback.onLoaded(pages));
            }
        });
    }

    /**
      Cập nhật tài liệu đã có trong DB.
     */
    public void update(ScanDocument document, OnCompleteCallback callback) {
        executor.execute(() -> {
            documentDao.update(document);
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(callback::onComplete);
            }
        });
    }

    /**
      Xóa một tài liệu khỏi DB.
     */
    public void delete(ScanDocument document, OnCompleteCallback callback) {
        executor.execute(() -> {
            documentDao.delete(document);
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(callback::onComplete);
            }
        });
    }

    /**
      Xóa toàn bộ lịch sử.
     */
    public void deleteAll(OnCompleteCallback callback) {
        executor.execute(() -> {
            documentDao.deleteAll();
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(callback::onComplete);
            }
        });
    }

    // ─────────────────────────────────────────
    // Callback interface — thông báo hoàn tất về UI
    // ─────────────────────────────────────────

    /**
      Interface callback đơn giản, báo hiệu tác vụ background đã xong.
     */
    public interface OnCompleteCallback {
        void onComplete();
    }
}
