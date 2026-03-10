package com.vauth.webviewapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * WebView app optimized for Cipta Desa.
 * Floating buttons REMOVED for clean UI.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val DEFAULT_URL = "https://ciptadesa.com"
    }

    private var webView: WebView? = null
    private var progressBar: ProgressBar? = null
    private var container: FrameLayout? = null
    private var loadingOverlay: FrameLayout? = null
    
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var geolocationCallback: GeolocationPermissions.Callback? = null
    private var geolocationOrigin: String? = null
    private var pendingPermissionRequest: PermissionRequest? = null
    private var isFirstPageLoad = true

    // File chooser launcher
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val clipData = data?.clipData
            val uris = if (clipData != null) {
                (0 until clipData.itemCount).map { 
                    clipData.getItemAt(it).uri 
                }.toTypedArray()
            } else {
                data?.data?.let { arrayOf(it) } ?: arrayOf()
            }
            fileUploadCallback?.onReceiveValue(uris)
        } else {
            fileUploadCallback?.onReceiveValue(null)
        }
        fileUploadCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            container = FrameLayout(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            
            webView = WebView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            
            progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    8
                ).apply {
                    gravity = Gravity.TOP
                }
                isIndeterminate = false
                max = 100
                progressDrawable?.setTint(ContextCompat.getColor(context, android.R.color.holo_blue_dark))
            }
            
            loadingOverlay = FrameLayout(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(ContextCompat.getColor(context, android.R.color.white))
                visibility = View.VISIBLE 
                
                val loadingSpinner = ProgressBar(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.CENTER
                    }
                }
                addView(loadingSpinner)
            }
            
            container?.addView(webView)
            container?.addView(progressBar)
            container?.addView(loadingOverlay)
            
            setContentView(container)
            setupBackPressHandler()
            setupWebView()
            
            webView?.loadUrl(DEFAULT_URL)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            finish()
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView?.canGoBack() == true) {
                    webView?.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView?.settings?.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            setGeolocationEnabled(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        
        webView?.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar?.visibility = View.VISIBLE
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar?.visibility = View.GONE
                if (isFirstPageLoad) {
                    loadingOverlay?.visibility = View.GONE
                    isFirstPageLoad = false
                }
            }
            
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("whatsapp:")) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    return true
                }
                return false
            }
        }
        
        webView?.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar?.progress = newProgress
            }
            
            override fun onShowFileChooser(w: WebView?, f: ValueCallback<Array<Uri>>?, p: FileChooserParams?): Boolean {
                fileUploadCallback = f
                p?.createIntent()?.let { fileChooserLauncher.launch(it) }
                return true
            }
        }
    }

    override fun onDestroy() {
        webView?.let { wv ->
            wv.loadUrl("about:blank")
            container?.removeView(wv)
            wv.destroy()
        }
        super.onDestroy()
    }
}
