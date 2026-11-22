package es.maria.rutapanel;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
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
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.navigation.ForceNightMode;
import com.google.android.libraries.navigation.NavigationApi;
import com.google.android.libraries.navigation.NavigationView;
import com.google.android.libraries.navigation.Navigator;
import com.google.android.libraries.navigation.RoutingOptions;
import com.google.android.libraries.navigation.Waypoint;

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
    private static final long ROUTE_TIMEOUT_MS = 45_000;
    private static final int BACKGROUND = Color.rgb(16, 24, 23);
    private static final int PANEL = Color.rgb(29, 40, 37);
    private static final int FOREGROUND = Color.rgb(242, 246, 239);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService geocoding = Executors.newSingleThreadExecutor();
    private final Runnable routeTimeout = () -> {
        stopRoute();
        toast("La búsqueda tardó demasiado. Comprueba la conexión y vuelve a intentarlo.");
    };
    private final Navigator.ArrivalListener arrival = event -> {
        stopRoute();
        toast("Has llegado al destino.");
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
    private NavigationView mapView;
    private Navigator navigator;
    private AlertDialog destinationPicker;
    private Future<?> pendingSearch;
    private int routeRequest;
    private boolean searching;
    private boolean guiding;
    private Button center;
    private GoogleMap map;
    private Location lastFix;
    private TextView gps;
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
        if (state != null) {
            following = state.getBoolean("following", true);
            moving = state.getBoolean("moving");
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);
        root.setFitsSystemWindows(true);
        root.setFocusableInTouchMode(true);
        LinearLayout navigation = new LinearLayout(this);
        navigation.addView(button("Inicio", this::finish));
        TextView title = text("Mapa");
        title.setGravity(Gravity.CENTER_VERTICAL);
        navigation.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
        root.addView(navigation);

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

        FrameLayout mapArea = new FrameLayout(this);
        root.addView(mapArea, new LinearLayout.LayoutParams(-1, 0, 1));
        String problem = mapConfigurationProblem();
        if (problem == null) {
            mapView = new NavigationView(this);
            mapArea.addView(mapView, new FrameLayout.LayoutParams(-1, -1));
            mapView.onCreate(state == null ? null : state.getBundle("map"));
            mapView.setNavigationUiEnabled(false);
            mapView.setForceNightMode(ForceNightMode.FORCE_NIGHT);
            mapView.getMapAsync(this::configureMap);
        } else {
            TextView message = text(problem);
            message.setGravity(Gravity.CENTER);
            mapArea.addView(message, new FrameLayout.LayoutParams(-1, -1));
        }

        gps = text("");
        root.addView(gps);
        if (mapView != null) {
            center = button("Centrar mapa", () -> {
                following = true;
                updateCamera();
            });
            mapArea.addView(center, overlay(Gravity.BOTTOM | Gravity.RIGHT, 48));
        }
        permissionAction = button("", this::handleLocationAction);
        root.addView(permissionAction);
        root.addView(buildMusicBar());
        setContentView(root);
        root.requestFocus();
        showGps();
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

    private String mapConfigurationProblem() {
        try {
            Bundle metadata = getPackageManager().getApplicationInfo(getPackageName(),
                    PackageManager.GET_META_DATA).metaData;
            String key = metadata == null ? "" : metadata.getString("com.google.android.geo.API_KEY", "");
            if (key.trim().isEmpty() || key.equals("RUTA_MAPS_UNCONFIGURED")) {
                return "Este APK se generó sin credenciales de navegación. Instala la compilación con rutas.";
            }
        } catch (PackageManager.NameNotFoundException exception) {
            return "No se pudo leer la configuración del mapa.";
        }
        int availability = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
        return availability == ConnectionResult.SUCCESS ? null
                : "Actualiza o activa los Servicios de Google Play para cargar el mapa.";
    }

    private void configureMap(GoogleMap readyMap) {
        if (isDestroyed()) return;
        map = readyMap;
        map.getUiSettings().setMyLocationButtonEnabled(false);
        map.getUiSettings().setMapToolbarEnabled(false);
        map.setOnCameraMoveStartedListener(reason -> {
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                following = false;
                firstCenter = false;
            }
        });
        setDotEnabled(foreground && lastFix != null);
        updateCamera();
    }

    @Override protected void onStart() {
        super.onStart();
        if (mapView != null) mapView.onStart();
    }

    @Override protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
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
        if (mapView != null) mapView.onPause();
        super.onPause();
    }

    @Override protected void onStop() {
        stopRoute();
        if (mapView != null) mapView.onStop();
        super.onStop();
    }

    @Override protected void onDestroy() {
        geocoding.shutdownNow();
        if (navigator != null) {
            navigator.removeArrivalListener(arrival);
            navigator.cleanup();
        }
        if (mapView != null) mapView.onDestroy();
        super.onDestroy();
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (mapView != null) mapView.onTrimMemory(level);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("following", following);
        state.putBoolean("moving", moving);
        if (mapView != null) {
            Bundle mapState = new Bundle();
            mapView.onSaveInstanceState(mapState);
            state.putBundle("map", mapState);
        }
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
                            + "la precisión y la velocidad. Con ubicación precisa puede seguir tu rumbo.")
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
                } catch (SecurityException | IllegalArgumentException exception) {
                    stopLocation();
                }
            }
        } else {
            stopLocation();
        }
        showGps();
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
    }

    private void setDotEnabled(boolean enabled) {
        if (map == null) return;
        try {
            map.setMyLocationEnabled(enabled && hasLocation());
        } catch (SecurityException ignored) {
            // Android can revoke the permission between the check and the Maps SDK call.
        }
    }

    private void updateCamera() {
        if (map == null || lastFix == null || !following || guiding) return;
        Location fix = lastFix;
        CameraPosition.Builder camera = new CameraPosition.Builder(map.getCameraPosition())
                .target(new LatLng(fix.getLatitude(), fix.getLongitude()));
        if (firstCenter) {
            camera.zoom(precise() ? 17f : 13f);
            firstCenter = false;
        }
        if (fix.hasBearing() && fix.hasSpeed() && fix.getSpeed() >= 1f) {
            camera.bearing(fix.getBearing());
        }
        map.moveCamera(CameraUpdateFactory.newCameraPosition(camera.build()));
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
        route.setEnabled(guiding || searching || (!moving && mapView != null));
        route.setText(guiding ? "Finalizar ruta" : searching ? "Cancelar búsqueda" : "Buscar ruta");
        if (center != null) center.setVisibility(guiding ? View.GONE : View.VISIBLE);
        destination.setHint(moving ? "Destino bloqueado en marcha" : "Dirección o lugar");
    }

    private void openRoute() {
        if (searching || guiding) {
            stopRoute();
            return;
        }
        if (moving || mapView == null) return;
        String query = destination.getText().toString().trim();
        if (query.isEmpty()) {
            destination.setError("Escribe un destino");
            return;
        }
        if (!precise() || !enabled(LocationManager.GPS_PROVIDER)) {
            toast("Activa la ubicación precisa y el GPS para calcular la ruta.");
            return;
        }
        if (lastFix == null) {
            toast("Espera a que el GPS encuentre tu posición.");
            return;
        }
        if (!Geocoder.isPresent()) {
            toast("Este dispositivo no dispone de búsqueda de direcciones.");
            return;
        }
        int request = ++routeRequest;
        searching = true;
        showGps();
        handler.postDelayed(routeTimeout, ROUTE_TIMEOUT_MS);
        pendingSearch = geocoding.submit(() -> {
            try {
                Geocoder geocoder = new Geocoder(getApplicationContext(), Locale.getDefault());
                List<Address> results = geocoder.getFromLocationName(query, 5);
                List<Address> matches = new ArrayList<>();
                if (results != null) {
                    for (Address address : results) {
                        if (address.hasLatitude() && address.hasLongitude()) matches.add(address);
                    }
                }
                handler.post(() -> selectDestination(request, query, matches));
            } catch (IOException | IllegalArgumentException e) {
                handler.post(() -> routeError(request, "No se pudo buscar la dirección. Comprueba la conexión."));
            }
        });
    }

    private void selectDestination(int request, String query, List<Address> matches) {
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
        for (int i = 0; i < matches.size(); i++) {
            String label = matches.get(i).getAddressLine(0);
            labels[i] = TextUtils.isEmpty(label) ? query : label;
        }
        destinationPicker = new AlertDialog.Builder(this)
                .setTitle("Confirma el destino")
                .setItems(labels, (dialog, index) -> {
                    destinationPicker = null;
                    if (!isCurrentRoute(request)) return;
                    Address address = matches.get(index);
                    Waypoint waypoint = Waypoint.builder().setTitle(labels[index])
                            .setLatLng(address.getLatitude(), address.getLongitude()).build();
                    requestGuidance(request, waypoint);
                })
                .setNegativeButton("Cancelar", (dialog, which) -> stopRoute())
                .setOnCancelListener(dialog -> stopRoute())
                .show();
    }

    private void requestGuidance(int request, Waypoint waypoint) {
        if (moving || !precise() || lastFix == null) {
            routeError(request, "Confirma el destino con el coche detenido y el GPS activo.");
            return;
        }
        if (navigator != null) {
            calculateRoute(request, waypoint);
            return;
        }
        NavigationApi.getNavigator(this, new NavigationApi.NavigatorListener() {
            @Override public void onNavigatorReady(Navigator ready) {
                if (!isCurrentRoute(request)) return;
                navigator = ready;
                navigator.setTaskRemovedBehavior(Navigator.TaskRemovedBehavior.QUIT_SERVICE);
                navigator.addArrivalListener(arrival);
                calculateRoute(request, waypoint);
            }

            @Override public void onError(int code) {
                String message;
                switch (code) {
                    case NavigationApi.ErrorCode.NOT_AUTHORIZED:
                        message = "La clave de esta compilación no está autorizada para Navigation SDK.";
                        break;
                    case NavigationApi.ErrorCode.TERMS_NOT_ACCEPTED:
                        message = "Acepta las condiciones de Google para iniciar la navegación.";
                        break;
                    case NavigationApi.ErrorCode.NETWORK_ERROR:
                        message = "No se pudo conectar con Google. Comprueba tu conexión.";
                        break;
                    default:
                        message = "No se pudo iniciar la navegación de Google (código " + code + ").";
                }
                routeError(request, message);
            }
        });
    }

    private void calculateRoute(int request, Waypoint waypoint) {
        if (!isCurrentRoute(request)) return;
        if (moving || !precise() || lastFix == null) {
            routeError(request, "Para iniciar la ruta, detente y espera la ubicación GPS.");
            return;
        }
        handler.postDelayed(routeTimeout, ROUTE_TIMEOUT_MS);
        RoutingOptions options = new RoutingOptions()
                .travelMode(RoutingOptions.TravelMode.DRIVING)
                .routingStrategy(RoutingOptions.RoutingStrategy.DEFAULT_BEST);
        navigator.setDestination(waypoint, options).setOnResultListener(status -> {
            if (!isCurrentRoute(request)) return;
            handler.removeCallbacks(routeTimeout);
            if (status != Navigator.RouteStatus.OK) {
                String message;
                switch (status) {
                    case NETWORK_ERROR:
                        message = "No se pudo calcular la ruta. Comprueba tu conexión.";
                        break;
                    case LOCATION_DISABLED:
                    case LOCATION_UNKNOWN:
                        message = "Google no pudo obtener tu posición. Comprueba el GPS.";
                        break;
                    case QUOTA_CHECK_FAILED:
                        message = "Se ha alcanzado la cuota de navegación del proyecto.";
                        break;
                    default:
                        message = "No se encontró una ruta en coche a ese destino.";
                }
                routeError(request, message);
                return;
            }
            if (!precise() || lastFix == null) {
                routeError(request, "No hay una posición GPS reciente para iniciar la ruta.");
                return;
            }
            searching = false;
            guiding = true;
            mapView.setNavigationUiEnabled(true);
            navigator.startGuidance();
            showGps();
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
        handler.removeCallbacks(routeTimeout);
        if (pendingSearch != null) {
            pendingSearch.cancel(true);
            pendingSearch = null;
        }
        if (destinationPicker != null) {
            destinationPicker.dismiss();
            destinationPicker = null;
        }
        if (navigator != null && hadRoute) {
            navigator.stopGuidance();
            navigator.clearDestinations();
            mapView.setNavigationUiEnabled(false);
            following = true;
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
