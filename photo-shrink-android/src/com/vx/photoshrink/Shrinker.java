package com.vx.photoshrink;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Resizes a photo to the requested resolution and compresses it under a size
 * budget at the highest quality that fits (same algorithm as photo_shrink.py):
 *   1. Fix EXIF orientation, resize to the target resolution.
 *   2. Binary-search the encoder quality for the highest value under budget.
 *   3. If minimum quality is still too big, shrink resolution 10% per step
 *      unless strict resolution is on.
 */
final class Shrinker {

    static final int MIN_DIMENSION = 16;
    static final float DOWNSCALE_STEP = 0.90f;
    private static final int MAX_DECODE_RETRIES = 4;

    static final class Options {
        int width;              // 0 = not set
        int height;             // 0 = not set
        boolean exact;
        boolean allowUpscale;
        boolean strictResolution;
        double maxKb = 100;
        boolean webp;
        int minQuality = 40;
        int maxQuality = 95;
        int background = Color.WHITE;
    }

    static final class Result {
        final byte[] data;
        final int width;
        final int height;
        final int quality;
        final boolean webp;
        final int originalWidth;
        final int originalHeight;
        final int requestedWidth;
        final int requestedHeight;

        Result(byte[] data, int width, int height, int quality, boolean webp,
               int originalWidth, int originalHeight, int requestedWidth, int requestedHeight) {
            this.data = data;
            this.width = width;
            this.height = height;
            this.quality = quality;
            this.webp = webp;
            this.originalWidth = originalWidth;
            this.originalHeight = originalHeight;
            this.requestedWidth = requestedWidth;
            this.requestedHeight = requestedHeight;
        }

        boolean downscaled() {
            return width != requestedWidth || height != requestedHeight;
        }

        String extension() {
            return webp ? ".webp" : ".jpg";
        }

        String mimeType() {
            return webp ? "image/webp" : "image/jpeg";
        }
    }

    interface Progress {
        void onProgress(String message);
    }

    static final class ShrinkException extends Exception {
        ShrinkException(String message) {
            super(message);
        }
    }

    private Shrinker() {
    }

    static int[] targetSize(int w, int h, Options o) {
        int nw;
        int nh;
        if (o.width > 0 && o.height > 0) {
            if (o.exact) {
                nw = o.width;
                nh = o.height;
            } else {
                double r = Math.min((double) o.width / w, (double) o.height / h);
                nw = (int) Math.round(w * r);
                nh = (int) Math.round(h * r);
            }
        } else if (o.width > 0) {
            nw = o.width;
            nh = (int) Math.round((double) h * o.width / w);
        } else if (o.height > 0) {
            nw = (int) Math.round((double) w * o.height / h);
            nh = o.height;
        } else {
            nw = w;
            nh = h;
        }
        if (!o.allowUpscale && !o.exact && (nw > w || nh > h)) {
            double r = Math.min((double) w / nw, (double) h / nh);
            nw = (int) Math.round(nw * r);
            nh = (int) Math.round(nh * r);
        }
        return new int[]{Math.max(1, nw), Math.max(1, nh)};
    }

    /** Returns {width, height} after EXIF orientation, or null if unreadable. */
    static int[] orientedBounds(File source) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(source.getPath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        boolean swap = swapsAxes(readOrientation(source));
        return swap ? new int[]{bounds.outHeight, bounds.outWidth}
                : new int[]{bounds.outWidth, bounds.outHeight};
    }

    static Result run(File source, Options o, Progress p) throws ShrinkException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(source.getPath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new ShrinkException("Cannot read this image. Unsupported or corrupted file.");
        }

        int orientation = readOrientation(source);
        boolean swap = swapsAxes(orientation);
        int ow = swap ? bounds.outHeight : bounds.outWidth;
        int oh = swap ? bounds.outWidth : bounds.outHeight;
        int[] target = targetSize(ow, oh, o);

        p.onProgress("Decoding " + ow + "×" + oh + "…");
        int reqW = swap ? target[1] : target[0];
        int reqH = swap ? target[0] : target[1];
        Bitmap base = applyOrientation(decodeAtLeast(source, bounds.outWidth, bounds.outHeight, reqW, reqH), orientation);
        try {
            return fit(base, target, ow, oh, o, p);
        } finally {
            base.recycle();
        }
    }

    private static Result fit(Bitmap base, int[] target, int ow, int oh, Options o, Progress p)
            throws ShrinkException {
        int budget = (int) (o.maxKb * 1024);
        int w = target[0];
        int h = target[1];
        while (w >= MIN_DIMENSION && h >= MIN_DIMENSION) {
            p.onProgress(String.format(Locale.US, "Trying %d×%d…", w, h));
            Bitmap scaled = scale(base, w, h);
            Bitmap prepared = o.webp ? scaled : flatten(scaled, o.background);
            try {
                int[] quality = new int[1];
                byte[] data = bestQuality(prepared, o, budget, quality);
                if (data != null) {
                    return new Result(data, w, h, quality[0], o.webp, ow, oh, target[0], target[1]);
                }
            } finally {
                if (prepared != scaled) {
                    prepared.recycle();
                }
                if (scaled != base) {
                    scaled.recycle();
                }
            }
            if (o.strictResolution) {
                throw new ShrinkException(String.format(Locale.US,
                        "%d×%d does not fit under %s KB even at quality %d.\n"
                                + "Increase the KB limit, lower the min quality, or turn off strict resolution.",
                        w, h, formatKb(o.maxKb), o.minQuality));
            }
            w = (int) (w * DOWNSCALE_STEP);
            h = (int) (h * DOWNSCALE_STEP);
        }
        throw new ShrinkException("Cannot fit under " + formatKb(o.maxKb) + " KB with these settings.");
    }

    /** Binary search for the highest quality whose output is within budget. */
    private static byte[] bestQuality(Bitmap bmp, Options o, int budget, int[] qualityOut) {
        Bitmap.CompressFormat fmt = o.webp ? Bitmap.CompressFormat.WEBP : Bitmap.CompressFormat.JPEG;
        int lo = o.minQuality;
        int hi = o.maxQuality;
        byte[] best = null;
        ByteArrayOutputStream buf = new ByteArrayOutputStream(Math.max(budget, 1024));
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            buf.reset();
            if (!bmp.compress(fmt, mid, buf)) {
                hi = mid - 1;
                continue;
            }
            if (buf.size() <= budget) {
                best = buf.toByteArray();
                qualityOut[0] = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return best;
    }

    private static Bitmap decodeAtLeast(File source, int srcW, int srcH, int reqW, int reqH)
            throws ShrinkException {
        int sample = 1;
        while (srcW / (sample * 2) >= reqW && srcH / (sample * 2) >= reqH) {
            sample *= 2;
        }
        for (int attempt = 0; attempt < MAX_DECODE_RETRIES; attempt++) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try {
                Bitmap bmp = BitmapFactory.decodeFile(source.getPath(), opts);
                if (bmp == null) {
                    throw new ShrinkException("Cannot decode this image.");
                }
                return bmp;
            } catch (OutOfMemoryError e) {
                sample *= 2;
            }
        }
        throw new ShrinkException("Image is too large for available memory.");
    }

    /** Multi-step halving before the final resize keeps downscaled images sharp and alias-free. */
    private static Bitmap scale(Bitmap src, int w, int h) {
        if (src.getWidth() == w && src.getHeight() == h) {
            return src;
        }
        Bitmap cur = src;
        while (cur.getWidth() / 2 >= w && cur.getHeight() / 2 >= h) {
            Bitmap next = Bitmap.createScaledBitmap(cur, cur.getWidth() / 2, cur.getHeight() / 2, true);
            if (cur != src) {
                cur.recycle();
            }
            cur = next;
        }
        Bitmap out = Bitmap.createScaledBitmap(cur, w, h, true);
        if (cur != src && cur != out) {
            cur.recycle();
        }
        return out;
    }

    /** JPEG has no alpha; composite transparent pixels onto the background color. */
    private static Bitmap flatten(Bitmap src, int background) {
        if (!src.hasAlpha()) {
            return src;
        }
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(background);
        canvas.drawBitmap(src, 0, 0, null);
        out.setHasAlpha(false);
        return out;
    }

    private static int readOrientation(File source) {
        try {
            return new ExifInterface(source.getPath())
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException | RuntimeException e) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private static boolean swapsAxes(int orientation) {
        return orientation == ExifInterface.ORIENTATION_ROTATE_90
                || orientation == ExifInterface.ORIENTATION_ROTATE_270
                || orientation == ExifInterface.ORIENTATION_TRANSPOSE
                || orientation == ExifInterface.ORIENTATION_TRANSVERSE;
    }

    private static Bitmap applyOrientation(Bitmap bmp, int orientation) {
        Matrix m = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                m.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                m.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                m.setScale(1, -1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                m.setRotate(90);
                m.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                m.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                m.setRotate(-90);
                m.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                m.setRotate(-90);
                break;
            default:
                return bmp;
        }
        Bitmap out = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        if (out != bmp) {
            bmp.recycle();
        }
        return out;
    }

    static String formatKb(double kb) {
        return kb == Math.floor(kb) ? String.valueOf((long) kb) : String.format(Locale.US, "%.1f", kb);
    }
}
