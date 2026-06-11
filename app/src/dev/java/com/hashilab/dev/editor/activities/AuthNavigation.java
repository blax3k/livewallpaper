package com.hashilab.dev.editor.activities;

import android.app.Activity;
import android.content.Intent;

import com.hashilab.dev.editor.utils.AppPreferences;

class AuthNavigation {

    static void signOut(Activity activity) {
        AppPreferences.clearSessionCookie(activity);
        Intent intent = new Intent(activity, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}
