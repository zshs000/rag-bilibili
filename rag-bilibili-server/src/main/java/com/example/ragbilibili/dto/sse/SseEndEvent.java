package com.example.ragbilibili.dto.sse;

import lombok.Data;
import lombok.NoArgsConstructor;

import com.example.ragbilibili.dto.response.MessageSourceResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * SSE End事件
 */
@Data
@NoArgsConstructor
public class SseEndEvent {
    private String type = "end";
    private Long assistantMessageId;
    private String fullContent;
    private List<MessageSourceResponse> sources = new ArrayList<>();

    public SseEndEvent(Long assistantMessageId, String fullContent) {
        this.assistantMessageId = assistantMessageId;
        this.fullContent = fullContent;
    }

    public SseEndEvent(Long assistantMessageId, String fullContent, List<MessageSourceResponse> sources) {
        this.assistantMessageId = assistantMessageId;
        this.fullContent = fullContent;
        this.sources = sources == null ? new ArrayList<>() : sources;
    }
}
