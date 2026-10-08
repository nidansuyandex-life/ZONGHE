package com.example.myapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

// 注意：这里导入你的AAR包中的类，包名需要根据你实际的SDK去填
// 例如：import com.iflytek.sparkchain.core.SparkChain

class MainActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. 初始化 WebView 并加载 JS 文件
        val webView = WebView(this)
        setContentView(webView)
        
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        
        // 加载 assets 目录下的 JS 库
        webView.loadUrl("file:///android_asset/index.js")
        
        // 或者加载HTML，并在HTML中引用JS
        // webView.loadUrl("file:///android_asset/index.html")
        
        webView.webViewClient = WebViewClient()

        // 2. 初始化讯飞 SDK (根据实际API修改)
        initIflytekSDK()
    }

    private fun initIflytekSDK() {
        try {
            // 假设你的AAR中有一个 SparkChain 类
            // SparkChain.getInst().init(this)
            println("讯飞SDK初始化成功")
        } catch (e: Exception) {
            e.printStackTrace()
            println("讯飞SDK初始化失败: ${e.message}")
        }
    }
}