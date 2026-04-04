package com.example.springbootbase.service.impl;

import com.example.springbootbase.dto.request.LogEventRequest;
import com.example.springbootbase.dto.response.EventLogResponse;
import com.example.springbootbase.model.EventRecord;
import com.example.springbootbase.model.SessionInfo;
import com.example.springbootbase.service.EventLogService;
import com.example.springbootbase.store.InMemoryDataStore;
import com.example.springbootbase.util.IdUtil;
import com.example.springbootbase.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 事件日志服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventLogServiceImpl implements EventLogService {

    private final InMemoryDataStore store;

    @Override
    public EventLogResponse log(LogEventRequest request, SessionInfo sessionInfo) {
        String eventId = IdUtil.newId();

        EventRecord record = EventRecord.builder()
                .eventId(eventId)
                .userId(sessionInfo.getUserId())
                .username(sessionInfo.getUsername())
                .eventType(request.getEventType())
                .page(request.getPage())
                .action(request.getAction())
                .payload(request.getPayload())
                .recordId(request.getRecordId())
                .createdAt(TimeUtil.now())
                .build();

        store.getEventRecords().add(record);
        log.info("[event-log] user={}, eventType={}, action={}, recordId={}",
                record.getUsername(), record.getEventType(), record.getAction(), record.getRecordId());

        return EventLogResponse.builder()
                .success(true)
                .message("事件记录成功")
                .eventId(eventId)
                .build();
    }
}
