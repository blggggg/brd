package com.brd;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

interface TaskMapper {
    @Select("SELECT id, company_id, report_format_id, class_name, order_no, enabled, other_fields, remark, created_at, updated_at FROM task WHERE company_id=#{companyId} AND report_format_id=#{reportFormatId} ORDER BY order_no, id")
    List<Map<String,Object>> list(@Param("companyId") long companyId, @Param("reportFormatId") int reportFormatId);

    @Select("SELECT id, company_id, report_format_id, class_name, order_no, enabled, other_fields, remark, created_at, updated_at FROM task WHERE id=#{id}")
    Map<String,Object> one(@Param("id") long id);

    @Insert("INSERT INTO task(company_id,report_format_id,class_name,order_no,enabled,other_fields,remark,created_at,updated_at) VALUES(#{companyId},#{reportFormatId},#{className},#{orderNo},#{enabled},#{otherFields},#{remark},CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insert(Map<String,Object> task);

    @Update("UPDATE task SET company_id=#{companyId},report_format_id=#{reportFormatId},class_name=#{className},order_no=#{orderNo},enabled=#{enabled},other_fields=#{otherFields},remark=#{remark},updated_at=CURRENT_TIMESTAMP WHERE id=#{id}")
    int update(Map<String,Object> task);

    @Delete("DELETE FROM task WHERE id=#{id}")
    int delete(@Param("id") long id);
}
