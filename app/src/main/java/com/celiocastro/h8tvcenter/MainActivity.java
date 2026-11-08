package com.celiocastro.h8tvcenter;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
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
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, dp(104), 1);
            cardParams.setMargins(dp(4), dp(2), dp(4), dp(2));
            row.addView(createSystemCard(systems[i][0], systems[i][1]), cardParams);
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
        card.setPadding(dp(6), dp(8), dp(6), dp(8));

        SystemIconView icon = new SystemIconView(this, systemIndex(name));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(62), dp(48));
        iconParams.bottomMargin = dp(5);
        card.addView(icon, iconParams);

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
        return card;
    }

    private int systemIndex(String name) {
        for (int i = 0; i < systems.length; i++) if (systems[i][0].equals(name)) return i;
        return 0;
    }

    // Ícones vetoriais individuais desenhados no próprio APK.
    private class SystemIconView extends View {
        private final int type;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int light = Color.rgb(225,230,238), mid = Color.rgb(105,115,130), red = Color.rgb(229,9,20);
        SystemIconView(android.content.Context context, int type) { super(context); this.type = type; }
        private void box(Canvas c,float l,float t,float r,float b,float rad,int color) { p.setStyle(Paint.Style.FILL); p.setColor(color); c.drawRoundRect(new RectF(l,t,r,b),rad,rad,p); }
        private void dot(Canvas c,float x,float y,float rad,int color) { p.setStyle(Paint.Style.FILL); p.setColor(color); c.drawCircle(x,y,rad,p); }
        private void line(Canvas c,float x,float y,float xx,float yy,int color,float w) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(w); p.setStrokeCap(Paint.Cap.ROUND); p.setColor(color); c.drawLine(x,y,xx,yy,p); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); c.save(); c.scale(getWidth()/100f,getHeight()/76f);
            switch(type) {
                case 0: box(c,39,8,61,43,5,mid); box(c,46,13,54,32,4,red); line(c,50,43,50,53,light,4); box(c,31,52,69,63,5,light); dot(c,63,57,3,red); break;
                case 1: box(c,15,23,85,52,5,light); box(c,22,29,78,46,3,mid); line(c,35,32,35,43,Color.BLACK,3); line(c,29,37,41,37,Color.BLACK,3); dot(c,65,35,3,red); dot(c,73,40,3,red); break;
                case 2: box(c,16,20,84,53,7,mid); box(c,23,26,77,47,4,light); line(c,34,32,34,42,Color.DKGRAY,3); line(c,29,37,39,37,Color.DKGRAY,3); dot(c,66,36,4,red); dot(c,74,36,4,red); break;
                case 3: box(c,12,23,88,52,10,mid); box(c,22,27,78,48,8,light); dot(c,69,35,5,red); dot(c,78,40,4,red); dot(c,28,38,6,mid); break;
                case 4: case 5: case 6: box(c,31,3,69,72,6,light); box(c,36,9,64,35,2,mid); box(c,39,13,61,31,1,Color.rgb(91,128,105)); dot(c,43,49,4,mid); dot(c,57,49,4,red); dot(c,62,59,2,mid); dot(c,70,62,2,mid); break;
                case 7: box(c,12,24,88,53,13,light); dot(c,30,38,8,mid); line(c,30,30,30,46,Color.DKGRAY,3); line(c,22,38,38,38,Color.DKGRAY,3); dot(c,66,34,4,red); dot(c,76,42,4,mid); break;
                case 8: box(c,29,3,71,71,5,mid); box(c,34,9,66,31,2,Color.BLACK); box(c,35,35,65,42,2,red); dot(c,43,50,3,light); dot(c,57,50,3,light); box(c,38,60,62,66,2,Color.BLACK); break;
                case 9: box(c,13,25,87,55,14,light); dot(c,28,39,7,mid); line(c,28,31,28,47,Color.DKGRAY,3); line(c,20,39,36,39,Color.DKGRAY,3); dot(c,68,34,3,red); dot(c,77,42,3,mid); dot(c,59,43,3,mid); break;
                case 10: box(c,12,24,88,55,13,light); box(c,43,28,57,49,5,mid); dot(c,28,39,7,mid); dot(c,50,38,5,red); dot(c,72,37,5,mid); break;
                case 11: box(c,25,3,75,72,6,light); box(c,30,8,70,33,2,mid); box(c,30,39,70,66,2,mid); dot(c,36,53,3,red); dot(c,64,53,3,red); break;
                case 12: box(c,16,24,84,53,9,light); box(c,28,29,72,47,4,mid); box(c,42,32,58,43,2,Color.BLACK); dot(c,77,39,3,red); break;
                case 13: box(c,19,12,81,65,10,light); box(c,33,18,67,46,3,mid); box(c,38,22,62,42,1,Color.rgb(45,65,85)); dot(c,26,39,5,mid); dot(c,73,33,3,red); dot(c,73,44,3,mid); break;
                case 14: box(c,14,20,86,54,8,mid); box(c,22,25,78,48,5,light); dot(c,50,36,8,red); line(c,30,45,70,45,Color.DKGRAY,2); break;
                default: box(c,30,8,70,65,5,light); box(c,37,16,63,48,2,mid); dot(c,50,31,6,red); line(c,40,55,60,55,red,3); line(c,44,61,56,61,red,2); break;
            } c.restore();
        }
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
