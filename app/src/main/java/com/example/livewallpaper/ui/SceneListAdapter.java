package com.example.livewallpaper.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.livewallpaper.R;

import java.util.List;

public class SceneListAdapter extends ArrayAdapter<String> {
    private final Context context;
    private final List<String> sceneFileNames;

    public SceneListAdapter(Context context, List<String> sceneFileNames) {
        super(context, R.layout.list_item_scene, sceneFileNames);
        this.context = context;
        this.sceneFileNames = sceneFileNames;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context)
                .inflate(R.layout.list_item_scene, parent, false);
        }

        TextView sceneNameTextView = convertView.findViewById(R.id.scene_file_name);
        String fileName = sceneFileNames.get(position);
        sceneNameTextView.setText(fileName);

        return convertView;
    }
}
