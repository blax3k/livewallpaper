package com.hashilab.dev.editor.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.livewallpaper.R;
import com.example.livewallpaper.logging.TimberLog;
import com.hashilab.dev.editor.network.WebEditorApiClient;
import com.hashilab.dev.editor.utils.AppPreferences;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    private EditText emailInput;
    private EditText passwordInput;
    private Button signInButton;
    private ProgressBar progressBar;
    private TextView errorText;

    private volatile boolean destroyed = false;

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyed = true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        emailInput   = findViewById(R.id.input_email);
        passwordInput = findViewById(R.id.input_password);
        signInButton = findViewById(R.id.btn_sign_in);
        progressBar  = findViewById(R.id.progress_bar);
        errorText    = findViewById(R.id.text_error);

        signInButton.setOnClickListener(v -> attemptLogin());
    }

    private void attemptLogin() {
        String email    = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (email.isEmpty()) {
            errorText.setText("Email is required");
            errorText.setVisibility(View.VISIBLE);
            return;
        }
        if (password.isEmpty()) {
            errorText.setText("Password is required");
            errorText.setVisibility(View.VISIBLE);
            return;
        }

        errorText.setVisibility(View.GONE);
        signInButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        String serverUrl = AppPreferences.getServerUrl(this);
        new Thread(() -> {
            try {
                String cookie = WebEditorApiClient.login(serverUrl, email, password);
                AppPreferences.setSessionCookie(this, cookie);
                TimberLog.d(TAG, "Login successful");

                if (!destroyed) runOnUiThread(() -> {
                    Intent intent = new Intent(this, ProjectsActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
            } catch (WebEditorApiClient.AuthException e) {
                if (!destroyed) runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    signInButton.setEnabled(true);
                    errorText.setText(e.getMessage());
                    errorText.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                TimberLog.e(TAG, "Login failed", e);
                if (!destroyed) runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    signInButton.setEnabled(true);
                    errorText.setText("Could not connect to server");
                    errorText.setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }
}
