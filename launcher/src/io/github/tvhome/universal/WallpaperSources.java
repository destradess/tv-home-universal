package io.github.tvhome.universal;
import android.content.Context;
final class WallpaperSources {
    static final String[] IDS={"tmdb","pixabay","wallhaven","nasa","wikimedia","anilist","jikan","waifu","danbooru","safebooru","unsplash","pexels","alphacoders"};
    static final String[] NAMES={"TMDB","Pixabay","Wallhaven","NASA","Wikimedia","AniList","Jikan","Waifu.im","Danbooru","Safebooru","Unsplash","Pexels","Alpha Coders"};
    static String selected(Context c){String id=MovieWallpaperProvider.prefs(c).getString("source","tmdb");for(String valid:IDS)if(id.equals(valid))return id;return "tmdb";}
    static String name(String id){for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return NAMES[i];return "TMDB";}
    static String unavailable(String id){
        if(id.equals("unsplash")||id.equals("pexels"))return UiText.get(R.string.ui_053);
        if(id.equals("alphacoders"))return UiText.get(R.string.ui_037);
        return "";
    }
    static String key(Context c,String source){return MovieWallpaperProvider.prefs(c).getString(source.equals("tmdb")||source.equals("jikan")?"credential":"key_"+source,"");}
    static boolean acceptsKey(String s){return s.equals("tmdb")||s.equals("jikan")||s.equals("pixabay")||s.equals("wallhaven")||s.equals("waifu");}
    static boolean requiresKey(String s){return s.equals("tmdb")||s.equals("jikan")||s.equals("pixabay");}
    static String keyLabel(String s){return UiText.get(R.string.ui_014);}
    static void select(Context c,String source){MovieWallpaperProvider.prefs(c).edit().putString("source",source).putLong("revision",System.currentTimeMillis()).apply();}
    static void saveKey(Context c,String source,String key){
        if(source.equals("tmdb")||source.equals("jikan")){MovieWallpaperProvider.configure(c,MovieWallpaperProvider.enabled(c),key);}
        else MovieWallpaperProvider.prefs(c).edit().putString("key_"+source,key).putLong("revision",System.currentTimeMillis()).apply();
        c.getSharedPreferences("wallpaper_"+source,0).edit().remove("fetched").remove("retry").apply();
    }
}
