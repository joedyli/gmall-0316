package com.atguigu.gmall.search.pojo;

import com.atguigu.gmall.pms.entity.BrandEntity;
import com.atguigu.gmall.pms.entity.CategoryBrandEntity;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import lombok.Data;

import java.util.List;

@Data
public class SearchResponseVo {

    private List<BrandEntity> brands; // 品牌过滤列表

    private List<CategoryEntity> categories; // 分类过滤列表

    private List<SearchResponseAttrVo> filters; // 属性过滤列表

    // 分页参数
    private Long total;
    private Integer pageNum;
    private Integer pageSize;

    private List<Goods> goodsList;
}
