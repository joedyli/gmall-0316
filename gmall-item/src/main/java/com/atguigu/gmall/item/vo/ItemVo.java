package com.atguigu.gmall.item.vo;

import com.atguigu.gmall.pms.vo.GroupVo;
import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.pms.entity.SkuImagesEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ItemVo {

    // 三级分类集合：3个元素
    private List<CategoryEntity> categories;

    // 品牌信息
    private Long brandId;
    private String brandName;

    // spu信息
    private Long spuId;
    private String spuName;

    // sku信息
    private Long skuId;
    private String title;
    private String subTitle;
    private String defaultImage;
    private BigDecimal price;
    private BigDecimal weight;

    // 优惠信息
    private List<ItemSaleVo> sales;

    // 库存信息
    private Boolean store = false;

    // sku图片列表
    private List<SkuImagesEntity> images;

    // 同一个spu下所有sku可选值列表
    // [{attrId:3, attrName:机身颜色, attrValues:[黑色，白色，金色]},
    //  {attrId:4, attrName:运行内存, attrValues:[6G, 8G, 12G]},
    //  {attrId:5, attrName:机身存储, attrValues:[128G, 256G, 512G]}]
    private List<SaleAttrValueVo> saleAttrs;

    // 当前sku销售属性
    // {3: 白色, 4: 8G, 5: 512G}
    private Map<Long, String> saleAttr;

    // sku销售属性和skuId的映射关系
    // {"黑色,8G,128G": 100, "白色,8G,128G": 101, "黑色,8G,256G": 102}
    private String skuJsons;

    // 海报信息，spu图片列表
    private List<String> spuImages;

    // 规格参数组与组下规格参数名和值
    private List<GroupVo> groups;
}
