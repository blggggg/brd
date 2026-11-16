package com.brd;

import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects")
public class Projects {
    private final MiscSetupMapper projects;
    Projects(MiscSetupMapper projects){this.projects=projects;}

    @GetMapping
    List<Map<String,Object>> list(){
        return projects.listProjects();
    }

    @PostMapping
    Map<String,Object> add(@RequestBody Map<String,Object> body){
        String name=Db.text(body,"name",1,100);
        var existing=projects.findProject(name);
        if(!existing.isEmpty())throw new Problem(409,"PROJECT_EXISTS","这个项目已经在名单中");
        long id=projects.nextProjectId();
        projects.addProject(id,name);
        return Map.of("id",id,"name",name);
    }
}
