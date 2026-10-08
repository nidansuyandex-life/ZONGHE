package com.example.myapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechIntent: Intent? = null
    private var pendingStartAfterPermission = false
    private var isSpeechListening = false
    private val requestAudioCode = 1001

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()

        // 给网页提供 Android 原生语音识别能力。
        webView.addJavascriptInterface(AndroidSpeechBridge(), "AndroidSpeech")

        // 原项目这里错误地加载了不存在的 index.js，导致 App 打开后白屏。
        webView.loadUrl("file:///android_asset/index.html")

        initSpeechRecognizer()
    }

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isSpeechListening = true
                callJs("window.onNativeSpeechState && window.onNativeSpeechState('listening')")
            }

            override fun onBeginningOfSpeech() {
                callJs("window.onNativeSpeechState && window.onNativeSpeechState('speaking')")
            }

            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                isSpeechListening = false
                callJs("window.onNativeSpeechState && window.onNativeSpeechState('processing')")
            }

            override fun onError(error: Int) {
                isSpeechListening = false
                callJs(
                    "window.onNativeSpeechError && window.onNativeSpeechError(" +
                        JSONObjectEscaper.quote(nativeSpeechError(error)) + ")"
                )
            }

            override fun onResults(results: Bundle?) {
                isSpeechListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim().orEmpty()
                if (text.isEmpty()) {
                    callJs("window.onNativeSpeechError && window.onNativeSpeechError('没有听清，请再说一次')")
                } else {
                    callJs(
                        "window.onNativeSpeechResult && window.onNativeSpeechResult(" +
                            JSONObjectEscaper.quote(text) + ")"
                    )
                }
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.SIMPLIFIED_CHINESE.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    private fun startNativeSpeech() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            callJs("window.onNativeSpeechError && window.onNativeSpeechError('设备没有可用的语音识别服务')")
            return
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingStartAfterPermission = true
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), requestAudioCode)
            return
        }

        if (speechRecognizer == null || speechIntent == null) initSpeechRecognizer()
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.startListening(speechIntent)
            callJs("window.onNativeSpeechState && window.onNativeSpeechState('starting')")
        } catch (e: Exception) {
            callJs(
                "window.onNativeSpeechError && window.onNativeSpeechError(" +
                    JSONObjectEscaper.quote(e.message ?: "无法启动语音识别") + ")"
            )
        }
    }

    private fun stopNativeSpeech() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != requestAudioCode) return

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            callJs("window.onNativeSpeechPermission && window.onNativeSpeechPermission(true)")
            if (pendingStartAfterPermission) {
                pendingStartAfterPermission = false
                startNativeSpeech()
            }
        } else {
            pendingStartAfterPermission = false
            callJs("window.onNativeSpeechPermission && window.onNativeSpeechPermission(false)")
            callJs("window.onNativeSpeechError && window.onNativeSpeechError('麦克风权限未开启，请在系统设置中允许“日常”使用麦克风')")
        }
    }

    private fun callJs(script: String) {
        runOnUiThread {
            if (::webView.isInitialized) {
                webView.evaluateJavascript(script, null)
            }
        }
    }

    override fun onDestroy() {
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
        try {
            webView.removeJavascriptInterface("AndroidSpeech")
            webView.destroy()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    inner class AndroidSpeechBridge {
        @JavascriptInterface
        fun start() {
            runOnUiThread { startNativeSpeech() }
        }

        @JavascriptInterface
        fun stop() {
            runOnUiThread { stopNativeSpeech() }
        }

        @JavascriptInterface
        fun isAvailable(): Boolean {
            return SpeechRecognizer.isRecognitionAvailable(this@MainActivity)
        }
    }

    private fun nativeSpeechError(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "录音失败，请检查麦克风"
            SpeechRecognizer.ERROR_CLIENT -> "语音识别客户端错误，请重试"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "没有麦克风权限"
            SpeechRecognizer.ERROR_NETWORK -> "网络错误，请检查网络"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时，请重试"
            SpeechRecognizer.ERROR_NO_MATCH -> "没有听清，请再说一次"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音识别服务正忙，请稍后再试"
            SpeechRecognizer.ERROR_SERVER -> "语音识别服务器错误"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有检测到说话，请点击麦克风后立即说话"
            else -> "语音识别失败（$error）"
        }
    }

    private object JSONObjectEscaper {
        fun quote(value: String): String {
            return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t") + "\""
        }
    }
}
