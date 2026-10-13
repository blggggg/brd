package com.brd;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.stereotype.Component;

@Configuration
class Security {
    @Bean PasswordEncoder encoder(){return new BCryptPasswordEncoder(12);}
    @Bean SecurityFilterChain chain(HttpSecurity http,AccountGate gate) throws Exception {
        return http.authorizeHttpRequests(a->a.anyRequest().permitAll())
            .formLogin(f->f.disable()).httpBasic(b->b.disable()).logout(l->l.disable())
            .addFilterAfter(gate,CsrfFilter.class)
            .headers(h->h.contentSecurityPolicy(c->c.policyDirectives("default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'; object-src 'none'")))
            .build();
    }
    @Bean FilterRegistrationBean<AccountGate> disableRegistration(AccountGate gate){var r=new FilterRegistrationBean<>(gate);r.setEnabled(false);return r;}
}

@Component
class AccountGate extends OncePerRequestFilter {
    private final Db db;
    AccountGate(Db db){this.db=db;}
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
        String path=req.getRequestURI();
        if(!path.startsWith("/api/")||Set.of("/api/v1/auth/login","/api/v1/auth/csrf").contains(path)){chain.doFilter(req,res);return;}
        var session=req.getSession(false); Map<String,Object> user=null;
        if(session!=null&&session.getAttribute("uid")!=null){
            var rows=db.list("SELECT * FROM app_user WHERE id=?",session.getAttribute("uid"));
            if(!rows.isEmpty()&&Db.bool(rows.get(0).get("enabled"))&&Objects.equals(session.getAttribute("authVersion"),Db.num(rows.get(0).get("auth_version"))))user=rows.get(0);
        }
        if(user==null){if(session!=null)session.invalidate();fail(res,401,"UNAUTHENTICATED","请重新登录");return;}
        if(Db.bool(user.get("must_change"))&&!Set.of("/api/v1/auth/me","/api/v1/auth/password","/api/v1/auth/logout").contains(path)){fail(res,403,"PASSWORD_CHANGE_REQUIRED","请先修改临时密码");return;}
        req.setAttribute("account",user);chain.doFilter(req,res);
    }
    private void fail(HttpServletResponse r,int s,String c,String m)throws IOException{r.setStatus(s);r.setContentType("application/json;charset=UTF-8");r.getWriter().write("{\"code\":\""+c+"\",\"message\":\""+m+"\"}");}
    @SuppressWarnings("unchecked") static Map<String,Object> account(HttpServletRequest req){return (Map<String,Object>)req.getAttribute("account");}
    static long actor(HttpServletRequest req){return Db.num(account(req).get("id"));}
    static void admin(Map<String,Object> u){if(!"ADMIN".equals(u.get("role")))throw new Problem(403,"FORBIDDEN","需要管理员权限");}
    static void write(Map<String,Object> u,Map<String,Object> d){
        if("READER".equals(u.get("role"))||(!"ADMIN".equals(u.get("role"))&&d!=null&&Db.num(d.get("owner_id"))!=Db.num(u.get("id"))))throw new Problem(403,"FORBIDDEN","仅文档负责人或管理员可操作");
    }
}
