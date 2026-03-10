package com.example.ragbilibili.mapper;

import com.example.ragbilibili.entity.Chunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 分片 Mapper 接口
 */
@Mapper
public interface ChunkMapper {
    /**
     * 根据ID查询分片
     */
    Chunk selectById(@Param("id") Long id);

    /**
     * 根据视频ID查询分片列表
     */
    List<Chunk> selectByVideoId(@Param("videoId") Long videoId);

    /**
     * 统计视频分片数量
     */
    int countByVideoId(@Param("videoId") Long videoId);

    /**
     * 批量插入分片
     */
    int batchInsert(@Param("chunks") List<Chunk> chunks);

    /**
     * 根据视频ID删除分片
     */
    int deleteByVideoId(@Param("videoId") Long videoId);
}
