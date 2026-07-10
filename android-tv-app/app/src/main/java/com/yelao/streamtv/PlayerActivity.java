package com.yelao.streamtv;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

public class PlayerActivity extends AppCompatActivity {
    static final String EXTRA_CHANNEL_JSON = "channel_json";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideOverlayRunnable = () -> {
        if (playerOverlay != null) playerOverlay.animate().alpha(0f).setDuration(300).start();
    };

    private PlayerView playerView;
    private View playerOverlay;
    private TextView playerTitle;
    private TextView playerStatus;
    private ProgressBar playerProgress;
    private ExoPlayer player;
    private Channel channel;
    private int streamIndex = 0;
    private boolean terminalErrorShown = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        hideSystemUi();

        playerView = findViewById(R.id.playerView);
        playerOverlay = findViewById(R.id.playerOverlay);
        playerTitle = findViewById(R.id.playerTitle);
        playerStatus = findViewById(R.id.playerStatus);
        playerProgress = findViewById(R.id.playerProgress);

        String rawChannel = getIntent().getStringExtra(EXTRA_CHANNEL_JSON);
        try {
            channel = new Gson().fromJson(rawChannel, Channel.class);
        } catch (Exception ignored) {
            channel = null;
        }

        if (channel == null || channel.streams == null || channel.streams.isEmpty()) {
            showTerminalError("Este canal no tiene fuentes disponibles.");
            return;
        }

        playerTitle.setText(channel.name);
        playerView.setControllerAutoShow(true);
        playerView.setControllerShowTimeoutMs(4000);
        playCurrentStream();
    }

    private void playCurrentStream() {
        releasePlayer();
        if (channel == null || streamIndex >= channel.streams.size()) {
            showTerminalError("Ninguna de las fuentes del canal pudo reproducirse.");
            return;
        }

        StreamSource source = channel.streams.get(streamIndex);
        playerProgress.setVisibility(View.VISIBLE);
        playerStatus.setText(streamIndex == 0
                ? "EN VIVO"
                : "Probando fuente " + (streamIndex + 1) + " de " + channel.streams.size());
        showOverlay();

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(30_000)
                .setUserAgent(source.userAgent == null || source.userAgent.isEmpty()
                        ? "Mozilla/5.0 (Linux; Android TV) AppleWebKit/537.36 StreamTVTest/0.1"
                        : source.userAgent);

        Map<String, String> headers = new HashMap<>();
        if (source.referrer != null && !source.referrer.isEmpty()) headers.put("Referer", source.referrer);
        headers.put("Accept", "*/*");
        httpFactory.setDefaultRequestProperties(headers);

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(httpFactory))
                .build();
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_BUFFERING) {
                    playerProgress.setVisibility(View.VISIBLE);
                } else if (playbackState == Player.STATE_READY) {
                    playerProgress.setVisibility(View.GONE);
                    playerStatus.setText("EN VIVO" + qualitySuffix(source));
                    scheduleOverlayHide();
                } else if (playbackState == Player.STATE_ENDED) {
                    tryNextSource();
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                tryNextSource();
            }
        });

        playerView.setPlayer(player);
        player.setMediaItem(MediaItem.fromUri(source.url));
        player.prepare();
        player.play();
    }

    private String qualitySuffix(StreamSource source) {
        return source.quality == null || source.quality.isEmpty() ? "" : " · " + source.quality;
    }

    private void tryNextSource() {
        if (terminalErrorShown) return;
        streamIndex++;
        handler.post(this::playCurrentStream);
    }

    private void showTerminalError(String message) {
        if (terminalErrorShown || isFinishing()) return;
        terminalErrorShown = true;
        playerProgress.setVisibility(View.GONE);
        new AlertDialog.Builder(this)
                .setTitle(channel == null ? "Error de reproducción" : channel.name)
                .setMessage(message)
                .setPositiveButton("Volver", (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void showOverlay() {
        handler.removeCallbacks(hideOverlayRunnable);
        playerOverlay.setAlpha(1f);
    }

    private void scheduleOverlayHide() {
        handler.removeCallbacks(hideOverlayRunnable);
        handler.postDelayed(hideOverlayRunnable, 4000);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            showOverlay();
            scheduleOverlayHide();
        }
        return super.dispatchKeyEvent(event);
    }

    private void hideSystemUi() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private void releasePlayer() {
        if (playerView != null) playerView.setPlayer(null);
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        releasePlayer();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        super.onDestroy();
    }
}
