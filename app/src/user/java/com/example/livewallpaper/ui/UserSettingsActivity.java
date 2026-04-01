package com.example.livewallpaper.ui;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.example.livewallpaper.R;
import com.example.livewallpaper.sensors.MotionConfig;

public class UserSettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_settings);

        // Ensure MotionConfig is initialized
        MotionConfig.initialize(this);

        SwitchCompat tiltMotionSwitch = findViewById(R.id.switch_tilt_motion);
        if (tiltMotionSwitch != null) {
            tiltMotionSwitch.setChecked(MotionConfig.isGyroMotionEnabled());
            tiltMotionSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                MotionConfig.setGyroMotionEnabled(isChecked);
            });
        }
    }
}
