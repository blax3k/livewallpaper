package com.hashilab.dev.editor.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.livewallpaper.R;
import com.example.livewallpaper.scene.models.Project;
import com.hashilab.dev.editor.utils.AppPreferences;
import com.hashilab.dev.editor.viewmodels.ProjectsViewModel;

import java.util.ArrayList;
import java.util.List;

public class ProjectsActivity extends AppCompatActivity {

    // Keep these accessible to ProjectActivity for shared prefs key names.
    static final String PREFS_NAME = "ProjectBrowser";
    static final String PREF_SERVER_URL = "server_url";
    static final String DEFAULT_SERVER_URL = AppPreferences.DEFAULT_SERVER_URL;

    private List<Project> projectList;
    private ProjectAdapter adapter;
    private ProjectsViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_projects);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        RecyclerView recyclerView = findViewById(R.id.sceneGridRecyclerView);
        projectList = new ArrayList<>();
        adapter = new ProjectAdapter(projectList);
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(ProjectsViewModel.class);
        viewModel.getProjects().observe(this, projects -> {
            projectList.clear();
            projectList.addAll(projects);
            adapter.notifyDataSetChanged();
        });
        viewModel.getError().observe(this, err -> {
            if (err != null) Toast.makeText(this, "Failed to load projects: " + err, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_projects, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private class ProjectAdapter extends RecyclerView.Adapter<GridItem> {
        private final List<Project> projects;

        public ProjectAdapter(List<Project> projects) {
            this.projects = projects;
        }

        @NonNull
        @Override
        public GridItem onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.grid_item_project, parent, false);
            return new GridItem(view);
        }

        @Override
        public void onBindViewHolder(@NonNull GridItem holder, int position) {
            Project project = projects.get(position);
            holder.projectName.setText(project.name);

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(ProjectsActivity.this, ProjectActivity.class);
                intent.putExtra(ProjectActivity.EXTRA_PROJECT_NAME, project.name);
                intent.putExtra(ProjectActivity.EXTRA_PROJECT_ID, project.id);
                startActivity(intent);
            });

            String baseUrl = AppPreferences.getServerUrl(ProjectsActivity.this);
            List<String> thumbnailUrls = project.sceneThumbnailUrls;
            for (int i = 0; i < 4; i++) {
                if (i < thumbnailUrls.size()) {
                    Glide.with(ProjectsActivity.this)
                            .load(baseUrl + thumbnailUrls.get(i))
                            .into(holder.thumbs[i]);
                } else {
                    Glide.with(ProjectsActivity.this).clear(holder.thumbs[i]);
                    holder.thumbs[i].setImageDrawable(null);
                }
            }
        }

        @Override
        public int getItemCount() {
            return projects.size();
        }
    }

    private static class GridItem extends RecyclerView.ViewHolder {
        TextView projectName;
        ImageView[] thumbs = new ImageView[4];

        public GridItem(View itemView) {
            super(itemView);
            projectName = itemView.findViewById(R.id.text_project_name);
            thumbs[0] = itemView.findViewById(R.id.thumb_0);
            thumbs[1] = itemView.findViewById(R.id.thumb_1);
            thumbs[2] = itemView.findViewById(R.id.thumb_2);
            thumbs[3] = itemView.findViewById(R.id.thumb_3);
        }
    }
}
