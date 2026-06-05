package com.hashilab.dev.editor.network;

import com.example.livewallpaper.logging.TimberLog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal HTTP client for talking to the Live Wallpaper Web Editor backend.
 * All methods are blocking — call them from a background thread.
 */
public class WebEditorApiClient {

    private static final String TAG = "WebEditorApiClient";
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    public static class Project {
        public final String id;
        public final String name;
        public final String version;
        public final List<String> sceneThumbnailUrls;

        public Project(String id, String name, String version, List<String> sceneThumbnailUrls) {
            this.id = id;
            this.name = name;
            this.version = version != null ? version : "";
            this.sceneThumbnailUrls = sceneThumbnailUrls != null ? sceneThumbnailUrls : new ArrayList<>();
        }

        @Override
        public String toString() { return name; }
    }

    public static class SceneInfo {
        public final String id;
        public final String name;
        public final String label;
        public final String updatedAt;
        public SceneInfo(String id, String name, String label, String updatedAt) {
            this.id = id;
            this.name = name;
            this.label = label;
            this.updatedAt = updatedAt != null ? updatedAt : "";
        }
        @Override
        public String toString() { return label != null ? label : name; }
    }

    private final String baseUrl;

    public WebEditorApiClient(String baseUrl) {
        // Strip trailing slash for consistent URL construction
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** Fetch the list of all projects. */
    public List<Project> fetchProjects() throws IOException {
        String json = get("/api/projects");
        List<Project> projects = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                
                JSONArray urlsArr = obj.optJSONArray("scene_thumbnail_urls");
                List<String> sceneThumbnailUrls = new ArrayList<>();
                if (urlsArr != null) {
                    for (int j = 0; j < urlsArr.length(); j++) {
                        sceneThumbnailUrls.add(urlsArr.getString(j));
                    }
                }

                projects.add(new Project(
                        obj.getString("id"),
                        obj.getString("name"),
                        obj.optString("version", ""),
                        sceneThumbnailUrls));
            }
        } catch (Exception e) {
            throw new IOException("Failed to parse projects response: " + e.getMessage(), e);
        }
        return projects;
    }

    /** Fetch the scenes belonging to a project (id, name, label only). */
    public List<SceneInfo> fetchScenesForProject(String projectId) throws IOException {
        String json = get("/api/scenes?projectId=" + projectId);
        List<SceneInfo> scenes = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String id = obj.optString("id", "");
                String name = obj.optString("name", "");
                String label = obj.optString("label", name);
                String updatedAt = obj.optString("updated_at", "");
                scenes.add(new SceneInfo(id, name, label, updatedAt));
            }
        } catch (Exception e) {
            throw new IOException("Failed to parse scenes response: " + e.getMessage(), e);
        }
        return scenes;
    }

    /** Fetch the full JSON data for a scene by its name. Returns the raw JSON string. */
    public String fetchSceneData(String sceneName) throws IOException {
        return get("/api/scenes/" + sceneName);
    }

    /** Download a binary file (uploaded image) and return its bytes. */
    public byte[] downloadUpload(String filename) throws IOException {
        return getBytes("/uploads/" + filename);
    }

    /**
     * Returns the Content-Length of a remote path via a HEAD request, or -1 if unknown.
     * Use this to preflight file sizes before downloading.
     */
    public long getRemoteFileSize(String path) throws IOException {
        String urlString = baseUrl + path;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("HEAD");
            conn.connect();
            return conn.getContentLengthLong();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // -------------------------------------------------------------------------

    private String get(String path) throws IOException {
        byte[] bytes = getBytes(path);
        return new String(bytes, "UTF-8");
    }

    private byte[] getBytes(String path) throws IOException {
        String urlString = baseUrl + path;
        TimberLog.d(TAG, "GET " + urlString);

        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json, */*");

            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status + " for " + urlString);
            }

            return readStream(conn.getInputStream());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static byte[] readStream(InputStream is) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = is.read(chunk)) != -1) {
            buf.write(chunk, 0, n);
        }
        return buf.toByteArray();
    }
}
