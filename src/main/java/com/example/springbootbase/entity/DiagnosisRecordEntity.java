package com.example.springbootbase.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 诊断记录实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("diagnosis_record")
public class DiagnosisRecordEntity {

    @TableId
    private String recordId;

    private String userId;

    private String username;

    private String role;

    private String status;

    private String stepsJson;

    private String feedback;

    private Integer errorIndex;

    private String tagsJson;

    private Boolean isSocratic;

    private String problemType;

    private String imageName;

    private String aiFeedback;

    private String errorType;

    private String note;

    private String mathDataJson;

    private OffsetDateTime createdAt;
}

