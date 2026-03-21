package com.liskovsoft.smartyoutubetv2.companion;

import android.app.Application;

public class CompanionApplication extends Application {

    private static CompanionApplication sInstance;

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
    }

    public static CompanionApplication getInstance() {
        return sInstance;
    }
}
