package com.celiocastro.h8tvcenter;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(10, 12, 16);
    private static final int PANEL = Color.rgb(25, 29, 37);
    private static final int ACCENT = Color.rgb(229, 9, 20);
    private LinearLayout root;
    private LinearLayout content;

    private final String[][] systems = {
        {"ATARI 2600", "Clássicos"},
        {"NES", "Nintendo"},
        {"MASTER SYSTEM", "Sega"},
        {"MEGA DRIVE", "Sega"},
        {"GAME BOY", "Nintendo"},
        {"GAME BOY COLOR", "Nintendo"},
        {"GAME BOY ADVANCE", "Nintendo"},
        {"SUPER NINTENDO", "Nintendo"},
        {"ARCADE", "Arcade"},
        {"PLAYSTATION", "Sony"},
        {"NINTENDO 64", "Experimental"},
        {"NINTENDO DS", "Experimental"},
        {"DREAMCAST", "Experimental"},
        {"PSP", "Experimental"},
        {"SEGA SATURN", "Experimental"},
        {"NATIVE32", "Formato especial"}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
        buildHome();
    }

    private void buildHome() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(34), dp(22), dp(34), dp(22));

        TextView brand = new TextView(this);
        brand.setText("H8TV  /  EMULATION CENTER");
        brand.setTextColor(Color.WHITE);
        brand.setTextSize(24);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(brand);

        TextView subtitle = new TextView(this);
        subtitle.setText("SUA BIBLIOTECA RETRÔ EM UM SÓ LUGAR");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(11);
        subtitle.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.bottomMargin = dp(20);
        root.addView(subtitle, subParams);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        addSection("CONSOLES");
        LinearLayout row = null;
        for (int i = 0; i < systems.length; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
                rowParams.bottomMargin = dp(10);
                content.addView(row, rowParams);
            }
            row.addView(createSystemCard(systems[i][0], systems[i][1]), new LinearLayout.LayoutParams(0, dp(104), 1));
        }

        TextView footer = new TextView(this);
        footer.setText("PROTÓTIPO 0.1.0  •  MAIS PLATAFORMAS E EMULADORES SERÃO ADICIONADOS POR ETAPAS");
        footer.setTextColor(Color.GRAY);
        footer.setTextSize(10);
        footer.setPadding(0, dp(16), 0, 0);
        root.addView(footer);

        setContentView(root);
    }

    private void addSection(String title) {
        TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(ACCENT);
        label.setTextSize(12);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setPadding(0, 0, 0, dp(10));
        content.addView(label);
    }

    private View createSystemCard(String name, String category) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setBackground(background(PANEL, dp(12), Color.rgb(54, 60, 72), dp(1)));
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) card.getLayoutParams();
        card.setPadding(dp(6), dp(8), dp(6), dp(8));

        TextView icon = new TextView(this);
        icon.setText("🎮");
        icon.setTextSize(25);
        card.addView(icon);

        TextView title = new TextView(this);
        title.setText(name);
        title.setTextColor(Color.WHITE);
        title.setTextSize(12);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(1);
        card.addView(title);

        TextView sub = new TextView(this);
        sub.setText(category);
        sub.setTextColor(Color.LTGRAY);
        sub.setTextSize(10);
        card.addView(sub);

        card.setOnFocusChangeListener((v, focused) ->
            v.setBackground(background(focused ? ACCENT : PANEL, dp(12),
                focused ? Color.WHITE : Color.rgb(54, 60, 72), focused ? dp(2) : dp(1)))
        );
        card.setOnClickListener(v ->
            Toast.makeText(this, name + ": biblioteca será habilitada na próxima etapa.", Toast.LENGTH_SHORT).show()
        );
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, dp(104), 1);
        cardParams.setMargins(dp(4), dp(2), dp(4), dp(2));
        card.setLayoutParams(cardParams);
        return card;
    }

    private GradientDrawable background(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return super.onKeyDown(keyCode, event);
        }
        return super.onKeyDown(keyCode, event);
    }
}
