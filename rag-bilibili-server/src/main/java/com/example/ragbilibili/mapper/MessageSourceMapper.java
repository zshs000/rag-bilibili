package com.example.ragbilibili.mapper;

import com.example.ragbilibili.entity.MessageSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MessageSourceMapper {
    int batchInsert(@Param("sources") List<MessageSource> sources);

    List<MessageSource> selectByMessageIds(@Param("messageIds") List<Long> messageIds);

    int deleteBySessionId(@Param("sessionId") Long sessionId);

    int deleteBySessionIds(@Param("sessionIds") List<Long> sessionIds);

    int deleteByVideoIdSessions(@Param("videoId") Long videoId);
}
