package com.example.springbootbase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.springbootbase.entity.SessionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话 Mapper。
 */
@Mapper
public interface SessionMapper extends BaseMapper<SessionEntity> {
}

