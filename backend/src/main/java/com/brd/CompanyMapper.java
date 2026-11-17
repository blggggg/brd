package com.brd;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Select;

public interface CompanyMapper {
    @Select("SELECT id, company_name, enabled FROM company WHERE enabled = TRUE ORDER BY company_name, id")
    List<Map<String,Object>> listEnabled();
}
