package com.brd;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

@Component
public class ProjectDatabase {
    private final JdbcTemplate jdbc;
    ProjectDatabase(
            @Value("${brd.projects.datasource.url}") String url,
            @Value("${brd.projects.datasource.username}") String username,
            @Value("${brd.projects.datasource.password:}") String password) {
        var source=new DriverManagerDataSource();
        source.setDriverClassName("com.mysql.cj.jdbc.Driver");
        source.setUrl(url);
        source.setUsername(username);
        source.setPassword(password);
        this.jdbc=new JdbcTemplate(source);
    }
    List<Map<String,Object>> list(String sql,Object... args){return jdbc.queryForList(sql,args);}
    Map<String,Object> one(String sql,Object... args){var rows=list(sql,args);if(rows.isEmpty())throw new Problem(404,"NOT_FOUND","记录不存在");return rows.get(0);}
    int update(String sql,Object... args){return jdbc.update(sql,args);}
    long insert(String sql,Object... args){var key=new GeneratedKeyHolder();jdbc.update(c->{var s=c.prepareStatement(sql,new String[]{"id"});for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;},key);return java.util.Objects.requireNonNull(key.getKey()).longValue();}
    Long nextId(){return jdbc.queryForObject("SELECT COALESCE(MAX(PROPERTY_ID),0)+1 FROM misc_setup WHERE COMPANY_ID=0 AND SAMPLE_TYPE_ID=0 AND LOCATION_ID=0",Long.class);}
    void add(long id,String name){jdbc.update("INSERT INTO misc_setup(COMPANY_ID,SAMPLE_TYPE_ID,LOCATION_ID,PROPERTY_ID,PROPERTY_DESC,DATE_CREATED,ENABLE_DATE) VALUES(0,0,0,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,name);}
}
