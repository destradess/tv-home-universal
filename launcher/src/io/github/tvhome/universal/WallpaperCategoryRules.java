package io.github.tvhome.universal;
final class WallpaperCategoryRules {
    static final String[] IDS={"movie","tv","anime","top_movie","top_tv","top_anime"};
    static final String[] ENDPOINTS={
        "/trending/movie/week?language=id-ID&page=1",
        "/trending/tv/week?language=id-ID&page=1",
        "/trending/all/week?language=id-ID&page=1",
        "/movie/top_rated?language=id-ID&page=1",
        "/tv/top_rated?language=id-ID&page=1",
        "/discover/tv?language=id-ID&include_adult=false&with_genres=16&with_origin_country=JP&vote_count.gte=100&sort_by=vote_average.desc&page=1"
    };
    static int index(String id){for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return i;return -1;}
    static boolean enabled(int mask,String id){int i=index(id);return i>=0&&(mask&(1<<i))!=0;}
    static int nextCategory(int previous,int mask,java.util.List<String> available){
        for(int step=1;step<=IDS.length;step++){
            int i=Math.floorMod(previous+step,IDS.length);
            if(enabled(mask,IDS[i])&&available.contains(IDS[i]))return i;
        }
        return -1;
    }
}
