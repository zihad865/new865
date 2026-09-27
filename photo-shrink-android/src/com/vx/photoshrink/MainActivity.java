package com.vx.photoshrink;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String TAG = "PhotoShrink";
    private static final int REQ_PICK = 1;
    private static final int REQ_SAVE = 2;
    private static final String PREFS = "settings";
    private static final int PREVIEW_MAX = 1024;
    private static final String GALLERY_DIR = "Pictures/PhotoShrink";

    // View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR (API 26); not present in the API 23 stubs.
    private static final int FLAG_LIGHT_NAV_BAR = 0x00000010;

    private static final int PRIMARY = Color.rgb(79, 70, 229);
    private static final int PRIMARY_DARK = Color.rgb(67, 56, 202);
    private static final int ACCENT_END = Color.rgb(6, 182, 212);
    private static final int SURFACE = Color.rgb(238, 242, 255);
    private static final int TEXT = Color.rgb(30, 27, 75);
    private static final int MUTED = Color.rgb(100, 116, 139);
    private static final int OK = Color.rgb(21, 128, 61);
    private static final int ERROR = Color.rgb(185, 28, 28);

    // {label, width, height, maxKb, exact}
    private static final Object[][] PRESETS = {
            {"1000×1000 · 100KB", 1000, 1000, 100.0, true},
            {"1000×1000 · 60KB", 1000, 1000, 60.0, true},
            {"Passport 300×300 · 20KB", 300, 300, 20.0, true},
            {"Signature 300×80 · 10KB", 300, 80, 10.0, true},
            {"1080 wide · 100KB", 1080, 0, 100.0, false},
            {"Original · 100KB", 0, 0, 100.0, false},
    };

    private EditText widthIn;
    private EditText heightIn;
    private EditText maxKbIn;
    private EditText minQualityIn;
    private CheckBox exactBox;
    private CheckBox strictBox;
    private CheckBox upscaleBox;
    private CheckBox lockBox;
    private RadioButton jpegBtn;
    private RadioButton webpBtn;
    private TextView lockSummary;
    private ImageView preview;
    private TextView sourceInfo;
    private TextView status;
    private Button pickBtn;
    private Button convertBtn;
    private Button saveBtn;
    private Button shareBtn;
    private ProgressBar progress;
    private LinearLayout header;
    private LinearLayout body;
    private final List<View> lockables = new ArrayList<>();

    private File sourceFile;
    private String sourceName = "photo";
    private long sourceBytes;
    private Shrinker.Result result;
    private File resultFile;
    private volatile boolean destroyed;
    private boolean busy;
    private boolean restoring;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupWindow();
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

    // ---------------------------------------------------------------- window & layout

    /** Draw edge-to-edge on every version (enforced from targetSdk 35) and pad content by the system bar insets. */
    private void setupWindow() {
        Window w = getWindow();
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        w.setStatusBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 26) {
            flags |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | FLAG_LIGHT_NAV_BAR;
            w.setNavigationBarColor(Color.argb(230, 255, 255, 255));
        }
        w.getDecorView().setSystemUiVisibility(flags);
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private View buildUi() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(Color.WHITE);

        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{PRIMARY, ACCENT_END}));
        TextView title = new TextView(this);
        title.setText("Photo Shrink");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView subtitle = new TextView(this);
        subtitle.setText("Any pixels. Any KB. Best quality that fits.");
        subtitle.setTextColor(Color.argb(230, 255, 255, 255));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        header.addView(title);
        header.addView(subtitle);
        outer.addView(header, matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        outer.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        buildBody(body);

        outer.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int l = insets.getSystemWindowInsetLeft();
                int t = insets.getSystemWindowInsetTop();
                int r = insets.getSystemWindowInsetRight();
                int b = insets.getSystemWindowInsetBottom();
                header.setPadding(dp(20) + l, dp(18) + t, dp(20) + r, dp(18));
                body.setPadding(dp(16) + l, dp(16), dp(16) + r, dp(24) + b);
                return insets.consumeSystemWindowInsets();
            }
        });
        header.setPadding(dp(20), dp(18), dp(20), dp(18));
        body.setPadding(dp(16), dp(16), dp(16), dp(24));
        return outer;
    }

    private void buildBody(LinearLayout root) {
        // Lock card
        LinearLayout lockCard = card();
        lockBox = new CheckBox(this);
        lockBox.setText("🔒  Lock settings");
        lockBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        lockBox.setTypeface(Typeface.DEFAULT_BOLD);
        lockBox.setTextColor(TEXT);
        lockBox.setButtonTintList(ColorStateList.valueOf(PRIMARY));
        lockBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                if (!restoring) {
                    onLockToggled(checked);
                }
            }
        });
        lockSummary = new TextView(this);
        lockSummary.setTextColor(MUTED);
        lockSummary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        lockSummary.setPadding(dp(4), dp(2), 0, 0);
        lockCard.addView(lockBox);
        lockCard.addView(lockSummary);
        root.addView(lockCard, cardParams());

        // Photo
        pickBtn = primaryButton("Select photo");
        pickBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickImage();
            }
        });
        root.addView(pickBtn, spaced(matchWrap(), 14));

        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackground(rounded(SURFACE, dp(14)));
        preview.setClipToOutline(true);
        root.addView(preview, spaced(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(240)), 10));

        sourceInfo = new TextView(this);
        sourceInfo.setText("No photo selected");
        sourceInfo.setTextColor(MUTED);
        sourceInfo.setPadding(dp(4), dp(6), 0, dp(4));
        root.addView(sourceInfo);

        // Presets
        root.addView(sectionTitle("Presets"));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        for (final Object[] p : PRESETS) {
            Button b = chip((String) p[0]);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyPreset(p);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
            lp.rightMargin = dp(8);
            presetRow.addView(b, lp);
            lockables.add(b);
        }
        hs.addView(presetRow);
        root.addView(hs);

        // Settings card
        LinearLayout settings = card();
        settings.addView(label("Resolution (pixels)"));
        LinearLayout dims = new LinearLayout(this);
        dims.setOrientation(LinearLayout.HORIZONTAL);
        dims.setGravity(Gravity.CENTER_VERTICAL);
        widthIn = numberField("Width", false);
        heightIn = numberField("Height", false);
        TextView x = new TextView(this);
        x.setText("×");
        x.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        x.setPadding(dp(10), 0, dp(10), 0);
        dims.addView(widthIn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        dims.addView(x);
        dims.addView(heightIn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        settings.addView(dims);
        settings.addView(hint("Empty = original. Only one filled = keep aspect ratio."));

        exactBox = checkBox("Exact size (fill both, ignore aspect ratio)");
        strictBox = checkBox("Never reduce pixels to reach the KB limit");
        upscaleBox = checkBox("Allow enlarging small photos");
        settings.addView(exactBox);
        settings.addView(strictBox);
        settings.addView(upscaleBox);

        settings.addView(spaced(label("Max file size (KB)"), 10));
        maxKbIn = numberField("e.g. 100", true);
        settings.addView(maxKbIn, matchWrap());

        settings.addView(spaced(label("Format"), 10));
        RadioGroup fmt = new RadioGroup(this);
        fmt.setOrientation(RadioGroup.HORIZONTAL);
        jpegBtn = radio("JPG (works everywhere)");
        webpBtn = radio("WEBP (sharper)");
        fmt.addView(jpegBtn);
        fmt.addView(webpBtn);
        settings.addView(fmt);

        settings.addView(spaced(label("Min quality before pixels shrink (1–95)"), 10));
        minQualityIn = numberField("40", false);
        settings.addView(minQualityIn, matchWrap());
        root.addView(settings, cardParams());

        lockables.add(widthIn);
        lockables.add(heightIn);
        lockables.add(exactBox);
        lockables.add(strictBox);
        lockables.add(upscaleBox);
        lockables.add(maxKbIn);
        lockables.add(jpegBtn);
        lockables.add(webpBtn);
        lockables.add(minQualityIn);

        // Convert & result
        convertBtn = primaryButton("Convert");
        convertBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                convert();
            }
        });
        root.addView(convertBtn, spaced(matchWrap(), 16));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(ColorStateList.valueOf(PRIMARY));
        progress.setVisibility(View.GONE);
        root.addView(progress, matchWrap());

        status = new TextView(this);
        status.setPadding(dp(4), dp(8), dp(4), dp(8));
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        root.addView(status);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        saveBtn = secondaryButton("Save to Gallery");
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        shareBtn = secondaryButton("Share");
        shareBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                share();
            }
        });
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, dp(48), 1);
        a.rightMargin = dp(6);
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, dp(48), 1);
        b.leftMargin = dp(6);
        actions.addView(saveBtn, a);
        actions.addView(shareBtn, b);
        root.addView(actions);
    }

    // ---------------------------------------------------------------- view helpers

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams cardParams() {
        return spaced(matchWrap(), 12);
    }

    private LinearLayout.LayoutParams spaced(LinearLayout.LayoutParams lp, int topDp) {
        lp.topMargin = dp(topDp);
        return lp;
    }

    private <T extends View> T spaced(T v, int topDp) {
        v.setPadding(v.getPaddingLeft(), dp(topDp), v.getPaddingRight(), v.getPaddingBottom());
        return v;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private Drawable ripple(Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(Color.argb(60, 255, 255, 255)), content, null);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(rounded(SURFACE, dp(16)));
        c.setPadding(dp(14), dp(12), dp(14), dp(14));
        return c;
    }

    private Button primaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(dp(52));
        b.setStateListAnimator(null);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{PRIMARY, PRIMARY_DARK});
        g.setCornerRadius(dp(14));
        b.setBackground(ripple(g));
        return b;
    }

    private Button secondaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(PRIMARY);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setStateListAnimator(null);
        GradientDrawable g = rounded(Color.WHITE, dp(14));
        g.setStroke(dp(2), PRIMARY);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(40, 79, 70, 229)), g, null));
        return b;
    }

    private Button chip(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(PRIMARY_DARK);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setStateListAnimator(null);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(14), 0, dp(14), 0);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(40, 79, 70, 229)),
                rounded(SURFACE, dp(20)), null));
        return b;
    }

    private TextView sectionTitle(String text) {
        TextView t = label(text);
        t.setPadding(dp(4), dp(14), 0, dp(6));
        return t;
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        return t;
    }

    private TextView hint(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(MUTED);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setPadding(dp(4), 0, 0, dp(4));
        return t;
    }

    private CheckBox checkBox(String text) {
        CheckBox c = new CheckBox(this);
        c.setText(text);
        c.setTextColor(TEXT);
        c.setButtonTintList(ColorStateList.valueOf(PRIMARY));
        return c;
    }

    private RadioButton radio(String text) {
        RadioButton r = new RadioButton(this);
        r.setText(text);
        r.setTextColor(TEXT);
        r.setId(View.generateViewId());
        r.setButtonTintList(ColorStateList.valueOf(PRIMARY));
        return r;
    }

    private EditText numberField(String hintText, boolean decimal) {
        EditText e = new EditText(this);
        e.setHint(hintText);
        e.setSingleLine(true);
        e.setTextColor(TEXT);
        e.setBackgroundTintList(ColorStateList.valueOf(PRIMARY));
        e.setInputType(InputType.TYPE_CLASS_NUMBER | (decimal ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
        return e;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- lock

    private void onLockToggled(boolean checked) {
        if (checked) {
            Shrinker.Options o = readOptions();
            if (o == null) {
                restoring = true;
                lockBox.setChecked(false);
                restoring = false;
                toast("Fix the highlighted settings first");
                return;
            }
            savePrefs();
            applyLockState(true, o);
            toast("Locked: " + describe(o));
        } else {
            applyLockState(false, null);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("locked", false).apply();
            toast("Unlocked");
        }
    }

    private void applyLockState(boolean locked, Shrinker.Options o) {
        for (View v : lockables) {
            v.setEnabled(!locked);
            v.setAlpha(locked ? 0.45f : 1f);
        }
        if (locked) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("locked", true).apply();
            lockSummary.setText(describe(o) + "\nEvery photo you select converts automatically. Untick to change.");
            lockSummary.setTextColor(PRIMARY_DARK);
            convertBtn.setText("Convert again");
        } else {
            lockSummary.setText("Tick to keep the current pixels & KB until you untick.");
            lockSummary.setTextColor(MUTED);
            convertBtn.setText("Convert");
        }
    }

    private String describe(Shrinker.Options o) {
        StringBuilder sb = new StringBuilder();
        if (o.width > 0 && o.height > 0) {
            sb.append(o.width).append("×").append(o.height).append(" px").append(o.exact ? " (exact)" : " (fit)");
        } else if (o.width > 0) {
            sb.append(o.width).append(" px wide");
        } else if (o.height > 0) {
            sb.append(o.height).append(" px tall");
        } else {
            sb.append("Original pixels");
        }
        sb.append(" · max ").append(Shrinker.formatKb(o.maxKb)).append(" KB · ").append(o.webp ? "WEBP" : "JPG");
        if (o.strictResolution) {
            sb.append(" · strict");
        }
        return sb.toString();
    }

    private boolean isLocked() {
        return lockBox.isChecked();
    }

    // ---------------------------------------------------------------- prefs

    private void loadPrefs() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        widthIn.setText(sp.getString("width", "1000"));
        heightIn.setText(sp.getString("height", "1000"));
        maxKbIn.setText(sp.getString("maxKb", "100"));
        minQualityIn.setText(sp.getString("minQ", "40"));
        exactBox.setChecked(sp.getBoolean("exact", true));
        strictBox.setChecked(sp.getBoolean("strict", false));
        upscaleBox.setChecked(sp.getBoolean("upscale", false));
        if (sp.getBoolean("webp", false)) {
            webpBtn.setChecked(true);
        } else {
            jpegBtn.setChecked(true);
        }
        boolean locked = sp.getBoolean("locked", false);
        Shrinker.Options o = locked ? readOptions() : null;
        if (locked && o == null) {
            locked = false;
        }
        restoring = true;
        lockBox.setChecked(locked);
        restoring = false;
        applyLockState(locked, o);
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

    private void applyPreset(Object[] p) {
        if (isLocked()) {
            return;
        }
        int w = (Integer) p[1];
        int h = (Integer) p[2];
        widthIn.setText(w > 0 ? String.valueOf(w) : "");
        heightIn.setText(h > 0 ? String.valueOf(h) : "");
        maxKbIn.setText(Shrinker.formatKb((Double) p[3]));
        exactBox.setChecked((Boolean) p[4]);
        jpegBtn.setChecked(true);
        widthIn.setError(null);
        heightIn.setError(null);
        maxKbIn.setError(null);
        toast("Preset: " + p[0]);
    }

    // ---------------------------------------------------------------- state

    private void updateButtons() {
        pickBtn.setEnabled(!busy);
        convertBtn.setEnabled(!busy && sourceFile != null);
        saveBtn.setEnabled(!busy && result != null);
        shareBtn.setEnabled(!busy && resultFile != null);
        convertBtn.setAlpha(convertBtn.isEnabled() ? 1f : 0.5f);
        pickBtn.setAlpha(pickBtn.isEnabled() ? 1f : 0.5f);
        saveBtn.setAlpha(saveBtn.isEnabled() ? 1f : 0.4f);
        shareBtn.setAlpha(shareBtn.isEnabled() ? 1f : 0.4f);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void setBusy(boolean b) {
        busy = b;
        updateButtons();
    }

    private void setStatus(String text, int color) {
        status.setTextColor(color);
        status.setText(text);
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
        setStatus("Loading photo…", MUTED);
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
                            setStatus("✗ " + err, ERROR);
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
                        sourceInfo.setText(String.format(Locale.US, "Original: %s · %d×%d · %s",
                                name, d[0], d[1], humanSize(size)));
                        updateButtons();
                        if (isLocked()) {
                            convert();
                        } else {
                            setStatus("Ready. Set pixels and KB, then tap Convert.", MUTED);
                        }
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

    private Integer readInt(EditText e, int min, int max, boolean optional, String name) {
        String s = e.getText().toString().trim();
        if (s.isEmpty()) {
            if (optional) {
                return 0;
            }
            e.setError(name + " required");
            return null;
        }
        try {
            int v = Integer.parseInt(s);
            if (v < min || v > max) {
                e.setError(name + " must be " + min + "–" + max);
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
        try {
            kb = Double.parseDouble(maxKbIn.getText().toString().trim());
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
        if (!isLocked()) {
            savePrefs();
        }
        setBusy(true);
        result = null;
        resultFile = null;
        setStatus("Converting…", MUTED);
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
                                        setStatus(message, MUTED);
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
                            setStatus("✗ " + err, ERROR);
                            return;
                        }
                        result = fr;
                        resultFile = fo;
                        if (t != null) {
                            preview.setImageBitmap(t);
                        }
                        StringBuilder sb = new StringBuilder();
                        sb.append(String.format(Locale.US, "✓ %d×%d · %s · %s quality %d",
                                fr.width, fr.height, humanSize(fr.data.length),
                                fr.webp ? "WEBP" : "JPG", fr.quality));
                        sb.append(String.format(Locale.US, "\nFrom %d×%d · %s",
                                fr.originalWidth, fr.originalHeight, humanSize(sourceBytes)));
                        if (fr.downscaled()) {
                            sb.append(String.format(Locale.US,
                                    "\nNote: %d×%d could not fit the KB limit, reduced to %d×%d."
                                            + " Tick \"Never reduce pixels\" to prevent this.",
                                    fr.requestedWidth, fr.requestedHeight, fr.width, fr.height));
                        }
                        setStatus(sb.toString(), OK);
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
        if (Build.VERSION.SDK_INT >= 29) {
            saveToGallery(result, resultFile.getName());
        } else {
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
    }

    /** API 29+: write straight into Pictures/PhotoShrink through MediaStore, no permission needed. */
    private void saveToGallery(final Shrinker.Result r, final String name) {
        setBusy(true);
        new Thread(new Runnable() {
            @Override
            public void run() {
                String msg;
                Uri uri = null;
                try {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    v.put(MediaStore.MediaColumns.MIME_TYPE, r.mimeType());
                    v.put("relative_path", GALLERY_DIR);
                    v.put("is_pending", 1);
                    uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                    if (uri == null) {
                        throw new IOException("Gallery refused the file");
                    }
                    try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                        if (out == null) {
                            throw new IOException("Cannot open gallery file");
                        }
                        out.write(r.data);
                    }
                    ContentValues done = new ContentValues();
                    done.put("is_pending", 0);
                    getContentResolver().update(uri, done, null, null);
                    msg = "Saved to " + GALLERY_DIR + " (" + humanSize(r.data.length) + ")";
                } catch (IOException | RuntimeException e) {
                    Log.e(TAG, "gallery save failed", e);
                    if (uri != null) {
                        try {
                            getContentResolver().delete(uri, null, null);
                        } catch (RuntimeException ignored) {
                            Log.w(TAG, "cleanup failed", ignored);
                        }
                    }
                    msg = "Save failed: " + e.getMessage();
                }
                final String m = msg;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!destroyed) {
                            setBusy(false);
                            toast(m);
                        }
                    }
                });
            }
        }).start();
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
