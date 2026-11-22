package es.maria.rutapanel.tests;
import android.app.*;
import android.content.*;
import android.media.*;
import android.media.session.*;
import android.os.*;
import android.widget.*;
import java.lang.reflect.*;

public final class PanelTests extends Instrumentation {
 private int checks=0,plays=0,pauses=0,nexts=0,prevs=0;
 private MediaSession media;
 private Activity activity;
 private void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);checks++;}
 private Object field(String n)throws Exception{Field f=activity.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(activity);}
 private Object call(String name, Class<?>[] types, Object... args) throws Exception {
     for (Class<?> type = activity.getClass(); type != null; type = type.getSuperclass()) {
         Method method;
         try {
             method = type.getDeclaredMethod(name, types);
         } catch (NoSuchMethodException ignored) {
             continue;
         }
         method.setAccessible(true);
         return method.invoke(activity, args);
     }
     throw new NoSuchMethodException(name);
 }
 private void main(Runnable r){runOnMainSync(r);waitForIdleSync();}
 private void wrapped(RunnableWithException r){main(()->{try{r.run();}catch(Exception e){throw new RuntimeException(e);}});}
 private interface RunnableWithException{void run()throws Exception;}
 private void state(int s){media.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS).setState(s,30000,1f).build());}
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){Bundle out=new Bundle();try{
  getTargetContext().getSharedPreferences("local",0).edit().clear().putBoolean("intro",true).commit();
  activity=startActivitySync(new Intent().setClassName("es.maria.rutapanel","es.maria.rutapanel.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
  wrapped(()->{check((Boolean)field("driving"),"Conducción por defecto");check(!(Boolean)call("parked",new Class<?>[]{}),"Bloqueo manual activo");check(!((Button)field("play")).isEnabled(),"Sin sesión no hay reproducción");java.util.List<String> permissions = java.util.Arrays.asList(getTargetContext().getPackageManager().getPackageInfo("es.maria.rutapanel",4096).requestedPermissions);
   check(permissions.contains(android.Manifest.permission.ACCESS_FINE_LOCATION)
       && permissions.contains(android.Manifest.permission.ACCESS_COARSE_LOCATION)
       && !permissions.contains("android.permission.ACCESS_BACKGROUND_LOCATION"), "Ubicación solo en primer plano");});
  wrapped(()->{media=new MediaSession(getTargetContext(),"Sesión de prueba");media.setCallback(new MediaSession.Callback(){public void onPlay(){plays++;state(PlaybackState.STATE_PLAYING);}public void onPause(){pauses++;state(PlaybackState.STATE_PAUSED);}public void onSkipToNext(){nexts++;}public void onSkipToPrevious(){prevs++;}});media.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,"Canción de prueba").putString(MediaMetadata.METADATA_KEY_ARTIST,"Artista de prueba").putLong(MediaMetadata.METADATA_KEY_DURATION,180000).build());state(PlaybackState.STATE_PAUSED);media.setActive(true);});
  Thread.sleep(800);wrapped(()->{call("bindSession",new Class<?>[]{});check(((TextView)field("title")).getText().toString().equals("Canción de prueba"),"Metadata real de MediaSession");check(((Button)field("play")).isEnabled(),"Control activado");call("transport",new Class<?>[]{int.class},1);});Thread.sleep(400);
  wrapped(()->{check(plays==1,"PLAY enviado a sesión");call("transport",new Class<?>[]{int.class},1);call("transport",new Class<?>[]{int.class},2);call("transport",new Class<?>[]{int.class},0);});Thread.sleep(400);
  wrapped(()->((Button)field("play")).performClick());Thread.sleep(400);
  wrapped(()->{
      check(plays==2,"El botón reanuda la sesión pausada");
      PlaybackState playing = new PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,0,1f).build();
      PlaybackState paused = new PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED,0,0f).build();
      Class<?>[] signature = {String.class, PlaybackState.class, boolean.class};
      int spotify = (Integer)call("sessionRank",signature,"com.spotify.music",playing,false);
      int youtube = (Integer)call("sessionRank",signature,"com.google.android.apps.youtube.music",playing,false);
      int other = (Integer)call("sessionRank",signature,"otro.reproductor",playing,false);
      int pausedSpotify = (Integer)call("sessionRank",signature,"com.spotify.music",paused,false);
      int pausedCurrent = (Integer)call("sessionRank",signature,"otro.reproductor",paused,true);
      check(spotify > youtube && youtube > other && other > pausedSpotify
          && pausedCurrent > pausedSpotify, "Prioridad de sesiones y continuidad al pausar");
  });
  wrapped(()->{check(pauses==1,"PAUSE enviado a sesión");check(nexts==1,"NEXT enviado a sesión");check(prevs==1,"PREVIOUS enviado a sesión");Field d=activity.getClass().getDeclaredField("driving");d.setAccessible(true);d.set(activity,false);check((Boolean)call("parked",new Class<?>[]{}),"Desbloqueo aparcada");d.set(activity,true);media.release();});Thread.sleep(400);
  wrapped(()->{call("bindSession",new Class<?>[]{});check(!((Button)field("play")).isEnabled(),"Cierre de sesión desactiva controles");});
  ActivityMonitor mapMonitor = addMonitor("es.maria.rutapanel.MapActivity", null, false);
  main(() -> {
      int mapButton = activity.getResources().getIdentifier("open_map", "id", "es.maria.rutapanel");
      activity.findViewById(mapButton).performClick();
  });
  Activity map = waitForMonitorWithTimeout(mapMonitor, 5000);
  check(map != null, "Mapa se abre dentro de Ruta desde el panel");
  main(() -> {
      media = new MediaSession(getTargetContext(), "Música junto al mapa");
      media.setMetadata(new MediaMetadata.Builder().putString(
          MediaMetadata.METADATA_KEY_TITLE, "Canción con el mapa abierto").build());
      state(PlaybackState.STATE_PLAYING);
      media.setActive(true);
  });
  Thread.sleep(1500);
  main(() -> {
      int fieldId = map.getResources().getIdentifier("map_destination", "id", "es.maria.rutapanel");
      check(map.findViewById(fieldId) instanceof EditText, "Mapa ofrece un campo de destino");
      int routeId = map.getResources().getIdentifier("find_route", "id", "es.maria.rutapanel");
      check(map.findViewById(routeId) instanceof Button, "Mapa ofrece buscar rutas dentro de Ruta");
      int musicId = map.getResources().getIdentifier("map_now_playing", "id", "es.maria.rutapanel");
      check(((TextView)map.findViewById(musicId)).getText().toString().contains("Canción con el mapa abierto"),
          "Mapa muestra la sesión multimedia activa");
      android.view.View decor = map.getWindow().getDecorView();
      check(hasText(decor, "Permitir ubicación") || hasText(decor, "Abrir ajustes de la app"),
          "Mapa ofrece activar la ubicación cuando falta el permiso");
      media.release();
      map.finish();
  });
  out.putString("stream","OK: "+checks+" comprobaciones funcionales.\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("stream","ERROR: "+e.toString()+"\n"+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,out);}}

 private boolean hasText(android.view.View view, String text) {
     if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return true;
     if (view instanceof android.view.ViewGroup) {
         android.view.ViewGroup group = (android.view.ViewGroup) view;
         for (int i = 0; i < group.getChildCount(); i++) {
             if (hasText(group.getChildAt(i), text)) return true;
         }
     }
     return false;
 }
}
