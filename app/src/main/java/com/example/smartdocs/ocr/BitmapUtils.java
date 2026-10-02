package com.example.smartdocs.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.exifinterface.media.ExifInterface;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;

/*
  BitmapUtils — tiện ích xử lý ảnh dùng chung toàn ứng dụng.
 */
public class BitmapUtils {

    //Đọc Bitmap từ URI với kích thước giảm xuống để tránh tràn RAM (OOM).
    public static Bitmap layBitmapTuUri(Context context, Uri uri, int maxSize) throws IOException {
        ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r");
        if (pfd == null) return null;
        FileDescriptor fd = pfd.getFileDescriptor();

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFileDescriptor(fd, null, options);

        int sampleSize = 1;
        while (options.outWidth / sampleSize > maxSize
                || options.outHeight / sampleSize > maxSize) {
            sampleSize *= 2;
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = sampleSize;

        Bitmap bitmap = BitmapFactory.decodeFileDescriptor(fd, null, options);
        pfd.close();
        return bitmap;
    }

    public static Bitmap layBitmapTuUri(Context context, Uri uri) throws IOException {
        return layBitmapTuUri(context, uri, 1920);
    }

     //Xoay ảnh đúng chiều theo thông tin EXIF orientation.
    public static Bitmap xoayTheoExif(Context context, Uri uri, Bitmap bitmap) {
        if (bitmap == null) return null;
        try {
            InputStream is = context.getContentResolver().openInputStream(uri);
            if (is == null) return bitmap;
            ExifInterface exif = new ExifInterface(is);
            is.close();

            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);

            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270);
                    break;
                default:
                    return bitmap;
            }

            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            return rotated;
        } catch (Exception e) {
            return bitmap;
        }
    }
}
