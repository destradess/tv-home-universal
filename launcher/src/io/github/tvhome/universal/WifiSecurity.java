package io.github.tvhome.universal;
final class WifiSecurity {
    static String kind(String capabilities){
        if(capabilities==null)return "system";
        String c=capabilities.toUpperCase(java.util.Locale.ROOT);
        if(c.contains("WAPI")||c.contains("WEP")||c.contains("EAP")||c.contains("IEEE8021X"))return "system";
        if(c.contains("PSK"))return "wpa2";
        if(c.contains("SAE"))return "wpa3";
        if(c.contains("OWE"))return "owe";
        if(c.contains("WPA")||c.contains("RSN")||c.contains("WAPI"))return "system";
        return "open";
    }
}
