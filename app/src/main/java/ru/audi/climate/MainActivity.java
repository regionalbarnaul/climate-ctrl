package ru.audi.climate;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView tempValue;
    private SeekBar tempSlider;
    private TextView fanValue;
    private View[] fanSegs;
    private Button[] dirButtons;
    private Button connectBtn;
    private TextView statusText;
    private FlowView flowView;
    private LinearLayout calibBar;
    private Button[] calibBtns;   // 8 штук: старты 0..3, концы 4..7
    private Button calReset, calDone;

    private double temp = 20.0;
    private int fan = 0;
    private int dir = 2;

    private static final int COLOR_BG_BLOCK  = 0xFF131C2C;
    private static final int COLOR_BG_ACTIVE = 0xFF1A3A3D;
    private static final int COLOR_ACCENT    = 0xFF5CE1E6;
    private static final int COLOR_TEXT      = 0xFFFFFFFF;
    private static final int COLOR_SEG_OFF   = 0xFF1E2A3D;

    private static final String[] CAL_NAMES = {
        "Ст. стекло", "Ст. лицо", "Ст. ноги", "Ст. стек+ног",
        "Кн. стекло", "Кн. лицо", "Кн. ноги", "Кн. стек+ног"
    };

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
            @Override public void onStopTrackingTouch(SeekBar sb) {}
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
                statusText.setText("USB пока не подключён");
            }
        });

        gear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (calibBar.getVisibility() == View.VISIBLE) {
                    flowView.stopCalib();
                    calibBar.setVisibility(View.GONE);
                    statusText.setText("Точки сохранены");
                } else {
                    calibBar.setVisibility(View.VISIBLE);
                    flowView.startCalib(0);
                    highlightCalibBtn(0);
                    statusText.setText("Тапни по салону: " + CAL_NAMES[0]);
                }
            }
        });

        for (int i = 0; i < calibBtns.length; i++) {
            final int idx = i;
            calibBtns[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    flowView.startCalib(idx);
                    highlightCalibBtn(idx);
                    statusText.setText("Тапни по салону: " + CAL_NAMES[idx]);
                }
            });
        }

        calReset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                flowView.resetPoints();
                statusText.setText("Точки сброшены");
            }
        });

        calDone.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                flowView.stopCalib();
                calibBar.setVisibility(View.GONE);
                statusText.setText("Готово, точки сохранены");
            }
        });

        flowView.setListener(new FlowView.OnPointSet() {
            @Override public void onPointSet(int index, float xf, float yf) {
                String n = (index >= 0 && index < CAL_NAMES.length) ? CAL_NAMES[index] : "?";
                statusText.setText(String.format("%s: x=%.2f y=%.2f", n, xf, yf));
            }
        });

        renderFan();
        updateDirVisual();
        tempValue.setText(String.format("%.1f°C", temp));

        if (flowView != null) {
            flowView.setFan(fan);
            flowView.setDirection(dir);
        }
    }

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
    }

    private void renderFan() {
        fanValue.setText(String.valueOf(fan));
        for (int i = 0; i < fanSegs.length; i++) {
            fanSegs[i].setBackgroundColor(i < fan ? COLOR_ACCENT : COLOR_SEG_OFF);
        }
    }
}
