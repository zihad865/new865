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
    static final int MAX_DIMENSION = 20000;
    static final int MIN_QUALITY = 40;
    static final int MAX_QUALITY = 95;
    private static final float DOWNSCALE_STEP = 0.90f;
    private static final int MAX_OOM_RETRIES = 4;
    private static final int INITIAL_BUFFER = 256 * 1024;

    static final class Options {
        int width;              // 0 = not set
        int height;             // 0 = not set
        boolean keepAspect = true;
        boolean strictResolution;
        double maxKb = 100;
        boolean webp;
        int background = Color.WHITE;
    }

    static final class Result {
        final byte[] data;
        final int width;
        final int height;
        final int quality;
        final boolean webp;
        final int requestedWidth;
        final int requestedHeight;

        Result(byte[] data, int width, int height, int quality, boolean webp,
               int requestedWidth, int requestedHeight) {
            this.data = data;
            this.width = width;
            this.height = height;
            this.quality = quality;
            this.webp = webp;
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
        boolean exact = o.width > 0 && o.height > 0 && !o.keepAspect;
        if (o.width > 0 && o.height > 0) {
            if (exact) {
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
        // Never enlarge unless the user asked for an exact (stretched) size.
        if (!exact && (nw > w || nh > h)) {
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
        return swapsAxes(readOrientation(source))
                ? new int[]{bounds.outHeight, bounds.outWidth}
                : new int[]{bounds.outWidth, bounds.outHeight};
    }

    static Result run(File source, Options o, Progress p) throws ShrinkException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(source.getPath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new ShrinkException("This file can't be read as an image.");
        }

        int orientation = readOrientation(source);
        boolean swap = swapsAxes(orientation);
        int ow = swap ? bounds.outHeight : bounds.outWidth;
        int oh = swap ? bounds.outWidth : bounds.outHeight;
        int[] target = targetSize(ow, oh, o);
        int reqW = swap ? target[1] : target[0];
        int reqH = swap ? target[0] : target[1];

        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= reqW && bounds.outHeight / (sample * 2) >= reqH) {
            sample *= 2;
        }

        // Any step (decode, rotate, scale, encode) can run out of memory on huge photos;
        // retry the whole pipeline from a smaller decode instead of failing.
        for (int attempt = 0; attempt <= MAX_OOM_RETRIES; attempt++) {
            Bitmap base = null;
            try {
                p.onProgress("Preparing photo…");
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sample;
                opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                Bitmap decoded = BitmapFactory.decodeFile(source.getPath(), opts);
                if (decoded == null) {
                    throw new ShrinkException("This file can't be read as an image.");
                }
                base = applyOrientation(decoded, orientation);
                return fit(base, target, o, p);
            } catch (OutOfMemoryError e) {
                sample *= 2;
            } finally {
                if (base != null) {
                    base.recycle();
                }
            }
        }
        throw new ShrinkException("This photo is too large for the memory on this device.");
    }

    private static Result fit(Bitmap base, int[] target, Options o, Progress p) throws ShrinkException {
        int budget = (int) Math.min(Integer.MAX_VALUE, o.maxKb * 1024);
        int w = target[0];
        int h = target[1];
        while (true) {
            p.onProgress(String.format(Locale.US, "Compressing %d × %d…", w, h));
            Bitmap scaled = scale(base, w, h);
            Bitmap prepared = o.webp ? scaled : flatten(scaled, o.background);
            try {
                int[] quality = new int[1];
                byte[] data = bestQuality(prepared, o.webp, budget, quality);
                if (data != null) {
                    return new Result(data, w, h, quality[0], o.webp, target[0], target[1]);
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
                        "%d × %d can't fit in %s KB without reducing pixels.\n"
                                + "Raise the size limit or turn off \"Never Reduce Pixels\".",
                        w, h, formatKb(o.maxKb)));
            }
            int nw = (int) (w * DOWNSCALE_STEP);
            int nh = (int) (h * DOWNSCALE_STEP);
            if (nw < MIN_DIMENSION || nh < MIN_DIMENSION) {
                throw new ShrinkException("Can't fit this photo in " + formatKb(o.maxKb) + " KB. Raise the size limit.");
            }
            w = nw;
            h = nh;
        }
    }

    /** Binary search for the highest quality whose output is within budget. */
    private static byte[] bestQuality(Bitmap bmp, boolean webp, int budget, int[] qualityOut) {
        Bitmap.CompressFormat fmt = webp ? Bitmap.CompressFormat.WEBP : Bitmap.CompressFormat.JPEG;
        int lo = MIN_QUALITY;
        int hi = MAX_QUALITY;
        byte[] best = null;
        ByteArrayOutputStream buf = new ByteArrayOutputStream(Math.min(budget, INITIAL_BUFFER));
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

    /** Returns the oriented bitmap; recycles the input if a new one was created. */
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
        try {
            Bitmap out = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            if (out != bmp) {
                bmp.recycle();
            }
            return out;
        } catch (OutOfMemoryError e) {
            bmp.recycle();
            throw e;
        }
    }

    static String formatKb(double kb) {
        return kb == Math.floor(kb) ? String.valueOf((long) kb) : String.format(Locale.US, "%.1f", kb);
    }
}
