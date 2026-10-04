package org.tvbuttons.free;

import android.accessibilityservice.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;

public class MapperService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {
 public static volatile MapperService instance;
 final Handler handler=new Handler(Looper.getMainLooper());
 SharedPreferences prefs;KeyPresses presses;AccessibilityActions actions;
 public static volatile int lastKey=-1;
 protected void onServiceConnected(){
  instance=this;prefs=getSharedPreferences("buttons",0);actions=new AccessibilityActions(this);
  presses=new KeyPresses(new KeyPresses.Scheduler(){public void post(Runnable r,long delay){handler.postDelayed(r,delay);}public void cancel(Runnable r){handler.removeCallbacks(r);}},this::execute);
  prefs.registerOnSharedPreferenceChangeListener(this);updateFilter();
 }
 void updateFilter(){AccessibilityServiceInfo info=getServiceInfo();if(info==null)return;
  if(!ControlMode.adb(this)&&ControlMode.enabled(this)){info.flags|=AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;info.eventTypes=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;}
  else {info.flags&=~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;info.eventTypes=0;}
  setServiceInfo(info);
 }
 public void onAccessibilityEvent(AccessibilityEvent e){
  if(actions==null||ControlMode.adb(this)||!ControlMode.enabled(this))return;
  if(e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&e.getPackageName()!=null)actions.window(e.getPackageName().toString());
 }
 public void onInterrupt(){cancel();}
 String assignment(int key,String type){return prefs.getString("map."+key+"."+type,"");}
 protected boolean onKeyEvent(KeyEvent e){
  if(prefs==null||ControlMode.adb(this)||!ControlMode.enabled(this))return false;
  lastKey=e.getKeyCode();
  if(MainActivity.screenActive){cancel();return false;}
  int key=e.getKeyCode();if(key==KeyEvent.KEYCODE_POWER)return false;
  if(actions.mouseEvent(e))return true;
  if(e.getAction()==KeyEvent.ACTION_UP){if(!presses.owns(key))return false;presses.up(key);return true;}
  if(e.getAction()!=KeyEvent.ACTION_DOWN)return false;
  if(presses.owns(key))return true;
  String single=assignment(key,"single"),dbl=assignment(key,"double"),lng=assignment(key,"long");
  if(single.isEmpty()&&dbl.isEmpty()&&lng.isEmpty())return false;
  if(e.getRepeatCount()!=0)return false;
  boolean supported=false;for(String a:new String[]{single,dbl,lng})if(!a.isEmpty()&&actions.limitation(a).isEmpty())supported=true;
  if(!supported){actions.toast("Назначение этой кнопки требует режима ADB");return false;}
  presses.down(key,single,dbl,lng);return true;
 }
 void execute(String action){if(actions!=null&&!ControlMode.adb(this)&&ControlMode.enabled(this))actions.execute(action);}
 void cancel(){if(presses!=null)presses.cancel();}
 public void onSharedPreferenceChanged(SharedPreferences p,String key){cancel();if(key.equals("control_mode")||key.equals("enabled")){actions.close();actions=new AccessibilityActions(this);updateFilter();}}
 public void onDestroy(){cancel();if(actions!=null)actions.close();if(prefs!=null)prefs.unregisterOnSharedPreferenceChangeListener(this);if(instance==this)instance=null;super.onDestroy();}
}
