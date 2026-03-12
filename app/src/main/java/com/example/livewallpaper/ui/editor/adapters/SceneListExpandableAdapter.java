package com.example.livewallpaper.ui.editor.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;

import com.example.livewallpaper.R;
import com.example.livewallpaper.scene.models.SceneData;

import java.util.List;
import java.util.Map;

public class SceneListExpandableAdapter extends BaseExpandableListAdapter {
    private final Context context;
    private final List<SceneData.TimeOfDay> groups;
    private final Map<SceneData.TimeOfDay, List<String>> children;

    /**
     * Constructor for the expandable adapter
     */
    public SceneListExpandableAdapter(Context context,
                                      List<SceneData.TimeOfDay> groups,
                                      Map<SceneData.TimeOfDay, List<String>> children,
                                      Map<String, String> sceneMetadata) {
        this.context = context;
        this.groups = groups;
        this.children = children;
    }

    @Override
    public int getGroupCount() {
        return groups.size();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        SceneData.TimeOfDay timeOfDay = groups.get(groupPosition);
        List<String> sceneList = children.get(timeOfDay);
        return sceneList != null ? sceneList.size() : 0;
    }

    @Override
    public Object getGroup(int groupPosition) {
        return groups.get(groupPosition);
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        SceneData.TimeOfDay timeOfDay = groups.get(groupPosition);
        List<String> sceneList = children.get(timeOfDay);
        if (sceneList != null) {
            return sceneList.get(childPosition);
        }
        return null;
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context)
                .inflate(R.layout.list_group_time_of_day, parent, false);
        }

        TextView groupTitle = (TextView) convertView;

        SceneData.TimeOfDay timeOfDay = groups.get(groupPosition);
        List<String> sceneList = children.get(timeOfDay);
        int sceneCount = sceneList != null ? sceneList.size() : 0;

        groupTitle.setText(timeOfDay.toString() + " (" + sceneCount + ")");

        return convertView;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context)
                .inflate(R.layout.list_item_scene_child, parent, false);
        }

        TextView sceneNameTextView = (TextView) convertView;

        SceneData.TimeOfDay timeOfDay = groups.get(groupPosition);
        List<String> sceneList = children.get(timeOfDay);
        if (sceneList == null || childPosition >= sceneList.size()) {
            return convertView;
        }

        String fileName = sceneList.get(childPosition);
        sceneNameTextView.setText(fileName);

        // Store scene filename in tag for use in click listener
        sceneNameTextView.setTag(fileName);

        return convertView;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    /**
     * Update the adapter with new scene data.
     *
     * @param groups the list of TimeOfDay groups
     * @param children the map of TimeOfDay to scene filenames
     */
    public void updateData(List<SceneData.TimeOfDay> groups,
                          Map<SceneData.TimeOfDay, List<String>> children) {
        this.groups.clear();
        this.groups.addAll(groups);
        this.children.clear();
        this.children.putAll(children);
        notifyDataSetChanged();
    }
}

