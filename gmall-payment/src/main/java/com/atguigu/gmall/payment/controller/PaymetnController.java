package com.atguigu.gmall.payment.controller;

import com.alipay.api.AlipayApiException;
import com.atguigu.gmall.common.bean.UserInfo;
import com.atguigu.gmall.common.exception.OrderException;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.payment.config.AlipayTemplate;
import com.atguigu.gmall.payment.interceptor.LoginInterceptor;
import com.atguigu.gmall.payment.service.PaymentService;
import com.atguigu.gmall.payment.vo.PayVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class PaymetnController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private AlipayTemplate alipayTemplate;

    @GetMapping("pay.html")
    public String toPay(@RequestParam("orderToken") String orderToken, Model model){

        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();

        OrderEntity orderEntity = this.paymentService.queryOrderByToken(orderToken);
        if (orderEntity == null || orderEntity.getUserId() != userId || orderEntity.getStatus() != 0) {
            throw new OrderException("此订单不存在或者不属于您");
        }

        model.addAttribute("orderEntity", orderEntity);

        return "pay";
    }

    @GetMapping("alipay.html")
    @ResponseBody
    public String toAlipay(@RequestParam("orderToken")String orderToken){

        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();

        OrderEntity orderEntity = this.paymentService.queryOrderByToken(orderToken);
        if (orderEntity == null || orderEntity.getUserId() != userId || orderEntity.getStatus() != 0) {
            throw new OrderException("此订单不存在或者不属于您");
        }

        try {
            PayVo payVo = new PayVo();
            payVo.setTotal_amount("0.01"); // 这里注意：写0.01  保留两位小数
            payVo.setSubject("谷粒商城支付平台");
            payVo.setPassback_params(null);
            payVo.setOut_trade_no(orderToken);
            String form = this.alipayTemplate.pay(payVo);
            return form;
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        return null;
    }

    @GetMapping("pay/success")
    public String paySuccess(){

        return "paySuccess";
    }

    @PostMapping("pay/async/success")
    public Object payAsyncSuccess(){

        return null;
    }

}
