package com.example.springbootbase.dto.response;

import com.example.springbootbase.vo.HistoryItemVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 历史记录响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryResponse {
    private long total;
    private List<HistoryItemVO> list;
}
