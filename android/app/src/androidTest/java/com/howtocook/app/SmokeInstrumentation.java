package com.howtocook.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Device smoke test using only the Android platform; no test runtime dependencies. */
public class SmokeInstrumentation extends Instrumentation {
    private Activity activity;
    private final StringBuilder log = new StringBuilder();
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle results = new Bundle();
        try {
            getTargetContext().getSharedPreferences("favorites", 0).edit().clear().commit();
            activity = launch();
            int total;
            try (java.io.InputStream in = getTargetContext().getAssets().open("recipes.json")) {
                total = new org.json.JSONObject(new java.util.Scanner(in, "UTF-8").useDelimiter("\\A").next()).getJSONArray("recipes").length();
            }
            await(() -> text("离线菜谱 · " + total + " 道") != null, "catalog loads offline");
            Spinner spinner = find(Spinner.class);
            runOnMainSync(() -> {
                for (int i = 0; i < spinner.getCount(); i++) if ("素菜".equals(spinner.getItemAtPosition(i))) spinner.setSelection(i);
            });
            EditText query = find(EditText.class);
            runOnMainSync(() -> query.setText("蒜蓉空心菜"));
            await(() -> text("蒜蓉空心菜") != null, "Chinese search in category");
            runOnMainSync(() -> {
                ListView list = find(ListView.class);
                list.performItemClick(list.getChildAt(0), 0, list.getAdapter().getItemId(0));
            });
            await(() -> text("☆ 收藏") != null, "recipe detail opens");
            final WebView web = find(WebView.class);
            await(() -> "true".equals(script(web, "document.body.innerText.includes('必备原料和工具') && document.body.innerText.includes('HowToCook')")), "offline body and attribution render");
            await(() -> "true".equals(script(web, "document.images.length > 0 && Array.from(document.images).every(i => i.complete && i.naturalWidth > 0)")), "bundled image renders offline");
            click("☆ 收藏"); await(() -> text("★ 已收藏") != null, "favorite can be saved");
            runOnMainSync(() -> activity.finish()); waitForIdleSync();
            activity = launch();
            await(() -> text("我的收藏") != null && find(ListView.class).getCount() > 0, "restart catalog");
            click("我的收藏"); await(() -> text("蒜蓉空心菜  ★") != null, "favorite persists after activity restart");
            runOnMainSync(() -> {
                ListView list = find(ListView.class);
                list.performItemClick(list.getChildAt(0), 0, list.getAdapter().getItemId(0));
            });
            await(() -> text("★ 已收藏") != null, "saved detail opens");
            final WebView linkedWeb = find(WebView.class);
            await(() -> "true".equals(script(linkedWeb, "document.body.innerText.includes('热锅凉油法')")), "detail contains original local link");
            script(linkedWeb, "Array.from(document.querySelectorAll('a')).find(a => a.innerText.includes('热锅凉油法')).click(); true");
            await(() -> text("阅读文档") != null, "local tips link opens inside app");
            await(() -> "true".equals(script(linkedWeb, "document.body.innerText.includes('炒/煎')")), "linked tips render offline");
            click("‹ 返回"); await(() -> text("★ 已收藏") != null, "back returns to linked recipe");
            click("★ 已收藏"); click("‹ 返回");
            await(() -> text("我的收藏 · 0 道") != null, "favorite can be removed");
            click("全部菜谱");
            runOnMainSync(() -> find(EditText.class).setText("不存在的菜名xyz"));
            await(() -> text("离线菜谱 · 0 道") != null, "empty search has explicit empty state");
            click("来源与许可");
            final WebView about = find(WebView.class);
            await(() -> "true".equals(script(about, "document.body.innerText.includes('free and unencumbered')")), "license renders");
            results.putString("stream", "\n" + log + "ALL SMOKE CHECKS PASSED\n");
            finish(Activity.RESULT_OK, results);
        } catch (Throwable error) {
            results.putString("stream", "\n" + log + "FAILED: " + error + "\n");
            finish(1, results);
        }
    }
    private Activity launch() {
        Intent intent = new Intent(Intent.ACTION_MAIN).setClassName(getTargetContext(), "com.howtocook.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return startActivitySync(intent);
    }
    private void click(String value) throws Exception {
        await(() -> text(value) != null, "button available: " + value);
        runOnMainSync(() -> text(value).performClick()); waitForIdleSync();
    }
    private void await(Callable<Boolean> condition, String description) throws Exception {
        long deadline = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.call()) { log.append("PASS: ").append(description).append('\n'); return; }
            Thread.sleep(200);
        }
        throw new AssertionError(description);
    }
    private String script(WebView web, String js) throws Exception {
        CountDownLatch ready = new CountDownLatch(1); AtomicReference<String> result = new AtomicReference<>();
        runOnMainSync(() -> {
            web.getSettings().setJavaScriptEnabled(true);
            web.evaluateJavascript(js, value -> { result.set(value); web.getSettings().setJavaScriptEnabled(false); ready.countDown(); });
        });
        if (!ready.await(5, TimeUnit.SECONDS)) return "timeout";
        return result.get();
    }
    private TextView text(String text) { return findText(activity.getWindow().getDecorView(), text); }
    private TextView findText(View v, String text) {
        if (!v.isShown()) return null;
        if (v instanceof TextView && text.contentEquals(((TextView)v).getText())) return (TextView)v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)v).getChildCount(); i++) {
            TextView found = findText(((ViewGroup)v).getChildAt(i), text); if (found != null) return found;
        }
        return null;
    }
    private <T extends View> T find(Class<T> type) { return findView(activity.getWindow().getDecorView(), type); }
    private <T extends View> T findView(View view, Class<T> type) {
        if (!view.isShown()) return null;
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) {
            T found = findView(((ViewGroup)view).getChildAt(i), type); if (found != null) return found;
        }
        return null;
    }
}
