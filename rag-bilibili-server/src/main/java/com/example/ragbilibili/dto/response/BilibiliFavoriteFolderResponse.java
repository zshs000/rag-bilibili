package com.example.ragbilibili.dto.response;

public record BilibiliFavoriteFolderResponse(
        long id,
        String title,
        int mediaCount,
        boolean privateFolder) {
}
