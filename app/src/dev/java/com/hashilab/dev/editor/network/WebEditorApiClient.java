package com.hashilab.dev.editor.network;

import com.example.livewallpaper.logging.TimberLog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Minimal HTTP client for talking to the Live Wallpaper Web Editor backend.
 * All methods are blocking — call them from a background thread.
 */
public class WebEditorApiClient {

    private static final String TAG = "WebEditorApiClient";
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    public static class AuthException extends IOException {
        public AuthException(String message) { super(message); }
    }

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
    private final String sessionCookie;

    public WebEditorApiClient(String baseUrl) {
        this(baseUrl, null);
    }

    public WebEditorApiClient(String baseUrl, String sessionCookie) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.sessionCookie = sessionCookie;
    }

    /**
     * Authenticate with the server. Returns the raw session cookie value to store and pass back
     * to the constructor. Throws {@link AuthException} for invalid credentials.
     */
    public static String login(String baseUrl, String email, String password) throws IOException {
        String urlString = stripTrailingSlash(baseUrl) + "/api/auth/login";
        TimberLog.d(TAG, "POST " + urlString);

        byte[] body = buildLoginBody(email, password);
        HttpURLConnection conn = null;
        try {
            conn = openPostConnection(urlString, body);
            int status = conn.getResponseCode();
            if (status == 401 || status == 400) throw parseAuthError(conn);
            if (status < 200 || status >= 300) throw new IOException("HTTP " + status + " for " + urlString);
            String cookie = extractSidCookie(conn);
            if (cookie == null) throw new IOException("No session cookie returned from login");
            return cookie;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static byte[] buildLoginBody(String email, String password) throws IOException {
        try {
            JSONObject body = new JSONObject();
            body.put("email", email);
            body.put("password", password);
            return body.toString().getBytes("UTF-8");
        } catch (Exception e) {
            throw new IOException("Failed to build login request", e);
        }
    }

    private static HttpURLConnection openPostConnection(String urlString, byte[] body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }
        return conn;
    }

    private static AuthException parseAuthError(HttpURLConnection conn) {
        String message = "Invalid email or password";
        InputStream errStream = conn.getErrorStream();
        if (errStream != null) {
            try {
                String errBody = new String(readStream(errStream), "UTF-8");
                String msg = new JSONObject(errBody).optString("message", "");
                if (!msg.isEmpty()) message = msg;
            } catch (Exception ignored) {}
        }
        return new AuthException(message);
    }

    /** Returns the sid cookie value from Set-Cookie headers, or null if absent. */
    private static String extractSidCookie(HttpURLConnection conn) {
        for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
            if (!"set-cookie".equalsIgnoreCase(entry.getKey()) || entry.getValue() == null) continue;
            for (String c : entry.getValue()) {
                if (!c.startsWith("sid=")) 
                {
                    continue;
                }
                String value = c.substring("sid=".length());
                int semi = value.indexOf(';');
                return semi >= 0 ? value.substring(0, semi) : value;
            }
        }
        return null;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** Fetch the list of all projects. */
    public List<Project> fetchProjects() throws IOException {
        String json = get("/api/projects?activeOnly=true");
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

    /** Fetch a single project by ID. */
    public Project fetchProject(String projectId) throws IOException {
        String json = get("/api/projects/" + projectId);
        try {
            JSONObject obj = new JSONObject(json);
            return new Project(
                    obj.getString("id"),
                    obj.getString("name"),
                    obj.optString("version", ""),
                    new ArrayList<>());
        } catch (Exception e) {
            throw new IOException("Failed to parse project response: " + e.getMessage(), e);
        }
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
            if (sessionCookie != null) {
                conn.setRequestProperty("Cookie", "sid=" + sessionCookie);
            }
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
            if (sessionCookie != null) {
                conn.setRequestProperty("Cookie", "sid=" + sessionCookie);
            }

            int status = conn.getResponseCode();
            if (status == 401) throw new AuthException("Session expired — please sign in again");
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
