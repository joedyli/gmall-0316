package com.atguigu.gmall.gateway.filter;

import com.atguigu.gmall.common.utils.IpUtil;
import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.gateway.config.JwtProperties;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
@EnableConfigurationProperties(JwtProperties.class)
public class AuthGatewayFilterFactory extends AbstractGatewayFilterFactory<AuthGatewayFilterFactory.PathConfig> {

    @Autowired
    private JwtProperties properties;

    public AuthGatewayFilterFactory() {
        super(PathConfig.class);
    }

    @Override
    public GatewayFilter apply(PathConfig config) {

        return new GatewayFilter() {
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

                System.out.println("这是一个局部过滤器，只过滤配置了该过滤器的服务" + config);

                // webflux提供的request对象 等价于HttpServletRequest
                ServerHttpRequest request = exchange.getRequest();
                // 等价于httpServletResponse对象
                ServerHttpResponse response = exchange.getResponse();

                // 1.判断当前路径，在不在拦截名单中，不在，直接放行。
                List<String> pathes = config.pathes;
                // webflux中的uri === url 。获取当前请求的路径
                String curPath = request.getURI().getPath();
                // 如果百名单为空，或者当前路径没有包含任何一个白名单中的路径，放行
                if (CollectionUtils.isEmpty(pathes) || !pathes.stream().anyMatch(path -> curPath.startsWith(path))){
                    return chain.filter(exchange);
                }

                // 2.获取token信息：异步通过头信息传递token，同步方式只能通过携带cookie的方式来传递
                String token = request.getHeaders().getFirst("token");
                if (StringUtils.isBlank(token)){
                    MultiValueMap<String, HttpCookie> cookies = request.getCookies();
                    if (cookies.containsKey(properties.getCookieName())){
                        HttpCookie cookie = cookies.getFirst(properties.getCookieName());
                        token = cookie.getValue();
                    }
                }

                // 3.判空token，重定向到登录页面
                if (StringUtils.isBlank(token)){
                    response.setStatusCode(HttpStatus.SEE_OTHER);
                    response.getHeaders().set(HttpHeaders.LOCATION, "http://sso.gmall.com/toLogin.html?returnUrl=" + request.getURI());
                    // 请求结束
                    return response.setComplete();
                }

                try {
                    // 4.解析token，解析出现异常，则重定向到登录页面
                    Map<String, Object> map = JwtUtils.getInfoFromToken(token, properties.getPublicKey());

                    // 5.防盗用，验证jwt载荷中的ip，是否和当前请求的ip一致，不一致，则重定向到登录页面
                    String curIp = IpUtil.getIpAddressAtGateway(request);// 当前用户ip
                    String ip = map.get("ip").toString(); // token所属用户ip
                    if (!StringUtils.equals(curIp, ip)){
                        response.setStatusCode(HttpStatus.SEE_OTHER);
                        response.getHeaders().set(HttpHeaders.LOCATION, "http://sso.gmall.com/toLogin.html?returnUrl=" + request.getURI());
                        // 请求结束
                        return response.setComplete();
                    }

                    // 6.把用户的登录信息传递给后续的服务
                    request.mutate().header("userId", map.get("userId").toString()).build();
                    exchange.mutate().request(request).build();
                } catch (Exception e) {
                    e.printStackTrace();
                    response.setStatusCode(HttpStatus.SEE_OTHER);
                    response.getHeaders().set(HttpHeaders.LOCATION, "http://sso.gmall.com/toLogin.html?returnUrl=" + request.getURI());
                    // 请求结束
                    return response.setComplete();
                }

                return chain.filter(exchange);
            }
        };
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return Arrays.asList("pathes");
    }

    @Override
    public ShortcutType shortcutType() {
        return ShortcutType.GATHER_LIST;
    }

    @Data
    public static class PathConfig {
        private List<String> pathes;
    }

    @Data
    public static class KeyValueConfig {

        private String key;
        private String value;
    }
}
