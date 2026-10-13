package com.brd;

import java.sql.Statement;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

@Component
public class Db {
    final JdbcTemplate jdbc;
    public Db(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<Map<String,Object>> list(String sql, Object... args) { return jdbc.queryForList(sql,args); }
    public Map<String,Object> one(String sql, Object... args) {
        var rows=list(sql,args); if(rows.isEmpty()) throw new Problem(404,"NOT_FOUND","记录不存在"); return rows.get(0);
    }
    public int update(String sql,Object... args) { return jdbc.update(sql,args); }
    public long insert(String sql,Object... args) {
        var key=new GeneratedKeyHolder();
        jdbc.update(c->{var s=c.prepareStatement(sql,new String[]{"id"}); for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;},key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    static long num(Object value) { return ((Number)value).longValue(); }
    static boolean bool(Object value) { return value instanceof Boolean ? (Boolean)value : num(value)!=0; }
    static String text(Map<String,?> input,String key,int min,int max) {
        Object raw=input.get(key); String val=raw==null?"":raw.toString().trim();
        if(val.length()<min||val.length()>max)throw new Problem(400,"INVALID_INPUT",key+" 长度应为 "+min+" 至 "+max);
        return val;
    }
    static long id(Map<String,?> input,String key) {
        try { return Long.parseLong(String.valueOf(input.get(key))); } catch(Exception ex){throw new Problem(400,"INVALID_INPUT",key+" 格式错误");}
    }
    static Map<String,Object> publicRow(Map<String,Object> row) {
        var copy=new LinkedHashMap<String,Object>(); row.forEach((k,v)->copy.put(k,v instanceof Long ? v.toString() : v));return copy;
    }
    static List<Map<String,Object>> publicRows(List<Map<String,Object>> rows) { return rows.stream().map(Db::publicRow).toList(); }
    void log(long actor,String action,Long doc,Long version,Long from,String reason) {
        update("INSERT INTO operation_log(actor_id,action,document_id,version_id,from_version_id,reason) VALUES(?,?,?,?,?,?)",actor,action,doc,version,from,reason);
    }
}
