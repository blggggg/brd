package com.brd;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Service
class UploadWorker {
    final Db db;final Storage storage;final TransactionTemplate tx;
    final ExecutorService pool=new ThreadPoolExecutor(2,2,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(20),new ThreadPoolExecutor.AbortPolicy());
    UploadWorker(Db db,Storage storage,TransactionTemplate tx){this.db=db;this.storage=storage;this.tx=tx;}
    @PreDestroy void close(){pool.shutdownNow();}
    @EventListener(ApplicationReadyEvent.class) void recover(){db.update("UPDATE upload_job SET state='FAILED',error_code='SERVER_RESTARTED' WHERE state IN ('RECEIVING','CHECKING')");}
    void enqueue(String id){try{pool.submit(()->process(id));}catch(RejectedExecutionException e){db.update("UPDATE upload_job SET state='FAILED',error_code='SERVER_BUSY' WHERE id=?",id);storage.discardTemp(db.one("SELECT temp_key FROM upload_job WHERE id=?",id).get("temp_key").toString());}}
    void process(String id){String key=null;try{
        var job=db.one("SELECT * FROM upload_job WHERE id=?",id);key=job.get("temp_key").toString();if(!"CHECKING".equals(job.get("state")))return;storage.inspect(key,job.get("extension").toString());
        tx.executeWithoutResult(s->finish(id));
    }catch(Exception e){db.update("UPDATE upload_job SET state='FAILED',error_code=? WHERE id=? AND state='CHECKING'",e instanceof Problem?((Problem)e).code:"FILE_CHECK_FAILED",id);}finally{if(key!=null)storage.discardTemp(key);}}
    void finish(String id){
        var j=db.one("SELECT * FROM upload_job WHERE id=? FOR UPDATE",id);if(!"CHECKING".equals(j.get("state")))return;
        long actor=Db.num(j.get("actor_id"));var u=db.one("SELECT * FROM app_user WHERE id=? FOR UPDATE",actor);if(!Db.bool(u.get("enabled")))throw new Problem(403,"ACCOUNT_DISABLED","账号已停用");
        boolean fresh=j.get("document_id")==null;Map<String,Object>d=fresh?null:db.one("SELECT * FROM brd_document WHERE id=? FOR UPDATE",j.get("document_id"));AccountGate.write(u,d);
        if(!fresh){var dup=db.list("SELECT id FROM brd_version WHERE document_id=? AND sha256=?",d.get("id"),j.get("sha256"));if(!dup.isEmpty()){db.update("UPDATE upload_job SET state='DUPLICATE',result_version_id=? WHERE id=?",dup.get(0).get("id"),id);return;}}
        if(j.get("base_version_id")!=null){if(fresh)throw new Problem(400,"INVALID_BASE","首版不能有基于版本");db.one("SELECT id FROM brd_version WHERE id=? AND document_id=?",j.get("base_version_id"),d.get("id"));}
        String key=j.get("temp_key").toString();try{storage.promote(key);}catch(IOException e){throw new Problem(503,"STORAGE_ERROR","文件存储失败");}
        long docId=fresh?db.insert("INSERT INTO brd_document(title,project_name,delivery_batch,owner_id) VALUES(?,?,?,?)",j.get("title"),j.get("project_name"),j.get("delivery_batch"),actor):Db.num(d.get("id"));
        int seq=fresh?1:((Number)d.get("next_sequence")).intValue();
        long version=db.insert("INSERT INTO brd_version(document_id,sequence_no,base_version_id,object_key,original_filename,extension,sha256,size_bytes,change_note,uploaded_by) VALUES(?,?,?,?,?,?,?,?,?,?)",docId,seq,j.get("base_version_id"),key,j.get("original_filename"),j.get("extension"),j.get("sha256"),j.get("size_bytes"),j.get("change_note"),actor);
        db.update("UPDATE brd_document SET latest_version_id=?,current_version_id=?,next_sequence=?,revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",version,fresh?version:d.get("current_version_id"),seq+1,docId);
        db.update("UPDATE upload_job SET state='SUCCEEDED',document_id=?,result_version_id=? WHERE id=?",docId,version,id);db.log(actor,"UPLOAD",docId,version,null,j.get("change_note").toString());
    }
}

@RestController @RequestMapping("/api/v1")
public class Uploads {
    final Db db;final Storage storage;final UploadWorker worker;final TransactionTemplate tx;
    Uploads(Db db,Storage storage,UploadWorker worker,TransactionTemplate tx){this.db=db;this.storage=storage;this.worker=worker;this.tx=tx;}
    @PostMapping(value={"/brd-uploads","/brds/{documentId}/uploads"},consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<?> upload(@PathVariable(required=false)Long documentId,@RequestParam MultipartFile file,@RequestParam Map<String,String>b,@RequestHeader("Idempotency-Key")String requestKey,HttpServletRequest r)throws Exception{
        AccountGate.write(AccountGate.account(r),documentId==null?null:db.one("SELECT * FROM brd_document WHERE id=?",documentId));
        if(!requestKey.matches("[a-zA-Z0-9-]{16,80}"))throw new Problem(400,"INVALID_KEY","幂等键格式错误");
        String note=Db.text(b,"changeNote",2,500),title=documentId==null?Db.text(b,"title",1,160):"",project=documentId==null?Db.text(b,"projectName",1,120):"",batch=documentId==null?Db.text(b,"deliveryBatch",1,80):"";
        Long base=b.containsKey("baseVersionId")&&!b.get("baseVersionId").isBlank()?Db.id(b,"baseVersionId"):null;
        if(base!=null){if(documentId==null)throw new Problem(400,"INVALID_BASE","首版不能指定来源");db.one("SELECT id FROM brd_version WHERE id=? AND document_id=?",base,documentId);}
        var incoming=storage.receive(file);long actor=AccountGate.actor(r);String id=UUID.randomUUID().toString();boolean retained=false;
        try{
            var bytes=new ByteArrayOutputStream();var data=new DataOutputStream(bytes);for(String v:List.of(String.valueOf(documentId),String.valueOf(base),title,project,batch,note,incoming.hash(),incoming.name()))data.writeUTF(v);String hash=Storage.hash(bytes.toByteArray());
            String jobId=tx.execute(s->{
                db.one("SELECT id FROM app_user WHERE id=? FOR UPDATE",actor);
                var existing=db.list("SELECT id,request_hash FROM upload_job WHERE actor_id=? AND request_key=?",actor,requestKey);
                if(!existing.isEmpty()){if(!hash.equals(existing.get(0).get("request_hash")))throw new Problem(409,"KEY_REUSED","相同幂等键不能用于不同上传");return existing.get(0).get("id").toString();}
                db.update("INSERT INTO upload_job(id,actor_id,request_key,request_hash,document_id,base_version_id,title,project_name,delivery_batch,change_note,original_filename,extension,sha256,size_bytes,temp_key,state) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'CHECKING')",id,actor,requestKey,hash,documentId,base,title,project,batch,note,incoming.name(),incoming.ext(),incoming.hash(),incoming.size(),incoming.key());return id;
            });
            if(id.equals(jobId)){retained=true;worker.enqueue(id);}return ResponseEntity.accepted().body(Map.of("uploadId",jobId));
        }finally{if(!retained)storage.discardTemp(incoming.key());}
    }
    @GetMapping("/uploads/{id}") Map<String,Object> status(@PathVariable String id,HttpServletRequest r){var j=owned(id,r);var result=new LinkedHashMap<String,Object>();for(String k:List.of("id","state","document_id","result_version_id","error_code"))result.put(k,j.get(k));return Db.publicRow(result);}
    Map<String,Object> owned(String id,HttpServletRequest r){var j=db.one("SELECT * FROM upload_job WHERE id=?",id);if(Db.num(j.get("actor_id"))!=AccountGate.actor(r)&&!"ADMIN".equals(AccountGate.account(r).get("role")))throw new Problem(404,"NOT_FOUND","上传任务不存在");return j;}
    @PostMapping("/uploads/{id}/cancel") void cancel(@PathVariable String id,HttpServletRequest r){owned(id,r);tx.executeWithoutResult(s->{var j=db.one("SELECT * FROM upload_job WHERE id=? FOR UPDATE",id);if(!"CHECKING".equals(j.get("state")))throw new Problem(409,"UPLOAD_FINALIZED","任务已经结束");db.update("UPDATE upload_job SET state='CANCELLED' WHERE id=?",id);});}
}
