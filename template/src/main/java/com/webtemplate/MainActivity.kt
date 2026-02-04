package com.webtemplate

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsetsController
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Load config first to get status bar settings
        val config = loadConfig()
        val statusBarDark = config.optBoolean("statusBarDark", false)
        
        // Set status bar appearance based on config
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                if (statusBarDark) {
                    // Dark status bar with light icons
                    controller.setSystemBarsAppearance(
                        0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    )
                    window.statusBarColor = Color.parseColor("#1C1B1F")
                } else {
                    // Light status bar with dark icons
                    controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    )
                    window.statusBarColor = Color.parseColor("#F5F5F5")
                }
            }
        } else {
            @Suppress("DEPRECATION")
            if (statusBarDark) {
                window.statusBarColor = Color.parseColor("#1C1B1F")
                window.decorView.systemUiVisibility = 0
            } else {
                window.statusBarColor = Color.parseColor("#F5F5F5")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
        
        // Create layout programmatically
        val rootLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            // Set background color to match status bar
            setBackgroundColor(if (statusBarDark) Color.parseColor("#1C1B1F") else Color.parseColor("#F5F5F5"))
            // Add padding for status bar
            fitsSystemWindows = true
        }
        
        // Create WebView
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        
        // Create ProgressBar
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                8
            ).apply {
                gravity = android.view.Gravity.TOP
            }
            isIndeterminate = false
            max = 100
        }
        
        rootLayout.addView(webView)
        rootLayout.addView(progressBar)
        setContentView(rootLayout)
        
        setupWebView()
        
        // Load URL from config
        val url = config.optString("url", "https://example.com")
        webView.loadUrl(url)
    }
    
    private fun loadConfig(): JSONObject {
        return try {
            val configStream = assets.open("config.json")
            val reader = BufferedReader(InputStreamReader(configStream))
            val configContent = reader.readText()
            reader.close()
            JSONObject(configContent)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false // Load all URLs in WebView
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
