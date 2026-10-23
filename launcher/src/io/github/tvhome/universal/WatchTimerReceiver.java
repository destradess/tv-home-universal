package io.github.tvhome.universal;
public class WatchTimerReceiver extends android.content.BroadcastReceiver {
    @Override public void onReceive(android.content.Context context,android.content.Intent intent){WatchTimerController.check();}
}
