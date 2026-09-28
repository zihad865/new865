package com.vx.photoshrink;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** iOS-style (Human Interface Guidelines) palette and widgets built from platform views. */
final class Ui {

    final Context ctx;
    final boolean dark;

    final int background;
    final int card;
    final int separator;
    final int label;
    final int secondary;
    final int tertiary;
    final int blue;
    final int green;
    final int red;
    final int orange;
    final int fill;
    final int segmentSelected;
    final int tintedFill;

    static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);

    Ui(Context ctx) {
        this.ctx = ctx;
        int night = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        dark = night == Configuration.UI_MODE_NIGHT_YES;
        if (dark) {
            background = Color.BLACK;
            card = Color.rgb(28, 28, 30);
            separator = Color.rgb(56, 56, 58);
            label = Color.WHITE;
            secondary = Color.argb(153, 235, 235, 245);
            tertiary = Color.argb(77, 235, 235, 245);
            blue = Color.rgb(10, 132, 255);
            green = Color.rgb(48, 209, 88);
            red = Color.rgb(255, 69, 58);
            orange = Color.rgb(255, 159, 10);
            fill = Color.argb(61, 118, 118, 128);
            segmentSelected = Color.rgb(99, 99, 102);
            tintedFill = Color.argb(46, 10, 132, 255);
        } else {
            background = Color.rgb(242, 242, 247);
            card = Color.WHITE;
            separator = Color.rgb(198, 198, 200);
            label = Color.BLACK;
            secondary = Color.argb(153, 60, 60, 67);
            tertiary = Color.argb(77, 60, 60, 67);
            blue = Color.rgb(0, 122, 255);
            green = Color.rgb(52, 199, 89);
            red = Color.rgb(255, 59, 48);
            orange = Color.rgb(255, 149, 0);
            fill = Color.argb(31, 118, 118, 128);
            segmentSelected = Color.WHITE;
            tintedFill = Color.argb(31, 0, 122, 255);
        }
    }

    int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                ctx.getResources().getDisplayMetrics()));
    }

    GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private Drawable pressable(Drawable content, int rippleColor) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, rounded(Color.WHITE, 12));
    }

    /** Grey fill with a touch ripple, for tappable content areas such as the photo well. */
    Drawable pressableFill() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        return new RippleDrawable(ColorStateList.valueOf(tintedFill), g, null);
    }

    TextView text(String s, float sp, int color, Typeface face) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(face);
        t.setIncludeFontPadding(true);
        return t;
    }

    LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    LinearLayout.LayoutParams margins(LinearLayout.LayoutParams lp, int top, int bottom) {
        lp.topMargin = dp(top);
        lp.bottomMargin = dp(bottom);
        return lp;
    }

    /** Grouped inset section: optional uppercase header, white rounded card, optional footer. */
    LinearLayout section(LinearLayout parent, String header, String footer) {
        if (header != null) {
            TextView h = text(header.toUpperCase(java.util.Locale.US), 13, secondary, REGULAR);
            h.setLetterSpacing(0.02f);
            h.setPadding(dp(16), dp(0), dp(16), dp(7));
            parent.addView(h, margins(matchWrap(), 22, 0));
        }
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(rounded(card, 12));
        c.setClipToOutline(true);
        parent.addView(c, margins(matchWrap(), header == null ? 22 : 0, 0));
        if (footer != null) {
            TextView f = footer(footer);
            parent.addView(f, matchWrap());
            c.setTag(f);
        }
        return c;
    }

    TextView footer(String s) {
        TextView f = text(s, 13, secondary, REGULAR);
        f.setPadding(dp(16), dp(7), dp(16), 0);
        f.setLineSpacing(0, 1.1f);
        return f;
    }

    void addSeparator(LinearLayout cardView) {
        View v = new View(ctx);
        v.setBackgroundColor(separator);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(0.5f)));
        lp.leftMargin = dp(16);
        cardView.addView(v, lp);
    }

    /** A 44pt-min row: title on the left, accessory view on the right. */
    LinearLayout row(LinearLayout cardView, String title, View accessory) {
        if (cardView.getChildCount() > 0) {
            addSeparator(cardView);
        }
        LinearLayout r = new LinearLayout(ctx);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(48));
        r.setPadding(dp(16), dp(4), dp(12), dp(4));
        TextView t = text(title, 17, label, REGULAR);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (accessory != null) {
            r.addView(accessory);
        }
        cardView.addView(r, matchWrap());
        return r;
    }

    /** Right-aligned borderless number field with an optional unit suffix, like iOS Settings. */
    LinearLayout valueField(EditText field, String unit) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        field.setBackground(null);
        field.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        field.setTextColor(label);
        field.setHintTextColor(tertiary);
        field.setSingleLine(true);
        field.setPadding(dp(8), dp(8), dp(4), dp(8));
        field.setMinWidth(dp(96));
        field.setSelectAllOnFocus(true);
        box.addView(field);
        if (unit != null) {
            box.addView(text(unit, 17, secondary, REGULAR));
        }
        return box;
    }

    EditText numberField(String hint, boolean decimal) {
        EditText e = new EditText(ctx);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | (decimal ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
        return e;
    }

    /** iOS toggle: 51×31 pill track, green when on, white knob. */
    Switch toggle() {
        Switch s = new Switch(ctx);
        s.setShowText(false);
        s.setText("");
        s.setSwitchMinWidth(dp(51));

        GradientDrawable knob = new GradientDrawable();
        knob.setShape(GradientDrawable.OVAL);
        knob.setColor(Color.WHITE);
        knob.setSize(dp(27), dp(27));
        knob.setStroke(Math.max(1, dp(0.5f)), Color.argb(30, 0, 0, 0));
        s.setThumbDrawable(new InsetDrawable(knob, dp(2)));

        StateListDrawable track = new StateListDrawable();
        GradientDrawable on = rounded(green, 16);
        on.setSize(dp(51), dp(31));
        GradientDrawable off = rounded(dark ? Color.rgb(57, 57, 61) : Color.rgb(233, 233, 234), 16);
        off.setSize(dp(51), dp(31));
        track.addState(new int[]{android.R.attr.state_checked}, on);
        track.addState(new int[]{}, off);
        s.setTrackDrawable(track);
        return s;
    }

    /** Filled blue button (iOS "filled" style). */
    Button filledButton(String s) {
        Button b = new Button(ctx);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        b.setTypeface(MEDIUM);
        b.setTextColor(Color.WHITE);
        b.setStateListAnimator(null);
        b.setMinHeight(dp(50));
        b.setBackground(pressable(rounded(blue, 12), Color.argb(70, 255, 255, 255)));
        return b;
    }

    /** Tinted button (iOS "tinted" style): light blue fill, blue text. */
    Button tintedButton(String s) {
        Button b = new Button(ctx);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        b.setTypeface(MEDIUM);
        b.setTextColor(blue);
        b.setStateListAnimator(null);
        b.setMinHeight(dp(50));
        b.setBackground(pressable(rounded(tintedFill, 12), Color.argb(50, 0, 122, 255)));
        return b;
    }

    /** Segmented control with equally sized segments. */
    static final class Segmented {
        final LinearLayout view;
        private final TextView[] items;
        private final Ui ui;
        private int selected;
        private Runnable listener;

        Segmented(Ui ui, String... labels) {
            this.ui = ui;
            view = new LinearLayout(ui.ctx);
            view.setOrientation(LinearLayout.HORIZONTAL);
            view.setBackground(ui.rounded(ui.fill, 9));
            view.setPadding(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2));
            items = new TextView[labels.length];
            for (int i = 0; i < labels.length; i++) {
                final int index = i;
                TextView t = ui.text(labels[i], 13, ui.label, MEDIUM);
                t.setGravity(Gravity.CENTER);
                t.setPadding(ui.dp(14), ui.dp(6), ui.dp(14), ui.dp(6));
                t.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (view.isEnabled()) {
                            select(index);
                            if (listener != null) {
                                listener.run();
                            }
                        }
                    }
                });
                items[i] = t;
                view.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            }
            select(0);
        }

        void select(int index) {
            selected = index;
            for (int i = 0; i < items.length; i++) {
                items[i].setBackground(i == index ? ui.rounded(ui.segmentSelected, 7) : null);
                items[i].setElevation(i == index ? ui.dp(1) : 0);
                items[i].setTypeface(i == index ? BOLD : MEDIUM);
            }
        }

        int selected() {
            return selected;
        }

        void setEnabled(boolean enabled) {
            view.setEnabled(enabled);
            view.setAlpha(enabled ? 1f : 0.45f);
            for (TextView t : items) {
                t.setEnabled(enabled);
            }
        }

        void setOnChange(Runnable r) {
            listener = r;
        }
    }
}
