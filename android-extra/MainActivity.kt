package ru.audi.climate

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
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
    }

    override fun onResume() {
        super.onResume()
        attachBridge()
    }

    override fun onPostResume() {
        super.onPostResume()
        attachBridge()
    }

    private fun attachBridge() {
        if (bridgeAttached) return
        val wv: WebView? = try { bridge?.webView } catch (_: Throwable) { null }
        if (wv == null) return
        webView = wv
        wv.addJavascriptInterface(UsbBridge(), "AndroidSerial")
        bridgeAttached = true
        wv.post {
            wv.evaluateJavascript(
                "window.dispatchEvent(new Event('androidserial-ready'))", null)
        }
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
