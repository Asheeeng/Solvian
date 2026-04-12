package com.example.springbootbase.dto.response;

import com.example.springbootbase.vo.SubmissionItemVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 提交列表响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionListResponse {
    private long total;
    private List<SubmissionItemVO> list;
}
