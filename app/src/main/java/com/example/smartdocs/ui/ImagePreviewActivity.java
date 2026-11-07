package com.example.smartdocs.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.example.smartdocs.R;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;

/*
  ImagePreviewActivity - man hinh xem va dieu chinh vung cat anh tuong tac.
  Cho phep nguoi dung:
    1. Keo 4 goc hoac 4 canh de cat anh chinh xac
    2. Bam Nut Tu dong cat de can khung vao 8% vien
    3. Bam Nut Toan anh de mo rong khung crop phu toan bo
    4. Xac nhan de luu bitmap vung da cat va chuyen tiep OCR
 */
public class ImagePreviewActivity extends AppCompatActivity {

    public static final String KEY_IMAGE_URI = "uri";
    public static final String KEY_RESULT_URI = "result_uri";

    // Views
    ImageView imgPreviewCrop;
    CropOverlayView cropOverlayView;
    MaterialButton btnAutoCrop, btnResetCrop, btnRetake, btnConfirm;

    // URI anh goc nhan tu Intent
    Uri uriAnh;
    // URI anh sau khi cat
    Uri uriDaCrop;

    // Bitmap goc dang hien thi
    Bitmap bitmapHienTai;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_preview);

        // 1. Anh xa views
        anhXaView();

        // 2. Lay URI tu Intent
        uriAnh = getIntent().getParcelableExtra(KEY_IMAGE_URI);
        if (uriAnh == null) {
            Toast.makeText(this, "Lỗi: không có ảnh để xem.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        uriDaCrop = uriAnh;

        // 3. Hien thi anh va khoi tao khung crop
        hienThiAnh(uriAnh);

        // 4. Gan su kien cac nut bam
        ganSuKien();
    }

    void anhXaView() {
        imgPreviewCrop = (ImageView) findViewById(R.id.imgPreviewCrop);
        cropOverlayView = (CropOverlayView) findViewById(R.id.cropOverlayView);
        btnAutoCrop = (MaterialButton) findViewById(R.id.btnAutoCrop);
        btnResetCrop = (MaterialButton) findViewById(R.id.btnResetCrop);
        btnRetake = (MaterialButton) findViewById(R.id.btnRetake);
        btnConfirm = (MaterialButton) findViewById(R.id.btnConfirm);
    }

    void ganSuKien() {
        // Nut Tu dong cat: co khung vao 8% vien tai lieu
        if (btnAutoCrop != null) {
            btnAutoCrop.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (cropOverlayView != null) {
                        cropOverlayView.thucHienAutoCrop();
                        Toast.makeText(ImagePreviewActivity.this, "Đã căn chỉnh vùng cắt tự động", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        // Nut Toan anh: dat lai khung crop phu toan bo anh
        if (btnResetCrop != null) {
            btnResetCrop.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (cropOverlayView != null) {
                        cropOverlayView.datLaiToanBoAnh();
                        Toast.makeText(ImagePreviewActivity.this, "Đã chọn toàn bộ ảnh", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        // Nut Chup lai: quay ve de chup/chon lai anh
        if (btnRetake != null) {
            btnRetake.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setResult(RESULT_CANCELED);
                    finish();
                }
            });
        }

        // Nut Xac nhan: cat anh theo dung khung da keo va tra ket qua
        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    xacNhanVaCatAnh();
                }
            });
        }
    }

    /*
      Hien thi anh tu URI va truyen sang CropOverlayView de tinh toan khung cat
     */
    void hienThiAnh(Uri uri) {
        try {
            bitmapHienTai = com.example.smartdocs.ocr.BitmapUtils.layBitmapTuUri(this, uri);
            if (bitmapHienTai != null) {
                // Xoay dung chieu theo EXIF
                bitmapHienTai = com.example.smartdocs.ocr.BitmapUtils.xoayTheoExif(this, uri, bitmapHienTai);
                imgPreviewCrop.setImageBitmap(bitmapHienTai);

                // Thiet lap vung anh cho overlay crop tuong tac
                if (cropOverlayView != null) {
                    cropOverlayView.thietLapAnh(bitmapHienTai, imgPreviewCrop);
                }
            }
        } catch (Exception e) {
            imgPreviewCrop.setImageURI(uri);
        }
    }

    /*
      Cat anh theo vung nguoi dung da keo va tra ve MainActivity
     */
    void xacNhanVaCatAnh() {
        if (bitmapHienTai == null) {
            traKetQua(uriDaCrop);
            return;
        }

        btnConfirm.setEnabled(false);
        btnConfirm.setText("Đang xử lý...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap bitmapCropped = null;
                if (cropOverlayView != null) {
                    bitmapCropped = cropOverlayView.layBitmapDaCrop(bitmapHienTai);
                }

                if (bitmapCropped != null) {
                    Uri uriCropped = luuBitmapTamThoi(bitmapCropped);
                    if (uriCropped != null) {
                        uriDaCrop = uriCropped;
                    }
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btnConfirm.setEnabled(true);
                        btnConfirm.setText("Cắt & Tiếp tục");
                        traKetQua(uriDaCrop);
                    }
                });
            }
        }).start();
    }

    void traKetQua(Uri resultUri) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(KEY_RESULT_URI, resultUri);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    /*
      Luu Bitmap vao file cache tam thoi va tao URI qua FileProvider
     */
    Uri luuBitmapTamThoi(Bitmap bitmap) {
        try {
            File cacheDir = new File(getCacheDir(), "cropped");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            File file = new File(cacheDir, "crop_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos);
            fos.flush();
            fos.close();
            return FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bitmapHienTai != null && !bitmapHienTai.isRecycled()) {
            bitmapHienTai.recycle();
            bitmapHienTai = null;
        }
    }
}