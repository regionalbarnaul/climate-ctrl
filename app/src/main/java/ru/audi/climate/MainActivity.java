package ru.audi.climate;

import android.app.Activity;
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
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    // ==== UI ====
    private TextView tempValue;
    private SeekBar tempSlider;
    private TextView fanValue;
    private View[] fanSegs;
    private Button[] dirButtons;
    private Button connectBtn;
    private TextView statusText;
    private FlowView flowView;
    private LinearLayout calibBar;
    private Button[] calibBtns;
    private Button calReset, calDone;

    private double temp = 20.0;
    private int fan = 0;
    private int dir = 2;

    private static final int COLOR_BG_BLOCK  = 0xFF131C2C;
    private static final int COLOR_BG_ACTIVE = 0xFF1A3A3D;
    private static final int COLOR_ACCENT    = 0xFF5CE1E6;
    private static final int COLOR_TEXT      = 0xFFFFFFFF;
    private static final int COLOR_SEG_OFF   = 0xFF1E2A3D;

    // ==== USB ====
    private static final String ACTION_USB_PERM = "ru.audi.climate.USB_PERMISSION";
    private UsbManager usbManager;
    private UsbSerialPort usbPort;
    private SerialInputOutputManager usbIoManager;
    private final ExecutorService usbExecutor = Executors.newSingleThreadExecutor();
    private boolean usbConnected = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver permReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            if (!ACTION_USB_PERM.equals(intent.getAction())) return;
            boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
            if (granted) {
                usbExecutor.execute(new Runnable() {
                    @Override public void run() { openPort(); }
                });
            } else {
                setStatusMain("Доступ к USB отклонён");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ---- UI инициализация ----
        tempValue   = findViewById(R.id.tempValue);
        tempSlider  = findViewById(R.id.tempSlider);
        fanValue    = findViewById(R.id.fanValue);
        connectBtn  = findViewById(R.id.connectBtn);
        statusText  = findViewById(R.id.statusText);
        flowView    = findViewById(R.id.flowView);
        calibBar    = findViewById(R.id.calibBar);
        Button gear = findViewById(R.id.btnGear);

        calibBtns = new Button[]{
            findViewById(R.id.cal0), findViewById(R.id.cal1),
            findViewById(R.id.cal2), findViewById(R.id.cal3),
            findViewById(R.id.cal4), findViewById(R.id.cal5),
            findViewById(R.id.cal6), findViewById(R.id.cal7)
        };
        calReset = findViewById(R.id.calReset);
        calDone  = findViewById(R.id.calDone);

        dirButtons = new Button[]{
            findViewById(R.id.btnDir0),
            findViewById(R.id.btnDir1),
            findViewById(R.id.btnDir2),
            findViewById(R.id.btnDir3)
        };
        fanSegs = new View[]{
            findViewById(R.id.seg0),
            findViewById(R.id.seg1),
            findViewById(R.id.seg2),
            findViewById(R.id.seg3),
            findViewById(R.id.seg4)
        };

        for (Button b : dirButtons) {
            b.setAllCaps(false);
            b.setBackgroundColor(COLOR_BG_BLOCK);
            b.setTextColor(COLOR_TEXT);
        }

        Button minus = findViewById(R.id.btnFanMinus);
        Button plus  = findViewById(R.id.btnFanPlus);
        minus.setAllCaps(false);
        plus.setAllCaps(false);
        minus.setBackgroundColor(COLOR_BG_BLOCK);
        plus.setBackgroundColor(COLOR_BG_BLOCK);
        minus.setTextColor(COLOR_ACCENT);
        plus.setTextColor(COLOR_ACCENT);
        connectBtn.setAllCaps(false);
        connectBtn.setBackgroundColor(0xFF0A1E20);
        connectBtn.setTextColor(COLOR_ACCENT);

        gear.setAllCaps(false);
        for (Button b : calibBtns) {
            b.setAllCaps(false);
            b.setBackgroundColor(COLOR_BG_BLOCK);
            b.setTextColor(COLOR_TEXT);
        }
        calReset.setAllCaps(false);
        calReset.setBackgroundColor(COLOR_BG_BLOCK);
        calReset.setTextColor(0xFFFF8888);
        calDone.setAllCaps(false);
        calDone.setBackgroundColor(COLOR_BG_ACTIVE);
        calDone.setTextColor(COLOR_ACCENT);

        tempSlider.setMax(28);
        tempSlider.setProgress(8);
        tempSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                temp = 16 + p * 0.5;
                tempValue.setText(String.format("%.1f°C", temp));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {
                int angle = (int)Math.round(((temp - 16) / 14) * 180);
                usbSend("A" + angle);
            }
        });

        for (int i = 0; i < dirButtons.length; i++) {
            final int idx = i;
            dirButtons[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { setDir(idx); }
            });
        }

        minus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { changeFan(-1); }
        });
        plus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { changeFan(1); }
        });

        connectBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (usbConnected) usbDisconnect();
                else usbConnect();
            }
        });

        // ---- калибровка ----
        gear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (calibBar.getVisibility() == View.VISIBLE) {
                    flowView.stopCalib();
                    calibBar.setVisibility(View.GONE);
                    setStatusMain("Точки сохранены");
                } else {
                    calibBar.setVisibility(View.VISIBLE);
                    flowView.startCalib(0);
                    highlightCalibBtn(0);
                    setStatusMain("Тапни по салону: старт стекло");
                }
            }
        });

        for (int i = 0; i < calibBtns.length; i++) {
            final int idx = i;
            calibBtns[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    flowView.startCalib(idx);
                    highlightCalibBtn(idx);
                }
            });
        }
        calReset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                flowView.resetPoints();
                setStatusMain("Точки сброшены");
            }
        });
        calDone.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                flowView.stopCalib();
                calibBar.setVisibility(View.GONE);
                setStatusMain("Готово, точки сохранены");
            }
        });

        flowView.setListener(new FlowView.OnPointSet() {
            @Override public void onPointSet(int index, float xf, float yf) {
                setStatusMain(String.format("точка %d: x=%.2f y=%.2f", index+1, xf, yf));
            }
        });

        renderFan();
        updateDirVisual();
        tempValue.setText(String.format("%.1f°C", temp));
        if (flowView != null) {
            flowView.setFan(fan);
            flowView.setDirection(dir);
        }

        // ---- USB ----
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        IntentFilter f = new IntentFilter(ACTION_USB_PERM);
        if (Build.VERSION.SDK_INT >= 33)
            registerReceiver(permReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else
            registerReceiver(permReceiver, f);

        setStatusMain("USB не подключён");
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(permReceiver); } catch (Exception ignored) {}
        usbExecutor.execute(new Runnable() {
            @Override public void run() { closePort(); }
        });
        usbExecutor.shutdown();
        super.onDestroy();
    }

    // ================= USB =================

    private void usbConnect() {
        if (usbManager == null) { setStatusMain("USB сервис недоступен"); return; }
        List<UsbSerialDriver> drivers =
            UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (drivers.isEmpty()) {
            setStatusMain("CH340 не найден");
            return;
        }
        UsbSerialDriver d = drivers.get(0);
        if (usbManager.hasPermission(d.getDevice())) {
            usbExecutor.execute(new Runnable() {
                @Override public void run() { openPort(); }
            });
        } else {
            int flag = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
            usbManager.requestPermission(d.getDevice(),
                PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERM), flag));
            setStatusMain("Запрос доступа...");
        }
    }

    private void usbDisconnect() {
        usbExecutor.execute(new Runnable() {
            @Override public void run() { closePort(); }
        });
    }

    private void openPort() {
        List<UsbSerialDriver> drivers =
            UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (drivers.isEmpty()) { setStatusMain("CH340 не найден"); return; }
        UsbSerialDriver d = drivers.get(0);
        UsbDeviceConnection conn = usbManager.openDevice(d.getDevice());
        if (conn == null) { setStatusMain("Не удалось открыть порт"); return; }
        final UsbSerialPort p = d.getPorts().get(0);
        try {
            p.open(conn);
            p.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            usbPort = p;
            usbIoManager = new SerialInputOutputManager(p,
                new SerialInputOutputManager.Listener() {
                    @Override public void onNewData(byte[] data) {
                        final String s = new String(data).trim();
                        if (!s.isEmpty()) mainHandler.post(new Runnable() {
                            @Override public void run() { onUsbLine(s); }
                        });
                    }
                    @Override public void onRunError(Exception e) { /* игнор */ }
                });
            usbExecutor.submit(usbIoManager);

            mainHandler.post(new Runnable() {
                @Override public void run() {
                    usbConnected = true;
                    updateConnectButton();
                    setStatusMain("✓ Подключено");
                    // синхронизация состояния
                    usbSend("V" + fan);
                    usbSend(String.valueOf(dir + 1));
                    usbSend("A" + (int)Math.round(((temp - 16) / 14) * 180));
                }
            });
        } catch (Exception e) {
            setStatusMain("Ошибка порта: " + e.getMessage());
        }
    }

    private void closePort() {
        try { if (usbIoManager != null) usbIoManager.stop(); } catch (Exception ignored) {}
        usbIoManager = null;
        try { if (usbPort != null) usbPort.close(); } catch (Exception ignored) {}
        usbPort = null;
        mainHandler.post(new Runnable() {
            @Override public void run() {
                usbConnected = false;
                updateConnectButton();
                setStatusMain("USB отключён");
            }
        });
    }

    private void usbSend(final String cmd) {
        if (!usbConnected || usbPort == null) return;
        usbExecutor.execute(new Runnable() {
            @Override public void run() {
                try { usbPort.write((cmd + "\n").getBytes(), 1000); }
                catch (Exception ignored) {}
            }
        });
    }

    // Сюда приходят строки от Arduino
    private void onUsbLine(String line) {
        // Можно распарсить статус от платы. Пока просто игнор.
        // Например, если Arduino шлёт "TEMP=23.5" — можно показывать.
    }

    private void setStatusMain(final String t) {
        mainHandler.post(new Runnable() {
            @Override public void run() { statusText.setText(t); }
        });
    }

    private void updateConnectButton() {
        if (usbConnected) {
            connectBtn.setText("ОТКЛЮЧИТЬ");
            connectBtn.setTextColor(0xFFFF8888);
        } else {
            connectBtn.setText("ПОДКЛЮЧИТЬ");
            connectBtn.setTextColor(COLOR_ACCENT);
        }
    }

    // ================= UI helpers =================

    private void highlightCalibBtn(int idx) {
        for (int j = 0; j < calibBtns.length; j++) {
            calibBtns[j].setBackgroundColor(j == idx ? COLOR_BG_ACTIVE : COLOR_BG_BLOCK);
            calibBtns[j].setTextColor(j == idx ? COLOR_ACCENT : COLOR_TEXT);
        }
    }

    private void setDir(int n) {
        dir = n;
        updateDirVisual();
        if (flowView != null) flowView.setDirection(dir);
        usbSend(String.valueOf(dir + 1));
    }

    private void updateDirVisual() {
        for (int i = 0; i < dirButtons.length; i++) {
            Button b = dirButtons[i];
            if (i == dir) {
                b.setBackgroundColor(COLOR_BG_ACTIVE);
                b.setTextColor(COLOR_ACCENT);
            } else {
                b.setBackgroundColor(COLOR_BG_BLOCK);
                b.setTextColor(COLOR_TEXT);
            }
        }
    }

    private void changeFan(int delta) {
        fan = Math.max(0, Math.min(5, fan + delta));
        renderFan();
        if (flowView != null) flowView.setFan(fan);
        usbSend("V" + fan);
    }

    private void renderFan() {
        fanValue.setText(String.valueOf(fan));
        for (int i = 0; i < fanSegs.length; i++) {
            fanSegs[i].setBackgroundColor(i < fan ? COLOR_ACCENT : COLOR_SEG_OFF);
        }
    }
}
