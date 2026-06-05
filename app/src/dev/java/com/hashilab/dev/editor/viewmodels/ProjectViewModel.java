package com.hashilab.dev.editor.viewmodels;

import android.app.Application;
import android.app.WallpaperManager;
import android.content.ComponentName;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.livewallpaper.gl.GLWallpaperService;
import com.example.livewallpaper.logging.TimberLog;
import com.example.livewallpaper.prefs.WallpaperPreferences;
import com.hashilab.dev.editor.network.WebEditorApiClient;
import com.hashilab.dev.editor.utils.AppPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProjectViewModel extends AndroidViewModel {

    private static final String TAG = "ProjectViewModel";

    public boolean isCurrentWallpaperMine() {
        android.app.WallpaperInfo info = WallpaperManager.getInstance(getApplication()).getWallpaperInfo();
        if (info == null) return false;
        ComponentName myService = new ComponentName(
                getApplication(),
                com.example.livewallpaper.gl.GLWallpaperService.class);
        return myService.getPackageName().equals(info.getPackageName())
                && myService.getClassName().equals(info.getServiceName());
    }

    public boolean isCurrentProjectMine(String projectId) {
        if (!isCurrentWallpaperMine()) return false;
        String currentProjectId = WallpaperPreferences.getCurrentProjectId(getApplication());
        return projectId != null && projectId.equals(currentProjectId);
    }

    public void unsetWallpaper(String projectId) {
        executor.execute(() -> {
            if (!isCurrentProjectMine(projectId)) return;
            WallpaperPreferences.setActiveProject(getApplication(), null, null, null);
            try {
                WallpaperManager.getInstance(getApplication()).clear();
            } catch (IOException e) {
                TimberLog.e(TAG, "Failed to clear wallpaper", e);
                error.postValue("Failed to clear wallpaper: " + e.getMessage());
            }
        });
    }



    // ── State enum ─────────────────────────────────────────────────────────────

    public enum ProjectState {
        NOT_DOWNLOADED,
        DOWNLOADING,
        DOWNLOADED,
        OUT_OF_SYNC   // reserved for future use
    }

    // ── Download progress ──────────────────────────────────────────────────────

    public static class DownloadProgress {
        public final long downloadedBytes;
        public final long totalBytes; // 0 = unknown

        public DownloadProgress(long downloadedBytes, long totalBytes) {
            this.downloadedBytes = downloadedBytes;
            this.totalBytes = totalBytes;
        }

        /** Returns a formatted string like "12.3 MB / 45.6 MB" or "12.3 MB" if total unknown. */
        public String format() {
            if (totalBytes <= 0) return formatBytes(downloadedBytes);
            return formatBytes(downloadedBytes) + " / " + formatBytes(totalBytes);
        }

        private static String formatBytes(long bytes) {
            if (bytes >= 1_073_741_824L) {
                return String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0);
            }
            return String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0);
        }
    }

    // ── LiveData ───────────────────────────────────────────────────────────────

    private final MutableLiveData<List<WebEditorApiClient.SceneInfo>> scenes = new MutableLiveData<>();
    private final MutableLiveData<ProjectState> state = new MutableLiveData<>();
    private final MutableLiveData<DownloadProgress> downloadProgress = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> wallpaperActivated = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public ProjectViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<WebEditorApiClient.SceneInfo>> getScenes() { return scenes; }
    public LiveData<ProjectState> getState() { return state; }
    public LiveData<DownloadProgress> getDownloadProgress() { return downloadProgress; }
    public LiveData<String> getError() { return error; }
    public LiveData<Boolean> getWallpaperActivated() { return wallpaperActivated; }
    public LiveData<Boolean> getLoading() { return loading; }

    // ── Scene loading ──────────────────────────────────────────────────────────

    /** Fetches scenes from the server and then checks download state. Skips re-fetch if already loaded. */
    public void loadScenes(String projectId) {
        if (scenes.getValue() != null) {
            checkDownloadState(projectId);
            return;
        }
        fetchScenes(projectId);
    }

    /** Forces a re-fetch from the server regardless of cached state. */
    public void refreshScenes(String projectId) {
        fetchScenes(projectId);
    }

    private void fetchScenes(String projectId) {
        loading.postValue(true);
        String url = AppPreferences.getServerUrl(getApplication());
        executor.execute(() -> {
            try {
                WebEditorApiClient client = new WebEditorApiClient(url);
                List<WebEditorApiClient.SceneInfo> fetched = client.fetchScenesForProject(projectId);
                scenes.postValue(fetched);
                postDownloadState(projectId);
            } catch (Exception e) {
                TimberLog.e(TAG, "Failed to load scenes", e);
                error.postValue(e.getMessage());
            } finally {
                loading.postValue(false);
            }
        });
    }

    // ── Download state ─────────────────────────────────────────────────────────

    public void checkDownloadState(String projectId) {
        executor.execute(() -> postDownloadState(projectId));
    }

    private void postDownloadState(String projectId) {
        state.postValue(isProjectDownloaded(projectId)
                ? ProjectState.DOWNLOADED
                : ProjectState.NOT_DOWNLOADED);
    }

    // ── Download ───────────────────────────────────────────────────────────────

    public void downloadProject(String projectId, String projectName) {
        List<WebEditorApiClient.SceneInfo> sceneList = scenes.getValue();
        if (sceneList == null || sceneList.isEmpty()) {
            error.postValue("No scenes loaded — cannot download.");
            return;
        }

        state.postValue(ProjectState.DOWNLOADING);
        downloadProgress.postValue(new DownloadProgress(0, 0));

        String url = AppPreferences.getServerUrl(getApplication());
        final List<WebEditorApiClient.SceneInfo> scenesToDownload = new ArrayList<>(sceneList);

        executor.execute(() -> {
            try {
                WebEditorApiClient client = new WebEditorApiClient(url);

                File projectDir  = getOrCreateProjectDir(projectId, projectName);
                File scenesDir   = new File(projectDir, "scenes");
                File texturesDir = new File(projectDir, "textures");
                scenesDir.mkdirs();
                texturesDir.mkdirs();

                // ── Phase 1: Fetch all scene JSONs ─────────────────────────────
                Map<String, String> sceneJsonMap = new LinkedHashMap<>();
                Set<String> allTextureFilenames = new HashSet<>();
                for (WebEditorApiClient.SceneInfo scene : scenesToDownload) {
                    String json = client.fetchSceneData(scene.id);
                    sceneJsonMap.put(scene.name, json);
                    allTextureFilenames.addAll(extractTextureFilenames(json));
                }

                // ── Phase 2: Preflight — sum content lengths for progress bar ──
                long totalBytes = 0;
                for (String json : sceneJsonMap.values()) {
                    totalBytes += json.getBytes("UTF-8").length;
                }
                for (String filename : allTextureFilenames) {
                    File dest = new File(texturesDir, filename);
                    if (dest.exists()) {
                        totalBytes += dest.length();
                    } else {
                        long size = client.getRemoteFileSize("/uploads/" + filename);
                        if (size > 0) totalBytes += size;
                    }
                }
                final long finalTotal = totalBytes;
                downloadProgress.postValue(new DownloadProgress(0, finalTotal));

                // ── Phase 3: Download textures ─────────────────────────────────
                long downloadedBytes = 0;
                for (String filename : allTextureFilenames) {
                    File dest = new File(texturesDir, filename);
                    if (dest.exists()) {
                        downloadedBytes += dest.length();
                    } else {
                        byte[] bytes = client.downloadUpload(filename);
                        writeFile(dest, bytes);
                        downloadedBytes += bytes.length;
                    }
                    final long progress = downloadedBytes;
                    downloadProgress.postValue(new DownloadProgress(progress, finalTotal));
                }

                // ── Phase 4: Save scene JSONs ──────────────────────────────────
                for (Map.Entry<String, String> entry : sceneJsonMap.entrySet()) {
                    String sceneName = entry.getKey();
                    String json = entry.getValue();
                    try {
                        JSONObject root = new JSONObject(json);
                        if (root.has("data")) json = root.getJSONObject("data").toString();
                    } catch (Exception ignored) {}
                    String filename = sceneName.endsWith(".json") ? sceneName : sceneName + ".json";
                    byte[] jsonBytes = json.getBytes("UTF-8");
                    writeFile(new File(scenesDir, filename), jsonBytes);
                    downloadedBytes += jsonBytes.length;
                    final long progress = downloadedBytes;
                    downloadProgress.postValue(new DownloadProgress(progress, finalTotal));
                }

                // ── Phase 5: Write manifest ────────────────────────────────────
                saveProjectManifest(projectDir, projectId, projectName, scenesToDownload);
                state.postValue(ProjectState.DOWNLOADED);

            } catch (Exception e) {
                TimberLog.e(TAG, "Download failed", e);
                error.postValue("Download failed: " + e.getMessage());
                state.postValue(ProjectState.NOT_DOWNLOADED);
            }
        });
    }

    // ── Activate as wallpaper ──────────────────────────────────────────────────

    /** Saves this project's dirs to WallpaperPreferences so the service renders from them. */
    public void activateProject(String projectId) {
        executor.execute(() -> {
            File dir = findProjectDir(projectId);
            if (dir == null) {
                error.postValue("Project folder not found — try re-downloading.");
                return;
            }
            WallpaperPreferences.setActiveProject(
                    getApplication(),
                    projectId,
                    new File(dir, "scenes").getAbsolutePath(),
                    new File(dir, "textures").getAbsolutePath());
            // If the wallpaper is already running, post a GL-thread-safe reload request.
            // If it's not running yet, the updated WallpaperPreferences will be picked up
            // when the service starts after the user sets it via the system chooser.
            GLWallpaperService.requestProjectReload();
            wallpaperActivated.postValue(true);
        });
    }

    // ── Delete ─────────────────────────────────────────────────────────────────

    public void deleteProject(String projectId) {
        executor.execute(() -> {
            File dir = findProjectDir(projectId);
            if (dir != null) deleteDirectory(dir);
            state.postValue(ProjectState.NOT_DOWNLOADED);
        });
    }

    // ── Project directory helpers ──────────────────────────────────────────────

    /**
     * Scans the projects root for a directory whose name ends with "_{projectId}".
     * Returns null if not found.
     */
    private File findProjectDir(String projectId) {
        File root = getProjectsRoot();
        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs == null) return null;
        String suffix = "_" + projectId;
        for (File dir : dirs) {
            if (dir.getName().endsWith(suffix)) return dir;
        }
        return null;
    }

    /** Returns an existing project dir (via findProjectDir) or creates a new one. */
    private File getOrCreateProjectDir(String projectId, String projectName) {
        File existing = findProjectDir(projectId);
        if (existing != null) return existing;
        String safeName = projectName.replaceAll("[^a-zA-Z0-9._-]", "_");
        File dir = new File(getProjectsRoot(), safeName + "_" + projectId);
        dir.mkdirs();
        return dir;
    }

    private File getProjectsRoot() {
        File root = new File(getApplication().getExternalFilesDir(null), "projects");
        root.mkdirs();
        return root;
    }

    // ── Manifest helpers ───────────────────────────────────────────────────────

    private boolean isProjectDownloaded(String projectId) {
        File dir = findProjectDir(projectId);
        return dir != null && new File(dir, "manifest.json").exists();
    }

    private void saveProjectManifest(File projectDir, String projectId, String projectName,
                                     List<WebEditorApiClient.SceneInfo> sceneList) {
        try {
            JSONObject manifest = new JSONObject();
            manifest.put("id", projectId);
            manifest.put("name", projectName);
            manifest.put("version", "");
            JSONArray arr = new JSONArray();
            for (WebEditorApiClient.SceneInfo s : sceneList) {
                JSONObject obj = new JSONObject();
                obj.put("id", s.id);
                obj.put("name", s.name);
                obj.put("label", s.label != null ? s.label : s.name);
                arr.put(obj);
            }
            manifest.put("scenes", arr);
            writeFile(new File(projectDir, "manifest.json"),
                    manifest.toString().getBytes("UTF-8"));
        } catch (Exception e) {
            TimberLog.e(TAG, "Failed to save project manifest", e);
        }
    }

    // ── JSON helpers ───────────────────────────────────────────────────────────

    private Set<String> extractTextureFilenames(String sceneJson) {
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

    // ── File I/O ───────────────────────────────────────────────────────────────

    private static void writeFile(File dest, byte[] data) throws IOException {
        dest.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(dest)) {
            fos.write(data);
        }
    }

    private static void deleteDirectory(File dir) {
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) deleteDirectory(child);
                else child.delete();
            }
        }
        dir.delete();
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
    }
}

