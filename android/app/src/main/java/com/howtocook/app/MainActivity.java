package com.howtocook.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String ORIGIN = "https://appassets.androidplatform.net";
    private static final int GREEN = Color.rgb(35,103,71), INK = Color.rgb(37,50,43), PAPER = Color.rgb(250,248,242);
    private final ArrayList<Recipe> recipes = new ArrayList<>(), visible = new ArrayList<>();
    private final ArrayList<String> history = new ArrayList<>();
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private Set<String> favorites;
    private LinearLayout root, browse, detail;
    private EditText search;
    private Spinner categories;
    private TextView count, detailTitle;
    private Button favoriteButton, savedTab;
    private ListView list;
    private WebView web;
    private BaseAdapter adapter;
    private boolean favoritesOnly = false, destroyed = false;
    private String currentPath = "", categoryToRestore = "全部分类";
    private int listPosition;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        favorites = new HashSet<>(getSharedPreferences("favorites", MODE_PRIVATE).getStringSet("ids", Collections.emptySet()));
        if (state != null) {
            favoritesOnly = state.getBoolean("saved");
            currentPath = state.getString("path", "");
            categoryToRestore = state.getString("category", "全部分类");
            listPosition = state.getInt("position");
            ArrayList<String> restored = state.getStringArrayList("history");
            if (restored != null) history.addAll(restored);
        }
        createUi();
        if (state != null) search.setText(state.getString("query", ""));
        count.setText("正在加载离线菜谱…");
        loader.execute(() -> {
            try {
                JSONObject data = new JSONObject(readAsset("recipes.json"));
                ArrayList<Recipe> loaded = new ArrayList<>();
                JSONArray entries = data.getJSONArray("recipes");
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject r = entries.getJSONObject(i);
                    loaded.add(new Recipe(r.getString("id"), r.getString("title"), r.getString("category"),
                        r.getString("difficulty"), r.getString("calories"), r.getString("html"), r.getString("search")));
                }
                ArrayList<String> names = new ArrayList<>(); names.add("全部分类");
                JSONArray cs = data.getJSONArray("categories");
                for (int i = 0; i < cs.length(); i++) names.add(cs.getString(i));
                runOnUiThread(() -> {
                    if (destroyed) return;
                    recipes.addAll(loaded);
                    ArrayAdapter<String> spinner = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
                    categories.setAdapter(spinner);
                    categories.setSelection(Math.max(0, names.indexOf(categoryToRestore)));
                    refresh(); list.setSelection(listPosition);
                    if (!currentPath.isEmpty()) showPage(currentPath);
                });
            } catch (Exception error) {
                runOnUiThread(() -> { if (!destroyed) count.setText(R.string.load_error); });
            }
        });
    }
    private void createUi() {
        root = column(); root.setBackgroundColor(PAPER);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        setContentView(root); root.requestApplyInsets();
        browse = column(); root.addView(browse, new LinearLayout.LayoutParams(-1, -1));
        TextView brand = label("下厨指南", 30); brand.setPadding(dp(20), dp(18), dp(20), 0); browse.addView(brand);
        TextView tagline = label("把每一餐，做得有把握。", 15); tagline.setPadding(dp(20), dp(4), dp(20), dp(12)); browse.addView(tagline);
        search = new EditText(this); search.setSingleLine(true); search.setTextSize(16); search.setHint("搜索菜名、食材或做法");
        search.setContentDescription("搜索菜谱"); search.setPadding(dp(18), dp(8), dp(18), dp(8)); browse.addView(search);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(); }
            public void afterTextChanged(Editable s) {}
        });
        categories = new Spinner(this); categories.setContentDescription("菜谱分类"); browse.addView(categories);
        categories.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { refresh(); }
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        count = label("", 14); count.setPadding(dp(20), dp(8), dp(20), dp(8)); browse.addView(count);
        FrameLayout results = new FrameLayout(this); browse.addView(results, new LinearLayout.LayoutParams(-1, 0, 1));
        list = new ListView(this); list.setDividerHeight(dp(1)); results.addView(list, new FrameLayout.LayoutParams(-1, -1));
        TextView empty = label("还没有匹配的菜谱\n试试其他关键词，或在详情页收藏菜谱。", 17);
        empty.setGravity(android.view.Gravity.CENTER); results.addView(empty, new FrameLayout.LayoutParams(-1, -1)); list.setEmptyView(empty);
        adapter = new BaseAdapter() {
            public int getCount() { return visible.size(); }
            public Recipe getItem(int position) { return visible.get(position); }
            public long getItemId(int position) { return position; }
            public View getView(int position, View recycled, ViewGroup parent) {
                Recipe r = getItem(position);
                LinearLayout row = column(); row.setPadding(dp(20), dp(14), dp(20), dp(14));
                row.addView(label(r.title + (favorites.contains(r.id) ? "  ★" : ""), 19));
                TextView meta = label(r.category + "  ·  " + r.difficulty + (r.calories.isEmpty() ? "" : "  ·  " + r.calories + " 大卡"), 13);
                meta.setTextColor(Color.rgb(98,114,102)); row.addView(meta); return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            listPosition = list.getFirstVisiblePosition(); history.clear(); openPage(visible.get(position).html);
        });
        LinearLayout tabs = new LinearLayout(this); browse.addView(tabs);
        Button all = button("全部菜谱"); savedTab = button("我的收藏"); Button about = button("来源与许可");
        for (Button b : new Button[]{all, savedTab, about}) tabs.addView(b, new LinearLayout.LayoutParams(0, dp(56), 1));
        all.setOnClickListener(v -> { favoritesOnly = false; refresh(); });
        savedTab.setOnClickListener(v -> { favoritesOnly = true; refresh(); });
        about.setOnClickListener(v -> { history.clear(); openPage("attribution.html"); });
        detail = column(); detail.setVisibility(View.GONE); root.addView(detail, new LinearLayout.LayoutParams(-1, -1));
        LinearLayout toolbar = new LinearLayout(this); detail.addView(toolbar);
        Button back = button("‹ 返回"); toolbar.addView(back); back.setOnClickListener(v -> goBack());
        detailTitle = label("", 18); detailTitle.setGravity(android.view.Gravity.CENTER_VERTICAL); toolbar.addView(detailTitle, new LinearLayout.LayoutParams(0, dp(56), 1));
        favoriteButton = button("收藏"); toolbar.addView(favoriteButton); favoriteButton.setOnClickListener(v -> toggleFavorite());
        web = new WebView(this); web.setBackgroundColor(PAPER);
        web.getSettings().setJavaScriptEnabled(false);
        web.getSettings().setAllowFileAccess(false); web.getSettings().setAllowContentAccess(false);
        web.getSettings().setBlockNetworkLoads(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String asset = assetPath(request.getUrl());
                if (asset == null) return response(403, "Forbidden");
                try {
                    String ext = MimeTypeMap.getFileExtensionFromUrl(request.getUrl().toString());
                    String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase(Locale.ROOT));
                    if (mime == null) mime = "application/octet-stream";
                    return new WebResourceResponse(mime, "UTF-8", getAssets().open(asset));
                } catch (IOException e) { return response(404, "Not Found"); }
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return navigate(request.getUrl()); }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return navigate(Uri.parse(url)); }
        });
        detail.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
    }
    private static WebResourceResponse response(int code, String message) {
        return new WebResourceResponse("text/plain", "UTF-8", code, message, Collections.emptyMap(), new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8)));
    }
    private String assetPath(Uri uri) {
        if (!"https".equals(uri.getScheme()) || !"appassets.androidplatform.net".equals(uri.getHost())) return null;
        String p = uri.getPath();
        if (p == null || !p.startsWith("/assets/") || p.contains("..") || p.contains("\\")) return null;
        return p.substring(8);
    }
    private boolean navigate(Uri uri) {
        String asset = assetPath(uri);
        if (asset != null) {
            String fragment = uri.getEncodedFragment();
            String next = asset + (fragment == null ? "" : "#" + fragment);
            if (asset.equals(currentPath.split("#", 2)[0]) && fragment != null) return false;
            openPage(next); return true;
        }
        if ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()) || "mailto".equals(uri.getScheme())) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
            catch (ActivityNotFoundException e) { Toast.makeText(this, "未找到可打开该链接的应用", Toast.LENGTH_SHORT).show(); }
        }
        return true;
    }
    private void refresh() {
        if (adapter == null) return;
        String category = categories.getSelectedItem() == null ? "全部分类" : categories.getSelectedItem().toString();
        visible.clear();
        for (Recipe r : recipes) if (r.matches(search.getText().toString(), category) && (!favoritesOnly || favorites.contains(r.id))) visible.add(r);
        adapter.notifyDataSetChanged();
        count.setText(getString(R.string.recipe_count, getString(favoritesOnly ? R.string.saved_label : R.string.offline_label), visible.size()));
        savedTab.setText(favoritesOnly ? "★ 我的收藏" : "我的收藏");
    }
    private void openPage(String path) {
        if (!currentPath.isEmpty()) history.add(currentPath);
        showPage(path);
    }
    private void showPage(String path) {
        currentPath = path;
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(), 0);
        browse.setVisibility(View.GONE); detail.setVisibility(View.VISIBLE);
        Recipe r = currentRecipe(); detailTitle.setText(r == null ? (path.startsWith("attribution") ? "来源与许可" : "阅读文档") : r.title);
        favoriteButton.setVisibility(r == null ? View.GONE : View.VISIBLE); updateFavoriteButton();
        String[] parts = path.split("#", 2);
        Uri.Builder uri = Uri.parse(ORIGIN + "/assets/").buildUpon();
        for (String segment : parts[0].split("/")) uri.appendPath(segment);
        if (parts.length == 2) uri.encodedFragment(parts[1]);
        web.loadUrl(uri.build().toString());
    }
    private Recipe currentRecipe() {
        String p = currentPath.split("#", 2)[0];
        for (Recipe r : recipes) if (r.html.equals(p)) return r;
        return null;
    }
    private void updateFavoriteButton() {
        Recipe r = currentRecipe();
        favoriteButton.setText(r != null && favorites.contains(r.id) ? "★ 已收藏" : "☆ 收藏");
    }
    private void toggleFavorite() {
        Recipe r = currentRecipe(); if (r == null) return;
        if (!favorites.add(r.id)) favorites.remove(r.id);
        getSharedPreferences("favorites", MODE_PRIVATE).edit().putStringSet("ids", new HashSet<>(favorites)).apply();
        updateFavoriteButton(); refresh();
    }
    private void goBack() {
        if (!history.isEmpty()) { showPage(history.remove(history.size() - 1)); return; }
        if (!currentPath.isEmpty()) {
            currentPath = ""; web.loadUrl("about:blank"); detail.setVisibility(View.GONE); browse.setVisibility(View.VISIBLE); refresh(); list.setSelection(listPosition); return;
        }
        super.onBackPressed();
    }
    @Override public void onBackPressed() { goBack(); }
    @Override public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("saved", favoritesOnly); state.putString("path", currentPath);
        state.putString("query", search.getText().toString());
        state.putString("category", categories.getSelectedItem() == null ? categoryToRestore : categories.getSelectedItem().toString());
        state.putInt("position", currentPath.isEmpty() ? list.getFirstVisiblePosition() : listPosition);
        state.putStringArrayList("history", new ArrayList<>(history));
    }
    @Override public void onDestroy() { destroyed = true; loader.shutdownNow(); web.destroy(); super.onDestroy(); }
    private String readAsset(String name) throws IOException {
        try (InputStream in = getAssets().open(name); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n; while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toString("UTF-8");
        }
    }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView label(String text, int size) { TextView t = new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(INK); return t; }
    private Button button(String text) { Button b = new Button(this); b.setText(text); b.setTextColor(GREEN); b.setAllCaps(false); return b; }
}
