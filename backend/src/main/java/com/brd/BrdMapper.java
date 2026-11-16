package com.brd;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.UpdateProvider;

interface BrdMapper {
    @SelectProvider(type=BoundSqlProvider.class, method="statement")
    List<Map<String,Object>> select(Map<String,Object> statement);

    @UpdateProvider(type=BoundSqlProvider.class, method="statement")
    int update(Map<String,Object> statement);

    @InsertProvider(type=BoundSqlProvider.class, method="statement")
    @Options(useGeneratedKeys=true, keyProperty="generatedId")
    int insert(Map<String,Object> statement);
}
