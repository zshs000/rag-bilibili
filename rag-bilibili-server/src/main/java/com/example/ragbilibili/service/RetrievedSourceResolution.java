package com.example.ragbilibili.service;

import java.util.List;
import java.util.Set;

public record RetrievedSourceResolution(
        List<RetrievedSourceCandidate> candidates,
        Set<String> authorizedUncitableVectorIds) {
}
