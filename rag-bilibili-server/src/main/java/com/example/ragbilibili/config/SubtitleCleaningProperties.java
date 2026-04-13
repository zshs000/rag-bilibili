package com.example.ragbilibili.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 字幕清洗策略的可配置参数。
 * <p>
 * 这些默认值同时服务于本地运行和文档化，避免规则散落在清洗器内部难以调优。
 */
@ConfigurationProperties(prefix = "rag.subtitle-cleaning")
public class SubtitleCleaningProperties {
    private int maxWindowSize = 3;
    private boolean conditionalExpandTo3 = true;
    private int weakKeywordThreshold = 2;
    private List<String> strongPatterns = new ArrayList<>(List.of(
            "本[期条次个]视频由.+赞助",
            "感谢.+对本[期条次个]视频的支持",
            "点击.{0,8}(下方|下边).{0,8}(链接|购物车)",
            "下载.{0,12}(app|APP|软件|应用)",
            "记得.{0,8}(一键三连|三连|点赞|投币|收藏)",
            "商务合作",
            "邀请码",
            "首单立减",
            "限时优惠",
            "高价回收"
    ));
    private List<String> weakKeywords = new ArrayList<>(List.of(
            "转转", "转转二手", "赞助", "三连", "点赞", "投币", "收藏", "关注", "转发", "链接",
            "下单", "优惠", "折扣", "返现", "补贴", "邀请码", "商务合作", "高价回收",
            "回收闲置", "下载app", "购物车", "点击下方"
    ));
    private List<String> suspiciousPatterns = new ArrayList<>(List.of(
            "感谢",
            "赞助",
            "点击.{0,8}(下方|下边)?",
            "链接",
            "下载",
            "优惠",
            "邀请码",
            "商务合作",
            "转转",
            "购物车"
    ));

    public int getMaxWindowSize() {
        return Math.max(1, maxWindowSize);
    }

    public void setMaxWindowSize(int maxWindowSize) {
        this.maxWindowSize = maxWindowSize;
    }

    public boolean isConditionalExpandTo3() {
        return conditionalExpandTo3;
    }

    public void setConditionalExpandTo3(boolean conditionalExpandTo3) {
        this.conditionalExpandTo3 = conditionalExpandTo3;
    }

    public int getWeakKeywordThreshold() {
        return Math.max(1, weakKeywordThreshold);
    }

    public void setWeakKeywordThreshold(int weakKeywordThreshold) {
        this.weakKeywordThreshold = weakKeywordThreshold;
    }

    public List<String> getStrongPatterns() {
        return strongPatterns == null ? List.of() : strongPatterns;
    }

    public void setStrongPatterns(List<String> strongPatterns) {
        this.strongPatterns = strongPatterns == null ? new ArrayList<>() : new ArrayList<>(strongPatterns);
    }

    public List<String> getWeakKeywords() {
        return weakKeywords == null ? List.of() : weakKeywords;
    }

    public void setWeakKeywords(List<String> weakKeywords) {
        this.weakKeywords = weakKeywords == null ? new ArrayList<>() : new ArrayList<>(weakKeywords);
    }

    public List<String> getSuspiciousPatterns() {
        return suspiciousPatterns == null ? List.of() : suspiciousPatterns;
    }

    public void setSuspiciousPatterns(List<String> suspiciousPatterns) {
        this.suspiciousPatterns = suspiciousPatterns == null ? new ArrayList<>() : new ArrayList<>(suspiciousPatterns);
    }
}