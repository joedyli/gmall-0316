package com.atguigu.gmall.payment.controller;

import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.bean.UserInfo;
import com.atguigu.gmall.common.exception.OrderException;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.payment.config.AlipayTemplate;
import com.atguigu.gmall.payment.entity.PaymentInfoEntity;
import com.atguigu.gmall.payment.interceptor.LoginInterceptor;
import com.atguigu.gmall.payment.service.PaymentService;
import com.atguigu.gmall.payment.vo.PayAsyncVo;
import com.atguigu.gmall.payment.vo.PayVo;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Controller
public class PaymetnController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private AlipayTemplate alipayTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

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

            PaymentInfoEntity paymentInfoEntity = this.paymentService.savePaymentInfo(payVo, 1);
            payVo.setPassback_params(paymentInfoEntity.getId().toString());
            String form = this.alipayTemplate.pay(payVo);
            return form;
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 给支付宝提供的同步回调接口
     * @return
     */
    @GetMapping("pay/success")
    public String paySuccess(PayAsyncVo payAsyncVo){

        return "paySuccess";
    }

    /**
     * 异步回调接口
     * 以该接口为准，更新订单的支付状态，并真正的减库存
     * @return
     */
    @PostMapping("pay/async/success")
    @ResponseBody
    public String payAsyncSuccess(PayAsyncVo payAsyncVo){
        System.out.println(payAsyncVo);

        // 1.验签
        Boolean flag = alipayTemplate.checkSignature(payAsyncVo);
        if (!flag){
            return "failure";
        }

        // 2.校验业务参数
        // 回调回来的业务数据
        String payId = payAsyncVo.getPassback_params(); // 对账记录的唯一标识
        // 如果没有原样回调的该参数，说明该回调可能是伪造的
        if(StringUtils.isBlank(payId)){
            return "failure";
        }
        String app_id = payAsyncVo.getApp_id();
        String out_trade_no = payAsyncVo.getOut_trade_no();
        String total_amount = payAsyncVo.getTotal_amount();
        // 获取工程中的业务数据
        PaymentInfoEntity paymentInfoEntity = this.paymentService.queryPaymentInfoById(payId);
        if (!StringUtils.equals(app_id, this.alipayTemplate.getApp_id())
                || !StringUtils.equals(out_trade_no, paymentInfoEntity.getOutTradeNo())
                || paymentInfoEntity.getTotalAmount().compareTo(new BigDecimal(total_amount)) != 0){
            return "failure";
        }

        // 3.校验支付状态：TRADE_SUCCESS
        if (!StringUtils.equals("TRADE_SUCCESS", payAsyncVo.getTrade_status())){
            return "failure";
        }

        // 4.更新支付对账表
        if (this.paymentService.updatePaymentInfo(payAsyncVo) == 1) {
            // 5.更新oms和wms，同步/异步
            this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "order.success", out_trade_no);
            // 6.返回success
            return "success";
        }
        return "failure";
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    private static final Integer COUNT = 2000;

    @GetMapping("seckill/{skuId}")
    public ResponseVo<Object> seckill(@PathVariable("skuId")Long skuId){
        // 获取登录信息
        UserInfo userInfo = LoginInterceptor.getUserInfo();

        String stockString = this.redisTemplate.opsForValue().get("seckill:stock:" + skuId);
        if (StringUtils.isBlank(stockString)){
            throw new OrderException("手慢了，秒杀结束。请下次再来！");
        }

        int num = Integer.parseInt(stockString) > COUNT ? COUNT : Integer.parseInt(stockString);

        RSemaphore semaphore = this.redissonClient.getSemaphore("seckill:semaphore:" + skuId);
        semaphore.trySetPermits(num);

        boolean flag = semaphore.tryAcquire();
        if (flag){
            this.redisTemplate.opsForValue().decrement("seckill:stock:" + skuId);

            String orderToken = IdWorker.getTimeId();
            Map<String, Object> map = new HashMap<>();
            map.put("skuId", skuId);
            map.put("count", 1);
            map.put("userId", userInfo.getUserId());
            this.redisTemplate.opsForValue().set("seckill:success:" + orderToken, JSON.toJSONString(map));

            this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "seckill.success", orderToken);
            semaphore.release();

            RCountDownLatch countDownLatch = this.redissonClient.getCountDownLatch("seckill:latch:" + userInfo.getUserId());
            countDownLatch.trySetCount(1);
            // 需要在异步创建订单的方法中，通过
            // countDownLatch.countDown()
            return ResponseVo.ok("恭喜您，秒杀成功！");
        }
        return ResponseVo.fail("秒杀失败，下次再来！");
    }

    @GetMapping("seckill/query")
    public ResponseVo<OrderEntity>  queryOrder() throws InterruptedException {
        UserInfo userInfo = LoginInterceptor.getUserInfo();

        RCountDownLatch countDownLatch = this.redissonClient.getCountDownLatch("seckill:latch:" + userInfo.getUserId());
        countDownLatch.await();

        // TODO: 查询订单

        return ResponseVo.ok(null);
    }
}
