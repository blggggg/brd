package com.brd.walmart;

import java.util.Map;

public class ReportCover {
    public Map<String, String> buildFields(long companyId, String templateName, String reportNo) {
        return Map.of(
                "COMPANY_ID", Long.toString(companyId),
                "COMPANY_NAME", "Walmart Canada",
                "TEMPLATE_NAME", templateName,
                "REPORT_NO", reportNo,
                "MASTER_SAMPLE_NO", reportNo
        );
    }
}
