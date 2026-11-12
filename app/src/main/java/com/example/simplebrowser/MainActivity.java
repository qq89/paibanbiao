package com.example.simplebrowser;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    // ====== 默认网址（首次打开使用，之后可在APP内修改）======
    private static final String DEFAULT_PRIMARY = "http://jjjjjj8889.gicp.net:28136/schedule";
    private static final String DEFAULT_BACKUP  = "http://192.168.66.44:28136/schedule";

    private WebView webView;
    private SharedPreferences prefs;
    private String primaryUrl;
    private String backupUrl;

    // 超时控制
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;
    private boolean fallbackTriggered = false;

    // 5次点击修改网址
    private int clickCount = 0;
    private long lastClickTime = 0;
    private static final long CLICK_INTERVAL = 600; // 600ms内连续点击才算

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("app_config", Context.MODE_PRIVATE);
        primaryUrl = prefs.getString("primary_url", DEFAULT_PRIMARY);
        backupUrl  = prefs.getString("backup_url",  DEFAULT_BACKUP);

        webView = new WebView(this);
        setContentView(webView);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(WebView view, String url) {
                // 页面加载完成，取消超时计时器
                cancelTimeout();
                fallbackTriggered = false;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                // 主网址发生网络错误时立即切换（不一定等到2秒）
                if (!fallbackTriggered && request.isForMainFrame()) {
                    switchToBackup();
                }
            }
        });

        // 用于5次点击修改网址：点击WebView本身
        webView.setOnClickListener(v -> handleSecretClick());

        loadPrimaryWithTimeout();
    }

    private void loadPrimaryWithTimeout() {
        fallbackTriggered = false;
        cancelTimeout();

        timeoutRunnable = () -> {
            if (!fallbackTriggered) {
                // 2秒内没有加载完成，切换到备用网址
                switchToBackup();
            }
        };
        // 2000毫秒 = 2秒
        timeoutHandler.postDelayed(timeoutRunnable, 2000);

        webView.loadUrl(primaryUrl);
    }

    private void switchToBackup() {
        if (fallbackTriggered) return;
        fallbackTriggered = true;
        cancelTimeout();
        webView.stopLoading();
        webView.loadUrl(backupUrl);
    }

    private void cancelTimeout() {
        if (timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    // ====== 重写触摸分发事件，确保连续快速点击可靠触发 ======
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (ev.getAction() == android.view.MotionEvent.ACTION_DOWN) {
            long now = System.currentTimeMillis();
            if (now - lastClickTime > CLICK_INTERVAL) {
                clickCount = 0; // 间隔超过600ms，重新计数
            }
            lastClickTime = now;
            clickCount++;

            if (clickCount >= 5) {
                clickCount = 0;
                showEditUrlDialog();
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private void showEditUrlDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("修改默认网址");

        // 用一个简单的纵向布局放两个输入框
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        final EditText inputPrimary = new EditText(this);
        inputPrimary.setText(primaryUrl);
        inputPrimary.setHint("主网址");
        layout.addView(inputPrimary);

        final EditText inputBackup = new EditText(this);
        inputBackup.setText(backupUrl);
        inputBackup.setHint("备用网址");
        layout.addView(inputBackup);

        builder.setView(layout);

        builder.setPositiveButton("保存并重新加载", (dialog, which) -> {
            String newPrimary = inputPrimary.getText().toString().trim();
            String newBackup  = inputBackup.getText().toString().trim();

            if (newPrimary.isEmpty() || newBackup.isEmpty()) {
                Toast.makeText(MainActivity.this, "网址不能为空", Toast.LENGTH_SHORT).show();
                return;
            }

            primaryUrl = newPrimary;
            backupUrl  = newBackup;

            prefs.edit()
                    .putString("primary_url", primaryUrl)
                    .putString("backup_url", backupUrl)
                    .apply();

            Toast.makeText(MainActivity.this, "已保存，即将重新加载", Toast.LENGTH_SHORT).show();
            loadPrimaryWithTimeout();
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    // ====== 切换到后台就“自杀” ======
    @Override
    protected void onPause() {
        super.onPause();
        // 老人查看完排班表，切到后台即关闭，不占资源
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
