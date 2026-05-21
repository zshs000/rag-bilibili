package com.example.ragbilibili.vectorstore.dashvector;

import com.aliyun.dashvector.DashVectorCollection;
import com.aliyun.dashvector.models.Doc;
import com.aliyun.dashvector.models.DocOpResult;
import com.aliyun.dashvector.models.Vector;
import com.aliyun.dashvector.models.requests.DeleteDocRequest;
import com.aliyun.dashvector.models.requests.QueryDocRequest;
import com.aliyun.dashvector.models.requests.UpsertDocRequest;
import com.aliyun.dashvector.models.responses.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
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
    private final DashVectorFilterExpressionConverter filterExpressionConverter;

    private static final Logger logger = LoggerFactory.getLogger(DashVectorStore.class);

    public DashVectorStore(DashVectorCollection collection, String collectionName, EmbeddingModel embeddingModel,
                           int defaultTopK, double defaultSimilarityThreshold, String metric) {
        this.collection = collection;
        this.collectionName = collectionName;
        this.embeddingModel = embeddingModel;
        this.defaultTopK = defaultTopK;
        this.defaultSimilarityThreshold = defaultSimilarityThreshold;
        this.metric = metric == null ? "cosine" : metric;
        this.filterExpressionConverter = new DashVectorFilterExpressionConverter();
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
                    .field("content", document.getText());

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
    public void delete(List<String> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }

        DeleteDocRequest request = DeleteDocRequest.builder().ids(ids).build();
        Response<?> response = collection.delete(request);

        if (!response.isSuccess()) {
            throw new RuntimeException("Failed to delete documents from DashVector: " + response.getMessage());
        }

        logger.debug("Successfully deleted {} documents from DashVector collection '{}'", ids.size(), collectionName);
    }

    @Override
    public void delete(Filter.Expression filterExpression) {
        throw new UnsupportedOperationException(
                "DashVector does not support filter-based deletion. " +
                        "Use doDelete(List<String> ids) instead."
        );
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
            requestBuilder.filter(filterExpressionConverter.convertExpression(searchRequest.getFilterExpression()));
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
        return similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(this.defaultTopK)
                .similarityThreshold(this.defaultSimilarityThreshold)
                .build());
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
                .id(doc.getId())
                .text(contentValue == null ? "" : String.valueOf(contentValue))
                .metadata(metadata)
                .build();
    }
}
