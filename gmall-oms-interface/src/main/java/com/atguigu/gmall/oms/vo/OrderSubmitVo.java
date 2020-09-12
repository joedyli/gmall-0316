package com.atguigu.gmall.oms.vo;

import com.atguigu.gmall.ums.entity.UserAddressEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderSubmitVo {

    private String orderToken; // 防重的唯一标识

    private BigDecimal totalPrice; // 验总价

    private UserAddressEntity address; // 收货地址信息

    private Integer payType; // 支付方式

    private String deliveryCompany; // 配送方式：快递公司

    private Integer bounds;

    private List<OrderItemVo> items; // 订单详情信息

}
