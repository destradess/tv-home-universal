package io.github.tvhome.universal;
import java.net.URL;
final class WallpaperEndpointPolicy {
    static boolean allowed(String source,String address,boolean api){
        try{
            URL u=new URL(address);String host=u.getHost();
            if(!"https".equals(u.getProtocol())||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443))return false;
            if(api){
                switch(source){
                    case "pixabay":return host.equals("pixabay.com");
                    case "wallhaven":return host.equals("wallhaven.cc");
                    case "nasa":return host.equals("images-api.nasa.gov");
                    case "wikimedia":return host.equals("commons.wikimedia.org");
                    case "anilist":return host.equals("graphql.anilist.co");
                    case "waifu":return host.equals("api.waifu.im");
                    case "danbooru":return host.equals("danbooru.donmai.us");
                    case "safebooru":return host.equals("safebooru.org");
                }
                return false;
            }
            switch(source){
                case "pixabay":return host.equals("pixabay.com")||host.equals("cdn.pixabay.com");
                case "wallhaven":return host.equals("w.wallhaven.cc")||host.equals("th.wallhaven.cc");
                case "nasa":return host.equals("apod.nasa.gov")||host.equals("www.nasa.gov")||host.equals("images-assets.nasa.gov");
                case "wikimedia":return host.equals("upload.wikimedia.org")||host.equals("thumb.wikimedia.org");
                case "anilist":return host.equals("s4.anilist.co");
                case "jikan":return host.equals("image.tmdb.org");
                case "waifu":return host.equals("cdn.waifu.im");
                case "danbooru":return host.equals("cdn.donmai.us");
                case "safebooru":return host.equals("safebooru.org");
            }
        }catch(Exception ignored){}return false;
    }
}
