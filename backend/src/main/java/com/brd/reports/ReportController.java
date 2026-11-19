package com.brd.reports;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final ReportService reports;

    public ReportController(ReportService reports) {
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

    @PostMapping("/self-test/seed")
    @Transactional
    public Map<String, Object> seed(HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) request.getAttribute("account");
        if (user == null || !"ADMIN".equals(user.get("role"))) {
            throw new com.brd.Problem(403, "FORBIDDEN", "需要管理员权限");
        }
        return reports.seedSelfTestTasks();
    }

    @GetMapping("/self-test/generate")
    public ResponseEntity<ByteArrayResource> generate(@RequestParam long companyId,
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
        byte[] docx = reports.generateSimulated(companyId, reportFormatId, reportNo.trim());
        String fileName = "BRD-SIMULATED-" + companyId + "-" + reportNo.trim().replaceAll("[^a-zA-Z0-9._-]", "_") + ".docx";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .header("X-BRD-Report-Mode", "SIMULATED")
                .body(new ByteArrayResource(docx));
    }
}
