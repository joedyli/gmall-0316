package com.atguigu.gmall.pms.service.impl;

import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.feign.GmallSmsClient;
import com.atguigu.gmall.pms.mapper.SkuMapper;
import com.atguigu.gmall.pms.mapper.SpuDescMapper;
import com.atguigu.gmall.pms.service.*;
import com.atguigu.gmall.pms.vo.SkuVo;
import com.atguigu.gmall.pms.vo.SpuAttrValueVo;
import com.atguigu.gmall.pms.vo.SpuVo;
import com.atguigu.gmall.sms.vo.SkuSaleVo;
import io.seata.spring.annotation.GlobalTransactional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.SpuMapper;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;


@Service("spuService")
public class SpuServiceImpl extends ServiceImpl<SpuMapper, SpuEntity> implements SpuService {

    @Autowired
    private SpuDescMapper descMapper;

    @Autowired
    private SpuAttrValueService spuAttrValueService;

    @Autowired
    private SkuMapper skuMapper;

    @Autowired
    private SkuImagesService imagesService;

    @Autowired
    private SkuAttrValueService attrValueService;

    @Autowired
    private SpuDescService spuDescService;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<SpuEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<SpuEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public PageResultVo querySpuByCidPage(Long cid, PageParamVo pageParamVo) {
        QueryWrapper<SpuEntity> wrapper = new QueryWrapper<>();

        // 判断cid是否不为0
        if (cid != 0){
            wrapper.eq("category_id", cid);
        }

        // 判断key不为空要有关键字查询
        String key = pageParamVo.getKey();
        if (StringUtils.isNotBlank(key)){
            wrapper.and(t -> t.eq("id", key).or().like("name", key));
        }

        IPage<SpuEntity> page = this.page(
                pageParamVo.getPage(),
                wrapper
        );

        return new PageResultVo(page);
    }

    @GlobalTransactional
    @Override
    public void bigSave(SpuVo spu) throws FileNotFoundException {
        // 1.spu相关表信息保存：pms_spu pms_spu_desc pms_spu_attr_value
        // 1.1. 保存pms_spu
        Long spuId = saveSpu(spu);  // 保存失败，回滚

        // 1.2. 保存pms_spu_desc
        //this.saveSpuDesc(spu, spuId);
        this.spuDescService.saveSpuDesc(spu, spuId);  // 保存成功

//        try {
//            TimeUnit.SECONDS.sleep(4);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        //new FileInputStream("xxxx");

        // 1.3. 保存pms_spu_attr_value
        saveBaseAttr(spu, spuId);

        // 2.sku相关信息表保存：pms_sku pms_skuImages pms_sku_attr_value
        saveSku(spu, spuId);

        this.rabbitTemplate.convertAndSend("PMS_SPU_EXCHANGE", "item.insert", spuId);
        //int i = 1/0;
    }

    private void saveSku(SpuVo spu, Long spuId) {
        List<SkuVo> skus = spu.getSkus();
        if (CollectionUtils.isEmpty(skus)){
            return;
        }
        // 遍历保存sku的相关信息
        skus.forEach(skuVo -> {
            // 2.1. 保存pms_sku
            skuVo.setId(null);
            skuVo.setSpuId(spuId);
            skuVo.setBrandId(spu.getBrandId());
            skuVo.setCatagoryId(spu.getCategoryId());
            List<String> images = skuVo.getImages();
            if (!CollectionUtils.isEmpty(images)){
                skuVo.setDefaultImage(skuVo.getDefaultImage() == null ? images.get(0) : skuVo.getDefaultImage());
            }
            this.skuMapper.insert(skuVo);
            Long skuId = skuVo.getId();

            // 2.2. 保存pms_skuImages
            if (!CollectionUtils.isEmpty(images)){
                List<SkuImagesEntity> imagesEntities = images.stream().map(image -> {
                    SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                    skuImagesEntity.setId(null);
                    skuImagesEntity.setSkuId(skuId);
                    skuImagesEntity.setUrl(image);
                    skuImagesEntity.setSort(1);
                    skuImagesEntity.setDefaultStatus(0);
                    if (StringUtils.equals(skuVo.getDefaultImage(), image)){
                        skuImagesEntity.setDefaultStatus(1);
                    }
                    return skuImagesEntity;
                }).collect(Collectors.toList());
                this.imagesService.saveBatch(imagesEntities);
            }

            // 2.3. 保存pms_sku_attr_value
            List<SkuAttrValueEntity> saleAttrs = skuVo.getSaleAttrs();
            if (!CollectionUtils.isEmpty(saleAttrs)){
                saleAttrs.forEach(attr -> {
                    attr.setSkuId(skuId);
                    attr.setSort(0);
                    attr.setId(null);
                });
                this.attrValueService.saveBatch(saleAttrs);
            }

            // 3.优惠信息表保存
            SkuSaleVo skuSaleVo = new SkuSaleVo();
            BeanUtils.copyProperties(skuVo, skuSaleVo);
            skuSaleVo.setSkuId(skuId);
            this.smsClient.saveSkuSales(skuSaleVo);
        });
    }

    private void saveBaseAttr(SpuVo spu, Long spuId) {
        List<SpuAttrValueVo> baseAttrs = spu.getBaseAttrs();
        if (!CollectionUtils.isEmpty(baseAttrs)){
            List<SpuAttrValueEntity> spuAttrValueEntities = baseAttrs.stream().map(spuAttrValueVo -> {
                SpuAttrValueEntity baseEntity = new SpuAttrValueEntity();
                BeanUtils.copyProperties(spuAttrValueVo, baseEntity);
                baseEntity.setSpuId(spuId);
                baseEntity.setSort(1);
                baseEntity.setId(null);
                return baseEntity;
            }).collect(Collectors.toList());
            this.spuAttrValueService.saveBatch(spuAttrValueEntities);
        }
    }

    private Long saveSpu(SpuVo spu) {
        spu.setCreateTime(new Date());
        spu.setUpdateTime(spu.getCreateTime());
        spu.setId(null); // 防止id注入，显式的设置id为null
        this.save(spu);
        return spu.getId();
    }

}

//class Test{
//
//    public static void main(String[] args) {
//
//        List<User> users = Arrays.asList(
//                new User("柳岩", 20, 1),
//                new User("马蓉", 21, 1),
//                new User("亮亮", 22, 2),
//                new User("小鹿", 23, 1),
//                new User("老王", 24, 2)
//        );
//
//        // stream流式函数：
//        // 初始化：users.stream  Stream.of()
//        // 中间处理：filter map reduce
//        // 结束：collect(collector.toList/toSet/toMap)
//        System.out.println(users.stream().filter(t -> t.getSex() == 1).collect(Collectors.toList()));
//        System.out.println(users.stream().map(t -> t.getName()).collect(Collectors.toList()));
//        System.out.println(users.stream().map(User::getAge).reduce((a, b) -> a + b).get());
//
//        List<Person> persons = users.stream().map(user -> {
//            Person person = new Person();
//            person.setUserName(user.getName());
//            person.setAge(user.getAge());
//            return person;
//        }).collect(Collectors.toList());
//        System.out.println(persons);
//    }
//}
//
//@Data
//@AllArgsConstructor
//@NoArgsConstructor
//class User{
//    private String name;
//    private Integer age;
//    private Integer sex;
//}
//
//@Data
//class Person{
//    private String userName;
//    private Integer age;
//}
