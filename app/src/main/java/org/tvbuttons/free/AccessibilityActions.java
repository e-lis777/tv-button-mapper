package org.tvbuttons.free;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.*;
import android.graphics.*;
import android.hardware.HardwareBuffer;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.util.Base64;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import org.json.JSONArray;
import java.nio.charset.StandardCharsets;

/** No shell, root, network requests or input injection API in this backend. */
final class AccessibilityActions {
 final MapperService service;final Handler handler=new Handler(Looper.getMainLooper());final ScreenTools screens;
 String current="",previous="";boolean mouse,closed;int x,y;int macroGeneration;
 AccessibilityActions(MapperService s){service=s;screens=new ScreenTools(s,true,this::execute);}
 void window(String pkg){if(pkg.equals(service.getPackageName())){if(MainActivity.screenActive){mouse=false;screens.action("cursoroff");}return;}
  if(pkg.equals("android")||pkg.equals("com.android.systemui"))return;
  if(!pkg.equals(current)){previous=current;current=pkg;}
 }
 static String platformLimitation(Context context,String a){
  String reason=ActionSupport.limitation(a,Build.VERSION.SDK_INT);if(!reason.isEmpty())return reason;
  if(a.equals("inputs")||a.startsWith("hdmi:"))try{
   android.content.pm.ActivityInfo activity=context.getPackageManager().getActivityInfo(ComponentName.unflattenFromString("com.yandex.tv.live/.input.SourceActivity"),0);
   if(!activity.exported||!activity.enabled||(activity.permission!=null&&context.checkSelfPermission(activity.permission)!=android.content.pm.PackageManager.PERMISSION_GRANTED))return "Для выбора HDMI на этой прошивке нужен режим ADB";
  }catch(android.content.pm.PackageManager.NameNotFoundException e){return "Для выбора HDMI на этой прошивке нужен режим ADB";}
  return "";
 }
 String limitation(String a){
  String reason=platformLimitation(service,a);if(!reason.isEmpty())return reason;
  if(a.startsWith("macro:")){try{JSONArray steps=decodeMacro(a);for(int i=0;i<steps.length();i++){String step=steps.getString(i);if(step.startsWith("macro:"))return "Вложенные макросы не поддерживаются";if(step.startsWith("delay:")){int delay=Integer.parseInt(step.substring(6));if(delay<0||delay>30000)return "Неверная задержка макроса";}else {reason=limitation(step);if(!reason.isEmpty())return reason;}}}catch(Exception e){return "Не удалось прочитать макрос";}}
  return "";
 }
 static JSONArray decodeMacro(String a)throws Exception {JSONArray steps=new JSONArray(new String(Base64.decode(a.substring(6),Base64.NO_WRAP),StandardCharsets.UTF_8));if(steps.length()==0||steps.length()>20)throw new IllegalArgumentException();return steps;}
 void execute(String a){if(closed||ControlMode.adb(service)||!ControlMode.enabled(service)||a.isEmpty())return;
  String reason=limitation(a);if(!reason.isEmpty()){toast(reason);return;}
  try{run(a);}catch(Exception e){toast("Не удалось выполнить «"+ActionCatalog.label(a)+"» на этом ТВ");}
 }
 void run(String a)throws Exception {
  AudioManager audio=service.getSystemService(AudioManager.class);
  switch(a){
   case "none":return;
   case "home":global(AccessibilityService.GLOBAL_ACTION_HOME);return;
   case "back":global(AccessibilityService.GLOBAL_ACTION_BACK);return;
   case "recents":global(AccessibilityService.GLOBAL_ACTION_RECENTS);return;
   case "power":case "sleep":global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);return;
   case "volup":audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_RAISE,AudioManager.FLAG_SHOW_UI);return;
   case "voldown":audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_LOWER,AudioManager.FLAG_SHOW_UI);return;
   case "mute":audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_TOGGLE_MUTE,AudioManager.FLAG_SHOW_UI);return;
   case "play":media(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);return;
   case "next":media(KeyEvent.KEYCODE_MEDIA_NEXT);return;
   case "prev":media(KeyEvent.KEYCODE_MEDIA_PREVIOUS);return;
   case "ff":media(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);return;
   case "rewind":media(KeyEvent.KEYCODE_MEDIA_REWIND);return;
   case "settings":start(new Intent(Settings.ACTION_SETTINGS));return;
   case "wifi":start(new Intent(Settings.ACTION_WIFI_SETTINGS));return;
   case "bluetooth":if(!tryStart(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)))component("com.android.tv.settings/.accessories.AddAccessoryActivity");return;
   case "picture":component("com.yandex.tv.settings/.display.DisplayActivity");return;
   case "sound":component("com.yandex.tv.settings/.sound.SoundActivity");return;
   case "alice":if(!tryStart(new Intent(Intent.ACTION_ASSIST)))component("com.yandex.tv.alice/.app.AliceGatewayActivity");return;
   case "previous":if(previous.isEmpty()){toast("Предыдущее приложение пока не известно");return;}launch(previous);return;
   case "inputs":component("com.yandex.tv.live/.input.SourceActivity");return;
   case "screenshot":screenshot();return;
   case "mouse":mouse=!mouse;if(mouse){x=width()/2;y=height()/2;screens.action("cursor:"+x+","+y);toast("Мышь: стрелки — движение, OK — клик, Назад — выход");}else screens.action("cursoroff");return;
   case "scrollup":case "scrolldown":scroll(a.equals("scrollup"));return;
   case "black":case "clock":case "canceltimer":screens.action(a);return;
  }
  if(a.startsWith("app:")){launch(a.substring(4));return;}
  if(a.startsWith("timer:")){screens.action(a);return;}
  if(a.startsWith("link:")){String url=new String(Base64.decode(a.substring(5),Base64.NO_WRAP),StandardCharsets.UTF_8);if(!url.startsWith("http://")&&!url.startsWith("https://"))throw new IllegalArgumentException();start(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));return;}
  if(a.startsWith("hdmi:")){int port=Integer.parseInt(a.substring(5));if(port<1||port>3)throw new IllegalArgumentException();component("com.yandex.tv.live/.input.SourceActivity");selectInput(port,0);return;}
  if(a.startsWith("macro:")){JSONArray steps=decodeMacro(a);int generation=++macroGeneration;macroStep(steps,0,generation);return;}
  throw new IllegalArgumentException();
 }
 void macroStep(JSONArray steps,int index,int generation){if(closed||generation!=macroGeneration||index>=steps.length()||ControlMode.adb(service)||!ControlMode.enabled(service))return;
  String a=steps.optString(index);try{if(a.startsWith("delay:")){handler.postDelayed(()->macroStep(steps,index+1,generation),Integer.parseInt(a.substring(6)));return;}run(a);handler.post(()->macroStep(steps,index+1,generation));}catch(Exception e){toast("Макрос остановлен: не удалось выполнить «"+ActionCatalog.label(a)+"»");}}
 void global(int a){if(!service.performGlobalAction(a))throw new IllegalStateException();}
 void media(int key){AudioManager audio=service.getSystemService(AudioManager.class);audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,key));audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,key));}
 void start(Intent intent){service.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
 boolean tryStart(Intent intent){try{start(intent);return true;}catch(RuntimeException e){return false;}}
 void component(String component){start(new Intent(Intent.ACTION_MAIN).setComponent(ComponentName.unflattenFromString(component)));}
 void launch(String pkg){Intent intent=service.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);if(intent==null)intent=service.getPackageManager().getLaunchIntentForPackage(pkg);if(intent==null)throw new ActivityNotFoundException();start(intent);}
 int width(){android.util.DisplayMetrics d=new android.util.DisplayMetrics();service.getSystemService(WindowManager.class).getDefaultDisplay().getRealMetrics(d);return d.widthPixels;}
 int height(){android.util.DisplayMetrics d=new android.util.DisplayMetrics();service.getSystemService(WindowManager.class).getDefaultDisplay().getRealMetrics(d);return d.heightPixels;}
 boolean mouseEvent(KeyEvent event){int key=event.getKeyCode();if(screens.black!=null&&key==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP)screens.action("blackoff");return true;}if(!mouse)return false;
  if(key==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP){mouse=false;screens.action("cursoroff");}return true;}
  if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){if(event.getAction()==KeyEvent.ACTION_UP)gesture(x,y,x,y,60);return true;}
  if(key>=KeyEvent.KEYCODE_DPAD_UP&&key<=KeyEvent.KEYCODE_DPAD_RIGHT){if(event.getAction()==KeyEvent.ACTION_DOWN){int delta=event.getRepeatCount()>0?30:18;if(key==19)y-=delta;if(key==20)y+=delta;if(key==21)x-=delta;if(key==22)x+=delta;x=Math.max(0,Math.min(width()-1,x));y=Math.max(0,Math.min(height()-1,y));screens.action("cursor:"+x+","+y);}return true;}
  return false;
 }
 void gesture(int x1,int y1,int x2,int y2,long duration){Path path=new Path();path.moveTo(x1,y1);if(x1!=x2||y1!=y2)path.lineTo(x2,y2);GestureDescription gesture=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,duration)).build();if(!service.dispatchGesture(gesture,new AccessibilityService.GestureResultCallback(){public void onCancelled(GestureDescription g){if(!closed)toast("Это приложение не приняло жест мыши / прокрутки");}},handler))toast("ТВ не поддерживает этот жест");}
 void scroll(boolean up){AccessibilityNodeInfo root=service.getRootInActiveWindow();try{if(root!=null&&scrollNode(root,up?AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD:AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))return;}finally{if(root!=null)root.recycle();}int h=height(),w=width();gesture(w/2,up?h/3:2*h/3,w/2,up?2*h/3:h/3,300);}
 boolean scrollNode(AccessibilityNodeInfo n,int action){if(n.isScrollable()&&n.performAction(action))return true;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo child=n.getChild(i);if(child!=null)try{if(scrollNode(child,action))return true;}finally{child.recycle();}}return false;}
 void selectInput(int port,int attempt){handler.postDelayed(()->{if(closed||ControlMode.adb(service)||!ControlMode.enabled(service))return;AccessibilityNodeInfo root=service.getRootInActiveWindow();boolean found=false;try{if(root!=null&&"com.yandex.tv.live".contentEquals(root.getPackageName())){for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText("HDMI"+port)){try{if(node.getText()!=null&&("HDMI"+port).contentEquals(node.getText())){found=node.performAction(AccessibilityNodeInfo.ACTION_CLICK);if(!found){Rect bounds=new Rect();node.getBoundsInScreen(bounds);gesture(bounds.centerX(),bounds.centerY(),bounds.centerX(),bounds.centerY(),60);found=true;}break;}}finally{node.recycle();}}}}finally{if(root!=null)root.recycle();}if(!found){if(attempt<5)selectInput(port,attempt+1);else toast("Выбери HDMI в открывшемся меню: ТВ не дал переключить его автоматически");}},attempt==0?600:300);}
 void screenshot(){if(Build.VERSION.SDK_INT<30){toast("Нужен Android 11 или режим ADB");return;}service.takeScreenshot(Display.DEFAULT_DISPLAY,service.getMainExecutor(),new AccessibilityService.TakeScreenshotCallback(){public void onFailure(int error){if(!closed)toast("ТВ не разрешил скриншот этого экрана");}public void onSuccess(AccessibilityService.ScreenshotResult result){HardwareBuffer buffer=result.getHardwareBuffer();Bitmap hardware=null,image=null;try{if(closed)return;hardware=Bitmap.wrapHardwareBuffer(buffer,result.getColorSpace());if(hardware==null)throw new IllegalStateException();image=hardware.copy(Bitmap.Config.ARGB_8888,false);saveScreenshot(image);}catch(Exception e){toast("Не удалось сохранить скриншот");}finally{if(image!=null)image.recycle();if(hardware!=null)hardware.recycle();buffer.close();}}});}
 void saveScreenshot(Bitmap image)throws Exception {String name="TV-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmmss",java.util.Locale.ROOT).format(new java.util.Date())+".png";ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,name);values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/TVButtons");values.put(MediaStore.Images.Media.IS_PENDING,1);Uri uri=service.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new java.io.IOException();try(java.io.OutputStream out=service.getContentResolver().openOutputStream(uri)){if(out==null||!image.compress(Bitmap.CompressFormat.PNG,100,out))throw new java.io.IOException();values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);service.getContentResolver().update(uri,values,null,null);}catch(Exception e){service.getContentResolver().delete(uri,null,null);throw e;}toast("Скриншот сохранён в Pictures/TVButtons");}
 void toast(String message){handler.post(()->{if(!closed)Toast.makeText(service,message,Toast.LENGTH_LONG).show();});}
 void close(){closed=true;mouse=false;macroGeneration++;handler.removeCallbacksAndMessages(null);screens.close();}
}
