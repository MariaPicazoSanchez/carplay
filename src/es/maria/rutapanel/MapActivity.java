package es.maria.rutapanel;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.TilesOverlay;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class MapActivity extends MediaActivity implements LocationListener {
    static final String EXTRA_DESTINATION = "es.maria.rutapanel.DESTINATION";
    private static final int REQUEST_LOCATION = 1;
    private static final float ROUTE_LOCK_KMH = 11f;
    private static final long FIX_TIMEOUT_MS = 10_000;
    private static final long ROUTE_TIMEOUT_MS = 120_000;
    private static final double ARRIVAL_METERS = 35;
    private static final double OFF_ROUTE_METERS = 60;
    private static final long REPLAN_INTERVAL_MS = 20_000;
    private static final int BACKGROUND = Color.rgb(16, 24, 23);
    private static final int PANEL = Color.rgb(29, 40, 37);
    private static final int FOREGROUND = Color.rgb(242, 246, 239);
    private static final int ROUTE_COLOR = Color.rgb(64, 156, 255);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Runnable routeTimeout = () -> {
        stopRoute();
        toast("La búsqueda tardó demasiado. Comprueba la conexión y vuelve a intentarlo.");
    };
    private final Runnable resumeFollowing = () -> {
        following = true;
        updateCamera();
    };
    private final Runnable expireFix = () -> {
        lastFix = null;
        setDotEnabled(false);
        showGps();
    };
    private final BroadcastReceiver providerChanges = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            refreshLocation();
        }
    };
    private LocationManager locationManager;
    private RoutePlanner planner;
    private MapView mapView;
    private Marker me;
    private Marker target;
    private Polyline line;
    private AlertDialog destinationPicker;
    private Future<?> pendingSearch;
    private int routeRequest;
    private boolean searching;
    private boolean guiding;
    private boolean replanning;
    private RoutePlanner.Route activeRoute;
    private RoutePlanner.Place activePlace;
    private int offRouteFixes;
    private long lastPlanAt;
    private Button center;
    private Location lastFix;
    private TextView gps;
    private TextView routeInfo;
    private EditText destination;
    private Button route;
    private Button permissionAction;
    private TextView nowPlaying;
    private Button musicPrevious;
    private Button musicToggle;
    private Button musicNext;
    private boolean foreground;
    private boolean tracking;
    private boolean following = true;
    private boolean firstCenter = true;
    private boolean moving;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        locationManager = getSystemService(LocationManager.class);
        String userAgent = getPackageName() + "/1.2.0 (Android)";
        planner = new RoutePlanner(userAgent);
        if (state != null) {
            following = state.getBoolean("following", true);
            moving = state.getBoolean("moving");
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);
        root.setFocusableInTouchMode(true);

        LinearLayout search = new LinearLayout(this);
        destination = new EditText(this);
        destination.setId(R.id.map_destination);
        destination.setSingleLine();
        destination.setTextColor(FOREGROUND);
        destination.setHintTextColor(0xffb5c5bb);
        destination.setFilters(new InputFilter[]{new InputFilter.LengthFilter(240)});
        if (state == null) destination.setText(getIntent().getStringExtra(EXTRA_DESTINATION));
        destination.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        destination.setOnEditorActionListener((view, action, event) -> {
            if (action != android.view.inputmethod.EditorInfo.IME_ACTION_GO) return false;
            openRoute();
            return true;
        });
        search.addView(destination, new LinearLayout.LayoutParams(0, -2, 1));
        route = button("Buscar ruta", this::openRoute);
        route.setId(R.id.find_route);
        search.addView(route);
        root.addView(search);

        routeInfo = text("");
        routeInfo.setVisibility(View.GONE);
        root.addView(routeInfo);

        FrameLayout mapArea = new FrameLayout(this);
        root.addView(mapArea, new LinearLayout.LayoutParams(-1, 0, 1));
        mapView = createMap(userAgent, state);
        mapArea.addView(mapView, new FrameLayout.LayoutParams(-1, -1));

        gps = text("");
        root.addView(gps);
        center = button("Centrar mapa", () -> {
            handler.removeCallbacks(resumeFollowing);
            following = true;
            updateCamera();
        });
        mapArea.addView(center, overlay(Gravity.BOTTOM | Gravity.RIGHT, 48));
        permissionAction = button("", this::handleLocationAction);
        root.addView(permissionAction);
        root.addView(buildMusicBar());
        LinearLayout screen = withRail(NAV_MAP, root);
        screen.setBackgroundColor(BACKGROUND);
        screen.setFitsSystemWindows(true);
        setContentView(screen);
        root.requestFocus();
        showGps();
    }

    /** Mapa de OpenStreetMap: no necesita clave ni Servicios de Google Play. */
    private MapView createMap(String userAgent, Bundle state) {
        IConfigurationProvider config = Configuration.getInstance();
        config.load(getApplicationContext(), getSharedPreferences("osmdroid", MODE_PRIVATE));
        config.setUserAgentValue(userAgent);
        File base = new File(getCacheDir(), "osmdroid");
        config.setOsmdroidBasePath(base);
        config.setOsmdroidTileCache(new File(base, "tiles"));

        MapView view = new MapView(this);
        view.setTileSource(TileSourceFactory.MAPNIK);
        view.setMultiTouchControls(true);
        view.setTilesScaledToDpi(true);
        view.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        // Colores invertidos para un mapa oscuro de noche, como el resto del panel.
        view.getOverlayManager().getTilesOverlay().setColorFilter(TilesOverlay.INVERT_COLORS);
        view.setMinZoomLevel(3.0);
        view.setMaxZoomLevel(19.0);
        double lat = state == null ? 40.4168 : state.getDouble("mapLat", 40.4168);
        double lon = state == null ? -3.7038 : state.getDouble("mapLon", -3.7038);
        double zoom = state == null ? 6.0 : state.getDouble("mapZoom", 6.0);
        view.getController().setZoom(zoom);
        view.getController().setCenter(new GeoPoint(lat, lon));
        if (state != null) firstCenter = false;

        CopyrightOverlay copyright = new CopyrightOverlay(this);
        copyright.setTextColor(FOREGROUND);
        view.getOverlays().add(copyright);

        me = new Marker(view);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(ROUTE_COLOR);
        dot.setStroke(dp(3), Color.WHITE);
        dot.setSize(dp(20), dp(20));
        me.setIcon(dot);
        me.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        me.setInfoWindow(null);
        me.setOnMarkerClickListener((marker, map) -> true);
        me.setTitle("Tu posición");

        // Si arrastras el mapa, deja de seguir tu posición hasta pulsar «Centrar mapa».
        view.getOverlays().add(new Overlay() {
            public void draw(Canvas canvas, MapView map, boolean shadow) {
                // Capa invisible: solo observa los gestos.
            }

            @Override public boolean onTouchEvent(MotionEvent event, MapView map) {
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE
                        || event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
                    handler.removeCallbacks(resumeFollowing);
                    following = false;
                    firstCenter = false;
                }
                return false;
            }
        });
        return view;
    }

    @Override protected void onNavigate(int item) {
        if (item == NAV_MAP) {
            // Ya estás en Mapa: vuelve a centrar en tu posición.
            handler.removeCallbacks(resumeFollowing);
            following = true;
            updateCamera();
            return;
        }
        super.onNavigate(item);
    }

    private LinearLayout buildMusicBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        nowPlaying = text("");
        nowPlaying.setId(R.id.map_now_playing);
        nowPlaying.setMaxLines(2);
        nowPlaying.setEllipsize(TextUtils.TruncateAt.END);
        bar.addView(nowPlaying, new LinearLayout.LayoutParams(0, -2, 1));
        musicPrevious = addMusicButton(bar, "|◀", "Canción anterior", 0);
        musicToggle = addMusicButton(bar, "▶", "Reproducir", 1);
        musicNext = addMusicButton(bar, "▶|", "Canción siguiente", 2);
        return bar;
    }

    private Button addMusicButton(LinearLayout bar, String symbol, String description, int action) {
        Button control = button(symbol, () -> transport(action));
        control.setContentDescription(description);
        control.setPadding(0, 0, 0, 0);
        bar.addView(control, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return control;
    }

    @Override protected void renderMedia() {
        MediaMetadata metadata = controller == null ? null : controller.getMetadata();
        PlaybackState state = controller == null ? null : controller.getPlaybackState();
        String track = metadata == null ? null : metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (TextUtils.isEmpty(track) && metadata != null) {
            track = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        }
        String player = controller == null ? "" : playerLabel(controller.getPackageName());
        if (!access()) {
            nowPlaying.setText("Activa los controles de música en Inicio");
        } else if (player.isEmpty()) {
            nowPlaying.setText("Sin música activa");
        } else {
            nowPlaying.setText(TextUtils.isEmpty(track) ? player : track + "\n" + player);
        }
        long actions = state == null ? 0 : state.getActions();
        boolean playing = isPlaying(state);
        boolean canPause = (actions & (PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE)) != 0;
        musicPrevious.setEnabled((actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0);
        musicNext.setEnabled((actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0);
        musicToggle.setEnabled((actions & playButtonActions(state)) != 0);
        musicToggle.setText(!playing ? "▶" : canPause ? "Ⅱ" : "■");
        musicToggle.setContentDescription(!playing ? "Reproducir" : canPause ? "Pausar" : "Detener");
    }

    @Override protected void onResume() {
        super.onResume();
        mapView.onResume();
        foreground = true;
        IntentFilter filter = new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(providerChanges, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(providerChanges, filter);
        }
        refreshLocation();
    }

    @Override protected void onPause() {
        foreground = false;
        unregisterReceiver(providerChanges);
        stopLocation();
        mapView.onPause();
        super.onPause();
    }

    @Override protected void onStop() {
        stopRoute();
        super.onStop();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        mapView.onDetach();
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("following", following);
        state.putBoolean("moving", moving);
        state.putDouble("mapLat", mapView.getMapCenter().getLatitude());
        state.putDouble("mapLon", mapView.getMapCenter().getLongitude());
        state.putDouble("mapZoom", mapView.getZoomLevelDouble());
    }

    private boolean precise() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasLocation() {
        return precise() || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean enabled(String provider) {
        return locationManager.getAllProviders().contains(provider)
                && locationManager.isProviderEnabled(provider);
    }

    private String provider() {
        return precise() ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
    }

    private boolean requestedBefore() {
        return getPreferences(MODE_PRIVATE).getBoolean("locationRequested", false);
    }

    private void handleLocationAction() {
        if (!hasLocation()) {
            if (requestedBefore()) {
                openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Ubicación en el mapa")
                    .setMessage("Ruta usa tu ubicación mientras tienes el mapa abierto para mostrar tu posición, "
                            + "la precisión y la velocidad, y como punto de partida de las rutas.")
                    .setNegativeButton("Ahora no", null)
                    .setPositiveButton("Continuar", (dialog, which) -> {
                        getPreferences(MODE_PRIVATE).edit().putBoolean("locationRequested", true).apply();
                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
                    }).show();
        } else if (!enabled(provider())) {
            openSettings(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        } else if (!precise()) {
            openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    private void openSettings(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException exception) {
            toast("Abre los ajustes de Android desde el menú del teléfono.");
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == REQUEST_LOCATION) refreshLocation();
    }

    private void refreshLocation() {
        if (foreground && hasLocation() && enabled(provider())) {
            if (!tracking) {
                try {
                    locationManager.requestLocationUpdates(provider(), 1000L, 0f, this,
                            Looper.getMainLooper());
                    tracking = true;
                    useLastKnownLocation();
                } catch (SecurityException | IllegalArgumentException exception) {
                    stopLocation();
                }
            }
        } else {
            stopLocation();
        }
        showGps();
    }

    /** Muestra enseguida la última posición conocida si es reciente, sin esperar al primer fix. */
    private void useLastKnownLocation() {
        try {
            Location known = locationManager.getLastKnownLocation(provider());
            if (known != null) onLocationChanged(known);
        } catch (SecurityException ignored) {
            // El permiso puede revocarse entre la comprobación y la llamada.
        }
    }

    private void stopLocation() {
        if (tracking) {
            locationManager.removeUpdates(this);
            tracking = false;
        }
        handler.removeCallbacks(expireFix);
        lastFix = null;
        setDotEnabled(false);
    }

    @Override public void onProviderEnabled(String name) {
        refreshLocation();
    }

    @Override public void onProviderDisabled(String name) {
        refreshLocation();
    }

    @Override public void onStatusChanged(String name, int status, Bundle extras) {
        // Required by LocationListener on Android versions before API 30.
    }

    @Override public void onLocationChanged(Location location) {
        if (!foreground || !tracking || !hasLocation() || !enabled(provider())) return;
        long age = SystemClock.elapsedRealtime() - location.getElapsedRealtimeNanos() / 1_000_000L;
        if (age >= FIX_TIMEOUT_MS) return;
        lastFix = location;
        if (location.hasSpeed()) moving = location.getSpeed() * 3.6f > ROUTE_LOCK_KMH;
        handler.removeCallbacks(expireFix);
        handler.postDelayed(expireFix, FIX_TIMEOUT_MS - Math.max(0L, age));
        showGps();
        setDotEnabled(true);
        updateCamera();
        followRoute(location);
    }

    private void setDotEnabled(boolean enabled) {
        if (mapView == null || me == null) return;
        boolean shown = mapView.getOverlays().contains(me);
        if (enabled && lastFix != null && hasLocation()) {
            me.setPosition(new GeoPoint(lastFix.getLatitude(), lastFix.getLongitude()));
            if (!shown) mapView.getOverlays().add(me);
        } else if (shown) {
            mapView.getOverlays().remove(me);
        }
        mapView.invalidate();
    }

    private void updateCamera() {
        if (mapView == null || lastFix == null || !following) return;
        Location fix = lastFix;
        if (firstCenter) {
            mapView.getController().setZoom(precise() ? 17.0 : 13.0);
            firstCenter = false;
        }
        mapView.getController().setCenter(new GeoPoint(fix.getLatitude(), fix.getLongitude()));
        if (fix.hasBearing() && fix.hasSpeed() && fix.getSpeed() >= 1f) {
            mapView.setMapOrientation(-fix.getBearing());
        }
    }

    private void showGps() {
        String action = null;
        if (!hasLocation()) {
            gps.setText("Ubicación sin permiso");
            action = requestedBefore() ? "Abrir ajustes de la app" : "Permitir ubicación";
        } else if (!enabled(provider())) {
            gps.setText("Ubicación desactivada");
            action = "Abrir ajustes de ubicación";
        } else {
            String accuracy = lastFix != null && lastFix.hasAccuracy()
                    ? String.format(Locale.getDefault(), "±%.0f m", lastFix.getAccuracy()) : "—";
            String speed = lastFix != null && lastFix.hasSpeed()
                    ? String.format(Locale.getDefault(), "%.1f km/h", lastFix.getSpeed() * 3.6f) : "—";
            gps.setText("Precisión " + accuracy + "  ·  Velocidad " + speed);
            if (!precise()) action = "Activar ubicación precisa";
        }
        permissionAction.setVisibility(action == null ? View.GONE : View.VISIBLE);
        permissionAction.setText(action);
        if (moving && destination.hasFocus()) {
            destination.clearFocus();
            getSystemService(android.view.inputmethod.InputMethodManager.class)
                    .hideSoftInputFromWindow(destination.getWindowToken(), 0);
        }
        destination.setEnabled(!moving && !searching && !guiding);
        route.setEnabled(guiding || searching || !moving);
        route.setText(guiding ? "Finalizar ruta" : searching ? "Cancelar búsqueda" : "Buscar ruta");
        destination.setHint(moving ? "Destino bloqueado en marcha" : "Dirección o lugar");
        if (searching) {
            routeInfo.setVisibility(View.VISIBLE);
            routeInfo.setText("Calculando la ruta más rápida en coche…");
        } else if (!guiding) {
            routeInfo.setVisibility(View.GONE);
        }
    }

    private void openRoute() {
        if (searching || guiding) {
            stopRoute();
            return;
        }
        if (moving) return;
        String query = destination.getText().toString().trim();
        if (query.isEmpty()) {
            destination.setError("Escribe un destino");
            return;
        }
        if (!hasLocation() || !enabled(provider())) {
            toast("Activa la ubicación para calcular la ruta desde tu posición.");
            return;
        }
        if (lastFix == null) {
            toast("Espera a que el GPS encuentre tu posición.");
            return;
        }
        final double nearLat = lastFix.getLatitude();
        final double nearLon = lastFix.getLongitude();
        int request = ++routeRequest;
        searching = true;
        showGps();
        handler.postDelayed(routeTimeout, ROUTE_TIMEOUT_MS);
        pendingSearch = worker.submit(() -> {
            List<RoutePlanner.Place> matches = new ArrayList<>();
            if (Geocoder.isPresent()) {
                try {
                    Geocoder geocoder = new Geocoder(getApplicationContext(), Locale.getDefault());
                    List<Address> results = geocoder.getFromLocationName(query, 5);
                    if (results != null) {
                        for (Address address : results) {
                            if (!address.hasLatitude() || !address.hasLongitude()) continue;
                            String label = address.getAddressLine(0);
                            matches.add(new RoutePlanner.Place(TextUtils.isEmpty(label) ? query : label,
                                    address.getLatitude(), address.getLongitude()));
                        }
                    }
                } catch (IOException | IllegalArgumentException ignored) {
                    // Se intenta con el buscador de OpenStreetMap.
                }
            }
            if (matches.isEmpty()) {
                try {
                    matches.addAll(planner.search(query, nearLat, nearLon));
                } catch (IOException | RuntimeException e) {
                    handler.post(() -> routeError(request, "No se pudo buscar la dirección. Comprueba la conexión."));
                    return;
                }
            }
            handler.post(() -> selectDestination(request, query, matches));
        });
    }

    private void selectDestination(int request, String query, List<RoutePlanner.Place> matches) {
        if (!isCurrentRoute(request)) return;
        handler.removeCallbacks(routeTimeout);
        if (matches.isEmpty()) {
            routeError(request, "No se encontró el destino. Añade la ciudad o el código postal.");
            return;
        }
        if (moving) {
            routeError(request, "Busca y confirma tu destino cuando estés detenida.");
            return;
        }
        String[] labels = new String[matches.size()];
        for (int i = 0; i < matches.size(); i++) labels[i] = matches.get(i).label;
        destinationPicker = new AlertDialog.Builder(this)
                .setTitle("Confirma el destino")
                .setItems(labels, (dialog, index) -> {
                    destinationPicker = null;
                    if (!isCurrentRoute(request)) return;
                    calculateRoute(request, matches.get(index));
                })
                .setNegativeButton("Cancelar", (dialog, which) -> stopRoute())
                .setOnCancelListener(dialog -> stopRoute())
                .show();
    }

    private void calculateRoute(int request, RoutePlanner.Place place) {
        if (!isCurrentRoute(request)) return;
        if (lastFix == null) {
            routeError(request, "No hay una posición GPS reciente para calcular la ruta.");
            return;
        }
        Location fix = lastFix;
        handler.postDelayed(routeTimeout, ROUTE_TIMEOUT_MS);
        pendingSearch = worker.submit(() -> {
            try {
                RoutePlanner.Route result = planner.plan(fix.getLatitude(), fix.getLongitude(),
                        place.lat, place.lon);
                handler.post(() -> showRoute(request, place, result));
            } catch (RoutePlanner.NoRouteException e) {
                handler.post(() -> routeError(request, e.getMessage()));
            } catch (IOException | RuntimeException e) {
                handler.post(() -> routeError(request,
                        "No se pudo descargar la red de carreteras. Comprueba la conexión."));
            } catch (InterruptedException e) {
                // Búsqueda cancelada.
            }
        });
    }

    private void showRoute(int request, RoutePlanner.Place place, RoutePlanner.Route result) {
        if (!isCurrentRoute(request)) return;
        handler.removeCallbacks(routeTimeout);
        searching = false;
        guiding = true;
        activePlace = place;
        offRouteFixes = 0;
        lastPlanAt = SystemClock.elapsedRealtime();
        drawRoute(result);

        if (target == null) {
            target = new Marker(mapView);
            target.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        }
        target.setPosition(new GeoPoint(place.lat, place.lon));
        target.setTitle(place.label);
        if (!mapView.getOverlays().contains(target)) mapView.getOverlays().add(target);

        // Vista general del trayecto y, después, seguimiento de la posición.
        following = false;
        mapView.setMapOrientation(0);
        double north = -90, south = 90, east = -180, west = 180;
        for (int i = 0; i < result.lat.length; i++) {
            north = Math.max(north, result.lat[i]);
            south = Math.min(south, result.lat[i]);
            east = Math.max(east, result.lon[i]);
            west = Math.min(west, result.lon[i]);
        }
        BoundingBox box = new BoundingBox(north, east, south, west);
        mapView.zoomToBoundingBox(box.increaseByScale(1.15f), true, dp(32));
        handler.postDelayed(resumeFollowing, 5000);
        showGps();
    }

    private void drawRoute(RoutePlanner.Route result) {
        activeRoute = result;
        List<GeoPoint> points = new ArrayList<>(result.lat.length);
        for (int i = 0; i < result.lat.length; i++) points.add(new GeoPoint(result.lat[i], result.lon[i]));
        if (line == null) {
            line = new Polyline(mapView);
            line.getOutlinePaint().setColor(ROUTE_COLOR);
            line.getOutlinePaint().setStrokeWidth(dp(6));
            line.getOutlinePaint().setStrokeCap(android.graphics.Paint.Cap.ROUND);
            line.setInfoWindow(null);
        }
        line.setPoints(points);
        if (!mapView.getOverlays().contains(line)) {
            // Debajo del punto de posición y del marcador de destino.
            mapView.getOverlays().add(Math.min(1, mapView.getOverlays().size()), line);
        }
        mapView.invalidate();
        updateRouteInfo(result.meters);
    }

    private void updateRouteInfo(double remainingMeters) {
        if (activeRoute == null) return;
        double fraction = activeRoute.meters <= 0 ? 0 : remainingMeters / activeRoute.meters;
        long minutes = Math.max(1, Math.round(activeRoute.seconds * fraction / 60));
        String time = minutes >= 60
                ? String.format(Locale.getDefault(), "%d h %02d min", minutes / 60, minutes % 60)
                : minutes + " min";
        String distance = remainingMeters >= 1000
                ? String.format(Locale.getDefault(), "%.1f km", remainingMeters / 1000)
                : String.format(Locale.getDefault(), "%.0f m", remainingMeters);
        routeInfo.setVisibility(View.VISIBLE);
        routeInfo.setText(time + "  ·  " + distance + "  ·  " + activePlace.label
                + "\nRuta en coche más rápida (" + activeRoute.engine + ")");
    }

    /** Llegada, progreso y recálculo automático si te sales de la ruta. */
    private void followRoute(Location fix) {
        if (!guiding || activeRoute == null || activePlace == null) return;
        double toDestination = RoutePlanner.distance(fix.getLatitude(), fix.getLongitude(),
                activePlace.lat, activePlace.lon);
        if (toDestination < ARRIVAL_METERS) {
            stopRoute();
            toast("Has llegado al destino.");
            return;
        }
        double[] where = activeRoute.locate(fix.getLatitude(), fix.getLongitude());
        updateRouteInfo(where[1]);
        boolean reliable = !fix.hasAccuracy() || fix.getAccuracy() < 40;
        offRouteFixes = where[0] > OFF_ROUTE_METERS && reliable ? offRouteFixes + 1 : 0;
        if (offRouteFixes >= 3 && !replanning
                && SystemClock.elapsedRealtime() - lastPlanAt > REPLAN_INTERVAL_MS) {
            replan(fix);
        }
    }

    private void replan(Location fix) {
        replanning = true;
        lastPlanAt = SystemClock.elapsedRealtime();
        int request = routeRequest;
        RoutePlanner.Place place = activePlace;
        routeInfo.setText("Recalculando la ruta…");
        pendingSearch = worker.submit(() -> {
            RoutePlanner.Route result = null;
            try {
                result = planner.plan(fix.getLatitude(), fix.getLongitude(), place.lat, place.lon);
            } catch (IOException | RoutePlanner.NoRouteException | RuntimeException e) {
                // Se mantiene la ruta anterior y se reintenta más tarde.
            } catch (InterruptedException e) {
                return;
            }
            RoutePlanner.Route finalResult = result;
            handler.post(() -> {
                if (!guiding || request != routeRequest) return;
                replanning = false;
                offRouteFixes = 0;
                if (finalResult != null) drawRoute(finalResult);
                else if (activeRoute != null) updateRouteInfo(activeRoute.meters);
            });
        });
    }

    private boolean isCurrentRoute(int request) {
        return searching && request == routeRequest && !isFinishing() && !isDestroyed();
    }

    private void routeError(int request, String message) {
        if (!isCurrentRoute(request)) return;
        stopRoute();
        toast(message);
    }

    private void stopRoute() {
        boolean hadRoute = searching || guiding;
        routeRequest++;
        searching = false;
        guiding = false;
        replanning = false;
        activeRoute = null;
        activePlace = null;
        handler.removeCallbacks(routeTimeout);
        handler.removeCallbacks(resumeFollowing);
        if (pendingSearch != null) {
            pendingSearch.cancel(true);
            pendingSearch = null;
        }
        if (destinationPicker != null) {
            destinationPicker.dismiss();
            destinationPicker = null;
        }
        if (hadRoute && mapView != null) {
            if (line != null) mapView.getOverlays().remove(line);
            if (target != null) mapView.getOverlays().remove(target);
            mapView.invalidate();
            following = true;
            updateCamera();
        }
        showGps();
    }

    private FrameLayout.LayoutParams overlay(int gravity, int bottom) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2, gravity);
        params.setMargins(dp(12), dp(12), dp(12), dp(bottom));
        return params;
    }

    private TextView text(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(FOREGROUND);
        view.setTextSize(14);
        view.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable background = new GradientDrawable();
        background.setColor(PANEL);
        background.setCornerRadius(dp(12));
        view.setBackground(background);
        return view;
    }

    private Button button(String title, Runnable action) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private int dp(int size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
