package es.maria.rutapanel;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class MainActivity extends MediaActivity {
    private final int BG=Color.rgb(16,24,23), CARD=Color.rgb(29,40,37), TEXT=Color.rgb(242,246,239), MUTED=Color.rgb(173,190,180), ACCENT=Color.rgb(184,233,147);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean driving=true, resumed=false;
    private TextView clock, title, artist, status, lockLabel, progressLabel;
    private Button play, prev, next, enableMusic;
    private ProgressBar progress;
    private ImageView artwork;
    private final Runnable tick=new Runnable(){public void run(){
        if(!resumed)return;
        clock.setText(new SimpleDateFormat("HH:mm",new Locale("es","ES")).format(new Date()));
        renderProgress(); handler.postDelayed(this,1000);
    }};
    private android.content.SharedPreferences prefs(){return getSharedPreferences("local",MODE_PRIVATE);}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView label(String text,int size,int color){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setFontFeatureSettings("tnum");return v;}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(1);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(0);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private void space(LinearLayout p,int h){View v=new View(this);p.addView(v,lp(1,h));}
    private Button button(String text,Runnable action){Button b=new Button(this);b.setText(text);b.setTextSize(16);b.setAllCaps(false);b.setTextColor(TEXT);b.setMinHeight(dp(56));b.setMinimumHeight(dp(56));b.setBackground(shape(Color.rgb(44,60,53),18));b.setPadding(dp(12),dp(4),dp(12),dp(4));b.setOnClickListener(v->action.run());return b;}
    private void addButton(LinearLayout p,String text,Runnable action){Button b=button(text,action);LinearLayout.LayoutParams l=lp(-1,56);l.topMargin=dp(9);p.addView(b,l);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override public void onCreate(Bundle state){super.onCreate(state);
        driving=state==null || state.getBoolean("driving",true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        build();
        if(!prefs().getBoolean("intro",false)) new AlertDialog.Builder(this).setTitle("Bienvenida a Ruta")
            .setMessage("Configura todo con el vehículo aparcado. El modo conducción es manual: bloquea destinos y ajustes, pero no detecta movimiento.\n\nLa pestaña Mapa muestra tu ubicación si la autorizas. Puedes buscar e iniciar una ruta en esa pantalla. Esta app no sustituye un sistema homologado ni garantiza evitar distracciones.")
            .setPositiveButton("Entendido",(d,w)->prefs().edit().putBoolean("intro",true).apply()).setCancelable(false).show();
    }
    @Override public void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putBoolean("driving",driving);}
    private void build(){
        LinearLayout root=column();root.setPadding(dp(18),dp(10),dp(18),dp(10));root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(dp(18)+i.left,dp(10)+i.top,dp(18)+i.right,dp(10)+i.bottom);}else{v.setPadding(dp(18)+insets.getSystemWindowInsetLeft(),dp(10)+insets.getSystemWindowInsetTop(),dp(18)+insets.getSystemWindowInsetRight(),dp(10)+insets.getSystemWindowInsetBottom());}return insets;});
        boolean wide=getResources().getConfiguration().screenWidthDp>=680;
        LinearLayout header=row();TextView brand=label("RUTA  /  TU PANEL",14,ACCENT);brand.setTypeface(null,1);header.addView(brand,new LinearLayout.LayoutParams(0,dp(40),1));brand.setGravity(Gravity.CENTER_VERTICAL);
        clock=label("--:--",25,TEXT);clock.setTypeface(null,1);if(wide){Button settingsButton=button("Ajustes",this::settings);settingsButton.setTextSize(13);LinearLayout.LayoutParams sl=lp(90,48);sl.rightMargin=dp(18);header.addView(settingsButton,sl);}header.addView(clock);root.addView(header);space(root,8);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout content=wide?row():column();content.setGravity(Gravity.TOP);scroll.addView(content);
        LinearLayout nav=column();nav.setPadding(dp(18),dp(wide?10:18),dp(18),dp(wide?10:18));nav.setBackground(shape(CARD,26));
        LinearLayout media=column();media.setPadding(dp(18),dp(wide?10:18),dp(18),dp(wide?10:18));media.setBackground(shape(CARD,26));
        if(wide){LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,-2,1.12f);a.rightMargin=dp(12);content.addView(nav,a);content.addView(media,new LinearLayout.LayoutParams(0,-2,1));}
        else{content.addView(nav,lp(-1,-2));LinearLayout.LayoutParams m=lp(-1,-2);m.topMargin=dp(12);content.addView(media,m);}
        nav.addView(label("TU PRÓXIMO DESTINO",12,ACCENT));space(nav,wide?6:8);TextView heading=label("Vamos a tu ritmo.",29,TEXT);heading.setTypeface(null,1);if(!wide){nav.addView(heading);space(nav,5);}
        space(nav,8);
        Button maps=button("Mapa",()->showMap(""));
        maps.setId(R.id.open_map);
        maps.setBackground(shape(ACCENT,18));maps.setTextColor(BG);nav.addView(maps,lp(-1,58));
        LinearLayout fav=row();Button home=button("⌂  Casa",()->favorite("casa"));Button work=button("▣  Trabajo",()->favorite("trabajo"));LinearLayout.LayoutParams f=new LinearLayout.LayoutParams(0,dp(56),1);f.topMargin=dp(9);f.rightMargin=dp(8);fav.addView(home,f);LinearLayout.LayoutParams f2=new LinearLayout.LayoutParams(0,dp(56),1);f2.topMargin=dp(9);fav.addView(work,f2);nav.addView(fav);
        media.addView(label("AHORA SUENA",12,ACCENT));space(media,wide?5:12);
        LinearLayout song=row();artwork=new ImageView(this);artwork.setBackground(shape(Color.rgb(52,73,60),16));artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);song.addView(artwork,lp(wide?42:64,wide?42:64));
        LinearLayout info=column();info.setPadding(dp(12),0,0,0);title=label("Tu música, aquí",wide?18:22,TEXT);title.setTypeface(null,1);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);artist=label("Abre tu reproductor para empezar",14,MUTED);artist.setMaxLines(2);info.addView(title);space(info,4);info.addView(artist);song.addView(info,new LinearLayout.LayoutParams(0,-2,1));media.addView(song);space(media,wide?5:12);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));media.addView(progress,lp(-1,wide?4:8));progressLabel=label("— / —",12,MUTED);progressLabel.setGravity(Gravity.RIGHT);media.addView(progressLabel);space(media,wide?4:10);
        LinearLayout controls=row();prev=button("|◀",()->transport(0));prev.setContentDescription("Canción anterior");play=button("▶",()->transport(1));play.setContentDescription("Reproducir o pausar");play.setTextSize(26);play.setBackground(shape(ACCENT,24));play.setTextColor(BG);next=button("▶|",()->transport(2));next.setContentDescription("Canción siguiente");
        for(Button b:new Button[]{prev,play,next}){LinearLayout.LayoutParams c=new LinearLayout.LayoutParams(0,dp(wide?56:64),1);c.setMargins(dp(3),0,dp(3),0);controls.addView(b,c);}media.addView(controls);
        status=label("",12,MUTED);space(media,wide?4:10);media.addView(status);
        enableMusic=button("Activar controles de música",this::musicPermission);
        enableMusic.setId(R.id.enable_music);
        media.addView(enableMusic,lp(-1,56));
        if(!wide)addButton(media,"Ajustes y privacidad",this::settings);
        space(root,10);LinearLayout footer=row();lockLabel=label(driving?"●  Modo conducción · manual":"●  Aparcada · configuración",13,driving?ACCENT:MUTED);footer.addView(lockLabel,new LinearLayout.LayoutParams(0,-2,1));Button mode=button(driving?"He aparcado":"Iniciar trayecto",this::toggleMode);mode.setTextSize(14);footer.addView(mode,lp(156,56));root.addView(footer);setContentView(root);renderMedia();
    }
    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        handler.post(tick);
    }

    @Override protected void onPause() {
        resumed = false;
        handler.removeCallbacks(tick);
        super.onPause();
    }

    @Override protected void renderMedia(){if(title==null)return;MediaMetadata m=controller==null?null:controller.getMetadata();PlaybackState s=controller==null?null:controller.getPlaybackState();
        String t=m==null?null:m.getString(MediaMetadata.METADATA_KEY_TITLE);String a=m==null?null:m.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (m != null && (t == null || t.isEmpty())) t = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        if (m != null && (a == null || a.isEmpty())) a = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        title.setText(t==null||t.isEmpty()?"Sin canción disponible":t);
        artist.setText(a==null?"":a);
        artist.setVisibility(a==null||a.isEmpty()?View.GONE:View.VISIBLE);
        Bitmap art=m==null?null:m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);if(art==null&&m!=null)art=m.getBitmap(MediaMetadata.METADATA_KEY_ART);artwork.setImageBitmap(art);
        boolean playing=isPlaying(s);
        long actions=s==null?0:s.getActions();prev.setEnabled((actions&PlaybackState.ACTION_SKIP_TO_PREVIOUS)!=0);next.setEnabled((actions&PlaybackState.ACTION_SKIP_TO_NEXT)!=0);
        boolean canPause = (actions & (PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE)) != 0;
        play.setText(!playing ? "▶" : canPause ? "Ⅱ" : "■");
        play.setEnabled((actions & playButtonActions(s)) != 0);
        play.setContentDescription(!playing ? "Reproducir" : canPause ? "Pausar" : "Detener");
        for(Button b:new Button[]{prev,play,next})b.setAlpha(b.isEnabled()?1f:0.38f);
        boolean allowed = access();
        enableMusic.setVisibility(allowed ? View.GONE : View.VISIBLE);
        status.setText(controller != null ? playerLabel(controller.getPackageName())
                : allowed ? "Cuando haya una sesión activa, podrás controlarla aquí."
                : "Activa los controles cuando estés aparcada.");
        renderProgress();
    }
    private String duration(long ms){return String.format(Locale.ROOT,"%d:%02d",Math.max(0,ms)/60000,(Math.max(0,ms)/1000)%60);}
    private void renderProgress(){if(progress==null)return;MediaMetadata m=controller==null?null:controller.getMetadata();PlaybackState s=controller==null?null:controller.getPlaybackState();long d=m==null?0:m.getLong(MediaMetadata.METADATA_KEY_DURATION);long pos=s==null?0:s.getPosition();if(s!=null&&s.getState()==PlaybackState.STATE_PLAYING&&s.getLastPositionUpdateTime()>0)pos+=(long)((SystemClock.elapsedRealtime()-s.getLastPositionUpdateTime())*s.getPlaybackSpeed());pos=Math.max(0,d>0?Math.min(pos,d):pos);progress.setProgress(d>0?(int)(1000.0*pos/d):0);progressLabel.setText(d>0?duration(pos)+" / "+duration(d):"— / —");}
    private void toggleMode(){if(driving)new AlertDialog.Builder(this).setTitle("¿Has aparcado?").setMessage("El bloqueo es manual y no detecta si el vehículo se mueve. Desbloquea destinos y ajustes solo cuando estés aparcada.").setNegativeButton("Seguir en conducción",null).setPositiveButton("Sí, estoy aparcada",(d,w)->{driving=false;build();bindSession();}).show();else{driving=true;build();bindSession();}}
    private boolean parked(){if(driving){toast("Disponible al aparcar. Usa «He aparcado» cuando estés detenida en un lugar seguro.");return false;}return true;}
    private void showMap(String destination) {
        Intent intent = new Intent(this, MapActivity.class);
        intent.putExtra(MapActivity.EXTRA_DESTINATION, destination);
        startActivity(intent);
    }

    private void favorite(String key) {
        String value = prefs().getString(key, "");
        if (value.isEmpty()) editFavorite(key);
        else showMap(value);
    }
    private void editFavorite(String key){if(!parked())return;EditText field=new EditText(this);field.setText(prefs().getString(key,""));field.setSingleLine(true);field.setHint("Dirección (se guarda solo en este móvil)");field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(240)});new AlertDialog.Builder(this).setTitle(key.equals("casa")?"Dirección de casa":"Dirección del trabajo").setView(field).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",(d,w)->prefs().edit().putString(key,field.getText().toString().trim()).apply()).show();}
    private void settings(){if(!parked())return;String[] options={"Activar o revocar control de música","Guardar dirección de casa","Guardar dirección del trabajo","Créditos del mapa","Privacidad y seguridad","Borrar direcciones guardadas"};new AlertDialog.Builder(this).setTitle("Ajustes · Ruta").setItems(options,(d,w)->{switch(w){case 0:musicPermission();break;case 1:editFavorite("casa");break;case 2:editFavorite("trabajo");break;case 3:showNavigationNotices();break;case 4:message("Privacidad y seguridad","Ruta 1.2.0 · Android 8 o posterior\n\nEl mapa de OpenStreetMap usa Internet para descargar las teselas. Ruta solicita ubicación para mostrar tu posición, precisión y velocidad mientras tienes Mapa abierto. Las coordenadas no se guardan en Ruta. No lee mensajes ni guarda notificaciones.\n\nEl acceso opcional a notificaciones autoriza al sistema a entregarlas, aunque esta app no procesa su contenido. Solo consulta sesiones de música y mantiene título, artista y carátula en memoria mientras la pantalla está abierta.\n\nLas direcciones se guardan en el espacio privado de la app, sin copia de seguridad. Al calcular una ruta, Ruta descarga la red de carreteras de OpenStreetMap (Overpass) alrededor del origen y del destino y calcula el trayecto en el propio móvil con el algoritmo A*. Para trayectos de más de 60 km, el servidor público OSRM recibe el origen y el destino. El buscador de direcciones recibe el texto que escribes.\n\nEl modo conducción es manual, no detecta movimiento y no bloquea otras apps. El móvil puede calentarse y consumir batería con la pantalla encendida.\n\nNo hay garantía de riesgo cero, certificación para conducción ni auditoría independiente.");break;case 5:new AlertDialog.Builder(this).setTitle("¿Borrar casa y trabajo?").setNegativeButton("Cancelar",null).setPositiveButton("Borrar",(a,n)->{prefs().edit().remove("casa").remove("trabajo").apply();toast("Direcciones borradas.");}).show();break;}}).setNegativeButton("Cerrar",null).show();}
    private void showNavigationNotices() {
        message("Créditos del mapa", "Mapa y datos de carreteras © colaboradores de OpenStreetMap, "
                + "disponibles bajo la licencia Open Database License (ODbL): openstreetmap.org/copyright

"
                + "Teselas del mapa: servidores de la Fundación OpenStreetMap.
"
                + "Red de carreteras para el cálculo de rutas: Overpass API.
"
                + "Búsqueda de direcciones: buscador del sistema o Nominatim (OpenStreetMap).
"
                + "Trayectos largos: servidor público OSRM.

"
                + "Visor de mapas: osmdroid (Apache License 2.0).");
    }

    private void message(String t,String s){new AlertDialog.Builder(this).setTitle(t).setMessage(s).setPositiveButton("Cerrar",null).show();}
    private void musicPermission() {
        if (!parked()) return;
        new AlertDialog.Builder(this)
                .setTitle("Control de música en Ruta")
                .setMessage("Autoriza «Ruta · Control de música» en los ajustes de Android y vuelve al panel. "
                        + "Ruta necesita este permiso para mostrar y controlar la música de otros reproductores. "
                        + "El permiso permite recibir notificaciones personales. Ruta no las lee ni las guarda.")
                .setNegativeButton("Ahora no", null)
                .setPositiveButton("Abrir ajustes de Android", (dialog, which) -> openMusicSettings())
                .show();
    }

    private void openMusicSettings() {
        if (Build.VERSION.SDK_INT >= 30) {
            Intent detail = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener.flattenToString());
            try {
                startActivity(detail);
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // Some devices only expose the list of notification listeners.
            }
        }
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (ActivityNotFoundException | SecurityException e) {
            toast("Busca «Acceso a notificaciones» en los ajustes de Android.");
        }
    }
}
