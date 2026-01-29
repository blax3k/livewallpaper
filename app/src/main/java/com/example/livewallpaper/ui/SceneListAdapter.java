package com.example.livewallpaper.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.livewallpaper.R;

import java.util.List;

public class SceneListAdapter extends ArrayAdapter<String> {
    private final Context context;
    private final List<String> sceneFileNames;
    private OnSceneInteractionListener interactionListener;

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
        Button optionsButton = convertView.findViewById(R.id.scene_options_button);

        String fileName = sceneFileNames.get(position);
        sceneNameTextView.setText(fileName);

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
}


