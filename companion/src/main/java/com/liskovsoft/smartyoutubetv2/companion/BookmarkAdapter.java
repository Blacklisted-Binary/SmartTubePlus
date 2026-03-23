package com.liskovsoft.smartyoutubetv2.companion;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * 2-column grid of up to {@link Bookmark#SLOT_COUNT} bookmark buttons.
 * Tap = open bookmark; long-press = edit bookmark.
 */
public class BookmarkAdapter extends RecyclerView.Adapter<BookmarkAdapter.ViewHolder> {

    public interface Listener {
        void onBookmarkTap(int slot, Bookmark bookmark);
        void onBookmarkLongPress(int slot, Bookmark bookmark);
    }

    private List<Bookmark> mBookmarks;
    private final Listener mListener;

    public BookmarkAdapter(List<Bookmark> bookmarks, Listener listener) {
        mBookmarks = bookmarks;
        mListener  = listener;
    }

    public void setBookmarks(List<Bookmark> bookmarks) {
        mBookmarks = bookmarks;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bookmark, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int pos) {
        Bookmark bm = mBookmarks.get(pos);
        String label = bm.isEmpty()
                ? h.btn.getContext().getString(R.string.bookmark_empty_slot, pos + 1)
                : bm.label.isEmpty() ? bm.url : bm.label;
        h.btn.setText(label);
        h.btn.setAlpha(bm.isEmpty() ? 0.45f : 1.0f);

        h.btn.setOnClickListener(v -> {
            if (mListener != null) mListener.onBookmarkTap(pos, bm);
        });
        h.btn.setOnLongClickListener(v -> {
            if (mListener != null) mListener.onBookmarkLongPress(pos, bm);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return mBookmarks == null ? 0 : mBookmarks.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final Button btn;
        ViewHolder(@NonNull View v) {
            super(v);
            btn = v.findViewById(R.id.btn_bookmark);
        }
    }
}
