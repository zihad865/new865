package com.vx.photoshrink;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String TAG = "PhotoShrink";
    private static final int REQ_PICK = 1;
    private static final int REQ_SAVE = 2;
    private static final String PREFS = "settings";
    private static final String GALLERY_DIR = "Pictures/PhotoShrink";
    private static final int PREVIEW_MAX = 1280;
    private static final int KEEP_RESULTS = 10;
    private static final int MAX_STEM = 60;

    // View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR (API 26); not present in the API 23 stubs.
    private static final int FLAG_LIGHT_NAV_BAR = 0x00000010;

    private Ui ui;
    private LinearLayout column;
    private FrameLayout photoFrame;
    private ImageView preview;
    private TextView placeholder;
    private TextView originalValue;
    private EditText widthIn;
    private EditText heightIn;
    private EditText maxKbIn;
    private Switch keepAspect;
    private Switch neverReduce;
    private Switch lockSwitch;
    private Ui.Segmented format;
    private TextView lockFooter;
    private Button convertBtn;
    private LinearLayout progressRow;
    private TextView progressText;
    private TextView errorText;
    private LinearLayout resultHeader;
    private LinearLayout resultCard;
    private TextView resultFooter;
    private TextView dimsValue;
    private TextView sizeValue;
    private TextView qualityValue;
    private LinearLayout actions;
    private Button saveBtn;
    private Button shareBtn;

    private File sourceFile;
    private String sourceName = "photo";
    private long sourceBytes;
    private int[] sourceDims;
    private Shrinker.Result result;
    private File resultFile;
    private Uri pendingUri;
    private volatile boolean destroyed;
    private boolean busy;
    private boolean restoring;

    // ================================================================ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ui = new Ui(this);
        setupWindow();
        setContentView(buildUi());
        loadPrefs();
        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        }
        cleanupSources();
        if (savedInstanceState == null) {
            handleIncoming(getIntent());
        }
        refresh();
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

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (sourceFile != null) {
            out.putString("srcPath", sourceFile.getPath());
            out.putString("srcName", sourceName);
            out.putLong("srcBytes", sourceBytes);
            out.putIntArray("srcDims", sourceDims);
        }
        if (result != null && resultFile != null) {
            out.putString("resPath", resultFile.getPath());
            out.putIntArray("resMeta", new int[]{result.width, result.height, result.quality,
                    result.webp ? 1 : 0, result.requestedWidth, result.requestedHeight});
        }
    }

    private void restoreState(Bundle in) {
        String src = in.getString("srcPath");
        if (src == null || !new File(src).isFile()) {
            return;
        }
        sourceFile = new File(src);
        sourceName = in.getString("srcName", "photo");
        sourceBytes = in.getLong("srcBytes");
        sourceDims = in.getIntArray("srcDims");
        showOriginal();
        String res = in.getString("resPath");
        int[] m = in.getIntArray("resMeta");
        if (res != null && m != null && m.length == 6 && new File(res).isFile()) {
            try {
                byte[] data = readFile(new File(res));
                result = new Shrinker.Result(data, m[0], m[1], m[2], m[3] == 1, m[4], m[5]);
                resultFile = new File(res);
                showResult();
            } catch (IOException e) {
                Log.w(TAG, "could not restore result", e);
            }
        }
        final File f = sourceFile;
        final File r = resultFile;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap b = r != null ? decodePreview(r) : decodePreview(f);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!destroyed) {
                            setPreview(b);
                        }
                    }
                });
            }
        }).start();
    }

    private void handleIncoming(Intent intent) {
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) {
                requestLoad(uri);
            }
        }
    }

    // ================================================================ window

    /** Edge-to-edge on API 23+ (enforced from targetSdk 35); content is padded by system bar insets. */
    private void setupWindow() {
        Window w = getWindow();
        w.getDecorView().setBackgroundColor(ui.background);
        if (Build.VERSION.SDK_INT >= 23) {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            w.setStatusBarColor(Color.TRANSPARENT);
            if (!ui.dark) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            if (Build.VERSION.SDK_INT >= 26) {
                flags |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
                if (!ui.dark) {
                    flags |= FLAG_LIGHT_NAV_BAR;
                }
                w.setNavigationBarColor(Color.argb(220, Color.red(ui.background),
                        Color.green(ui.background), Color.blue(ui.background)));
            }
            w.getDecorView().setSystemUiVisibility(flags);
        } else {
            w.setStatusBarColor(Color.BLACK);
        }
    }

    // ================================================================ layout

    private View buildUi() {
        final ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(ui.background);

        FrameLayout center = new FrameLayout(this);
        scroll.addView(center);

        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        float screenDp = getResources().getConfiguration().screenWidthDp;
        int width = screenDp > 680 ? ui.dp(640) : ViewGroup.LayoutParams.MATCH_PARENT;
        center.addView(column, new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL));

        buildContent(column);

        scroll.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
                return insets.consumeSystemWindowInsets();
            }
        });
        return scroll;
    }

    private void buildContent(LinearLayout root) {
        root.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(32));

        TextView title = ui.text("Photo Shrink", 34, ui.label, Ui.BOLD);
        title.setPadding(ui.dp(4), ui.dp(8), 0, 0);
        root.addView(title);

        // ---- Photo
        LinearLayout photoCard = ui.section(root, null, null);
        photoFrame = new FrameLayout(this);
        photoFrame.setBackground(ui.pressableFill());
        photoFrame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickImage();
            }
        });
        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setVisibility(View.GONE);
        photoFrame.addView(preview, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        placeholder = ui.text("＋\nChoose Photo", 17, ui.blue, Ui.MEDIUM);
        placeholder.setGravity(Gravity.CENTER);
        placeholder.setLineSpacing(ui.dp(4), 1f);
        photoFrame.addView(placeholder, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        photoCard.addView(photoFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(260)));
        originalValue = ui.text("None", 17, ui.secondary, Ui.REGULAR);
        TextView change = ui.text("Choose…", 17, ui.blue, Ui.REGULAR);
        change.setPadding(ui.dp(12), ui.dp(8), ui.dp(4), ui.dp(8));
        change.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickImage();
            }
        });
        LinearLayout origRow = ui.row(photoCard, "Original", originalValue);
        origRow.addView(change);

        // ---- Size
        LinearLayout size = ui.section(root, "Size", "Leave width or height empty to scale automatically.");
        widthIn = ui.numberField("Auto", false);
        heightIn = ui.numberField("Auto", false);
        ui.row(size, "Width", ui.valueField(widthIn, "px"));
        ui.row(size, "Height", ui.valueField(heightIn, "px"));
        keepAspect = ui.toggle();
        neverReduce = ui.toggle();
        ui.row(size, "Keep Aspect Ratio", keepAspect);
        ui.row(size, "Never Reduce Pixels", neverReduce);

        // ---- File
        LinearLayout file = ui.section(root, "File", "WEBP looks sharper at the same size. Some upload forms accept JPG only.");
        maxKbIn = ui.numberField("100", true);
        ui.row(file, "Max Size", ui.valueField(maxKbIn, "KB"));
        format = new Ui.Segmented(ui, "JPG", "WEBP");
        LinearLayout fmtRow = ui.row(file, "Format", null);
        fmtRow.addView(format.view, new LinearLayout.LayoutParams(ui.dp(150), ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- Lock
        LinearLayout lock = ui.section(root, null, "");
        lockFooter = (TextView) lock.getTag();
        lockSwitch = ui.toggle();
        ui.row(lock, "Lock Settings", lockSwitch);
        lockSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                if (!restoring) {
                    onLockToggled(checked);
                }
            }
        });

        // ---- Convert
        convertBtn = ui.filledButton("Convert");
        convertBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                convert();
            }
        });
        root.addView(convertBtn, ui.margins(ui.matchWrap(), 28, 0));

        progressRow = new LinearLayout(this);
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        progressRow.setGravity(Gravity.CENTER);
        progressRow.setPadding(0, ui.dp(12), 0, 0);
        ProgressBar spinner = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(ui.secondary));
        progressText = ui.text("", 15, ui.secondary, Ui.REGULAR);
        progressText.setPadding(ui.dp(8), 0, 0, 0);
        progressRow.addView(spinner);
        progressRow.addView(progressText);
        progressRow.setVisibility(View.GONE);
        root.addView(progressRow, ui.matchWrap());

        errorText = ui.text("", 15, ui.red, Ui.REGULAR);
        errorText.setGravity(Gravity.CENTER);
        errorText.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), 0);
        errorText.setVisibility(View.GONE);
        root.addView(errorText, ui.matchWrap());

        // ---- Result
        resultHeader = new LinearLayout(this);
        resultHeader.setOrientation(LinearLayout.VERTICAL);
        root.addView(resultHeader, ui.matchWrap());
        resultCard = ui.section(resultHeader, "Result", "");
        resultFooter = (TextView) resultCard.getTag();
        dimsValue = ui.text("", 17, ui.secondary, Ui.REGULAR);
        sizeValue = ui.text("", 17, ui.secondary, Ui.REGULAR);
        qualityValue = ui.text("", 17, ui.secondary, Ui.REGULAR);
        ui.row(resultCard, "Dimensions", dimsValue);
        ui.row(resultCard, "File Size", sizeValue);
        ui.row(resultCard, "Quality", qualityValue);

        actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        saveBtn = ui.tintedButton("Save to Gallery");
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        shareBtn = ui.tintedButton("Share");
        shareBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                share();
            }
        });
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        a.rightMargin = ui.dp(6);
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        b.leftMargin = ui.dp(6);
        actions.addView(saveBtn, a);
        actions.addView(shareBtn, b);
        resultHeader.addView(actions, ui.margins(ui.matchWrap(), 16, 0));
    }

    // ================================================================ state → views

    private void refresh() {
        boolean locked = lockSwitch.isChecked();
        convertBtn.setEnabled(!busy && sourceFile != null);
        convertBtn.setAlpha(convertBtn.isEnabled() ? 1f : 0.4f);
        convertBtn.setText(locked && result != null ? "Convert Again" : "Convert");
        photoFrame.setEnabled(!busy);
        progressRow.setVisibility(busy ? View.VISIBLE : View.GONE);
        resultHeader.setVisibility(result != null ? View.VISIBLE : View.GONE);
        saveBtn.setEnabled(!busy && result != null);
        shareBtn.setEnabled(!busy && resultFile != null);
        saveBtn.setAlpha(saveBtn.isEnabled() ? 1f : 0.5f);
        shareBtn.setAlpha(shareBtn.isEnabled() ? 1f : 0.5f);

        View[] lockables = {widthIn, heightIn, maxKbIn, keepAspect, neverReduce};
        for (View v : lockables) {
            v.setEnabled(!locked);
            v.setAlpha(locked ? 0.45f : 1f);
        }
        format.setEnabled(!locked);
        boolean aspectApplies = widthIn.length() > 0 && heightIn.length() > 0;
        keepAspect.setEnabled(!locked && aspectApplies);
        keepAspect.setAlpha(keepAspect.isEnabled() ? 1f : 0.45f);
    }

    /** Removes source copies left behind by earlier sessions. */
    private void cleanupSources() {
        File[] files = getCacheDir().listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.getName().startsWith("src-") && !f.equals(sourceFile) && !f.delete()) {
                Log.w(TAG, "could not delete " + f);
            }
        }
    }

    private void setBusy(boolean b, String message) {
        busy = b;
        progressText.setText(message == null ? "" : message);
        if (b) {
            errorText.setVisibility(View.GONE);
        }
        refresh();
        if (!b && pendingUri != null) {
            Uri next = pendingUri;
            pendingUri = null;
            requestLoad(next);
        }
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }

    private void setPreview(Bitmap b) {
        if (b != null) {
            preview.setImageBitmap(b);
            preview.setVisibility(View.VISIBLE);
            placeholder.setVisibility(View.GONE);
        } else {
            preview.setImageDrawable(null);
            preview.setVisibility(View.GONE);
            placeholder.setText(sourceFile != null ? "Preview unavailable" : "＋\nChoose Photo");
            placeholder.setVisibility(View.VISIBLE);
        }
    }

    private void showOriginal() {
        if (sourceDims != null) {
            originalValue.setText(String.format(Locale.US, "%d × %d · %s",
                    sourceDims[0], sourceDims[1], humanSize(sourceBytes)));
        }
    }

    private void showResult() {
        Shrinker.Result r = result;
        dimsValue.setText(String.format(Locale.US, "%d × %d", r.width, r.height));
        sizeValue.setText(humanSize(r.data.length) + " · " + (r.webp ? "WEBP" : "JPG"));
        String grade;
        int color;
        if (r.quality >= 80) {
            grade = "Excellent";
            color = ui.green;
        } else if (r.quality >= 60) {
            grade = "Good";
            color = ui.label;
        } else {
            grade = "Low";
            color = ui.orange;
        }
        qualityValue.setText(r.quality + "% · " + grade);
        qualityValue.setTextColor(color);

        StringBuilder tip = new StringBuilder();
        if (r.downscaled()) {
            tip.append(String.format(Locale.US, "Reduced from %d × %d to fit the size limit.",
                    r.requestedWidth, r.requestedHeight));
        }
        if (r.quality < 60) {
            if (tip.length() > 0) {
                tip.append(' ');
            }
            tip.append(r.webp ? "For a sharper result, raise the size limit or use fewer pixels."
                    : "For a sharper result, choose WEBP, raise the size limit, or use fewer pixels.");
        }
        resultFooter.setText(tip);
        resultFooter.setVisibility(tip.length() > 0 ? View.VISIBLE : View.GONE);
    }

    // ================================================================ settings & lock

    private void loadPrefs() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        restoring = true;
        widthIn.setText(sp.getString("width", "1000"));
        heightIn.setText(sp.getString("height", "1000"));
        maxKbIn.setText(sp.getString("maxKb", "100"));
        keepAspect.setChecked(sp.getBoolean("keepAspect", true));
        neverReduce.setChecked(sp.getBoolean("strict", false));
        format.select(sp.getBoolean("webp", false) ? 1 : 0);
        boolean locked = sp.getBoolean("locked", false) && readOptions(false) != null;
        lockSwitch.setChecked(locked);
        restoring = false;
        updateLockFooter();

        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (!restoring) {
                    refresh();
                }
            }
        };
        widthIn.addTextChangedListener(watcher);
        heightIn.addTextChangedListener(watcher);
    }

    private void savePrefs() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("width", widthIn.getText().toString().trim())
                .putString("height", heightIn.getText().toString().trim())
                .putString("maxKb", maxKbIn.getText().toString().trim())
                .putBoolean("keepAspect", keepAspect.isChecked())
                .putBoolean("strict", neverReduce.isChecked())
                .putBoolean("webp", format.selected() == 1)
                .putBoolean("locked", lockSwitch.isChecked())
                .apply();
    }

    private void onLockToggled(boolean checked) {
        if (checked && readOptions(true) == null) {
            restoring = true;
            lockSwitch.setChecked(false);
            restoring = false;
            return;
        }
        hideKeyboard();
        savePrefs();
        updateLockFooter();
        refresh();
    }

    private void updateLockFooter() {
        if (lockSwitch.isChecked()) {
            Shrinker.Options o = readOptions(false);
            lockFooter.setText((o != null ? describe(o) + ". " : "")
                    + "Every photo you choose converts automatically. Turn off to change.");
        } else {
            lockFooter.setText("Keep these settings for every photo until you turn this off.");
        }
    }

    private String describe(Shrinker.Options o) {
        StringBuilder sb = new StringBuilder();
        if (o.width > 0 && o.height > 0) {
            sb.append(o.width).append(" × ").append(o.height).append(" px");
            if (o.keepAspect) {
                sb.append(" (fit)");
            }
        } else if (o.width > 0) {
            sb.append(o.width).append(" px wide");
        } else if (o.height > 0) {
            sb.append(o.height).append(" px tall");
        } else {
            sb.append("Original pixels");
        }
        sb.append(", max ").append(Shrinker.formatKb(o.maxKb)).append(" KB, ").append(o.webp ? "WEBP" : "JPG");
        return sb.toString();
    }

    private Integer readDimension(EditText e, boolean showErrors) {
        String s = e.getText().toString().trim();
        if (s.isEmpty()) {
            return 0;
        }
        try {
            int v = Integer.parseInt(s);
            if (v >= Shrinker.MIN_DIMENSION && v <= Shrinker.MAX_DIMENSION) {
                return v;
            }
        } catch (NumberFormatException ignored) {
            // fall through to the error below
        }
        if (showErrors) {
            e.setError(Shrinker.MIN_DIMENSION + "–" + Shrinker.MAX_DIMENSION);
        }
        return null;
    }

    private Shrinker.Options readOptions(boolean showErrors) {
        Integer w = readDimension(widthIn, showErrors);
        Integer h = readDimension(heightIn, showErrors);
        Double kb = null;
        try {
            double v = Double.parseDouble(maxKbIn.getText().toString().trim());
            if (v >= 1 && v <= 100000) {
                kb = v;
            }
        } catch (NumberFormatException ignored) {
            // handled below
        }
        if (kb == null && showErrors) {
            maxKbIn.setError("1–100000");
        }
        if (w == null || h == null || kb == null) {
            return null;
        }
        Shrinker.Options o = new Shrinker.Options();
        o.width = w;
        o.height = h;
        o.keepAspect = keepAspect.isChecked();
        o.strictResolution = neverReduce.isChecked();
        o.maxKb = kb;
        o.webp = format.selected() == 1;
        o.background = Color.WHITE;
        return o;
    }

    private void hideKeyboard() {
        View f = getCurrentFocus();
        if (f != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
            }
            f.clearFocus();
        }
    }

    // ================================================================ pick & load

    private void pickImage() {
        if (busy) {
            return;
        }
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, "Choose Photo"), REQ_PICK);
        } catch (ActivityNotFoundException e) {
            toast("No gallery or file app found");
        }
    }

    /** Loads now, or after the running job finishes so it never overwrites a file in use. */
    private void requestLoad(Uri uri) {
        if (busy) {
            pendingUri = uri;
            return;
        }
        loadSource(uri);
    }

    private void loadSource(final Uri uri) {
        hideKeyboard();
        setBusy(true, "Opening photo…");
        final File previous = sourceFile;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final File dest = new File(getCacheDir(), "src-" + System.nanoTime() + ".img");
                final String name = displayName(uri);
                String error = null;
                long bytes = 0;
                Bitmap thumb = null;
                int[] dims = null;
                try (InputStream in = getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(dest)) {
                    if (in == null) {
                        throw new IOException("the file could not be opened");
                    }
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        bytes += n;
                    }
                } catch (IOException | SecurityException e) {
                    Log.e(TAG, "load failed", e);
                    error = "Couldn't open this photo: " + e.getMessage();
                }
                if (error == null) {
                    dims = Shrinker.orientedBounds(dest);
                    if (dims == null) {
                        error = "This file isn't a supported image.";
                    } else {
                        thumb = decodePreview(dest);
                    }
                }
                if (error != null && !dest.delete()) {
                    Log.w(TAG, "could not delete " + dest);
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
                        if (err != null) {
                            setBusy(false, null);
                            showError(err);
                            return;
                        }
                        if (previous != null && !previous.equals(dest) && !previous.delete()) {
                            Log.w(TAG, "could not delete " + previous);
                        }
                        sourceFile = dest;
                        sourceName = name;
                        sourceBytes = size;
                        sourceDims = d;
                        result = null;
                        resultFile = null;
                        setPreview(t);
                        showOriginal();
                        setBusy(false, null);
                        if (lockSwitch.isChecked() && pendingUri == null) {
                            convert();
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
        return sanitizeStem(name);
    }

    /** File-system and share-safe stem: no extension, no leading dots, bounded length. */
    static String sanitizeStem(String name) {
        if (name == null) {
            return "photo";
        }
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        stem = stem.replaceAll("[^A-Za-z0-9._ -]", "_").trim().replaceAll("^[.\\s]+", "");
        if (stem.length() > MAX_STEM) {
            stem = stem.substring(0, MAX_STEM).trim();
        }
        return stem.isEmpty() ? "photo" : stem;
    }

    private Bitmap decodePreview(File f) {
        BitmapFactory.Options b = new BitmapFactory.Options();
        b.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), b);
        if (b.outWidth <= 0 || b.outHeight <= 0) {
            return null;
        }
        int s = 1;
        while (b.outWidth / (s * 2) >= PREVIEW_MAX || b.outHeight / (s * 2) >= PREVIEW_MAX) {
            s *= 2;
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = s;
        try {
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    // ================================================================ convert

    private void convert() {
        if (busy || sourceFile == null) {
            return;
        }
        final Shrinker.Options o = readOptions(true);
        if (o == null) {
            return;
        }
        hideKeyboard();
        savePrefs();
        updateLockFooter();
        result = null;
        resultFile = null;
        setBusy(true, "Converting…");
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
                                        progressText.setText(message);
                                    }
                                }
                            });
                        }
                    });
                    out = writeResult(r, stem);
                    thumb = decodePreview(out);
                } catch (Shrinker.ShrinkException e) {
                    error = e.getMessage();
                } catch (IOException e) {
                    Log.e(TAG, "write failed", e);
                    error = "Couldn't save the result: " + e.getMessage();
                } catch (OutOfMemoryError e) {
                    error = "Not enough memory. Try fewer pixels.";
                } catch (RuntimeException e) {
                    Log.e(TAG, "convert failed", e);
                    error = "Conversion failed. Please try another photo.";
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
                        if (err != null) {
                            setBusy(false, null);
                            showError(err);
                            return;
                        }
                        result = fr;
                        resultFile = fo;
                        if (t != null) {
                            setPreview(t);
                        }
                        showResult();
                        setBusy(false, null);
                    }
                });
            }
        }).start();
    }

    /** Writes into cache/out with a unique name; keeps recent files so earlier shares stay readable. */
    private File writeResult(Shrinker.Result r, String stem) throws IOException {
        File dir = new File(getCacheDir(), ShareProvider.OUT_DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("can't create " + dir);
        }
        String base = stem + "_" + r.width + "x" + r.height;
        File out = new File(dir, base + r.extension());
        for (int i = 2; out.exists(); i++) {
            out = new File(dir, base + "-" + i + r.extension());
        }
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(r.data);
        }
        pruneResults(dir);
        return out;
    }

    private static void pruneResults(File dir) {
        File[] files = dir.listFiles();
        if (files == null || files.length <= KEEP_RESULTS) {
            return;
        }
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        for (int i = KEEP_RESULTS; i < files.length; i++) {
            if (!files[i].delete()) {
                Log.w(TAG, "could not delete " + files[i]);
            }
        }
    }

    // ================================================================ save & share

    private void save() {
        if (busy || result == null || resultFile == null) {
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
                toast("No file manager available. Use Share instead.");
            }
        }
    }

    /** API 29+: write straight into Pictures/PhotoShrink through MediaStore, no permission needed. */
    private void saveToGallery(final Shrinker.Result r, final String name) {
        setBusy(true, "Saving…");
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
                        throw new IOException("the gallery refused the file");
                    }
                    try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                        if (out == null) {
                            throw new IOException("can't open the gallery file");
                        }
                        out.write(r.data);
                    }
                    ContentValues done = new ContentValues();
                    done.put("is_pending", 0);
                    getContentResolver().update(uri, done, null, null);
                    msg = "Saved to Gallery";
                } catch (IOException | RuntimeException e) {
                    Log.e(TAG, "gallery save failed", e);
                    if (uri != null) {
                        try {
                            getContentResolver().delete(uri, null, null);
                        } catch (RuntimeException cleanup) {
                            Log.w(TAG, "cleanup failed", cleanup);
                        }
                    }
                    msg = "Couldn't save: " + e.getMessage();
                }
                final String m = msg;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!destroyed) {
                            setBusy(false, null);
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
                throw new IOException("can't open the destination");
            }
            out.write(result.data);
            toast("Saved");
        } catch (IOException | SecurityException e) {
            Log.e(TAG, "save failed", e);
            toast("Couldn't save: " + e.getMessage());
        }
    }

    private void share() {
        if (busy || result == null || resultFile == null || !resultFile.isFile()) {
            return;
        }
        Uri uri = ShareProvider.uriFor(resultFile);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(result.mimeType());
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.setClipData(ClipData.newRawUri(resultFile.getName(), uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(send, "Share Photo"));
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
            requestLoad(data.getData());
        } else if (requestCode == REQ_SAVE) {
            writeTo(data.getData());
        }
    }

    // ================================================================ util

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private static byte[] readFile(File f) throws IOException {
        long len = f.length();
        if (len > Integer.MAX_VALUE) {
            throw new IOException("file too large");
        }
        byte[] data = new byte[(int) len];
        try (InputStream in = new FileInputStream(f)) {
            int off = 0;
            while (off < data.length) {
                int n = in.read(data, off, data.length - off);
                if (n < 0) {
                    throw new IOException("unexpected end of file");
                }
                off += n;
            }
        }
        return data;
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
