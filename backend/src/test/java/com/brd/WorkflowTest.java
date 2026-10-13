package com.brd;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.datasource.url=jdbc:h2:mem:workflow;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","brd.scanner=structural"})
class WorkflowTest {
    static final String ADMIN_INITIAL = "test-admin-" + UUID.randomUUID();
    static final String ADMIN_CHANGED = "test-admin-changed-" + UUID.randomUUID();
    static final String READER_INITIAL = "test-reader-" + UUID.randomUUID();
    static final String READER_CHANGED = "test-reader-changed-" + UUID.randomUUID();
    static final String READER_RESET = "test-reader-reset-" + UUID.randomUUID();
    @LocalServerPort int port; @Autowired Db db;
    static Path files;
    static {try{files=Files.createTempDirectory("brd-workflow-test-");}catch(IOException e){throw new ExceptionInInitializerError(e);}}
    @DynamicPropertySource static void settings(DynamicPropertyRegistry r){r.add("brd.storage",()->files.toString());r.add("brd.admin-password",()->ADMIN_INITIAL);}
    final ObjectMapper json=new ObjectMapper();
    HttpClient client(){return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();}
    URI uri(String p){return URI.create("http://127.0.0.1:"+port+"/api/v1"+p);}
    Map<String,Object> body(HttpResponse<String> r){return json.readValue(r.body(),Map.class);}
    HttpResponse<String> call(HttpClient c,String path,String method,Object b)throws Exception{
        var builder=HttpRequest.newBuilder(uri(path));
        if(!method.equals("GET")){var csrf=body(c.send(HttpRequest.newBuilder(uri("/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString()));builder.header(csrf.get("headerName").toString(),csrf.get("token").toString());}
        builder.header("Content-Type","application/json").method(method,b==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(b)));
        return c.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    void login(HttpClient c,String user,String password)throws Exception{var r=call(c,"/auth/login","POST",Map.of("username",user,"password",password));assertEquals(200,r.statusCode(),r.body());}
    byte[] pdf(String text){return ("%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n% "+text+"\n%%EOF").getBytes(StandardCharsets.UTF_8);}
    HttpResponse<String> upload(HttpClient c,String path,byte[] bytes,String name,String mime,Map<String,String> fields,String key)throws Exception{
        String boundary="BRDTestBoundary928374";var out=new ByteArrayOutputStream();
        for(var f:fields.entrySet())out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+f.getKey()+"\"\r\n\r\n"+f.getValue()+"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+name+"\"\r\nContent-Type: "+mime+"\r\n\r\n").getBytes());out.write(bytes);out.write(("\r\n--"+boundary+"--\r\n").getBytes());
        var csrf=body(call(c,"/auth/csrf","GET",null));return c.send(HttpRequest.newBuilder(uri(path)).header(csrf.get("headerName").toString(),csrf.get("token").toString()).header("Content-Type","multipart/form-data; boundary="+boundary).header("Idempotency-Key",key).POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
    }
    Map<String,Object> await(HttpClient c,String id)throws Exception{for(int i=0;i<100;i++){var b=body(call(c,"/uploads/"+id,"GET",null));if(!Set.of("CHECKING","RECEIVING").contains(b.get("state")))return b;Thread.sleep(80);}fail("Upload did not complete");return null;}
    @Test void accountsFilesVersionsAndPermissions()throws Exception{
        var admin=client();assertEquals(401,call(admin,"/brds","GET",null).statusCode());
        login(admin,"admin",ADMIN_INITIAL);assertEquals(403,call(admin,"/brds","GET",null).statusCode());
        assertEquals(200,call(admin,"/auth/password","PUT",Map.of("oldPassword",ADMIN_INITIAL,"newPassword",ADMIN_CHANGED)).statusCode());
        assertEquals(401,call(admin,"/auth/me","GET",null).statusCode());login(admin,"admin",ADMIN_CHANGED);
        var noCsrf=admin.send(HttpRequest.newBuilder(uri("/users")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(),HttpResponse.BodyHandlers.ofString());assertEquals(403,noCsrf.statusCode());
        var fields=Map.of("title","Login BRD","projectName","CRM","deliveryBatch","R2","changeNote","Initial version");String key=UUID.randomUUID().toString();
        var first=upload(admin,"/brd-uploads",pdf("one"),"first.pdf","application/pdf",fields,key);assertEquals(202,first.statusCode(),first.body());var firstJob=await(admin,body(first).get("uploadId").toString());assertEquals("SUCCEEDED",firstJob.get("state"),firstJob.toString());String doc=firstJob.get("document_id").toString(),v1=firstJob.get("result_version_id").toString();
        var retry=upload(admin,"/brd-uploads",pdf("one"),"first.pdf","application/pdf",fields,key);assertEquals(body(first).get("uploadId"),body(retry).get("uploadId"));
        assertEquals(409,upload(admin,"/brd-uploads",pdf("changed"),"first.pdf","application/pdf",fields,key).statusCode());
        var detail=body(call(admin,"/brds/"+doc,"GET",null));assertEquals(v1,detail.get("current_version_id"));
        var second=upload(admin,"/brds/"+doc+"/uploads",pdf("two"),"first.pdf","application/pdf",Map.of("changeNote","Second version","baseVersionId",v1),UUID.randomUUID().toString());assertEquals(202,second.statusCode(),second.body());var j2=await(admin,body(second).get("uploadId").toString());assertEquals("SUCCEEDED",j2.get("state"));String v2=j2.get("result_version_id").toString();
        detail=body(call(admin,"/brds/"+doc,"GET",null));assertEquals(v1,detail.get("current_version_id"));assertEquals(v2,detail.get("latest_version_id"));
        var dup=upload(admin,"/brds/"+doc+"/uploads",pdf("two"),"renamed.pdf","application/pdf",Map.of("changeNote","Duplicate"),UUID.randomUUID().toString());assertEquals("DUPLICATE",await(admin,body(dup).get("uploadId").toString()).get("state"));assertEquals(2,db.list("SELECT * FROM brd_version WHERE document_id=?",Long.valueOf(doc)).size());
        var set=Map.of("versionId",v2,"expectedRevision",detail.get("revision"),"reason","Use second version");assertEquals(200,call(admin,"/brds/"+doc+"/current-version","PUT",set).statusCode());assertEquals(409,call(admin,"/brds/"+doc+"/current-version","PUT",set).statusCode());
        detail=body(call(admin,"/brds/"+doc,"GET",null));assertEquals(200,call(admin,"/brds/"+doc+"/current-version","PUT",Map.of("versionId",v1,"expectedRevision",detail.get("revision"),"reason","Return to first")).statusCode());
        var bytes=admin.send(HttpRequest.newBuilder(uri("/brds/"+doc+"/versions/"+v1+"/download")).GET().build(),HttpResponse.BodyHandlers.ofByteArray());assertArrayEquals(pdf("one"),bytes.body());assertTrue(bytes.headers().firstValue("Content-Disposition").orElse("").contains("attachment"));
        var bad=upload(admin,"/brd-uploads","not a PDF".getBytes(),"bad.pdf","application/pdf",fields,UUID.randomUUID().toString());assertEquals("FAILED",await(admin,body(bad).get("uploadId").toString()).get("state"));assertEquals(1,db.list("SELECT * FROM brd_document").size());
        var created=call(admin,"/users","POST",Map.of("username","reader","displayName","Reader","role","READER","password",READER_INITIAL));assertEquals(200,created.statusCode(),created.body());String readerId=body(created).get("id").toString();
        var reader=client();login(reader,"reader",READER_INITIAL);call(reader,"/auth/password","PUT",Map.of("oldPassword",READER_INITIAL,"newPassword",READER_CHANGED));login(reader,"reader",READER_CHANGED);assertEquals(200,call(reader,"/brds","GET",null).statusCode());assertEquals(403,call(reader,"/users","GET",null).statusCode());assertEquals(403,upload(reader,"/brd-uploads",pdf("reader"),"reader.pdf","application/pdf",fields,UUID.randomUUID().toString()).statusCode());
        assertEquals(200,call(admin,"/users/"+readerId,"PATCH",Map.of("enabled",false)).statusCode());assertEquals(401,call(reader,"/auth/me","GET",null).statusCode());
        assertEquals(409,call(admin,"/users/1","PATCH",Map.of("enabled",false)).statusCode());
        assertEquals(200,call(admin,"/users/"+readerId+"/password-reset","POST",Map.of("password",READER_RESET)).statusCode());
    }
    @Test void docxStructureAndScanUnavailable()throws Exception{
        var s=new Storage(files.resolve("scan-test").toString(),"structural","127.0.0.1",1);String key="sample.docx";
        try(var zip=new ZipOutputStream(Files.newOutputStream(s.temp(key)))){
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));zip.write("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Override ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\" PartName=\"/word/document.xml\"/></Types>".getBytes());zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));zip.write("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body/></w:document>".getBytes());zip.closeEntry();
        }
        assertDoesNotThrow(()->s.inspect(key,"docx"));
        var unavailable=new Storage(s.root.toString(),"clamav","127.0.0.1",1);assertThrows(IOException.class,()->unavailable.inspect(key,"docx"));
    }
}
