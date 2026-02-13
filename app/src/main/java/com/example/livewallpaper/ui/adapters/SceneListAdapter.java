package com.example.livewallpaper.ui.adapters;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.livewallpaper.R;
import com.example.livewallpaper.scene.SceneData;
import com.example.livewallpaper.scene.SceneLoader;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

public class SceneListAdapter extends ArrayAdapter<String> {
    private static final String TAG = "SceneListAdapter";
    private final Context context;
    private final List<String> sceneFileNames;
    private OnSceneInteractionListener interactionListener;
    private String persistentScenesPath;

    /**
     * Callback interface for scene list interactions
     */
    public interface OnSceneInteractionListener {
        void onSceneSelected(int position, String sceneFileName);
        void onOptionsMenuRequested(int position, String sceneFileName, View anchorView);
    }

    public SceneListAdapter(Context context, List<String> sceneFileNames) {
        super(context, R.layout.list_item_scene, sceneFileNames);
        this.context = context;
        this.sceneFileNames = sceneFileNames;
    }

    /**
     * Set the path to persistent scenes directory for loading scene data
     */
    public void setPersistentScenesPath(String path) {
        this.persistentScenesPath = path;
    }

    /**
     * Set the listener for scene interactions
     */
    public void setOnSceneInteractionListener(OnSceneInteractionListener listener) {
        this.interactionListener = listener;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context)
                .inflate(R.layout.list_item_scene, parent, false);
        }

        TextView sceneNameTextView = convertView.findViewById(R.id.scene_file_name);
        TextView sceneTimeOfDayTextView = convertView.findViewById(R.id.scene_time_of_day);
        Button optionsButton = convertView.findViewById(R.id.scene_options_button);

        String fileName = sceneFileNames.get(position);
        sceneNameTextView.setText(fileName);

        // Load and display the timeOfDay for this scene
        String timeOfDayText = loadTimeOfDayForScene(fileName);
        if (timeOfDayText != null && !timeOfDayText.isEmpty()) {
            sceneTimeOfDayTextView.setText("Time: " + timeOfDayText);
            sceneTimeOfDayTextView.setVisibility(View.VISIBLE);
        } else {
            sceneTimeOfDayTextView.setVisibility(View.GONE);
        }

        // Set up click listener for the text (to open edit activity)
        sceneNameTextView.setOnClickListener(v -> {
            if (interactionListener != null) {
                interactionListener.onSceneSelected(position, fileName);
            }
        });

        // Set up options button (ellipsis) click listener
        optionsButton.setOnClickListener(v -> {
            if (interactionListener != null) {
                interactionListener.onOptionsMenuRequested(position, fileName, v);
            }
        });

        // Set up long-press listener on the entire item
        convertView.setOnLongClickListener(v -> {
            if (interactionListener != null) {
                interactionListener.onOptionsMenuRequested(position, fileName, optionsButton);
                return true;
            }
            return false;
        });

        return convertView;
    }

    /**
     * Load the timeOfDay value for a scene from its JSON file
     */
    private String loadTimeOfDayForScene(String fileName) {
        try {
            Gson gson = new Gson();
            SceneData sceneData;

            if (persistentScenesPath != null) {
                // Load from persistent storage
                File sceneFile = new File(persistentScenesPath, fileName);
                if (sceneFile.exists()) {
                    try (FileReader reader = new FileReader(sceneFile)) {
                        sceneData = gson.fromJson(reader, SceneData.class);
                    }
                } else {
                    return null;
                }
            } else {
                // If no persistent path is set, return null (can't load from assets easily)
                return null;
            }

            if (sceneData != null && sceneData.timeOfDay != null) {
                return sceneData.timeOfDay.toString();
            }
        } catch (IOException e) {
            Log.w(TAG, "Error loading timeOfDay for scene " + fileName + ": " + e.getMessage());
        }

        return null;
    }
}


