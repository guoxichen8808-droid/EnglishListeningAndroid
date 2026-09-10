package com.englishlistening.browser;

import org.json.JSONArray;
import org.json.JSONObject;

public class HomePageBuilder {
    public static String build(AppData data) {
        JSONObject stats = data.stats();
        long todayMinutes = stats.optLong("todaySeconds", 0L) / 60L;
        int goal = data.dailyGoalMinutes();
        int progress = (int) Math.min(100, todayMinutes * 100L / Math.max(1, goal));
        int practices = stats.optInt("todayPractices", 0);
        int streak = stats.optInt("streak", 0);
        int favorites = data.favorites().length();
        int materials = data.materials().length();
        String last = data.lastLearningTitle();
        if (last == null || last.trim().isEmpty()) last = "还没有学习记录";

        StringBuilder custom = new StringBuilder();
        JSONArray shortcuts = data.shortcuts();
        if (shortcuts.length() > 0) {
            custom.append("<div class='section'><div class='head'><h2>我的网站</h2><a href='app://shortcuts'>管理</a></div><div class='grid'>");
            for (int i = 0; i < shortcuts.length(); i++) {
                JSONObject o = shortcuts.optJSONObject(i);
                if (o == null) continue;
                String name = AppData.html(o.optString("name", "网站"));
                String url = AppData.html(o.optString("url", ""));
                custom.append("<a class='card' href='").append(url).append("'><div class='badge'>MY</div><b>")
                        .append(name).append("</b><span>").append(AppData.html(AppData.compactHost(o.optString("url")))).append("</span></a>");
            }
            custom.append("</div></div>");
        }

        return "<!doctype html><html lang='zh-CN'><head><meta charset='utf-8'>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'>" +
                "<title>English Listening Ultimate</title><style>" +
                "*{box-sizing:border-box}body{margin:0;background:#f5f7fb;color:#111827;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Noto Sans SC',sans-serif}" +
                ".wrap{max-width:760px;margin:auto;padding:18px 16px 34px}.hero{background:linear-gradient(135deg,#111827,#1d4ed8);color:white;border-radius:24px;padding:22px 20px;box-shadow:0 10px 30px rgba(17,24,39,.12)}" +
                ".hero h1{font-size:26px;margin:0 0 5px}.hero p{font-size:13px;opacity:.88;margin:0}.stats{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-top:17px}.stat{background:rgba(255,255,255,.12);border-radius:14px;padding:10px;text-align:center}.stat b{font-size:18px;display:block}.stat span{font-size:10px;opacity:.82}" +
                ".progress{height:8px;background:rgba(255,255,255,.18);border-radius:8px;margin-top:13px;overflow:hidden}.progress i{display:block;height:100%;background:white;width:" + progress + "%}.goal{font-size:11px;margin-top:6px;opacity:.82}" +
                ".resume{display:block;text-decoration:none;color:#111827;background:white;margin-top:13px;border-radius:17px;padding:14px 15px;border:1px solid #e7eaf0}.resume small{display:block;color:#6b7280;margin-bottom:4px}.resume b{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}" +
                ".section{margin-top:22px}.head{display:flex;align-items:center;justify-content:space-between}.head h2,.section h2{font-size:17px;margin:0 0 10px}.head a{font-size:12px;text-decoration:none;color:#2563eb;margin-bottom:10px}.grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}.card{display:block;text-decoration:none;color:#111827;background:white;border:1px solid #e7eaf0;border-radius:17px;padding:15px 14px;min-height:105px}.badge{width:38px;height:38px;border-radius:12px;background:#eff6ff;color:#1d4ed8;display:flex;align-items:center;justify-content:center;font-weight:800;font-size:12px;margin-bottom:9px}.card b{font-size:14px;display:block;margin-bottom:4px}.card span{font-size:11px;line-height:1.45;color:#6b7280;display:block}" +
                ".tools{display:grid;grid-template-columns:repeat(4,1fr);gap:8px}.tool{display:block;text-align:center;text-decoration:none;color:#111827;background:white;border:1px solid #e7eaf0;border-radius:15px;padding:13px 4px;font-size:11px}.tool em{display:block;font-style:normal;font-size:20px;margin-bottom:4px}" +
                ".search{display:flex;background:white;border:1px solid #e7eaf0;border-radius:16px;padding:8px}.search input{min-width:0;flex:1;border:0;outline:0;background:transparent;padding:8px;font-size:14px}.search button{border:0;border-radius:11px;background:#1d4ed8;color:white;padding:0 14px;font-weight:700}" +
                ".tip{background:#eef2ff;border-radius:16px;padding:14px 15px;color:#374151;font-size:12px;line-height:1.7}.foot{text-align:center;color:#9ca3af;font-size:10px;margin-top:22px}@media(max-width:380px){.grid{grid-template-columns:1fr}.tools{grid-template-columns:repeat(2,1fr)}}" +
                "</style><script>function yg(){var q=document.getElementById('q').value.trim();if(q)location.href='https://youglish.com/pronounce/'+encodeURIComponent(q)+'/english';}</script></head><body><div class='wrap'>" +
                "<div class='hero'><h1>English Listening Ultimate</h1><p>听力 · 跟读 · 听写 · AI陪练 · 素材库</p>" +
                "<div class='stats'><div class='stat'><b>" + todayMinutes + "</b><span>今日分钟</span></div><div class='stat'><b>" + practices + "</b><span>今日训练</span></div><div class='stat'><b>" + streak + "</b><span>连续天数</span></div></div>" +
                "<div class='progress'><i></i></div><div class='goal'>今日目标 " + goal + " 分钟 · 已完成 " + progress + "%</div></div>" +
                "<a class='resume' href='app://continue'><small>继续上次学习</small><b>" + AppData.html(last) + "</b></a>" +
                "<div class='section'><h2>学习中心</h2><div class='tools'>" +
                "<a class='tool' href='app://studio'><em>🎧</em>训练工作室</a><a class='tool' href='app://materials'><em>📚</em>素材库 <b>" + materials + "</b></a>" +
                "<a class='tool' href='app://favorites'><em>⭐</em>收藏 <b>" + favorites + "</b></a><a class='tool' href='app://stats'><em>📈</em>学习统计</a>" +
                "<a class='tool' href='app://downloads'><em>⬇</em>下载</a><a class='tool' href='app://history'><em>🕘</em>历史</a>" +
                "<a class='tool' href='app://backup'><em>☁</em>备份恢复</a><a class='tool' href='app://settings'><em>⚙</em>设置</a></div></div>" +
                "<div class='section'><h2>精选听力网站</h2><div class='grid'>" +
                site("EL", "ELLLO", "https://elllo.org/", "分级听力、多口音、原文与练习") +
                site("BBC", "BBC Learning English", "https://www.bbc.co.uk/learningenglish/", "6 Minute English、自然英式英语") +
                site("VOA", "VOA Learning English", "https://learningenglish.voanews.com/", "清晰美音、分级内容") +
                site("YG", "YouGlish", "https://youglish.com/", "真人视频里的单词与短语发音") +
                "</div></div>" + custom +
                "<div class='section'><div class='head'><h2>快速查真人发音</h2><a href='app://shortcuts'>添加网站</a></div><div class='search'><input id='q' placeholder='例如 ceiling grid / touch up' onkeydown=\"if(event.key==='Enter')yg()\"><button onclick='yg()'>YouGlish</button></div></div>" +
                "<div class='section'><div class='tip'>推荐使用顺序：选一篇素材 → 先盲听 → 打开训练工作室做逐句精听/听写/跟读 → 保存难句 → 最后交给 ChatGPT 或豆包做复述和纠错。AI不是必须步骤，本地训练本身即可使用。</div></div>" +
                "<div class='foot'>Ultimate Edition · 本地学习数据保存在手机内</div></div></body></html>";
    }

    private static String site(String badge, String name, String url, String desc) {
        return "<a class='card' href='" + url + "'><div class='badge'>" + badge + "</div><b>" + name + "</b><span>" + desc + "</span></a>";
    }
}
