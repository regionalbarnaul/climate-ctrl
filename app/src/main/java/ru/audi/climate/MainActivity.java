package ru.audi.climate;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.GradientDrawable;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
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

    private TextView tempValue;
    private SeekBar tempSlider;
    private TextView fanValue;
    private View[] fanSegs;
    private DirButton[] dirButtons;
    private Button connectBtn;
    private TextView statusText;
    private FlowView flowView;
    private LinearLayout calibBar;
    private Button[] calibBtns;
    private Button calReset, calDone;

    private LinearLayout servoBar;
    private Button[] servoBtns;
    private Button servoMinus, servoPlus, servoE, servoClose;
    private TextView servoLabel;
    private int servoSelected = 0;

    private static final String[] SERVO_NAMES = {
        "S0 — вентилятор", "S1 — тепло/холод", "S2 — направление"
    };

    private double temp = 20.0;
    private int fan = 0;
    private int dir = 2;

    private static final int COLOR_BG_BLOCK  = 0xFF131C2C;
    private static final int COLOR_BG_ACTIVE = 0xFF1A3A3D;
    private static final int COLOR_ACCENT    = 0xFF5CE1E6;
    private static final int COLOR_TEXT      = 0xFFFFFFFF;
    private static final int COLOR_SEG_OFF   = 0xFF1E2A3D;
    private static final int COLOR_BORDER    = 0x335CE1E6;
    private static final int COLOR_DANGER    = 0xFFFF8888;

    private static final String[] FAN_LABELS = {"ВЫКЛ", "MIN", "1", "2", "3", "MAX"};

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
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putDouble("temp", temp);
        outState.putInt("fan", fan);
        outState.putInt("dir", dir);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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

        servoBar   = findViewById(R.id.servoBar);
        servoLabel = findViewById(R.id.servoLabel);
        servoMinus = findViewById(R.id.servoMinus);
        servoPlus  = findViewById(R.id.servoPlus);
        servoE     = findViewById(R.id.servoE);
        servoClose = findViewById(R.id.servoClose);
        servoBtns  = new Button[]{
            findViewById(R.id.servo0),
            findViewById(R.id.servo1),
            findViewById(R.id.servo2)
        };

        dirButtons = new DirButton[]{
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

        // Восстановление состояния после поворота экрана
        if (savedInstanceState != null) {
            temp = savedInstanceState.getDouble("temp", 20.0);
            fan  = savedInstanceState.getInt("fan", 0);
            dir  = savedInstanceState.getInt("dir", 2);
        }

        for (int i = 0; i < dirButtons.length; i++) {
            dirButtons[i].setDirIndex(i);
            final int idx = i;
            dirButtons[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { setDir(idx); }
            });
        }

        Button minus = findViewById(R.id.btnFanMinus);
        Button plus  = findViewById(R.id.btnFanPlus);
        styleBtn(minus, COLOR_BG_BLOCK, COLOR_BORDER, 26, COLOR_ACCENT);
        styleBtn(plus,  COLOR_BG_BLOCK, COLOR_BORDER, 26, COLOR_ACCENT);
        styleBtn(connectBtn, 0xFF0A1E20, COLOR_ACCENT, 14, COLOR_ACCENT);

        gear.setAllCaps(false);
        for (Button b : calibBtns) {
            styleBtn(b, COLOR_BG_BLOCK, COLOR_BORDER, 10, COLOR_TEXT);
        }
        styleBtn(calReset, COLOR_BG_BLOCK, 0x55FF8888, 10, COLOR_DANGER);
        styleBtn(calDone, COLOR_BG_ACTIVE, COLOR_ACCENT, 10, COLOR_ACCENT);

        for (Button b : servoBtns) {
            styleBtn(b, COLOR_BG_BLOCK, COLOR_BORDER, 10, COLOR_TEXT);
        }
        styleBtn(servoMinus, COLOR_BG_BLOCK, COLOR_BORDER, 12, COLOR_ACCENT);
        styleBtn(servoPlus,  COLOR_BG_BLOCK, COLOR_BORDER, 12, COLOR_ACCENT);
        styleBtn(servoClose, COLOR_BG_BLOCK, 0x55FF8888, 10, COLOR_DANGER);
        styleBtn(servoE,     COLOR_BG_ACTIVE, COLOR_ACCENT, 10, COLOR_ACCENT);

        tempSlider.setMax(28);
        tempSlider.setProgress((int)Math.round((temp - 16) / 0.5));
        tempSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                temp = 16 + p * 0.5;
                tempValue.setText(String.format("%.1f°C", temp));
                if (flowView != null) flowView.setTemperature(temp);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {
                int angle = (int)Math.round(((temp - 16) / 14) * 180);
                usbSend("A" + angle);
            }
        });

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

        gear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleServoPanel(); }
        });
        gear.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                toggleFlowCalib();
                return true;
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

        for (int i = 0; i < servoBtns.length; i++) {
            final int idx = i;
            servoBtns[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    servoSelected = idx;
                    highlightServoBtn(idx);
                    usbSend("S" + idx);
                    setStatusMain("Калибровка " + SERVO_NAMES[idx]);
                }
            });
        }
        servoMinus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                usbSend("-");
                setStatusMain(SERVO_NAMES[servoSelected] + "  −5°");
            }
        });
        servoPlus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                usbSend("+");
                setStatusMain(SERVO_NAMES[servoSelected] + "  +5°");
            }
        });
        servoE.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                usbSend("E");
                servoBar.setVisibility(View.GONE);
                setStatusMain("Сохранено в EEPROM");
            }
        });
        servoClose.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                usbSend("X");
                servoBar.setVisibility(View.GONE);
                setStatusMain("Калибровка закрыта");
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
            flowView.setTemperature(temp);
        }

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

    private void toggleServoPanel() {
        if (servoBar.getVisibility() == View.VISIBLE) {
            usbSend("X");
            servoBar.setVisibility(View.GONE);
            setStatusMain("Калибровка закрыта");
        } else {
            if (calibBar.getVisibility() == View.VISIBLE) {
                flowView.stopCalib();
                calibBar.setVisibility(View.GONE);
            }
            servoBar.setVisibility(View.VISIBLE);
            servoSelected = 0;
            highlightServoBtn(0);
            usbSend("CAL");
            usbSend("S0");
            setStatusMain("Калибровка S0 (вентилятор)");
        }
    }

    private void toggleFlowCalib() {
        if (calibBar.getVisibility() == View.VISIBLE) {
            flowView.stopCalib();
            calibBar.setVisibility(View.GONE);
            setStatusMain("Точки сохранены");
        } else {
            if (servoBar.getVisibility() == View.VISIBLE) {
                usbSend("X");
                servoBar.setVisibility(View.GONE);
            }
            calibBar.setVisibility(View.VISIBLE);
            flowView.startCalib(0);
            highlightCalibBtn(0);
            setStatusMain("Тапни по салону: старт стекло");
        }
    }

    private GradientDrawable roundBtn(int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        float r = getResources().getDisplayMetrics().density * radiusDp;
        g.setCornerRadius(r);
        g.setColor(fill);
        if (stroke != 0) {
            float sw = getResources().getDisplayMetrics().density * 1.5f;
            g.setStroke((int)sw, stroke);
        }
        return g;
    }

    private void styleBtn(Button b, int fill, int stroke, int radiusDp, int textColor) {
        b.setAllCaps(false);
        b.setBackground(roundBtn(fill, stroke, radiusDp));
        b.setTextColor(textColor);
        float d = getResources().getDisplayMetrics().density;
        b.setPadding((int)(d*6), 0, (int)(d*6), 0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
    }

    private void highlightCalibBtn(int idx) {
        for (int j = 0; j < calibBtns.length; j++) {
            if (j == idx) {
                styleBtn(calibBtns[j], COLOR_BG_ACTIVE, COLOR_ACCENT, 10, COLOR_ACCENT);
            } else {
                styleBtn(calibBtns[j], COLOR_BG_BLOCK, COLOR_BORDER, 10, COLOR_TEXT);
            }
        }
    }

    private void highlightServoBtn(int idx) {
        for (int j = 0; j < servoBtns.length; j++) {
            if (j == idx) {
                styleBtn(servoBtns[j], COLOR_BG_ACTIVE, COLOR_ACCENT, 10, COLOR_ACCENT);
            } else {
                styleBtn(servoBtns[j], COLOR_BG_BLOCK, COLOR_BORDER, 10, COLOR_TEXT);
            }
        }
        if (servoLabel != null) servoLabel.setText(SERVO_NAMES[idx]);
    }

    private void setDir(int n) {
        dir = n;
        updateDirVisual();
        if (flowView != null) flowView.setDirection(dir);
        usbSend(String.valueOf(dir + 1));
    }

    private void updateDirVisual() {
        for (int i = 0; i < dirButtons.length; i++) {
            dirButtons[i].setActive(i == dir);
        }
    }

    private void changeFan(int delta) {
        fan = Math.max(0, Math.min(5, fan + delta));
        renderFan();
        if (flowView != null) flowView.setFan(fan);
        usbSend("V" + fan);
    }

    private void renderFan() {
        int i = Math.max(0, Math.min(5, fan));
        fanValue.setText(FAN_LABELS[i]);
        if (i == 0 || i == 1 || i == 5) {
            fanValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        } else {
            fanValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f);
        }
        for (int k = 0; k < fanSegs.length; k++) {
            fanSegs[k].setBackgroundColor(k < fan ? COLOR_ACCENT : COLOR_SEG_OFF);
        }
    }

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
                    @Override public void onNewData(byte[] data) { }
                    @Override public void onRunError(Exception e) { }
                });
            usbExecutor.submit(usbIoManager);

            mainHandler.post(new Runnable() {
                @Override public void run() {
                    usbConnected = true;
                    updateConnectButton();
                    setStatusMain("✓ Подключено");
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

    private void setStatusMain(final String t) {
        mainHandler.post(new Runnable() {
            @Override public void run() { statusText.setText(t); }
        });
    }

    private void updateConnectButton() {
        if (usbConnected) {
            connectBtn.setText("ОТКЛЮЧИТЬ");
            connectBtn.setTextColor(COLOR_DANGER);
            connectBtn.setBackground(roundBtn(0xFF1E0A0E, 0xFFFF8888, 14));
        } else {
            connectBtn.setText("ПОДКЛЮЧИТЬ");
            connectBtn.setTextColor(COLOR_ACCENT);
            connectBtn.setBackground(roundBtn(0xFF0A1E20, COLOR_ACCENT, 14));
        }
    }
}
