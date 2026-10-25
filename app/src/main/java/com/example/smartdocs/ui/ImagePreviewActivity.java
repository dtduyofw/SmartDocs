package com.example.smartdocs.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;

import com.example.smartdocs.R;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/*
  ImagePreviewActivity — màn hình xem và điều chỉnh ảnh trước khi OCR.
  Luồng:
    1. Nhận URI ảnh từ MainActivity (qua Intent extra "uri")
    2. Hiển thị ảnh với khung crop 4 góc
    3. Nút Auto Crop → tự động crop vùng trung tâm (loại bỏ viền dư)
    4. Nút Retake → quay về MainActivity để chọn lại ảnh
    5. Nút Confirm → trả URI ảnh về MainActivity để OCR
 */
public class ImagePreviewActivity extends AppCompatActivity {

    // Key truyền URI qua Intent
    public static final String KEY_IMAGE_URI = "uri";
    // Key trả kết quả về MainActivity
    public static final String KEY_RESULT_URI = "result_uri";


    // Views
    ImageView imgPreviewCrop;
    MaterialButton btnAutoCrop, btnRetake, btnConfirm;
    ImageView imgCropIcon;

    // URI ảnh gốc nhận từ Intent
    Uri uriAnh;
    // URI ảnh sau khi crop (có thể khác uriAnh nếu đã crop)
    Uri uriDaCrop;

    // Bitmap đang hiển thị
    Bitmap bitmapHienTai;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_preview);

        // 1. Ánh xạ view
        anhXaView();

        // 2. Lấy URI từ Intent
        uriAnh = getIntent().getParcelableExtra(KEY_IMAGE_URI);
        if (uriAnh == null) {
            Toast.makeText(this, "Lỗi: không có ảnh để xem.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        uriDaCrop = uriAnh; // Mặc định dùng ảnh gốc

        // 3. Hiển thị ảnh
        hienThiAnh(uriAnh);

        // 4. Nút Auto Crop
        btnAutoCrop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                thucHienAutoCrop();
            }
        });

        // 5. Icon crop giữa màn hình (cùng chức năng Auto Crop)
        imgCropIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                thucHienAutoCrop();
            }
        });

        // 6. Nút Retake → quay về, MainActivity sẽ mở lại camera/gallery
        btnRetake.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Trả kết quả CANCELED → MainActivity biết user muốn chụp lại
                setResult(RESULT_CANCELED);
                finish();
            }
        });

        // 7. Nút Confirm → trả URI về MainActivity để OCR
        btnConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                xacNhanAnh();
            }
        });
    }

    void anhXaView() {
        imgPreviewCrop = (ImageView) findViewById(R.id.imgPreviewCrop);
        btnAutoCrop = (MaterialButton) findViewById(R.id.btnAutoCrop);
        btnRetake = (MaterialButton) findViewById(R.id.btnRetake);
        btnConfirm = (MaterialButton) findViewById(R.id.btnConfirm);
        imgCropIcon = (ImageView) findViewById(R.id.imgCropIcon);
    }

    /*
      Hiển thị ảnh từ URI lên ImageView.
      Tự động xoay ảnh đúng chiều dựa vào EXIF metadata.
     */
    void hienThiAnh(Uri uri) {
        try {
            bitmapHienTai = com.example.smartdocs.ocr.BitmapUtils.layBitmapTuUri(this, uri);
            if (bitmapHienTai != null) {
                // Xoay đúng chiều theo EXIF
                bitmapHienTai = com.example.smartdocs.ocr.BitmapUtils.xoayTheoExif(this, uri, bitmapHienTai);
                imgPreviewCrop.setImageBitmap(bitmapHienTai);
            }
        } catch (Exception e) {
            // Fallback: set trực tiếp URI nếu đọc Bitmap lỗi
            imgPreviewCrop.setImageURI(uri);
        }
    }

    /*
      Auto Crop: tự động cắt bỏ 10% viền xung quanh để loại bỏ nền dư.
      Chạy trên background thread để không block UI.
     */
    void thucHienAutoCrop() {
        if (bitmapHienTai == null) return;

        btnAutoCrop.setEnabled(false);
        btnAutoCrop.setText("Đang crop...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Cắt bỏ 8% viền mỗi cạnh
                    int w = bitmapHienTai.getWidth();
                    int h = bitmapHienTai.getHeight();
                    int margin = (int) (Math.min(w, h) * 0.08f);

                    int x = margin;
                    int y = margin;
                    int newW = w - margin * 2;
                    int newH = h - margin * 2;

                    Bitmap bitmapCropped = Bitmap.createBitmap(
                            bitmapHienTai, x, y, newW, newH);

                    // Lưu bitmap crop vào file tạm
                    Uri uriCropped = luuBitmapTamThoi(bitmapCropped);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (uriCropped != null) {
                                bitmapHienTai = bitmapCropped;
                                uriDaCrop = uriCropped;
                                imgPreviewCrop.setImageBitmap(bitmapCropped);
                                Toast.makeText(ImagePreviewActivity.this,
                                        "Tự động cắt thành công!", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(ImagePreviewActivity.this,
                                        "Không thể crop ảnh.", Toast.LENGTH_SHORT).show();
                            }
                            btnAutoCrop.setEnabled(true);
                            btnAutoCrop.setText("Tự động cắt");
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ImagePreviewActivity.this,
                                    "Lỗi crop: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            btnAutoCrop.setEnabled(true);
                            btnAutoCrop.setText("Tự động cắt");
                        }
                    });
                }
            }
        }).start();
    }

    /*
      Lưu Bitmap vào file tạm trong thư mục cache và trả về URI qua FileProvider.
     */
    Uri luuBitmapTamThoi(Bitmap bitmap) {
        try {
            File cacheDir = new File(getCacheDir(), "cropped");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            File file = new File(cacheDir, "crop_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            fos.close();
            return FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
        } catch (Exception e) {
            return null;
        }
    }

    /*
      Xác nhận ảnh → trả URI về MainActivity.
      MainActivity sẽ nhận kết quả qua onActivityResult.
     */
    void xacNhanAnh() {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(KEY_RESULT_URI, uriDaCrop);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Giải phóng Bitmap để tránh memory leak
        if (bitmapHienTai != null && !bitmapHienTai.isRecycled()) {
            bitmapHienTai.recycle();
            bitmapHienTai = null;
        }
    }
}
