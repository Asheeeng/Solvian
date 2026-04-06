package com.example.springbootbase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.springbootbase.entity.AiFeedbackRecordEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 反馈记录 Mapper。
 */
@Mapper
public interface AiFeedbackRecordMapper extends BaseMapper<AiFeedbackRecordEntity> {
}

