package com.hashilab.dev.editor.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.Menu;
import android.view.MenuItem;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.livewallpaper.R;
import com.example.livewallpaper.logging.TimberLog;
import com.example.livewallpaper.managers.SceneFileManager;
import com.hashilab.dev.editor.network.WebEditorApiClient;
import com.hashilab.dev.editor.utils.AppPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dev-only activity for browsing the web editor.
 *
 * <ul>
 *   <li>Projects screen – lists every project with a "Downloaded" badge or a per-row
 *       Download button. Tapping a row navigates to that project's scenes.</li>
 *   <li>Scenes screen – lists every scene in the selected project. Tapping a scene
 *       opens {@link EditSceneActivity}. If the project is not yet downloaded a
 *       "Download Project" button is shown at the bottom.</li>
 * </ul>
 *
 * Download state is tracked with a small JSON manifest file stored in
 * {@code getExternalFilesDir("projects")/{projectId}.json}. A project is considered
 * downloaded when that file exists (it is written only after every scene and texture
 * has been successfully saved).
 */
public class ProjectBrowserActivity extends AppCompatActivity {

    private static final String TAG = "ProjectBrowserActivity";
    static final String PREFS_NAME = "ProjectBrowser";
    static final String PREF_SERVER_URL = "server_url";
    static final String DEFAULT_SERVER_URL = "https://livewallpaper-backend-production.up.railway.app/";

    // ── State ──────────────────────────────────────────────────────────────────

    private enum ViewState { PROJECTS, SCENES }
    private ViewState viewState = ViewState.PROJECTS;

    private final List<ProjectRow> projectRows = new ArrayList<>();
    private WebEditorApiClient.Project selectedProject;
    private List<WebEditorApiClient.SceneInfo> currentScenes = new ArrayList<>();

    // ── Views ──────────────────────────────────────────────────────────────────

    private TextView textHeader;
    private TextView textStatus;
    private ProgressBar progressBar;
    private ListView listView;
    /** "Download Project" button shown in the scenes view when the project is not yet downloaded. */
    private Button btnDownloadProject;

    // ── Adapters ───────────────────────────────────────────────────────────────

    private ProjectRowAdapter projectAdapter;
    private ArrayAdapter<WebEditorApiClient.SceneInfo> sceneAdapter;

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    private static final int MENU_SIGN_OUT = 1;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_SIGN_OUT, 0, "Sign Out");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_SIGN_OUT) {
            AuthNavigation.signOut(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (AppPreferences.getSessionCookie(this) == null) {
            AuthNavigation.signOut(this);
            return;
        }

        setContentView(R.layout.activity_project_browser);

        textHeader         = findViewById(R.id.text_header);
        textStatus         = findViewById(R.id.text_status);
        progressBar        = findViewById(R.id.progress_bar);
        listView           = findViewById(R.id.list_items);
        btnDownloadProject = findViewById(R.id.btn_download);

        btnDownloadProject.setOnClickListener(v -> onDownloadProjectClicked());

        projectAdapter = new ProjectRowAdapter(this, projectRows);
        listView.setAdapter(projectAdapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (viewState == ViewState.PROJECTS) {
                onProjectRowClicked(projectRows.get(position));
            } else if (viewState == ViewState.SCENES) {
                onSceneTapped(currentScenes.get(position));
            }
        });

        setProjectsView();

        // Handle back: when the scenes view is showing, return to the projects list
        // rather than finishing the activity. Uses OnBackPressedDispatcher so it works
        // correctly on all API levels (the deprecated onBackPressed() override is not
        // reliably called on API 33+ with predictive-back navigation enabled).
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (viewState == ViewState.SCENES) {
                    setProjectsView();
                } else {
                    // Let the system handle it (finish the activity).
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        // Auto-load projects using the stored server URL
        loadProjects();
    }


    // ── View switching ─────────────────────────────────────────────────────────

    private void setProjectsView() {
        viewState = ViewState.PROJECTS;
        textHeader.setText("Projects");
        btnDownloadProject.setVisibility(View.GONE);
        listView.setAdapter(projectAdapter);
        selectedProject = null;
        currentScenes = new ArrayList<>();
    }

    private void setScenesView(WebEditorApiClient.Project project,
                               List<WebEditorApiClient.SceneInfo> scenes,
                               boolean projectDownloaded) {
        viewState = ViewState.SCENES;
        selectedProject = project;
        currentScenes = scenes;

        textHeader.setText(project.name + " — Scenes");
        btnDownloadProject.setVisibility(projectDownloaded ? View.GONE : View.VISIBLE);

        sceneAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, scenes);
        listView.setAdapter(sceneAdapter);
    }

    // ── Status / progress helpers ──────────────────────────────────────────────

    private void showStatus(String message) {
        runOnUiThread(() -> {
            textStatus.setText(message);
            textStatus.setVisibility(View.VISIBLE);
        });
    }

    private void hideStatus() {
        runOnUiThread(() -> textStatus.setVisibility(View.GONE));
    }

    private void showProgress() {
        runOnUiThread(() -> {
            progressBar.setIndeterminate(true);
            progressBar.setVisibility(View.VISIBLE);
        });
    }

    private void showProgress(int current, int total) {
        runOnUiThread(() -> {
            progressBar.setIndeterminate(false);
            progressBar.setMax(total);
            progressBar.setProgress(current);
            progressBar.setVisibility(View.VISIBLE);
        });
    }

    private void hideProgress() {
        runOnUiThread(() -> progressBar.setVisibility(View.GONE));
    }

    private void setButtonsEnabled(boolean enabled) {
        runOnUiThread(() -> btnDownloadProject.setEnabled(enabled));
    }

    // ── Actions ────────────────────────────────────────────────────────────────

    /** Fetch projects from the stored server URL and populate the list. */
    private void loadProjects() {
        String url = getStoredServerUrl();
        setButtonsEnabled(false);
        showProgress();
        showStatus("Connecting to " + url + " …");

        new Thread(() -> {
            try {
                WebEditorApiClient client = buildClient(url);
                List<WebEditorApiClient.Project> fetched = client.fetchProjects();

                runOnUiThread(() -> {
                    projectRows.clear();
                    for (WebEditorApiClient.Project p : fetched) {
                        boolean downloaded = isProjectDownloaded(p.id);
                        boolean needsUpdate = downloaded
                                && !p.version.isEmpty()
                                && !p.version.equals(getLocalProjectVersion(p.id));
                        projectRows.add(new ProjectRow(p, downloaded, needsUpdate));
                    }
                    projectAdapter.notifyDataSetChanged();
                    setProjectsView();
                    hideProgress();
                    hideStatus();
                    setButtonsEnabled(true);
                    if (fetched.isEmpty()) {
                        showStatus("No projects found on this server.");
                    }
                });
            } catch (WebEditorApiClient.AuthException e) {
                runOnUiThread(() -> AuthNavigation.signOut(this));
            } catch (Exception e) {
                TimberLog.e(TAG, "Failed to fetch projects", e);
                runOnUiThread(() -> {
                    hideProgress();
                    showStatus("Error: " + e.getMessage());
                    setButtonsEnabled(true);
                });
            }
        }).start();
    }

    /** Tapping a project row always navigates to its scenes view. */
    private void onProjectRowClicked(ProjectRow row) {
        // Use the local manifest if available — no network required.
        if (row.isDownloaded) {
            List<WebEditorApiClient.SceneInfo> local = loadProjectManifest(row.project.id);
            if (!local.isEmpty()) {
                setScenesView(row.project, local, true);
                return;
            }
        }
        // Otherwise fetch from the server.
        fetchScenesFromServer(row.project, /* forDownload */ false);
    }

    /** "Download" button on an individual project row. */
    private void onProjectRowDownloadClicked(ProjectRow row) {
        fetchScenesFromServer(row.project, /* forDownload */ true);
    }

    /** "Download Project" button in the scenes view (shown when current project is not downloaded). */
    private void onDownloadProjectClicked() {
        if (selectedProject == null || currentScenes.isEmpty()) return;
        String url = getStoredServerUrl();
        setButtonsEnabled(false);
        showProgress(0, currentScenes.size());
        showStatus("Downloading 0 / " + currentScenes.size() + " scenes…");

        final WebEditorApiClient.Project project = selectedProject;
        final List<WebEditorApiClient.SceneInfo> scenes = new ArrayList<>(currentScenes);
        new Thread(() -> {
            try {
                downloadProject(url, project, scenes);
            } catch (WebEditorApiClient.AuthException e) {
                runOnUiThread(() -> AuthNavigation.signOut(this));
            } catch (Exception e) {
                TimberLog.e(TAG, "Download failed", e);
                runOnUiThread(() -> {
                    hideProgress();
                    showStatus("Download failed: " + e.getMessage());
                    setButtonsEnabled(true);
                });
            }
        }).start();
    }

    /** Tapping a scene row → open {@link EditSceneActivity} if the file exists locally. */
    private void onSceneTapped(WebEditorApiClient.SceneInfo scene) {
        SceneFileManager sfm = new SceneFileManager(this, null);
        String fileName = scene.name.endsWith(".json") ? scene.name : scene.name + ".json";
        File sceneFile = new File(sfm.getFallbackScenesDirectory(), fileName);
        if (sceneFile.exists()) {
            Intent intent = new Intent(this, EditSceneActivity.class);
            intent.putExtra(EditSceneActivity.EXTRA_SCENE_FILE_NAME, fileName);
            startActivity(intent);
        } else {
            Toast.makeText(this, "Scene not on device — download the project first.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ── Network helpers ────────────────────────────────────────────────────────

    /**
     * Fetch the scene list for a project from the server.
     *
     * @param forDownload when {@code true}, immediately kick off a full project download
     *                    after the scene list has been retrieved; when {@code false}, just
     *                    navigate to the scenes view.
     */
    private void fetchScenesFromServer(WebEditorApiClient.Project project, boolean forDownload) {
        String url = getStoredServerUrl();
        setButtonsEnabled(false);
        showProgress();
        showStatus("Loading scenes for \"" + project.name + "\" …");

        new Thread(() -> {
            try {
                WebEditorApiClient client = buildClient(url);
                List<WebEditorApiClient.SceneInfo> scenes = client.fetchScenesForProject(project.id);

                runOnUiThread(() -> {
                    hideProgress();
                    setButtonsEnabled(true);

                    if (scenes.isEmpty()) {
                        hideStatus();
                        showStatus("This project has no scenes.");
                        return;
                    }

                    if (forDownload) {
                        currentScenes = scenes;
                        selectedProject = project;
                        setButtonsEnabled(false);
                        showProgress(0, scenes.size());
                        showStatus("Downloading 0 / " + scenes.size() + " scenes…");
                        new Thread(() -> {
                            try {
                                downloadProject(url, project, scenes);
                            } catch (WebEditorApiClient.AuthException ex) {
                                runOnUiThread(() -> AuthNavigation.signOut(this));
                            } catch (Exception ex) {
                                TimberLog.e(TAG, "Download failed", ex);
                                runOnUiThread(() -> {
                                    hideProgress();
                                    showStatus("Download failed: " + ex.getMessage());
                                    setButtonsEnabled(true);
                                });
                            }
                        }).start();
                    } else {
                        hideStatus();
                        boolean downloaded = isProjectDownloaded(project.id);
                        setScenesView(project, scenes, downloaded);
                    }
                });
            } catch (WebEditorApiClient.AuthException e) {
                runOnUiThread(() -> AuthNavigation.signOut(this));
            } catch (Exception e) {
                TimberLog.e(TAG, "Failed to fetch scenes", e);
                runOnUiThread(() -> {
                    hideProgress();
                    showStatus("Error: " + e.getMessage());
                    setButtonsEnabled(true);
                });
            }
        }).start();
    }

    // ── Download logic ─────────────────────────────────────────────────────────

    private void downloadProject(String serverUrl,
                                 WebEditorApiClient.Project project,
                                 List<WebEditorApiClient.SceneInfo> scenes) throws IOException {
        WebEditorApiClient client = buildClient(serverUrl);
        SceneFileManager sceneFileManager = new SceneFileManager(this, null);
        File scenesDir   = sceneFileManager.getFallbackScenesDirectory();
        File texturesDir = sceneFileManager.getFallbackTexturesDirectory();

        int done = 0;
        int total = scenes.size();
        int successCount = 0;

        for (WebEditorApiClient.SceneInfo sceneInfo : scenes) {
            showStatus("Downloading scene \"" + sceneInfo.label + "\" …");
            try {
                // 1. Fetch full scene JSON
                String sceneJson = client.fetchSceneData(sceneInfo.name);

                // 2. Collect uploaded-image filenames
                Set<String> uploadedFilenames = extractUploadedFilenames(sceneJson);

                // 3. Download each uploaded image
                for (String filename : uploadedFilenames) {
                    File dest = new File(texturesDir, filename);
                    if (dest.exists()) {
                        TimberLog.d(TAG, "Texture already cached, skipping: " + filename);
                        continue;
                    }
                    showStatus("Downloading texture " + filename + " …");
                    byte[] imageBytes = client.downloadUpload(filename);
                    writeFile(dest, imageBytes);
                    TimberLog.d(TAG, "Saved texture: " + dest.getAbsolutePath());
                }

                // 4. Unwrap "data" envelope then save scene JSON
                String sceneJsonToSave = sceneJson;
                try {
                    JSONObject root = new JSONObject(sceneJson);
                    if (root.has("data")) {
                        sceneJsonToSave = root.getJSONObject("data").toString();
                        TimberLog.d(TAG, "Unwrapped 'data' envelope for: " + sceneInfo.name);
                    }
                } catch (Exception parseEx) {
                    TimberLog.w(TAG, "Could not parse scene JSON for unwrapping: " + parseEx.getMessage());
                }
                String jsonFilename = sceneInfo.name.endsWith(".json")
                        ? sceneInfo.name : sceneInfo.name + ".json";
                File sceneFile = new File(scenesDir, jsonFilename);
                writeFile(sceneFile, sceneJsonToSave.getBytes("UTF-8"));
                TimberLog.d(TAG, "Saved scene: " + sceneFile.getAbsolutePath());

                successCount++;
            } catch (Exception e) {
                TimberLog.e(TAG, "Failed to download scene: " + sceneInfo.name, e);
            }

            done++;
            showProgress(done, total);
        }

        // Write the manifest only after all scenes succeed.
        final boolean allOk = successCount == total && total > 0;
        if (allOk) {
            saveProjectManifest(project, scenes);
        }

        final int finalSuccess = successCount;
        final int finalTotal = total;
        runOnUiThread(() -> {
            hideProgress();
            showStatus("Downloaded " + finalSuccess + " / " + finalTotal + " scenes.");
            setButtonsEnabled(true);

            // Refresh download badges in the project list.
            for (ProjectRow row : projectRows) {
                if (row.project.id.equals(project.id)) {
                    row.isDownloaded = allOk;
                    if (allOk) row.needsUpdate = false;
                }
            }
            projectAdapter.notifyDataSetChanged();

            // If currently showing this project's scenes, update the download button visibility.
            if (viewState == ViewState.SCENES
                    && selectedProject != null
                    && selectedProject.id.equals(project.id)) {
                btnDownloadProject.setVisibility(allOk ? View.GONE : View.VISIBLE);
            }
        });
    }

    // ── Manifest helpers ───────────────────────────────────────────────────────

    private File getProjectsManifestDir() {
        File dir = new File(getExternalFilesDir(null), "projects");
        dir.mkdirs();
        return dir;
    }

    private File getManifestFile(String projectId) {
        return new File(getProjectsManifestDir(), projectId + ".json");
    }

    private boolean isProjectDownloaded(String projectId) {
        return getManifestFile(projectId).exists();
    }

    private void saveProjectManifest(WebEditorApiClient.Project project,
                                     List<WebEditorApiClient.SceneInfo> scenes) {
        try {
            JSONObject manifest = new JSONObject();
            manifest.put("id", project.id);
            manifest.put("name", project.name);
            manifest.put("version", project.version);
            JSONArray arr = new JSONArray();
            for (WebEditorApiClient.SceneInfo s : scenes) {
                JSONObject obj = new JSONObject();
                obj.put("id", s.id);
                obj.put("name", s.name);
                obj.put("label", s.label != null ? s.label : s.name);
                obj.put("updatedAt", s.updatedAt);
                arr.put(obj);
            }
            manifest.put("scenes", arr);
            writeFile(getManifestFile(project.id), manifest.toString().getBytes("UTF-8"));
            TimberLog.d(TAG, "Saved project manifest for: " + project.name + " version: " + project.version);
        } catch (Exception e) {
            TimberLog.e(TAG, "Failed to save project manifest", e);
        }
    }

    /** Returns the version stored in the local manifest, or an empty string if not found. */
    private String getLocalProjectVersion(String projectId) {
        File f = getManifestFile(projectId);
        if (!f.exists()) return "";
        try {
            byte[] bytes = readBytesFromFile(f);
            JSONObject manifest = new JSONObject(new String(bytes, "UTF-8"));
            return manifest.optString("version", "");
        } catch (Exception e) {
            TimberLog.e(TAG, "Failed to read version from manifest for: " + projectId, e);
            return "";
        }
    }

    private List<WebEditorApiClient.SceneInfo> loadProjectManifest(String projectId) {
        List<WebEditorApiClient.SceneInfo> scenes = new ArrayList<>();
        File f = getManifestFile(projectId);
        if (!f.exists()) return scenes;
        try {
            byte[] bytes = readBytesFromFile(f);
            String json = new String(bytes, "UTF-8");
            JSONObject manifest = new JSONObject(json);
            JSONArray arr = manifest.optJSONArray("scenes");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    scenes.add(new WebEditorApiClient.SceneInfo(
                            obj.optString("id"),
                            obj.optString("name"),
                            obj.optString("label"),
                            obj.optString("updatedAt")));
                }
            }
        } catch (Exception e) {
            TimberLog.e(TAG, "Failed to load project manifest for: " + projectId, e);
        }
        return scenes;
    }

    // ── Scene JSON helpers ─────────────────────────────────────────────────────

    private Set<String> extractUploadedFilenames(String sceneJson) {
        Set<String> filenames = new HashSet<>();
        try {
            JSONObject root = new JSONObject(sceneJson);
            JSONObject data = root.has("data") ? root.getJSONObject("data") : root;
            JSONArray sprites = data.optJSONArray("sprites");
            if (sprites == null) return filenames;
            for (int i = 0; i < sprites.length(); i++) {
                JSONObject sprite = sprites.getJSONObject(i);
                String resource = sprite.optString("textureResource", "");
                if (resource.startsWith("/uploads/")) {
                    String filename = resource.substring("/uploads/".length());
                    if (!filename.isEmpty()) filenames.add(filename);
                }
            }
        } catch (Exception e) {
            TimberLog.w(TAG, "Could not parse scene JSON for textures: " + e.getMessage());
        }
        return filenames;
    }

    // ── File I/O helpers ───────────────────────────────────────────────────────

    private static void writeFile(File dest, byte[] data) throws IOException {
        dest.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(dest)) {
            fos.write(data);
        }
    }

    private static byte[] readBytesFromFile(File file) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream((int) file.length());
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) != -1) bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private WebEditorApiClient buildClient(String url) {
        return new WebEditorApiClient(url, AppPreferences.getSessionCookie(this));
    }

    // ── Prefs ──────────────────────────────────────────────────────────────────

    private SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private String getStoredServerUrl() {
        return getPrefs().getString(PREF_SERVER_URL, DEFAULT_SERVER_URL);
    }

    // ── Inner types ────────────────────────────────────────────────────────────

    /** Wraps a {@link WebEditorApiClient.Project} with its local download state. */
    private static class ProjectRow {
        final WebEditorApiClient.Project project;
        boolean isDownloaded;
        boolean needsUpdate;
        ProjectRow(WebEditorApiClient.Project project, boolean isDownloaded, boolean needsUpdate) {
            this.project = project;
            this.isDownloaded = isDownloaded;
            this.needsUpdate = needsUpdate;
        }
    }

    /** List adapter that shows each project with a "Downloaded" badge or a Download button. */
    private class ProjectRowAdapter extends ArrayAdapter<ProjectRow> {

        ProjectRowAdapter(Context context, List<ProjectRow> items) {
            super(context, R.layout.item_project, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_project, parent, false);
            }

            ProjectRow row = getItem(position);
            if (row == null) return convertView;

            TextView nameView    = convertView.findViewById(R.id.text_project_name);
            TextView statusView  = convertView.findViewById(R.id.text_project_status);
            TextView badgeView   = convertView.findViewById(R.id.text_downloaded_badge);
            Button   updateBtn   = convertView.findViewById(R.id.btn_update_project);
            Button   downloadBtn = convertView.findViewById(R.id.btn_download_project);

            nameView.setText(row.project.name);

            if (!row.isDownloaded) {
                // Not on device yet
                statusView.setText("Not downloaded");
                badgeView.setVisibility(View.GONE);
                updateBtn.setVisibility(View.GONE);
                downloadBtn.setVisibility(View.VISIBLE);
                downloadBtn.setOnClickListener(v -> onProjectRowDownloadClicked(row));
                updateBtn.setOnClickListener(null);
            } else if (row.needsUpdate) {
                // Downloaded but a newer version exists on the server
                statusView.setText("Update available");
                badgeView.setVisibility(View.VISIBLE);
                updateBtn.setVisibility(View.VISIBLE);
                downloadBtn.setVisibility(View.GONE);
                updateBtn.setOnClickListener(v -> onProjectRowDownloadClicked(row));
                downloadBtn.setOnClickListener(null);
            } else {
                // Downloaded and up to date
                statusView.setText("Tap to view scenes");
                badgeView.setVisibility(View.VISIBLE);
                updateBtn.setVisibility(View.GONE);
                downloadBtn.setVisibility(View.GONE);
                updateBtn.setOnClickListener(null);
                downloadBtn.setOnClickListener(null);
            }

            return convertView;
        }
    }
}
