package com.streamvault.player.youtube

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JsNTransformer @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    private val webViewThread = HandlerThread("JsNWebView").apply { start() }
    private val handler = Handler(webViewThread.looper)
    private var webView: WebView? = null
    private var loadedHash: Int? = null
    private var nTransformFn: String? = null
    private var discoveryDone = false
    private val lock = Any()

    private fun webView(): WebView = synchronized(lock) {
        webView ?: createWebView().also { webView = it }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        // WebView MUST be created on the main thread
        val latch = CountDownLatch(1)
        var wv: WebView? = null
        Handler(Looper.getMainLooper()).post {
            try {
                wv = WebView(appContext).apply {
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
                    }
                }
            } catch (e: Exception) {
                Log.e("JsNTransformer", "WebView creation failed: ${e.message}")
            } finally {
                latch.countDown()
            }
        }
        latch.await(5, TimeUnit.SECONDS)
        return wv ?: throw IllegalStateException("WebView creation failed or timed out")
    }

    fun prepare(js: String) {
        val hash = js.hashCode()
        if (loadedHash == hash && discoveryDone) {
            Log.d("JsNTransformer", "prepare: already loaded (hash=$hash)")
            return
        }
        nTransformFn = null
        discoveryDone = false
        val latch = CountDownLatch(1)
        handler.post {
            val wv = webView()
            wv.loadDataWithBaseURL(
                "https://www.youtube.com",
                "<html><head></head><body><script>$js</script></body></html>",
                "text/html",
                "utf-8",
                null
            )
            handler.postDelayed({ latch.countDown() }, 5000)
        }
        latch.await(30, TimeUnit.SECONDS)
        loadedHash = hash
        Log.d("JsNTransformer", "prepare complete, hash=$hash")
    }

    fun transformUrl(streamUrl: String): String? {
        val idx = findQueryParamIndex(streamUrl, "n") ?: return null
        val valueStart = idx + "n=".length
        var valueEnd = streamUrl.indexOf('&', valueStart)
        if (valueEnd < 0) valueEnd = streamUrl.length
        val rawN = streamUrl.substring(valueStart, valueEnd)
        if (rawN.isBlank()) return null

        val transformed = transformNValue(streamUrl, rawN) ?: return null
        val encoded = URLEncoder.encode(transformed, "UTF-8")
        Log.d("JsNTransformer", "transformUrl: ${rawN.length}chars -> ${encoded.length}chars")
        return streamUrl.substring(0, valueStart) + encoded + streamUrl.substring(valueEnd)
    }

    fun transformNValue(streamUrl: String, rawN: String): String? {
        val fn = nTransformFn
        if (fn != null) {
            return evaluateNTransform(streamUrl, rawN, fn)
        }
        if (!discoveryDone && loadedHash != null) {
            return discoverAndTransform(streamUrl, rawN)
        }
        return null
    }

    private fun evaluateNTransform(streamUrl: String, rawN: String, fnExpr: String): String? {
        val wv = synchronized(lock) { webView } ?: return null
        val latch = CountDownLatch(1)
        val result = arrayOf<String?>(null)

        val script = when (fnExpr) {
            "ob_get_n" -> {
                "(function(){try{var ob=new _yt_player.Ob(${JSONObject.quote(streamUrl)},true);var n=ob.get('n');if(n==null||n===undefined)return null;return n;}catch(e){return null;}})()"
            }
            "ob_ps_get_n" -> {
                "(function(){try{var ob=new _yt_player.Ob(${JSONObject.quote(streamUrl)},true);if(typeof ob.pS==='function')ob.pS();var n=ob.get('n');if(n==null||n===undefined)return null;return n;}catch(e){return null;}})()"
            }
            else -> {
                "(function(){try{var url=${JSONObject.quote(streamUrl)};var rawN=${JSONObject.quote(rawN)};var n=$fnExpr;if(n==null||n===undefined)return null;return n;}catch(e){return null;}})()"
            }
        }

        handler.post {
            wv.evaluateJavascript(script) { res ->
                result[0] = res
                latch.countDown()
            }
        }
        if (!latch.await(10, TimeUnit.SECONDS)) return null
        val r = result[0] ?: return null
        val clean = r.removeSurroundingQuotes()
        return when {
            clean.isEmpty() || clean == "null" || clean == "undefined" -> null
            else -> clean
        }
    }

    private fun discoverAndTransform(realUrl: String, rawN: String): String? {
        val wv = synchronized(lock) { webView } ?: return null
        val latch = CountDownLatch(1)
        val result = arrayOf<String?>(null)

        Log.d("JsNTransformer", "discovery: scan _yt_player for n-transform function...")

        val url = JSONObject.quote(realUrl)
        val nVal = JSONObject.quote(rawN)

        val scanScript = """
        (function(){
            var out=[];
            var testUrl=$url;
            var nVal=$nVal;

            // 1. Try Ob class with the URL — if it has n= in query, Ob.get("n") should work
            if(typeof _yt_player!=='undefined' && typeof _yt_player.Ob==='function'){
                try{
                    var ob=new _yt_player.Ob(testUrl,true);
                    var n=ob.get('n');
                    if(n!=null && n!==undefined && String(n).length>=8 && String(n)!==nVal){
                        out.push('OB_WORKS:'+n);
                    }else{
                        out.push('OB_NULL:n='+n);
                    }
                }catch(e){out.push('OB_ERR:'+e);}
            }

            // 2. Scan all _yt_player keys for functions containing the n-transform pattern
            //    (split/swap/join operations on array-like structures)
            try{
                var keys=Object.keys(_yt_player||{});
                for(var i=0;i<keys.length;i++){
                    try{
                        var v=_yt_player[keys[i]];
                        if(typeof v!=='function')continue;
                        var s=String(v);
                        // Look for the n-transform pattern: array manipulation + join
                        if(s.indexOf('.split')!==-1 && s.indexOf('.join')!==-1 && s.length>200 && s.length<5000){
                            // Try calling it with the n-value
                            try{
                                var result=v(nVal);
                                if(result!=null && result!==undefined && String(result)!==nVal && String(result).length>=8){
                                    out.push('FN:'+keys[i]+':'+String(result).substring(0,50));
                                }
                            }catch(e2){}
                        }
                    }catch(e){}
                }
            }catch(e){out.push('scan_err:'+e);}

            return out.join('\\n');
        })()
        """.trimIndent()

        handler.post {
            wv.evaluateJavascript(scanScript) { scanResult ->
                val scanClean = scanResult?.removeSurroundingQuotes()?.replace("\\n", "\n") ?: ""
                Log.d("JsNTransformer", "discovery results:\n$scanClean")

                val lines = scanClean.lines()
                for (line in lines) {
                    if (line.startsWith("OB_WORKS:")) {
                        val nResult = line.substringAfter("OB_WORKS:")
                        Log.d("JsNTransformer", "FOUND Ob.get('n'): $nResult")
                        nTransformFn = "ob_get_n"
                        discoveryDone = true
                        result[0] = nResult
                        latch.countDown()
                        return@evaluateJavascript
                    }
                    if (line.startsWith("FN:")) {
                        val parts = line.split(":")
                        if (parts.size >= 2) {
                            val fnName = parts[1]
                            val nResult = parts[2]
                            Log.d("JsNTransformer", "FOUND FN: $fnName -> $nResult")
                            nTransformFn = "_yt_player['$fnName']"
                            discoveryDone = true
                            result[0] = nResult
                            latch.countDown()
                            return@evaluateJavascript
                        }
                    }
                }

                Log.w("JsNTransformer", "No working n-transform found — will retry on next call")
                discoveryDone = false
                result[0] = null
                latch.countDown()
            }
        }
        latch.await(30, TimeUnit.SECONDS)
        return result[0]
    }

    private fun findQueryParamIndex(url: String, param: String): Int? {
        val marker = "$param="
        var start = 0
        while (start < url.length) {
            val idx = url.indexOf(marker, start)
            if (idx < 0) return null
            if (idx == 0 || url[idx - 1] == '?' || url[idx - 1] == '&') {
                return idx
            }
            start = idx + marker.length
        }
        return null
    }

    private fun String.removeSurroundingQuotes(): String {
        if (length >= 2 && this[0] == '"' && this[lastIndex] == '"') {
            return substring(1, lastIndex)
        }
        return this
    }
}
