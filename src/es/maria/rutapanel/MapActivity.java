package es.maria.rutapanel;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
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
import com.google.android.gms.maps.LocationSource;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapColorScheme;

import java.util.Locale;

public final class MapActivity extends Activity implements LocationListener, LocationSource {
    private static final int REQUEST_LOCATION = 1;
    private static final float ROUTE_LOCK_KMH = 11f;
    private static final long FIX_TIMEOUT_MS = 10_000;
    private static final int BACKGROUND = Color.rgb(16, 24, 23);
    private static final int PANEL = Color.rgb(29, 40, 37);
    private static final int FOREGROUND = Color.rgb(242, 246, 239);

    private final Handler handler = new Handler(Looper.getMainLooper());
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
    private MapView mapView;
    private GoogleMap map;
    private Location lastFix;
    private OnLocationChangedListener dot;
    private TextView gps;
    private EditText destination;
    private Button route;
    private Button permissionAction;
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
        destination.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        destination.setOnEditorActionListener((view, action, event) -> {
            if (action != android.view.inputmethod.EditorInfo.IME_ACTION_GO) return false;
            openRoute();
            return true;
        });
        search.addView(destination, new LinearLayout.LayoutParams(0, -2, 1));
        route = button("Abrir ruta", this::openRoute);
        search.addView(route);
        root.addView(search);

        FrameLayout mapArea = new FrameLayout(this);
        root.addView(mapArea, new LinearLayout.LayoutParams(-1, 0, 1));
        String problem = mapConfigurationProblem();
        if (problem == null) {
            mapView = new MapView(this);
            mapArea.addView(mapView, new FrameLayout.LayoutParams(-1, -1));
            mapView.onCreate(state == null ? null : state.getBundle("map"));
            mapView.getMapAsync(this::configureMap);
        } else {
            TextView message = text(problem);
            message.setGravity(Gravity.CENTER);
            mapArea.addView(message, new FrameLayout.LayoutParams(-1, -1));
        }

        gps = text("");
        mapArea.addView(gps, overlay(Gravity.TOP | Gravity.LEFT, 12));
        if (mapView != null) {
            Button center = button("Centrar mapa", () -> {
                following = true;
                updateCamera();
            });
            mapArea.addView(center, overlay(Gravity.BOTTOM | Gravity.RIGHT, 48));
        }
        permissionAction = button("", this::handleLocationAction);
        root.addView(permissionAction);
        setContentView(root);
        root.requestFocus();
        showGps();
    }

    private String mapConfigurationProblem() {
        try {
            Bundle metadata = getPackageManager().getApplicationInfo(getPackageName(),
                    PackageManager.GET_META_DATA).metaData;
            String key = metadata == null ? "" : metadata.getString("com.google.android.geo.API_KEY", "");
            if (key.trim().isEmpty() || key.equals("RUTA_MAPS_UNCONFIGURED")) {
                return "Esta compilación necesita una clave de Google Maps.";
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
        map.setMapColorScheme(MapColorScheme.DARK);
        map.getUiSettings().setMyLocationButtonEnabled(false);
        map.getUiSettings().setMapToolbarEnabled(false);
        map.setLocationSource(this);
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
        if (mapView != null) mapView.onStop();
        super.onStop();
    }

    @Override protected void onDestroy() {
        if (mapView != null) mapView.onDestroy();
        super.onDestroy();
    }

    @Override public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemory();
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
        if (dot != null) dot.onLocationChanged(location);
        updateCamera();
    }

    @Override public void activate(OnLocationChangedListener listener) {
        dot = listener;
        if (foreground && lastFix != null) dot.onLocationChanged(lastFix);
    }

    @Override public void deactivate() {
        dot = null;
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
        if (map == null || lastFix == null || !following) return;
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
        destination.setEnabled(!moving);
        route.setEnabled(!moving);
        destination.setHint(moving ? "Destino bloqueado en marcha" : "Dirección o lugar");
    }

    private void openRoute() {
        if (moving) return;
        String query = destination.getText().toString().trim();
        if (query.isEmpty()) {
            destination.setError("Escribe un destino");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("google.navigation:q=" + Uri.encode(query) + "&mode=d"));
        intent.setPackage("com.google.android.apps.maps");
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException exception) {
            toast("Abre o instala Google Maps para iniciar la ruta.");
        }
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
