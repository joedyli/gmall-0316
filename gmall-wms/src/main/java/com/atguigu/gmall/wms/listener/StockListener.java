package com.atguigu.gmall.wms.listener;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.wms.mapper.WareSkuMapper;
import com.atguigu.gmall.wms.vo.SkuLockVo;
import com.rabbitmq.client.Channel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class StockListener {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "stock:lock:";

    @Autowired
    private WareSkuMapper wareSkuMapper;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "STOCK_UNLOCK_QUEUE", durable = "true"),
            exchange = @Exchange(value = "ORDER_EXCHANGE", ignoreDeclarationExceptions = "true", type = ExchangeTypes.TOPIC),
            key = {"order.failure", "stock.dead"}
    ))
    public void unlock(String orderToken, Channel channel, Message message) throws IOException {

        // 判断orderToken是否为空
        if (StringUtils.isBlank(orderToken)){
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            return;
        }

        // 以orderTOken为key查询锁定库存信息的缓存
        String json = this.redisTemplate.opsForValue().get(KEY_PREFIX + orderToken);
        // 锁定库存信息不为空，直接解锁库存
        if (StringUtils.isNotBlank(json)){
            List<SkuLockVo> skuLockVos = JSON.parseArray(json, SkuLockVo.class);
            skuLockVos.forEach(skuLockVo -> {
                this.wareSkuMapper.unlock(skuLockVo.getWareSkuId(), skuLockVo.getCount());
            });
            // 防止重复解锁库存
            this.redisTemplate.delete(KEY_PREFIX + orderToken);
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "STOCK_MINUS_QUEUE", durable = "true"),
            exchange = @Exchange(value = "ORDER_EXCHANGE", ignoreDeclarationExceptions = "true", type = ExchangeTypes.TOPIC),
            key = {"stock.minus"}
    ))
    public void minus(String orderToken, Channel channel, Message message) throws IOException {

        // 判断orderToken是否为空
        if (StringUtils.isBlank(orderToken)){
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            return;
        }

        // 以orderTOken为key查询锁定库存信息的缓存
        String json = this.redisTemplate.opsForValue().get(KEY_PREFIX + orderToken);
        // 锁定库存信息不为空，直接解锁库存
        if (StringUtils.isNotBlank(json)){
            List<SkuLockVo> skuLockVos = JSON.parseArray(json, SkuLockVo.class);
            skuLockVos.forEach(skuLockVo -> {
                this.wareSkuMapper.minus(skuLockVo.getWareSkuId(), skuLockVo.getCount());
            });
            // 防止重复解锁库存
            this.redisTemplate.delete(KEY_PREFIX + orderToken);
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }
}
