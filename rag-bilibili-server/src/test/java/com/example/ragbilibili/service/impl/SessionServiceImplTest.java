package com.example.ragbilibili.service.impl;

import com.example.ragbilibili.dto.response.SessionResponse;
import com.example.ragbilibili.entity.SessionWithVideoTitle;
import com.example.ragbilibili.mapper.MessageMapper;
import com.example.ragbilibili.mapper.SessionMapper;
import com.example.ragbilibili.mapper.VideoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceImplTest {

    @Mock
    private SessionMapper sessionMapper;

    @Mock
    private VideoMapper videoMapper;

    @Mock
    private MessageMapper messageMapper;

    @InjectMocks
    private SessionServiceImpl sessionService;

    @Test
    void listSessionsShouldUseJoinedVideoTitleWithoutExtraVideoLookup() {
        SessionWithVideoTitle singleVideoSession = new SessionWithVideoTitle();
        singleVideoSession.setId(1L);
        singleVideoSession.setUserId(1L);
        singleVideoSession.setSessionType("SINGLE_VIDEO");
        singleVideoSession.setVideoId(101L);
        singleVideoSession.setVideoTitle("测试视频");
        singleVideoSession.setCreateTime(LocalDateTime.of(2026, 4, 14, 10, 0, 0));

        SessionWithVideoTitle allVideosSession = new SessionWithVideoTitle();
        allVideosSession.setId(2L);
        allVideosSession.setUserId(1L);
        allVideosSession.setSessionType("ALL_VIDEOS");
        allVideosSession.setCreateTime(LocalDateTime.of(2026, 4, 14, 9, 0, 0));

        when(sessionMapper.selectWithVideoTitleByUserId(1L))
                .thenReturn(List.of(singleVideoSession, allVideosSession));

        List<SessionResponse> responses = sessionService.listSessions(1L);

        assertEquals(2, responses.size());
        assertEquals("测试视频", responses.get(0).getVideoTitle());
        assertEquals("ALL_VIDEOS", responses.get(1).getSessionType());
        verify(sessionMapper).selectWithVideoTitleByUserId(1L);
        verifyNoInteractions(videoMapper);
    }
}
