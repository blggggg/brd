package com.brd;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
class TaskMyBatisConfiguration {
    @Bean("taskSqlSessionFactory")
    SqlSessionFactory taskSqlSessionFactory(
            @Value("${brd.projects.datasource.url}") String url,
            @Value("${brd.projects.datasource.username}") String username,
            @Value("${brd.projects.datasource.password:}") String password) throws Exception {
        var source = new DriverManagerDataSource();
        source.setDriverClassName("com.mysql.cj.jdbc.Driver");
        source.setUrl(url);
        source.setUsername(username);
        source.setPassword(password);
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(source);
        SqlSessionFactory sqlSessionFactory = factory.getObject();
        sqlSessionFactory.getConfiguration().addMapper(TaskMapper.class);
        return sqlSessionFactory;
    }

    @Bean("taskSqlSessionTemplate")
    SqlSessionTemplate taskSqlSessionTemplate(@Qualifier("taskSqlSessionFactory") SqlSessionFactory factory) {
        return new SqlSessionTemplate(factory);
    }

    @Bean
    TaskMapper taskMapper(@Qualifier("taskSqlSessionTemplate") SqlSessionTemplate template) {
        return template.getMapper(TaskMapper.class);
    }
}
