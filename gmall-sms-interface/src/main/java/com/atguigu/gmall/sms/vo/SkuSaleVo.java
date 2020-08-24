package com.atguigu.gmall.sms.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SkuSaleVo {

    private Long skuId;

    // sku的积分优惠
    private BigDecimal growBounds;
    private BigDecimal buyBounds;
    private List<Integer> work;

    // sku的满减信息
    private BigDecimal fullPrice;
    private BigDecimal reducePrice;
    private Integer fullAddOther;

    // sku的打折信息
    private Integer fullCount;
    private BigDecimal discount;
    private Integer ladderAddOther;
}
