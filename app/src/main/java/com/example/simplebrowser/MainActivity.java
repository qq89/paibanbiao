package com.example.simplebrowser;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    // ====== 四个默认网址 ======
    private static final String DEFAULT_PRIMARY = "http://jjjjjj8889.gicp.net:28136/schedule";
    private static final String DEFAULT_BACKUP1 = "http://192.168.66.44:28136/schedule";
    private static final String DEFAULT_BACKUP2 = "https://espnote.netlify.app";
    private static final String DEFAULT_BACKUP3 = "https://espnote.pages.dev";

    private WebView webView;
    private SharedPreferences prefs;
    
    private String primaryUrl;
    private String backup1Url;
    private String backup2Url;
    private String backup3Url;

    private int currentLevel = 0;
    private boolean isSwitching = false;

    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;

    private int clickCount = 0;
    private long lastClickTime = 0;
    private static final long CLICK_INTERVAL = 600;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("app_config", Context.MODE_PRIVATE);
        primaryUrl = prefs.getString("primary_url", DEFAULT_PRIMARY);
        backup1Url = prefs.getString("backup1_url", DEFAULT_BACKUP1);
        backup2Url = prefs.getString("backup2_url", DEFAULT_BACKUP2);
        backup3Url = prefs.getString("backup3_url", DEFAULT_BACKUP3);

        webView = new WebView(this);
        setContentView(webView);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);

        // ====== 缩放设置：只保留双指，禁用双击 ======
        // 1. 允许支持缩放
        webView.getSettings().setSupportZoom(true);
        // 2. 设置内置缩放控件
        webView.getSettings().setBuiltInZoomControls(true);
        // 3. 隐藏原生的 +/- 缩放按钮
        webView.getSettings().setDisplayZoomControls(false);
        // 注意：去掉了 setUseWideViewPort(true) 和 setLoadWithOverviewMode(true)
        // 这样双击屏幕就不会触发放大，但双指捏合依然有效
        // ============================================

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(WebView view, String url) {
                cancelTimeout();
                isSwitching = false;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (!isSwitching && request.isForMainFrame()) {
                    switchToNextFallback();
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);
                if (!isSwitching && request.isForMainFrame()) {
                    switchToNextFallback();
                }
            }
        });

        currentLevel = 0;
        loadUrlWithTimeout();
    }

    private String getUrlByLevel(int level) {
        switch (level) {
            case 0: return primaryUrl;
            case 1: return backup1Url;
            case 2: return backup2Url;
            case 3: return backup3Url;
            default: return null;
        }
    }

    private long getTimeoutByLevel(int level) {
        switch (level) {
            case 0: return 2000;  
            case 1: return 2000;  
            case 2: return 3000;  
            case 3: return 10000; 
            default: return 2000;
        }
    }

    private void loadUrlWithTimeout() {
        cancelTimeout();
        isSwitching = false;
        
        String urlToLoad = getUrlByLevel(currentLevel);

        if (urlToLoad == null || urlToLoad.trim().isEmpty()) {
            switchToNextFallback();
            return;
        }

        timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                switchToNextFallback();
            }
        };
        
        long timeoutMs = getTimeoutByLevel(currentLevel);
        timeoutHandler.postDelayed(timeoutRunnable, timeoutMs);

        webView.loadUrl(urlToLoad);
    }

    private void switchToNextFallback() {
        if (isSwitching) return;
        isSwitching = true;
        cancelTimeout();

        if (currentLevel < 3) {
            currentLevel++;
            webView.stopLoading();
            loadUrlWithTimeout();
        } else {
            Toast.makeText(MainActivity.this, "所有网址均无法访问", Toast.LENGTH_LONG).show();
            isSwitching = false;
        }
    }

    private void cancelTimeout() {
        if (timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            long now = System.currentTimeMillis();
            if (now - lastClickTime > CLICK_INTERVAL) {
                clickCount = 0;
            }
            lastClickTime = now;
            clickCount++;

            if (clickCount == 3) {
                String currentUrl = webView.getUrl();
                if (currentUrl == null || currentUrl.isEmpty() || currentUrl.equals("about:blank")) {
                    currentUrl = getUrlByLevel(currentLevel);
                }
                showUrlToast(currentUrl);
            }

            if (clickCount >= 5) {
                clickCount = 0;
                showEditUrlDialog();
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private void showUrlToast(String url) {
        Toast toast = new Toast(getApplicationContext());
        TextView tv = new TextView(MainActivity.this);
        tv.setText("当前网址:\n" + url);
        tv.setTextColor(Color.WHITE);
        tv.setBackgroundColor(Color.parseColor("#CC000000"));
        tv.setPadding(40, 40, 40, 40);
        tv.setTextSize(16);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(5); 
        
        toast.setView(tv);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.BOTTOM, 0, 200); 
        toast.show();
    }

    private void showEditUrlDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("修改默认网址");

        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        final EditText inputPrimary = new EditText(this);
        inputPrimary.setText(primaryUrl);
        inputPrimary.setHint("主网址 (第一顺位)");
        layout.addView(inputPrimary);

        final EditText inputBackup1 = new EditText(this);
        inputBackup1.setText(backup1Url);
        inputBackup1.setHint("备用1 (局域网)");
        layout.addView(inputBackup1);

        final EditText inputBackup2 = new EditText(this);
        inputBackup2.setText(backup2Url);
        inputBackup2.setHint("备用2 (netlify)");
        layout.addView(inputBackup2);

        final EditText inputBackup3 = new EditText(this);
        inputBackup3.setText(backup3Url);
        inputBackup3.setHint("备用3 (pages 保底)");
        layout.addView(inputBackup3);

        scrollView.addView(layout);
        builder.setView(scrollView);

        builder.setPositiveButton("保存并重新加载", (dialog, which) -> {
            primaryUrl = inputPrimary.getText().toString().trim();
            backup1Url = inputBackup1.getText().toString().trim();
            backup2Url = inputBackup2.getText().toString().trim();
            backup3Url = inputBackup3.getText().toString().trim();

            prefs.edit()
                    .putString("primary_url", primaryUrl)
                    .putString("backup1_url", backup1Url)
                    .putString("backup2_url", backup2Url)
                    .putString("backup3_url", backup3Url)
                    .apply();

            Toast.makeText(MainActivity.this, "已保存，即将从主网址重新加载", Toast.LENGTH_SHORT).show();
            currentLevel = 0; 
            loadUrlWithTimeout();
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        finish(); 
    }

    @Override
    protected void onDestroy() {
        cancelTimeout();
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
