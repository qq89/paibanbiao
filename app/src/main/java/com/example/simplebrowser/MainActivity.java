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
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String DEFAULT_PRIMARY = "http://jjjjjj8889.gicp.net:28136/schedule";
    private static final String DEFAULT_BACKUP  = "http://192.168.66.44:28136/schedule";

    private WebView webView;
    private SharedPreferences prefs;
    private String primaryUrl;
    private String backupUrl;

    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;
    private boolean fallbackTriggered = false;

    private int clickCount = 0;
    private long lastClickTime = 0;
    private static final long CLICK_INTERVAL = 600;

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
                cancelTimeout();
                fallbackTriggered = false;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (!fallbackTriggered && request.isForMainFrame()) {
                    switchToBackup();
                }
            }
        });

        loadPrimaryWithTimeout();
    }

    private void loadPrimaryWithTimeout() {
        fallbackTriggered = false;
        cancelTimeout();

        timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (!fallbackTriggered) {
                    switchToBackup();
                }
            }
        };
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

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            long now = System.currentTimeMillis();
            if (now - lastClickTime > CLICK_INTERVAL) {
                clickCount = 0;
            }
            lastClickTime = now;
            clickCount++;

            // 连点 3 下：闪现当前网址
            if (clickCount == 3) {
                String currentUrl = webView.getUrl();
                if (currentUrl == null || currentUrl.isEmpty() || currentUrl.equals("about:blank")) {
                    currentUrl = primaryUrl;
                }
                showUrlToast(currentUrl);
            }

            // 连点 5 下：弹出修改网址窗口
            if (clickCount >= 5) {
                clickCount = 0;
                showEditUrlDialog();
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    // 自定义 Toast 防止长网址被截断
    private void showUrlToast(String url) {
        Toast toast = new Toast(getApplicationContext());
        
        TextView tv = new TextView(MainActivity.this);
        tv.setText("当前网址:\n" + url);
        tv.setTextColor(Color.WHITE);
        tv.setBackgroundColor(Color.parseColor("#CC000000")); // 半透明黑色
        tv.setPadding(40, 40, 40, 40);
        tv.setTextSize(16);
        tv.setGravity(Gravity.CENTER);
        // 允许自动换行并最多显示5行
        tv.setMaxLines(5); 
        
        toast.setView(tv);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER, 0, 0); // 屏幕正中间闪现
        toast.show();
    }

    private void showEditUrlDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("修改默认网址");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
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
