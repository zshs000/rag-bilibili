package com.example.ragbilibili.mapper;

import com.example.ragbilibili.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 消息 Mapper 接口
 */
@Mapper
public interface MessageMapper {
    /**
     * 根据会话ID查询消息列表
     */
    List<Message> selectBySessionId(@Param("sessionId") Long sessionId);

    /**
     * 插入消息
     */
    int insert(Message message);

    /**
     * 根据会话ID列表删除消息
     */
    int deleteBySessionIds(@Param("sessionIds") List<Long> sessionIds);

    /**
     * 根据会话ID删除消息
     */
    int deleteBySessionId(@Param("sessionId") Long sessionId);
}
