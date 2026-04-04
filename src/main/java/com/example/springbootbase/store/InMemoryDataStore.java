package com.example.springbootbase.store;

import com.example.springbootbase.model.AiFeedbackRecord;
import com.example.springbootbase.model.DiagnosisRecord;
import com.example.springbootbase.model.EventRecord;
import com.example.springbootbase.model.SessionInfo;
import com.example.springbootbase.model.UserAccount;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 内存存储。
 */
@Getter
@Component
public class InMemoryDataStore {

    private final Map<String, UserAccount> usersByUsername = new ConcurrentHashMap<>();
    private final Map<String, SessionInfo> sessionsByToken = new ConcurrentHashMap<>();

    private final Map<String, DiagnosisRecord> diagnosisById = new ConcurrentHashMap<>();
    private final List<String> diagnosisOrder = new CopyOnWriteArrayList<>();

    private final List<EventRecord> eventRecords = new CopyOnWriteArrayList<>();
    private final List<AiFeedbackRecord> aiFeedbackRecords = new CopyOnWriteArrayList<>();
}
