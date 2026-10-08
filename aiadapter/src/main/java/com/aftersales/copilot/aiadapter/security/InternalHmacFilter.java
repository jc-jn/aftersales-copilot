package com.aftersales.copilot.aiadapter.security;

import jakarta.servlet.FilterChain; import jakarta.servlet.ServletException; import jakarta.servlet.ServletInputStream; import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order; import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component @Order(-110)
public class InternalHmacFilter extends OncePerRequestFilter {
    private final InternalHmacService hmac;
    public InternalHmacFilter(InternalHmacService hmac){this.hmac=hmac;}
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
        if(!req.getRequestURI().startsWith("/internal/")){chain.doFilter(req,res);return;}
        byte[] bytes=req.getInputStream().readNBytes(1024*1024+1);
        if(bytes.length>1024*1024){res.sendError(413,"internal body too large");return;}
        String body=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
        boolean ok;
        try { ok=hmac.verify(req.getHeader("X-Internal-Service"),parse(req.getHeader("X-Internal-Timestamp")),req.getHeader("X-Internal-Nonce"),req.getMethod(),req.getRequestURI(),body,req.getHeader("X-Internal-Signature")); }
        catch(org.springframework.web.server.ResponseStatusException e){res.sendError(503,"security state unavailable");return;}
        if(!ok){res.sendError(401,"invalid internal signature");return;}
        req.setAttribute("internalHmacVerified", Boolean.TRUE);
        chain.doFilter(new CachedBodyRequest(req,body),res);
    }
    private long parse(String s){try{return Long.parseLong(s);}catch(Exception e){return 0;}}
    static class CachedBodyRequest extends HttpServletRequestWrapper { private final byte[] bytes; CachedBodyRequest(HttpServletRequest r,String b){super(r);bytes=b.getBytes(java.nio.charset.StandardCharsets.UTF_8);} public ServletInputStream getInputStream(){return new ServletInputStream(){int i;public int read(){return i<bytes.length?bytes[i++] & 0xff:-1;}public boolean isFinished(){return i>=bytes.length;}public boolean isReady(){return true;}public void setReadListener(jakarta.servlet.ReadListener l){}};} }
}
