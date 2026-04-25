package com.example.ragbilibili.service.impl;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 事务外准备好的导入数据。
 */
public class PreparedVideoImportData {

    private final String bvid;
    private final String title;
    private final String description;
    private final List<Document> indexedDocuments;
    private final List<String> vectorIds;
    private final List<PreparedChunkPayload> chunkPayloads;

    public PreparedVideoImportData(String bvid,
                                   String title,
                                   String description,
                                   List<Document> indexedDocuments,
                                   List<String> vectorIds,
                                   List<PreparedChunkPayload> chunkPayloads) {
        this.bvid = bvid;
        this.title = title;
        this.description = description;
        this.indexedDocuments = List.copyOf(indexedDocuments);
        this.vectorIds = List.copyOf(vectorIds);
        this.chunkPayloads = List.copyOf(chunkPayloads);
    }

    public String getBvid() {
        return bvid;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public List<Document> getIndexedDocuments() {
        return indexedDocuments;
    }

    public List<String> getVectorIds() {
        return vectorIds;
    }

    public List<PreparedChunkPayload> getChunkPayloads() {
        return chunkPayloads;
    }

    public static class PreparedChunkPayload {
        private final int chunkIndex;
        private final int totalChunks;
        private final String chunkText;
        private final String vectorId;

        public PreparedChunkPayload(int chunkIndex, int totalChunks, String chunkText, String vectorId) {
            this.chunkIndex = chunkIndex;
            this.totalChunks = totalChunks;
            this.chunkText = chunkText;
            this.vectorId = vectorId;
        }

        public int getChunkIndex() {
            return chunkIndex;
        }

        public int getTotalChunks() {
            return totalChunks;
        }

        public String getChunkText() {
            return chunkText;
        }

        public String getVectorId() {
            return vectorId;
        }
    }
}