package com.example.livewallpaper.scene.models;

import java.util.ArrayList;
import java.util.List;

public class Project {
    public String name;
    public int version;
    public String id;
    public List<String> sceneThumbnailUrls;

    public Project(String id, String name, String version, List<String> sceneThumbnailUrls) {
        this.id = id;
        this.name = name;
        this.version = (version == null || version.isEmpty()) ? 0 : Integer.parseInt(version);
        this.sceneThumbnailUrls = sceneThumbnailUrls != null ? sceneThumbnailUrls : new ArrayList<>();
    }
}
