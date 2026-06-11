package com.hashilab.dev.editor.activities;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.signature.ObjectKey;
import com.example.livewallpaper.R;
import com.hashilab.dev.editor.network.WebEditorApiClient;
import com.hashilab.dev.editor.utils.AppPreferences;
import com.hashilab.dev.editor.viewmodels.ProjectViewModel;
import com.hashilab.dev.editor.viewmodels.ProjectViewModel.ProjectState;

import java.util.ArrayList;
import java.util.List;

public class ProjectActivity extends androidx.appcompat.app.AppCompatActivity {

    private static final String TAG = "ProjectActivity";

    public static final String EXTRA_PROJECT_NAME = "project_name";
    public static final String EXTRA_PROJECT_ID   = "project_id";

    private String projectId;
    private String projectName;

    private List<WebEditorApiClient.SceneInfo> sceneList;
    private SceneAdapter adapter;
    private ProjectViewModel viewModel;

    // ── Views ──────────────────────────────────────────────────────────────────

    private Button downloadProjectButton;
    private Button setProjectAsWallpaperButton;
    private Button deleteWallpaperButton;
    private ImageButton refreshButton;
    private ProgressBar downloadProgressBar;
    private TextView downloadSizeText;

    // ── State ──────────────────────────────────────────────────────────────────

    private ProjectState currentState = ProjectState.NOT_DOWNLOADED;

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project);

        projectName = getIntent().getStringExtra(EXTRA_PROJECT_NAME);
        projectId   = getIntent().getStringExtra(EXTRA_PROJECT_ID);

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.project_toolbar);
        TextView titleLabel = findViewById(R.id.sceneNameLabel);
        if (titleLabel != null) titleLabel.setText(projectName);
        toolbar.setNavigationOnClickListener(v -> finish());

        downloadProjectButton      = findViewById(R.id.downloadProjectButton);
        setProjectAsWallpaperButton = findViewById(R.id.setProjectAsWallpaperButton);
        deleteWallpaperButton       = findViewById(R.id.deleteWallpaperButton);
        refreshButton               = findViewById(R.id.refreshButton);
        downloadProgressBar         = findViewById(R.id.downloadProgressBar);
        downloadSizeText            = findViewById(R.id.downloadSizeText);

        downloadProjectButton.setOnClickListener(v ->
                viewModel.downloadProject(projectId, projectName));
        refreshButton.setOnClickListener(v ->
                viewModel.downloadProjectUpdate(projectId, projectName));
        setProjectAsWallpaperButton.setOnClickListener(v -> {
            if(viewModel.isCurrentWallpaperMine())
            {
                Toast.makeText(this, "Setting as wallpaper", Toast.LENGTH_SHORT).show();
            }
            viewModel.activateProject(projectId);
        });
        deleteWallpaperButton.setOnClickListener(v ->
                {
                    viewModel.deleteProject(projectId);
                    viewModel.unsetWallpaper(projectId);
                });

        RecyclerView recyclerView = findViewById(R.id.sceneGridRecyclerView);
        sceneList = new ArrayList<>();
        adapter = new SceneAdapter(sceneList);
        recyclerView.setAdapter(adapter);

        SwipeRefreshLayout swipeRefresh = findViewById(R.id.swipeRefreshLayout);

        viewModel = new ViewModelProvider(this).get(ProjectViewModel.class);

        viewModel.getScenes().observe(this, scenes -> {
            sceneList.clear();
            sceneList.addAll(scenes);
            adapter.notifyDataSetChanged();
        });
        viewModel.getState().observe(this, this::setState);
        viewModel.getDownloadProgress().observe(this, progress -> {
            if (progress.totalBytes > 0) {
                downloadProgressBar.setIndeterminate(false);
                downloadProgressBar.setProgress(
                        (int)(progress.downloadedBytes * 10000L / progress.totalBytes));
            } else {
                downloadProgressBar.setIndeterminate(true);
            }
            downloadSizeText.setText(progress.format());
        });
        viewModel.getError().observe(this, err -> {
            if ("UNAUTHORIZED".equals(err)) {
                AuthNavigation.signOut(this);
            } else if (err != null) {
                Toast.makeText(this, err, Toast.LENGTH_LONG).show();
            }
        });
        viewModel.getLoading().observe(this, swipeRefresh::setRefreshing);
        viewModel.getUpdateAvailable().observe(this, available -> updateRefreshButtonVisibility());

        swipeRefresh.setOnRefreshListener(() -> viewModel.refreshScenes(projectId));
        viewModel.getWallpaperActivated().observe(this, activated -> {
            if (activated == null || !activated) return;
            ComponentName glService = new ComponentName(
                    getPackageName(), "com.example.livewallpaper.gl.GLWallpaperService");
            WallpaperManager wm = WallpaperManager.getInstance(this);
            android.app.WallpaperInfo info = wm.getWallpaperInfo();
            boolean alreadySet = info != null && glService.equals(info.getComponent());
            if (!alreadySet) {
                // Not yet set — open the system chooser so the user can activate it.
                Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
                intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, glService);
                startActivity(intent);
            }
            // If already set, requestProjectReload() already posted the GL-thread reload.
        });

        viewModel.loadScenes(projectId);
    }

    // ── State ──────────────────────────────────────────────────────────────────

    private void setState(ProjectState state) {
        currentState = state;
        downloadProjectButton.setVisibility(View.GONE);
        setProjectAsWallpaperButton.setVisibility(View.GONE);
        deleteWallpaperButton.setVisibility(View.GONE);
        downloadProgressBar.setVisibility(View.GONE);
        downloadSizeText.setVisibility(View.GONE);

        switch (state) {
            case NOT_DOWNLOADED:
                downloadProjectButton.setVisibility(View.VISIBLE);
                break;
            case DOWNLOADING:
                downloadProgressBar.setVisibility(View.VISIBLE);
                downloadSizeText.setVisibility(View.VISIBLE);
                break;
            case DOWNLOADED:
                setProjectAsWallpaperButton.setVisibility(View.VISIBLE);
                deleteWallpaperButton.setVisibility(View.VISIBLE);
                break;
        }
        updateRefreshButtonVisibility();
    }

    private void updateRefreshButtonVisibility() {
        Boolean available = viewModel.getUpdateAvailable().getValue();
        boolean show = currentState == ProjectState.DOWNLOADED
                && available != null && available;
        refreshButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // ── Adapter ────────────────────────────────────────────────────────────────

    private class SceneAdapter extends RecyclerView.Adapter<SceneViewHolder> {
        private final List<WebEditorApiClient.SceneInfo> scenes;

        SceneAdapter(List<WebEditorApiClient.SceneInfo> scenes) {
            this.scenes = scenes;
        }

        @NonNull
        @Override
        public SceneViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.grid_item_scene, parent, false);
            return new SceneViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SceneViewHolder holder, int position) {
            WebEditorApiClient.SceneInfo scene = scenes.get(position);
            holder.sceneName.setText(scene.label != null ? scene.label : scene.name);
            String baseUrl = AppPreferences.getServerUrl(ProjectActivity.this);
            String cacheKey = scene.updatedAt.isEmpty() ? scene.id : scene.updatedAt;
            String thumbnailUrl = baseUrl + "/thumbnails/" + scene.id + ".jpg";
            Glide.with(ProjectActivity.this)
                    .load(thumbnailUrl)
                    .signature(new ObjectKey(cacheKey))
                    .into(holder.thumb);
        }

        @Override
        public int getItemCount() { return scenes.size(); }
    }

    private static class SceneViewHolder extends RecyclerView.ViewHolder {
        TextView sceneName;
        ImageView thumb;

        SceneViewHolder(View itemView) {
            super(itemView);
            sceneName = itemView.findViewById(R.id.text_scene_name);
            thumb     = itemView.findViewById(R.id.thumb_0);
        }
    }
}
