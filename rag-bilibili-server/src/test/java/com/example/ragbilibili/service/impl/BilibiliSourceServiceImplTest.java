package com.example.ragbilibili.service.impl;

import com.example.ragbilibili.dto.request.BilibiliFavoritePageRequest;
import com.example.ragbilibili.dto.request.BilibiliSourceCredentialsRequest;
import com.example.ragbilibili.dto.request.BilibiliUpVideoPageRequest;
import com.example.ragbilibili.exception.BusinessException;
import com.example.ragbilibili.integration.bilibili.BilibiliApiClient;
import com.example.ragbilibili.integration.bilibili.BilibiliFavoriteFolder;
import com.example.ragbilibili.integration.bilibili.BilibiliRequestCredentials;
import com.example.ragbilibili.integration.bilibili.BilibiliSourceVideoPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BilibiliSourceServiceImplTest {
    @Mock private BilibiliApiClient client;

    @Test
    void mapsFavoriteFoldersAndCredentials() {
        when(client.getFavoriteFolders(any())).thenReturn(List.of(
                new BilibiliFavoriteFolder(10L, "默认收藏夹", 170, false)));
        BilibiliSourceServiceImpl service = new BilibiliSourceServiceImpl(client);

        var result = service.listFavoriteFolders(credentials());

        assertEquals("默认收藏夹", result.get(0).title());
        ArgumentCaptor<BilibiliRequestCredentials> captor = ArgumentCaptor.forClass(BilibiliRequestCredentials.class);
        verify(client).getFavoriteFolders(captor.capture());
        assertEquals("sess", captor.getValue().sessdata());
    }

    @Test
    void anonymousUpModeDoesNotForwardResidualCredentials() {
        when(client.getUpVideos(anyLong(), anyInt(), anyInt(), any())).thenReturn(
                new BilibiliSourceVideoPage(1, 20, 0, false, List.of()));
        BilibiliSourceServiceImpl service = new BilibiliSourceServiceImpl(client);
        BilibiliUpVideoPageRequest request = new BilibiliUpVideoPageRequest();
        request.setUp("https://space.bilibili.com/1045711541");
        request.setUseCredentials(false);
        request.setSessdata("residual");

        service.listUpVideos(request);

        ArgumentCaptor<BilibiliRequestCredentials> captor = ArgumentCaptor.forClass(BilibiliRequestCredentials.class);
        verify(client).getUpVideos(org.mockito.ArgumentMatchers.eq(1045711541L),
                org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq(20), captor.capture());
        assertNull(captor.getValue());
    }

    @Test
    void rejectsMissingCredentialsInAuthenticatedUpMode() {
        BilibiliSourceServiceImpl service = new BilibiliSourceServiceImpl(client);
        BilibiliUpVideoPageRequest request = new BilibiliUpVideoPageRequest();
        request.setUp("1045711541");
        request.setUseCredentials(true);

        BusinessException error = assertThrows(BusinessException.class, () -> service.listUpVideos(request));
        assertEquals(400, error.getCode());
    }

    private BilibiliSourceCredentialsRequest credentials() {
        BilibiliSourceCredentialsRequest request = new BilibiliSourceCredentialsRequest();
        request.setSessdata("sess");
        request.setBiliJct("csrf");
        request.setBuvid3("buvid");
        return request;
    }
}
