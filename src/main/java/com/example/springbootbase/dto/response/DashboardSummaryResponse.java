package com.example.springbootbase.dto.response;

import com.example.springbootbase.vo.DashboardSummaryVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 仪表盘摘要响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryResponse {
    private DashboardSummaryVO summary;
}
