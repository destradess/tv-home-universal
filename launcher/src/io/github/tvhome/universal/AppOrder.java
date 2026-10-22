package io.github.tvhome.universal;

import java.util.ArrayList;
import java.util.List;

/** Stable order: retain saved apps, seed favourites once, append newly installed apps. */
final class AppOrder {
    static List<String> merge(List<String> saved, List<String> defaults, List<String> installed) {
        java.util.Set<String> available=new java.util.HashSet<>(installed);
        java.util.Set<String> unique=new java.util.LinkedHashSet<>();
        for(String pkg:saved)if(available.contains(pkg))unique.add(pkg);
        if(unique.isEmpty())for(String pkg:defaults)if(available.contains(pkg))unique.add(pkg);
        unique.addAll(installed);
        List<String> result=new ArrayList<>(unique);
        return result;
    }

    static void move(List<String> order, String pkg, int position) {
        if (!order.remove(pkg)) return;
        order.add(Math.max(0, Math.min(position, order.size())), pkg);
    }
}
