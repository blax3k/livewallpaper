package com.example.livewallpaper.ui;

import android.os.Bundle;
import android.util.Log;
import android.widget.ListView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.livewallpaper.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SceneListActivity extends AppCompatActivity {
    private static final String TAG = "SceneListActivity";
    private static final String SCENES_FOLDER = "scenes";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "SceneListActivity onCreate called");

        setContentView(R.layout.activity_scene_list);
        Log.d(TAG, "Scene list layout inflated successfully");

        ListView scenesList = findViewById(R.id.scenes_list);
        if (scenesList != null) {
            List<String> sceneFileNames = loadSceneFileNames();
            SceneListAdapter adapter = new SceneListAdapter(this, sceneFileNames);
            scenesList.setAdapter(adapter);
            Log.d(TAG, "Loaded " + sceneFileNames.size() + " scenes");
        } else {
            Log.e(TAG, "Scenes ListView not found!");
            Toast.makeText(this, "Failed to load scenes view", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Load all JSON file names from the assets/scenes folder.
     */
    private List<String> loadSceneFileNames() {
        List<String> sceneFileNames = new ArrayList<>();
        try {
            String[] assetFiles = getAssets().list(SCENES_FOLDER);
            if (assetFiles != null) {
                // Filter for JSON files only
                for (String fileName : assetFiles) {
                    if (fileName.endsWith(".json")) {
                        sceneFileNames.add(fileName);
                        Log.d(TAG, "Found scene file: " + fileName);
                    }
                }
                // Sort the list for consistent display
                java.util.Collections.sort(sceneFileNames);
            } else {
                Log.e(TAG, "Scenes folder not found or is not a directory");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error loading scene files: " + e.getMessage(), e);
            Toast.makeText(this, "Failed to load scenes: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        return sceneFileNames;
    }
}
