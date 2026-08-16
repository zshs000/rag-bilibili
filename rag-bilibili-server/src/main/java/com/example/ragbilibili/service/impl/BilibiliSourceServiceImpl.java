package com.example.ragbilibili.service.impl;

import com.example.ragbilibili.dto.request.BilibiliFavoritePageRequest;
import com.example.ragbilibili.dto.request.BilibiliSourceCredentialsRequest;
import com.example.ragbilibili.dto.request.BilibiliUpVideoPageRequest;
import com.example.ragbilibili.dto.response.BilibiliFavoriteFolderResponse;
import com.example.ragbilibili.dto.response.BilibiliSourceVideoResponse;
import com.example.ragbilibili.dto.response.BilibiliVideoPageResponse;
import com.example.ragbilibili.exception.BusinessException;
import com.example.ragbilibili.exception.ErrorCode;
import com.example.ragbilibili.integration.bilibili.BilibiliApiClient;
import com.example.ragbilibili.integration.bilibili.BilibiliRequestCredentials;
import com.example.ragbilibili.integration.bilibili.BilibiliSourceVideo;
import com.example.ragbilibili.integration.bilibili.BilibiliSourceVideoPage;
import com.example.ragbilibili.integration.bilibili.BilibiliUpParser;
import com.example.ragbilibili.service.BilibiliSourceService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BilibiliSourceServiceImpl implements BilibiliSourceService {
    private final BilibiliApiClient client;

    public BilibiliSourceServiceImpl(BilibiliApiClient client) {
        this.client = client;
    }

    @Override
    public List<BilibiliFavoriteFolderResponse> listFavoriteFolders(BilibiliSourceCredentialsRequest request) {
        return client.getFavoriteFolders(credentials(request)).stream()
                .map(folder -> new BilibiliFavoriteFolderResponse(
                        folder.id(), folder.title(), folder.mediaCount(), folder.privateFolder()))
                .toList();
    }

    @Override
    public BilibiliVideoPageResponse listFavoriteVideos(long folderId, BilibiliFavoritePageRequest request) {
        if (folderId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        return toResponse(client.getFavoriteVideos(
                folderId, request.getPage(), request.getPageSize(), credentials(request)));
    }

    @Override
    public BilibiliVideoPageResponse listUpVideos(BilibiliUpVideoPageRequest request) {
        long mid;
        try {
            mid = BilibiliUpParser.parseMid(request.getUp());
        }
        catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), ex.getMessage());
        }
        BilibiliRequestCredentials credentials = request.isUseCredentials()
                ? requiredCredentials(request.getSessdata(), request.getBiliJct(), request.getBuvid3())
                : null;
        return toResponse(client.getUpVideos(mid, request.getPage(), request.getPageSize(), credentials));
    }

    private BilibiliRequestCredentials credentials(BilibiliSourceCredentialsRequest request) {
        return new BilibiliRequestCredentials(request.getSessdata(), request.getBiliJct(), request.getBuvid3());
    }

    private BilibiliRequestCredentials requiredCredentials(String sessdata, String biliJct, String buvid3) {
        if (!hasText(sessdata) || !hasText(biliJct) || !hasText(buvid3)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "登录模式需要完整填写三项B站凭证");
        }
        return new BilibiliRequestCredentials(sessdata.trim(), biliJct.trim(), buvid3.trim());
    }

    private BilibiliVideoPageResponse toResponse(BilibiliSourceVideoPage page) {
        return new BilibiliVideoPageResponse(
                page.page(), page.pageSize(), page.total(), page.hasMore(),
                page.items().stream().map(this::toVideoResponse).toList());
    }

    private BilibiliSourceVideoResponse toVideoResponse(BilibiliSourceVideo video) {
        return new BilibiliSourceVideoResponse(
                video.bvid(), video.title(), video.coverUrl(), video.durationSeconds(),
                video.ownerMid(), video.ownerName(), video.publishTime(), video.unavailable());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
