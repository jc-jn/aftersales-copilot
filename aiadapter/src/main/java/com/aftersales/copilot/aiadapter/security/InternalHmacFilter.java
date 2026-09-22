package com.aftersales.copilot.aiadapter.security;

import jakarta.servlet.FilterChain; import jakarta.servlet.ServletException; import jakarta.servlet.ServletInputStream; import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order; import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component @Order(1)
public class InternalHmacFilter extends OncePerRequestFilter {
    private final InternalHmacService hmac;
    public InternalHmacFilter(InternalHmacService hmac){this.hmac=hmac;}
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
        if(!req.getRequestURI().startsWith("/internal/")){chain.doFilter(req,res);return;}
        if(req.getRequestURI().equals("/internal/v1/health/ready")){chain.doFilter(req,res);return;}
        String body=new String(req.getInputStream().readAllBytes(),req.getCharacterEncoding()==null?java.nio.charset.StandardCharsets.UTF_8:java.nio.charset.Charset.forName(req.getCharacterEncoding()));
        boolean ok=hmac.verify(req.getHeader("X-Internal-Service"),parse(req.getHeader("X-Internal-Timestamp")),req.getHeader("X-Internal-Nonce"),req.getMethod(),req.getRequestURI(),body,req.getHeader("X-Internal-Signature"));
        if(!ok){res.sendError(401,"invalid internal signature");return;}
        chain.doFilter(new CachedBodyRequest(req,body),res);
    }
    private long parse(String s){try{return Long.parseLong(s);}catch(Exception e){return 0;}}
    static class CachedBodyRequest extends HttpServletRequestWrapper { private final byte[] bytes; CachedBodyRequest(HttpServletRequest r,String b){super(r);bytes=b.getBytes(java.nio.charset.StandardCharsets.UTF_8);} public ServletInputStream getInputStream(){return new ServletInputStream(){int i;public int read(){return i<bytes.length?bytes[i++]:-1;}public boolean isFinished(){return i>=bytes.length;}public boolean isReady(){return true;}public void setReadListener(jakarta.servlet.ReadListener l){}};} }
}
