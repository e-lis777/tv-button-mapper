package org.tvbuttons.free;
import android.content.*;
public final class BootReceiver extends BroadcastReceiver {
 public void onReceive(Context context,Intent intent){String action=intent.getAction();if((Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))&&context.getSharedPreferences("buttons",0).getBoolean("enabled",true)){context.startForegroundService(new Intent(context,AdbBridgeService.class));}}
}
