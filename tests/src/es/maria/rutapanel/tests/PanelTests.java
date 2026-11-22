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
 private Object call(String name,Class<?>[] types,Object...args)throws Exception{Method m=activity.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,args);}
 private void main(Runnable r){runOnMainSync(r);waitForIdleSync();}
 private void wrapped(RunnableWithException r){main(()->{try{r.run();}catch(Exception e){throw new RuntimeException(e);}});}
 private interface RunnableWithException{void run()throws Exception;}
 private void state(int s){media.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS).setState(s,30000,1f).build());}
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){Bundle out=new Bundle();try{
  getTargetContext().getSharedPreferences("local",0).edit().clear().putBoolean("intro",true).commit();
  activity=startActivitySync(new Intent().setClassName("es.maria.rutapanel","es.maria.rutapanel.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
  wrapped(()->{check((Boolean)field("driving"),"Conducción por defecto");check(!(Boolean)call("parked",new Class<?>[]{}),"Bloqueo manual activo");check(!((Button)field("play")).isEnabled(),"Sin sesión no hay reproducción");check(getTargetContext().getPackageManager().getPackageInfo("es.maria.rutapanel",4096).requestedPermissions==null,"Cero permisos solicitados");call("openPlayer",new Class<?>[]{String.class},"no.instalada");call("openMap",new Class<?>[]{String.class},"Prueba & lugar #1");});
  wrapped(()->{media=new MediaSession(getTargetContext(),"Sesión de prueba");media.setCallback(new MediaSession.Callback(){public void onPlay(){plays++;state(PlaybackState.STATE_PLAYING);}public void onPause(){pauses++;state(PlaybackState.STATE_PAUSED);}public void onSkipToNext(){nexts++;}public void onSkipToPrevious(){prevs++;}});media.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,"Canción de prueba").putString(MediaMetadata.METADATA_KEY_ARTIST,"Artista de prueba").putLong(MediaMetadata.METADATA_KEY_DURATION,180000).build());state(PlaybackState.STATE_PAUSED);media.setActive(true);});
  Thread.sleep(800);wrapped(()->{call("bindSession",new Class<?>[]{});check(((TextView)field("title")).getText().toString().equals("Canción de prueba"),"Metadata real de MediaSession");check(((Button)field("play")).isEnabled(),"Control activado");call("transport",new Class<?>[]{int.class},1);});Thread.sleep(400);
  wrapped(()->{check(plays==1,"PLAY enviado a sesión");call("transport",new Class<?>[]{int.class},1);call("transport",new Class<?>[]{int.class},2);call("transport",new Class<?>[]{int.class},0);});Thread.sleep(400);
  wrapped(()->{check(pauses==1,"PAUSE enviado a sesión");check(nexts==1,"NEXT enviado a sesión");check(prevs==1,"PREVIOUS enviado a sesión");Field d=activity.getClass().getDeclaredField("driving");d.setAccessible(true);d.set(activity,false);check((Boolean)call("parked",new Class<?>[]{}),"Desbloqueo aparcada");d.set(activity,true);media.release();});Thread.sleep(400);
  wrapped(()->{call("bindSession",new Class<?>[]{});check(!((Button)field("play")).isEnabled(),"Cierre de sesión desactiva controles");});
  out.putString("stream","OK: "+checks+" comprobaciones funcionales.\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("stream","ERROR: "+e.toString()+"\n"+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,out);}}
}
