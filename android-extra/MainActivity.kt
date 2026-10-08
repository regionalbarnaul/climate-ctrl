package ru.audi.climate

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.getcapacitor.BridgeActivity
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.util.concurrent.Executors

class MainActivity : BridgeActivity() {

    private var usbManager: UsbManager? = null
    private var port: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private val io = Executors.newSingleThreadExecutor()
    private var webView: WebView? = null
    private var bridgeAttached = false

    private val handler = Handler(Looper.getMainLooper())
    private var attempts = 0
    private val maxAttempts = 60

    private val ACTION = "ru.audi.climate.USB_PERMISSION"

    private val permReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != ACTION) return
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                io.execute { openPort() }
            } else toJs("ERR: permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val f = IntentFilter(ACTION)
        if (Build.VERSION.SDK_INT >= 33)
            registerReceiver(permReceiver, f, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(permReceiver, f)
        scheduleAttach()
    }

    override fun onResume() {
        super.onResume()
        scheduleAttach()
    }

    private fun scheduleAttach() {
        attempts = 0
        tryAttach()
    }

    private fun tryAttach() {
        if (bridgeAttached) return
        val wv = grabWebView()
        if (wv != null) {
            webView = wv
            try {
                wv.addJavascriptInterface(UsbBridge(), "AndroidSerial")
                bridgeAttached = true
                wv.post {
                    wv.evaluateJavascript(
                        "window.dispatchEvent(new Event('androidserial-ready'))", null)
                }
                android.util.Log.i("Climate", "AndroidSerial bridge attached")
            } catch (e: Throwable) {
                android.util.Log.e("Climate", "attach failed: ${e.message}")
            }
            return
        }
        attempts++
        if (attempts < maxAttempts) {
            handler.postDelayed({ tryAttach() }, 100)
        } else {
            android.util.Log.e("Climate", "bridge.webView never appeared")
        }
    }

    private fun grabWebView(): WebView? {
        try {
            val b = bridge ?: return null
            val wv = b.webView ?: return null
            return wv
        } catch (_: Throwable) {}
        try {
            val wv2 = getBridge()?.webView
            if (wv2 != null) return wv2
        } catch (_: Throwable) {}
        return null
    }

    override fun onDestroy() {
        try { unregisterReceiver(permReceiver) } catch (_: Exception) {}
        io.execute { closePort() }; io.shutdown()
        super.onDestroy()
    }

    private fun toJs(msg: String) {
        val esc = msg.replace("\\", "\\\\").replace("'", "\\'")
                     .replace("\n", "\\n").replace("\r", "")
        webView?.post {
            webView?.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('serial-data',{detail:'$esc'}))", null)
        }
    }

    inner class UsbBridge {
        @JavascriptInterface fun connect() {
            val m = usbManager ?: return
            val d = UsbSerialProber.getDefaultProber().findAllDrivers(m).firstOrNull()
                ?: return toJs("ERR: CH340 not found")
            if (m.hasPermission(d.device)) io.execute { openPort() }
            else {
                val flag = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                m.requestPermission(d.device,
                    PendingIntent.getBroadcast(this@MainActivity, 0, Intent(ACTION), flag))
            }
        }
        @JavascriptInterface fun send(data: String) {
            io.execute { try { port?.write(data.toByteArray(), 1000) }
                         catch (e: Exception) { toJs("ERR: ${e.message}") } }
        }
        @JavascriptInterface fun disconnect() { io.execute { closePort() } }
    }

    private fun openPort() {
        val m = usbManager ?: return
        val d = UsbSerialProber.getDefaultProber().findAllDrivers(m).firstOrNull() ?: return
        val conn = m.openDevice(d.device) ?: return toJs("ERR: openDevice")
        val p = d.ports[0]
        try {
            p.open(conn)
            p.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            port = p
            ioManager = SerialInputOutputManager(p, object : SerialInputOutputManager.Listener {
                override fun onNewData(data: ByteArray) = toJs(String(data, Charsets.UTF_8))
                override fun onRunError(e: Exception) = toJs("ERR: ${e.message}")
            }).also { io.submit(it) }
            toJs("OK: connected")
        } catch (e: Exception) { toJs("ERR: ${e.message}") }
    }

    private fun closePort() {
        try { ioManager?.stop() } catch (_: Exception) {}; ioManager = null
        try { port?.close() } catch (_: Exception) {}; port = null
        toJs("OK: disconnected")
    }
}
