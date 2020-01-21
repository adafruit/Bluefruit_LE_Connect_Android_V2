package com.adafruit.bluefruit.le.connect.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;

import magick.ImageInfo;
import magick.MagickException;
import magick.MagickImage;
import magick.QuantizeInfo;
import magick.util.MagickBitmap;

public class ImageUtils {
    // Log
    private final static String TAG = ImageUtils.class.getSimpleName();

    public static Bitmap applyEInkModeToImage(@NonNull Context context, @NonNull Bitmap bitmap) {

        Bitmap result = null;
        ImageInfo imageInfo = null;
        try {

            // Load bitmap
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos);
            ImageInfo info = new ImageInfo();
            info.setMagick("png");
            MagickImage image = new MagickImage(info, bos.toByteArray());

            // Load palette
            MagickImage paletteImage = loadEInkPalette(context);

            QuantizeInfo quantizeInfo = new QuantizeInfo();
            quantizeInfo.setDither(1);

            image.remapImage(quantizeInfo, paletteImage);

            result = MagickBitmap.ToBitmap(image);
        } catch (MagickException e) {
            e.printStackTrace();
        }


        return result;
    }

    public static @Nullable
    MagickImage loadEInkPalette(@NonNull Context context) {
        Bitmap bitmap = FileHelper.getBitmapFromAsset(context, "imagetransfer/eink3color.png");
        if (bitmap == null) return null;

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos);

        MagickImage magickImage = null;
        try {
            ImageInfo info = new ImageInfo();
            info.setMagick("png");
            magickImage = new MagickImage(info, bos.toByteArray());
        } catch (MagickException e) {
            Log.e(TAG, "loadEInkPalette error: " + e);
        }

        return magickImage;
    }


    @SuppressWarnings("SameParameterValue")
    public static Bitmap scaleAndRotateImage(@NonNull Bitmap image, Size resolution, float rotationDegress, int backgroundColor) {
        // Calculate resolution for fitted image
        final float widthRatio = resolution.getWidth() / (float) image.getWidth();
        final float heightRatio = resolution.getHeight() / (float) image.getHeight();

        Size fitResolution;
        if (heightRatio < widthRatio) {
            float width = Math.round((resolution.getHeight() / (float) image.getHeight()) * image.getWidth());
            fitResolution = new Size((int) (width), resolution.getHeight());
        } else {
            float height = Math.round((resolution.getWidth() / (float) image.getWidth()) * image.getHeight());
            fitResolution = new Size(resolution.getWidth(), (int) (height));
        }

        Bitmap fitImage = getResizedBitmap(image, fitResolution.getWidth(), fitResolution.getHeight(), rotationDegress);

        final int x = (resolution.getWidth() - fitImage.getWidth()) / 2;
        final int y = (resolution.getHeight() - fitImage.getHeight()) / 2;

        Bitmap newImage = Bitmap.createBitmap(resolution.getWidth(), resolution.getHeight(), fitImage.getConfig());
        Canvas canvas = new Canvas(newImage);
        canvas.drawColor(backgroundColor);
        canvas.drawBitmap(fitImage, x, y, null);
        return newImage;
    }

    private static Bitmap getResizedBitmap(Bitmap bitmap, int newWidth, int newHeight, float rotationDegress) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;

        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        matrix.postRotate(rotationDegress);

        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, false);
    }

}
