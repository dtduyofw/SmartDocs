package com.example.smartdocs.ui.viewmodel;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.smartdocs.data.model.ScanDocument;
import com.example.smartdocs.data.repository.DocumentRepository;
import com.example.smartdocs.api.GeminiRepository;
import com.example.smartdocs.ui.SettingsFragment;

import java.util.List;

/**
 * ViewModel — giữ trạng thái UI và sống qua configuration change (xoay màn hình).
 *
 * Extends AndroidViewModel (thay vì ViewModel) để có Application context
 * cần thiết cho Repository mà không gây memory leak như giữ Activity context.
 *
 * UI (Activity/Fragment) chỉ observe LiveData từ đây, không gọi trực tiếp DB.
 */
public class DocumentViewModel extends AndroidViewModel {

    DocumentRepository repository;

    // ─────────────────────────────────────────
    // LiveData cho danh sách tài liệu (từ Room)
    // ─────────────────────────────────────────
    LiveData<List<ScanDocument>> allDocuments;

    // ─────────────────────────────────────────
    // MutableLiveData quản lý trạng thái UI
    // ─────────────────────────────────────────

    // Trạng thái loading toàn cục (hiển thị/ẩn ProgressBar)
    MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    // Thông báo lỗi (null = không có lỗi)
    MutableLiveData<String> errorMessage = new MutableLiveData<>(null);

    // Văn bản OCR trả về sau khi scan
    MutableLiveData<String> ocrResult = new MutableLiveData<>(null);

    // Văn bản tóm tắt AI trả về sau khi gọi Gemini API
    MutableLiveData<String> aiSummary = new MutableLiveData<>(null);

    // URI ảnh hiện tại đang xử lý
    MutableLiveData<Uri> selectedImageUri = new MutableLiveData<>(null);

    // Tín hiệu lưu thành công, chứa tài liệu vừa lưu (dùng cho Toast / Navigation)
    MutableLiveData<ScanDocument> newlySavedDocument = new MutableLiveData<>(null);

    // FIX #2 (Lifecycle): Giữ text trạng thái loading qua xoay màn hình
    // Nếu để setText() trong Activity, text sẽ mất sau khi rotate.
    MutableLiveData<String> loadingStatusText = new MutableLiveData<>("");


    public DocumentViewModel(@NonNull Application application) {
        super(application);
        repository = new DocumentRepository(application);
        // Bắt đầu observe toàn bộ danh sách ngay khi ViewModel khởi tạo
        allDocuments = repository.getAllDocuments();
    }

    /**
     * Expose repository để các caller đặc biệt (ví dụ: Undo sau khi xóa) có thể
     * gọi trực tiếp insert() mà không cần thêm method wrapper.
     */
    public DocumentRepository getRepository() {
        return repository;
    }

    // ─────────────────────────────────────────
    // Getter LiveData (Activity chỉ observe, không set trực tiếp)
    // ─────────────────────────────────────────

    public LiveData<List<ScanDocument>> getAllDocuments() {
        return allDocuments;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<String> getOcrResult() {
        return ocrResult;
    }

    public LiveData<String> getAiSummary() {
        return aiSummary;
    }

    public LiveData<Uri> getSelectedImageUri() {
        return selectedImageUri;
    }

    public LiveData<ScanDocument> getNewlySavedDocument() {
        return newlySavedDocument;
    }

    // FIX #2 (Lifecycle): Getter loadingStatusText — Activity observe để hiện text đúng sau rotate
    public LiveData<String> getLoadingStatusText() {
        return loadingStatusText;
    }

    // ─────────────────────────────────────────
    // Actions — UI gọi các method này
    // ─────────────────────────────────────────

    /**
     * Cập nhật URI ảnh khi người dùng chọn từ Gallery hoặc Camera.
     * Reset kết quả OCR và AI cũ để sẵn sàng cho lần scan mới.
     */
    public void setSelectedImageUri(Uri uri) {
        selectedImageUri.setValue(uri);
        // Xóa kết quả cũ khi chọn ảnh mới
        ocrResult.setValue(null);
        aiSummary.setValue(null);
        errorMessage.setValue(null);
    }

    /**
     * Cập nhật kết quả OCR (gọi từ OCR Helper sau khi nhận diện xong).
     */
    public void setOcrResult(String text) {
        ocrResult.setValue(text);
    }

    /**
     * Lưu trực tiếp kết quả OCR (Offline mode hoặc fallback) và phát tín hiệu mở preview.
     */
    public void ocrLuuTrucTiep(String ocrText) {
        ocrResult.setValue(ocrText);
        saveCurrentDocument();
    }

    /**
     * Dùng Gemini sửa lỗi OCR rồi tự động lưu vào database và phát tín hiệu mở preview.
     */
    public void ocrSuaLoiVaLuu(String textThoMlKit) {
        loadingStatusText.setValue("✍️ Gemini đang sửa lỗi OCR & lưu vào lịch sử...");
        isLoading.setValue(true);

        GeminiRepository.getInstance().correctOcrText(SettingsFragment.getApiKey(getApplication()), textThoMlKit, new GeminiRepository.GeminiCallback() {
            @Override
            public void onSuccess(String textDaSua) {
                SettingsFragment.tangAiCount(getApplication());
                ocrResult.setValue(textDaSua.trim());
                saveCurrentDocument();
            }

            @Override
            public void onError(String errorMsg) {
                // Fallback về text gốc ML Kit và lưu
                ocrResult.setValue(textThoMlKit);
                saveCurrentDocument();
            }
        });
    }

    /**
     * Cập nhật tóm tắt AI (gọi sau khi Gemini API trả về).
     */
    public void setAiSummary(String summary) {
        aiSummary.setValue(summary);
    }

    /**
     * Đặt trạng thái loading.
     * true = đang xử lý (hiện ProgressBar), false = xong (ẩn ProgressBar).
     */
    public void setLoading(boolean loading) {
        isLoading.setValue(loading);
    }

    /**
     * Đặt thông báo lỗi để hiển thị lên UI.
     */
    public void setError(String message) {
        errorMessage.setValue(message);
        isLoading.setValue(false); // Auto tắt loading khi có lỗi
    }

    /**
     * Lưu tài liệu hiện tại vào Room Database.
     * Tự động lấy giá trị từ các LiveData đang giữ.
     */
    public void saveCurrentDocument() {
        Uri uri = selectedImageUri.getValue();
        String ocr = ocrResult.getValue();
        String summary = aiSummary.getValue();

        // Kiểm tra dữ liệu hợp lệ trước khi lưu
        if (ocr == null || ocr.trim().isEmpty()) {
            setError("Chưa có dữ liệu OCR để lưu.");
            return;
        }

        // Tạo tiêu đề từ 5 từ đầu của văn bản OCR
        String title = buildTitle(ocr);

        ScanDocument document = new ScanDocument(
                uri != null ? uri.toString() : "",
                ocr,
                summary != null ? summary : "",
                System.currentTimeMillis(),
                title
        );

        setLoading(true);
        repository.insert(document, newId -> {
            // Callback trên main thread
            document.id = (int) newId;
            isLoading.setValue(false);
            newlySavedDocument.setValue(document);
        });
    }

    /**
     * Xóa một tài liệu.
     */
    public void deleteDocument(ScanDocument document) {
        repository.delete(document, null);
    }

    /**
     * Xóa toàn bộ lịch sử.
     */
    public void deleteAll() {
        repository.deleteAll(null);
    }

    /**
     * Lấy danh sách tài liệu yêu thích.
     */
    public LiveData<List<ScanDocument>> getFavorites() {
        return repository.getFavorites();
    }

    /**
     * Toggle yêu thích / bỏ yêu thích một tài liệu.
     * Nếu đang yêu thích (1) → bỏ (0), và ngược lại.
     */
    public void toggleFavorite(ScanDocument document) {
        int newValue = (document.isFavorite == 1) ? 0 : 1;
        document.isFavorite = newValue;
        repository.updateFavorite(document.id, newValue, null);
    }

    // ─────────────────────────────────────────
    // Gemini API Actions
    // ─────────────────────────────────────────

    /**
     * Kiểm tra xem có đang loading không.
     * Dùng để chặn spam click từ Activity.
     */
    public boolean isCurrentlyLoading() {
        Boolean val = isLoading.getValue();
        return val != null && val;
    }

    /**
     * Tìm kiếm tài liệu theo từ khóa.
     * Trả về LiveData để UI tự observe kết quả.
     */
    public LiveData<List<ScanDocument>> search(String keyword) {
        return repository.search(keyword);
    }

    /**
     * Reset tín hiệu saveSuccess về false sau khi UI đã xử lý.
     * Tránh trigger lại khi Activity recreate.
     */
    public void resetSaveSuccess() {
        newlySavedDocument.setValue(null);
    }

    /**
     * Làm mới toàn bộ trạng thái tạm thời của phiên quét hiện tại.
     */
    public void reset() {
        selectedImageUri.setValue(null);
        ocrResult.setValue(null);
        aiSummary.setValue(null);
        errorMessage.setValue(null);
    }

    // ─────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────

    /**
     * Tạo tiêu đề ngắn từ văn bản OCR (lấy tối đa 5 từ đầu).
     */
    private String buildTitle(String ocrText) {
        if (ocrText == null || ocrText.trim().isEmpty()) return "Tài liệu không tiêu đề";
        String[] words = ocrText.trim().split("\\s+");
        int limit = Math.min(5, words.length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append(" ");
            sb.append(words[i]);
        }
        if (words.length > 5) sb.append("...");
        return sb.toString();
    }
}
