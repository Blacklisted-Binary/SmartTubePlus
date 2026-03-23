package com.liskovsoft.smartyoutubetv2.companion;

/** Immutable value object for a user-defined bookmark. */
public class Bookmark {
    public static final int SLOT_COUNT = 6;

    public final String label;
    /** Full YouTube URL, search query, or channel URL. Empty string = unset slot. */
    public final String url;

    public Bookmark(String label, String url) {
        this.label = label == null ? "" : label;
        this.url = url == null ? "" : url;
    }

    public boolean isEmpty() {
        return url.isEmpty();
    }
}
