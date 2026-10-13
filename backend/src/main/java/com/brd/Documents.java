package com.brd;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/brds")
public class Documents {
    final Db db; final Storage storage;
    Documents(Db db,Storage storage){this.db=db;this.storage=storage;}
    static final String SELECT="SELECT d.*,u.display_name AS owner_name,c.sequence_no AS current_sequence,l.sequence_no AS latest_sequence FROM brd_document d JOIN app_user u ON u.id=d.owner_id LEFT JOIN brd_version c ON c.id=d.current_version_id LEFT JOIN brd_version l ON l.id=d.latest_version_id ";
    @GetMapping Map<String,Object> list(@RequestParam(defaultValue="")String search,@RequestParam(defaultValue="0")int page){
        if(page<0||page>100000||search.length()>160)throw new Problem(400,"INVALID_INPUT","分页或搜索参数错误");
        String term="%"+search.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        String where=" WHERE (d.title LIKE ? ESCAPE '!' OR d.project_name LIKE ? ESCAPE '!' OR d.delivery_batch LIKE ? ESCAPE '!')";
        return Map.of("items",Db.publicRows(db.list(SELECT+where+" ORDER BY d.updated_at DESC,d.id DESC LIMIT 20 OFFSET ?",term,term,term,page*20)),"total",db.one("SELECT COUNT(*) AS n FROM brd_document d"+where,term,term,term).get("n"));
    }
    @GetMapping("/{id}") Map<String,Object> detail(@PathVariable long id){return Db.publicRow(db.one(SELECT+" WHERE d.id=?",id));}
    @GetMapping("/{id}/versions") Map<String,Object> versions(@PathVariable long id,@RequestParam(defaultValue="0")int page){
        db.one("SELECT id FROM brd_document WHERE id=?",id);if(page<0||page>100000)throw new Problem(400,"INVALID_INPUT","分页错误");
        return Map.of("items",Db.publicRows(db.list("SELECT v.id,v.document_id,v.sequence_no,v.base_version_id,v.original_filename,v.extension,v.sha256,v.size_bytes,v.change_note,v.uploaded_at,u.display_name AS uploaded_by_name FROM brd_version v JOIN app_user u ON u.id=v.uploaded_by WHERE v.document_id=? ORDER BY v.sequence_no DESC LIMIT 10 OFFSET ?",id,page*10)),"total",db.one("SELECT COUNT(*) AS n FROM brd_version WHERE document_id=?",id).get("n"));
    }
    @GetMapping("/{id}/versions/{versionId}") Map<String,Object> version(@PathVariable long id,@PathVariable long versionId){var v=db.one("SELECT * FROM brd_version WHERE id=? AND document_id=?",versionId,id);v.remove("object_key");return Db.publicRow(v);}
    @PutMapping("/{id}/current-version") @Transactional Map<String,Object> current(@PathVariable long id,@RequestBody Map<String,Object>b,HttpServletRequest r){
        var d=db.one("SELECT * FROM brd_document WHERE id=? FOR UPDATE",id);AccountGate.write(AccountGate.account(r),d);long v=Db.id(b,"versionId");db.one("SELECT id FROM brd_version WHERE id=? AND document_id=?",v,id);
        String reason=Db.text(b,"reason",2,300);if(Db.num(d.get("revision"))!=Db.id(b,"expectedRevision"))throw new Problem(409,"VERSION_CONFLICT","文档已更新，请刷新后重新确认");
        if(Db.num(d.get("current_version_id"))!=v){db.update("UPDATE brd_document SET current_version_id=?,revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",v,id);db.log(AccountGate.actor(r),"SET_CURRENT",id,v,Db.num(d.get("current_version_id")),reason);}return detail(id);
    }
    @PatchMapping("/{id}") @Transactional Map<String,Object> edit(@PathVariable long id,@RequestBody Map<String,Object>b,HttpServletRequest r){
        var d=db.one("SELECT * FROM brd_document WHERE id=? FOR UPDATE",id);AccountGate.write(AccountGate.account(r),d);if(Db.num(d.get("revision"))!=Db.id(b,"expectedRevision"))throw new Problem(409,"VERSION_CONFLICT","文档已更新");long owner=Db.num(d.get("owner_id"));
        if(b.containsKey("ownerId")){AccountGate.admin(AccountGate.account(r));owner=Db.id(b,"ownerId");var u=db.one("SELECT * FROM app_user WHERE id=?",owner);if(!Db.bool(u.get("enabled"))||"READER".equals(u.get("role")))throw new Problem(400,"INVALID_OWNER","负责人必须为有效管理员或上传者");}
        db.update("UPDATE brd_document SET title=?,project_name=?,delivery_batch=?,owner_id=?,revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",Db.text(b,"title",1,160),Db.text(b,"projectName",1,120),Db.text(b,"deliveryBatch",1,80),owner,id);db.log(AccountGate.actor(r),"EDIT_DOCUMENT",id,null,null,"更新文档归属及元数据");return detail(id);
    }
    @GetMapping("/{id}/operations") List<Map<String,Object>> operations(@PathVariable long id){return Db.publicRows(db.list("SELECT o.*,u.display_name AS actor_name FROM operation_log o JOIN app_user u ON u.id=o.actor_id WHERE o.document_id=? ORDER BY o.id DESC LIMIT 100",id));}
    @GetMapping("/{id}/versions/{versionId}/download") ResponseEntity<?> download(@PathVariable long id,@PathVariable long versionId,HttpServletRequest r)throws Exception{return file(id,versionId,r);}
    @GetMapping("/{id}/current/download") ResponseEntity<?> downloadCurrent(@PathVariable long id,HttpServletRequest r)throws Exception{return file(id,Db.num(db.one("SELECT current_version_id FROM brd_document WHERE id=?",id).get("current_version_id")),r);}
    ResponseEntity<?> file(long id,long versionId,HttpServletRequest r)throws Exception{
        var v=db.one("SELECT v.*,d.title FROM brd_version v JOIN brd_document d ON d.id=v.document_id WHERE v.id=? AND v.document_id=?",versionId,id);var path=storage.object(v.get("object_key").toString());
        if(!Files.isRegularFile(path))throw new Problem(503,"FILE_MISSING","文件暂不可用，请联系管理员");
        String title=v.get("title").toString().replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]","_");String name="BRD-"+String.format("%06d",id)+"_V"+v.get("sequence_no")+"_"+title+"."+v.get("extension");
        db.log(AccountGate.actor(r),"DOWNLOAD",id,versionId,null,null);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(Files.size(path)).header("Cache-Control","no-store").header("X-BRD-Version",String.valueOf(versionId)).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(name,StandardCharsets.UTF_8).build().toString()).body(new FileSystemResource(path));
    }
}
