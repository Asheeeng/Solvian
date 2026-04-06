package com.example.springbootbase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.springbootbase.entity.EventRecordEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 事件日志 Mapper。
 */
@Mapper
public interface EventRecordMapper extends BaseMapper<EventRecordEntity> {
}

