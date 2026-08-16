package com.example.ragbilibili.service;

import com.example.ragbilibili.dto.request.BilibiliFavoritePageRequest;
import com.example.ragbilibili.dto.request.BilibiliSourceCredentialsRequest;
import com.example.ragbilibili.dto.request.BilibiliUpVideoPageRequest;
import com.example.ragbilibili.dto.response.BilibiliFavoriteFolderResponse;
import com.example.ragbilibili.dto.response.BilibiliVideoPageResponse;

import java.util.List;

public interface BilibiliSourceService {
    List<BilibiliFavoriteFolderResponse> listFavoriteFolders(BilibiliSourceCredentialsRequest request);

    BilibiliVideoPageResponse listFavoriteVideos(long folderId, BilibiliFavoritePageRequest request);

    BilibiliVideoPageResponse listUpVideos(BilibiliUpVideoPageRequest request);
}
