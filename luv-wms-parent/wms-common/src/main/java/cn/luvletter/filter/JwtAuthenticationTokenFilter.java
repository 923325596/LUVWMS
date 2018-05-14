package cn.luvletter.filter;

import cn.luvletter.bean.AuthenticationBean;
import cn.luvletter.exception.ApplicationException;
import cn.luvletter.exception.InvalidTokenException;
import cn.luvletter.security.service.OprtService;
import cn.luvletter.util.JWTUtil;
import cn.luvletter.util.JdbcUtil;
import cn.luvletter.util.WMSUtil;
import com.sun.org.apache.regexp.internal.RE;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * @author Zephyr Ji
 * @ Description: jwt拦截器 （1、从header中拿到token。2、检验token，如果过期，则通过refreshToken重新获取token)
 * @ Date 2018/2/13
 */
@Component
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {
    private Logger log = LoggerFactory.getLogger(JwtAuthenticationTokenFilter.class);

    @Autowired
    private JWTUtil jwtUtil;
    @Autowired
    private JdbcUtil jdbcUtil;
    @Autowired
    private OprtService oprtService;
    @Override
    protected void doFilterInternal(HttpServletRequest httpServletRequest,
                                    HttpServletResponse httpServletResponse,
                                    FilterChain filterChain) throws ServletException, IOException {
        //从header中拿到token
        final String token = JWTUtil.getToken(httpServletRequest);
        if(StringUtils.isNotEmpty(token)) {
            String username = JWTUtil.getUsernameFromToken(token);
            if (username != null) {
                //根据token中的账户获取用户信息
                AuthenticationBean authenticationBean = oprtService.loadOprt(username);
                if(authenticationBean == null){
                    throw new ApplicationException("username:"+username+"not found");
                }
                String password = authenticationBean.getPassword();
                String ipAddr = WMSUtil.getIpAddr(httpServletRequest);
                boolean isValid = true;
                try {
                    isValid = JWTUtil.validateToken(token, password,ipAddr);
                }catch (InvalidTokenException e){
                    //token失效，重新生成token,如果refreshToken过期，则认证失败
                    isValid = jwtUtil.refreshToken(httpServletResponse,authenticationBean);
                }

                if (isValid) {
                    log.debug("username:"+username+" token 验证成功！");
                    UsernamePasswordAuthenticationToken authentication = null;
                    try {
                        authentication = new UsernamePasswordAuthenticationToken(username, password, getAuthentication(username));
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(
                                httpServletRequest));
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        filterChain.doFilter(httpServletRequest, httpServletResponse);
    }
    public Collection<? extends GrantedAuthority> getAuthentication(String no) throws SQLException {
        List<Map<String, Object>> list = jdbcUtil.selectByParams("select sr.role_name authority,u.no username\n" +
                "        from sys_operator u\n" +
                "        LEFT JOIN sys_oprt_role sor on u.no= sor.oprt_no\n" +
                "        LEFT JOIN sys_role sr on sor.role_no=sr.role_no\n" +
                "        LEFT JOIN sys_role_permission srp on srp.role_no=sr.role_no\n" +
                "        LEFT JOIN sys_permission sp on sp.permission_no =srp.permission_no\n" +
                "        where u.no=?", no);

        List<SimpleGrantedAuthority> v2 = new ArrayList<>();
        SimpleGrantedAuthority v3 = null;
        for(Map<String,Object> v : list){
            v3 = new SimpleGrantedAuthority((String)v.get("authority"));
            v2.add(v3);
        }
        return v2;
    }
}
