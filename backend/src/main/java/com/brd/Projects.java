package com.brd;

import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects")
public class Projects {
    private final ProjectDatabase projects;
    Projects(ProjectDatabase projects){this.projects=projects;}

    @GetMapping
    List<Map<String,Object>> list(){
        return projects.list("SELECT PROPERTY_ID AS id, PROPERTY_DESC AS name FROM misc_setup WHERE COMPANY_ID=0 AND SAMPLE_TYPE_ID=0 AND LOCATION_ID=0 AND PROPERTY_DESC IS NOT NULL AND TRIM(PROPERTY_DESC)<>'' ORDER BY PROPERTY_ID");
    }

    @PostMapping
    Map<String,Object> add(@RequestBody Map<String,Object> body){
        String name=Db.text(body,"name",1,100);
        var existing=projects.list("SELECT PROPERTY_ID AS id, PROPERTY_DESC AS name FROM misc_setup WHERE COMPANY_ID=0 AND SAMPLE_TYPE_ID=0 AND LOCATION_ID=0 AND LOWER(TRIM(PROPERTY_DESC))=LOWER(?)",name);
        if(!existing.isEmpty())throw new Problem(409,"PROJECT_EXISTS","这个项目已经在名单中");
        Long nextId=projects.nextId();
        long id=nextId==null?1:nextId;
        projects.add(id,name);
        return Map.of("id",id,"name",name);
    }
}
