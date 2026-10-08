package ru.audi.climate;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
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

    private double temp = 20.0;
    private int fan = 0;
    private int dir = 2;

    private static final int COLOR_BG_BLOCK  = 0xFF131C2C;
    private static final int COLOR_BG_ACTIVE = 0xFF1A3A3D;
    private static final int COLOR_ACCENT    = 0xFF5CE1E6;
    private static final int COLOR_TEXT      = 0xFFFFFFFF;
    private static final int COLOR_SEG_OFF   = 0xFF1E2A3D;

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
                statusText.setText("USB пока не подключён (мост на этапе 4)");
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
