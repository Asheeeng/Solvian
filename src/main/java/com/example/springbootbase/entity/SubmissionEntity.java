package com.example.springbootbase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 学生作业提交实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("submissions")
public class SubmissionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long studentId;

    private String studentUserId;

    private String studentName;

    private String displayName;

    private Long classId;

    private String fileName;

    private String imagePath;

    private String contentType;

    private Long fileSize;

    private String checkStatus;

    private String checkResultJson;

    private String diagnosisRecordId;

    private OffsetDateTime submitTime;

    private OffsetDateTime checkedAt;
}
