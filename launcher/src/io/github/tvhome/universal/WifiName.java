package io.github.tvhome.universal;

final class WifiName {
    static String clean(String raw) {
        if(raw==null)return "";
        if(raw.startsWith("\"") && raw.endsWith("\"") && raw.length()>=2)raw=raw.substring(1,raw.length()-1);
        if(raw.isEmpty() || raw.equalsIgnoreCase("<unknown ssid>") || raw.equals("0x"))return "";
        return raw;
    }
}
