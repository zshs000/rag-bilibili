package com.example.ragbilibili.mapper;

import com.example.ragbilibili.entity.VideoImportItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface VideoImportItemMapper {
    int insert(VideoImportItem item);

    VideoImportItem selectById(@Param("id") Long id);

    List<VideoImportItem> selectByBatchId(@Param("batchId") Long batchId);

    VideoImportItem selectNextQueued();

    int claim(@Param("id") Long id, @Param("startTime") LocalDateTime startTime);

    VideoImportItem selectActiveByUserIdAndBvid(@Param("userId") Long userId, @Param("bvid") String bvid);

    int markSucceeded(@Param("id") Long id, @Param("videoId") Long videoId,
                      @Param("finishTime") LocalDateTime finishTime);

    int markFailed(@Param("id") Long id, @Param("failReason") String failReason,
                   @Param("finishTime") LocalDateTime finishTime);

    int resetFailedByBatchId(@Param("batchId") Long batchId);

    int resetRunningToQueued();
}
