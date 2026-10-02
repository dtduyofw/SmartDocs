package com.example.smartdocs.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executors;

/*
  OcrManager — lớp helper đóng gói toàn bộ logic ML Kit Text Recognition v2.
  Luồng hoạt động:
    Uri ảnh → InputImage → TextRecognizer → phân tích → callback kết quả
    Thiết kế Singleton để tái sử dụng TextRecognizer (tránh khởi tạo lại nhiều lần).
    TextRecognizer cần được close() khi không còn dùng nữa để giải phóng tài nguyên.
 */
public class OcrManager {
    // Singleton
    static OcrManager instance;
    // TextRecognizer dùng model Latin (tiếng Anh + ký tự Latin)
    // Hỗ trợ đa ngôn ngữ Latin bao gồm tiếng Việt
    TextRecognizer recognizer;

    private OcrManager() {
        // Khởi tạo với tùy chọn mặc định
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    public static OcrManager getInstance() {
        if (instance == null) {
            synchronized (OcrManager.class) {
                if (instance == null) {
                    instance = new OcrManager();
                }
            }
        }
        return instance;
    }
    // Callback interface

    //Callback trả về kết quả OCR.
    public interface OcrCallback {
        /*
          Gọi khi nhận diện thành công.
          @param fullText  Toàn bộ văn bản gộp lại (dễ dùng nhất)
          @param result    Object Text đầy đủ (nếu cần truy cập Block/Line/Element)
         */
        void onSuccess(String fullText, Text result);

        /*
          Gọi khi xảy ra lỗi trong quá trình nhận diện.
          @param errorMessage  Mô tả lỗi
         */
        void onError(String errorMessage);
    }

    // Core method: nhận diện từ URI

    // Kích thước tối đa chuẩn hóa cạnh dài nhất cho OCR (1400px - 2048px theo chuẩn Google ML Kit)
    private static final int MAX_OCR_DIMENSION = 2048;
    /**
      Nhận diện văn bản từ URI ảnh (Camera hoặc Gallery).
      Đã tích hợp tự động:
      - Phương pháp 1: Tiền xử lý xoay EXIF + chuyển Grayscale + tăng tương phản (Contrast Boost).
      - Phương pháp 2: Chuẩn hóa độ phân giải tối ưu (scale cạnh dài về tối đa 2048px, giải mã an toàn inSampleSize).
      @param context  Context để đọc ảnh từ URI
      @param imageUri URI của ảnh cần nhận diện
      @param callback Callback trả về kết quả (chạy trên main thread)
     */
    public void recognizeFromUri(Context context, Uri imageUri, OcrCallback callback) {
        // Chạy tiền xử lý trên background thread để không block main UI thread
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                InputImage image;
                Bitmap processedBitmap = null;

                try {
                    // Áp dụng Phương pháp 1 & 2: Tiền xử lý ảnh và chuẩn hóa kích thước
                    processedBitmap = tienXuLyAnh(context, imageUri);
                    if (processedBitmap != null) {
                        image = InputImage.fromBitmap(processedBitmap, 0);
                    } else {
                        // Fallback đọc trực tiếp nếu tiền xử lý trả về null
                        image = InputImage.fromFilePath(context, imageUri);
                    }
                } catch (Exception e) {
                    try {
                        image = InputImage.fromFilePath(context, imageUri);
                    } catch (IOException ioException) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                if (callback != null) {
                                    callback.onError("Không thể đọc ảnh: " + ioException.getMessage());
                                }
                            }
                        });
                        return;
                    }
                }

                final Bitmap bitmapCanRecycle = processedBitmap;

                // Gửi ảnh đã tối ưu cho ML Kit xử lý (bất đồng bộ)
                recognizer.process(image)
                        .addOnSuccessListener(visionText -> {
                            // Giải phóng bộ nhớ bitmap tạm
                            if (bitmapCanRecycle != null && !bitmapCanRecycle.isRecycled()) {
                                bitmapCanRecycle.recycle();
                            }

                            String fullText = visionText.getText();
                            if (fullText == null || fullText.trim().isEmpty()) {
                                if (callback != null) {
                                    callback.onError("Không tìm thấy văn bản trong ảnh.\nHãy thử chụp lại với ánh sáng tốt hơn.");
                                }
                                return;
                            }

                            if (callback != null) {
                                callback.onSuccess(fullText, visionText);
                            }
                        })
                        .addOnFailureListener(e -> {
                            if (bitmapCanRecycle != null && !bitmapCanRecycle.isRecycled()) {
                                bitmapCanRecycle.recycle();
                            }
                            if (callback != null) {
                                callback.onError("ML Kit lỗi: " + e.getMessage());
                            }
                        });
            }
        });
    }

    /*
      Phương pháp 1 & 2: Tiền xử lý ảnh cho OCR.
      - Decode ảnh an toàn bằng inSampleSize (tránh tràn RAM trên camera 48MP/108MP).
      - Xoay ảnh đúng chiều theo dữ liệu EXIF.
      - Scale cạnh dài về kích thước lý tưởng (tối đa 2048px).
      - Tăng độ tương phản & Grayscale để nét chữ nổi bật hoàn toàn trên nền giấy.
     */
    private Bitmap tienXuLyAnh(Context context, Uri uri) {
        try {
            // 1. Đọc kích thước gốc an toàn
            BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
            boundsOptions.inJustDecodeBounds = true;
            InputStream isBounds = context.getContentResolver().openInputStream(uri);
            if (isBounds == null) return null;
            BitmapFactory.decodeStream(isBounds, null, boundsOptions);
            isBounds.close();

            int origW = boundsOptions.outWidth;
            int origH = boundsOptions.outHeight;
            if (origW <= 0 || origH <= 0) return null;

            // 2. Phương pháp 2: Chuẩn hóa kích thước qua inSampleSize (tránh tràn RAM)
            BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
            decodeOptions.inSampleSize = 1;
            int maxDimension = Math.max(origW, origH);
            while (maxDimension / (decodeOptions.inSampleSize * 2) >= MAX_OCR_DIMENSION) {
                decodeOptions.inSampleSize *= 2;
            }

            InputStream isDecode = context.getContentResolver().openInputStream(uri);
            if (isDecode == null) return null;
            Bitmap decoded = BitmapFactory.decodeStream(isDecode, null, decodeOptions);
            isDecode.close();
            if (decoded == null) return null;

            // 3. Phương pháp 1 (Phần a): Đọc EXIF và xoay ảnh đúng chiều thẳng đứng
            Bitmap rotated = BitmapUtils.xoayTheoExif(context, uri, decoded);

            // 4. Phương pháp 2: Scale chính xác về kích thước tối đa 2048px nếu vẫn vượt ngưỡng
            Bitmap scaled = scaleAnhChuan(rotated);
            if (scaled != rotated && rotated != decoded) {
                rotated.recycle();
            }

            // 5. Phương pháp 1 (Phần b): Chuyển sang Grayscale và tăng độ tương phản (Contrast Boost)
            Bitmap preprocessed = tangTuongPhanVaGrayscale(scaled);
            if (preprocessed != scaled) {
                scaled.recycle();
            }

            return preprocessed;
        } catch (Exception e) {
            return null;
        }
    }

    //Scale kích thước ảnh về cạnh dài tối đa 2048px (giữ nguyên tỷ lệ khung hình).
    private Bitmap scaleAnhChuan(Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int max = Math.max(w, h);

        if (max <= MAX_OCR_DIMENSION) {
            return src;
        }

        float ratio = (float) MAX_OCR_DIMENSION / max;
        int targetW = Math.round(w * ratio);
        int targetH = Math.round(h * ratio);

        return Bitmap.createScaledBitmap(src, targetW, targetH, true);
    }

    /*
      Áp dụng bộ lọc Grayscale + Tăng tương phản bằng ColorMatrix:
      - Độ tương phản 1.35x làm nét chữ nổi bật, đậm rõ.
      - Giảm nhẹ độ sáng (-15) giúp nét chữ thanh mảnh không bị mất nét.
     */
    private Bitmap tangTuongPhanVaGrayscale(Bitmap src) {
        try {
            Bitmap bmp = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

            // Chuyển sang Grayscale đơn sắc
            ColorMatrix cmGray = new ColorMatrix();
            cmGray.setSaturation(0);

            // Tăng tương phản (Contrast Boost): nét chữ đậm hẳn, nền giấy sáng trắng
            float contrast = 1.35f;
            float brightness = -15f;
            ColorMatrix cmContrast = new ColorMatrix(new float[]{
                    contrast, 0, 0, 0, brightness,
                    0, contrast, 0, 0, brightness,
                    0, 0, contrast, 0, brightness,
                    0, 0, 0, 1, 0
            });

            cmContrast.preConcat(cmGray);
            paint.setColorFilter(new ColorMatrixColorFilter(cmContrast));
            canvas.drawBitmap(src, 0, 0, paint);

            return bmp;
        } catch (Exception e) {
            return src;
        }
    }

    // Utility: phân tích cấu trúc chi tiết
    /*
      Lấy danh sách từng dòng văn bản từ kết quả OCR.
      ML Kit phân tích theo cấu trúc: Text → Block → Line → Element (từ).
      @param result Object Text từ ML Kit
      @return Văn bản đã được định dạng lại, mỗi dòng trên một hàng
     */
    public String extractFormattedText(Text result) {
        if (result == null) return "";

        StringBuilder sb = new StringBuilder();

        for (Text.TextBlock block : result.getTextBlocks()) {
            // Mỗi Block là một đoạn văn bản tách biệt (paragraph)
            for (Text.Line line : block.getLines()) {
                sb.append(line.getText()).append("\n");
            }
            sb.append("\n"); // Thêm dòng trắng giữa các block
        }

        return sb.toString().trim();
    }

    // Giải phóng tài nguyên
    /**
     * Đóng TextRecognizer khi ứng dụng không còn cần OCR nữa.
     * Gọi trong onDestroy() của Application hoặc khi chắc chắn không dùng lại.
     */
    public void close() {
        if (recognizer != null) {
            recognizer.close();
        }
        instance = null; // Reset singleton để có thể tạo lại nếu cần
    }
}
