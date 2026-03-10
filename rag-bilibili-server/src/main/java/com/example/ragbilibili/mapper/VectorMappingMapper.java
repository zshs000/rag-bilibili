package com.example.ragbilibili.mapper;

import com.example.ragbilibili.entity.VectorMapping;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 向量映射 Mapper 接口
 */
@Mapper
public interface VectorMappingMapper {
    /**
     * 根据视频ID查询向量ID列表
     */
    List<String> selectVectorIdsByVideoId(@Param("videoId") Long videoId);

    /**
     * 批量插入向量映射
     */
    int batchInsert(@Param("mappings") List<VectorMapping> mappings);

    /**
     * 根据视频ID删除映射
     */
    int deleteByVideoId(@Param("videoId") Long videoId);
}
