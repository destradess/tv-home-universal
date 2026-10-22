package io.github.tvhome.universal;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AtomicFile;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;

/** Runs on the existing wallpaper worker. Metadata once daily, bounded private image cache. */
final class ExternalWallpaperProvider {
    private static final int JSON_LIMIT=1024*1024,IMAGE_LIMIT=8*1024*1024;
    private static final Object LOCK=new Object();
    private static final class Image {
        final String url,title,credit,page;
        Image(String u,String t,String c,String p){url=u;title=t;credit=c;page=p;}
    }
    static MovieWallpaperProvider.Result next(Context c,String source){
        synchronized(LOCK){
            SharedPreferences state=c.getSharedPreferences("wallpaper_"+source,0);
            File dir=new File(c.getFilesDir(),"wallpaper-source-"+source);
            if(!dir.isDirectory()&&!dir.mkdirs())return null;
            List<Image> list=load(dir,source);
            long now=System.currentTimeMillis();
            if((list.isEmpty()||now-state.getLong("fetched",0)>86400000L)&&now>=state.getLong("retry",0)){
                try{
                    List<Image> fresh=fetch(c,source);
                    if(fresh.isEmpty())throw new IOException("No images");
                    JSONArray metadata=new JSONArray();
                    for(Image image:fresh)metadata.put(new JSONObject().put("url",image.url).put("title",image.title).put("credit",image.credit).put("page",image.page));
                    write(new File(dir,"playlist.json"),metadata.toString().getBytes(StandardCharsets.UTF_8));
                    list=fresh;state.edit().putLong("fetched",now).remove("retry").apply();
                }catch(Exception error){
                    state.edit().putLong("retry",now+1800000L).apply();
                    MovieWallpaperProvider.prefs(c).edit().putString("status",WallpaperSources.name(source)+UiText.get(R.string.ui_004)).apply();
                    android.util.Log.i("TVHomeWallpaper",source+": "+error.getClass().getSimpleName());
                }
            }
            if(list.isEmpty())return null;
            int previous=state.getInt("index",-1),index=new Random().nextInt(list.size());
            if(index==previous&&list.size()>1)index=(index+1)%list.size();
            // At most two downloads per rotation, then use existing cache.
            for(int step=0;step<list.size();step++){
                int at=(index+step)%list.size();Image image=list.get(at);
                try{
                    File file=new File(dir,hash(image.url)+".img");
                    if(!file.exists()&&step<2)write(file,request(source,image.url,false,null,IMAGE_LIMIT));
                    Bitmap bitmap=decode(file);
                    if(bitmap==null){if(file.isFile())file.delete();continue;}
                    state.edit().putInt("index",at).apply();trim(dir,file);
                    String credit=image.credit.isEmpty()?WallpaperSources.name(source):image.credit;
                    MovieWallpaperProvider.prefs(c).edit().putString("current_credit",credit).putString("current_page",image.page)
                        .putString("current_source",source).putString("status","Wallpaper "+WallpaperSources.name(source)+UiText.get(R.string.ui_003)).apply();
                    return new MovieWallpaperProvider.Result(bitmap,image.title+" / "+credit);
                }catch(Exception ignored){}
            }
            return null;
        }
    }
    private static List<Image> fetch(Context c,String source)throws Exception{
        List<Image> out=new ArrayList<>();String key=WallpaperSources.key(c,source);
        if(WallpaperSources.requiresKey(source)&&key.isEmpty())throw new IOException("Key required");
        JSONArray a;
        switch(source){
            case "pixabay":
                a=obj(source,"https://pixabay.com/api/?key="+enc(key)+"&q=nature+landscape&orientation=horizontal&image_type=photo&safesearch=true&per_page=30",null).getJSONArray("hits");
                for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);if(o.optInt("imageWidth")<=o.optInt("imageHeight"))continue;
                    add(out,source,o.optString("largeImageURL"),o.optString("tags",UiText.get(R.string.ui_092)),o.optString("user")+" / Pixabay",o.optString("pageURL"));}break;
            case "wallhaven":
                a=obj(source,"https://wallhaven.cc/api/v1/search?purity=100&ratios=16x9&sorting=toplist&topRange=1M"+(key.isEmpty()?"":"&apikey="+enc(key)),null).getJSONArray("data");
                for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);if(!o.optString("purity").equals("sfw")||o.optLong("file_size")>IMAGE_LIMIT)continue;
                    add(out,source,o.optString("path"),"Wallhaven "+o.optString("id"),"Wallhaven",o.optString("url"));}break;
            case "nasa":
                a=obj(source,"https://images-api.nasa.gov/search?q=nebula&media_type=image&page_size=30",null).getJSONObject("collection").getJSONArray("items");
                for(int i=0;i<a.length();i++){
                    JSONObject item=a.getJSONObject(i);JSONArray links=item.optJSONArray("links"),data=item.optJSONArray("data");
                    if(links==null||data==null||data.length()==0)continue;JSONObject o=data.getJSONObject(0);
                    String url="";int width=0;
                    for(int j=0;j<links.length();j++){
                        JSONObject link=links.getJSONObject(j);int w=link.optInt("width"),h=link.optInt("height");
                        if(!link.optString("render").equals("image")||w<=h||w<800||w>2800||link.optLong("size")>IMAGE_LIMIT)continue;
                        if(w>width){width=w;url=link.optString("href");}
                    }
                    add(out,source,url,o.optString("title",UiText.get(R.string.ui_025)),o.optString("photographer",o.optString("center","NASA"))+" / NASA","https://images.nasa.gov/details/"+enc(o.optString("nasa_id")));
                }break;
            case "wikimedia":
                JSONObject pages=obj(source,"https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrsearch=landscape%20filetype:bitmap&gsrnamespace=6&gsrlimit=15&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&iiurlwidth=1920&iiextmetadatafilter=Artist%7CLicenseShortName%7CLicenseUrl",null).getJSONObject("query").getJSONObject("pages");
                for(Iterator<String> it=pages.keys();it.hasNext();){JSONObject o=pages.getJSONObject(it.next());JSONArray info=o.optJSONArray("imageinfo");if(info==null||info.length()==0)continue;
                    JSONObject image=info.getJSONObject(0);if(image.optInt("width")<=image.optInt("height"))continue;JSONObject meta=image.optJSONObject("extmetadata");
                    String artist=metaValue(meta,"Artist"),license=metaValue(meta,"LicenseShortName");
                    if(artist.isEmpty()||license.isEmpty())continue;
                    add(out,source,image.optString("thumburl",image.optString("url")),o.optString("title").replaceFirst("^File:",""),artist+" / "+license+" / Wikimedia",image.optString("descriptionurl"));}break;
            case "anilist":
                String query="{Page(perPage:40){media(type:ANIME,isAdult:false,sort:POPULARITY_DESC){title{romaji english}bannerImage siteUrl isAdult}}}";
                a=new JSONObject(text(request(source,"https://graphql.anilist.co/",true,null,JSON_LIMIT,query))).getJSONObject("data").getJSONObject("Page").getJSONArray("media");
                for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);if(o.optBoolean("isAdult",true))continue;JSONObject title=o.getJSONObject("title");
                    String t=title.optString("english","");if(t.isEmpty()||t.equals("null"))t=title.optString("romaji","Anime");
                    add(out,source,o.optString("bannerImage"),t,"AniList",o.optString("siteUrl"));}break;
            case "jikan":
                a=MovieWallpaperProvider.popularAnime(key);
                for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);String path=o.optString("backdrop_path");if(!MovieBackdropRules.validPath(path))continue;
                    add(out,source,"https://image.tmdb.org/t/p/w1280"+path,o.optString("name",o.optString("title","Anime")),"Jikan / MyAnimeList / TMDB", "https://www.themoviedb.org/");}break;
            case "waifu":
                a=obj(source,"https://api.waifu.im/images?IsNsfw=False&Orientation=Landscape&PageSize=30",key.isEmpty()?null:key).getJSONArray("items");
                for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);if(o.optBoolean("isNsfw",true)||o.optBoolean("isAnimated",true)||o.optInt("width")<=o.optInt("height")||o.optLong("byteSize")>IMAGE_LIMIT)continue;
                    JSONArray artists=o.optJSONArray("artists");String artist=artists!=null&&artists.length()>0?artists.getJSONObject(0).optString("name"):"";
                    add(out,source,o.optString("url"),UiText.get(R.string.ui_050)+o.optString("id"),artist.isEmpty()?"Waifu.im":artist+" / Waifu.im",o.optString("source"));}break;
            case "danbooru":
            case "safebooru":
                boolean dan=source.equals("danbooru");
                String endpoint=dan?"https://danbooru.donmai.us/posts.json?limit=30&tags=rating%3Ag+scenery":"https://safebooru.org/index.php?page=dapi&s=post&q=index&json=1&limit=30&tags=rating%3Asafe+landscape";
                a=new JSONArray(text(request(source,endpoint,true,null,JSON_LIMIT)));
                for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);if(!o.optString("rating").equals(dan?"g":"safe")||o.optInt(dan?"image_width":"width")<=o.optInt(dan?"image_height":"height")||o.optBoolean("is_deleted",false))continue;
                    if(!dan&&!o.optString("status","active").equals("active"))continue;
                    String url=o.optString("large_file_url",o.optString("file_url"));if(url.endsWith(".gif")||url.endsWith(".webm")||url.endsWith(".mp4")||o.optLong("file_size")>IMAGE_LIMIT)continue;
                    String id=o.optString("id"),name=WallpaperSources.name(source),artist=o.optString("tag_string_artist","").replace('_',' ');
                    add(out,source,url,UiText.get(R.string.ui_050)+id,artist.isEmpty()?name:artist+" / "+name,dan?"https://danbooru.donmai.us/posts/"+id:"https://safebooru.org/index.php?page=post&s=view&id="+id);}break;
            default:throw new IOException("Unsupported source");
        }
        return out;
    }
    private static String metaValue(JSONObject meta,String key){JSONObject v=meta==null?null:meta.optJSONObject(key);return v==null?"":android.text.Html.fromHtml(v.optString("value"),android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim();}
    private static void add(List<Image> out,String source,String url,String title,String credit,String page){
        if(out.size()>=30||!WallpaperEndpointPolicy.allowed(source,url,false))return;
        for(Image existing:out)if(existing.url.equals(url))return;
        out.add(new Image(url,clean(title,160),clean(credit,240),page));
    }
    private static String clean(String value,int limit){return value.replaceAll("[\\p{Cntrl}]"," ").substring(0,Math.min(value.length(),limit));}
    private static JSONObject obj(String source,String url,String key)throws Exception{return new JSONObject(text(request(source,url,true,key,JSON_LIMIT)));}
    private static String text(byte[] b){return new String(b,StandardCharsets.UTF_8);}
    private static String enc(String s)throws Exception{return URLEncoder.encode(s,"UTF-8");}
    private static byte[] request(String source,String address,boolean api,String key,int limit)throws Exception{
        return request(source,address,api,key,limit,null);
    }
    private static byte[] request(String source,String address,boolean api,String key,int limit,String graphql)throws Exception{
        if(!WallpaperEndpointPolicy.allowed(source,address,api))throw new IOException("Unexpected host");
        if(key!=null&&(!api||!source.equals("waifu")))throw new IOException("Credential host mismatch");
        HttpsURLConnection conn=(HttpsURLConnection)new URL(address).openConnection();
        conn.setConnectTimeout(8000);conn.setReadTimeout(12000);conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("User-Agent","TVHome/1.77 (personal Android TV launcher)");
        if(api)conn.setRequestProperty("Accept","application/json");
        if(key!=null)conn.setRequestProperty("X-Api-Key",key);
        try{
            if(graphql!=null){
                if(!source.equals("anilist")||!api)throw new IOException("Unexpected POST endpoint");
                conn.setRequestMethod("POST");conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/json");
                byte[] body=new JSONObject().put("query",graphql).toString().getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(body.length);try(OutputStream out=conn.getOutputStream()){out.write(body);}
            }
            if(conn.getResponseCode()!=200||conn.getContentLengthLong()>limit)throw new IOException("HTTP request failed");
            if(!api&&!conn.getContentType().toLowerCase(java.util.Locale.ROOT).startsWith("image/"))throw new IOException("Not an image");
            try(InputStream in=conn.getInputStream()){return read(in,limit);}
        }finally{conn.disconnect();}
    }
    private static byte[] read(InputStream in,int limit)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>limit)throw new IOException("Too large");out.write(buffer,0,n);}return out.toByteArray();}
    private static void write(File file,byte[] data)throws IOException{AtomicFile a=new AtomicFile(file);FileOutputStream out=a.startWrite();try{out.write(data);a.finishWrite(out);}catch(IOException e){a.failWrite(out);throw e;}}
    private static List<Image> load(File dir,String source){List<Image> list=new ArrayList<>();try(InputStream in=new AtomicFile(new File(dir,"playlist.json")).openRead()){
        JSONArray a=new JSONArray(text(read(in,JSON_LIMIT)));for(int i=0;i<a.length()&&i<30;i++){JSONObject o=a.getJSONObject(i);add(list,source,o.getString("url"),o.getString("title"),o.optString("credit"),o.optString("page"));}
    }catch(Exception ignored){}return list;}
    private static Bitmap decode(File file){if(!file.exists())return null;BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),o);if(o.outWidth<=0||o.outHeight<=0||o.outWidth>16000||o.outHeight>16000)return null;o.inSampleSize=1;while(o.outWidth/o.inSampleSize>1920||o.outHeight/o.inSampleSize>1080)o.inSampleSize*=2;o.inJustDecodeBounds=false;o.inPreferredConfig=Bitmap.Config.RGB_565;try{return BitmapFactory.decodeFile(file.getPath(),o);}catch(RuntimeException e){return null;}}
    private static String hash(String url)throws Exception{byte[] digest=MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:digest)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
    private static void trim(File dir,File keep){File[] files=dir.listFiles((d,n)->n.endsWith(".img"));if(files==null||files.length<=6)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));int left=files.length;for(File f:files)if(left>6&&!f.equals(keep)&&f.delete())left--;}
}
