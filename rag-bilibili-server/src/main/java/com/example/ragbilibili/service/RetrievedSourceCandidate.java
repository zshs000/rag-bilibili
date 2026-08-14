package com.example.ragbilibili.service;

public record RetrievedSourceCandidate(
        int citationIndex,
        String vectorId,
        String bvid,
        String videoTitle,
        Long cid,
        Integer pageNumber,
        Long startTimeMs,
        Long endTimeMs,
        String snippet) {
}
