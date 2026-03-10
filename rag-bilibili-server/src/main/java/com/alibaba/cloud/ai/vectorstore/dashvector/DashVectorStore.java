package com.alibaba.cloud.ai.vectorstore.dashvector;

import com.aliyun.dashvector.DashVectorCollection;
import com.aliyun.dashvector.models.Doc;
import com.aliyun.dashvector.models.DocOpResult;
import com.aliyun.dashvector.models.Vector;
import com.aliyun.dashvector.models.requests.DeleteDocRequest;
import com.aliyun.dashvector.models.requests.QueryDocRequest;
import com.aliyun.dashvector.models.requests.UpsertDocRequest;
import com.aliyun.dashvector.models.responses.Response;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class DashVectorStore implements VectorStore {
    private final DashVectorCollection collection;
    private final String collectionName;
    private final EmbeddingModel embeddingModel;
    private final int defaultTopK;
    private final double defaultSimilarityThreshold;
    private final String metric;

    public DashVectorStore(DashVectorCollection collection, String collectionName, EmbeddingModel embeddingModel,
                           int defaultTopK, double defaultSimilarityThreshold, String metric) {
        this.collection = collection;
        this.collectionName = collectionName;
        this.embeddingModel = embeddingModel;
        this.defaultTopK = defaultTopK;
        this.defaultSimilarityThreshold = defaultSimilarityThreshold;
        this.metric = metric == null ? "cosine" : metric;
    }

    @Override
    public String getName() {
        return collectionName;
    }

    @Override
    public void add(List<Document> documents) {
        if (CollectionUtils.isEmpty(documents)) {
            return;
        }

        List<Doc> docs = new ArrayList<>(documents.size());
        for (Document document : documents) {
            float[] embedding = embeddingModel.embed(document);
            List<Float> vectorValues = new ArrayList<>(embedding.length);
            for (float value : embedding) {
                vectorValues.add(value);
            }

            Doc.DocBuilder builder = Doc.builder()
                    .vector(Vector.builder().value(vectorValues).build())
                    .field("content", document.getContent());

            if (document.getId() != null && !document.getId().isBlank()) {
                builder.id(document.getId());
            }
            if (document.getMetadata() != null && !document.getMetadata().isEmpty()) {
                builder.fields(document.getMetadata());
            }
            docs.add(builder.build());
        }

        Response<List<DocOpResult>> response = collection.upsert(UpsertDocRequest.builder().docs(docs).build());
        if (!Boolean.TRUE.equals(response.isSuccess())) {
            throw new IllegalStateException("Failed to upsert documents to DashVector: " + response.getMessage());
        }
    }

    @Override
    public Optional<Boolean> delete(List<String> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Optional.of(Boolean.TRUE);
        }
        Response<List<DocOpResult>> response = collection.delete(DeleteDocRequest.builder().ids(ids).build());
        return Optional.of(Boolean.TRUE.equals(response.isSuccess()));
    }

    @Override
    public List<Document> similaritySearch(SearchRequest searchRequest) {
        int topK = searchRequest.getTopK() > 0 ? searchRequest.getTopK() : defaultTopK;
        float[] queryEmbedding = embeddingModel.embed(searchRequest.getQuery());
        List<Float> vectorValues = new ArrayList<>(queryEmbedding.length);
        for (float value : queryEmbedding) {
            vectorValues.add(value);
        }

        var requestBuilder = QueryDocRequest.builder()
                .vector(Vector.builder().value(vectorValues).build())
                .topk(topK)
                .includeVector(false);

        if (searchRequest.hasFilterExpression()) {
            requestBuilder.filter(String.valueOf(searchRequest.getFilterExpression()));
        }

        Response<List<Doc>> response = collection.query(requestBuilder.build());
        if (!Boolean.TRUE.equals(response.isSuccess())) {
            throw new IllegalStateException("Failed to query DashVector: " + response.getMessage());
        }

        List<Doc> results = response.getOutput();
        if (results == null) {
            return List.of();
        }

        double threshold = searchRequest.getSimilarityThreshold();
        return results.stream()
                .filter(doc -> matchesThreshold(doc.getScore(), threshold))
                .map(this::toDocument)
                .collect(Collectors.toList());
    }

    @Override
    public List<Document> similaritySearch(String query) {
        return similaritySearch(SearchRequest.query(query)
                .withTopK(defaultTopK)
                .withSimilarityThreshold(defaultSimilarityThreshold));
    }

    private boolean matchesThreshold(float rawScore, double threshold) {
        if (threshold <= 0) {
            return true;
        }
        if ("euclidean".equalsIgnoreCase(metric)) {
            return rawScore <= threshold;
        }
        return toSimilarity(rawScore) >= threshold;
    }

    private double toSimilarity(float rawScore) {
        if ("cosine".equalsIgnoreCase(metric)) {
            return 1.0d - rawScore;
        }
        return rawScore;
    }

    private Document toDocument(Doc doc) {
        Map<String, Object> metadata = new HashMap<>();
        if (doc.getFields() != null) {
            metadata.putAll(doc.getFields());
        }
        Object contentValue = metadata.remove("content");
        metadata.put("score", toSimilarity(doc.getScore()));

        return Document.builder()
                .withId(doc.getId())
                .withContent(contentValue == null ? "" : String.valueOf(contentValue))
                .withMetadata(metadata)
                .build();
    }
}
