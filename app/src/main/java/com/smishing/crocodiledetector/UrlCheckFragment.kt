package com.smishing.crocodiledetector

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment

class UrlCheckFragment : Fragment() {

    private lateinit var webView: WebView

    private val BACKEND_URL = "https://crocodile-backend.onrender.com"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        webView = WebView(requireContext()).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            loadUrl(BACKEND_URL, mapOf("ngrok-skip-browser-warning" to "true"))
        }
        return webView
    }

    override fun onDestroyView() {
        webView.destroy()
        super.onDestroyView()
    }
}