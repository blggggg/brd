package com.brd;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tasks")
public class Tasks {
    private final TaskMapper tasks;
    private final ObjectMapper json;

    Tasks(TaskMapper tasks) {
        this.tasks = tasks;
        this.json = new ObjectMapper();
    }

    @GetMapping
    List<Map<String,Object>> list(@RequestParam long companyId, @RequestParam int reportFormatId, HttpServletRequest request) {
        requireAdmin(request);
        if (companyId < 1) throw new Problem(400, "INVALID_INPUT", "companyId 必须大于 0");
        if (reportFormatId < 0) throw new Problem(400, "INVALID_INPUT", "reportFormatId 不能小于 0");
        return tasks.list(companyId, reportFormatId);
    }

    @PostMapping
    Map<String,Object> create(@RequestBody Map<String,Object> body, HttpServletRequest request) {
        requireAdmin(request);
        long companyId = positive(body, "companyId");
        int reportFormatId = format(body);
        String className = Db.text(body, "className", 1, 255);
        int orderNo = order(body);
        boolean enabled = enabled(body);
        String fields = fields(body.get("otherFields"));
        String remark = optionalText(body.get("remark"), 500, "remark");
        Map<String,Object> row = row(companyId, reportFormatId, className, orderNo, enabled, fields, remark);
        tasks.insert(row);
        return tasks.one(((Number)row.get("id")).longValue());
    }

    @PutMapping("/{id}")
    Map<String,Object> update(@PathVariable long id, @RequestBody Map<String,Object> body, HttpServletRequest request) {
        requireAdmin(request);
        if (id < 1) throw new Problem(400, "INVALID_INPUT", "任务 ID 无效");
        long companyId = positive(body, "companyId");
        int reportFormatId = format(body);
        String className = Db.text(body, "className", 1, 255);
        int orderNo = order(body);
        boolean enabled = enabled(body);
        String fields = fields(body.get("otherFields"));
        String remark = optionalText(body.get("remark"), 500, "remark");
        Map<String,Object> row = row(companyId, reportFormatId, className, orderNo, enabled, fields, remark);
        row.put("id", id);
        int count = tasks.update(row);
        if (count == 0) throw new Problem(404, "NOT_FOUND", "任务配置不存在");
        return tasks.one(id);
    }

    @DeleteMapping("/{id}")
    Map<String,Object> delete(@PathVariable long id, HttpServletRequest request) {
        requireAdmin(request);
        if (id < 1) throw new Problem(400, "INVALID_INPUT", "任务 ID 无效");
        int count = tasks.delete(id);
        if (count == 0) throw new Problem(404, "NOT_FOUND", "任务配置不存在");
        return Map.of("deleted", true, "id", id);
    }

    private static void requireAdmin(HttpServletRequest request) {
        AccountGate.admin(AccountGate.account(request));
    }

    private static long positive(Map<String,Object> body, String key) {
        long value = Db.id(body, key);
        if (value < 1) throw new Problem(400, "INVALID_INPUT", key + " 必须大于 0");
        return value;
    }

    private static int order(Map<String,Object> body) {
        try {
            int value = Integer.parseInt(String.valueOf(body.get("orderNo")));
            if (value < 0) throw new NumberFormatException();
            return value;
        } catch (Exception error) {
            throw new Problem(400, "INVALID_INPUT", "orderNo 必须是大于等于 0 的整数");
        }
    }

    private static int format(Map<String,Object> body) {
        try {
            int value = Integer.parseInt(String.valueOf(body.get("reportFormatId")));
            if (value < 0) throw new NumberFormatException();
            return value;
        } catch (Exception error) {
            throw new Problem(400, "INVALID_INPUT", "reportFormatId 必须是大于等于 0 的整数");
        }
    }

    private static Map<String,Object> row(long companyId,int reportFormatId,String className,int orderNo,boolean enabled,String fields,String remark) {
        var row = new HashMap<String,Object>();
        row.put("companyId",companyId);row.put("reportFormatId",reportFormatId);row.put("className",className);
        row.put("orderNo",orderNo);row.put("enabled",enabled);row.put("otherFields",fields);row.put("remark",remark);
        return row;
    }

    private static boolean enabled(Map<String,Object> body) {
        Object value = body.get("enabled");
        if (value == null) return true;
        if (value instanceof Boolean flag) return flag;
        if ("true".equalsIgnoreCase(String.valueOf(value))) return true;
        if ("false".equalsIgnoreCase(String.valueOf(value))) return false;
        throw new Problem(400, "INVALID_INPUT", "enabled 必须是布尔值");
    }

    private String fields(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return "{}";
        String raw;
        try {
            raw = value instanceof String s ? s : json.writeValueAsString(value);
            if (!json.readTree(raw).isObject()) throw new IllegalArgumentException();
        } catch (Exception error) {
            throw new Problem(400, "INVALID_INPUT", "其他字段必须是有效的 JSON 对象");
        }
        if (raw.length() > 60000) throw new Problem(400, "INVALID_INPUT", "其他字段最多 60000 个字符");
        return raw;
    }

    private static String optionalText(Object value, int max, String key) {
        String text = value == null ? "" : value.toString().trim();
        if (text.length() > max) throw new Problem(400, "INVALID_INPUT", key + " 最多 " + max + " 个字符");
        return text;
    }
}
