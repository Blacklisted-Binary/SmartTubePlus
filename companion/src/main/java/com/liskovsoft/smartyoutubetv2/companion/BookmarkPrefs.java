package com.liskovsoft.smartyoutubetv2.companion;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists up to {@link Bookmark#SLOT_COUNT} bookmarks using SharedPreferences.
 */
public class BookmarkPrefs {
    private static final String PREFS_NAME = "bookmarks";
    private static final String KEY_LABEL  = "bm_label_";
    private static final String KEY_URL    = "bm_url_";

    private final SharedPreferences mPrefs;

    public BookmarkPrefs(Context context) {
        mPrefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public List<Bookmark> loadAll() {
        List<Bookmark> list = new ArrayList<>(Bookmark.SLOT_COUNT);
        for (int i = 0; i < Bookmark.SLOT_COUNT; i++) {
            String label = mPrefs.getString(KEY_LABEL + i, "");
            String url   = mPrefs.getString(KEY_URL   + i, "");
            list.add(new Bookmark(label, url));
        }
        return list;
    }

    public void save(int slot, Bookmark bookmark) {
        if (slot < 0 || slot >= Bookmark.SLOT_COUNT) return;
        mPrefs.edit()
                .putString(KEY_LABEL + slot, bookmark.label)
                .putString(KEY_URL   + slot, bookmark.url)
                .apply();
    }
}
