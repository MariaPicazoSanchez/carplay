package es.maria.rutapanel;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.*;
import android.provider.Settings;
import android.view.KeyEvent;
import android.widget.Toast;

abstract class MediaActivity extends Activity {
    private static final String SPOTIFY = "com.spotify.music";
    private static final String YOUTUBE_MUSIC = "com.google.android.apps.youtube.music";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean resumed;
    protected MediaController controller;
    private MediaSessionManager manager;
    protected ComponentName listener;
    private final MediaController.Callback mediaCallback=new MediaController.Callback(){
        @Override public void onMetadataChanged(MediaMetadata m){renderMedia();}
        @Override public void onPlaybackStateChanged(PlaybackState s){renderMedia();}
        @Override public void onSessionDestroyed(){if(resumed)bindSession();}
    };
    private final MediaSessionManager.OnActiveSessionsChangedListener sessions=cs -> {if(resumed)bindSession();};

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            bindSession();
            handler.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        manager = getSystemService(MediaSessionManager.class);
        listener = new ComponentName(this, MediaAccessService.class);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (access()) {
            try {
                manager.addOnActiveSessionsChangedListener(sessions, listener, handler);
            } catch (SecurityException ignored) {
                // The permission can change between the check and registration.
            }
        }
        refresh.run();
    }

    @Override protected void onPause() {
        resumed = false;
        handler.removeCallbacks(refresh);
        manager.removeOnActiveSessionsChangedListener(sessions);
        unbind();
        super.onPause();
    }

    protected abstract void renderMedia();

    protected final boolean access() {
        if (Build.VERSION.SDK_INT >= 27) {
            return getSystemService(NotificationManager.class).isNotificationListenerAccessGranted(listener);
        }
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (flat == null) return false;
        for (String item : flat.split(":")) {
            if (listener.equals(ComponentName.unflattenFromString(item))) return true;
        }
        return false;
    }
    private void unbind(){if(controller!=null){controller.unregisterCallback(mediaCallback);controller=null;}}
    protected final void bindSession() {
        MediaController selected = null;
        if (access()) {
            try {
                int bestRank = -1;
                for (MediaController candidate : manager.getActiveSessions(listener)) {
                    PlaybackState state = candidate.getPlaybackState();
                    boolean current = controller != null
                            && controller.getSessionToken().equals(candidate.getSessionToken());
                    int rank = sessionRank(candidate.getPackageName(), state, current);
                    if (rank > bestRank) {
                        bestRank = rank;
                        selected = candidate;
                    }
                }
            } catch (SecurityException ignored) {
                selected = null;
            }
        }
        if (selected != null && controller != null
                && controller.getSessionToken().equals(selected.getSessionToken())) return;
        unbind();
        if (selected != null) {
            try {
                selected.registerCallback(mediaCallback, handler);
                controller = selected;
            } catch (SecurityException ignored) {
                // Notification access can be revoked while selecting a session.
            }
        }
        renderMedia();
    }

    private static int sessionRank(String packageName, PlaybackState state, boolean current) {
        int preference = SPOTIFY.equals(packageName) ? 2 : YOUTUBE_MUSIC.equals(packageName) ? 1 : 0;
        if (isPlaying(state)) return 8 + preference;
        if (state == null || state.getState() == PlaybackState.STATE_NONE) return preference;
        // Keep the paused player selected so Play resumes the same session.
        if (current) return 7;
        return 3 + preference;
    }

    protected static boolean isPlaying(PlaybackState state) {
        return state != null && state.getState() == PlaybackState.STATE_PLAYING;
    }

    protected static long playButtonActions(PlaybackState state) {
        long direct = isPlaying(state)
                ? PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_STOP : PlaybackState.ACTION_PLAY;
        return direct | PlaybackState.ACTION_PLAY_PAUSE;
    }
    protected final String playerLabel(String pkg){if(SPOTIFY.equals(pkg))return "Spotify";if(YOUTUBE_MUSIC.equals(pkg))return "YouTube Music";try{return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(PackageManager.NameNotFoundException e){return "Otro reproductor";}}
    protected final void transport(int action) {
        if (!access()) {
            bindSession();
            return;
        }
        if (controller == null) return;
        try {
            PlaybackState state = controller.getPlaybackState();
            if (state == null) return;
            long actions = state.getActions();
            MediaController.TransportControls controls = controller.getTransportControls();
            if (action == 0 && (actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0) {
                controls.skipToPrevious();
            } else if (action == 2 && (actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0) {
                controls.skipToNext();
            } else if (action == 1 && (actions & playButtonActions(state)) != 0) {
                long direct = isPlaying(state) ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY;
                if ((actions & direct) != 0) {
                    if (isPlaying(state)) controls.pause();
                    else controls.play();
                } else if ((actions & PlaybackState.ACTION_PLAY_PAUSE) != 0) {
                    sendPlayPauseKey();
                } else {
                    controls.stop();
                }
            }
        } catch (SecurityException e) {
            unbind();
            renderMedia();
            Toast.makeText(this, "Se ha revocado el acceso a la música.", Toast.LENGTH_LONG).show();
        }
    }

    private void sendPlayPauseKey() {
        int key = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
        controller.dispatchMediaButtonEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
        controller.dispatchMediaButtonEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
    }
}
