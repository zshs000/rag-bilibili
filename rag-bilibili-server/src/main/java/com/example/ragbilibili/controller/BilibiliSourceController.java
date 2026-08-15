package com.example.ragbilibili.controller;

import com.example.ragbilibili.common.Result;
import com.example.ragbilibili.dto.request.BilibiliFavoritePageRequest;
import com.example.ragbilibili.dto.request.BilibiliSourceCredentialsRequest;
import com.example.ragbilibili.dto.request.BilibiliUpVideoPageRequest;
import com.example.ragbilibili.dto.response.BilibiliFavoriteFolderResponse;
import com.example.ragbilibili.dto.response.BilibiliVideoPageResponse;
import com.example.ragbilibili.service.BilibiliSourceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bilibili-sources")
public class BilibiliSourceController {
    private final BilibiliSourceService service;

    public BilibiliSourceController(BilibiliSourceService service) {
        this.service = service;
    }

    @PostMapping("/favorite-folders")
    public Result<List<BilibiliFavoriteFolderResponse>> listFavoriteFolders(
            @Valid @RequestBody BilibiliSourceCredentialsRequest request) {
        return Result.success(service.listFavoriteFolders(request));
    }

    @PostMapping("/favorite-folders/{folderId}/videos")
    public Result<BilibiliVideoPageResponse> listFavoriteVideos(
            @PathVariable long folderId,
            @Valid @RequestBody BilibiliFavoritePageRequest request) {
        return Result.success(service.listFavoriteVideos(folderId, request));
    }

    @PostMapping("/up-videos")
    public Result<BilibiliVideoPageResponse> listUpVideos(
            @Valid @RequestBody BilibiliUpVideoPageRequest request) {
        return Result.success(service.listUpVideos(request));
    }
}
