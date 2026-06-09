package com.hashilab.dev.editor.utils;

import android.app.Application;

import com.example.livewallpaper.logging.TimberLog;
import com.hashilab.dev.editor.network.WebEditorApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProjectFileManager {

    private static final String TAG = "ProjectFileManager";

    public interface ProgressCallback {
        void onProgress(long downloadedBytes, long totalBytes);
    }

    private final Application application;

    public ProjectFileManager(Application application) {
        this.application = application;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public boolean isProjectDownloaded(String projectId) {
        File dir = findProjectDir(projectId);
        return dir != null && new File(dir, "manifest.json").exists();
    }

    public String getLocalProjectVersion(String projectId) {
        File dir = findProjectDir(projectId);
        if (dir == null) return "";
        File manifestFile = new File(dir, "manifest.json");
        if (!manifestFile.exists()) return "";
        try {
            JSONObject obj = new JSONObject(new String(readFile(manifestFile), "UTF-8"));
            return obj.optString("version", "");
        } catch (Exception e) {
            return "";
        }
    }

    /** Fetches the server version and returns true if it differs from the locally stored version. */
    public boolean checkVersionNeedsUpdate(String projectId) {
        try {
            if (!isProjectDownloaded(projectId)) return false;
            WebEditorApiClient.Project project = buildClient().fetchProject(projectId);
            if (project.version.isEmpty()) return false;
            return !project.version.equals(getLocalProjectVersion(projectId));
        } catch (Exception e) {
            TimberLog.w(TAG, "Failed to check project version: " + e.getMessage());
            return false;
        }
    }

    public File findProjectDir(String projectId) {
        File root = getProjectsRoot();
        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs == null) return null;
        String suffix = "_" + projectId;
        for (File dir : dirs) {
            if (dir.getName().endsWith(suffix)) return dir;
        }
        return null;
    }

    /**
     * Downloads a project from the server into its local directory.
     * Skips textures that are already cached. Blocking — call from a background thread.
     */
    public void downloadProject(String projectId, String projectName,
                                List<WebEditorApiClient.SceneInfo> scenes,
                                ProgressCallback callback) throws Exception {
        WebEditorApiClient client = buildClient();

        File projectDir  = getOrCreateProjectDir(projectId, projectName);
        File scenesDir   = new File(projectDir, "scenes");
        File texturesDir = new File(projectDir, "textures");
        scenesDir.mkdirs();
        texturesDir.mkdirs();

        // Phase 1: Fetch all scene JSONs and extract texture filenames
        Map<String, String> sceneJsonMap = new LinkedHashMap<>();
        Set<String> allTextureFilenames = new HashSet<>();
        for (WebEditorApiClient.SceneInfo scene : scenes) {
            String json = client.fetchSceneData(scene.id);
            sceneJsonMap.put(scene.name, json);
            allTextureFilenames.addAll(extractTextureFilenames(json));
        }

        // Phase 2: Preflight — sum content lengths for progress bar
        long totalBytes = 0;
        for (String json : sceneJsonMap.values()) totalBytes += json.getBytes("UTF-8").length;
        for (String filename : allTextureFilenames) {
            File dest = new File(texturesDir, filename);
            totalBytes += dest.exists() ? dest.length() : Math.max(0, client.getRemoteFileSize("/uploads/" + filename));
        }
        if (callback != null) callback.onProgress(0, totalBytes);
        final long finalTotal = totalBytes;

        // Phase 3: Download textures (skip if already cached)
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
            if (callback != null) callback.onProgress(downloadedBytes, finalTotal);
        }

        // Phase 4: Save scene JSONs
        for (Map.Entry<String, String> entry : sceneJsonMap.entrySet()) {
            String json = unwrapDataEnvelope(entry.getValue());
            String filename = toJsonFilename(entry.getKey());
            byte[] jsonBytes = json.getBytes("UTF-8");
            writeFile(new File(scenesDir, filename), jsonBytes);
            downloadedBytes += jsonBytes.length;
            if (callback != null) callback.onProgress(downloadedBytes, finalTotal);
        }

        // Phase 5: Fetch server version and write manifest
        String serverVersion = "";
        try {
            serverVersion = client.fetchProject(projectId).version;
        } catch (Exception e) {
            TimberLog.w(TAG, "Could not fetch project version: " + e.getMessage());
        }
        saveProjectManifest(projectDir, projectId, projectName, scenes, serverVersion);
    }

    /**
     * Downloads updated project content into a temp directory, then atomically replaces the
     * existing project directory. Blocking — call from a background thread.
     */
    public void downloadProjectUpdate(String projectId, String projectName,
                                      List<WebEditorApiClient.SceneInfo> scenes,
                                      ProgressCallback callback) throws Exception {
        File projectDir = findProjectDir(projectId);
        if (projectDir == null) {
            throw new IOException("Project folder not found — try re-downloading.");
        }

        File tempDir     = new File(getProjectsRoot(), projectDir.getName() + "_update_tmp");
        File scenesDir   = new File(tempDir, "scenes");
        File texturesDir = new File(tempDir, "textures");
        scenesDir.mkdirs();
        texturesDir.mkdirs();

        try {
            WebEditorApiClient client = buildClient();
            WebEditorApiClient.Project project = client.fetchProject(projectId);

            // Phase 1: Fetch all scene JSONs and extract texture filenames
            Map<String, String> sceneJsonMap = new LinkedHashMap<>();
            Set<String> allTextureFilenames = new HashSet<>();
            for (WebEditorApiClient.SceneInfo scene : scenes) {
                String json = client.fetchSceneData(scene.id);
                sceneJsonMap.put(scene.name, json);
                allTextureFilenames.addAll(extractTextureFilenames(json));
            }

            // Phase 2: Preflight — sum content lengths for progress bar
            long totalBytes = 0;
            for (String json : sceneJsonMap.values()) totalBytes += json.getBytes("UTF-8").length;
            for (String filename : allTextureFilenames) {
                totalBytes += Math.max(0, client.getRemoteFileSize("/uploads/" + filename));
            }
            if (callback != null) callback.onProgress(0, totalBytes);
            final long finalTotal = totalBytes;

            // Phase 3: Download all textures fresh
            long downloadedBytes = 0;
            for (String filename : allTextureFilenames) {
                byte[] bytes = client.downloadUpload(filename);
                writeFile(new File(texturesDir, filename), bytes);
                downloadedBytes += bytes.length;
                if (callback != null) callback.onProgress(downloadedBytes, finalTotal);
            }

            // Phase 4: Save scene JSONs
            for (Map.Entry<String, String> entry : sceneJsonMap.entrySet()) {
                String json = unwrapDataEnvelope(entry.getValue());
                String filename = toJsonFilename(entry.getKey());
                byte[] jsonBytes = json.getBytes("UTF-8");
                writeFile(new File(scenesDir, filename), jsonBytes);
                downloadedBytes += jsonBytes.length;
                if (callback != null) callback.onProgress(downloadedBytes, finalTotal);
            }

            // Phase 5: Write manifest with server version
            saveProjectManifest(tempDir, projectId, projectName, scenes, project.version);

            // Phase 6: Atomically replace existing project dir
            replaceProjectDir(projectDir, tempDir);

        } catch (Exception e) {
            deleteDirectory(tempDir);
            throw e;
        }
    }

    public void deleteProject(String projectId) {
        File dir = findProjectDir(projectId);
        if (dir != null) deleteDirectory(dir);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private WebEditorApiClient buildClient() {
        return new WebEditorApiClient(AppPreferences.getServerUrl(application));
    }

    private File getProjectsRoot() {
        File root = new File(application.getExternalFilesDir(null), "projects");
        root.mkdirs();
        return root;
    }

    private File getOrCreateProjectDir(String projectId, String projectName) {
        File existing = findProjectDir(projectId);
        if (existing != null) return existing;
        String safeName = projectName.replaceAll("[^a-zA-Z0-9._-]", "_");
        File dir = new File(getProjectsRoot(), safeName + "_" + projectId);
        dir.mkdirs();
        return dir;
    }

    private void saveProjectManifest(File projectDir, String projectId, String projectName,
                                     List<WebEditorApiClient.SceneInfo> sceneList, String version) {
        try {
            JSONObject manifest = new JSONObject();
            manifest.put("id", projectId);
            manifest.put("name", projectName);
            manifest.put("version", version);
            JSONArray arr = new JSONArray();
            for (WebEditorApiClient.SceneInfo s : sceneList) {
                JSONObject obj = new JSONObject();
                obj.put("id", s.id);
                obj.put("name", s.name);
                obj.put("label", s.label != null ? s.label : s.name);
                arr.put(obj);
            }
            manifest.put("scenes", arr);
            writeFile(new File(projectDir, "manifest.json"), manifest.toString().getBytes("UTF-8"));
        } catch (Exception e) {
            TimberLog.e(TAG, "Failed to save project manifest", e);
        }
    }

    private Set<String> extractTextureFilenames(String sceneJson) {
        Set<String> filenames = new HashSet<>();
        try {
            JSONObject root = new JSONObject(sceneJson);
            JSONObject data = root.has("data") ? root.getJSONObject("data") : root;
            JSONArray sprites = data.optJSONArray("sprites");
            if (sprites == null) return filenames;
            for (int i = 0; i < sprites.length(); i++) {
                String resource = sprites.getJSONObject(i).optString("textureResource", "");
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

    private static String unwrapDataEnvelope(String json) {
        try {
            JSONObject root = new JSONObject(json);
            if (root.has("data")) return root.getJSONObject("data").toString();
        } catch (Exception ignored) {}
        return json;
    }

    private static String toJsonFilename(String name) {
        return name.endsWith(".json") ? name : name + ".json";
    }

    private static void writeFile(File dest, byte[] data) throws IOException {
        dest.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(dest)) {
            fos.write(data);
        }
    }

    private static byte[] readFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    private static void replaceProjectDir(File projectDir, File tempDir) throws IOException {
        File backupDir = new File(projectDir.getParent(), projectDir.getName() + "_bak");
        if (!projectDir.renameTo(backupDir)) {
            throw new IOException("Failed to move project dir to backup");
        }
        if (!tempDir.renameTo(projectDir)) {
            backupDir.renameTo(projectDir);
            throw new IOException("Failed to move updated project into place");
        }
        deleteDirectory(backupDir);
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
}
