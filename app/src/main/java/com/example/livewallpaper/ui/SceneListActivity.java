package com.example.livewallpaper.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.livewallpaper.GLWallpaperService;
import com.example.livewallpaper.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SceneListActivity extends AppCompatActivity {
    private static final String TAG = "SceneListActivity";

    private SceneFileManager sceneFileManager;
    private List<String> sceneFileNames;
    private SceneListAdapter adapter;
    private ListView scenesList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "SceneListActivity onCreate called");

        setContentView(R.layout.activity_scene_list);
        Log.d(TAG, "Scene list layout inflated successfully");

        // Initialize SceneFileManager to access persistent storage
        sceneFileManager = new SceneFileManager(this, null);

        scenesList = findViewById(R.id.scenes_list);
        if (scenesList != null) {
            sceneFileNames = loadSceneFileNames();
            adapter = new SceneListAdapter(this, sceneFileNames);
            scenesList.setAdapter(adapter);

            // Set up interaction listener for options menu and scene selection
            adapter.setOnSceneInteractionListener(new SceneListAdapter.OnSceneInteractionListener() {
                @Override
                public void onSceneSelected(int position, String sceneFileName) {
                    openEditScene(sceneFileName);
                }

                @Override
                public void onOptionsMenuRequested(int position, String sceneFileName, View anchorView) {
                    showContextMenu(position, sceneFileName, anchorView);
                }
            });

            // Set up click listener for list items (to edit)
            scenesList.setOnItemClickListener((parent, view, position, id) -> {
                String selectedScene = sceneFileNames.get(position);
                Log.d(TAG, "Scene selected for editing: " + selectedScene);
                openEditScene(selectedScene);
            });

            Log.d(TAG, "Loaded " + sceneFileNames.size() + " scenes from persistent storage");
        } else {
            Log.e(TAG, "Scenes ListView not found!");
            Toast.makeText(this, "Failed to load scenes view", Toast.LENGTH_SHORT).show();
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
                    Log.d(TAG, "Found scene file: " + file.getName());
                }
                // Sort the list for consistent display
                java.util.Collections.sort(fileNames);
            } else {
                Log.e(TAG, "Error listing files in persistent directory");
            }
        } else {
            Log.e(TAG, "Persistent scenes directory does not exist or is not a directory");
        }

        return fileNames;
    }

    /**
     * Show a context menu (PopupMenu) with the delete option.
     */
    private void showContextMenu(int position, String sceneFileName, View anchorView) {
        PopupMenu popupMenu = new PopupMenu(this, anchorView);
        popupMenu.getMenuInflater().inflate(R.menu.scene_context_menu, popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.menu_delete_scene) {
                showDeleteConfirmationDialog(position, sceneFileName);
                return true;
            }
            return false;
        });

        popupMenu.show();
    }

    /**
     * Show a confirmation dialog before deleting a scene.
     */
    private void showDeleteConfirmationDialog(int position, String sceneFileName) {
        new AlertDialog.Builder(this)
            .setTitle("Delete Scene")
            .setMessage("Are you sure you want to delete '" + sceneFileName + "'?")
            .setPositiveButton("Yes", (dialog, which) -> {
                deleteScene(position, sceneFileName);
            })
            .setNegativeButton("No", (dialog, which) -> {
                dialog.dismiss();
            })
            .show();
    }

    /**
     * Delete a scene file from persistent storage and update the UI.
     */
    private void deleteScene(int position, String sceneFileName) {
        if (sceneFileManager.deleteScene(sceneFileName)) {
            // Remove from list and notify adapter
            sceneFileNames.remove(position);
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "Scene deleted: " + sceneFileName, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Scene deleted successfully: " + sceneFileName);

            // Refresh the scene list in the running wallpaper
            GLWallpaperService.refreshSceneList(this);
        } else {
            Toast.makeText(this, "Failed to delete scene: " + sceneFileName, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Failed to delete scene: " + sceneFileName);
        }
    }

    private void openEditScene(String sceneFileName) {
        try {
            Intent intent = new Intent(this, EditSceneActivity.class);
            intent.putExtra(EditSceneActivity.EXTRA_SCENE_FILE_NAME, sceneFileName);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening edit scene: " + e.getMessage(), e);
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
            adapter.clear();
            adapter.addAll(sceneFileNames);
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "Scene list reset to defaults", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Scene list successfully reset to defaults");

            // Refresh the scene list in the running wallpaper
            GLWallpaperService.refreshSceneList(this);
        } catch (Exception e) {
            Log.e(TAG, "Error resetting scenes: " + e.getMessage(), e);
            Toast.makeText(this, "Failed to reset scenes: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
