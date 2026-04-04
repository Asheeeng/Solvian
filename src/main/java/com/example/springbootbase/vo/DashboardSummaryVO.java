package com.example.springbootbase.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 仪表盘统计输出结构。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryVO {
    private long totalEvaluations;
    private Map<String, Long> errorTypeCount;
    private Map<String, Long> aiFeedbackCount;
    private List<HistoryItemVO> recentRecords;
}
