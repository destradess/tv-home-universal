package io.github.tvhome.universal;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.wifi.*;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;

public class WifiNetworksActivity extends Activity {
    private WifiManager wifi;
    private LinearLayout choices;
    private TextView status;
    private boolean registered,editing,permissionAsked;
    private AlertDialog passwordDialog;
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable scanTimeout=()->{if(!isFinishing())render(false);};
    private final BroadcastReceiver results=new BroadcastReceiver(){public void onReceive(Context c,Intent intent){handler.removeCallbacks(scanTimeout);if(!isFinishing())render(intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED,false));}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(android.os.Build.VERSION.SDK_INT<30){TvPlatform.launch(this,TvPlatform.settings(Settings.ACTION_WIFI_SETTINGS));finish();return;}
        wifi=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setMinimumHeight(dp(216));
        status=new TextView(this);status.setTextSize(11);status.setPadding(dp(14),dp(6),dp(14),dp(6));panel.addView(status,new LinearLayout.LayoutParams(-1,-2));
        choices=new LinearLayout(this);choices.setOrientation(LinearLayout.VERTICAL);panel.addView(choices,new LinearLayout.LayoutParams(-1,-2));
        Button refresh=button(UiText.get(R.string.ui_101),"refresh",()->{permissionAsked=false;scan();});panel.addView(refresh,new LinearLayout.LayoutParams(-1,dp(36)));
        Button settings=button(UiText.get(R.string.ui_093),"wifi",this::systemSettings);panel.addView(settings,new LinearLayout.LayoutParams(-1,dp(36)));
        SettingsPopup.show(this,UiText.get(R.string.ui_069),panel,refresh);
    }
    @Override protected void onResume(){super.onResume();if(android.os.Build.VERSION.SDK_INT<30)return;
        if(!registered){TvPlatform.register(this,results,new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));registered=true;}
        scan();
    }
    @Override protected void onPause(){handler.removeCallbacks(scanTimeout);if(registered){unregisterReceiver(results);registered=false;}super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(passwordDialog!=null)passwordDialog.dismiss();super.onDestroy();}
    private void scan(){
        if(wifi==null){status.setText(UiText.get(R.string.ui_142));return;}
        if(checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            status.setText(UiText.get(R.string.ui_062));
            if(!permissionAsked){permissionAsked=true;requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},300);}return;
        }
        LocationManager location=(LocationManager)getSystemService(LOCATION_SERVICE);
        if(location!=null&&!location.isLocationEnabled()){status.setText(UiText.get(R.string.ui_021));return;}
        if(!wifi.isWifiEnabled()){status.setText(UiText.get(R.string.ui_141));return;}
        render(false);
        try{
            if(wifi.startScan()){status.setText(UiText.get(R.string.ui_085));handler.removeCallbacks(scanTimeout);handler.postDelayed(scanTimeout,10000);}
            else status.setText(UiText.get(R.string.ui_086));
        }catch(SecurityException error){status.setText(UiText.get(R.string.ui_061));}
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){super.onRequestPermissionsResult(code,permissions,grants);if(code==300){if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)scan();else status.setText(UiText.get(R.string.ui_060));}}
    private void render(boolean fresh){
        if(wifi==null)return;
        String focus="";for(int i=0;i<choices.getChildCount();i++){View v=choices.getChildAt(i);if(v.hasFocus())focus=String.valueOf(v.getTag());}
        Map<String,ScanResult> unique=new HashMap<>();
        try{for(ScanResult result:wifi.getScanResults()){
            if(result.SSID==null||result.SSID.isEmpty())continue;
            String key=result.SSID+"\n"+WifiSecurity.kind(result.capabilities);ScanResult old=unique.get(key);
            if(old==null||result.level>old.level)unique.put(key,result);
        }}catch(SecurityException error){status.setText(UiText.get(R.string.ui_061));return;}
        List<ScanResult> list=new ArrayList<>(unique.values());list.sort((a,b)->Integer.compare(b.level,a.level));
        choices.removeAllViews();String current="";
        try{current=WifiName.clean(wifi.getConnectionInfo().getSSID());}catch(RuntimeException ignored){}
        for(int i=0;i<Math.min(list.size(),30);i++){
            ScanResult result=list.get(i);String key=result.SSID+"\n"+WifiSecurity.kind(result.capabilities);
            Button row=button(result.SSID+(result.SSID.equals(current)?UiText.get(R.string.ui_001):""),"wifi",()->choose(result));row.setTag(key);
            row.setSingleLine(true);row.setEllipsize(android.text.TextUtils.TruncateAt.END);
            GlassControls.apply(row);GlassControls.focusOnlyRows(row);
            choices.addView(row,new LinearLayout.LayoutParams(-1,dp(36)));
            if(!editing&&key.equals(focus))row.requestFocus();
        }
        status.setText(list.isEmpty()?UiText.get(R.string.ui_029):fresh?UiText.get(R.string.ui_098):UiText.get(R.string.ui_046));
    }
    private Button button(String label,String icon,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(13);b.setMinHeight(0);b.setMinimumHeight(0);b.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);b.setPadding(dp(14),0,dp(14),0);MenuIcon.attach(b,icon,19,0xFFFFFFFF);b.setOnClickListener(v->action.run());return b;}
    private void choose(ScanResult result){
        String kind=WifiSecurity.kind(result.capabilities);
        try{WifiInfo info=wifi.getConnectionInfo();if(info!=null&&result.SSID.equals(WifiName.clean(info.getSSID()))&&info.getSupplicantState()==SupplicantState.COMPLETED){toast(UiText.get(R.string.ui_127)+result.SSID);return;}}catch(RuntimeException ignored){}
        if(kind.equals("system")){toast(UiText.get(R.string.ui_048));systemSettings();return;}
        Intent supported=new Intent(Settings.ACTION_WIFI_ADD_NETWORKS);
        if(supported.resolveActivity(getPackageManager())==null){toast(UiText.get(R.string.ui_097)+result.SSID+UiText.get(R.string.ui_007));systemSettings();return;}
        if(kind.equals("open")||kind.equals("owe")){submit(result,"",null);return;}
        EditText password=new EditText(this);password.setSingleLine(true);password.setHint(UiText.get(R.string.ui_073));password.setTextSize(13);
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(63)});
        password.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        editing=true;
        passwordDialog=new AlertDialog.Builder(this).setTitle(result.SSID).setView(password).setPositiveButton(UiText.get(R.string.ui_047),null).setNegativeButton(UiText.get(R.string.ui_026),null).create();
        passwordDialog.setOnDismissListener(d->{password.setText("");editing=false;passwordDialog=null;});
        passwordDialog.show();GlassControls.dialog(passwordDialog);
        passwordDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->submit(result,password.getText().toString(),password));
    }
    private void submit(ScanResult network,String secret,EditText field){
        try{
            WifiNetworkSuggestion.Builder builder=new WifiNetworkSuggestion.Builder().setSsid(network.SSID);
            String kind=WifiSecurity.kind(network.capabilities);
            if(kind.equals("wpa2"))builder.setWpa2Passphrase(secret);
            else if(kind.equals("wpa3"))builder.setWpa3Passphrase(secret);
            else if(kind.equals("owe"))builder.setIsEnhancedOpen(true);
            ArrayList<WifiNetworkSuggestion> configs=new ArrayList<>();configs.add(builder.build());
            Intent intent=new Intent(Settings.ACTION_WIFI_ADD_NETWORKS).putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST,configs);
            startActivityForResult(intent,301);
            if(passwordDialog!=null)passwordDialog.dismiss();
        }catch(IllegalArgumentException error){if(field!=null)field.setError(UiText.get(R.string.ui_095));else toast(UiText.get(R.string.ui_074));}
        catch(RuntimeException error){if(passwordDialog!=null)passwordDialog.dismiss();toast(UiText.get(R.string.ui_042));systemSettings();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request!=301||result!=RESULT_OK)return;
        ArrayList<Integer> codes=data==null?null:data.getIntegerArrayListExtra(Settings.EXTRA_WIFI_NETWORK_RESULT_LIST);
        if(codes==null||codes.isEmpty()){toast(UiText.get(R.string.ui_045));return;}
        if(codes.get(0)==Settings.ADD_WIFI_RESULT_SUCCESS)toast(UiText.get(R.string.ui_070));
        else if(codes.get(0)==Settings.ADD_WIFI_RESULT_ALREADY_EXISTS){toast(UiText.get(R.string.ui_072));systemSettings();}
        else toast(UiText.get(R.string.ui_071));
    }
    private void systemSettings(){try{startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));}catch(RuntimeException error){toast(UiText.get(R.string.ui_094));}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
