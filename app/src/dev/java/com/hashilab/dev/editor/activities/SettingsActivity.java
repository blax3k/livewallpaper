package com.hashilab.dev.editor.activities;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.livewallpaper.R;
import com.example.livewallpaper.gl.GLWallpaperService;
import com.example.livewallpaper.logging.TimberLog;
import com.example.livewallpaper.sensors.MotionConfig;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        setupScrollToggle();
        setupGyroToggle();
        setupTimeOverrideSpinner();
        setupApiServerUrlField();
    }

    private void setupScrollToggle() {
        Switch toggle = findViewById(R.id.toggle_scroll_motion);
        if (toggle == null) {
            TimberLog.e(TAG, "Scroll motion toggle not found!");
            return;
        }
        toggle.setChecked(MotionConfig.isScrollMotionEnabled());
        toggle.setOnCheckedChangeListener((btn, isChecked) -> {
            MotionConfig.setScrollMotionEnabled(isChecked);
            TimberLog.d(TAG, "Scroll motion toggled: " + (isChecked ? "ON" : "OFF"));
            Toast.makeText(this, "Scroll motion " + (isChecked ? "enabled" : "disabled"),
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void setupGyroToggle() {
        Switch toggle = findViewById(R.id.toggle_gyro_motion);
        if (toggle == null) {
            TimberLog.e(TAG, "Gyro motion toggle not found!");
            return;
        }
        toggle.setChecked(MotionConfig.isGyroMotionEnabled());
        toggle.setOnCheckedChangeListener((btn, isChecked) -> {
            MotionConfig.setGyroMotionEnabled(isChecked);
            TimberLog.d(TAG, "Gyro motion toggled: " + (isChecked ? "ON" : "OFF"));
            Toast.makeText(this, "Gyro motion " + (isChecked ? "enabled" : "disabled"),
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void setupTimeOverrideSpinner() {
        Spinner spinner = findViewById(R.id.spinner_time_override);
        if (spinner == null) {
            TimberLog.e(TAG, "Time override spinner not found!");
            return;
        }

        List<String> options = new ArrayList<>();
        options.add(MotionConfig.OVERRIDE_AUTO);
        for (int m = 0; m < 1440; m += 30) {
            options.add(String.valueOf(m));
        }

        List<String> displayLabels = new ArrayList<>();
        displayLabels.add(MotionConfig.OVERRIDE_AUTO);
        for (int m = 0; m < 1440; m += 30) {
            displayLabels.add(String.format(java.util.Locale.US, "%02d:%02d", m / 60, m % 60));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, displayLabels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        String currentOverride = MotionConfig.getTimeOfDayOverride();
        int initialPosition = options.indexOf(currentOverride);
        if (initialPosition >= 0) {
            spinner.setSelection(initialPosition);
        }

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = options.get(position);
                if (!selected.equals(MotionConfig.getTimeOfDayOverride())) {
                    MotionConfig.setTimeOfDayOverride(selected);
                    TimberLog.d(TAG, "Time of day override changed to: " + selected);
                    Toast.makeText(SettingsActivity.this,
                            "Time override: " + displayLabels.get(position),
                            Toast.LENGTH_SHORT).show();
                    GLWallpaperService.triggerSceneCycle();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupApiServerUrlField() {
        EditText urlField = findViewById(R.id.edit_api_server_url);
        if (urlField == null) {
            TimberLog.e(TAG, "API server URL field not found!");
            return;
        }

        SharedPreferences prefs = getSharedPreferences(
                ProjectBrowserActivity.PREFS_NAME, Context.MODE_PRIVATE);
        String stored = prefs.getString(
                ProjectBrowserActivity.PREF_SERVER_URL,
                ProjectBrowserActivity.DEFAULT_SERVER_URL);
        urlField.setText(stored);

        urlField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String url = s.toString().trim();
                prefs.edit()
                     .putString(ProjectBrowserActivity.PREF_SERVER_URL, url)
                     .apply();
                TimberLog.d(TAG, "API server URL updated: " + url);
            }
        });
    }
}

