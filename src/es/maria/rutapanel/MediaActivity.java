package es.maria.rutapanel;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.*;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

abstract class MediaActivity extends Activity {
    static final String EXTRA_OPEN_SETTINGS = "es.maria.rutapanel.OPEN_SETTINGS";
    protected static final int NAV_HOME = 0, NAV_MAP = 1, NAV_MUSIC = 2, NAV_PHONE = 3,
            NAV_APPS = 4, NAV_SETTINGS = 5;
    private static final String[] NAV_LABELS = {"Inicio", "Mapa", "Música", "Teléfono", "Apps", "Ajustes"};
    private static final int[] NAV_ICONS = {R.drawable.ic_nav_home, R.drawable.ic_nav_map,
            R.drawable.ic_nav_music, R.drawable.ic_nav_phone, R.drawable.ic_nav_apps,
            R.drawable.ic_nav_settings};
    private static final int RAIL_BACKGROUND = Color.rgb(12, 18, 17);
    private static final int RAIL_SELECTED = Color.rgb(44, 60, 53);
    private static final int RAIL_ACCENT = Color.rgb(184, 233, 147);
    private static final int RAIL_TEXT = Color.rgb(200, 212, 204);
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

    // ---------------------------------------------------------------- Barra lateral

    /** Barra de navegación vertical con iconos: Inicio, Mapa, Música, Teléfono, Apps y Ajustes. */
    protected final View navigationRail(int selected) {
        LinearLayout rail = new LinearLayout(this);
        rail.setId(R.id.nav_rail);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.setPadding(px(6), px(8), px(6), px(8));
        for (int i = 0; i < NAV_LABELS.length; i++) {
            final int item = i;
            boolean active = i == selected;
            LinearLayout entry = new LinearLayout(this);
            entry.setOrientation(LinearLayout.VERTICAL);
            entry.setGravity(Gravity.CENTER);
            entry.setPadding(0, px(6), 0, px(6));
            entry.setMinimumHeight(px(56));
            entry.setClickable(true);
            entry.setFocusable(true);
            entry.setContentDescription(NAV_LABELS[i]);
            entry.setSelected(active);
            if (active) {
                GradientDrawable background = new GradientDrawable();
                background.setColor(RAIL_SELECTED);
                background.setCornerRadius(px(14));
                entry.setBackground(background);
            }
            int color = active ? RAIL_ACCENT : RAIL_TEXT;
            ImageView icon = new ImageView(this);
            icon.setImageResource(NAV_ICONS[i]);
            icon.setImageTintList(ColorStateList.valueOf(color));
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            entry.addView(icon, new LinearLayout.LayoutParams(px(26), px(26)));
            TextView label = new TextView(this);
            label.setText(NAV_LABELS[i]);
            label.setTextSize(11);
            label.setTextColor(color);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-2, -2);
            labelParams.topMargin = px(3);
            entry.addView(label, labelParams);
            entry.setOnClickListener(v -> onNavigate(item));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, 0, 1);
            params.setMargins(0, px(2), 0, px(2));
            rail.addView(entry, params);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(RAIL_BACKGROUND);
        scroll.addView(rail, new ScrollView.LayoutParams(-1, -1));
        return scroll;
    }

    /** Coloca la barra a la izquierda del contenido de la pantalla. */
    protected final LinearLayout withRail(int selected, View content) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(navigationRail(selected), new LinearLayout.LayoutParams(px(76), -1));
        row.addView(content, new LinearLayout.LayoutParams(0, -1, 1));
        return row;
    }

    /** Acción de cada icono. Cada pantalla sobrescribe la suya (Inicio, Mapa o Ajustes). */
    protected void onNavigate(int item) {
        switch (item) {
            case NAV_HOME:
                goHome(false);
                break;
            case NAV_MAP:
                startActivity(new Intent(this, MapActivity.class));
                break;
            case NAV_MUSIC:
                openMusicApp();
                break;
            case NAV_PHONE:
                openPhone();
                break;
            case NAV_APPS:
                showApps();
                break;
            case NAV_SETTINGS:
                goHome(true);
                break;
            default:
                break;
        }
    }

    protected final void goHome(boolean openSettings) {
        Intent home = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (openSettings) home.putExtra(EXTRA_OPEN_SETTINGS, true);
        startActivity(home);
        if (!(this instanceof MainActivity)) finish();
    }

    /** Abre el reproductor que está sonando; si no hay ninguno, Spotify, YouTube Music u otra app de música. */
    protected final void openMusicApp() {
        List<String> candidates = new ArrayList<>();
        if (controller != null) candidates.add(controller.getPackageName());
        candidates.add(SPOTIFY);
        candidates.add(YOUTUBE_MUSIC);
        for (String pkg : candidates) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null && launchSafely(launch)) return;
        }
        Intent music = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC);
        if (!launchSafely(music)) showToast("No hay ninguna app de música instalada.");
    }

    /** Abre el marcador del teléfono. No llama a nadie ni necesita permisos. */
    protected final void openPhone() {
        if (!launchSafely(new Intent(Intent.ACTION_DIAL))) showToast("Este dispositivo no tiene app de teléfono.");
    }

    /** Lista las apps instaladas, por orden alfabético, para abrirlas desde el panel. */
    protected final void showApps() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = new ArrayList<>();
        for (ResolveInfo info : pm.queryIntentActivities(query, 0)) {
            if (!info.activityInfo.packageName.equals(getPackageName())) apps.add(info);
        }
        if (apps.isEmpty()) {
            showToast("No se encontraron apps.");
            return;
        }
        List<String> labels = new ArrayList<>();
        for (ResolveInfo info : apps) labels.add(info.loadLabel(pm).toString());
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < apps.size(); i++) order.add(i);
        Collections.sort(order, (a, b) -> labels.get(a).compareToIgnoreCase(labels.get(b)));
        List<ResolveInfo> sorted = new ArrayList<>();
        List<String> sortedLabels = new ArrayList<>();
        for (int i : order) {
            sorted.add(apps.get(i));
            sortedLabels.add(labels.get(i));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.select_dialog_item, android.R.id.text1, sortedLabels) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                View row = super.getView(position, convertView, parent);
                TextView text = row.findViewById(android.R.id.text1);
                Drawable icon = sorted.get(position).loadIcon(pm);
                icon.setBounds(0, 0, px(36), px(36));
                text.setCompoundDrawables(icon, null, null, null);
                text.setCompoundDrawablePadding(px(14));
                return row;
            }
        };
        new AlertDialog.Builder(this)
                .setTitle("Apps")
                .setAdapter(adapter, (dialog, which) -> {
                    ResolveInfo info = sorted.get(which);
                    Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                            .setComponent(new ComponentName(info.activityInfo.packageName, info.activityInfo.name))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    if (!launchSafely(launch)) showToast("No se pudo abrir la app.");
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private boolean launchSafely(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    private int px(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
