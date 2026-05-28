package com.hashilab.dev.editor.activities;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.example.livewallpaper.R;
import com.example.livewallpaper.scene.models.Project;
import com.hashilab.dev.editor.network.WebEditorApiClient;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProjectsActivity extends AppCompatActivity {

    private static final String TAG = "ProjectActivity";
    static final String PREFS_NAME = "ProjectBrowser";
    static final String PREF_SERVER_URL = "server_url";
    static final String DEFAULT_SERVER_URL = "https://livewallpaper-backend-production.up.railway.app/";

    private List<Project> projectList;
    private ProjectAdapter adapter;

    private final Map<String, Bitmap> thumbCache = new HashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_projects);

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        projectList = new ArrayList<>();
        adapter = new ProjectAdapter(projectList);
        recyclerView.setAdapter(adapter);

        loadProjects();
    }

    private void loadProjects() {
        String url = getStoredServerUrl();
        new Thread(() -> {
            try {
                WebEditorApiClient client = new WebEditorApiClient(url);
                List<WebEditorApiClient.Project> fetched = client.fetchProjects();
                runOnUiThread(() -> {
                    projectList.clear();
                    for (WebEditorApiClient.Project p : fetched) {
                        projectList.add(new Project(p.id, p.name, p.version, p.sceneNames));
                    }
                    adapter.notifyDataSetChanged();
                });

            }
            catch(Exception ex) {
                ex.printStackTrace();
            }
        }).start();
    }

    private void loadThumbnailAsync(ImageView imageView, String url) {
        imageView.setTag(url);
        Bitmap cached = thumbCache.get(url);
        imageView.setImageBitmap(cached);
        if (cached != null) return;
        WeakReference<ImageView> ref = new WeakReference<>(imageView);
        new Thread(() -> {
            try {
                java.net.URL u = new java.net.URL(url);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) u.openConnection();
                conn.setConnectTimeout(3_000);
                conn.setReadTimeout(5_000);
                conn.connect();
                if (conn.getResponseCode() == 200) {
                    Bitmap bmp = BitmapFactory.decodeStream(conn.getInputStream());
                    mainHandler.post(() -> {
                        thumbCache.put(url, bmp);
                        ImageView iv = ref.get();
                        if (iv != null && url.equals(iv.getTag())) {
                            iv.setImageBitmap(bmp);
                        }
                    });
                }
                conn.disconnect();
            } catch (Exception ignored) {}
        }).start();
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private String getStoredServerUrl() {
        return getPrefs().getString(PREF_SERVER_URL, DEFAULT_SERVER_URL);
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

            String baseUrl = getStoredServerUrl();
            if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            List<String> sceneNames = project.sceneNames;
            for (int i = 0; i < 4; i++) {
                if (i < sceneNames.size()) {
                    loadThumbnailAsync(holder.thumbs[i], baseUrl + "/thumbnails/" + sceneNames.get(i) + ".jpg");
                } else {
                    holder.thumbs[i].setImageDrawable(null);
                    holder.thumbs[i].setTag(null);
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
