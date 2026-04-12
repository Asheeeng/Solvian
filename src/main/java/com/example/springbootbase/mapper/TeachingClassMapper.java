package com.example.springbootbase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.springbootbase.entity.TeachingClassEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 班级 Mapper。
 */
@Mapper
public interface TeachingClassMapper extends BaseMapper<TeachingClassEntity> {
}
