package ru.audi.climate;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import com.getcapacitor.BridgeActivity;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends BridgeActivity {

    private static final String TAG = "Climate";
    private static final String ACTION = "ru.audi.climate.USB_PERMISSION";
    private static final int MAX_ATTEMPTS = 100;

    private UsbManager usbManager;
    private UsbSerialPort port;
    private SerialInputOutputManager ioManager;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private WebView webView;
    private boolean bridgeAttached = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int attempts = 0;

    private final BroadcastReceiver permReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            if (!ACTION.equals(intent.getAction())) return;
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                io.execute(() -> openPort());
            } else {
                toJs("ERR: permission denied");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        toast("onCreate");
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        IntentFilter f = new IntentFilter(ACTION);
        if (Build.VERSION.SDK_INT >= 33)
            registerReceiver(permReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else
            registerReceiver(permReceiver, f);
        scheduleAttach();
    }

    @Override protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        scheduleAttach();
    }

    @Override protected void onPostResume() {
        super.onPostResume();
        scheduleAttach();
    }

    private void scheduleAttach() {
        attempts = 0;
        tryAttach();
    }

    private void tryAttach() {
        if (bridgeAttached) return;
        WebView wv = grabWebView();
        if (wv != null) {
            webView = wv;
            try {
                wv.addJavascriptInterface(new UsbBridge(), "AndroidSerial");
                bridgeAttached = true;
                Log.i(TAG, "AndroidSerial attached on attempt " + attempts);
                toast("Мост прицеплен");
                wv.post(() -> wv.evaluateJavascript(
                    "window.dispatchEvent(new Event('androidserial-ready'))", null));
            } catch (Throwable e) {
                Log.e(TAG, "attach failed", e);
                toast("err: " + e.getMessage());
            }
            return;
        }
        attempts++;
        if (attempts < MAX_ATTEMPTS) {
            handler.postDelayed(this::tryAttach, 100);
        } else {
            Log.e(TAG, "WebView never found");
            toast("WebView не найден");
        }
    }

    private WebView grabWebView() {
        try {
            if (getBridge() != null && getBridge().getWebView() != null)
                return getBridge().getWebView();
        } catch (Throwable ignored) {}
        try {
            View decor = getWindow() != null ? getWindow().getDecorView() : null;
            return findWebView(decor);
        } catch (Throwable ignored) {}
        return null;
    }

    private WebView findWebView(View v) {
        if (v == null) return null;
        if (v instanceof WebView) return (WebView) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                WebView r = findWebView(g.getChildAt(i));
                if (r != null) return r;
            }
        }
        return null;
    }

    private void toast(String msg) {
        try { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) {}
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(permReceiver); } catch (Exception ignored) {}
        io.execute(this::closePort);
        io.shutdown();
        super.onDestroy();
    }

    private void toJs(String msg) {
        String esc = msg.replace("\\", "\\\\").replace("'", "\\'")
                        .replace("\n", "\\n").replace("\r", "");
        if (webView == null) return;
        webView.post(() -> webView.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent('serial-data',{detail:'" + esc + "'}))", null));
    }

    public class UsbBridge {
        @JavascriptInterface public void connect() {
            if (usbManager == null) return;
            List<UsbSerialDriver> drivers =
                UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
            if (drivers.isEmpty()) { toJs("ERR: CH340 not found"); return; }
            UsbSerialDriver d = drivers.get(0);
            if (usbManager.hasPermission(d.getDevice())) {
                io.execute(() -> openPort());
            } else {
                int flag = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
                usbManager.requestPermission(d.getDevice(),
                    PendingIntent.getBroadcast(MainActivity.this, 0, new Intent(ACTION), flag));
            }
        }
        @JavascriptInterface public void send(String data) {
            io.execute(() -> {
                try { if (port != null) port.write(data.getBytes(), 1000); }
                catch (Exception e) { toJs("ERR: " + e.getMessage()); }
            });
        }
        @JavascriptInterface public void disconnect() { io.execute(() -> closePort()); }
    }

    private void openPort() {
        if (usbManager == null) return;
        List<UsbSerialDriver> drivers =
            UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (drivers.isEmpty()) return;
        UsbSerialDriver d = drivers.get(0);
        UsbDeviceConnection conn = usbManager.openDevice(d.getDevice());
        if (conn == null) { toJs("ERR: openDevice"); return; }
        UsbSerialPort p = d.getPorts().get(0);
        try {
            p.open(conn);
            p.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            port = p;
            ioManager = new SerialInputOutputManager(p, new SerialInputOutputManager.Listener() {
                @Override public void onNewData(byte[] data) { toJs(new String(data)); }
                @Override public void onRunError(Exception e) { toJs("ERR: " + e.getMessage()); }
            });
            io.submit(ioManager);
            toJs("OK: connected");
        } catch (Exception e) { toJs("ERR: " + e.getMessage()); }
    }

    private void closePort() {
        try { if (ioManager != null) ioManager.stop(); } catch (Exception ignored) {}
        ioManager = null;
        try { if (port != null) port.close(); } catch (Exception ignored) {}
        port = null;
        toJs("OK: disconnected");
    }
}
