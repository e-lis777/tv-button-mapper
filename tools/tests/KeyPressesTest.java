package org.tvbuttons.free;
import java.util.*;

public final class KeyPressesTest {
 static final class Clock implements KeyPresses.Scheduler {
  long now;final Map<Runnable,Long> jobs=new LinkedHashMap<>();
  public void post(Runnable r,long delay){jobs.put(r,now+delay);}public void cancel(Runnable r){jobs.remove(r);}
  void advance(long delta){long end=now+delta;while(true){Runnable next=null;long at=Long.MAX_VALUE;for(Map.Entry<Runnable,Long> item:jobs.entrySet())if(item.getValue()<at){next=item.getKey();at=item.getValue();}if(next==null||at>end)break;now=at;jobs.remove(next);next.run();}now=end;}
 }
 static void equal(Object actual,Object expected){if(!actual.equals(expected))throw new AssertionError(actual+" != "+expected);}
 public static void main(String[] args){
  Clock clock=new Clock();List<String> events=new ArrayList<>();KeyPresses keys=new KeyPresses(clock,events::add);
  keys.down(8,"single","","");keys.up(8);equal(events,List.of("single"));events.clear();
  keys.down(8,"single","double","long");keys.up(8);clock.advance(299);equal(events,List.of());clock.advance(1);equal(events,List.of("single"));events.clear();
  keys.down(8,"single","double","long");keys.up(8);clock.advance(100);keys.down(8,"single","double","long");clock.advance(240);equal(events,List.of());keys.up(8);clock.advance(700);equal(events,List.of("double"));events.clear();
  keys.down(8,"single","double","long");clock.advance(650);keys.up(8);clock.advance(400);equal(events,List.of("long"));events.clear();
  keys.down(8,"single","double","long");keys.up(8);clock.advance(100);keys.down(8,"single","double","long");clock.advance(650);keys.up(8);clock.advance(400);equal(events,List.of("long"));events.clear();
  keys.down(8,"single","double","long");keys.up(8);keys.cancel();clock.advance(1000);equal(events,List.of());
  keys.down(8,"single","double","long");keys.cancel();keys.up(8);clock.advance(1000);equal(events,List.of());
  keys.down(8,"single","","long");keys.down(8,"single","","long");keys.up(8);equal(events,List.of("single"));events.clear();
  keys.down(8,"first","","");keys.down(9,"second","","");keys.up(9);keys.up(8);equal(events,List.of("second","first"));
  if(ActionSupport.limitation("closeapp",30).isEmpty())throw new AssertionError("force stop must require ADB");
  if(ActionSupport.limitation("screenshot",29).isEmpty())throw new AssertionError("screenshot API guard");
  if(!ActionSupport.limitation("screenshot",30).isEmpty())throw new AssertionError("screenshot API 30");
  if(ActionSupport.limitation("power",27).isEmpty())throw new AssertionError("lock API guard");
  System.out.println("PASS: single, delayed single, double, long, second hold, repeats, simultaneous keys, cancellation and API limits");
 }
}
