package com.brd;

import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class Accounts {
    final Db db; final PasswordEncoder encoder; final String dummy;
    final Map<String,ArrayDeque<Long>> failures=new ConcurrentHashMap<>();
    Accounts(Db db,PasswordEncoder encoder){this.db=db;this.encoder=encoder;dummy=encoder.encode(UUID.randomUUID().toString());}
    @Bean ApplicationRunner initializeAdmin(@Value("${brd.admin-password}")String password){return args->{
        if(db.list("SELECT id FROM app_user").isEmpty()){
            password(password);db.update("INSERT INTO app_user(username,display_name,password_hash,role) VALUES('admin','管理员',?,'ADMIN')",encoder.encode(password));
        }
    };}
    static String password(String p){if(p==null||p.length()<12||p.getBytes(StandardCharsets.UTF_8).length>72)throw new Problem(400,"INVALID_PASSWORD","密码至少 12 位，UTF-8 编码不超过 72 字节");return p;}
    Map<String,Object> safe(Map<String,Object> row){var r=Db.publicRow(row);r.remove("password_hash");r.remove("auth_version");return r;}
    @GetMapping("/auth/csrf") Map<String,String> csrf(CsrfToken token){return Map.of("token",token.getToken(),"headerName",token.getHeaderName());}
    @PostMapping("/auth/login") synchronized Map<String,Object> login(@RequestBody Map<String,String> b,HttpServletRequest req){
        String username=Db.text(b,"username",1,40),p=b.getOrDefault("password","");
        long now=System.currentTimeMillis();failures.entrySet().removeIf(e->e.getValue().isEmpty()||e.getValue().getLast()<now-300000);
        String[] keys={"user:"+username,"ip:"+req.getRemoteAddr()};
        for(String key:keys){var times=failures.computeIfAbsent(key,k->new ArrayDeque<>());while(!times.isEmpty()&&times.getFirst()<now-300000)times.removeFirst();if(times.size()>=10)throw new Problem(429,"RATE_LIMITED","尝试过于频繁，请稍后再试");}
        var rows=db.list("SELECT * FROM app_user WHERE username=?",username);
        boolean match=p.getBytes(StandardCharsets.UTF_8).length<=72&&encoder.matches(p,rows.isEmpty()?dummy:rows.get(0).get("password_hash").toString());
        if(!match||rows.isEmpty()||!Db.bool(rows.get(0).get("enabled"))){for(String key:keys)failures.get(key).add(now);throw new Problem(401,"INVALID_CREDENTIALS","账号或密码错误");}
        var user=rows.get(0);var old=req.getSession(false);if(old!=null)old.invalidate();var s=req.getSession(true);s.setAttribute("uid",Db.num(user.get("id")));s.setAttribute("authVersion",Db.num(user.get("auth_version")));db.log(Db.num(user.get("id")),"LOGIN",null,null,null,null);return safe(user);
    }
    @GetMapping("/auth/me") Map<String,Object> me(HttpServletRequest req){return safe(AccountGate.account(req));}
    @PostMapping("/auth/logout") void logout(HttpServletRequest req){req.getSession().invalidate();}
    @PutMapping("/auth/password") @Transactional void change(@RequestBody Map<String,String>b,HttpServletRequest req){
        long id=AccountGate.actor(req);var u=db.one("SELECT * FROM app_user WHERE id=? FOR UPDATE",id);
        if(!encoder.matches(b.getOrDefault("oldPassword",""),u.get("password_hash").toString()))throw new Problem(400,"INVALID_PASSWORD","当前密码不正确");
        String p=password(b.get("newPassword"));if(encoder.matches(p,u.get("password_hash").toString()))throw new Problem(400,"INVALID_PASSWORD","新密码不能与旧密码相同");
        db.update("UPDATE app_user SET password_hash=?,must_change=FALSE,auth_version=auth_version+1 WHERE id=?",encoder.encode(p),id);db.log(id,"CHANGE_PASSWORD",null,null,null,null);req.getSession().invalidate();
    }
    @GetMapping("/users") List<Map<String,Object>> users(HttpServletRequest r){AccountGate.admin(AccountGate.account(r));return db.list("SELECT * FROM app_user ORDER BY id").stream().map(this::safe).toList();}
    @PostMapping("/users") @Transactional Map<String,Object> create(@RequestBody Map<String,String>b,HttpServletRequest r){
        AccountGate.admin(AccountGate.account(r));String username=Db.text(b,"username",3,40);if(!username.matches("[a-zA-Z0-9_]{3,40}"))throw new Problem(400,"INVALID_INPUT","用户名仅限字母数字及下划线");
        long id=db.insert("INSERT INTO app_user(username,display_name,password_hash,role) VALUES(?,?,?,?)",username,Db.text(b,"displayName",1,80),encoder.encode(password(b.get("password"))),role(b.get("role")));db.log(AccountGate.actor(r),"CREATE_USER",null,null,null,"user:"+id);return safe(db.one("SELECT * FROM app_user WHERE id=?",id));
    }
    String role(String s){if(!Set.of("ADMIN","UPLOADER","READER").contains(s==null?"":s))throw new Problem(400,"INVALID_INPUT","角色错误");return s;}
    @PatchMapping("/users/{id}") @Transactional Map<String,Object> edit(@PathVariable long id,@RequestBody Map<String,Object>b,HttpServletRequest r){
        AccountGate.admin(AccountGate.account(r));db.list("SELECT id FROM app_user ORDER BY id FOR UPDATE");var u=db.one("SELECT * FROM app_user WHERE id=?",id);
        String role=b.containsKey("role")?role(String.valueOf(b.get("role"))):u.get("role").toString();boolean enabled=b.containsKey("enabled")?Boolean.TRUE.equals(b.get("enabled")):Db.bool(u.get("enabled"));
        if("ADMIN".equals(u.get("role"))&&Db.bool(u.get("enabled"))&&(!enabled||!role.equals("ADMIN"))&&db.list("SELECT id FROM app_user WHERE role='ADMIN' AND enabled=TRUE").size()==1)throw new Problem(409,"LAST_ADMIN","不能停用或降级最后一个管理员");
        String name=b.containsKey("displayName")?Db.text(b,"displayName",1,80):u.get("display_name").toString();db.update("UPDATE app_user SET role=?,enabled=?,display_name=?,auth_version=auth_version+1 WHERE id=?",role,enabled,name,id);db.log(AccountGate.actor(r),"UPDATE_USER",null,null,null,"user:"+id);return safe(db.one("SELECT * FROM app_user WHERE id=?",id));
    }
    @PostMapping("/users/{id}/password-reset") @Transactional void reset(@PathVariable long id,@RequestBody Map<String,String>b,HttpServletRequest r){AccountGate.admin(AccountGate.account(r));db.one("SELECT id FROM app_user WHERE id=?",id);db.update("UPDATE app_user SET password_hash=?,must_change=TRUE,auth_version=auth_version+1 WHERE id=?",encoder.encode(password(b.get("password"))),id);db.log(AccountGate.actor(r),"RESET_PASSWORD",null,null,null,"user:"+id);}
}
