package io.github.tvhome.universal;
final class WakeSoundPolicy {
    static boolean valid(int savedBoot,int currentBoot,long sleepAt,long now){
        return savedBoot==currentBoot&&sleepAt>=0&&now>=sleepAt;
    }
}
