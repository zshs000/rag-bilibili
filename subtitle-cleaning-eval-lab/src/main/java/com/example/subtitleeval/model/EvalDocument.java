package com.example.subtitleeval.model;

import java.util.HashMap;
import java.util.Map;

public class EvalDocument {
    private final String id;
    private final String text;
    private final Map<String, Object> metadata;

    private EvalDocument(Builder builder) {
        this.id = builder.id;
        this.text = builder.text;
        this.metadata = Map.copyOf(builder.metadata);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public static class Builder {
        private String id;
        private String text;
        private Map<String, Object> metadata = new HashMap<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public EvalDocument build() {
            return new EvalDocument(this);
        }
    }
}
