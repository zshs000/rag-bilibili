package com.example.ragbilibili.mapper;

import com.example.ragbilibili.entity.VideoImportBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VideoImportBatchMapper {
    int insert(VideoImportBatch batch);

    VideoImportBatch selectById(@Param("id") Long id);

    VideoImportBatch selectByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    List<VideoImportBatch> selectRecentByUserId(@Param("userId") Long userId);

    int refreshSummary(@Param("id") Long id);

    int clearCredentials(@Param("id") Long id);
}
