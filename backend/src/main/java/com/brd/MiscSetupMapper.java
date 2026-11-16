package com.brd;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

interface MiscSetupMapper {
    @Select("SELECT PROPERTY_ID AS id, PROPERTY_DESC AS name FROM misc_setup WHERE COMPANY_ID=0 AND SAMPLE_TYPE_ID=0 AND LOCATION_ID=0 AND PROPERTY_DESC IS NOT NULL AND TRIM(PROPERTY_DESC)<>'' ORDER BY PROPERTY_ID")
    List<Map<String,Object>> listProjects();

    @Select("SELECT PROPERTY_ID AS id, PROPERTY_DESC AS name FROM misc_setup WHERE COMPANY_ID=0 AND SAMPLE_TYPE_ID=0 AND LOCATION_ID=0 AND LOWER(TRIM(PROPERTY_DESC))=LOWER(#{name})")
    List<Map<String,Object>> findProject(@Param("name") String name);

    @Select("SELECT COALESCE(MAX(PROPERTY_ID),0)+1 FROM misc_setup WHERE COMPANY_ID=0 AND SAMPLE_TYPE_ID=0 AND LOCATION_ID=0")
    long nextProjectId();

    @Insert("INSERT INTO misc_setup(COMPANY_ID,SAMPLE_TYPE_ID,LOCATION_ID,PROPERTY_ID,PROPERTY_DESC,DATE_CREATED,ENABLE_DATE) VALUES(0,0,0,#{id},#{name},CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
    int addProject(@Param("id") long id, @Param("name") String name);
}
