package com.example.springbootbase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.springbootbase.entity.DiagnosisTaskEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 诊断任务 Mapper。
 */
@Mapper
public interface DiagnosisTaskMapper extends BaseMapper<DiagnosisTaskEntity> {
}
