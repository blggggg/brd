package com.brd.walmart;

import com.brd.TaskMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Walmart-specific report planning. Rendering remains delegated to the report engine. */
@Service
public class WalmartReportService {
    private final TaskMapper tasks;

    public WalmartReportService(TaskMapper tasks) {
        this.tasks = tasks;
    }

    public Map<String, Object> plan(long companyId, int reportFormatId, String reportNo) {
        List<Map<String, Object>> configured = tasks.list(companyId, reportFormatId).stream()
                .filter(row -> enabled(row.get("enabled")))
                .toList();
        return Map.of(
                "companyId", companyId,
                "reportFormatId", reportFormatId,
                "reportNo", reportNo,
                "engineReady", false,
                "tasks", configured,
                "reason", "BRD 已维护 Walmart 任务配置，但尚未接入 WMCA 报告引擎、Word 模板及报告业务数据 Mapper。"
        );
    }

    private boolean enabled(Object value) {
        return value instanceof Boolean flag ? flag : value instanceof Number number ? number.intValue() != 0 : "1".equals(String.valueOf(value));
    }
}
