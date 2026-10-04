package org.tvbuttons.free;

import java.util.HashMap;
import java.util.function.Consumer;

/** Key pairs are consumed together; switching mode cancels pending gestures. */
final class KeyPresses {
 interface Scheduler {void post(Runnable r,long delay);void cancel(Runnable r);}
 static final class Press {String single,dbl,lng;boolean second,longFired;Runnable hold;}
 final Scheduler scheduler;final Consumer<String> execute;
 final HashMap<Integer,Press> down=new HashMap<>();final HashMap<Integer,Runnable> pending=new HashMap<>();
 KeyPresses(Scheduler s,Consumer<String> e){scheduler=s;execute=e;}
 boolean owns(int key){return down.containsKey(key);}
 void down(int key,String single,String dbl,String lng){
  if(down.containsKey(key))return;
  Press p=new Press();p.single=single;p.dbl=dbl;p.lng=lng;
  Runnable prior=pending.remove(key);if(prior!=null){scheduler.cancel(prior);p.second=true;}
  down.put(key,p);p.hold=()->{if(down.get(key)!=p)return;p.longFired=true;execute.accept(p.lng);};
  if(!lng.isEmpty())scheduler.post(p.hold,650);
 }
 void up(int key){
  Press p=down.remove(key);if(p==null)return;scheduler.cancel(p.hold);if(p.longFired)return;
  if(p.dbl.isEmpty()){execute.accept(p.single);return;}
  if(p.second){execute.accept(p.dbl);return;}
  Runnable r=()->{pending.remove(key);execute.accept(p.single);};pending.put(key,r);scheduler.post(r,300);
 }
 void cancel(){for(Press p:down.values())scheduler.cancel(p.hold);for(Runnable r:pending.values())scheduler.cancel(r);down.clear();pending.clear();}
}
