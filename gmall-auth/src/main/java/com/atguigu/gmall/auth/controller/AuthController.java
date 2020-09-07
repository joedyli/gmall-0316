package com.atguigu.gmall.auth.controller;

import com.atguigu.gmall.auth.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * 跳转到登录页面，获取returnUrl
     *
     */
    @GetMapping("toLogin.html")
    public String toLogin(@RequestParam(value = "returnUrl", defaultValue = "http://gmall.com")String returnUrl, Model model){
        model.addAttribute("returnUrl", returnUrl);
        return "login";
    }


    /**
     * 登录方法：获取用户名和密码
     */
    @PostMapping("login")
    public String accredit(
            @RequestParam("loginName")String loginName,
            @RequestParam("password")String password,
            @RequestParam("returnUrl")String returnUrl,
            HttpServletRequest request,
            HttpServletResponse response
    ){
        this.authService.accredit(loginName, password, request, response);

        return "redirect:" + returnUrl;
    }
}
