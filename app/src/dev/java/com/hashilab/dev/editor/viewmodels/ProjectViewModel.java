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
import com.hashilab.dev.editor.utils.ProjectFileManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProjectViewModel extends AndroidViewModel {

    private static final String TAG = "ProjectViewModel";

    // ── State enum ─────────────────────────────────────────────────────────────

    public enum ProjectState {
        NOT_DOWNLOADED,
        DOWNLOADING,
        DOWNLOADED,
        OUT_OF_SYNC
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
    private final MutableLiveData<Boolean> updateAvailable = new MutableLiveData<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ProjectFileManager fileManager;

    public ProjectViewModel(@NonNull Application application) {
        super(application);
        fileManager = new ProjectFileManager(application);
    }

    public LiveData<List<WebEditorApiClient.SceneInfo>> getScenes() { return scenes; }
    public LiveData<ProjectState> getState() { return state; }
    public LiveData<DownloadProgress> getDownloadProgress() { return downloadProgress; }
    public LiveData<String> getError() { return error; }
    public LiveData<Boolean> getWallpaperActivated() { return wallpaperActivated; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<Boolean> getUpdateAvailable() { return updateAvailable; }

    // ── Scene loading ──────────────────────────────────────────────────────────

    /** Fetches scenes from the server and checks download state. Skips re-fetch if already loaded. */
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
        String cookie = AppPreferences.getSessionCookie(getApplication());
        executor.execute(() -> {
            try {
                WebEditorApiClient client = new WebEditorApiClient(url, cookie);
                List<WebEditorApiClient.SceneInfo> fetched = client.fetchScenesForProject(projectId);
                scenes.postValue(fetched);
                postDownloadState(projectId);
                updateAvailable.postValue(fileManager.checkVersionNeedsUpdate(projectId));
            } catch (WebEditorApiClient.AuthException e) {
                error.postValue("UNAUTHORIZED");
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
        state.postValue(fileManager.isProjectDownloaded(projectId)
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
        final List<WebEditorApiClient.SceneInfo> snapshot = new ArrayList<>(sceneList);
        executor.execute(() -> {
            try {
                fileManager.downloadProject(projectId, projectName, snapshot,
                        (downloaded, total) -> downloadProgress.postValue(new DownloadProgress(downloaded, total)));
                state.postValue(ProjectState.DOWNLOADED);
            } catch (WebEditorApiClient.AuthException e) {
                error.postValue("UNAUTHORIZED");
                state.postValue(ProjectState.NOT_DOWNLOADED);
            } catch (Exception e) {
                TimberLog.e(TAG, "Download failed", e);
                error.postValue("Download failed: " + e.getMessage());
                state.postValue(ProjectState.NOT_DOWNLOADED);
            }
        });
    }

    // ── Update ─────────────────────────────────────────────────────────────────

    public void downloadProjectUpdate(String projectId, String projectName) {
        List<WebEditorApiClient.SceneInfo> sceneList = scenes.getValue();
        if (sceneList == null || sceneList.isEmpty()) {
            error.postValue("No scenes loaded — cannot update.");
            return;
        }
        state.postValue(ProjectState.DOWNLOADING);
        downloadProgress.postValue(new DownloadProgress(0, 0));
        final List<WebEditorApiClient.SceneInfo> snapshot = new ArrayList<>(sceneList);
        executor.execute(() -> {
            try {
                fileManager.downloadProjectUpdate(projectId, projectName, snapshot,
                        (downloaded, total) -> downloadProgress.postValue(new DownloadProgress(downloaded, total)));
                state.postValue(ProjectState.DOWNLOADED);
                updateAvailable.postValue(false);
                if (isCurrentProjectMine(projectId)) {
                    File dir = fileManager.findProjectDir(projectId);
                    if (dir != null) {
                        WallpaperPreferences.setActiveProject(
                                getApplication(),
                                projectId,
                                new File(dir, "scenes").getAbsolutePath(),
                                new File(dir, "textures").getAbsolutePath());
                        GLWallpaperService.requestProjectReload();
                    }
                }
            } catch (WebEditorApiClient.AuthException e) {
                error.postValue("UNAUTHORIZED");
                state.postValue(ProjectState.DOWNLOADED);
            } catch (Exception e) {
                TimberLog.e(TAG, "Project update failed", e);
                error.postValue("Update failed: " + e.getMessage());
                state.postValue(ProjectState.DOWNLOADED);
            }
        });
    }

    // ── Activate as wallpaper ──────────────────────────────────────────────────

    /** Saves this project's dirs to WallpaperPreferences so the service renders from them. */
    public void activateProject(String projectId) {
        executor.execute(() -> {
            File dir = fileManager.findProjectDir(projectId);
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
            fileManager.deleteProject(projectId);
            state.postValue(ProjectState.NOT_DOWNLOADED);
        });
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

    // ── Wallpaper helpers ──────────────────────────────────────────────────────

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

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
    }
}
