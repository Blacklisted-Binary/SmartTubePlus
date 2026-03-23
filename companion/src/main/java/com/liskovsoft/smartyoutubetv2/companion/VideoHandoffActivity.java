package com.liskovsoft.smartyoutubetv2.companion;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Full-screen activity that plays a YouTube video at a specific timestamp using the
 * YouTube IFrame API, while the TV remains paused.
 *
 * <p>Launched by {@link MainActivity} when the user taps "Take With Me".
 * When the user taps "↩ Return to TV", this activity:
 * <ol>
 *   <li>Queries the WebView's JavaScript for the current playback position</li>
 *   <li>Sends {@code POST /resume positionMs=…} to the TV via {@link CompanionViewModel}</li>
 *   <li>Finishes, returning the user to the main remote screen</li>
 * </ol>
 * </p>
 */
public class VideoHandoffActivity extends AppCompatActivity {

    /** Intent extras */
    public static final String EXTRA_VIDEO_ID   = "handoff_video_id";
    public static final String EXTRA_START_MS   = "handoff_start_ms";
    public static final String EXTRA_TITLE      = "handoff_title";

    private static final String TAG = VideoHandoffActivity.class.getSimpleName();

    private WebView mWebView;
    private TextView mTvTitle;
    private TextView mTvElapsed;
    private View mBtnReturn;
    private View mBtnDiscard;
    private View mProgress;

    private CompanionViewModel mViewModel;
    private String mVideoId;
    private long mStartMs;

    // Elapsed timer
    private final Handler mTimerHandler = new Handler(Looper.getMainLooper());
    private long mHandoffStartWallMs;
    private final Runnable mTimerTick = new Runnable() {
        @Override
        public void run() {
            updateElapsed();
            mTimerHandler.postDelayed(this, 1_000);
        }
    };

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_handoff);

        mViewModel = new ViewModelProvider(this).get(CompanionViewModel.class);

        mVideoId = getIntent().getStringExtra(EXTRA_VIDEO_ID);
        mStartMs = getIntent().getLongExtra(EXTRA_START_MS, 0);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title == null || title.isEmpty()) title = getString(R.string.handoff_title_default);

        mTvTitle   = findViewById(R.id.tv_handoff_title);
        mTvElapsed = findViewById(R.id.tv_handoff_elapsed);
        mBtnReturn = findViewById(R.id.btn_return_to_tv);
        mBtnDiscard = findViewById(R.id.btn_discard_handoff);
        mProgress   = findViewById(R.id.handoff_progress);
        mWebView    = findViewById(R.id.wv_handoff);

        mTvTitle.setText(title);

        // Configure WebView
        WebSettings ws = mWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false); // allow autoplay
        ws.setDomStorageEnabled(true);
        mWebView.setWebChromeClient(new WebChromeClient());
        mWebView.setWebViewClient(new android.webkit.WebViewClient() {
            @Override
            public void onPageFinished(android.webkit.WebView view, String url) {
                mProgress.setVisibility(View.GONE);
            }
        });

        // Load the YouTube IFrame API player
        if (mVideoId != null && !mVideoId.isEmpty()) {
            String html = buildPlayerHtml(mVideoId, mStartMs / 1000L);
            // We load with a youtube.com base URL so the IFrame API CORS checks pass
            mWebView.loadDataWithBaseURL(
                    "https://www.youtube.com",
                    html,
                    "text/html",
                    "UTF-8",
                    null);
        } else {
            Toast.makeText(this, R.string.handoff_nothing_playing, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Return to TV button
        mBtnReturn.setOnClickListener(v -> returnToTv());

        // Discard (close without resuming TV)
        mBtnDiscard.setOnClickListener(v -> confirmDiscard());

        // Start elapsed timer
        mHandoffStartWallMs = System.currentTimeMillis();
        mTimerHandler.post(mTimerTick);
    }

    // ---- Return-to-TV flow --------------------------------------------------

    /**
     * Gets the current playback position from JavaScript, then resumes the TV at that position.
     * Falls back to an elapsed-time estimate if the JS call fails or the player isn't ready.
     */
    private void returnToTv() {
        mBtnReturn.setEnabled(false);
        mBtnDiscard.setEnabled(false);

        mWebView.evaluateJavascript("(function(){ "
                + "if(typeof player !== 'undefined' && player && player.getCurrentTime) {"
                + "  return Math.floor(player.getCurrentTime() * 1000);"
                + "} return -1; })()",
                value -> {
                    long posMs = mStartMs; // fallback
                    try {
                        // evaluateJavascript wraps the return value in quotes if it comes
                        // from some contexts; strip surrounding quotes defensively.
                        String raw = value.trim();
                        if (raw.startsWith("\"") && raw.endsWith("\"")) {
                            raw = raw.substring(1, raw.length() - 1);
                        }
                        long jsMs = Long.parseLong(raw);
                        if (jsMs >= 0) {
                            posMs = jsMs;
                        } else {
                            // JS player not ready – estimate from wall-clock elapsed time
                            long elapsed = System.currentTimeMillis() - mHandoffStartWallMs;
                            posMs = mStartMs + elapsed;
                        }
                    } catch (NumberFormatException ignored) {
                        // use fallback
                    }
                    mViewModel.returnToTv(posMs);
                    finish();
                });
    }

    private void confirmDiscard() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.handoff_discard_title)
                .setMessage(R.string.handoff_discard_message)
                .setPositiveButton(R.string.handoff_discard_yes, (d, w) -> finish())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ---- Elapsed timer ------------------------------------------------------

    private void updateElapsed() {
        long elapsedMs = System.currentTimeMillis() - mHandoffStartWallMs;
        long totalSec  = TimeUnit.MILLISECONDS.toSeconds(elapsedMs);
        long min = totalSec / 60;
        long sec = totalSec % 60;
        mTvElapsed.setText(getString(R.string.handoff_elapsed,
                String.format(Locale.getDefault(), "%d:%02d", min, sec)));
    }

    // ---- Lifecycle ----------------------------------------------------------

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mTimerHandler.removeCallbacks(mTimerTick);
        if (mWebView != null) {
            mWebView.stopLoading();
            mWebView.destroy();
        }
    }

    // ---- YouTube IFrame HTML ------------------------------------------------

    /**
     * Builds a minimal self-contained HTML page that loads the YouTube IFrame API and
     * starts playing {@code videoId} from {@code startSec}.
     *
     * <p>The global JS function {@code getCurrentTimeMs()} returns the current playback
     * position in milliseconds; it is called from {@link #returnToTv()} via
     * {@link WebView#evaluateJavascript}.</p>
     */
    private static String buildPlayerHtml(String videoId, long startSec) {
        return "<!DOCTYPE html>"
            + "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<style>"
            + "*{margin:0;padding:0;box-sizing:border-box}"
            + "body{background:#000;width:100vw;height:100vh;overflow:hidden}"
            + "#player{position:absolute;top:0;left:0;width:100%;height:100%}"
            + "</style></head>"
            + "<body>"
            + "<div id='player'></div>"
            + "<script>"
            + "var tag=document.createElement('script');"
            + "tag.src='https://www.youtube.com/iframe_api';"
            + "document.head.appendChild(tag);"
            + "var player;"
            + "function onYouTubeIframeAPIReady(){"
            + "  player=new YT.Player('player',{"
            + "    height:'100%',width:'100%',"
            + "    videoId:'" + escapeJs(videoId) + "',"
            + "    playerVars:{"
            + "      'start':" + startSec + ","
            + "      'autoplay':1,"
            + "      'playsinline':1,"
            + "      'rel':0,"
            + "      'fs':0"
            + "    }"
            + "  });"
            + "}"
            + "function getCurrentTimeMs(){"
            + "  if(player&&player.getCurrentTime){"
            + "    return Math.floor(player.getCurrentTime()*1000);"
            + "  }"
            + "  return -1;"
            + "}"
            + "</script>"
            + "</body></html>";
    }

    /** Minimal JavaScript string escaping for the videoId (no quotes/backslashes expected, but be safe). */
    private static String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }
}
