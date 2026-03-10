package com.example.ragbilibili.mapper;

import com.example.ragbilibili.entity.Video;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 视频 Mapper 接口
 */
@Mapper
public interface VideoMapper {
    /**
     * 根据ID查询视频
     */
    Video selectById(@Param("id") Long id);

    /**
     * 根据用户ID和BV号查询视频
     */
    Video selectByUserIdAndBvid(@Param("userId") Long userId, @Param("bvid") String bvid);

    /**
     * 根据用户ID查询视频列表
     */
    List<Video> selectByUserId(@Param("userId") Long userId);

    /**
     * 插入视频
     */
    int insert(Video video);

    /**
     * 更新视频
     */
    int update(Video video);

    /**
     * 删除视频
     */
    int deleteById(@Param("id") Long id);
}
