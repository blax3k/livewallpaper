package com.example.livewallpaper.ui.editor.adapters;

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
import java.util.Map;

public class SceneListAdapter extends ArrayAdapter<String> {
    private final Context context;
    private final List<String> sceneFileNames;
    private OnSceneInteractionListener interactionListener;
    private Map<String, String> sceneMetadata; // Cache of filename -> timeOfDay

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
     * Set the cached scene metadata (filename -> timeOfDay map)
     */
    public void setSceneMetadata(Map<String, String> metadata) {
        this.sceneMetadata = metadata;
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
        View rootView = convertView.findViewById(R.id.scene_item_root);

        String fileName = sceneFileNames.get(position);
        sceneNameTextView.setText(fileName);

        // Display the cached timeOfDay from metadata
        if (sceneMetadata != null && sceneMetadata.containsKey(fileName)) {
            String timeOfDay = sceneMetadata.get(fileName);
            if (timeOfDay != null && !timeOfDay.isEmpty()) {
                sceneTimeOfDayTextView.setText("Time: " + timeOfDay);
                sceneTimeOfDayTextView.setVisibility(View.VISIBLE);
            } else {
                sceneTimeOfDayTextView.setVisibility(View.GONE);
            }
        } else {
            sceneTimeOfDayTextView.setVisibility(View.GONE);
        }

        // Set up click listener on the entire root row (to open edit activity)
        // This makes the entire row tappable and provides visual feedback
        rootView.setOnClickListener(v -> {
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
        rootView.setOnLongClickListener(v -> {
            if (interactionListener != null) {
                interactionListener.onOptionsMenuRequested(position, fileName, optionsButton);
                return true;
            }
            return false;
        });

        return convertView;
    }
}


