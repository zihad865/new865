package com.vx.photoshrink;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String TAG = "PhotoShrink";
    private static final int REQ_PICK = 1;
    private static final int REQ_SAVE = 2;
    private static final String PREFS = "settings";
    private static final int PREVIEW_MAX = 1024;

    // {label, width, height, maxKb, exact}
    private static final Object[][] PRESETS = {
            {"Passport 300×300 · 20KB", 300, 300, 20.0, true},
            {"Signature 300×80 · 10KB", 300, 80, 10.0, true},
            {"600×600 · 50KB", 600, 600, 50.0, true},
            {"1080 wide · 100KB", 1080, 0, 100.0, false},
            {"Original size · 100KB", 0, 0, 100.0, false},
    };

    private EditText widthIn;
    private EditText heightIn;
    private EditText maxKbIn;
    private EditText minQualityIn;
    private CheckBox exactBox;
    private CheckBox strictBox;
    private CheckBox upscaleBox;
    private RadioButton jpegBtn;
    private RadioButton webpBtn;
    private ImageView preview;
    private TextView sourceInfo;
    private TextView status;
    private Button pickBtn;
    private Button convertBtn;
    private Button saveBtn;
    private Button shareBtn;
    private ProgressBar progress;

    private File sourceFile;
    private String sourceName = "photo";
    private long sourceBytes;
    private Shrinker.Result result;
    private File resultFile;
    private volatile boolean destroyed;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        loadPrefs();
        updateButtons();
        if (savedInstanceState == null) {
            handleIncoming(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncoming(intent);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }

    private void handleIncoming(Intent intent) {
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) {
                loadSource(uri);
            }
        }
    }

    // ---------------------------------------------------------------- UI

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(root);

        pickBtn = new Button(this);
        pickBtn.setText("Select photo");
        pickBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickImage();
            }
        });
        root.addView(pickBtn, matchWrap());

        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setAdjustViewBounds(true);
        preview.setBackgroundColor(Color.rgb(238, 238, 238));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220));
        plp.topMargin = dp(8);
        root.addView(preview, plp);

        sourceInfo = new TextView(this);
        sourceInfo.setText("No photo selected");
        sourceInfo.setPadding(0, dp(6), 0, dp(6));
        root.addView(sourceInfo);

        root.addView(sectionTitle("Presets"));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        for (final Object[] p : PRESETS) {
            Button b = new Button(this);
            b.setText((String) p[0]);
            b.setAllCaps(false);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyPreset(p);
                }
            });
            presetRow.addView(b);
        }
        hs.addView(presetRow);
        root.addView(hs);

        root.addView(sectionTitle("Resolution (pixels)"));
        LinearLayout dims = new LinearLayout(this);
        dims.setOrientation(LinearLayout.HORIZONTAL);
        widthIn = numberField("Width", false);
        heightIn = numberField("Height", false);
        TextView x = new TextView(this);
        x.setText(" × ");
        x.setGravity(Gravity.CENTER);
        dims.addView(widthIn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        dims.addView(x);
        dims.addView(heightIn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(dims);
        TextView hint = new TextView(this);
        hint.setText("Leave empty to keep original. Only one set = keep aspect ratio.");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        root.addView(hint);

        exactBox = new CheckBox(this);
        exactBox.setText("Exact size (ignore aspect ratio, needs both)");
        root.addView(exactBox);
        strictBox = new CheckBox(this);
        strictBox.setText("Strict resolution (never reduce pixels to fit KB)");
        root.addView(strictBox);
        upscaleBox = new CheckBox(this);
        upscaleBox.setText("Allow enlarging small photos");
        root.addView(upscaleBox);

        root.addView(sectionTitle("Max file size (KB)"));
        maxKbIn = numberField("e.g. 100", true);
        root.addView(maxKbIn, matchWrap());

        root.addView(sectionTitle("Format"));
        RadioGroup fmt = new RadioGroup(this);
        fmt.setOrientation(RadioGroup.HORIZONTAL);
        jpegBtn = new RadioButton(this);
        jpegBtn.setText("JPG (works everywhere)");
        jpegBtn.setId(View.generateViewId());
        webpBtn = new RadioButton(this);
        webpBtn.setText("WEBP (better quality)");
        webpBtn.setId(View.generateViewId());
        fmt.addView(jpegBtn);
        fmt.addView(webpBtn);
        root.addView(fmt);

        root.addView(sectionTitle("Min quality before shrinking pixels (1-100)"));
        minQualityIn = numberField("40", false);
        root.addView(minQualityIn, matchWrap());

        convertBtn = new Button(this);
        convertBtn.setText("Convert");
        convertBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                convert();
            }
        });
        LinearLayout.LayoutParams clp = matchWrap();
        clp.topMargin = dp(12);
        root.addView(convertBtn, clp);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        root.addView(progress, matchWrap());

        status = new TextView(this);
        status.setPadding(0, dp(8), 0, dp(8));
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        root.addView(status);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        saveBtn = new Button(this);
        saveBtn.setText("Save");
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        shareBtn = new Button(this);
        shareBtn.setText("Share");
        shareBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                share();
            }
        });
        actions.addView(saveBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        actions.addView(shareBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(actions);

        return scroll;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private TextView sectionTitle(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(12), 0, dp(2));
        return t;
    }

    private EditText numberField(String hint, boolean decimal) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | (decimal ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
        return e;
    }

    private void applyPreset(Object[] p) {
        int w = (Integer) p[1];
        int h = (Integer) p[2];
        widthIn.setText(w > 0 ? String.valueOf(w) : "");
        heightIn.setText(h > 0 ? String.valueOf(h) : "");
        maxKbIn.setText(Shrinker.formatKb((Double) p[3]));
        exactBox.setChecked((Boolean) p[4]);
        jpegBtn.setChecked(true);
        toast("Preset: " + p[0]);
    }

    private void updateButtons() {
        pickBtn.setEnabled(!busy);
        convertBtn.setEnabled(!busy && sourceFile != null);
        saveBtn.setEnabled(!busy && result != null);
        shareBtn.setEnabled(!busy && resultFile != null);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void setBusy(boolean b) {
        busy = b;
        updateButtons();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- prefs

    private void loadPrefs() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        widthIn.setText(sp.getString("width", ""));
        heightIn.setText(sp.getString("height", ""));
        maxKbIn.setText(sp.getString("maxKb", "100"));
        minQualityIn.setText(sp.getString("minQ", "40"));
        exactBox.setChecked(sp.getBoolean("exact", false));
        strictBox.setChecked(sp.getBoolean("strict", false));
        upscaleBox.setChecked(sp.getBoolean("upscale", false));
        if (sp.getBoolean("webp", false)) {
            webpBtn.setChecked(true);
        } else {
            jpegBtn.setChecked(true);
        }
    }

    private void savePrefs() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("width", widthIn.getText().toString().trim())
                .putString("height", heightIn.getText().toString().trim())
                .putString("maxKb", maxKbIn.getText().toString().trim())
                .putString("minQ", minQualityIn.getText().toString().trim())
                .putBoolean("exact", exactBox.isChecked())
                .putBoolean("strict", strictBox.isChecked())
                .putBoolean("upscale", upscaleBox.isChecked())
                .putBoolean("webp", webpBtn.isChecked())
                .apply();
    }

    // ---------------------------------------------------------------- pick & load

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, "Select photo"), REQ_PICK);
        } catch (ActivityNotFoundException e) {
            toast("No gallery or file app found");
        }
    }

    private void loadSource(final Uri uri) {
        setBusy(true);
        status.setTextColor(Color.DKGRAY);
        status.setText("Loading photo…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final File dest = new File(getCacheDir(), "source.bin");
                final String name = displayName(uri);
                String error = null;
                long bytes = 0;
                Bitmap thumb = null;
                int[] dims = null;
                try (InputStream in = getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(dest)) {
                    if (in == null) {
                        throw new IOException("Cannot open this file");
                    }
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        bytes += n;
                    }
                } catch (IOException | SecurityException e) {
                    Log.e(TAG, "load failed", e);
                    error = "Cannot open photo: " + e.getMessage();
                }
                if (error == null) {
                    dims = Shrinker.orientedBounds(dest);
                    if (dims == null) {
                        error = "This file is not a supported image.";
                    } else {
                        thumb = decodePreviewFile(dest);
                    }
                }
                final String err = error;
                final long size = bytes;
                final Bitmap t = thumb;
                final int[] d = dims;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (destroyed) {
                            return;
                        }
                        setBusy(false);
                        if (err != null) {
                            status.setText(err);
                            return;
                        }
                        sourceFile = dest;
                        sourceName = name;
                        sourceBytes = size;
                        result = null;
                        resultFile = null;
                        if (t != null) {
                            preview.setImageBitmap(t);
                        }
                        sourceInfo.setText(String.format(Locale.US, "Original: %s\n%d×%d · %s",
                                name, d[0], d[1], humanSize(size)));
                        status.setText("Ready. Set pixels and KB, then tap Convert.");
                        updateButtons();
                    }
                });
            }
        }).start();
    }

    private String displayName(Uri uri) {
        String name = null;
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) {
                name = c.getString(0);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "display name lookup failed", e);
        }
        if (TextUtils.isEmpty(name)) {
            name = uri.getLastPathSegment();
        }
        if (TextUtils.isEmpty(name)) {
            name = "photo";
        }
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        stem = stem.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        return stem.isEmpty() ? "photo" : stem;
    }

    private Bitmap decodePreviewFile(File f) {
        BitmapFactory.Options b = new BitmapFactory.Options();
        b.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), b);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sampleFor(b.outWidth, b.outHeight);
        try {
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    private Bitmap decodePreviewBytes(byte[] data) {
        BitmapFactory.Options b = new BitmapFactory.Options();
        b.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, b);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sampleFor(b.outWidth, b.outHeight);
        try {
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    private static int sampleFor(int w, int h) {
        int s = 1;
        while (w / (s * 2) >= PREVIEW_MAX || h / (s * 2) >= PREVIEW_MAX) {
            s *= 2;
        }
        return s;
    }

    // ---------------------------------------------------------------- convert

    private Integer readInt(EditText e, int min, int max, boolean optional, String label) {
        String s = e.getText().toString().trim();
        if (s.isEmpty()) {
            if (optional) {
                return 0;
            }
            e.setError(label + " required");
            return null;
        }
        try {
            int v = Integer.parseInt(s);
            if (v < min || v > max) {
                e.setError(label + " must be " + min + "–" + max);
                return null;
            }
            return v;
        } catch (NumberFormatException ex) {
            e.setError("Invalid number");
            return null;
        }
    }

    private Shrinker.Options readOptions() {
        Integer w = readInt(widthIn, Shrinker.MIN_DIMENSION, 20000, true, "Width");
        Integer h = readInt(heightIn, Shrinker.MIN_DIMENSION, 20000, true, "Height");
        Integer minQ = readInt(minQualityIn, 1, 95, false, "Min quality");
        Double kb = null;
        String kbText = maxKbIn.getText().toString().trim();
        try {
            kb = Double.parseDouble(kbText);
            if (kb < 1 || kb > 100000) {
                maxKbIn.setError("KB must be 1–100000");
                kb = null;
            }
        } catch (NumberFormatException ex) {
            maxKbIn.setError("Enter a KB value, e.g. 100");
        }
        if (w == null || h == null || minQ == null || kb == null) {
            return null;
        }
        if (exactBox.isChecked() && (w == 0 || h == 0)) {
            toast("Exact size needs both width and height");
            return null;
        }
        Shrinker.Options o = new Shrinker.Options();
        o.width = w;
        o.height = h;
        o.exact = exactBox.isChecked();
        o.strictResolution = strictBox.isChecked();
        o.allowUpscale = upscaleBox.isChecked();
        o.maxKb = kb;
        o.minQuality = minQ;
        o.maxQuality = 95;
        o.webp = webpBtn.isChecked();
        return o;
    }

    private void convert() {
        if (sourceFile == null) {
            toast("Select a photo first");
            return;
        }
        final Shrinker.Options o = readOptions();
        if (o == null) {
            return;
        }
        savePrefs();
        setBusy(true);
        result = null;
        resultFile = null;
        status.setTextColor(Color.DKGRAY);
        status.setText("Converting…");
        final File src = sourceFile;
        final String stem = sourceName;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Shrinker.Result r = null;
                File out = null;
                String error = null;
                Bitmap thumb = null;
                try {
                    r = Shrinker.run(src, o, new Shrinker.Progress() {
                        @Override
                        public void onProgress(final String message) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (!destroyed) {
                                        status.setText(message);
                                    }
                                }
                            });
                        }
                    });
                    out = writeResult(r, stem);
                    thumb = decodePreviewBytes(r.data);
                } catch (Shrinker.ShrinkException e) {
                    error = e.getMessage();
                } catch (IOException e) {
                    Log.e(TAG, "write failed", e);
                    error = "Could not write output: " + e.getMessage();
                } catch (OutOfMemoryError e) {
                    error = "Out of memory. Try a smaller resolution.";
                } catch (RuntimeException e) {
                    Log.e(TAG, "convert failed", e);
                    error = "Conversion failed: " + e;
                }
                final Shrinker.Result fr = r;
                final File fo = out;
                final String err = error;
                final Bitmap t = thumb;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (destroyed) {
                            return;
                        }
                        setBusy(false);
                        if (err != null) {
                            status.setTextColor(Color.rgb(198, 40, 40));
                            status.setText("✗ " + err);
                            return;
                        }
                        result = fr;
                        resultFile = fo;
                        if (t != null) {
                            preview.setImageBitmap(t);
                        }
                        status.setTextColor(Color.rgb(46, 125, 50));
                        StringBuilder sb = new StringBuilder();
                        sb.append(String.format(Locale.US, "✓ %d×%d · %s · %s quality %d",
                                fr.width, fr.height, humanSize(fr.data.length),
                                fr.webp ? "WEBP" : "JPG", fr.quality));
                        sb.append(String.format(Locale.US, "\nFrom %d×%d · %s",
                                fr.originalWidth, fr.originalHeight, humanSize(sourceBytes)));
                        if (fr.downscaled()) {
                            sb.append(String.format(Locale.US,
                                    "\nNote: %d×%d could not fit the KB limit, reduced to %d×%d."
                                            + " Enable Strict resolution to prevent this.",
                                    fr.requestedWidth, fr.requestedHeight, fr.width, fr.height));
                        }
                        status.setText(sb.toString());
                        updateButtons();
                    }
                });
            }
        }).start();
    }

    private File writeResult(Shrinker.Result r, String stem) throws IOException {
        File dir = new File(getCacheDir(), ShareProvider.OUT_DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Cannot create " + dir);
        }
        File[] old = dir.listFiles();
        if (old != null) {
            for (File f : old) {
                if (!f.delete()) {
                    Log.w(TAG, "could not delete " + f);
                }
            }
        }
        File out = new File(dir, stem + "_" + r.width + "x" + r.height + r.extension());
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(r.data);
        }
        return out;
    }

    // ---------------------------------------------------------------- save & share

    private void save() {
        if (result == null || resultFile == null) {
            return;
        }
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(result.mimeType());
        i.putExtra(Intent.EXTRA_TITLE, resultFile.getName());
        try {
            startActivityForResult(i, REQ_SAVE);
        } catch (ActivityNotFoundException e) {
            toast("No file manager available to save. Use Share instead.");
        }
    }

    private void writeTo(Uri uri) {
        if (result == null) {
            return;
        }
        try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
            if (out == null) {
                throw new IOException("Cannot open destination");
            }
            out.write(result.data);
            toast("Saved (" + humanSize(result.data.length) + ")");
        } catch (IOException | SecurityException e) {
            Log.e(TAG, "save failed", e);
            toast("Save failed: " + e.getMessage());
        }
    }

    private void share() {
        if (result == null || resultFile == null || !resultFile.isFile()) {
            return;
        }
        Uri uri = ShareProvider.uriFor(resultFile);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(result.mimeType());
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.setClipData(ClipData.newRawUri(resultFile.getName(), uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(send, "Share photo"));
        } catch (ActivityNotFoundException e) {
            toast("No app available to share");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        if (requestCode == REQ_PICK) {
            loadSource(data.getData());
        } else if (requestCode == REQ_SAVE) {
            writeTo(data.getData());
        }
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.US, "%.1f KB", kb);
        }
        return String.format(Locale.US, "%.2f MB", kb / 1024.0);
    }
}
