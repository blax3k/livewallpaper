package com.example.livewallpaper.ui.editor.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import com.example.livewallpaper.logging.TimberLog;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ExpandableListView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.livewallpaper.gl.GLWallpaperService;
import com.example.livewallpaper.R;
import com.example.livewallpaper.logging.TimberLog;
import com.example.livewallpaper.scene.models.SceneData;
import com.example.livewallpaper.ui.editor.managers.SceneFileManager;
import com.example.livewallpaper.ui.editor.adapters.SceneListExpandableAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SceneListActivity extends AppCompatActivity {
    private static final String TAG = "SceneListActivity";

    private SceneFileManager sceneFileManager;
    private List<String> sceneFileNames;
    private SceneListExpandableAdapter adapter;
    private ExpandableListView scenesList;

    // Data structures for grouping scenes by TimeOfDay
    private List<SceneData.TimeOfDay> timeOfDayGroups;
    private Map<SceneData.TimeOfDay, List<String>> scenesGroupedByTimeOfDay;

    // Activity result launcher for opening EditSceneActivity
    private final ActivityResultLauncher<Intent> editSceneActivityLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                // Scene was saved in EditSceneActivity, refresh the list and metadata
                TimberLog.d(TAG, "Scene was saved, refreshing scene list");
                sceneFileNames = loadSceneFileNames();

                // Reload metadata and rebuild grouped data
                var sceneMetadata = sceneFileManager.loadSceneMetadata();
                buildGroupedSceneData(sceneFileNames, sceneMetadata);

                // Update adapter with new grouped data
                if (adapter != null) {
                    adapter.updateData(timeOfDayGroups, scenesGroupedByTimeOfDay);
                }

                // Also refresh the wallpaper since scene was modified
                GLWallpaperService.refreshSceneList(this);
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TimberLog.d(TAG, "SceneListActivity onCreate called");

        setContentView(R.layout.activity_scene_list);
        TimberLog.d(TAG, "Scene list layout inflated successfully");

        // Initialize SceneFileManager to access persistent storage
        sceneFileManager = new SceneFileManager(this, null);

        // Load scenes immediately - no permission needed since we use app-specific storage
        loadAndDisplayScenes();
    }

    /**
     * Load and display the scenes in the expandable list view grouped by TimeOfDay
     */
    private void loadAndDisplayScenes() {
        scenesList = findViewById(R.id.scenes_list);
        if (scenesList != null) {
            sceneFileNames = loadSceneFileNames();

            // Load all scene metadata at once for efficient caching
            var sceneMetadata = sceneFileManager.loadSceneMetadata();

            // Build the grouped data structure
            buildGroupedSceneData(sceneFileNames, sceneMetadata);

            // Create and set the expandable adapter
            adapter = new SceneListExpandableAdapter(this, timeOfDayGroups, scenesGroupedByTimeOfDay, sceneMetadata);
            scenesList.setAdapter(adapter);

            // Set up click listener for child items (to edit scene)
            scenesList.setOnChildClickListener((parent, v, groupPosition, childPosition, id) -> {
                SceneData.TimeOfDay timeOfDay = timeOfDayGroups.get(groupPosition);
                List<String> sceneList = scenesGroupedByTimeOfDay.get(timeOfDay);
                if (sceneList != null && childPosition < sceneList.size()) {
                    String sceneFileName = sceneList.get(childPosition);
                    TimberLog.d(TAG, "Scene selected for editing: " + sceneFileName);
                    openEditScene(sceneFileName);
                }
                return false;
            });

            // Set up long-press context menu for delete options
            scenesList.setOnCreateContextMenuListener((menu, v, menuInfo) -> {
                if (menuInfo instanceof ExpandableListView.ExpandableListContextMenuInfo) {
                    ExpandableListView.ExpandableListContextMenuInfo info =
                        (ExpandableListView.ExpandableListContextMenuInfo) menuInfo;

                    // Only show menu for child items (scenes), not group headers
                    if (ExpandableListView.getPackedPositionType(info.packedPosition) ==
                        ExpandableListView.PACKED_POSITION_TYPE_CHILD) {

                        int groupPosition = ExpandableListView.getPackedPositionGroup(info.packedPosition);
                        int childPosition = ExpandableListView.getPackedPositionChild(info.packedPosition);

                        SceneData.TimeOfDay timeOfDay = timeOfDayGroups.get(groupPosition);
                        List<String> sceneList = scenesGroupedByTimeOfDay.get(timeOfDay);

                        if (sceneList != null && childPosition < sceneList.size()) {
                            String sceneFileName = sceneList.get(childPosition);
                            menu.add(0, 1, 0, "Edit").setOnMenuItemClickListener(item -> {
                                openEditScene(sceneFileName);
                                return true;
                            });
                            menu.add(0, 2, 0, "Delete").setOnMenuItemClickListener(item -> {
                                showDeleteConfirmationDialog(sceneFileName);
                                return true;
                            });
                        }
                    }
                }
            });

            // Expand all groups by default
            expandAllGroups();

            TimberLog.d(TAG, "Loaded " + sceneFileNames.size() + " scenes grouped by TimeOfDay");
        } else {
            TimberLog.e(TAG, "Scenes ExpandableListView not found!");
            Toast.makeText(this, "Failed to load scenes view", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Build a data structure that groups scenes by TimeOfDay.
     * Ensures all TimeOfDay categories are present, even if empty.
     */
    private void buildGroupedSceneData(List<String> sceneFileNames, Map<String, String> sceneMetadata) {
        // Initialize the groups list with all TimeOfDay values
        timeOfDayGroups = new ArrayList<>();
        for (SceneData.TimeOfDay timeOfDay : SceneData.TimeOfDay.values()) {
            timeOfDayGroups.add(timeOfDay);
        }

        // Initialize the map to hold scenes for each TimeOfDay
        scenesGroupedByTimeOfDay = new HashMap<>();
        for (SceneData.TimeOfDay timeOfDay : SceneData.TimeOfDay.values()) {
            scenesGroupedByTimeOfDay.put(timeOfDay, new ArrayList<>());
        }

        // Group the scenes by their TimeOfDay
        for (String fileName : sceneFileNames) {
            String timeOfDayStr = sceneMetadata != null ? sceneMetadata.get(fileName) : null;

            // Default to DAY if metadata is missing
            SceneData.TimeOfDay timeOfDay = SceneData.TimeOfDay.DAY;
            if (timeOfDayStr != null && !timeOfDayStr.isEmpty()) {
                try {
                    timeOfDay = SceneData.TimeOfDay.valueOf(timeOfDayStr);
                } catch (IllegalArgumentException e) {
                    TimberLog.w(TAG, "Unknown TimeOfDay value: " + timeOfDayStr + ", defaulting to DAY");
                }
            }

            scenesGroupedByTimeOfDay.get(timeOfDay).add(fileName);
        }

        // Sort scenes within each group for consistent display
        for (List<String> sceneList : scenesGroupedByTimeOfDay.values()) {
            java.util.Collections.sort(sceneList);
        }
    }

    /**
     * Expand all TimeOfDay groups by default to show all scenes.
     */
    private void expandAllGroups() {
        if (adapter != null) {
            for (int i = 0; i < adapter.getGroupCount(); i++) {
                scenesList.expandGroup(i);
            }
        }
    }

    /**
     * Load all JSON file names from the persistent scenes directory.
     */
    private List<String> loadSceneFileNames() {
        List<String> fileNames = new ArrayList<>();

        String persistentPath = sceneFileManager.getPersistentScenesDirectoryPath();
        File persistentDir = new File(persistentPath);

        if (persistentDir.exists() && persistentDir.isDirectory()) {
            File[] files = persistentDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    fileNames.add(file.getName());
                    TimberLog.d(TAG, "Found scene file: " + file.getName());
                }
                // Sort the list for consistent display
                java.util.Collections.sort(fileNames);
            } else {
                TimberLog.e(TAG, "Error listing files in persistent directory");
            }
        } else {
            TimberLog.e(TAG, "Persistent scenes directory does not exist or is not a directory");
        }

        return fileNames;
    }


    /**
     * Show a confirmation dialog before deleting a scene.
     */
    private void showDeleteConfirmationDialog(String sceneFileName) {
        new AlertDialog.Builder(this)
            .setTitle("Delete Scene")
            .setMessage("Are you sure you want to delete '" + sceneFileName + "'?")
            .setPositiveButton("Yes", (dialog, which) -> {
                deleteScene(sceneFileName);
            })
            .setNegativeButton("No", (dialog, which) -> {
                dialog.dismiss();
            })
            .show();
    }

    /**
     * Delete a scene file from persistent storage and update the UI.
     */
    private void deleteScene(String sceneFileName) {
        if (sceneFileManager.deleteScene(sceneFileName)) {
            // Remove from file names list
            sceneFileNames.remove(sceneFileName);

            // Reload metadata and rebuild grouped data
            var sceneMetadata = sceneFileManager.loadSceneMetadata();
            buildGroupedSceneData(sceneFileNames, sceneMetadata);

            // Update adapter with new grouped data
            if (adapter != null) {
                adapter.updateData(timeOfDayGroups, scenesGroupedByTimeOfDay);
            }

            Toast.makeText(this, "Scene deleted: " + sceneFileName, Toast.LENGTH_SHORT).show();
            TimberLog.d(TAG, "Scene deleted successfully: " + sceneFileName);

            // Refresh the scene list in the running wallpaper
            GLWallpaperService.refreshSceneList(this);
        } else {
            Toast.makeText(this, "Failed to delete scene: " + sceneFileName, Toast.LENGTH_SHORT).show();
            TimberLog.e(TAG, "Failed to delete scene: " + sceneFileName);
        }
    }

    private void openEditScene(String sceneFileName) {
        try {
            Intent intent = new Intent(this, EditSceneActivity.class);
            intent.putExtra(EditSceneActivity.EXTRA_SCENE_FILE_NAME, sceneFileName);
            editSceneActivityLauncher.launch(intent);
        } catch (Exception e) {
            TimberLog.e(TAG, "Error opening edit scene: " + e.getMessage(), e);
            Toast.makeText(this, "Failed to open scene editor: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_scene_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_reset_scenes) {
            showResetConfirmationDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Show a confirmation dialog before resetting the scene list to defaults.
     */
    private void showResetConfirmationDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Reset Scene List")
            .setMessage("This will discard all changes and restore default scenes. Continue?")
            .setPositiveButton("Yes", (dialog, which) -> resetSceneList())
            .setNegativeButton("No", null)
            .show();
    }

    /**
     * Reset the scene list to default and reload the UI.
     */
    private void resetSceneList() {
        try {
            sceneFileManager.resetToDefaultScenes();
            sceneFileNames = loadSceneFileNames();

            // Reload metadata and rebuild grouped data
            var sceneMetadata = sceneFileManager.loadSceneMetadata();
            buildGroupedSceneData(sceneFileNames, sceneMetadata);

            // Update adapter with new grouped data
            if (adapter != null) {
                adapter.updateData(timeOfDayGroups, scenesGroupedByTimeOfDay);
            }

            // Re-expand all groups
            expandAllGroups();

            Toast.makeText(this, "Scene list reset to defaults", Toast.LENGTH_SHORT).show();
            TimberLog.d(TAG, "Scene list successfully reset to defaults");

            // Refresh the scene list in the running wallpaper
            GLWallpaperService.refreshSceneList(this);
        } catch (Exception e) {
            TimberLog.e(TAG, "Error resetting scenes: " + e.getMessage(), e);
            Toast.makeText(this, "Failed to reset scenes: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
