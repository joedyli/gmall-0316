package com.atguigu.gmall.oms.listener;

import com.atguigu.gmall.oms.mapper.OrderMapper;
import com.rabbitmq.client.Channel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class OrderListener {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "ORDER_FAILURE_QUEUE", durable = "true"),
            exchange = @Exchange(value = "ORDER_EXCHANGE", ignoreDeclarationExceptions = "true", type = ExchangeTypes.TOPIC),
            key = {"order.failure"}
    ))
    public void failure(String orderToken, Channel channel, Message message) throws IOException {

        if (StringUtils.isNotBlank(orderToken)){
            this.orderMapper.updateStatus(orderToken, 5, 0);
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

    @RabbitListener(queues = "ORDER_DEAD_QUEUE")
    public void close(String orderToken, Channel channel, Message message) throws IOException {

        if (StringUtils.isNotBlank(orderToken)){
            if(this.orderMapper.updateStatus(orderToken, 4, 0) == 1){
                this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "order.failure", orderToken);
            }
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "ORDER_SUCCESS_QUEUE", durable = "true"),
            exchange = @Exchange(value = "ORDER_EXCHANGE", ignoreDeclarationExceptions = "true", type = ExchangeTypes.TOPIC),
            key = {"order.success"}
    ))
    public void success(String orderToken, Channel channel, Message message) throws IOException {

        if (StringUtils.isNotBlank(orderToken) && this.orderMapper.updateStatus(orderToken, 1, 0) == 1){
            // 发送消息给wms 减库存
            this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "stock.minus", orderToken);

            // 发送消息给ums 加积分
            // TODO: 查询订单，获取用户id 可以获得的成长值以及购物积分
            Map<String, Object> map = new HashMap<>();
            this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "user.bounds", map);
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }
}
