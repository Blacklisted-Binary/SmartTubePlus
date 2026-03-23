package com.liskovsoft.smartyoutubetv2.companion;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Main remote-control screen.
 *
 * <p>Sections: status bar | device list | voice/keyboard row | D-pad buttons | bookmarks</p>
 */
public class MainActivity extends AppCompatActivity {

    private static final int BOOKMARK_COLS = 2;

    private CompanionViewModel mViewModel;
    private TextView mTvStatus;
    private DeviceListAdapter mAdapter;
    private BookmarkAdapter mBookmarkAdapter;

    /** Launcher for the system speech recogniser */
    private ActivityResultLauncher<Intent> mSpeechLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mViewModel = new ViewModelProvider(this).get(CompanionViewModel.class);

        // Speech recogniser result
        mSpeechLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        List<String> matches = result.getData()
                                .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (matches != null && !matches.isEmpty()) {
                            mViewModel.search(matches.get(0));
                        }
                    }
                });

        // Status bar
        mTvStatus = findViewById(R.id.tv_status);

        // Device list
        RecyclerView rvDevices = findViewById(R.id.rv_devices);
        rvDevices.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new DeviceListAdapter(new ArrayList<>(), device -> mViewModel.selectDevice(device));
        rvDevices.setAdapter(mAdapter);

        // Bookmark grid
        RecyclerView rvBookmarks = findViewById(R.id.rv_bookmarks);
        rvBookmarks.setLayoutManager(new GridLayoutManager(this, BOOKMARK_COLS));
        mBookmarkAdapter = new BookmarkAdapter(new ArrayList<>(), new BookmarkAdapter.Listener() {
            @Override
            public void onBookmarkTap(int slot, Bookmark bookmark) {
                if (bookmark.isEmpty()) {
                    showBookmarkEditDialog(slot, bookmark);
                } else {
                    mViewModel.openBookmark(bookmark);
                }
            }

            @Override
            public void onBookmarkLongPress(int slot, Bookmark bookmark) {
                showBookmarkEditDialog(slot, bookmark);
            }
        });
        rvBookmarks.setAdapter(mBookmarkAdapter);

        // Observe VM
        mViewModel.getDevices().observe(this, this::onDevicesUpdated);
        mViewModel.getStatusMessage().observe(this, mTvStatus::setText);
        mViewModel.getBookmarks().observe(this, bookmarks -> mBookmarkAdapter.setBookmarks(bookmarks));

        // Voice button
        bindControlButton(R.id.btn_voice, this::startVoiceSearch);

        // Keyboard / text input button
        bindControlButton(R.id.btn_keyboard, this::showKeyboardSearch);

        // Playback remote-control buttons
        bindControlButton(R.id.btn_play,      () -> mViewModel.sendCommand("PLAY"));
        bindControlButton(R.id.btn_pause,     () -> mViewModel.sendCommand("PAUSE"));
        bindControlButton(R.id.btn_toggle,    () -> mViewModel.sendCommand("TOGGLE"));
        bindControlButton(R.id.btn_next,      () -> mViewModel.sendCommand("NEXT"));
        bindControlButton(R.id.btn_prev,      () -> mViewModel.sendCommand("PREV"));
        bindControlButton(R.id.btn_seek_fwd,  () -> mViewModel.sendCommand("SEEK_FWD"));
        bindControlButton(R.id.btn_seek_bwd,  () -> mViewModel.sendCommand("SEEK_BWD"));
        bindControlButton(R.id.btn_vol_up,    () -> mViewModel.sendCommand("VOL_UP"));
        bindControlButton(R.id.btn_vol_down,  () -> mViewModel.sendCommand("VOL_DOWN"));
    }

    // ---- Voice search -------------------------------------------------------

    private void startVoiceSearch() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_search_hint));
        try {
            mSpeechLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.voice_not_supported, Toast.LENGTH_SHORT).show();
        }
    }

    // ---- Keyboard / text search ---------------------------------------------

    private void showKeyboardSearch() {
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        et.setHint(R.string.keyboard_search_hint);

        new AlertDialog.Builder(this)
                .setTitle(R.string.keyboard_search_title)
                .setView(et)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String text = et.getText().toString().trim();
                    if (!text.isEmpty()) mViewModel.search(text);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ---- Bookmark edit ------------------------------------------------------

    private void showBookmarkEditDialog(int slot, Bookmark existing) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_bookmark_edit, null);
        EditText etLabel = dialogView.findViewById(R.id.et_bookmark_label);
        EditText etUrl   = dialogView.findViewById(R.id.et_bookmark_url);

        etLabel.setText(existing.label);
        etUrl.setText(existing.url);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.bookmark_edit_title, slot + 1))
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String label = etLabel.getText().toString().trim();
                    String url   = etUrl.getText().toString().trim();
                    mViewModel.saveBookmark(slot, new Bookmark(label, url));
                })
                .setNeutralButton(R.string.bookmark_clear, (d, w) ->
                        mViewModel.saveBookmark(slot, new Bookmark("", "")))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ---- Helpers ------------------------------------------------------------

    private void onDevicesUpdated(List<TvDeviceDiscovery.TvDevice> devices) {
        mAdapter.setDevices(devices);
        if (devices.isEmpty()) {
            findViewById(R.id.tv_no_devices).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.tv_no_devices).setVisibility(View.GONE);
        }
    }

    private void bindControlButton(int viewId, Runnable action) {
        View btn = findViewById(viewId);
        if (btn != null) {
            btn.setOnClickListener(v -> action.run());
        }
    }
}
