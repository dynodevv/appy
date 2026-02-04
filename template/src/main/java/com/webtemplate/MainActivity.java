package com.webtemplate;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Ultra-minimal WebView activity with zero external dependencies.
 * Reads URL from assets/config.json at startup.
 */
public class MainActivity extends Activity {

    private WebView webView;
    private ProgressBar progressBar;
    private String targetUrl = "https://example.com";
    private boolean statusBarDark = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Load configuration FIRST (before any UI)
        loadConfig();
        
        // Create and set content view FIRST before any window operations
        createUI();
        
        // Setup status bar AFTER setContentView
        setupStatusBar();
        
        // Setup WebView
        setupWebView();
        
        // Load URL
        if (webView != null && targetUrl != null) {
            webView.loadUrl(targetUrl);
        }
    }
    
    private void loadConfig() {
        InputStream inputStream = null;
        BufferedReader reader = null;
        try {
            inputStream = getAssets().open("config.json");
            reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            
            String jsonString = stringBuilder.toString();
            
            // Simple JSON parsing without using JSONObject
            // Parse "url" field
            int urlIndex = jsonString.indexOf("\"url\"");
            if (urlIndex >= 0) {
                int colonIndex = jsonString.indexOf(":", urlIndex);
                int startQuote = jsonString.indexOf("\"", colonIndex);
                int endQuote = jsonString.indexOf("\"", startQuote + 1);
                if (startQuote >= 0 && endQuote > startQuote) {
                    targetUrl = jsonString.substring(startQuote + 1, endQuote);
                }
            }
            
            // Parse "statusBarDark" field
            int statusIndex = jsonString.indexOf("\"statusBarDark\"");
            if (statusIndex >= 0) {
                int colonIndex = jsonString.indexOf(":", statusIndex);
                String afterColon = jsonString.substring(colonIndex + 1).trim();
                statusBarDark = afterColon.startsWith("true");
            }
        } catch (Exception e) {
            // Use defaults if config loading fails
            targetUrl = "https://example.com";
            statusBarDark = false;
        } finally {
            try {
                if (reader != null) reader.close();
                if (inputStream != null) inputStream.close();
            } catch (Exception ignored) {}
        }
    }
    
    private void createUI() {
        // Create root FrameLayout
        FrameLayout rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        
        // Set background color matching status bar
        int backgroundColor = statusBarDark ? Color.parseColor("#1C1B1F") : Color.parseColor("#F5F5F5");
        rootLayout.setBackgroundColor(backgroundColor);
        rootLayout.setFitsSystemWindows(true);
        
        // Create WebView
        webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ));
        
        // Create ProgressBar
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            8
        );
        progressBar.setLayoutParams(progressParams);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        
        // Add views
        rootLayout.addView(webView);
        rootLayout.addView(progressBar);
        
        // Set content view BEFORE any window operations
        setContentView(rootLayout);
    }

    @SuppressWarnings("deprecation")
    private void setupStatusBar() {
        try {
            // Set status bar color
            int statusBarColor = statusBarDark ? Color.parseColor("#1C1B1F") : Color.parseColor("#F5F5F5");
            getWindow().setStatusBarColor(statusBarColor);
            
            // Set navigation bar color
            getWindow().setNavigationBarColor(statusBarColor);
            
            // Set status bar icon color using deprecated but reliable API
            View decorView = getWindow().getDecorView();
            if (decorView != null) {
                int flags = decorView.getSystemUiVisibility();
                if (statusBarDark) {
                    // Dark background, light icons - clear the light flag
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                } else {
                    // Light background, dark icons - set the light flag
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                }
                decorView.setSystemUiVisibility(flags);
            }
        } catch (Exception ignored) {
            // Ignore any status bar errors - not critical
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        if (webView == null) return;
        
        try {
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
            settings.setAllowFileAccess(true);
            settings.setAllowContentAccess(true);
            settings.setLoadWithOverviewMode(true);
            settings.setUseWideViewPort(true);
            settings.setBuiltInZoomControls(true);
            settings.setDisplayZoomControls(false);
            settings.setSupportZoom(true);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    String url = request.getUrl().toString();
                    return handleUrl(view, url);
                }
                
                @Override
                @SuppressWarnings("deprecation")
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return handleUrl(view, url);
                }
                
                private boolean handleUrl(WebView view, String url) {
                    // Handle standard HTTP/HTTPS URLs in WebView
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        return false; // Let WebView handle it
                    }
                    
                    // Handle custom URL schemes by opening external apps
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        return true;
                    } catch (Exception e) {
                        // No app to handle this URL scheme - try to show webpage
                        return false;
                    }
                }
                
                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    super.onPageStarted(view, url, favicon);
                    if (progressBar != null) {
                        progressBar.setVisibility(View.VISIBLE);
                    }
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }
                }
            });

            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onProgressChanged(WebView view, int newProgress) {
                    if (progressBar != null) {
                        progressBar.setProgress(newProgress);
                    }
                }
            });
        } catch (Exception ignored) {
            // Ignore WebView setup errors
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
