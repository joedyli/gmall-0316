package com.atguigu.gmall.order.vo;

import com.atguigu.gmall.oms.vo.OrderItemVo;
import com.atguigu.gmall.ums.entity.UserAddressEntity;
import lombok.Data;

import java.util.List;

@Data
public class OrderConfirmVo {

    private List<UserAddressEntity> addresses; // 地址列表

    private List<OrderItemVo> orderItems; // 送货清单

    private Integer bounds; // 用户购买积分信息

    private String orderToken; // 防重的唯一标识
}
