package org.tvbuttons.free;
import android.app.*;import android.os.*;import android.widget.*;import android.graphics.Color;
public class InputsActivity extends Activity {
 public void onCreate(Bundle state){super.onCreate(state);LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(72,40,72,40);box.setBackgroundColor(0xff0d141e);TextView title=new TextView(this);title.setText("Выбери вход HDMI");title.setTextSize(28);title.setTextColor(Color.WHITE);box.addView(title);for(int i=1;i<=3;i++){final int port=i;Button button=new Button(this);button.setText("HDMI "+port);button.setTextColor(Color.WHITE);button.setAllCaps(false);box.addView(button,new LinearLayout.LayoutParams(-1,88));button.setOnClickListener(v->{if(AdbBridgeService.instance!=null)AdbBridgeService.instance.action("hdmi:"+port);finish();});}setContentView(box);}
 protected void onResume(){super.onResume();if(AdbBridgeService.instance!=null)AdbBridgeService.instance.visible(true);}protected void onPause(){if(AdbBridgeService.instance!=null)AdbBridgeService.instance.visible(false);super.onPause();}
}
