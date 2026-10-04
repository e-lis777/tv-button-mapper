package org.tvbuttons.free;

import android.content.Context;
import android.content.Intent;

/** One active backend; existing installations keep their ADB mode. */
final class ControlMode {
 static final String ADB="adb", ACCESSIBILITY="accessibility";
 static void initialize(Context c){android.content.SharedPreferences p=c.getSharedPreferences("buttons",0);if(!p.contains("control_mode"))p.edit().putString("control_mode",p.getAll().isEmpty()?ACCESSIBILITY:ADB).apply();}
 static boolean adb(Context c){android.content.SharedPreferences p=c.getSharedPreferences("buttons",0);return ADB.equals(p.getString("control_mode",p.getAll().isEmpty()?ACCESSIBILITY:ADB));}
 static boolean enabled(Context c){return c.getSharedPreferences("buttons",0).getBoolean("enabled",true);}
 static void reconcile(Context c){
  if(adb(c)&&enabled(c)){
   if(AdbBridgeService.instance==null)c.startForegroundService(new Intent(c,AdbBridgeService.class));
  }else c.stopService(new Intent(c,AdbBridgeService.class));
 }
}
