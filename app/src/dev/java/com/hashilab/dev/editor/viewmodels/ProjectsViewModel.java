package com.hashilab.dev.editor.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.livewallpaper.logging.TimberLog;
import com.example.livewallpaper.scene.models.Project;
import com.hashilab.dev.editor.network.WebEditorApiClient;
import com.hashilab.dev.editor.utils.AppPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProjectsViewModel extends AndroidViewModel {

    private static final String TAG = "ProjectsViewModel";

    private final MutableLiveData<List<Project>> projects = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public ProjectsViewModel(@NonNull Application application) {
        super(application);
        loadProjects();
    }

    public LiveData<List<Project>> getProjects() { return projects; }
    public LiveData<String> getError() { return error; }
    public LiveData<Boolean> getLoading() { return loading; }

    public void loadProjects() {
        loading.postValue(true);
        String url = AppPreferences.getServerUrl(getApplication());
        String cookie = AppPreferences.getSessionCookie(getApplication());
        executor.execute(() -> {
            try {
                WebEditorApiClient client = new WebEditorApiClient(url, cookie);
                List<WebEditorApiClient.Project> fetched = client.fetchProjects();
                List<Project> result = new ArrayList<>();
                for (WebEditorApiClient.Project p : fetched) {
                    result.add(new Project(p.id, p.name, p.version, p.sceneThumbnailUrls));
                }
                projects.postValue(result);
            } catch (WebEditorApiClient.AuthException e) {
                error.postValue("UNAUTHORIZED");
            } catch (Exception e) {
                TimberLog.e(TAG, "Failed to load projects", e);
                error.postValue(e.getMessage());
            } finally {
                loading.postValue(false);
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}

