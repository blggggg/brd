package com.brd;

import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class BrdMyBatisConfiguration {
    @Bean("brdSqlSessionFactory")
    SqlSessionFactory brdSqlSessionFactory(DataSource dataSource) throws Exception {
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        SqlSessionFactory result = factory.getObject();
        result.getConfiguration().addMapper(BrdMapper.class);
        return result;
    }

    @Bean("brdSqlSessionTemplate")
    SqlSessionTemplate brdSqlSessionTemplate(@Qualifier("brdSqlSessionFactory") SqlSessionFactory factory) {
        return new SqlSessionTemplate(factory);
    }

    @Bean
    BrdMapper brdMapper(@Qualifier("brdSqlSessionTemplate") SqlSessionTemplate template) {
        return template.getMapper(BrdMapper.class);
    }
}
