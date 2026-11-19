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

    // Trich xuat tu vung va thuat ngu trong tam tu tai lieu
    public void extractVocabulary(String apiKey, String ocrText, GeminiCallback callback) {
        String prompt = buildVocabularyPrompt(ocrText);
        callGeminiApi(apiKey, prompt, callback);
    }

    public void extractVocabulary(String ocrText, GeminiCallback callback) {
        extractVocabulary(null, ocrText, callback);
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

    // Phan loai loai tai lieu bang Gemini AI
    public void classifyDocumentType(String apiKey, String ocrText, GeminiCallback callback) {
        String prompt = buildClassifyPrompt(ocrText);
        callGeminiApi(apiKey, prompt, callback);
    }

    public void classifyDocumentType(String ocrText, GeminiCallback callback) {
        classifyDocumentType(null, ocrText, callback);
    }

    // Tao Ban do tu duy 4 tang bang Gemini AI
    public void generateMindmap(String apiKey, String ocrText, GeminiCallback callback) {
        String prompt = buildMindmapPrompt(ocrText);
        callGeminiApi(apiKey, prompt, callback);
    }

    public void generateMindmap(String ocrText, GeminiCallback callback) {
        generateMindmap(null, ocrText, callback);
    }

    // Dinh dang lai van ban OCR theo dung cu truc goc trong anh — bang biet, tieu de, v.v.
    public void formatOcrLayout(String apiKey, String ocrText, GeminiCallback callback) {
        String prompt = buildFormatOcrPrompt(ocrText);
        callGeminiApi(apiKey, prompt, callback);
    }

    public void formatOcrLayout(String ocrText, GeminiCallback callback) {
        formatOcrLayout(null, ocrText, callback);
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

    // Xay dung prompt dich thuat sang tieng Viet
    String buildTranslatePrompt(String ocrText) {
        return "Bạn là chuyên gia dịch thuật tài liệu.\n"
                + "Nhiệm vụ: Dịch toàn bộ văn bản OCR sau sang tiếng Việt một cách tự nhiên, mạch lạc, giữ nguyên thuật ngữ chuyên ngành quan trọng trong ngoặc đơn và trình bày rõ ràng theo bố cục gốc của tài liệu:\n\n"
                + "---\n"
                + ocrText
                + "\n---\n\n"
                + "Chỉ trả về bản dịch hoàn chỉnh bằng tiếng Việt, không thêm lời dẫn giải hay chú thích ngoài lề.";
    }

    // Xay dung prompt trich xuat thong tin cot loi tu tai lieu
    String buildExtractPrompt(String ocrText) {
        return "Bạn là trợ lý trích xuất dữ liệu tài liệu thông minh.\n"
                + "Nhiệm vụ: Từ văn bản OCR sau đây, hãy bóc tách toàn bộ các trường dữ liệu cốt lõi có trong tài liệu (họ tên, ngày tháng, mã số, địa chỉ, đơn vị cấp, số tiền, điều khoản chính...) thành từng dòng gạch đầu dòng ngắn gọn để người dùng dễ sao chép:\n\n"
                + "---\n"
                + ocrText
                + "\n---\n\n"
                + "Quy tắc:\n"
                + "- Liệt kê rõ ràng từng mục dạng gạch đầu dòng ngắn gọn, súc tích.\n"
                + "- Chỉ trích xuất các thông tin thực tế xuất hiện trong văn bản.\n"
                + "- Không thêm nhận xét hay lời dẫn giải ngoài lề.";
    }

    // Xay dung prompt loc tu vung trong tam
    String buildVocabularyPrompt(String ocrText) {
        return "Bạn là trợ lý học tập và giảng viên ngôn ngữ thông minh.\n"
                + "Nhiệm vụ: Hãy đọc kỹ văn bản tài liệu sau và lọc ra 8-12 từ vựng ngoại ngữ hoặc thuật ngữ chuyên ngành quan trọng nhất trong tài liệu.\n\n"
                + "Định dạng trình bày cho mỗi mục gồm:\n"
                + "- Từ/Thuật ngữ: [Từ gốc]\n"
                + "- Loại từ / Phiên âm: [Loại từ hoặc phiên âm nếu có]\n"
                + "- Nghĩa tiếng Việt: [Nghĩa chuẩn xác]\n"
                + "- Ví dụ ngắn: [Câu ví dụ ngắn bám sát ngữ cảnh bài đọc kèm dịch nghĩa]\n\n"
                + "Quy tắc:\n"
                + "- Bám sát đúng nội dung tài liệu bên dưới.\n"
                + "- Trình bày danh sách rõ ràng, dễ đọc, không thêm lời dẫn giải mở đầu hay kết thúc.\n\n"
                + "Nội dung tài liệu:\n"
                + "---\n"
                + ocrText
                + "\n---";
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
      Xây dựng prompt sửa lỗi OCR tiếng Việt và tự động tái cấu trúc bảng biểu.
      Dùng JSON output để parse chuẩn xác.
     */
    String buildCorrectOcrPrompt(String ocrTextTho) {
        return "Bạn là một hệ thống AI xử lý tài liệu thông minh: tự động sửa lỗi chính tả và tái tạo cấu trúc chuẩn xác từ dữ liệu OCR.\n"
                + "TUYỆT ĐỐI TUÂN THỦ CÁC QUY TẮC SAU:\n"
                + "1. Sửa toàn bộ lỗi chính tả, nhận diện sai ký tự và sai dấu tiếng Việt.\n"
                + "2. TỰ ĐỘNG NHẬN DIỆN VÀ TÁI TẠO CẤU TRÚC GỐC:\n"
                + "   - NẾU TÀI LIỆU CÓ BẢNG (bảng biểu, số liệu cột, hóa đơn danh mục hàng hóa, bảng điểm, thời khóa biểu, bảng thống kê...): BẮT BUỘC dựng lại thành bảng HTML hoàn chỉnh sử dụng các thẻ <table border='1'>, <tr>, <th> (cho tiêu đề cột) và <td> (cho các ô dữ liệu).\n"
                + "   - Nếu có tiêu đề chính/phụ: dùng <h3> hoặc <b>.\n"
                + "   - Nếu có danh sách: dùng <ul>/<li> hoặc gạch đầu dòng rõ ràng.\n"
                + "   - Nếu là văn bản thường: giữ nguyên cấu trúc đoạn văn bản mạch lạc, ngắt dòng hợp lý.\n"
                + "3. KHÔNG thêm bất kỳ câu giao tiếp, giải thích hay bình luận nào.\n"
                + "4. Trả về kết quả DUY NHẤT dưới định dạng JSON có cấu trúc như sau:\n"
                + "{\n"
                + "  \"corrected_text\": \"toàn bộ nội dung văn bản sau khi đã sửa lỗi và định dạng bảng HTML (nếu có)\"\n"
                + "}\n\n"
                + "Văn bản OCR cần xử lý:\n"
                + ocrTextTho;
    }

    /*
      Prompt dinh dang lai van ban OCR theo cau truc goc trong anh.
      Gemini se nhan dang bang bieu, tieu de, danh sach va tra ve HTML.
     */
    String buildFormatOcrPrompt(String ocrText) {
        return "Bạn là hệ thống tái cấu trúc tài liệu từ văn bản OCR.\n"
                + "Dưới đây là văn bản trích xuất từ ảnh tài liệu bằng OCR (có thể bị lộn xộn, thiếu căn chỉnh hàng cột).\n"
                + "Nhiệm vụ của bạn:\n"
                + "1. Phân tích cấu trúc gốc của tài liệu (tiêu đề, đoạn văn, danh sách, bảng biểu số liệu, v.v.).\n"
                + "2. Tái tạo lại định dạng bằng HTML:\n"
                + "   - Nếu phát hiện bảng biểu (hàng/cột số liệu, danh sách có cấu trúc cột, hóa đơn, bảng thống kê): BẮT BUỘC dùng thẻ <table border='1'>, <tr>, <th>, <td> đầy đủ.\n"
                + "   - Tiêu đề dùng <h3>. Danh sách dùng <ul>/<li>. Đoạn văn dùng <p>.\n"
                + "3. KHÔNG giải thích, KHÔNG bình luận, KHÔNG bọc trong markdown code block (không dùng ```html).\n"
                + "4. Chỉ trả về đúng nội dung HTML bắt đầu bằng thẻ HTML đầu tiên.\n\n"
                + "Văn bản OCR cần tái cấu trúc:\n"
                + "---\n"
                + ocrText
                + "\n---";
    }

    String buildClassifyPrompt(String ocrText) {
        return "Bạn là hệ thống phân loại tài liệu thông minh. Dựa vào nội dung văn bản sau, hãy phân loại tài liệu vào đúng 1 trong 5 nhóm:\n"
                + "1. Hóa đơn (hóa đơn, biên lai, phiếu thu, phiếu thanh toán, bill, vé tàu xe...)\n"
                + "2. Giấy tờ cá nhân (căn cước công dân, cccd, bằng lái xe, hộ chiếu, thẻ sinh viên, thẻ ngân hàng...)\n"
                + "3. Tài liệu học tập (giáo trình, sách, đề thi, bài tập, bài giảng, ghi chép học tập...)\n"
                + "4. Tài liệu số liệu (báo cáo tài chính, bảng thống kê, số liệu kinh doanh, biểu đồ, bảng biểu...)\n"
                + "5. Khác (nếu không thuộc 4 nhóm trên)\n\n"
                + "Chỉ trả về DUY NHẤT một cụm từ là tên nhóm: \"Hóa đơn\", \"Giấy tờ cá nhân\", \"Tài liệu học tập\", \"Tài liệu số liệu\" hoặc \"Khác\". Không thêm bất kỳ ký tự hay lời giải thích nào khác.\n\n"
                + "Văn bản tài liệu:\n"
                + ocrText;
    }

    String buildMindmapPrompt(String ocrText) {
        return "Bạn là chuyên gia phân tích và trực quan hóa sơ đồ tư duy.\n"
                + "Nhiệm vụ của bạn là phân tích toàn bộ văn bản tài liệu sau và cấu trúc hóa thành một Bản đồ tư duy (Mindmap / Concept Tree) theo phân cấp 4 tầng logic chặt chẽ:\n"
                + "- Tầng 1 (Gốc): Chủ đề chính của toàn bộ trang tài liệu.\n"
                + "- Tầng 2 (Nhánh lớn): Các luận điểm hoặc khái niệm cốt lõi.\n"
                + "- Tầng 3 (Nhánh con): Diễn giải chi tiết, công thức hoặc đặc điểm của từng luận điểm.\n"
                + "- Tầng 4: Ví dụ thực tế minh họa hoặc ứng dụng cụ thể.\n\n"
                + "QUY TẮC ĐỊNH DẠNG BẮT BUỘC:\n"
                + "1. Chỉ trả về DUY NHẤT một chuỗi JSON chuẩn hợp lệ (không kèm theo markdown code block, không thêm bất kỳ lời dẫn hay giải thích ngoài JSON).\n"
                + "2. Cấu trúc JSON phải đúng định dạng sau:\n"
                + "{\n"
                + "  \"title\": \"Chủ đề chính\",\n"
                + "  \"branches\": [\n"
                + "    {\n"
                + "      \"name\": \"Tên luận điểm cốt lõi\",\n"
                + "      \"sub_branches\": [\n"
                + "        {\n"
                + "          \"name\": \"Diễn giải chi tiết hoặc công thức\",\n"
                + "          \"examples\": [\n"
                + "            \"Ví dụ thực tế minh họa 1\",\n"
                + "            \"Ví dụ thực tế minh họa 2\"\n"
                + "          ]\n"
                + "        }\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}\n\n"
                + "Nội dung văn bản:\n"
                + ocrText;
    }

    // Trich xuat so lieu tu van ban OCR de ve bieu do
    public void extractChartData(String apiKey, String ocrText, GeminiCallback callback) {
        String prompt = buildChartDataPrompt(ocrText);
        callGeminiApi(apiKey, prompt, callback);
    }

    public void extractChartData(String ocrText, GeminiCallback callback) {
        extractChartData(null, ocrText, callback);
    }

    // Tao 5 cau hoi trac nghiem on tap bang Gemini AI
    public void generateQuiz(String apiKey, String ocrText, GeminiCallback callback) {
        String prompt = buildQuizPrompt(ocrText);
        callGeminiApi(apiKey, prompt, callback);
    }

    public void generateQuiz(String ocrText, GeminiCallback callback) {
        generateQuiz(null, ocrText, callback);
    }

    // Prompt yeu cau Gemini tao 5 cau hoi trac nghiem bam sat tai lieu hoc tap
    String buildQuizPrompt(String ocrText) {
        return "Bạn là chuyên gia khảo thí và trợ lý học tập thông minh.\n"
                + "Nhiệm vụ của bạn là đọc toàn bộ văn bản tài liệu học tập sau đây và tạo ra ĐÚNG 5 câu hỏi trắc nghiệm bám sát nội dung để người dùng ôn tập kiến thức.\n\n"
                + "YÊU CẦU MỖI CÂU HỎI:\n"
                + "1. Câu hỏi bám sát các ý trọng tâm, định nghĩa, công thức hoặc kiến thức cốt lõi trong tài liệu.\n"
                + "2. Đúng 4 phương án lựa chọn (options): mảng gồm 4 chuỗi đáp án.\n"
                + "3. Vị trí đáp án đúng (correct_index): số nguyên từ 0 đến 3 (0 là đáp án đầu tiên A, 1 là B, 2 là C, 3 là D).\n"
                + "4. Lời giải thích (explanation): lời giải thích ngắn gọn, súc tích bằng tiếng Việt vì sao chọn đáp án đó dựa trên tài liệu.\n\n"
                + "QUY TẮC BẮT BUỘC:\n"
                + "1. Chỉ trả về DUY NHẤT một chuỗi JSON hợp lệ (không markdown code block, không thêm lời dẫn).\n"
                + "2. Cấu trúc JSON phải đúng định dạng sau:\n"
                + "{\n"
                + "  \"questions\": [\n"
                + "    {\n"
                + "      \"question\": \"Nội dung câu hỏi?\",\n"
                + "      \"options\": [\n"
                + "        \"Nội dung đáp án A\",\n"
                + "        \"Nội dung đáp án B\",\n"
                + "        \"Nội dung đáp án C\",\n"
                + "        \"Nội dung đáp án D\"\n"
                + "      ],\n"
                + "      \"correct_index\": 0,\n"
                + "      \"explanation\": \"Lời giải thích ngắn gọn tại sao chọn đáp án đó\"\n"
                + "    }\n"
                + "  ]\n"
                + "}\n\n"
                + "Nội dung tài liệu học tập:\n"
                + "---\n"
                + ocrText
                + "\n---";
    }

    // Parse JSON ket qua tra ve thanh danh sach QuizQuestion
    public static java.util.ArrayList<com.example.smartdocs.data.model.QuizQuestion> parseQuizJson(String rawText) {
        java.util.ArrayList<com.example.smartdocs.data.model.QuizQuestion> list = new java.util.ArrayList<>();
        if (rawText == null || rawText.trim().isEmpty()) {
            return list;
        }

        try {
            String chuanHoa = rawText.trim();
            if (chuanHoa.contains("```")) {
                chuanHoa = chuanHoa.replaceAll("```json\\s*", "")
                        .replaceAll("```\\s*", "")
                        .trim();
            }

            int startIdx = chuanHoa.indexOf("{");
            int endIdx = chuanHoa.lastIndexOf("}");
            if (startIdx >= 0 && endIdx > startIdx) {
                chuanHoa = chuanHoa.substring(startIdx, endIdx + 1);
            }

            org.json.JSONObject rootObj = new org.json.JSONObject(chuanHoa);
            org.json.JSONArray questionsArray = rootObj.optJSONArray("questions");
            if (questionsArray == null) {
                return list;
            }

            for (int i = 0; i < questionsArray.length(); i++) {
                org.json.JSONObject qObj = questionsArray.getJSONObject(i);
                String qText = qObj.optString("question", "");
                org.json.JSONArray optsArray = qObj.optJSONArray("options");
                java.util.List<String> options = new java.util.ArrayList<>();
                if (optsArray != null) {
                    for (int j = 0; j < optsArray.length(); j++) {
                        options.add(optsArray.optString(j, ""));
                    }
                }

                int correctIdx = 0;
                if (qObj.has("correct_index")) {
                    Object idxVal = qObj.get("correct_index");
                    if (idxVal instanceof Number) {
                        correctIdx = ((Number) idxVal).intValue();
                    } else if (idxVal instanceof String) {
                        String sVal = ((String) idxVal).trim().toUpperCase();
                        if ("A".equals(sVal) || "0".equals(sVal)) correctIdx = 0;
                        else if ("B".equals(sVal) || "1".equals(sVal)) correctIdx = 1;
                        else if ("C".equals(sVal) || "2".equals(sVal)) correctIdx = 2;
                        else if ("D".equals(sVal) || "3".equals(sVal)) correctIdx = 3;
                    }
                }

                if (correctIdx < 0 || correctIdx >= options.size()) {
                    correctIdx = 0;
                }

                String explanation = qObj.optString("explanation", "");
                if (!qText.isEmpty() && options.size() >= 2) {
                    list.add(new com.example.smartdocs.data.model.QuizQuestion(qText, options, correctIdx, explanation));
                }
            }
        } catch (Exception e) {
            // Parse that bai
        }

        return list;
    }

    // Prompt yeu cau Gemini trich xuat cac cap label-value tu van ban
    String buildChartDataPrompt(String ocrText) {
        return "Bạn là chuyên gia phân tích số liệu và trực quan hóa dữ liệu.\n"
                + "Nhiệm vụ: Đọc toàn bộ văn bản tài liệu dưới đây và trích xuất TẤT CẢ các số liệu có thể vẽ biểu đồ.\n"
                + "Tìm kiếm các con số liên quan đến: doanh thu, chi phí, lợi nhuận, số lượng, phần trăm, tỷ lệ, thống kê, KPI, so sánh các hạng mục...\n\n"
                + "QUY TẮC ĐỊNH DẠNG BẮT BUỘC:\n"
                + "1. Chỉ trả về DUY NHẤT một chuỗi JSON hợp lệ (không markdown code block, không lời dẫn, không giải thích).\n"
                + "2. Cấu trúc JSON phải đúng định dạng:\n"
                + "{\n"
                + "  \"title\": \"Tiêu đề ngắn gọn mô tả nội dung số liệu\",\n"
                + "  \"don_vi\": \"đơn vị tính (VD: triệu đồng, %, người, sản phẩm...)\",\n"
                + "  \"data\": [\n"
                + "    {\"label\": \"Tên mục 1\", \"value\": 123.5},\n"
                + "    {\"label\": \"Tên mục 2\", \"value\": 456.0}\n"
                + "  ]\n"
                + "}\n\n"
                + "3. Mảng \"data\" phải có TỐI THIỂU 2 phần tử và TỐI ĐA 12 phần tử.\n"
                + "4. \"value\" phải là số thực (number), KHÔNG phải chuỗi.\n"
                + "5. Nếu văn bản không chứa bất kỳ số liệu nào có thể vẽ biểu đồ, trả về: {\"title\": \"\", \"don_vi\": \"\", \"data\": []}\n\n"
                + "Nội dung văn bản:\n"
                + ocrText;
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
            return "Kết nối quá chậm!\n"
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
