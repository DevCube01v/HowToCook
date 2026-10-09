package com.howtocook.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.webkit.*;
import android.widget.*;
import android.util.LruCache;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final String ORIGIN = "https://appassets.androidplatform.net";
    private final ArrayList<Recipe> recipes = new ArrayList<>(), visible = new ArrayList<>();
    private final ArrayList<String> categoryNames = new ArrayList<>(), history = new ArrayList<>();
    private final ExecutorService loader = Executors.newSingleThreadExecutor(), photos = Executors.newFixedThreadPool(2);
    private final LruCache<String, Bitmap> photoCache = new LruCache<String, Bitmap>(8192) {
        @Override protected int sizeOf(String key, Bitmap value) { return Math.max(1, value.getByteCount()/1024); }
    };
    private final ArrayList<TextView> chipLabels = new ArrayList<>();
    private final ArrayList<LinearLayout> navButtons = new ArrayList<>();
    private final ArrayList<Ui.Icon> navIcons = new ArrayList<>();
    private final ArrayList<TextView> navLabels = new ArrayList<>();
    private Set<String> favorites;
    private LinearLayout root, browse, detail, catalog, featured, chips, navigation, favoriteButton;
    private ScrollView home;
    private EditText search;
    private TextView count, catalogTitle, detailTitle, favoriteText, heroSubtitle, emptyTitle, emptySubtitle;
    private Ui.Icon favoriteIcon;
    private ListView list;
    private WebView web;
    private BaseAdapter adapter;
    private String activeTab = "recipes", selectedCategory = "全部分类", currentPath = "";
    private boolean catalogMode, loaded, destroyed, ignoreQuery;
    private int listPosition;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        favorites = new HashSet<>(getSharedPreferences("favorites", MODE_PRIVATE).getStringSet("ids", Collections.emptySet()));
        if (state != null) {
            activeTab = state.getString("tab", "recipes"); selectedCategory = state.getString("category", "全部分类");
            catalogMode = state.getBoolean("catalog"); currentPath = state.getString("path", ""); listPosition = state.getInt("position");
            ArrayList<String> restored = state.getStringArrayList("history"); if (restored != null) history.addAll(restored);
        }
        createUi();
        if (state != null) { ignoreQuery = true; search.setText(state.getString("query", "")); ignoreQuery = false; }
        refresh();
        loader.execute(() -> {
            try {
                JSONObject data = new JSONObject(readAsset("recipes.json"));
                ArrayList<Recipe> entries = new ArrayList<>(); JSONArray rs = data.getJSONArray("recipes");
                for (int i=0;i<rs.length();i++) {
                    JSONObject r = rs.getJSONObject(i);
                    entries.add(new Recipe(r.getString("id"),r.getString("title"),r.getString("category"),r.getString("difficulty"),
                        r.getString("calories"),r.getString("html"),r.getString("search"),r.optString("thumbnail")));
                }
                ArrayList<String> names = new ArrayList<>(); JSONArray cs = data.getJSONArray("categories");
                for (int i=0;i<cs.length();i++) names.add(cs.getString(i));
                runOnUiThread(() -> {
                    if (destroyed) return;
                    recipes.addAll(entries); categoryNames.addAll(names); loaded = true;
                    buildHome(); buildChips(); refresh(); list.setSelection(listPosition);
                    if (!currentPath.isEmpty()) showPage(currentPath);
                });
            } catch (Exception error) {
                runOnUiThread(() -> { if (!destroyed) { heroSubtitle.setText(R.string.load_error); count.setText(R.string.load_error); } });
            }
        });
    }
    private void createUi() {
        root = column(); root.setBackgroundColor(Ui.PAPER); root.setFocusableInTouchMode(true);
        root.setOnApplyWindowInsetsListener((v,insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        setContentView(root); root.requestApplyInsets(); root.requestFocus();
        FrameLayout screens = new FrameLayout(this); root.addView(screens,new LinearLayout.LayoutParams(-1,0,1));
        browse = column(); screens.addView(browse,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header = horizontal(); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(20),0,dp(20),0);
        TextView brand = label(getString(R.string.app_name),23,true); header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView offline = label(getString(R.string.offline_badge),11,false); offline.setTextColor(Ui.BLUE); offline.setPadding(dp(10),dp(5),dp(10),dp(5));
        offline.setBackground(Ui.surface(this,Ui.PALE,20,false)); header.addView(offline); browse.addView(header,new LinearLayout.LayoutParams(-1,dp(56)));
        LinearLayout searchBar = horizontal(); searchBar.setGravity(Gravity.CENTER_VERTICAL); searchBar.setPadding(dp(14),0,dp(4),0);
        searchBar.setBackground(Ui.surface(this,Color.WHITE,23,true));
        searchBar.addView(icon("search",Ui.MUTED,20));
        search = new EditText(this); search.setSingleLine(true); search.setTextSize(14); search.setTextColor(Ui.INK); search.setHintTextColor(Ui.MUTED);
        search.setHint(R.string.search_hint); search.setContentDescription(getString(R.string.search_description)); search.setBackgroundColor(Color.TRANSPARENT);
        search.setPadding(dp(10),0,0,0); search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchBar.addView(search,new LinearLayout.LayoutParams(0,-1,1));
        Ui.Icon clear = icon("close",Ui.MUTED,44); clear.setContentDescription(getString(R.string.clear_search)); clear.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        clear.setPadding(dp(13),dp(13),dp(13),dp(13)); clear.setOnClickListener(v -> search.setText("")); searchBar.addView(clear);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,dp(46)); sp.setMargins(dp(20),0,dp(20),dp(2)); browse.addView(searchBar,sp);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) { if (!ignoreQuery) { if (s.length()>0) catalogMode=true; refresh(); } }
            public void afterTextChanged(Editable s) {}
        });
        FrameLayout content = new FrameLayout(this); browse.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        home = new ScrollView(this); home.setFillViewport(false); home.setVerticalScrollBarEnabled(false); content.addView(home,new FrameLayout.LayoutParams(-1,-1));
        buildHome();
        catalog = column(); content.addView(catalog,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout heading = horizontal(); heading.setGravity(Gravity.CENTER_VERTICAL); heading.setPadding(dp(20),dp(16),dp(20),dp(10));
        catalogTitle = label(getString(R.string.all_recipes),20,true); heading.addView(catalogTitle,new LinearLayout.LayoutParams(0,-2,1));
        TextView returnHome = textAction(getString(R.string.back_categories)); returnHome.setOnClickListener(v -> returnHome()); heading.addView(returnHome); catalog.addView(heading);
        HorizontalScrollView scrollChips = new HorizontalScrollView(this); scrollChips.setHorizontalScrollBarEnabled(false);
        chips = horizontal(); chips.setPadding(dp(20),dp(2),dp(12),dp(8)); scrollChips.addView(chips); catalog.addView(scrollChips);
        count = label("",12,false); count.setTextColor(Ui.MUTED); count.setPadding(dp(20),dp(2),dp(20),dp(8)); catalog.addView(count);
        FrameLayout results = new FrameLayout(this); catalog.addView(results,new LinearLayout.LayoutParams(-1,0,1));
        list = new ListView(this); list.setDivider(null); list.setSelector(android.R.color.transparent); list.setVerticalScrollBarEnabled(false);
        list.setClipToPadding(false); list.setPadding(0,0,0,dp(14)); results.addView(list,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout empty = column(); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(24),dp(20),dp(24),dp(20));
        empty.addView(icon("book",Ui.MUTED,48)); emptyTitle = label("",18,true); emptyTitle.setGravity(Gravity.CENTER); emptyTitle.setPadding(0,dp(16),0,dp(8)); empty.addView(emptyTitle);
        emptySubtitle = label("",14,false); emptySubtitle.setTextColor(Ui.MUTED); emptySubtitle.setGravity(Gravity.CENTER); empty.addView(emptySubtitle);
        results.addView(empty,new FrameLayout.LayoutParams(-1,-1)); list.setEmptyView(empty);
        adapter = new BaseAdapter() {
            public int getCount() { return visible.size(); }
            public Recipe getItem(int position) { return visible.get(position); }
            public long getItemId(int position) { return position; }
            public View getView(int position,View recycled,ViewGroup parent) {
                RecipeRow row;
                if (recycled==null) { row=new RecipeRow(); recycled=row.outer; recycled.setTag(row); } else row=(RecipeRow)recycled.getTag();
                row.bind(getItem(position)); return recycled;
            }
        };
        list.setAdapter(adapter); list.setOnItemClickListener((parent,view,position,id) -> openRecipe(visible.get(position)));
        detail = column(); screens.addView(detail,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout toolbar = horizontal(); toolbar.setGravity(Gravity.CENTER_VERTICAL); toolbar.setPadding(dp(10),0,dp(14),0); toolbar.setBackgroundColor(Color.WHITE);
        Ui.Icon back = icon("back",Ui.INK,44); back.setPadding(dp(10),dp(10),dp(10),dp(10)); back.setContentDescription(getString(R.string.back));
        back.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES); back.setOnClickListener(v -> goBack()); toolbar.addView(back);
        detailTitle = label("",17,true); detailTitle.setMaxLines(1); detailTitle.setEllipsize(TextUtils.TruncateAt.END);
        toolbar.addView(detailTitle,new LinearLayout.LayoutParams(0,-2,1));
        favoriteButton = horizontal(); favoriteButton.setGravity(Gravity.CENTER); favoriteButton.setPadding(dp(12),dp(8),dp(12),dp(8));
        favoriteButton.setBackground(Ui.ripple(this,Ui.PALE,20,false)); favoriteIcon=icon("heart",Ui.BLUE,17); favoriteButton.addView(favoriteIcon);
        favoriteText=label(getString(R.string.save),13,true); favoriteText.setTextColor(Ui.BLUE); favoriteText.setPadding(dp(6),0,0,0); favoriteButton.addView(favoriteText);
        favoriteButton.setOnClickListener(v -> { Recipe r=currentRecipe(); if (r!=null) toggleFavorite(r); }); toolbar.addView(favoriteButton);
        detail.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(56)));
        web = new WebView(this); web.setBackgroundColor(Ui.PAPER); web.getSettings().setJavaScriptEnabled(false);
        web.getSettings().setAllowFileAccess(false); web.getSettings().setAllowContentAccess(false); web.getSettings().setBlockNetworkLoads(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request) {
                String asset=assetPath(request.getUrl()); if (asset==null) return response(403,"Forbidden");
                try {
                    String ext=MimeTypeMap.getFileExtensionFromUrl(request.getUrl().toString());
                    String mime=MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase(Locale.ROOT));
                    return new WebResourceResponse(mime==null?"application/octet-stream":mime,"UTF-8",getAssets().open(asset));
                } catch(IOException error) { return response(404,"Not Found"); }
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request) { return navigate(request.getUrl()); }
            @Override public boolean shouldOverrideUrlLoading(WebView view,String url) { return navigate(Uri.parse(url)); }
        });
        detail.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        navigation=horizontal(); navigation.setBackgroundColor(Color.WHITE); navigation.setPadding(dp(10),dp(4),dp(10),dp(4));
        String[] names={getString(R.string.recipes_tab),getString(R.string.saved_tab),getString(R.string.about_tab)};
        String[] icons={"book","heart","info"}, tabs={"recipes","favorites","about"};
        for (int i=0;i<3;i++) {
            final String tab=tabs[i]; LinearLayout item=column(); item.setGravity(Gravity.CENTER); item.setBackground(Ui.ripple(this,Color.WHITE,14,false));
            Ui.Icon image=icon(icons[i],Ui.MUTED,24); item.addView(image); TextView caption=label(names[i],11,false); caption.setGravity(Gravity.CENTER); caption.setPadding(0,dp(3),0,0); item.addView(caption);
            item.setContentDescription(names[i]); item.setOnClickListener(v -> switchTab(tab));
            navigation.addView(item,new LinearLayout.LayoutParams(0,dp(56),1)); navButtons.add(item); navIcons.add(image); navLabels.add(caption);
        }
        root.addView(navigation,new LinearLayout.LayoutParams(-1,-2));
    }
    private void buildHome() {
        home.removeAllViews(); LinearLayout page=column(); page.setPadding(dp(20),dp(16),dp(20),dp(20)); home.addView(page);
        LinearLayout hero=horizontal(); hero.setGravity(Gravity.CENTER_VERTICAL); hero.setPadding(dp(16),dp(16),dp(14),dp(16));
        GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(238,246,255),Color.rgb(222,236,255)});
        gradient.setCornerRadius(dp(18)); hero.setBackground(gradient);
        LinearLayout heroCopy=column(); TextView message=label(getString(R.string.hero_title),19,true); message.setLineSpacing(dp(2),1); heroCopy.addView(message);
        heroSubtitle=label(loaded?getString(R.string.hero_count,recipes.size()):getString(R.string.loading),12,false);
        heroSubtitle.setTextColor(Color.rgb(74,109,164)); heroSubtitle.setPadding(0,dp(7),0,0); heroCopy.addView(heroSubtitle);
        hero.addView(heroCopy,new LinearLayout.LayoutParams(0,-2,1)); Ui.Icon illustration=icon("kitchen",Ui.BLUE,65); hero.addView(illustration); page.addView(hero);
        TextView categoryHeading=label(getString(R.string.categories_heading),19,true); categoryHeading.setPadding(0,dp(22),0,dp(12)); page.addView(categoryHeading);
        String[] initial={"素菜","荤菜","水产","早餐","主食","汤与粥","甜品","饮品","调味料","半成品"};
        List<String> names=categoryNames.isEmpty()?Arrays.asList(initial):categoryNames;
        for (int start=0;start<names.size();start+=5) {
            LinearLayout row=horizontal();
            for (int i=start;i<Math.min(start+5,names.size());i++) {
                String name=names.get(i); LinearLayout tile=column(); tile.setGravity(Gravity.CENTER);
                tile.setBackground(Ui.ripple(this,Ui.PALE,12,false)); tile.addView(icon(Ui.categoryIcon(name),Ui.BLUE,29));
                TextView caption=label(name,11,false); caption.setGravity(Gravity.CENTER); caption.setTextColor(Color.rgb(52,85,134)); caption.setPadding(0,dp(7),0,0); tile.addView(caption);
                tile.setContentDescription(getString(R.string.category_description,name)); tile.setOnClickListener(v -> showCatalog(name));
                LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,dp(76),1); if(i>start) cell.leftMargin=dp(7); row.addView(tile,cell);
            }
            LinearLayout.LayoutParams rowLayout=new LinearLayout.LayoutParams(-1,-2); if(start>0) rowLayout.topMargin=dp(8); page.addView(row,rowLayout);
        }
        LinearLayout section=horizontal(); section.setGravity(Gravity.CENTER_VERTICAL); section.setPadding(0,dp(20),0,dp(10));
        section.addView(label(getString(R.string.featured_heading),19,true),new LinearLayout.LayoutParams(0,-2,1));
        TextView all=textAction(getString(R.string.view_all)); all.setOnClickListener(v -> showCatalog("全部分类")); section.addView(all); page.addView(section);
        featured=column(); page.addView(featured);
        if (loaded) {
            int number=0;
            for(String title:new String[]{"蒜蓉空心菜","简易红烧肉"}) for(Recipe r:recipes) if(r.title.equals(title)) { addFeatured(r); number++; break; }
            if(number==0) for(Recipe r:recipes) if(!r.thumbnail.isEmpty() && number++<2) addFeatured(r);
        }
    }
    private void addFeatured(Recipe recipe) {
        RecipeRow row=new RecipeRow(); row.outer.setPadding(0,dp(4),0,dp(4)); row.bind(recipe);
        row.outer.setOnClickListener(v -> openRecipe(recipe)); featured.addView(row.outer);
    }
    private void buildChips() {
        chips.removeAllViews(); chipLabels.clear(); ArrayList<String> names=new ArrayList<>(); names.add("全部分类"); names.addAll(categoryNames);
        for(String name:names) {
            TextView chip=label(name.equals("全部分类")?getString(R.string.all_chip):name,12,false); chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(15),0,dp(15),0); chip.setTag(name); chip.setOnClickListener(v -> { selectedCategory=name; listPosition=0; refresh(); list.setSelection(0); });
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-2,dp(34)); size.rightMargin=dp(8); chips.addView(chip,size); chipLabels.add(chip);
        }
    }
    private final class RecipeRow {
        final LinearLayout outer=column(), card=horizontal();
        final TextView title=label("",16,true), metadata=label("",12,false);
        final Ui.Icon save=icon("heart",Ui.MUTED,44), placeholder=icon("bowl",Ui.MUTED,32);
        final ImageView image=new ImageView(MainActivity.this);
        RecipeRow() {
            outer.setPadding(dp(20),dp(5),dp(20),dp(5)); outer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            card.setPadding(dp(10),dp(10),dp(6),dp(10)); card.setGravity(Gravity.CENTER_VERTICAL); card.setBackground(Ui.ripple(MainActivity.this,Color.WHITE,14,true)); outer.addView(card);
            FrameLayout photo=new FrameLayout(MainActivity.this); photo.setBackground(Ui.surface(MainActivity.this,Ui.PALE,10,false)); photo.setClipToOutline(true);
            FrameLayout.LayoutParams fallback=new FrameLayout.LayoutParams(dp(32),dp(32),Gravity.CENTER); photo.addView(placeholder,fallback);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP); image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); photo.addView(image,new FrameLayout.LayoutParams(-1,-1));
            card.addView(photo,new LinearLayout.LayoutParams(dp(72),dp(72))); LinearLayout copy=column(); copy.setPadding(dp(12),0,dp(4),0);
            title.setMaxLines(2); title.setEllipsize(TextUtils.TruncateAt.END); copy.addView(title);
            metadata.setPadding(0,dp(6),0,0); metadata.setTextColor(Ui.MUTED); metadata.setSingleLine(true); metadata.setEllipsize(TextUtils.TruncateAt.END); copy.addView(metadata);
            card.addView(copy,new LinearLayout.LayoutParams(0,-2,1)); save.setPadding(dp(12),dp(12),dp(12),dp(12)); save.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            save.setFocusable(false); save.setBackground(Ui.ripple(MainActivity.this,Color.WHITE,22,false)); card.addView(save);
        }
        void bind(Recipe r) {
            placeholder.symbol(Ui.categoryIcon(r.category));
            title.setText(r.title); metadata.setText(getString(R.string.recipe_metadata,r.category,r.difficulty));
            boolean saved=favorites.contains(r.id); save.appearance(saved?Ui.BLUE:Ui.MUTED,saved);
            save.setContentDescription(getString(saved?R.string.unsave_recipe:R.string.save_recipe,r.title)); save.setOnClickListener(v -> toggleFavorite(r));
            loadPhoto(image,r.thumbnail);
        }
    }
    private void loadPhoto(ImageView image,String path) {
        image.setTag(path); image.setImageDrawable(null); if(path.isEmpty()) return;
        Bitmap cached=photoCache.get(path); if(cached!=null) { image.setImageBitmap(cached); return; }
        photos.execute(() -> {
            try {
                BitmapFactory.Options bounds=new BitmapFactory.Options(); bounds.inJustDecodeBounds=true;
                try(InputStream in=getAssets().open(path)) { BitmapFactory.decodeStream(in,null,bounds); }
                BitmapFactory.Options options=new BitmapFactory.Options(); options.inSampleSize=1;
                while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>512) options.inSampleSize*=2;
                Bitmap bitmap; try(InputStream in=getAssets().open(path)) { bitmap=BitmapFactory.decodeStream(in,null,options); }
                if(bitmap==null) return; photoCache.put(path,bitmap);
                runOnUiThread(() -> { if(!destroyed && path.equals(image.getTag())) image.setImageBitmap(bitmap); });
            } catch(IOException ignored) { /* The category illustration remains visible. */ }
        });
    }
    private void showCatalog(String name) { selectedCategory=name; catalogMode=true; activeTab="recipes"; listPosition=0; refresh(); list.setSelection(0); }
    private void returnHome() {
        activeTab="recipes"; catalogMode=false; selectedCategory="全部分类"; listPosition=0;
        ignoreQuery=true; search.setText(""); ignoreQuery=false; hideKeyboard(); refresh();
    }
    private void switchTab(String tab) {
        hideKeyboard(); currentPath=""; history.clear(); web.loadUrl("about:blank"); activeTab=tab;
        if(tab.equals("about")) { showPage("attribution.html"); return; }
        ignoreQuery=true; search.setText(""); ignoreQuery=false; selectedCategory="全部分类"; catalogMode=tab.equals("favorites"); listPosition=0; refresh(); list.setSelection(0);
    }
    private void refresh() {
        if(adapter==null) return;
        boolean saved=activeTab.equals("favorites"); visible.clear();
        for(Recipe r:recipes) if(r.matches(search.getText().toString(),selectedCategory) && (!saved || favorites.contains(r.id))) visible.add(r);
        adapter.notifyDataSetChanged();
        catalogTitle.setText(saved?getString(R.string.saved_label):selectedCategory.equals("全部分类")?getString(R.string.all_recipes):getString(R.string.category_title,selectedCategory));
        count.setText(loaded?getString(R.string.recipe_count,getString(saved?R.string.saved_label:R.string.offline_label),visible.size()):getString(R.string.loading));
        emptyTitle.setText(saved && favorites.isEmpty()?R.string.no_favorites:R.string.no_results);
        emptySubtitle.setText(saved && favorites.isEmpty()?R.string.save_tip:R.string.search_tip);
        for(TextView chip:chipLabels) {
            boolean selected=selectedCategory.equals(chip.getTag()); chip.setTextColor(selected?Color.WHITE:Ui.MUTED);
            chip.setBackground(Ui.ripple(this,selected?Ui.BLUE:Color.WHITE,17,!selected));
        }
        for(int i=0;i<navLabels.size();i++) {
            boolean selected=activeTab.equals(new String[]{"recipes","favorites","about"}[i]);
            navIcons.get(i).appearance(selected?Ui.BLUE:Ui.MUTED,false); navLabels.get(i).setTextColor(selected?Ui.BLUE:Ui.MUTED);
            navButtons.get(i).setSelected(selected);
        }
        if(currentPath.isEmpty()) {
            browse.setVisibility(View.VISIBLE); detail.setVisibility(View.GONE); navigation.setVisibility(View.VISIBLE);
            boolean showList=catalogMode||saved||search.length()>0; home.setVisibility(showList?View.GONE:View.VISIBLE); catalog.setVisibility(showList?View.VISIBLE:View.GONE);
        }
    }
    private void openRecipe(Recipe r) { hideKeyboard(); listPosition=list.getFirstVisiblePosition(); history.clear(); openPage(r.html); }
    private void toggleFavorite(Recipe r) {
        if(!favorites.add(r.id)) favorites.remove(r.id);
        getSharedPreferences("favorites",MODE_PRIVATE).edit().putStringSet("ids",new HashSet<>(favorites)).apply();
        updateFavoriteButton(); buildHome(); refresh();
    }
    private static WebResourceResponse response(int code,String message) {
        return new WebResourceResponse("text/plain","UTF-8",code,message,Collections.emptyMap(),new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8)));
    }
    private String assetPath(Uri uri) {
        if(!"https".equals(uri.getScheme())||!"appassets.androidplatform.net".equals(uri.getHost())) return null;
        String p=uri.getPath(); if(p==null||!p.startsWith("/assets/")||p.contains("..")||p.contains("\\")) return null; return p.substring(8);
    }
    private boolean navigate(Uri uri) {
        String asset=assetPath(uri);
        if(asset!=null) {
            String fragment=uri.getEncodedFragment(); String next=asset+(fragment==null?"":"#"+fragment);
            if(asset.equals(currentPath.split("#",2)[0])&&fragment!=null) return false;
            openPage(next); return true;
        }
        if("https".equals(uri.getScheme())||"http".equals(uri.getScheme())||"mailto".equals(uri.getScheme())) {
            try { startActivity(new Intent(Intent.ACTION_VIEW,uri)); }
            catch(ActivityNotFoundException error) { Toast.makeText(this,R.string.no_link_app,Toast.LENGTH_SHORT).show(); }
        }
        return true;
    }
    private void openPage(String path) { if(!currentPath.isEmpty()) history.add(currentPath); showPage(path); }
    private void showPage(String path) {
        currentPath=path; hideKeyboard(); browse.setVisibility(View.GONE); detail.setVisibility(View.VISIBLE);
        navigation.setVisibility(path.startsWith("attribution")?View.VISIBLE:View.GONE);
        Recipe r=currentRecipe(); detailTitle.setText(r==null?getString(path.startsWith("attribution")?R.string.source_title:R.string.document_title):r.title);
        favoriteButton.setVisibility(r==null?View.GONE:View.VISIBLE); updateFavoriteButton(); refresh();
        String[] parts=path.split("#",2); Uri.Builder uri=Uri.parse(ORIGIN+"/assets/").buildUpon();
        for(String segment:parts[0].split("/")) uri.appendPath(segment);
        if(parts.length==2) uri.encodedFragment(parts[1]); web.loadUrl(uri.build().toString());
    }
    private Recipe currentRecipe() { String p=currentPath.split("#",2)[0]; for(Recipe r:recipes) if(r.html.equals(p)) return r; return null; }
    private void updateFavoriteButton() {
        Recipe r=currentRecipe(); boolean saved=r!=null&&favorites.contains(r.id);
        favoriteText.setText(saved?R.string.saved:R.string.save); favoriteIcon.appearance(Ui.BLUE,saved);
        favoriteButton.setContentDescription(getString(saved?R.string.unsave_detail:R.string.save_detail));
    }
    private void goBack() {
        if(!history.isEmpty()) { showPage(history.remove(history.size()-1)); return; }
        if(!currentPath.isEmpty()) {
            boolean about=activeTab.equals("about"); currentPath=""; web.loadUrl("about:blank"); if(about) returnHome(); else refresh(); list.setSelection(listPosition); return;
        }
        if(catalogMode||!activeTab.equals("recipes")||search.length()>0) { returnHome(); return; }
        super.onBackPressed();
    }
    @Override public void onBackPressed() { goBack(); }
    @Override public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putString("tab",activeTab); state.putBoolean("catalog",catalogMode); state.putString("path",currentPath);
        state.putString("query",search.getText().toString()); state.putString("category",selectedCategory);
        state.putInt("position",currentPath.isEmpty()?list.getFirstVisiblePosition():listPosition); state.putStringArrayList("history",new ArrayList<>(history));
    }
    @Override public void onDestroy() { destroyed=true; loader.shutdownNow(); photos.shutdownNow(); photoCache.evictAll(); web.destroy(); super.onDestroy(); }
    private String readAsset(String name) throws IOException {
        try(InputStream in=getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; int n; while((n=in.read(buffer))!=-1) out.write(buffer,0,n); return out.toString("UTF-8");
        }
    }
    private void hideKeyboard() { ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0); search.clearFocus(); root.requestFocus(); }
    private int dp(int value) { return Ui.dp(this,value); }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout horizontal() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private Ui.Icon icon(String name,int color,int size) { Ui.Icon v=new Ui.Icon(this,name,color); v.setLayoutParams(new LinearLayout.LayoutParams(dp(size),dp(size))); return v; }
    private TextView label(String text,int size,boolean bold) {
        TextView t=new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(Ui.INK); t.setIncludeFontPadding(false);
        if(bold) t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); return t;
    }
    private TextView textAction(String text) {
        TextView t=label(text,12,false); t.setTextColor(Ui.BLUE); t.setPadding(dp(8),dp(12),0,dp(12)); t.setBackground(Ui.ripple(this,Color.TRANSPARENT,10,false)); return t;
    }
}
