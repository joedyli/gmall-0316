package com.atguigu.gmall.payment.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.payment.entity.PaymentInfoEntity;
import com.atguigu.gmall.payment.feign.GmallOmsClient;
import com.atguigu.gmall.payment.mapper.PaymentInfoMapper;
import com.atguigu.gmall.payment.vo.PayAsyncVo;
import com.atguigu.gmall.payment.vo.PayVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;

@Service
public class PaymentService {

    @Autowired
    private GmallOmsClient omsClient;

    @Autowired
    private PaymentInfoMapper paymentInfoMapper;

    public OrderEntity queryOrderByToken(String orderToken) {

        ResponseVo<OrderEntity> orderEntityResponseVo = this.omsClient.queryOrderByToken(orderToken);
        return orderEntityResponseVo.getData();
    }

    public PaymentInfoEntity savePaymentInfo(PayVo payVo, Integer payType){
        PaymentInfoEntity paymentInfoEntity = new PaymentInfoEntity();

        paymentInfoEntity.setPaymentStatus(0);
        paymentInfoEntity.setOutTradeNo(payVo.getOut_trade_no());
        paymentInfoEntity.setPaymentType(payType);
        paymentInfoEntity.setSubject(payVo.getSubject());
        paymentInfoEntity.setTotalAmount(new BigDecimal(payVo.getTotal_amount()));
        paymentInfoEntity.setCreateTime(new Date());

        this.paymentInfoMapper.insert(paymentInfoEntity);
        return paymentInfoEntity;
    }

    public PaymentInfoEntity queryPaymentInfoById(String payId){
        return this.paymentInfoMapper.selectById(payId);
    }

    public int updatePaymentInfo(PayAsyncVo payAsyncVo) {
        PaymentInfoEntity paymentInfoEntity = this.paymentInfoMapper.selectById(payAsyncVo.getPassback_params());
        paymentInfoEntity.setTradeNo(payAsyncVo.getTrade_no());
        paymentInfoEntity.setCallbackTime(new Date());
        paymentInfoEntity.setCallbackContent(JSON.toJSONString(payAsyncVo));
        paymentInfoEntity.setPaymentStatus(1);
        return this.paymentInfoMapper.updateById(paymentInfoEntity);
    }
}
