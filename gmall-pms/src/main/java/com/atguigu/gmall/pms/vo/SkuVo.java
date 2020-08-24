package com.atguigu.gmall.pms.vo;

import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SkuVo extends SkuEntity {

    // sku的图片列表
    private List<String> images;

    // sku销售属性值
    private List<SkuAttrValueEntity> saleAttrs;

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
