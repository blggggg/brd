package com.brd.reports;

import jakarta.servlet.http.HttpServletRequest;
import com.brd.ReportGeneric;
import com.brd.walmart.ReportCover;
import com.brd.walmart.Signatures;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import com.aspose.words.Document;
import com.aspose.words.FindReplaceOptions;
import com.aspose.words.License;
import com.aspose.words.SaveFormat;
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
public class ReportController extends ReportGeneric {
    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @Override
    public byte[] runTasks(long companyId, int reportFormatId, String reportNo) {
        var fields = new LinkedHashMap<String, String>();
        fields.putAll(new ReportCover().buildFields(companyId, "GENERIC_NEWARG_WMCA", reportNo));
        fields.putAll(new Signatures().buildFields());
        drawReportTable(fields);
        drawAdditionalDocuments(fields);
        return saveReport(fields);
    }

    private void drawReportTable(Map<String, String> fields) {
        fields.put("TEST_PROTOCOL", "N/A — protocol source data is not configured");
        fields.put("OVERALL_RATING", "N/A — rating source data is not configured");
        fields.put("FAILURE_SUMMARY", "N/A — failure source data is not configured");
    }

    private void drawAdditionalDocuments(Map<String, String> fields) {
        fields.put("ADDITIONAL_DOCUMENTS", "N/A");
    }

    private byte[] saveReport(Map<String, String> fields) {
        String resource = "/templates/walmart/GENERIC_NEWARG_WMCA.docx";
        try (InputStream input = getClass().getResourceAsStream(resource);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            if (input == null) throw new IllegalStateException("Walmart template not found: " + resource);
            applyAsposeLicense();
            Document document = new Document(input);
            FindReplaceOptions options = new FindReplaceOptions();
            for (var field : fields.entrySet()) {
                document.getRange().replace("{{" + field.getKey() + "}}", field.getValue(), options);
            }
            document.save(bytes, SaveFormat.DOCX);
            return bytes.toByteArray();
        } catch (Exception error) {
            throw new IllegalStateException("Walmart report template generation failed", error);
        }
    }

    private void applyAsposeLicense() throws Exception {
        String licensePath = System.getenv("BRD_ASPOSE_LICENSE");
        if (licensePath == null || licensePath.isBlank()) return;
        License license = new License();
        try (InputStream licenseFile = new FileInputStream(licensePath)) {
            license.setLicense(licenseFile);
        }
    }

    @GetMapping("/generate")
    public ResponseEntity<ByteArrayResource> generateReport(@RequestParam long companyId,
                                                            @RequestParam(defaultValue = "60") int reportFormatId,
                                                            @RequestParam String reportNo,
                                                            HttpServletRequest request) {
        authorizeAndValidate(companyId, reportFormatId, reportNo, request);
        String normalizedReportNo = reportNo.trim();
        byte[] docx = companyId == 2105259L
                ? runTasks(companyId, reportFormatId, normalizedReportNo)
                : reports.generateSimulated(companyId, reportFormatId, normalizedReportNo);
        String fileName = "BRD-" + companyId + "-" + normalizedReportNo.replaceAll("[^a-zA-Z0-9._-]", "_") + ".docx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .header("X-BRD-Report-Mode", companyId == 2105259L ? "TEMPLATE" : "SIMULATED")
                .body(new ByteArrayResource(docx));
    }

    private void authorizeAndValidate(long companyId, int reportFormatId, String reportNo, HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) request.getAttribute("account");
        if (user == null || "READER".equals(user.get("role"))) {
            throw new com.brd.Problem(403, "FORBIDDEN", "需要上传者或管理员权限");
        }
        if (companyId < 1 || reportFormatId < 0 || reportNo == null || reportNo.isBlank() || reportNo.length() > 100) {
            throw new com.brd.Problem(400, "INVALID_INPUT", "请提供有效的公司、报告格式和报告编号");
        }
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
        byte[] docx = companyId == 2105259L ? runTasks(companyId, reportFormatId, reportNo.trim())
                : reports.generateSimulated(companyId, reportFormatId, reportNo.trim());
        String fileName = "BRD-" + companyId + "-" + reportNo.trim().replaceAll("[^a-zA-Z0-9._-]", "_") + ".docx";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .header("X-BRD-Report-Mode", companyId == 2105259L ? "TEMPLATE" : "SIMULATED")
                .body(new ByteArrayResource(docx));
    }
}
