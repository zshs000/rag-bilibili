package com.example.ragbilibili.mapper;

import com.example.ragbilibili.entity.Session;
import com.example.ragbilibili.entity.SessionWithVideoTitle;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会话 Mapper 接口
 */
@Mapper
public interface SessionMapper {
    /**
     * 根据 ID 查询会话
     */
    Session selectById(@Param("id") Long id);

    /**
     * 根据用户 ID 查询会话列表
     */
    List<Session> selectByUserId(@Param("userId") Long userId);

    /**
     * 查询带视频标题的会话列表
     */
    List<SessionWithVideoTitle> selectWithVideoTitleByUserId(@Param("userId") Long userId);

    /**
     * 根据视频 ID 查询会话 ID 列表
     */
    List<Long> selectIdsByVideoId(@Param("videoId") Long videoId);

    /**
     * 插入会话
     */
    int insert(Session session);

    /**
     * 删除会话
     */
    int deleteById(@Param("id") Long id);

    /**
     * 根据视频 ID 删除会话
     */
    int deleteByVideoId(@Param("videoId") Long videoId);
}
