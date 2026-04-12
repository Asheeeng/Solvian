package com.example.springbootbase.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 作业提交输出结构。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionItemVO {
    private Long id;
    private Long studentId;
    private String studentUserId;
    private String studentName;
    private Long classId;
    private String fileName;
    private String checkStatus;
    private String checkResultJson;
    private String diagnosisRecordId;
    private Instant submitTime;
    private Instant checkedAt;
}
