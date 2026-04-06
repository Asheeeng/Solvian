package com.example.springbootbase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.springbootbase.entity.DiagnosisRecordEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 诊断记录 Mapper。
 */
@Mapper
public interface DiagnosisRecordMapper extends BaseMapper<DiagnosisRecordEntity> {
}

