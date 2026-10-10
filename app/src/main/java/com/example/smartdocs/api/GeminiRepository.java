package com.example.smartdocs.api;

import com.example.smartdocs.api.model.GeminiRequest;
import com.example.smartdocs.api.model.GeminiResponse;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/*
 GeminiRepository — lớp trung gian xử lý logic gọi Gemini API.
 1. Xây dựng prompt phù hợp từ văn bản OCR thô
 2. Thực hiện API call bất đồng bộ qua Retrofit
 3. Parse response và trả kết quả qua callback
 4. Xử lý các trường hợp lỗi (network, API key sai, quota hết...)
 */
public class GeminiRepository {

    // API Key mặc định được cấu hình an toàn từ BuildConfig (đọc từ local.properties)
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


    //Xác định API Key cần dùng: ưu tiên key người dùng nhập, nếu rỗng thì dùng key mặc định từ BuildConfig.
    String resolveApiKey(String explicitKey) {
        if (explicitKey != null && !explicitKey.trim().isEmpty()) {
            return explicitKey.trim();
        }
        return DEFAULT_API_KEY;
    }

    // Callback interface

    //Callback trả kết quả từ Gemini API về cho caller (ViewModel).\
    public interface GeminiCallback {
        /*
          Gọi khi API trả về thành công.
          @param summary Văn bản tóm tắt / kết quả xử lý từ AI
         */
        void onSuccess(String summary);

        /*
          Gọi khi có lỗi.
          @param errorMessage Thông báo lỗi hiển thị cho người dùng
         */
        void onError(String errorMessage);
    }

    // API Methods

     //Gửi văn bản OCR lên Gemini để tóm tắt.
    public void summarizeText(String apiKey, String ocrText, GeminiCallback callback) {
        String prompt = buildSummarizePrompt(ocrText);
        callGeminiApi(apiKey, prompt, callback);
    }

    public void summarizeText(String ocrText, GeminiCallback callback) {
        summarizeText(null, ocrText, callback);
    }


    //Gửi văn bản OCR lên Gemini để dịch sang tiếng Việt.
    public void translateToVietnamese(String apiKey, String ocrText, GeminiCallback callback) {
        String prompt = buildTranslatePrompt(ocrText);
        callGeminiApi(apiKey, prompt, callback);
    }

    public void translateToVietnamese(String ocrText, GeminiCallback callback) {
        translateToVietnamese(null, ocrText, callback);
    }


    //Gửi văn bản OCR lên Gemini để trích xuất thông tin quan trọng .
    public void extractKeyInfo(String apiKey, String ocrText, GeminiCallback callback) {
        String prompt = buildExtractPrompt(ocrText);
        callGeminiApi(apiKey, prompt, callback);
    }

    public void extractKeyInfo(String ocrText, GeminiCallback callback) {
        extractKeyInfo(null, ocrText, callback);
    }

    //Smart Q&A: Gửi câu hỏi về tài liệu lên Gemini.
    public void askQuestion(String apiKey, String ocrText, String cauHoi, String chatHistory, GeminiCallback callback) {
        String prompt = buildQuestionPrompt(ocrText, cauHoi, chatHistory);
        callGeminiApi(apiKey, prompt, callback);
    }


    //Smart Q&A: Gửi câu hỏi về tài liệu lên Gemini.
    public void askQuestion(String apiKey, String ocrText, String cauHoi, GeminiCallback callback) {
        askQuestion(apiKey, ocrText, cauHoi, null, callback);
    }

    public void askQuestion(String ocrText, String cauHoi, GeminiCallback callback) {
        askQuestion(null, ocrText, cauHoi, null, callback);
    }


    // Sửa lỗi chính tả / sai dấu tiếng Việt trong kết quả OCR thô từ ML Kit.
    public void correctOcrText(String apiKey, String ocrTextTho, GeminiCallback callback) {
        String keyToUse = resolveApiKey(apiKey);
        if (keyToUse == null || keyToUse.trim().isEmpty()) {
            callback.onError("Chưa cấu hình API Key. Vui lòng vào Cài đặt để thêm key.");
            return;
        }
        String prompt = buildCorrectOcrPrompt(ocrTextTho);
        GeminiRequest request = new GeminiRequest(prompt);
        apiService.generateContent(keyToUse, request)
                .enqueue(new Callback<GeminiResponse>() {

                    @Override
                    public void onResponse(Call<GeminiResponse> call,
                                           Response<GeminiResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            String rawText = response.body().extractText();

                            if (rawText == null || rawText.trim().isEmpty()) {
                                callback.onError("Gemini trả về kết quả rỗng khi sửa OCR.");
                                return;
                            }

                            // Parse JSON
                            String textDaSua = trichXuatCorrectedText(rawText.trim());
                            callback.onSuccess(textDaSua);

                        } else {
                            callback.onError(parseHttpError(response.code()));
                        }
                    }
                    @Override
                    public void onFailure(Call<GeminiResponse> call, Throwable t) {
                        callback.onError(parseNetworkError(t));
                    }
                });
    }

    // Prompt Engineering
    //Xây dựng prompt tóm tắt .
    String buildSummarizePrompt(String ocrText) {
        return "Bạn là trợ lý phân tích tài liệu thông minh.\n\n"
                + "Văn bản sau được trích xuất từ ảnh tài liệu bằng OCR:\n"
                + "---\n"
                + ocrText
                + "\n---\n\n"
                + "Nhiệm vụ:\n"
                + "1. Tóm tắt nội dung chính trong 3-5 câu ngắn gọn bằng tiếng Việt.\n"
                + "2. Nêu các điểm quan trọng nhất của tài liệu.\n"
                + "3. Nếu văn bản không rõ ràng do OCR lỗi, hãy cố gắng hiểu và diễn giải.\n\n"
                + "Chỉ trả lời phần tóm tắt, không cần giải thích thêm.";
    }

    //Xây dựng prompt dịch thuật — dịch toàn bộ văn bản sang tiếng Việt.
    String buildTranslatePrompt(String ocrText) {
        return "Dịch toàn bộ văn bản sau sang tiếng Việt, giữ nguyên cấu trúc và định dạng:\n\n"
                + ocrText
                + "\n\nChỉ trả về bản dịch, không thêm chú thích.";
    }


    //Xây dựng prompt trích xuất thông tin quan trọng từ tài liệu.

    String buildExtractPrompt(String ocrText) {
        return "Từ văn bản OCR sau, hãy trích xuất các thông tin quan trọng:\n\n"
                + ocrText
                + "\n\nHãy liệt kê dạng bullet points các thông tin chính như:\n"
                + "- Tên/Tiêu đề\n"
                + "- Ngày tháng\n"
                + "- Số điện thoại, email, địa chỉ\n"
                + "- Số tiền, mã số\n"
                + "- Các thông tin quan trọng khác\n\n"
                + "Chỉ liệt kê những thông tin thực sự có trong văn bản.";
    }

    /*
      Xây dựng prompt Smart Q&A — Gemini đóng vai trợ lý đọc tài liệu.
      Hỗ trợ ngữ cảnh lịch sử chat (2-3 tin nhắn gần nhất) để hiểu câu hỏi nối tiếp.
     */
    String buildQuestionPrompt(String ocrText, String cauHoi, String chatHistory) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là trợ lý thông minh giúp người dùng hiểu tài liệu.\n\n");
        sb.append("NỘI DUNG TÀI LIỆU (trích xuất bằng OCR):\n");
        sb.append("===\n");
        sb.append(ocrText);
        sb.append("\n===\n\n");

        if (chatHistory != null && !chatHistory.trim().isEmpty()) {
            sb.append("LỊCH SỬ HỘI THOẠI GẦN ĐÂY (dùng để hiểu ngữ cảnh các câu hỏi tiếp nối):\n");
            sb.append("---\n");
            sb.append(chatHistory.trim());
            sb.append("\n---\n\n");
        }

        sb.append("CÂU HỎI CỦA NGƯỜI DÙNG: ").append(cauHoi).append("\n\n");
        sb.append("HƯỚNG DẪN TRẢ LỜI:\n");
        sb.append("- Chỉ trả lời dựa trên nội dung tài liệu ở trên và ngữ cảnh hội thoại trước đó.\n");
        sb.append("- Hiểu rõ các đại từ thay thế (như 'ai', 'ở đâu', 'khi nào', 'số tiền đó', 'họ', 'ông ấy/bà ấy') dựa theo lịch sử trò chuyện.\n");
        sb.append("- Nếu câu hỏi không liên quan hoặc tài liệu không có thông tin, hãy nói rõ.\n");
        sb.append("- Trả lời ngắn gọn, súc tích bằng tiếng Việt.\n");
        sb.append("- Không thêm thông tin ngoài tài liệu.");

        return sb.toString();
    }

    /*
      Xây dựng prompt sửa lỗi OCR tiếng Việt — trả về JSON có cấu trúc.
      Dùng JSON output thay vì text thô để:
      1. Tránh Gemini thêm câu giao tiếp ngoài nội dung cần sửa
      2. Parse chính xác bằng org.json (có sẵn trong Android SDK)
      3. Dễ dàng validate và fallback nếu JSON sai format
     */
    String buildCorrectOcrPrompt(String ocrTextTho) {
        return "Bạn là một hệ thống tự động sửa lỗi chính tả tiếng Việt từ dữ liệu OCR.\n"
                + "TUYỆT ĐỐI TUÂN THỦ CÁC QUY TẮC SAU:\n"
                + "1. Sửa các lỗi chính tả, sai dấu tiếng Việt.\n"
                + "2. Giữ nguyên 100% nội dung, ý nghĩa và cấu trúc xuống dòng của văn bản gốc.\n"
                + "3. KHÔNG giải thích, KHÔNG bình luận, KHÔNG thêm bất kỳ từ ngữ nào ngoài nội dung đã sửa.\n"
                + "4. Trả về kết quả DUY NHẤT dưới định dạng JSON có cấu trúc như sau:\n"
                + "{\n"
                + "  \"corrected_text\": \"nội dung văn bản sau khi đã sửa lỗi\"\n"
                + "}\n\n"
                + "Văn bản cần sửa:\n"
                + ocrTextTho;
    }

    // Core: thực hiện API call
    /*
      Gọi Gemini API với prompt đã xây dựng.
      Retrofit tự chạy request trên background thread,
      callback được gọi trên main thread.
     */
    void callGeminiApi(String apiKey, String prompt, GeminiCallback callback) {
        String keyToUse = resolveApiKey(apiKey);
        if (keyToUse == null || keyToUse.trim().isEmpty()) {
            callback.onError("Chưa cấu hình API Key. Vui lòng vào Cài đặt để thêm key.");
            return;
        }

        GeminiRequest request = new GeminiRequest(prompt);

        apiService.generateContent(keyToUse, request)
                .enqueue(new Callback<GeminiResponse>() {
                    @Override
                    public void onResponse(Call<GeminiResponse> call,
                                           Response<GeminiResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            // Parse văn bản từ response
                            String text = response.body().extractText();
                            if (text != null && !text.trim().isEmpty()) {
                                callback.onSuccess(text.trim());
                            } else {
                                callback.onError("Gemini trả về kết quả rỗng.\n"
                                        + "Hãy kiểm tra lại API Key hoặc thử lại.");
                            }
                        } else {
                            // HTTP error
                            int code = response.code();
                            String message = parseHttpError(code);
                            callback.onError(message);
                        }
                    }
                    @Override
                    public void onFailure(Call<GeminiResponse> call, Throwable t) {
                        callback.onError(parseNetworkError(t));
                    }
                });
    }


    //Phân biệt các loại lỗi mạng.
    String parseNetworkError(Throwable t) {
        if (t instanceof UnknownHostException) {
            return "Không có kết nối Internet!\n"
                    + "Vui lòng kiểm tra Internet và thử lại.";
        } else if (t instanceof SocketTimeoutException) {
            return "Kết nối quá chậm)!\n"
                    + "Kiểm tra chất lượng mạng và thử lại.";
        } else if (t instanceof IOException) {
            return "Mất kết nối giữa chừng!\n"
                    + "Có thể do Wifi không ổn định. Thử lại sau.";
        } else {
            return "Lỗi không xác định: "
                    + (t.getMessage() != null ? t.getMessage() : "Unknown error")
                    + "\nThử lại hoặc khởi động lại ứng dụng.";
        }
    }


    //Chuyển mã HTTP error thành thông báo.
    String parseHttpError(int httpCode) {
        switch (httpCode) {
            case 400:
                return "Yêu cầu không hợp lệ (400). Văn bản có thể quá dài.";
            case 401:
                return "API Key không hợp lệ (401).\nVào Settings để cập nhật API Key.";
            case 403:
                return "Không có quyền truy cập (403). Kiểm tra API Key.";
            case 429:
                return "Đã vượt quá giới hạn request (429).\nVui lòng thử lại sau ít phút.";
            case 500:
                return "Lỗi máy chủ Gemini (500). Thử lại sau.";
            case 503:
                return "Dịch vụ Gemini tạm thời không khả dụng (503). Thử lại sau.";
            default:
                return "Lỗi HTTP " + httpCode + ". Vui lòng thử lại.";
        }
    }

    /**
      Trích xuất giá trị "corrected_text" từ JSON response của Gemini.
      Xử lý 3 tình huống:
        1. JSON hợp lệ đúng format    → parse và trả về corrected_text
        2. Gemini bọc JSON trong ```json ... ```  → strip rồi parse
        3. Parse thất bại hoàn toàn   → fallback về rawText để không mất dữ liệu
      Dùng org.json — thư viện built-in của Android, không cần thêm dependency.
      @param rawText Text thô từ Gemini
      @return Văn bản đã sửa, hoặc rawText nếu không parse được
     */
    String trichXuatCorrectedText(String rawText) {
        try {
            // TH2: Gemini hay bọc JSON trong markdown code fence
            // VD: ```json\n{"corrected_text": "..."}\n```
            String chuanHoa = rawText;
            if (chuanHoa.contains("```")) {
                // Xóa phần mở: ```json hoặc ``` rồi lấy nội dung bên trong
                chuanHoa = chuanHoa.replaceAll("```json\\s*", "")
                                   .replaceAll("```\\s*", "")
                                   .trim();
            }
            // TH1: Parse JSON lấy giá trị corrected_text
            org.json.JSONObject jsonObj = new org.json.JSONObject(chuanHoa);
            String correctedText = jsonObj.getString("corrected_text");
            if (correctedText != null && !correctedText.trim().isEmpty()) {
                return correctedText.trim();
            }
            // corrected_text rỗng — fallback về raw text
            return rawText;
        } catch (org.json.JSONException e) {
            // TH3: Gemini không trả về JSON đúng format
            // Fallback an toàn: dùng raw text
            return rawText;
        }
    }
}
