package com.vauth.webviewapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.view.Gravity
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * WebView app optimized for Cipta Desa.
 * Floating buttons disabled for clean UI.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val DEFAULT_URL = "https://ciptadesa.com"
        private const val GITHUB_URL = "https://github.com/vauth/android-web"
    }

    private var webView: WebView? = null
    private var progressBar: ProgressBar? = null
    private var container: FrameLayout? = null
    private var loadingOverlay: FrameLayout? = null
    
    // Tombol dideklarasikan sebagai null agar tidak memakan memori
    private var fabGithub: FloatingActionButton? = null
    private var fabBack: FloatingActionButton? = null
    private var fabForward: FloatingActionButton? = null
    
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var geolocationCallback: GeolocationPermissions.Callback? = null
    private var geolocationOrigin: String? = null
    private var pendingPermissionRequest: PermissionRequest? = null
    private var isDestroyed = false
    private var isFirstPageLoad = true

    // File chooser launcher
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error handling file chooser result", e)
            fileUploadCallback?.onReceiveValue(null)
        } finally {
            fileUploadCallback = null
        }
    }

    // Geolocation permission launcher
    private val geolocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        try {
            val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            
            geolocationCallback?.invoke(geolocationOrigin, locationGranted, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling geolocation permission result", e)
            geolocationCallback?.invoke(geolocationOrigin, false, false)
        } finally {
            geolocationCallback = null
            geolocationOrigin = null
        }
    }

    // Media permission launcher
    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        try {
            val allGranted = permissions.values.all { it }
            
            pendingPermissionRequest?.let { request ->
                if (allGranted) {
                    request.grant(request.resources)
                } else {
                    request.deny()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling media permission result", e)
            pendingPermissionRequest?.deny()
        } finally {
            pendingPermissionRequest = null
        }
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
                // Safe way to set color across versions
                progressDrawable?.setTint(ContextCompat.getColor(context, R.color.progressBarColor))
            }
            
            loadingOverlay = FrameLayout(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(ContextCompat.getColor(context, R.color.loadingBackground))
                visibility = View.VISIBLE 
                
                val loadingSpinner = ProgressBar(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.CENTER
                    }
                    indeterminateDrawable?.setTint(ContextCompat.getColor(context, R.color.progressBarColor))
                }
                addView(loadingSpinner)
            }
            
            container?.addView(webView)
            container?.addView(progressBar)
            container?.addView(loadingOverlay)
            
            // TOMBOL DINONAKTIFKAN DI SINI
            // createFloatingButtons() 
            
            setContentView(container)
            setupBackPressHandler()
            setupWebView()
            
            // Load URL
            val websiteUrl = BuildConfig.WEBSITE_URL
            if (websiteUrl.isNullOrBlank()) {
                webView?.loadUrl(DEFAULT_URL)
            } else {
                webView?.loadUrl(websiteUrl)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in onCreate", e)
            finish()
        }
    }

    // Fungsi tetap ada tapi tidak dipanggil di onCreate agar tombol tidak muncul
    private fun createFloatingButtons() {
        // Kosong atau biarkan saja, selama tidak dipanggil di onCreate, tombol tidak akan muncul
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
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            setGeolocationEnabled(true)
            
            // OPTIMASI: Matikan popup otomatis agar iklan tidak mengganggu user
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            
            userAgentString = "$userAgentString WebViewApp/1.0"
        }
        
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
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

    private fun hasPermissions(permissions: Array<String>): Boolean {
        return permissions.all { 
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED 
        }
    }

    override fun onDestroy() {
        isDestroyed = true
        webView?.let { wv ->
            wv.loadUrl("about:blank")
            container?.removeView(wv)
            wv.destroy()
        }
        super.onDestroy()
    }
}
