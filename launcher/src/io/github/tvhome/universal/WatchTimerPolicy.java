package io.github.tvhome.universal;
final class WatchTimerPolicy {
    static int minutes(int value){return Math.max(1,Math.min(240,value));}
    static boolean valid(int savedBoot,int boot,long deadline){return boot>=0&&savedBoot==boot&&deadline>0;}
    static long seconds(long end,long now){return end<=now?0:1+(end-now-1)/1000;}
}
