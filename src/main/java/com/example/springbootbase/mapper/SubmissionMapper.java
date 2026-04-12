package com.example.springbootbase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.springbootbase.entity.SubmissionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学生提交 Mapper。
 */
@Mapper
public interface SubmissionMapper extends BaseMapper<SubmissionEntity> {
}
