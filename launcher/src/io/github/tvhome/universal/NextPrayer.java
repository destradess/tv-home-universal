package io.github.tvhome.universal;

final class NextPrayer {
    // At the scheduled minute, that prayer is due; highlight the following one.
    static int index(String[] times, int minuteOfDay) {
        if (times == null || times.length != 5) return -1;
        int[] minutes = new int[5];
        for (int i=0;i<5;i++) {
            if (times[i] == null || !times[i].matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) return -1;
            minutes[i]=Integer.parseInt(times[i].substring(0,2))*60+Integer.parseInt(times[i].substring(3));
            if(i>0 && minutes[i]<=minutes[i-1]) return -1;
        }
        for (int i=0;i<5;i++) if(minutes[i]>minuteOfDay) return i;
        return 0;
    }
}
