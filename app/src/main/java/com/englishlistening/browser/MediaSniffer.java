package com.englishlistening.browser;

import android.net.Uri;
import android.webkit.WebView;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MediaSniffer {
    public interface Callback { void onResult(List<Item> items); }

    public static class Item {
        public final String url;
        public final String type;
        public final long seenAt;
        Item(String url, String type, long seenAt) { this.url=url; this.type=type; this.seenAt=seenAt; }
        public String label() {
            String host=AppData.compactHost(url);
            String name=Uri.parse(url).getLastPathSegment();
            if(name==null||name.trim().isEmpty())name=url;
            if(name.length()>52)name=name.substring(0,52)+"…";
            return type+"  "+name+(host.isEmpty()?"":"\n"+host);
        }
    }

    private final Map<String,Item> found=Collections.synchronizedMap(new LinkedHashMap<>());

    public void observe(String url){
        String type=classify(url);if(type==null)return;
        synchronized(found){found.put(url,new Item(url,type,System.currentTimeMillis()));while(found.size()>300){String first=found.keySet().iterator().next();found.remove(first);}}
    }

    public void scan(WebView webView,Callback callback){
        if(webView==null){callback.onResult(snapshot());return;}
        String js="(function(){var o={};function a(u){if(!u)return;try{u=(new URL(u,location.href)).href}catch(e){};if(/^blob:|^data:/i.test(u))return;if(/\\.(mp3|m4a|aac|wav|ogg|flac|mp4|webm|mov|mkv|m3u8|jpg|jpeg|png|webp|gif|svg|pdf|doc|docx|ppt|pptx|xls|xlsx|zip)(\\?|#|$)/i.test(u))o[u]=1;}try{document.querySelectorAll('audio,video,source,a[href],img[src]').forEach(function(e){a(e.currentSrc||e.src||e.href);});}catch(e){}try{performance.getEntriesByType('resource').forEach(function(e){a(e.name);});}catch(e){}return JSON.stringify(Object.keys(o).slice(-220));})()";
        webView.evaluateJavascript(js,value->{try{String decoded=new JSONArray("["+value+"]").getString(0);JSONArray arr=new JSONArray(decoded);for(int i=0;i<arr.length();i++)observe(arr.optString(i));}catch(Exception ignored){}callback.onResult(snapshot());});
    }

    public List<Item> snapshot(){ArrayList<Item> items; synchronized(found){items=new ArrayList<>(found.values());}items.sort(Comparator.comparingLong((Item x)->x.seenAt).reversed());return items;}
    public void clear(){found.clear();}

    public static String classify(String url){
        if(url==null)return null;String u=url.toLowerCase(Locale.ROOT);if(!(u.startsWith("http://")||u.startsWith("https://")))return null;int q=u.indexOf('?');String p=q>=0?u.substring(0,q):u;
        if(p.endsWith(".m3u8"))return"HLS";
        if(p.matches(".*\\.(mp3|m4a|aac|wav|ogg|flac)$"))return"音频";
        if(p.matches(".*\\.(mp4|webm|mov|mkv)$"))return"视频";
        if(p.matches(".*\\.(jpg|jpeg|png|webp|gif|svg)$"))return"图片";
        if(p.matches(".*\\.(pdf|doc|docx|ppt|pptx|xls|xlsx|zip)$"))return"文档";
        return null;
    }
}
