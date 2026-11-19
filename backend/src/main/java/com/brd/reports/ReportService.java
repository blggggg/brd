package com.brd.reports;

import com.brd.TaskMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import com.brd.CompanyMapper;
import com.brd.Problem;

/** Company-specific report planning. Rendering remains delegated to the report engine. */
@Service
public class ReportService {
    private final TaskMapper tasks;
    private final CompanyMapper companies;

    public ReportService(TaskMapper tasks, CompanyMapper companies) {
        this.tasks = tasks;
        this.companies = companies;
    }

    public byte[] generateSimulated(long companyId, int reportFormatId, String reportNo) {
        var company = companies.listEnabled().stream()
                .filter(row -> ((Number) row.get("id")).longValue() == companyId)
                .findFirst().orElseThrow(() -> new Problem(404, "COMPANY_NOT_FOUND", "公司不存在或已停用"));
        var configured = tasks.list(companyId, reportFormatId).stream()
                .filter(row -> enabled(row.get("enabled"))).toList();
        if (configured.isEmpty()) throw new Problem(409, "NO_REPORT_TASKS", "请先运行自测数据初始化，再生成模拟报告");
        String companyName = String.valueOf(company.get("company_name"));
        String title = companyName + " 模拟报告";
        var paragraphs = new java.util.ArrayList<String>();
        paragraphs.add("模拟数据自测报告 · 非正式业务报告");
        paragraphs.add("公司：" + companyName + "（" + companyId + "）");
        paragraphs.add("报告格式：" + reportFormatId);
        paragraphs.add("报告编号／主样品编号：" + reportNo);
        paragraphs.add("生成时间：" + OffsetDateTime.now());
        paragraphs.add("");
        paragraphs.add("一、样品与协议摘要（模拟）");
        if (companyId == 2105259L) {
            paragraphs.add("样品：" + reportNo + "；来源：Walmart Canada 自测样品");
            paragraphs.add("协议：纺织品成分与标签检查；顺序 1");
            paragraphs.add("协议：化学品限制筛查；顺序 2");
            paragraphs.add("结果：模拟数据已载入；正式协议与检测数据需连接业务数据库。");
        } else if (companyId == 2004550L) {
            paragraphs.add("样品：" + reportNo + "；品牌：Carter's；品类：儿童服装");
            paragraphs.add("测试项目：纤维成分、耐洗色牢度、标签核验（均为模拟项目）");
            paragraphs.add("结果：模拟数据已载入；正式检测结果需连接业务数据库。");
        } else {
            paragraphs.add("样品：" + reportNo + "；当前公司没有专属模拟样例，使用通用演示内容。");
        }
        paragraphs.add("");
        paragraphs.add("二、已配置任务顺序");
        int sequence = 1;
        for (Map<String, Object> task : configured) {
            paragraphs.add(sequence++ + ". " + task.get("class_name") + "（Order No. " + task.get("order_no") + "）");
        }
        paragraphs.add("");
        paragraphs.add("本文件仅用于部署和接口自测。它没有调用真实报告任务，也没有使用正式报告模板或真实业务数据，不得作为检测报告对外使用。");
        try {
            return docx(title, paragraphs);
        } catch (Exception e) {
            throw new IllegalStateException("模拟报告生成失败", e);
        }
    }

    private byte[] docx(String title, List<String> paragraphs) throws Exception {
        StringBuilder body = new StringBuilder();
        body.append(paragraph(title, "Title"));
        for (String text : paragraphs) body.append(paragraph(text, text.startsWith("一、") || text.startsWith("二、") ? "Heading1" : null));
        body.append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/></w:sectPr>");
        String document = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>" + body + "</w:body></w:document>";
        try (var bytes = new ByteArrayOutputStream(); var zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            entry(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>");
            entry(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>");
            entry(zip, "word/document.xml", document);
            zip.finish();
            return bytes.toByteArray();
        }
    }

    private String paragraph(String text, String style) {
        return "<w:p>" + (style == null ? "" : "<w:pPr><w:pStyle w:val=\"" + style + "\"/></w:pPr>") + "<w:r><w:t xml:space=\"preserve\">" + xml(text) + "</w:t></w:r></w:p>";
    }

    private String xml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private void entry(ZipOutputStream zip, String name, String text) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
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
                "reason", "BRD 已维护该公司的任务配置，但尚未接入对应报告引擎、Word 模板及报告业务数据 Mapper。"
        );
    }

    public Map<String, Object> seedSelfTestTasks() {
        var seeded = new java.util.ArrayList<Map<String, Object>>();
        seedCompanyTasks(2105259L, 60, "Walmart Canada", List.of(
                new DemoTask("SELF_TEST_WALMART_PROTOCOL", 10, "{\"sampleNo\":\"52262240103\",\"protocol\":\"Textile composition and labeling\"}"),
                new DemoTask("SELF_TEST_WALMART_CHEMICAL", 20, "{\"sampleNo\":\"52262240103\",\"protocol\":\"Restricted substances screening\"}")), seeded);
        seedCompanyTasks(2004550L, 60, "Carter's", List.of(
                new DemoTask("SELF_TEST_CARTERS_SAMPLE", 10, "{\"sampleNo\":\"52262730004\",\"brand\":\"Carter's\",\"category\":\"Children's apparel\"}"),
                new DemoTask("SELF_TEST_CARTERS_RESULTS", 20, "{\"sampleNo\":\"52262730004\",\"tests\":[\"Fiber composition\",\"Colorfastness\",\"Label verification\"]}")), seeded);
        return Map.of("seededCount", seeded.size(), "tasks", seeded,
                "notice", "仅插入缺失的模拟自测任务；这些任务不会运行真实报告引擎。");
    }

    private record DemoTask(String className, int orderNo, String fields) {}

    private void seedCompanyTasks(long companyId, int format, String companyName, List<DemoTask> demoTasks,
                                  List<Map<String, Object>> seeded) {
        var company = companies.listEnabled().stream()
                .filter(row -> ((Number) row.get("id")).longValue() == companyId).findFirst();
        if (company.isEmpty()) return;
        var existing = tasks.list(companyId, format);
        for (DemoTask demo : demoTasks) {
            boolean present = existing.stream().anyMatch(row -> demo.className().equals(row.get("class_name")));
            if (present) continue;
            var row = new java.util.HashMap<String, Object>();
            row.put("companyId", companyId); row.put("reportFormatId", format);
            row.put("className", demo.className()); row.put("orderNo", demo.orderNo());
            row.put("enabled", true); row.put("otherFields", demo.fields());
            row.put("remark", "模拟自测配置（" + companyName + "）；非正式报告任务");
            tasks.insert(row);
            seeded.add(Map.of("companyId", companyId, "className", demo.className(), "orderNo", demo.orderNo()));
        }
    }

    private boolean enabled(Object value) {
        return value instanceof Boolean flag ? flag : value instanceof Number number ? number.intValue() != 0 : "1".equals(String.valueOf(value));
    }
}
