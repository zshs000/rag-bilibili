package com.example.ragbilibili.service;

import com.example.ragbilibili.dto.request.ImportVideoRequest;
import com.example.ragbilibili.dto.response.VideoResponse;
import java.util.List;

/**
 * 视频服务接口
 */
public interface VideoService {
    /**
     * 导入视频
     */
    VideoResponse importVideo(ImportVideoRequest request, Long userId);

    /**
     * 获取用户视频列表
     */
    List<VideoResponse> listVideos(Long userId);

    /**
     * 获取视频详情
     */
    VideoResponse getVideo(Long videoId, Long userId);

    /**
     * 删除视频（级联删除分片、向量、会话）
     */
    void deleteVideo(Long videoId, Long userId);
}
