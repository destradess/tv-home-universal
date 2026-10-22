package io.github.tvhome.universal;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;

/** Runs only on the wallpaper worker; private, bounded disk cache; HTTPS TMDB hosts only. */
final class MovieWallpaperProvider {
    private static final Object LOCK = new Object();
    private static final String API = "https://api.themoviedb.org/3";
    private static final int MAX_JSON = 1024 * 1024, MAX_IMAGE = 6 * 1024 * 1024;
    static final class Result {
        final Bitmap bitmap; final String title;
        Result(Bitmap bitmap, String title) { this.bitmap = bitmap; this.title = title; }
    }
    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("movie_wallpaper", Context.MODE_PRIVATE);
    }
    static boolean enabled(Context context) { return prefs(context).getBoolean("enabled", false); }
    static void configure(Context context, boolean enabled, String credential) {
        SharedPreferences.Editor edit = prefs(context).edit().putBoolean("enabled", enabled)
                .putLong("revision", System.currentTimeMillis());
        if (credential != null) edit.putString("credential", credential).remove("fetched_at").remove("retry_after");
        edit.apply();
        if (credential != null) context.getSharedPreferences("screensaver_popular", Context.MODE_PRIVATE)
                .edit().remove("fetched_at").remove("retry_after").apply();
    }
    static int categoryMask(Context context){
        int mask=0;for(int i=0;i<WallpaperCategoryRules.IDS.length;i++)
            if(prefs(context).getBoolean("category_"+WallpaperCategoryRules.IDS[i],true))mask|=1<<i;
        return mask;
    }
    static boolean setCategory(Context context,int index,boolean enabled){
        int mask=categoryMask(context), bit=1<<index;
        if(!enabled&&(mask&~bit)==0)return false;
        prefs(context).edit().putBoolean("category_"+WallpaperCategoryRules.IDS[index],enabled)
            .putLong("revision",System.currentTimeMillis()).apply();return true;
    }
    private static void status(Context context, String value) { prefs(context).edit().putString("status", value).apply(); }
    static Result next(Context context) { return next(context, false); }
    static Result random(Context context) { return next(context, true); }
    private static Result next(Context context, boolean randomOrder) {
        synchronized (LOCK) {
            if (!enabled(context)) return null;
            String source=WallpaperSources.selected(context);
            if(!source.equals("tmdb"))return ExternalWallpaperProvider.next(context,source);
            SharedPreferences p = randomOrder ? context.getSharedPreferences("screensaver_popular", Context.MODE_PRIVATE) : prefs(context);
            String language=UiText.locale().toLanguageTag();
            if(!language.equals(p.getString("language",""))) {
                p.edit().putString("language",language).remove("fetched_at").remove("retry_after").apply();
            }
            int mask=categoryMask(context);
            if (p.getInt("source_version",0)!=9 || p.getInt("category_mask",-1)!=mask) {
                p.edit().putInt("source_version",9).putInt("category_mask",mask)
                    .remove("fetched_at").remove("retry_after").apply();
            }
            String credential = prefs(context).getString("credential", "");
            if (credential.isEmpty()) { status(context, UiText.get(R.string.ui_081)); return null; }
            File directory = new File(context.getFilesDir(), randomOrder ? "screensaver-categories-v9" : "movie-wallpapers-categories-v9");
            if (!directory.isDirectory() && !directory.mkdirs()) { status(context, UiText.get(R.string.ui_035)); return null; }
            List<Movie> movies = loadPlaylist(directory);
            movies.removeIf(movie->!WallpaperCategoryRules.enabled(mask,movie.category));
            boolean networkAllowed = true;
            if (movies.isEmpty() || MovieBackdropRules.expired(p.getLong("fetched_at", 0), System.currentTimeMillis())) {
                long retry = p.getLong("retry_after", 0);
                if (System.currentTimeMillis() >= retry) {
                    try {
                        movies = refresh(credential, directory, mask);
                        p.edit().putLong("fetched_at", System.currentTimeMillis()).remove("retry_after").apply();
                    } catch (Exception error) {
                        networkAllowed = false;
                        p.edit().putLong("retry_after", System.currentTimeMillis() + 30 * 60 * 1000L).apply();
                        status(context, error instanceof AuthenticationFailure
                                ? UiText.get(R.string.ui_133)
                                : UiText.get(R.string.ui_129));
                    }
                } else networkAllowed = false;
            }
            if (movies.isEmpty()) return null;
            String indexKey = randomOrder ? "screensaver_index" : "index";
            int previous = p.getInt(indexKey, -1);
            int index = Math.floorMod(previous + 1, movies.size());
            List<String> available=new ArrayList<>();
            for(Movie movie:movies)available.add(movie.category);
            int category=WallpaperCategoryRules.nextCategory(p.getInt("category",-1),mask,available);
            if(category<0)return null;
            List<Integer> candidates=new ArrayList<>();
            for(int i=0;i<movies.size();i++)if(WallpaperCategoryRules.IDS[category].equals(movies.get(i).category)&&(previous<0||previous>=movies.size()||!movies.get(i).path.equals(movies.get(previous).path)))candidates.add(i);
            if(candidates.isEmpty())for(int i=0;i<movies.size();i++)if(WallpaperCategoryRules.IDS[category].equals(movies.get(i).category))candidates.add(i);
            index=candidates.get(new Random().nextInt(candidates.size()));
            Movie selected = movies.get(index);
            File file = imageFile(directory, selected);
            boolean downloaded = false;
            if (!file.isFile() && networkAllowed) {
                try {
                    byte[] image = request(selected.url, null, MAX_IMAGE);
                    write(file, image);
                    downloaded = true;
                } catch (Exception ignored) { status(context, UiText.get(R.string.ui_040)); }
            }
            Bitmap bitmap = decode(file);
            if (bitmap != null) {
                p.edit().putInt(indexKey, index).putInt("category",category).apply();
                if (networkAllowed && !randomOrder) status(context, UiText.get(R.string.ui_134));
                else if (!networkAllowed) status(context, UiText.get(R.string.ui_083));
                if (downloaded) trim(directory, file);
                return new Result(bitmap, selected.title);
            }
            // Offline or bad image: try only existing files, without repeated network waits.
            for (int offset = 1; offset < movies.size(); offset++) {
                int cachedIndex = (index + offset) % movies.size();
                Movie cached = movies.get(cachedIndex);
                bitmap = decode(imageFile(directory, cached));
                if (bitmap != null) {
                    p.edit().putInt(indexKey, cachedIndex).putInt("category",WallpaperCategoryRules.index(cached.category)).apply();
                    return new Result(bitmap, cached.title);
                }
            }
            status(context, UiText.get(R.string.ui_028));
            return null;
        }
    }
    private static final class Movie {
        final String path, url, title, category;
        Movie(String path, String url, String title, String category) { this.path = path; this.url = url; this.title = title; this.category=category; }
    }
    private static List<Movie> refresh(String credential, File directory, int mask) throws Exception {
        JSONObject config = new JSONObject(new String(request(API + "/configuration", credential, MAX_JSON), StandardCharsets.UTF_8));
        JSONObject images = config.getJSONObject("images");
        String base = images.getString("secure_base_url");
        if (!"https://image.tmdb.org/t/p/".equals(base)) throw new IOException("Unexpected image host");
        JSONArray sizes = images.getJSONArray("backdrop_sizes");
        String size = "";
        for (int i = 0; i < sizes.length(); i++) if ("w1280".equals(sizes.getString(i))) size = "w1280";
        if (size.isEmpty()) for (int i = 0; i < sizes.length(); i++) if ("w780".equals(sizes.getString(i))) size = "w780";
        if (size.isEmpty()) throw new IOException("No bounded landscape size");
        JSONArray results=new JSONArray();
        Map<String,JSONArray> trendingPages=new HashMap<>();
        // Each enabled category has its own bounded list; a failed category does not discard the others.
        for(int category=0;category<WallpaperCategoryRules.IDS.length;category++){
            if((mask&(1<<category))==0)continue;
            try{
                JSONArray list=new JSONArray();
                // Anime uses the actual trending list, filtered to Japanese animation.
                for(int page=1;page<=(category==2?3:1);page++){
                    String endpoint=WallpaperCategoryRules.ENDPOINTS[category].replace("page=1","page="+page);
                    JSONArray batch=trendingPages.get(endpoint);
                    if(batch==null){
                        batch=new JSONObject(new String(request(API+endpoint,credential,MAX_JSON),StandardCharsets.UTF_8)).getJSONArray("results");
                        trendingPages.put(endpoint,batch);
                    }
                    for(int j=0;j<batch.length();j++)list.put(batch.getJSONObject(j));
                }
                if(category==5){
                    // Combine anime movies and TV, ranked by rating with at least 100 votes.
                    JSONArray films=new JSONObject(new String(request(API+WallpaperCategoryRules.ENDPOINTS[5].replace("/discover/tv?","/discover/movie?"),credential,MAX_JSON),StandardCharsets.UTF_8)).getJSONArray("results");
                    List<JSONObject> ranked=new ArrayList<>();
                    for(int i=0;i<list.length();i++)ranked.add(new JSONObject(list.getJSONObject(i).toString()).put("media_type","tv"));
                    for(int i=0;i<films.length();i++)ranked.add(new JSONObject(films.getJSONObject(i).toString()).put("media_type","movie"));
                    ranked.sort((a,b)->Double.compare(b.optDouble("vote_average",0),a.optDouble("vote_average",0)));
                    list=new JSONArray();for(JSONObject item:ranked)list.put(item);
                }
                int accepted=0;Set<String> categoryPaths=new HashSet<>();
                for(int i=0;i<list.length()&&accepted<10;i++){
                    JSONObject item=new JSONObject(list.getJSONObject(i).toString());String path=item.optString("backdrop_path","");
                    if(item.optBoolean("adult",false)||!MovieBackdropRules.validPath(path)||!categoryPaths.add(path))continue;
                    if(category==2){
                        String type=item.optString("media_type","");
                        if(!MovieBackdropRules.supportedMedia(type))continue;
                        JSONArray genres=item.optJSONArray("genre_ids"),countries=item.optJSONArray("origin_country");
                        boolean animation=false,japanese="ja".equals(item.optString("original_language",""));
                        if(genres!=null)for(int g=0;g<genres.length();g++)if(genres.optInt(g)==16)animation=true;
                        if(countries!=null)for(int c=0;c<countries.length();c++)if("JP".equals(countries.optString(c)))japanese=true;
                        if(!animation||!japanese)continue;
                    }else item.put("media_type",category==5?item.optString("media_type","tv"):category==0||category==3?"movie":"tv");
                    item.put("wallpaper_category",WallpaperCategoryRules.IDS[category]);results.put(item);accepted++;
                }
            }catch(AuthenticationFailure error){throw error;}
            catch(Exception error){android.util.Log.i("TVHomeScreensaver","Category unavailable: "+WallpaperCategoryRules.IDS[category]);}
        }
        JSONArray cache = new JSONArray();
        List<Movie> movies = new ArrayList<>();
        Set<String> paths = new HashSet<>();
        for (int i = 0; i < results.length() && movies.size() < 60; i++) {
            JSONObject movie = results.getJSONObject(i);
            String mediaType = movie.optString("media_type", "");
            if (!MovieBackdropRules.supportedMedia(mediaType)) continue;
            String path = movie.optString("backdrop_path", "");
            if (movie.optBoolean("adult", false) || !MovieBackdropRules.validPath(path) || !paths.add(movie.optString("wallpaper_category",mediaType)+":"+path)) continue;
            String title = MovieBackdropRules.title(mediaType, movie.optString("title", ""),
                    movie.optString("name", ""), movie.optString("original_title", ""), movie.optString("original_name", ""));
            Movie entry = new Movie(path, base + size + path, title, movie.optString("wallpaper_category",mediaType));
            movies.add(entry);
            cache.put(new JSONObject().put("path", path).put("url", entry.url).put("title", title).put("category",entry.category));
        }
        if (movies.isEmpty()) throw new IOException("No usable backdrops");
        write(new File(directory, "playlist-all.json"), cache.toString().getBytes(StandardCharsets.UTF_8));
        return movies;
    }
    static JSONArray popularAnime(String credential) {
        JSONArray matched=new JSONArray();
        try {
            JSONObject response=new JSONObject(new String(request("https://api.jikan.moe/v4/top/anime?filter=bypopularity&limit=10",null,MAX_JSON),StandardCharsets.UTF_8));
            JSONArray list=response.getJSONArray("data");
            long deadline=android.os.SystemClock.elapsedRealtime()+30000;
            for(int i=0;i<list.length() && i<10;i++) {
                if(android.os.SystemClock.elapsedRealtime()>deadline)break;
                JSONObject anime=list.getJSONObject(i);
                if(anime.optString("rating","").startsWith("Rx"))continue;
                String[] aliases={anime.optString("title_english",""),anime.optString("title",""),anime.optString("title_japanese","")};
                String query=aliases[0].isEmpty()?aliases[1]:aliases[0];
                if(query.isEmpty())continue;
                try {
                    JSONArray hits=new JSONObject(new String(request(API+"/search/multi?language=en-US&include_adult=false&query="+URLEncoder.encode(query,"UTF-8"),credential,MAX_JSON),StandardCharsets.UTF_8)).getJSONArray("results");
                    for(int j=0;j<hits.length();j++) {
                        JSONObject hit=hits.getJSONObject(j);
                        String type=hit.optString("media_type","");
                        if(!MovieBackdropRules.supportedMedia(type)||hit.optBoolean("adult",false)||!MovieBackdropRules.validPath(hit.optString("backdrop_path","")))continue;
                        JSONArray genres=hit.optJSONArray("genre_ids");boolean animation=false;
                        if(genres!=null)for(int k=0;k<genres.length();k++)if(genres.optInt(k)==16)animation=true;
                        if(!animation)continue;
                        boolean titleMatch=false;
                        for(String field:new String[]{"title","name","original_title","original_name"})
                            if(AnimeTitleRules.matches(hit.optString(field,""),aliases))titleMatch=true;
                        if(titleMatch){matched.put(hit);break;}
                    }
                } catch(Exception ignored) { }
            }
        } catch(Exception error) { android.util.Log.i("TVHomeScreensaver","Anime list unavailable: "+error.getClass().getSimpleName()); }
        return matched;
    }
    private static List<Movie> loadPlaylist(File directory) {
        List<Movie> movies = new ArrayList<>();
        try (InputStream input = new AtomicFile(new File(directory, "playlist-all.json")).openRead()) {
            JSONArray cache = new JSONArray(new String(read(input, MAX_JSON), StandardCharsets.UTF_8));
            for (int i = 0; i < cache.length() && movies.size() < 60; i++) {
                JSONObject entry = cache.getJSONObject(i);
                String path = entry.getString("path"), url = entry.getString("url");
                if (!MovieBackdropRules.validPath(path) || !url.matches("https://image\\.tmdb\\.org/t/p/w(780|1280)/[A-Za-z0-9_-]+\\.(jpg|jpeg|png)")) continue;
                movies.add(new Movie(path, url, entry.getString("title"), entry.optString("category","movie")));
            }
        } catch (Exception ignored) { }
        return movies;
    }
    private static File imageFile(File directory, Movie movie) { return new File(directory, movie.path.substring(1)); }
    private static Bitmap decode(File file) {
        if (!file.isFile()) return null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true; BitmapFactory.decodeFile(file.getPath(), options);
            if (options.outWidth <= 0 || options.outHeight <= 0 || options.outWidth > 16000 || options.outHeight > 16000) return null;
            options.inSampleSize = 1;
            while (options.outWidth / options.inSampleSize > 1920 || options.outHeight / options.inSampleSize > 1080) options.inSampleSize *= 2;
            options.inJustDecodeBounds = false; options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getPath(), options);
        } catch (RuntimeException ignored) { return null; }
    }
    private static byte[] request(String address, String credential, int limit) throws Exception {
        address=address.replace("language=id-ID","language="+UiText.locale().toLanguageTag());
        URL url = new URL(address);
        if (!"https".equals(url.getProtocol()) || !("api.themoviedb.org".equals(url.getHost()) || "image.tmdb.org".equals(url.getHost()) || "api.jikan.moe".equals(url.getHost())))
            throw new IOException("Unexpected endpoint");
        if (credential != null && !"api.themoviedb.org".equals(url.getHost())) throw new IOException("Credential host mismatch");
        if (credential != null && MovieBackdropRules.isApiKey(credential)) {
            url = new URL(address + (address.contains("?") ? "&" : "?") + "api_key=" + credential);
        }
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setConnectTimeout(8000); connection.setReadTimeout(12000); connection.setInstanceFollowRedirects(false);
        if (credential != null && !MovieBackdropRules.isApiKey(credential)) connection.setRequestProperty("Authorization", "Bearer " + credential);
        try {
            int code = connection.getResponseCode();
            if (code == 401 || code == 403) throw new AuthenticationFailure();
            if (code != 200 || connection.getContentLengthLong() > limit) throw new IOException("TMDB request failed");
            try (InputStream input = connection.getInputStream()) { return read(input, limit); }
        } finally { connection.disconnect(); }
    }
    private static byte[] read(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
        while ((count = input.read(buffer)) != -1) { if (output.size() + count > limit) throw new IOException("Response too large"); output.write(buffer, 0, count); }
        return output.toByteArray();
    }
    private static void write(File file, byte[] data) throws IOException {
        AtomicFile atomic = new AtomicFile(file); FileOutputStream stream = atomic.startWrite();
        try { stream.write(data); atomic.finishWrite(stream); }
        catch (IOException error) { atomic.failWrite(stream); throw error; }
    }
    private static void trim(File directory, File current) {
        File[] files = directory.listFiles(file -> file.getName().matches("[A-Za-z0-9_-]+\\.(jpg|jpeg|png)"));
        if (files == null || files.length <= 6) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified)); int count = files.length;
        for (File file : files) if (count > 6 && !file.equals(current) && file.delete()) count--;
    }
    private static final class AuthenticationFailure extends IOException { }
}
