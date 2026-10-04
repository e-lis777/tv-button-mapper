package org.tvbuttons.free;

/** Platform limits of the accessibility backend; ADB actions stay unchanged. */
final class ActionSupport {
 static String limitation(String action,int sdk){
  if(action.equals("closeapp")||action.equals("restartapp"))return "Нужен режим ADB: Android не разрешает закрывать чужие приложения";
  if(action.equals("channelup")||action.equals("channeldown"))return "Нужен режим ADB для отправки команды переключения канала";
  if((action.equals("power")||action.equals("sleep")||action.equals("timer")||action.startsWith("timer:"))&&sdk<28)return "Без ADB перевод в сон доступен с Android 9";
  if(action.equals("screenshot")&&sdk<30)return "Без ADB скриншоты доступны с Android 11";
  return "";
 }
}
