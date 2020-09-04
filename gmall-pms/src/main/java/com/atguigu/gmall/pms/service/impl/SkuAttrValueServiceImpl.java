package com.atguigu.gmall.pms.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.pms.mapper.SkuMapper;
import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.SkuAttrValueMapper;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.service.SkuAttrValueService;
import org.springframework.util.CollectionUtils;


@Service("skuAttrValueService")
public class SkuAttrValueServiceImpl extends ServiceImpl<SkuAttrValueMapper, SkuAttrValueEntity> implements SkuAttrValueService {


    @Autowired
    private SkuAttrValueMapper attrValueMapper;

    @Autowired
    private SkuMapper skuMapper;

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<SkuAttrValueEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<SkuAttrValueEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public List<SkuAttrValueEntity> querySearchAttrValuesBySkuId(Long skuId) {
        return this.attrValueMapper.querySearchAttrValuesBySkuId(skuId);
    }

    @Override
    public List<SaleAttrValueVo> querySaleAttrValueVoBySpuId(Long spuId) {
        // 根据spuid查询sku
        List<SkuEntity> skuEntities = this.skuMapper.selectList(new QueryWrapper<SkuEntity>().eq("spu_id", spuId));
        if (CollectionUtils.isEmpty(skuEntities)){
            return null;
        }
        // 获取skuId集合
        List<Long> skuIds = skuEntities.stream().map(SkuEntity::getId).collect(Collectors.toList());

        // 根据skuIds查询销售属性
        List<SkuAttrValueEntity> skuAttrValueEntities = this.attrValueMapper.selectList(new QueryWrapper<SkuAttrValueEntity>().in("sku_id", skuIds));

        // 数据转化成List<SaleAttrValueVo>返回
        List<SaleAttrValueVo> saleAttrValueVos = new ArrayList<>();
        Map<Long, List<SkuAttrValueEntity>> map = skuAttrValueEntities.stream().collect(Collectors.groupingBy(t -> t.getAttrId()));
        map.forEach((attrId, skuAttrValues) -> {
            SaleAttrValueVo saleAttrValueVo = new SaleAttrValueVo();
            saleAttrValueVo.setAttrId(attrId);
            saleAttrValueVo.setAttrName(skuAttrValues.get(0).getAttrName());
            Set<String> attrValues = skuAttrValues.stream().map(SkuAttrValueEntity::getAttrValue).collect(Collectors.toSet());
            saleAttrValueVo.setAttrValues(attrValues);
            saleAttrValueVos.add(saleAttrValueVo);
        });

        return saleAttrValueVos;
    }

    @Override
    public String querySaleAttrValuesMappingSkuIdBySpuId(Long spuId) {
        List<Map<String, Object>> maps = this.attrValueMapper.querySaleAttrValuesMappingSkuIdBySpuId(spuId);
        if (CollectionUtils.isEmpty(maps)){
            return null;
        }
        Map<String, Long> saleAttrValuesMappingSkuIdMap  = maps.stream().collect(Collectors.toMap(map -> map.get("attrvalues").toString(), map -> (Long) map.get("sku_id")));
        return JSON.toJSONString(saleAttrValuesMappingSkuIdMap);
    }

}
