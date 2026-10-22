package io.github.tvhome.universal;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.LocaleList;
import java.util.*;

final class SystemLanguages {
    static List<Locale> available(){
        Resources system=Resources.getSystem();String[] tags;
        int id=system.getIdentifier("supported_locales","array","android");
        try{tags=id==0?system.getAssets().getLocales():system.getStringArray(id);}
        catch(Resources.NotFoundException ignored){tags=system.getAssets().getLocales();}
        TreeMap<String,Locale> unique=new TreeMap<>();
        for(String tag:tags){
            Locale locale=parse(tag);
            if(locale!=null)unique.put(locale.toLanguageTag(),locale);
        }
        if(unique.isEmpty()){unique.put("en-US",Locale.US);unique.put("id-ID",Locale.forLanguageTag("id-ID"));}
        List<Locale> locales=new ArrayList<>(unique.values());
        java.text.Collator order=java.text.Collator.getInstance(UiText.locale());
        locales.sort((a,b)->order.compare(a.getDisplayName(a),b.getDisplayName(b)));
        return locales;
    }
    static Locale parse(String tag){
        if(tag==null||tag.trim().isEmpty())return null;
        Locale l=Locale.forLanguageTag(tag.replace('_','-'));
        if(l.getLanguage().isEmpty()||l.getLanguage().equals("und")||l.toLanguageTag().equals("en-XA")||l.toLanguageTag().equals("ar-XB"))return null;
        return l;
    }
    static boolean permitted(Context c){return c.checkSelfPermission("android.permission.CHANGE_CONFIGURATION")==PackageManager.PERMISSION_GRANTED;}
    static void apply(Context c,Locale locale)throws Exception{
        if(!permitted(c))throw new SecurityException("CHANGE_CONFIGURATION required");
        Class<?> picker=Class.forName("com.android.internal.app.LocalePicker");
        java.lang.reflect.Method read=picker.getMethod("getLocales");
        LocaleList before=(LocaleList)read.invoke(null);
        if(before!=null&&!before.isEmpty()&&before.get(0).equals(locale))return;
        c.getSharedPreferences("system_language",Context.MODE_PRIVATE).edit()
            .putString("previous_locales",before==null?UiText.locale().toLanguageTag():before.toLanguageTags()).apply();
        picker.getMethod("updateLocales",LocaleList.class).invoke(null,new LocaleList(locale));
        LocaleList after=(LocaleList)read.invoke(null);
        if(after==null||after.isEmpty()||!after.get(0).equals(locale))throw new IllegalStateException("System locale did not change");
    }
}
