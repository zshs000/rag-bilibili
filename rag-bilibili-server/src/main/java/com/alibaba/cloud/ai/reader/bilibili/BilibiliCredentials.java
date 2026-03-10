package com.alibaba.cloud.ai.reader.bilibili;

import org.springframework.util.Assert;

public class BilibiliCredentials {
    private final String sessdata;
    private final String biliJct;
    private final String buvid3;

    private BilibiliCredentials(Builder builder) {
        this.sessdata = builder.sessdata;
        this.biliJct = builder.biliJct;
        this.buvid3 = builder.buvid3;
    }

    public String getSessdata() {
        return sessdata;
    }

    public String getBiliJct() {
        return biliJct;
    }

    public String getBuvid3() {
        return buvid3;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String sessdata;
        private String biliJct;
        private String buvid3;

        public Builder sessdata(String sessdata) {
            this.sessdata = sessdata;
            return this;
        }

        public Builder biliJct(String biliJct) {
            this.biliJct = biliJct;
            return this;
        }

        public Builder buvid3(String buvid3) {
            this.buvid3 = buvid3;
            return this;
        }

        public BilibiliCredentials build() {
            Assert.hasText(sessdata, "SESSDATA must not be empty");
            Assert.hasText(biliJct, "bili_jct must not be empty");
            return new BilibiliCredentials(this);
        }
    }
}
