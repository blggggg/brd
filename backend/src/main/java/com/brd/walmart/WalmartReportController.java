package com.brd.walmart;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/walmart")
public class WalmartReportController {
    private final WalmartReportService reports;

    public WalmartReportController(WalmartReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/report-plan")
    public Map<String, Object> plan(@RequestParam long companyId,
                                    @RequestParam(defaultValue = "60") int reportFormatId,
                                    @RequestParam String reportNo,
                                    HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) request.getAttribute("account");
        if (user == null || "READER".equals(user.get("role"))) {
            throw new com.brd.Problem(403, "FORBIDDEN", "需要上传者或管理员权限");
        }
        if (companyId < 1 || reportFormatId < 0 || reportNo.isBlank() || reportNo.length() > 100) {
            throw new com.brd.Problem(400, "INVALID_INPUT", "请提供有效的公司、报告格式和报告编号");
        }
        return reports.plan(companyId, reportFormatId, reportNo.trim());
    }
}
