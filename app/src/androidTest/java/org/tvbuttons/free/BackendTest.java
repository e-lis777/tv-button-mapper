package org.tvbuttons.free;

import android.app.Instrumentation;
import android.content.*;
import android.media.AudioManager;
import android.os.*;
import android.view.KeyEvent;
import android.accessibilityservice.AccessibilityServiceInfo;
import java.util.concurrent.atomic.AtomicReference;

/** Separate, locally signed test APK. No testing endpoints in the application. */
public final class BackendTest extends Instrumentation {
 public void onCreate(Bundle args){super.onCreate(args);start();}
 void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 void main(Runnable task)throws Exception {AtomicReference<Throwable> failure=new AtomicReference<>();runOnMainSync(()->{try{task.run();}catch(Throwable t){failure.set(t);}});if(failure.get()!=null)throw new Exception(failure.get());}
 void report(String text){Bundle b=new Bundle();b.putString("stream",text+"\n");sendStatus(0,b);}
 public void onStart(){int code=0;Bundle result=new Bundle();SharedPreferences prefs=getTargetContext().getSharedPreferences("buttons",0);String oldMode=prefs.getString("control_mode",ControlMode.ADB);boolean oldEnabled=prefs.getBoolean("enabled",true);String key="map."+KeyEvent.KEYCODE_F12+".single";String oldAction=prefs.getString(key,null);AudioManager audio=getTargetContext().getSystemService(AudioManager.class);int volume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);boolean muted=audio.isStreamMute(AudioManager.STREAM_MUSIC);
  try{
   for(int i=0;i<30&&MapperService.instance==null;i++)Thread.sleep(200);
   check(MapperService.instance!=null,"Enable MapperService before running tests");
   main(()->{MainActivity.screenActive=false;prefs.edit().putString("control_mode",ControlMode.ACCESSIBILITY).putBoolean("enabled",true).putString(key,"volup").commit();ControlMode.reconcile(getTargetContext());});Thread.sleep(500);
   check(AdbBridgeService.instance==null,"ADB service must stop in accessibility mode");
   main(()->{MapperService s=MapperService.instance;check((s.getServiceInfo().flags&AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS)!=0,"key filtering must be requested");check(!s.onKeyEvent(new KeyEvent(0,KeyEvent.KEYCODE_F11)),"unmapped key must pass");check(!s.onKeyEvent(new KeyEvent(0,KeyEvent.KEYCODE_POWER)),"physical power must pass");check(s.onKeyEvent(new KeyEvent(0,KeyEvent.KEYCODE_F12)),"mapped down must be consumed");check(s.onKeyEvent(new KeyEvent(1,KeyEvent.KEYCODE_F12)),"mapped up must be consumed");});
   if(!audio.isVolumeFixed()&&volume<audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))check(audio.getStreamVolume(AudioManager.STREAM_MUSIC)>volume,"mapped volume action did not execute");report("PASS accessibility key routing and volume without ADB helper");
   main(()->{prefs.edit().putString("control_mode",ControlMode.ADB).commit();MapperService s=MapperService.instance;check((s.getServiceInfo().flags&AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS)==0,"accessibility must release keys in ADB mode");check(!s.onKeyEvent(new KeyEvent(0,KeyEvent.KEYCODE_F12)),"ADB mode must not run accessibility mapping");prefs.edit().putString("control_mode",ControlMode.ACCESSIBILITY).commit();MainActivity.screenActive=true;check(!s.onKeyEvent(new KeyEvent(0,KeyEvent.KEYCODE_F12)),"configuration screen must not intercept navigation");MainActivity.screenActive=false;});report("PASS mutual exclusion, mode switch and configuration screen");
   main(()->{prefs.edit().putString(key,"restartapp").commit();MapperService s=MapperService.instance;check(!s.onKeyEvent(new KeyEvent(0,KeyEvent.KEYCODE_F12)),"unsupported-only mapping must pass original key");prefs.edit().putString(key,"volup").commit();prefs.edit().putBoolean("enabled",false).commit();check(!s.onKeyEvent(new KeyEvent(0,KeyEvent.KEYCODE_F12)),"disabled service must pass keys");prefs.edit().putBoolean("enabled",true).commit();});report("PASS unsupported assignments and disabled mode");
   main(()->{MapperService.instance.actions.execute("settings");});Thread.sleep(700);
   main(()->{MapperService s=MapperService.instance;check(!s.actions.current.isEmpty(),"foreground window events missing");s.actions.execute("home");s.actions.execute("clock");s.actions.execute("screenshot");});Thread.sleep(1000);
   main(()->{check(MapperService.instance.actions.screens.clock!=null,"accessibility clock overlay missing");MapperService.instance.actions.execute("clock");});report("PASS window events, settings, Home and accessibility overlay");
   result.putString("stream","ALL TESTS PASSED\n");code=-1;
  }catch(Throwable e){result.putString("stream","FAILED: "+e+"\n");}
  finally{try{main(()->{SharedPreferences.Editor editor=prefs.edit().putString("control_mode",oldMode).putBoolean("enabled",oldEnabled);if(oldAction==null)editor.remove(key);else editor.putString(key,oldAction);editor.commit();audio.setStreamVolume(AudioManager.STREAM_MUSIC,volume,0);audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,muted?AudioManager.ADJUST_MUTE:AudioManager.ADJUST_UNMUTE,0);});}catch(Exception ignored){}}
  finish(code,result);
 }
}
