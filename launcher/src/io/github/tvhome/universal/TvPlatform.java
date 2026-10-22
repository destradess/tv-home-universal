package io.github.tvhome.universal;

import android.content.*;
import android.content.pm.*;
import android.net.*;
import android.net.wifi.WifiInfo;
import android.media.tv.*;
import android.os.Build;
import android.provider.Settings;
import java.util.*;

/** Vendor integration lives here; all callers use capability-based fallbacks. */
final class TvPlatform {
    interface NetworkListener {void available(Network network);void capabilities(Network network,NetworkCapabilities caps);void lost(Network network);}
    static ConnectivityManager.NetworkCallback networkCallback(NetworkListener listener){
        if(Build.VERSION.SDK_INT>=31)return Api31.callback(listener);
        return new ConnectivityManager.NetworkCallback(){
            public void onAvailable(Network n){listener.available(n);}
            public void onCapabilitiesChanged(Network n,NetworkCapabilities c){listener.capabilities(n,c);}
            public void onLost(Network n){listener.lost(n);}
        };
    }
    private static final class Api31 {
        static ConnectivityManager.NetworkCallback callback(NetworkListener listener){
            return new ConnectivityManager.NetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO){
                public void onAvailable(Network n){listener.available(n);}
                public void onCapabilitiesChanged(Network n,NetworkCapabilities c){listener.capabilities(n,c);}
                public void onLost(Network n){listener.lost(n);}
            };
        }
    }
    static String wifiName(NetworkCapabilities caps){
        if(Build.VERSION.SDK_INT<29)return "";
        android.net.TransportInfo info=caps.getTransportInfo();
        return info instanceof WifiInfo?WifiName.clean(((WifiInfo)info).getSSID()):"";
    }
    static void register(Context c,BroadcastReceiver receiver,IntentFilter filter){
        if(Build.VERSION.SDK_INT>=33)c.registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED);
        else c.registerReceiver(receiver,filter);
    }
    static boolean isTcl(){return Build.MANUFACTURER.toLowerCase(Locale.ROOT).contains("tcl");}
    static boolean launch(Context c,Intent... choices){
        for(Intent intent:choices)try{c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return true;}catch(RuntimeException ignored){}
        return false;
    }
    static Intent[] settings(String action){return new Intent[]{new Intent(action),new Intent(Settings.ACTION_SETTINGS)};}
    static ActivityInfo systemHome(Context c){
        PackageManager pm=c.getPackageManager();Intent home=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> list=new ArrayList<>(pm.queryIntentActivities(home,0));ResolveInfo preferred=pm.resolveActivity(home,PackageManager.MATCH_DEFAULT_ONLY);
        if(preferred!=null)list.add(0,preferred);
        for(ResolveInfo result:list){
            ActivityInfo a=result.activityInfo;if(a==null||a.packageName.equals(c.getPackageName())||!a.enabled||!a.exported)continue;
            String name=a.name.toLowerCase(Locale.ROOT);
            if(name.contains("fallback")||name.contains("recovery")||name.contains("setup"))continue;
            if((a.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0)return a;
        }
        return null;
    }
    static List<TvInputInfo> inputs(Context c){
        TvInputManager manager=(TvInputManager)c.getSystemService(Context.TV_INPUT_SERVICE);
        List<TvInputInfo> result=new ArrayList<>();
        try{if(manager!=null)for(TvInputInfo input:manager.getTvInputList())if(input.isPassthroughInput()&&!input.isHidden(c))result.add(input);}catch(RuntimeException ignored){}
        result.sort((a,b)->String.valueOf(a.loadLabel(c)).compareToIgnoreCase(String.valueOf(b.loadLabel(c))));return result;
    }
    static boolean openInput(Context c,String id){return launch(c,new Intent(Intent.ACTION_VIEW,TvContract.buildChannelUriForPassthroughInput(id)))||openInputs(c);}
    static boolean openInputs(Context c){
        if(isTcl()&&launch(c,new Intent().setComponent(new ComponentName("com.tcl.suspension","com.tcl.suspension.ui.main.InputActivity"))))return true;
        return launch(c,new Intent("android.media.tv.action.SETUP_INPUTS"),new Intent(Settings.ACTION_SETTINGS));
    }
    static boolean openLiveTv(Context c){
        if(isTcl()&&launch(c,new Intent().setComponent(new ComponentName("com.tcl.tv","com.tcl.player.TVActivity"))))return true;
        return launch(c,new Intent(Intent.ACTION_VIEW,TvContract.Channels.CONTENT_URI),new Intent("android.intent.action.TV"));
    }
    static boolean openDisplay(Context c){
        if(isTcl()&&launch(c,new Intent().setComponent(new ComponentName("com.android.tv.settings","com.tcl.settings.PictureActivity"))))return true;
        return launch(c,settings(Settings.ACTION_DISPLAY_SETTINGS));
    }
    static boolean openSound(Context c){
        if(isTcl()&&launch(c,new Intent().setComponent(new ComponentName("com.android.tv.settings","com.tcl.settings.SoundActivity"))))return true;
        return launch(c,new Intent("com.android.tv.action.VIEW_SOUND_SETTINGS"),new Intent(Settings.ACTION_SOUND_SETTINGS),new Intent(Settings.ACTION_SETTINGS));
    }
    static boolean openBluetooth(Context c){return launch(c,new Intent("com.android.tv.settings.ACCESSORIES_CONNECTED_DEVICES"),new Intent(Settings.ACTION_BLUETOOTH_SETTINGS),new Intent(Settings.ACTION_SETTINGS));}
}
