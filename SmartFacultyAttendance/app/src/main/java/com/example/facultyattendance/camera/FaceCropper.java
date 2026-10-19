package com.example.facultyattendance.camera;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;

import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceLandmark;

/**
 * Aligns and crops one detected face into the square input the recognition model expects.
 * Alignment = rotate so the eyes are level, centre on the face box, scale to outSize.
 * One Canvas pass does rotate + crop + resize, so there is no intermediate large bitmap.
 *
 * The crop margin (CROP_SCALE) must roughly match how the model was trained; tune with your model.
 */
final class FaceCropper {
    static final float CROP_SCALE = 1.25f;

    private FaceCropper() { }

    static Bitmap alignedCrop(Bitmap upright, Face face, int outSize) {
        Rect box = face.getBoundingBox();
        float cx = box.exactCenterX();
        float cy = box.exactCenterY();
        float side = Math.max(box.width(), box.height()) * CROP_SCALE;

        float angleDeg = 0f;
        FaceLandmark a = face.getLandmark(FaceLandmark.LEFT_EYE);
        FaceLandmark b = face.getLandmark(FaceLandmark.RIGHT_EYE);
        if (a != null && b != null) {
            PointF p1 = a.getPosition();
            PointF p2 = b.getPosition();
            PointF left = p1.x <= p2.x ? p1 : p2;     // image-left eye first, regardless of naming
            PointF right = p1.x <= p2.x ? p2 : p1;
            angleDeg = (float) Math.toDegrees(Math.atan2(right.y - left.y, right.x - left.x));
        }

        float scale = outSize / side;
        Matrix m = new Matrix();
        m.postTranslate(-cx, -cy);
        m.postRotate(-angleDeg);
        m.postScale(scale, scale);
        m.postTranslate(outSize / 2f, outSize / 2f);

        Bitmap out = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(upright, m, paint);
        return out;
    }

    /** Mean luminance 0..255 of the crop, used to reject frames that are too dark or blown out. */
    static float meanLuminance(Bitmap crop) {
        int w = crop.getWidth(), h = crop.getHeight();
        int[] px = new int[w * h];
        crop.getPixels(px, 0, w, 0, 0, w, h);
        double sum = 0;
        for (int p : px) {
            sum += 0.299 * ((p >> 16) & 0xFF) + 0.587 * ((p >> 8) & 0xFF) + 0.114 * (p & 0xFF);
        }
        return (float) (sum / px.length);
    }

    /** Row-major RGB floats, normalised with the embedder's mean/std. */
    static float[] toNormalizedRgb(Bitmap crop, float mean, float std) {
        int w = crop.getWidth(), h = crop.getHeight();
        int[] px = new int[w * h];
        crop.getPixels(px, 0, w, 0, 0, w, h);
        float[] out = new float[w * h * 3];
        int k = 0;
        for (int p : px) {
            out[k++] = (((p >> 16) & 0xFF) - mean) / std;
            out[k++] = (((p >> 8) & 0xFF) - mean) / std;
            out[k++] = ((p & 0xFF) - mean) / std;
        }
        return out;
    }
}
