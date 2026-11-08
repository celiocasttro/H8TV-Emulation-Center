package com.celiocastro.h8tvcenter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(10, 12, 16);
    private static final int PANEL = Color.rgb(25, 29, 37);
    private static final int ACCENT = Color.rgb(229, 9, 20);
    private static final int PICK_FOLDER = 7101;
    private static final String PREFS = "h8tv_library";
    private static final String PREF_TREE = "rom_tree_uri";
    private static final String PREF_FAVORITES = "favorites";
    private static final String PREF_HISTORY = "history";

    private final String[][] systems = {
        {"ATARI 2600", "Atari 2600", "a26|bin"},
        {"NES", "Nintendo Entertainment System", "nes|unf|unif"},
        {"MASTER SYSTEM", "Sega Master System", "sms"},
        {"MEGA DRIVE", "Sega Mega Drive", "md|gen|bin"},
        {"GAME BOY", "Game Boy", "gb"},
        {"GAME BOY COLOR", "Game Boy Color", "gbc"},
        {"GAME BOY ADVANCE", "Game Boy Advance", "gba"},
        {"SUPER NINTENDO", "Super Nintendo", "sfc|smc"},
        {"ARCADE", "Arcade", "zip|7z"},
        {"PLAYSTATION", "PlayStation", "cue|iso|chd|pbp"},
        {"NINTENDO 64", "Nintendo 64", "n64|z64|v64"},
        {"NINTENDO DS", "Nintendo DS", "nds"},
        {"DREAMCAST", "Dreamcast", "cdi|gdi|chd"},
        {"PSP", "PlayStation Portable", "iso|cso"},
        {"SEGA SATURN", "Sega Saturn", "cue|iso|chd"},
        {"NATIVE32", "Native32", "smf|sgm|ssl"}
    };

    private final List<GameFile> allGames = new ArrayList<>();
    private LinearLayout root;
    private LinearLayout body;
    private String selectedSystem = null;
    private String searchTerm = "";
    private boolean favoritesOnly = false;
    private SharedPreferences prefs;

    private static class GameFile {
        String name, path, uri, system;
        long size;
        GameFile(String name, String path, String uri, String system, long size) {
            this.name = name; this.path = path; this.uri = uri; this.system = system; this.size = size;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersive();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        showHome();
        if (savedTree() != null) scanLibrary();
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label); b.setTextColor(Color.WHITE);
        b.setAllCaps(false); b.setTextSize(13);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL));
        b.setOnClickListener(v -> action.run());
        b.setFocusable(true);
        return b;
    }

    private void base(String subtitle) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(30), dp(18), dp(30), dp(18));
        TextView brand = text("H8TV  /  EMULATION CENTER", 23, Color.WHITE, true);
        root.addView(brand);
        TextView sub = text(subtitle, 11, Color.LTGRAY, false);
        sub.setPadding(0, dp(3), 0, dp(14));
        root.addView(sub);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void addBar(String title, Runnable back) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text(title, 15, ACCENT, true);
        bar.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        if (back != null) bar.addView(button("← Voltar", back));
        body.addView(bar);
        space(body, 8);
    }

    private void space(LinearLayout parent, int h) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(1, dp(h)));
    }

    private void showHome() {
        selectedSystem = null; favoritesOnly = false;
        base("BIBLIOTECA RETRÔ • CONTROLE REMOTO • ROMS LOCAIS E USB");
        LinearLayout actions = new LinearLayout(this);
        actions.addView(button("＋ Selecionar pasta de ROMs", this::pickFolder));
        actions.addView(button("↻ Atualizar biblioteca", this::scanLibrary));
        actions.addView(button("★ Favoritos", () -> { favoritesOnly = true; showGames(null); }));
        actions.addView(button("Histórico", this::showHistory));
        body.addView(actions);
        space(body, 12);
        Uri tree = savedTree();
        body.addView(text(tree == null ? "Nenhuma pasta selecionada." :
            "Pasta de ROMs vinculada. Se adicionou jogos, toque em Atualizar biblioteca.", 12, Color.LTGRAY, false));
        space(body, 12);
        addBar("CONSOLES", null);
        for (int i = 0; i < systems.length; i += 4) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = i; j < Math.min(i + 4, systems.length); j++) {
                final String sys = systems[j][0];
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setGravity(Gravity.CENTER);
                card.setFocusable(true);
                card.setPadding(dp(4), dp(12), dp(4), dp(12));
                card.setBackground(round(PANEL));
                TextView icon = text(iconFor(sys), 27, Color.WHITE, true);
                card.addView(icon);
                TextView name = text(sys, 11, Color.WHITE, true);
                name.setGravity(Gravity.CENTER);
                card.addView(name);
                card.setOnFocusChangeListener((v, f) -> v.setBackground(round(f ? ACCENT : PANEL)));
                card.setOnClickListener(v -> { selectedSystem = sys; favoritesOnly = false; if (allGames.isEmpty()) scanLibrary(); else showGames(sys); });
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(88), 1);
                cp.setMargins(dp(4), dp(3), dp(4), dp(3));
                row.addView(card, cp);
            }
            body.addView(row);
        }
        space(body, 10);
        body.addView(text("Selecione um console, abra um jogo e use o botão MENU durante a execução para acessar saves, cheats e ajustes.", 11, Color.GRAY, false));
    }

    private android.graphics.drawable.GradientDrawable round(int color) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(12));
        d.setStroke(dp(1), Color.rgb(58, 64, 75)); return d;
    }

    private String iconFor(String system) {
        if (system.equals("ATARI 2600")) return "🕹";
        if (system.equals("NES") || system.equals("SUPER NINTENDO")) return "🎮";
        if (system.contains("GAME BOY") || system.equals("PSP") || system.equals("NINTENDO DS")) return "▣";
        if (system.equals("ARCADE")) return "👾";
        if (system.equals("PLAYSTATION") || system.equals("NINTENDO 64") || system.equals("DREAMCAST")) return "🎯";
        return "◈";
    }

    private Uri savedTree() {
        String raw = prefs.getString(PREF_TREE, null);
        return raw == null ? null : Uri.parse(raw);
    }

    private void pickFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, PICK_FOLDER);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FOLDER && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri tree = data.getData();
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try { getContentResolver().takePersistableUriPermission(tree, flags); } catch (Exception ignored) {}
            prefs.edit().putString(PREF_TREE, tree.toString()).apply();
            Toast.makeText(this, "Pasta vinculada. Lendo jogos...", Toast.LENGTH_SHORT).show();
            scanLibrary();
        }
    }

    private void scanLibrary() {
        Uri tree = savedTree();
        if (tree == null) {
            new AlertDialog.Builder(this).setMessage("Selecione primeiro a pasta onde estão suas ROMs.")
                .setPositiveButton("Selecionar pasta", (d,w) -> pickFolder()).setNegativeButton("Cancelar", null).show();
            return;
        }
        allGames.clear();
        try {
            String rootId = DocumentsContract.getTreeDocumentId(tree);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId);
            scanDirectory(tree, children, "");
            Collections.sort(allGames, (a,b) -> a.name.compareToIgnoreCase(b.name));
            Toast.makeText(this, allGames.size() + " arquivo(s) de jogo encontrado(s).", Toast.LENGTH_LONG).show();
            if (selectedSystem != null || favoritesOnly) showGames(selectedSystem); else showHome();
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível ler a pasta: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void scanDirectory(Uri tree, Uri childrenUri, String parent) {
        String[] projection = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE};
        try (Cursor c = getContentResolver().query(childrenUri, projection, null, null, null)) {
            if (c == null) return;
            int idCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
            int sizeCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE);
            while (c.moveToNext()) {
                String id = c.getString(idCol);
                String name = c.getString(nameCol);
                String mime = c.getString(mimeCol);
                Uri doc = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    Uri nested = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id);
                    scanDirectory(tree, nested, parent + name + "/");
                } else if (name != null) {
                    String system = systemFor(name, parent);
                    if (system != null) {
                        long size = sizeCol >= 0 && !c.isNull(sizeCol) ? c.getLong(sizeCol) : 0;
                        allGames.add(new GameFile(name, parent + name, doc.toString(), system, size));
                    }
                }
            }
        } catch (Exception ignored) { }
    }

    private String systemFor(String filename, String folderPath) {
        String path = (folderPath + filename).toLowerCase(Locale.ROOT);
        if (path.contains("native32")) return "NATIVE32";
        if (path.contains("atari2600") || path.contains("atari 2600") || path.contains("/atari/")) return "ATARI 2600";
        if (path.contains("megadrive") || path.contains("mega drive") || path.contains("genesis")) return "MEGA DRIVE";
        if (path.contains("mastersystem") || path.contains("master system")) return "MASTER SYSTEM";
        if (path.contains("gameboyadvance") || path.contains("game boy advance") || path.contains("/gba/")) return "GAME BOY ADVANCE";
        if (path.contains("gameboycolor") || path.contains("game boy color") || path.contains("/gbc/")) return "GAME BOY COLOR";
        if (path.contains("gameboy") || path.contains("game boy") || path.contains("/gb/")) return "GAME BOY";
        if (path.contains("supernintendo") || path.contains("super nintendo") || path.contains("/snes/")) return "SUPER NINTENDO";
        if (path.contains("playstation") || path.contains("/ps1/") || path.contains("/psx/")) return "PLAYSTATION";
        if (path.contains("dreamcast")) return "DREAMCAST";
        if (path.contains("saturn")) return "SEGA SATURN";
        if (path.contains("psp")) return "PSP";
        if (path.contains("nintendo ds") || path.contains("/nds/") || path.contains("nintendods")) return "NINTENDO DS";
        if (path.contains("nintendo 64") || path.contains("/n64/") || path.contains("nintendo64")) return "NINTENDO 64";
        if (path.contains("arcade") || path.contains("mame") || path.contains("fbneo")) return "ARCADE";
        if (path.contains("/nes/") || path.contains("nintendo entertainment")) return "NES";
        String lower = filename.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot < 0 || dot == lower.length()-1) return null;
        String ext = lower.substring(dot+1);
        if (ext.equals("a26")) return "ATARI 2600";
        if (ext.equals("nes") || ext.equals("unf") || ext.equals("unif")) return "NES";
        if (ext.equals("sms")) return "MASTER SYSTEM";
        if (ext.equals("md") || ext.equals("gen")) return "MEGA DRIVE";
        if (ext.equals("gb")) return "GAME BOY";
        if (ext.equals("gbc")) return "GAME BOY COLOR";
        if (ext.equals("gba")) return "GAME BOY ADVANCE";
        if (ext.equals("sfc") || ext.equals("smc")) return "SUPER NINTENDO";
        if (ext.equals("zip") || ext.equals("7z")) return "ARCADE";
        if (ext.equals("n64") || ext.equals("z64") || ext.equals("v64")) return "NINTENDO 64";
        if (ext.equals("nds")) return "NINTENDO DS";
        if (ext.equals("cso")) return "PSP";
        if (ext.equals("cdi") || ext.equals("gdi")) return "DREAMCAST";
        if (ext.equals("cue") || ext.equals("pbp")) return "PLAYSTATION";
        if (ext.equals("smf") || ext.equals("sgm") || ext.equals("ssl")) return "NATIVE32";
        return null;
    }

    private void launchGame(GameFile game) {
        Set<String> h = history();
        h.remove(game.uri);
        h.add(game.uri);
        prefs.edit().putStringSet(PREF_HISTORY, h).apply();
        Intent intent = new Intent(this, EmulatorActivity.class);
        intent.putExtra(EmulatorActivity.EXTRA_ROM_URI, game.uri);
        intent.putExtra(EmulatorActivity.EXTRA_SYSTEM, game.system);
        intent.putExtra(EmulatorActivity.EXTRA_TITLE, game.name);
        startActivity(intent);
    }

    private void showGames(String system) {
        base(favoritesOnly ? "SEUS JOGOS FAVORITOS" : (system == null ? "TODOS OS JOGOS" : system));
        addBar(favoritesOnly ? "FAVORITOS" : (system == null ? "BIBLIOTECA" : system), this::showHome);
        EditText search = new EditText(this);
        search.setSingleLine(true); search.setTextColor(Color.WHITE); search.setHintTextColor(Color.GRAY);
        search.setHint("Pesquisar jogo...");
        body.addView(search);
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        body.addView(list);
        final Runnable[] renderRef = new Runnable[1];
        renderRef[0] = () -> {
            list.removeAllViews();
            String term = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            Set<String> fav = favorites();
            int count = 0;
            for (GameFile game : allGames) {
                if (system != null && !game.system.equals(system)) continue;
                if (favoritesOnly && !fav.contains(game.uri)) continue;
                if (!game.name.toLowerCase(Locale.ROOT).contains(term)) continue;
                count++;
                LinearLayout item = new LinearLayout(this);
                item.setGravity(Gravity.CENTER_VERTICAL);
                item.setPadding(dp(12), dp(8), dp(12), dp(8));
                item.setBackground(round(PANEL));
                LinearLayout info = new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL);
                info.addView(text(game.name, 14, Color.WHITE, true));
                info.addView(text(game.system + "  •  " + readableSize(game.size), 10, Color.LTGRAY, false));
                item.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
                item.addView(button(fav.contains(game.uri) ? "★" : "☆", () -> {
                    Set<String> updated = favorites();
                    if (!updated.add(game.uri)) updated.remove(game.uri);
                    prefs.edit().putStringSet(PREF_FAVORITES, updated).apply();
                    renderRef[0].run();
                }));
                item.setFocusable(true);
                item.setOnFocusChangeListener((v,f) -> v.setBackground(round(f ? Color.rgb(62,28,33) : PANEL)));
                item.setOnClickListener(v -> showGameDetails(game));
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, -2);
                ip.bottomMargin = dp(6); list.addView(item, ip);
            }
            if (count == 0) list.addView(text(favoritesOnly ? "Nenhum favorito encontrado." :
                "Nenhum jogo encontrado. Confira a pasta selecionada e as extensões suportadas.", 12, Color.LTGRAY, false));
        };
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a) {}
            public void onTextChanged(CharSequence s,int st,int before,int count) { renderRef[0].run(); }
            public void afterTextChanged(android.text.Editable e) {}
        });
        renderRef[0].run();
    }

    private void showGameDetails(GameFile game) {
        String[] options = {"▶ Jogar", "Adicionar/remover favorito", "Registrar no histórico", "Informações do arquivo"};
        new AlertDialog.Builder(this).setTitle(game.name).setItems(options, (dialog, which) -> {
            if (which == 0) {
                launchGame(game);
            } else if (which == 1) {
                Set<String> f = favorites();
                if (!f.add(game.uri)) f.remove(game.uri);
                prefs.edit().putStringSet(PREF_FAVORITES, f).apply();
                Toast.makeText(this, "Favoritos atualizados.", Toast.LENGTH_SHORT).show();
            } else if (which == 2) {
                Set<String> h = history(); h.remove(game.uri); h.add(game.uri);
                prefs.edit().putStringSet(PREF_HISTORY, h).apply();
                Toast.makeText(this, "Adicionado ao histórico.", Toast.LENGTH_SHORT).show();
            } else {
                new AlertDialog.Builder(this).setTitle("Informações")
                    .setMessage("Console: " + game.system + "\nArquivo: " + game.path +
                        "\nTamanho: " + readableSize(game.size) +
                        "\n\nA execução usa o núcleo Libretro integrado quando disponível.")
                    .setPositiveButton("OK", null).show();
            }
        }).setNegativeButton("Fechar", null).show();
    }

    private void showHistory() {
        Set<String> history = history();
        base("JOGOS ABERTOS RECENTEMENTE");
        addBar("HISTÓRICO", this::showHome);
        if (history.isEmpty()) body.addView(text("Seu histórico está vazio.", 13, Color.LTGRAY, false));
        else {
            for (String uri : history) {
                GameFile found = null;
                for (GameFile g : allGames) if (g.uri.equals(uri)) { found = g; break; }
                if (found != null) {
                    GameFile game = found;
                    body.addView(button(game.name + "  •  " + game.system, () -> showGameDetails(game)));
                }
            }
        }
    }

    private Set<String> favorites() {
        Set<String> s = prefs.getStringSet(PREF_FAVORITES, null);
        return s == null ? new HashSet<>() : new HashSet<>(s);
    }
    private Set<String> history() {
        Set<String> s = prefs.getStringSet(PREF_HISTORY, null);
        return s == null ? new HashSet<>() : new HashSet<>(s);
    }
    private String readableSize(long size) {
        if (size <= 0) return "tamanho indisponível";
        if (size < 1024*1024) return (size/1024) + " KB";
        return String.format(Locale.getDefault(), "%.1f MB", size/(1024.0*1024.0));
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            showHome(); return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
