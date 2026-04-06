package com.example.springbootbase.service.impl;

import com.example.springbootbase.dto.request.LogEventRequest;
import com.example.springbootbase.dto.response.EventLogResponse;
import com.example.springbootbase.entity.EventRecordEntity;
import com.example.springbootbase.mapper.EventRecordMapper;
import com.example.springbootbase.model.SessionInfo;
import com.example.springbootbase.service.EventLogService;
import com.example.springbootbase.util.IdUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * 事件日志服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventLogServiceImpl implements EventLogService {

    private final EventRecordMapper eventRecordMapper;
    private final ObjectMapper objectMapper;

    @Override
    public EventLogResponse log(LogEventRequest request, SessionInfo sessionInfo) {
        String eventId = IdUtil.newId();
        String eventName = (request.getEventType() == null || request.getEventType().isBlank())
                ? "unknown"
                : request.getEventType();

        EventRecordEntity record = EventRecordEntity.builder()
                .eventId(eventId)
                .userId(sessionInfo.getUserId())
                .username(sessionInfo.getUsername())
                .role(sessionInfo.getRole().name())
                .eventName(eventName)
                .eventType(request.getEventType())
                .page(request.getPage())
                .action(request.getAction())
                .recordId(request.getRecordId())
                .payloadJson(toJson(request.getPayload()))
                .createdAt(OffsetDateTime.now())
                .build();

        eventRecordMapper.insert(record);
        log.info("[event-log] user={}, eventType={}, action={}, recordId={}",
                record.getUsername(), record.getEventType(), record.getAction(), record.getRecordId());

        return EventLogResponse.builder()
                .success(true)
                .message("事件记录成功")
                .eventId(eventId)
                .build();
    }

    private String toJson(Object value) {
        try {
            if (value == null) {
                return "{}";
            }
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化事件 payload 失败", e);
        }
    }
}
