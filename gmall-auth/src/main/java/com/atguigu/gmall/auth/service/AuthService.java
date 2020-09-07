package com.atguigu.gmall.auth.service;

import com.atguigu.gmall.auth.config.JwtProperties;
import com.atguigu.gmall.auth.feign.GmallUmsClient;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.UserException;
import com.atguigu.gmall.common.utils.CookieUtils;
import com.atguigu.gmall.common.utils.IpUtil;
import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.ums.entity.UserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

@EnableConfigurationProperties({JwtProperties.class})
@Service
public class AuthService {

    //@Autowired
    private JwtProperties jwtProperties;

    public AuthService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @Autowired
    private GmallUmsClient umsClient;

    public void accredit(String loginName, String password, HttpServletRequest request, HttpServletResponse response) {

        // 1. 远程调用ums数据接口查询用户信息
        ResponseVo<UserEntity> userEntityResponseVo = this.umsClient.queryUser(loginName, password);
        UserEntity userEntity = userEntityResponseVo.getData();

        // 2. 判空
        if (userEntity == null){
            throw new UserException("用户名或密码错误。。。");
        }

        // 3. 生成jwt载荷信息
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userEntity.getId());
        map.put("userName", userEntity.getUsername());

        // 4. 防盗用，加入用户ip
        String ip = IpUtil.getIpAddressAtService(request);
        map.put("ip", ip);

        try {
            // 5. 生成jwt类型的token
            String token = JwtUtils.generateToken(map, this.jwtProperties.getPrivateKey(), this.jwtProperties.getExpire());

            // 6. 把jwt放入cookie中
            CookieUtils.setCookie(request, response, this.jwtProperties.getCookieName(), token, this.jwtProperties.getExpire() * 60);

            // 7. 把用户昵称放入cookie中
            CookieUtils.setCookie(request, response, this.jwtProperties.getUnick(), userEntity.getNickname(), this.jwtProperties.getExpire() * 60);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
