package com.example.smartdocs.api;

import com.example.smartdocs.api.model.GeminiRequest;
import com.example.smartdocs.api.model.GeminiResponse;
import com.example.smartdocs.data.model.QuizQuestion;

import java.util.ArrayList;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Lop Facade trung gian dieu phoi logic goi Gemini API
public class GeminiRepository {

    // API Key mac dinh duoc cau hinh tu BuildConfig
    static final String DEFAULT_API_KEY = com.example.smartdocs.BuildConfig.GEMINI_API_KEY;

    GeminiApiService apiService;

    // Singleton instance
    static GeminiRepository instance;

    private GeminiRepository() {
        apiService = ApiClient.getInstance().getApiService();
    }

    public static GeminiRepository getInstance() {
        if (instance == null) {
            synchronized (GeminiRepository.class) {
                if (instance == null) {
                    instance = new GeminiRepository();
                }
            }
        }
        return instance;
    }

    // Xac dinh API Key can dung: uu tien key nguoi dung nhap, neu rong thi dung key mac dinh
    String resolveApiKey(String explicitKey) {
        if (explicitKey != null && !explicitKey.trim().isEmpty()) {
            return explicitKey.trim();
        }
        return DEFAULT_API_KEY;
    }

    // Callback tra ket qua tu Gemini API ve cho caller
    public interface GeminiCallback {
        void onSuccess(String summary);
        void onError(String errorMessage);
    }

    // Gui van ban OCR len Gemini de tom tat
    public void summarizeText(String apiKey, String ocrText, GeminiCallback callback) {
        String prompt = GeminiPromptBuilder.buildSummarizePrompt(ocrText);
        callGeminiApi(apiKey, prompt, callback);
    }

    public void summarizeText(String ocrText, GeminiCallback callback) {
        summarizeText(null, ocrText, callback);
    }

    // Gui van ban OCR len Gemini de dich sang tieng Viet
    public void translateToVietnamese(String apiKey, String ocrText, GeminiCallback callback) {
        String prompt = GeminiPromptBuilder.buildTranslatePrompt(ocrText);
        callGeminiApi(apiKey, prompt, callback);
    }

    public void translateToVietnamese(String ocrText, GeminiCallback callback) {
        translateToVietnamese(null, ocrText, callback);
    }

    // Gui van ban OCR len Gemini de trich xuat thong tin quan trong
    public void extractKeyInfo(String apiKey, String ocrText, GeminiCallback callback) {
        String prompt = GeminiPromptBuilder.buildExtractPrompt(ocrText);
        callGeminiApi(apiKey, prompt, callback);
    }

    public void extractKeyInfo(String ocrText, GeminiCallback callback) {
        extractKeyInfo(null, ocrText, callback);
    }

    // Trich xuat tu vung va thuat ngu trong tam tu tai lieu
    public void extractVocabulary(String apiKey, String ocrText, GeminiCallback callback) {
        String prompt = GeminiPromptBuilder.buildVocabularyPrompt(ocrText);
        callGeminiApi(apiKey, prompt, callback);
    }

    public void extractVocabulary(String ocrText, GeminiCallback callback) {
        extractVocabulary(null, ocrText, callback);
    }

    // Smart Q&A: Gui cau hoi ve tai lieu len Gemini co lich su hoi thoai
    public void askQuestion(String apiKey, String ocrText, String cauHoi, String chatHistory, GeminiCallback callback) {
        String prompt = GeminiPromptBuilder.buildQuestionPrompt(ocrText, cauHoi, chatHistory);
        callGeminiApi(apiKey, prompt, callback);
    }

    public void askQuestion(String apiKey, String ocrText, String cauHoi, GeminiCallback callback) {
        askQuestion(apiKey, ocrText, cauHoi, null, callback);
    }

    public void askQuestion(String ocrText, String cauHoi, GeminiCallback callback) {
        askQuestion(null, ocrText, cauHoi, null, callback);
    }

    // Phan loai loai tai lieu bang Gemini AI
    public void classifyDocumentType(String apiKey, String ocrText, GeminiCallback callback) {
        String prompt = GeminiPromptBuilder.buildClassifyPrompt(ocrText);
        callGeminiApi(apiKey, prompt, callback);
    }

    public void classifyDocumentType(String ocrText, GeminiCallback callback) {
        classifyDocumentType(null, ocrText, callback);
    }

    // Tao Ban do tu duy 4 tang bang Gemini AI
    public void generateMindmap(String apiKey, String ocrText, GeminiCallback callback) {
        String prompt = GeminiPromptBuilder.buildMindmapPrompt(ocrText);
        callGeminiApi(apiKey, prompt, callback);
    }

    public void generateMindmap(String ocrText, GeminiCallback callback) {
        generateMindmap(null, ocrText, callback);
    }

    // Dinh dang lai van ban OCR theo dung cau truc goc trong anh
    public void formatOcrLayout(String apiKey, String ocrText, GeminiCallback callback) {
        String prompt = GeminiPromptBuilder.buildFormatOcrPrompt(ocrText);
        callGeminiApi(apiKey, prompt, callback);
    }

    public void formatOcrLayout(String ocrText, GeminiCallback callback) {
        formatOcrLayout(null, ocrText, callback);
    }

    // Sua loi chinh ta / sai dau tieng Viet trong ket qua OCR tho
    public void correctOcrText(String apiKey, String ocrTextTho, final GeminiCallback callback) {
        String keyToUse = resolveApiKey(apiKey);
        if (keyToUse == null || keyToUse.trim().isEmpty()) {
            callback.onError("Chưa cấu hình API Key. Vui lòng vào Cài đặt để thêm key.");
            return;
        }
        String prompt = GeminiPromptBuilder.buildCorrectOcrPrompt(ocrTextTho);
        GeminiRequest request = new GeminiRequest(prompt);
        apiService.generateContent(keyToUse, request)
                .enqueue(new Callback<GeminiResponse>() {
                    @Override
                    public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            String rawText = response.body().extractText();
                            if (rawText == null || rawText.trim().isEmpty()) {
                                callback.onError("Gemini trả về kết quả rỗng khi sửa OCR.");
                                return;
                            }
                            String textDaSua = GeminiParser.trichXuatCorrectedText(rawText.trim());
                            callback.onSuccess(textDaSua);
                        } else {
                            callback.onError(GeminiParser.parseHttpError(response.code()));
                        }
                    }

                    @Override
                    public void onFailure(Call<GeminiResponse> call, Throwable t) {
                        callback.onError(GeminiParser.parseNetworkError(t));
                    }
                });
    }

    // Trich xuat so lieu tu van ban OCR de ve bieu do
    public void extractChartData(String apiKey, String ocrText, GeminiCallback callback) {
        String prompt = GeminiPromptBuilder.buildChartDataPrompt(ocrText);
        callGeminiApi(apiKey, prompt, callback);
    }

    public void extractChartData(String ocrText, GeminiCallback callback) {
        extractChartData(null, ocrText, callback);
    }

    // Tao 5 cau hoi trac nghiem on tap bang Gemini AI
    public void generateQuiz(String apiKey, String ocrText, GeminiCallback callback) {
        String prompt = GeminiPromptBuilder.buildQuizPrompt(ocrText);
        callGeminiApi(apiKey, prompt, callback);
    }

    public void generateQuiz(String ocrText, GeminiCallback callback) {
        generateQuiz(null, ocrText, callback);
    }

    // Parse JSON ket qua tra ve thanh danh sach QuizQuestion
    public static ArrayList<QuizQuestion> parseQuizJson(String rawText) {
        return GeminiParser.parseQuizJson(rawText);
    }

    // Core: thuc hien goi Gemini API voi prompt da xay dung
    void callGeminiApi(String apiKey, String prompt, final GeminiCallback callback) {
        String keyToUse = resolveApiKey(apiKey);
        if (keyToUse == null || keyToUse.trim().isEmpty()) {
            callback.onError("Chưa cấu hình API Key. Vui lòng vào Cài đặt để thêm key.");
            return;
        }

        GeminiRequest request = new GeminiRequest(prompt);
        apiService.generateContent(keyToUse, request)
                .enqueue(new Callback<GeminiResponse>() {
                    @Override
                    public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            String text = response.body().extractText();
                            if (text != null && !text.trim().isEmpty()) {
                                callback.onSuccess(text.trim());
                            } else {
                                callback.onError("Gemini trả về kết quả rỗng.\nHãy kiểm tra lại API Key hoặc thử lại.");
                            }
                        } else {
                            int code = response.code();
                            String message = GeminiParser.parseHttpError(code);
                            callback.onError(message);
                        }
                    }

                    @Override
                    public void onFailure(Call<GeminiResponse> call, Throwable t) {
                        callback.onError(GeminiParser.parseNetworkError(t));
                    }
                });
    }
}
