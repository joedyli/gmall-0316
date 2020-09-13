package com.atguigu.gmall.wms.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.wms.vo.SkuLockVo;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.wms.mapper.WareSkuMapper;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.atguigu.gmall.wms.service.WareSkuService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;


@Service("wareSkuService")
public class WareSkuServiceImpl extends ServiceImpl<WareSkuMapper, WareSkuEntity> implements WareSkuService {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private WareSkuMapper wareSkuMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final String KEY_PREFIX = "stock:lock:";

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<WareSkuEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<WareSkuEntity>()
        );

        return new PageResultVo(page);
    }

    @Transactional
    @Override
    public List<SkuLockVo> checkAndLockStock(List<SkuLockVo> lockVos, String orderToken) {

        // 判空
        if (CollectionUtils.isEmpty(lockVos)){
            return null;
        }

        lockVos.forEach(lockVo -> {
            // 验库存并锁库存，保证原子性
            checkLock(lockVo);
        });

        // 如果任何一个商品锁定失败，返回锁定商品的具体信息
        if (lockVos.stream().anyMatch(skuLockVo -> !skuLockVo.getLock())){
            // 在返回之前，要把锁定成功的商品，先给解锁库存
            List<SkuLockVo> successLockVos = lockVos.stream().filter(SkuLockVo::getLock).collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(successLockVos)){
                successLockVos.forEach(lockVo -> {
                    this.wareSkuMapper.unlock(lockVo.getWareSkuId(), lockVo.getCount());
                });
            }

            // 返回锁定状态
            return lockVos;
        }

        // 为了方便将来减库存 或者一直不支付（解锁库存），需要把锁定库存信息缓存到redis中，以OrderToken作为key缓存
        this.redisTemplate.opsForValue().set(KEY_PREFIX + orderToken, JSON.toJSONString(lockVos));

        // 发送延时消息，定时解锁库存
        this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "stock.ttl", orderToken);

        // 如果返回值为空，说明锁库存成功
        return null;
    }

    private void checkLock(SkuLockVo lockVo){
        RLock lock = this.redissonClient.getLock("stock:lock:" + lockVo.getSkuId());
        lock.lock();

        try {
            // 验库存，查询
            List<WareSkuEntity> wareSkuEntities = this.wareSkuMapper.checkLock(lockVo.getSkuId(), lockVo.getCount());
            if (CollectionUtils.isEmpty(wareSkuEntities)){
                // 如果满足要求的仓库列表为空，说明验库存失败
                lockVo.setLock(false);
                return;
            }

            // 锁库存，更新。正常情况会就近调配，这里直接取第一个仓库发货
            Long id = wareSkuEntities.get(0).getId();
            Integer count = lockVo.getCount();
            if (this.wareSkuMapper.lock(id, count) == 1){
                lockVo.setLock(true);
                lockVo.setWareSkuId(id);
            }
        } finally {
            lock.unlock();
        }
    }
}
