package io.github.tvhome.universal;
public class TimerTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args){
  check(WatchTimerPolicy.minutes(0)==1);check(WatchTimerPolicy.minutes(-5)==1);
  check(WatchTimerPolicy.minutes(241)==240);check(WatchTimerPolicy.minutes(Integer.MAX_VALUE)==240);
  for(int i=1;i<=240;i++)check(WatchTimerPolicy.minutes(i)==i);
  check(WatchTimerPolicy.valid(9,9,60000));check(!WatchTimerPolicy.valid(9,10,60000));
  check(!WatchTimerPolicy.valid(-1,-1,60000));check(!WatchTimerPolicy.valid(9,9,0));
  check(WatchTimerPolicy.seconds(30000,0)==30);check(WatchTimerPolicy.seconds(30000,1)==30);
  check(WatchTimerPolicy.seconds(30000,1000)==29);check(WatchTimerPolicy.seconds(30000,29999)==1);
  check(WatchTimerPolicy.seconds(30000,30000)==0);check(WatchTimerPolicy.seconds(30000,31000)==0);
  long last=30;
  for(long now=0;now<=31000;now++){
   long seconds=WatchTimerPolicy.seconds(30000,now);
   check(seconds>=0&&seconds<=last);check((seconds==0)==(now>=30000));last=seconds;
  }
  System.out.println("PASS: duration bounds, all 240 slider minute values, reboot/stopped state, 30-second countdown and exact expiry boundary.");
 }
}
