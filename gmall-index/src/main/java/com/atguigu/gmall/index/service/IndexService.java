package com.atguigu.gmall.index.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.index.config.GmallCache;
import com.atguigu.gmall.index.feign.GmallPmsClient;
import com.atguigu.gmall.index.utils.DistributedLock;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class IndexService {

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "index:cates:";

    @Autowired
    private DistributedLock distributedLock;

    @Autowired
    private RedissonClient redissonClient;

    public List<CategoryEntity> querylvl1Categories() {
        ResponseVo<List<CategoryEntity>> listResponseVo = this.pmsClient.queryCategoriesByPid(0l);
        return listResponseVo.getData();
    }

    @GmallCache(prefix = KEY_PREFIX, timeout = 43200, random = 7200, lock = "lock")
    public List<CategoryEntity> queryLvl2CategoriesWithSub(Long pid) {
        ResponseVo<List<CategoryEntity>> listResponseVo = this.pmsClient.queryCategoriesWithSubByPid(pid);
        List<CategoryEntity> categoryEntities = listResponseVo.getData();
        return categoryEntities;
    }

    public List<CategoryEntity> queryLvl2CategoriesWithSub2(Long pid) {
        // 1.查询缓存
        String json = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);
        if (StringUtils.isNotBlank(json)) {
            return JSON.parseArray(json, CategoryEntity.class);
        }

        RLock lock = this.redissonClient.getLock("lock:" + pid);
        lock.lock();

        String json2 = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);
        if (StringUtils.isNotBlank(json2)) {
            lock.unlock();
            return JSON.parseArray(json2, CategoryEntity.class);
        }

        // 2.远程调用，查询数据库，并放入缓存
        ResponseVo<List<CategoryEntity>> listResponseVo = this.pmsClient.queryCategoriesWithSubByPid(pid);
        List<CategoryEntity> categoryEntities = listResponseVo.getData();

        // 1.为了解决缓存穿透，返回值为null依然要缓存
        // 2.为了解决缓存雪崩，给过期时间添加了随机值
        this.redisTemplate.opsForValue().set(KEY_PREFIX + pid, JSON.toJSONString(categoryEntities), 30 + new Random().nextInt(5), TimeUnit.DAYS);

        lock.unlock();

        return categoryEntities;
    }

    public void testLock() {

        RLock lock = this.redissonClient.getLock("lock");
        lock.lock();

        // 获取成功，则执行业务操作
        String numString = this.redisTemplate.opsForValue().get("num");
        if (StringUtils.isBlank(numString)) {
            this.redisTemplate.opsForValue().set("num", "0");
            return;
        }
        int num = Integer.parseInt(numString);
        this.redisTemplate.opsForValue().set("num", String.valueOf(++num));

        lock.unlock();
    }

    public void testLock3() {
        // 通过setnx获取锁
        String uuid = UUID.randomUUID().toString();
        Boolean lock = this.distributedLock.tryLock("lock", uuid, 30l);

        if(lock){
            // 获取成功，则执行业务操作
            String numString = this.redisTemplate.opsForValue().get("num");
            if (StringUtils.isBlank(numString)) {
                this.redisTemplate.opsForValue().set("num", "0");
                return;
            }
            try {
                TimeUnit.SECONDS.sleep(60);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            int num = Integer.parseInt(numString);
            this.redisTemplate.opsForValue().set("num", String.valueOf(++num));
        }
        this.testSubLock(uuid);
        this.distributedLock.unlock("lock", uuid);
    }

    public void testSubLock(String uuid){
        Boolean lock = this.distributedLock.tryLock("lock", uuid, 30l);
        System.out.println("=======================");
        this.distributedLock.unlock("lock", uuid);
    }

    public void testLock2() {
        // 通过setnx获取锁
        String uuid = UUID.randomUUID().toString();
        Boolean lock = this.redisTemplate.opsForValue().setIfAbsent("lock", uuid, 3, TimeUnit.SECONDS);
        // 获取失败，则重试
        if(!lock){
            try {
                Thread.sleep(100);
                testLock();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            //this.redisTemplate.expire("lock", 3, TimeUnit.SECONDS);
            // 获取成功，则执行业务操作
            String numString = this.redisTemplate.opsForValue().get("num");
            if (StringUtils.isBlank(numString)) {
                this.redisTemplate.opsForValue().set("num", "0");
                return;
            }
            int num = Integer.parseInt(numString);
            this.redisTemplate.opsForValue().set("num", String.valueOf(++num));

            // 释放锁（del）
            // 为了防误删，需要判断当前锁是不是自己的锁
            // 为了保证原子性，这里来使用lua脚本
            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
            this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList("lock"), uuid);

//            if(StringUtils.equals(uuid, this.redisTemplate.opsForValue().get("lock"))){
//                this.redisTemplate.delete("lock");
//            }
        }
    }

    public void testRead() {
        RReadWriteLock rwLock = this.redissonClient.getReadWriteLock("rwLock");
        rwLock.readLock().lock(10, TimeUnit.SECONDS);
        System.out.println("读操作。。。。。。");
    }

    public void testWrite() {
        RReadWriteLock rwLock = this.redissonClient.getReadWriteLock("rwLock");
        rwLock.writeLock().lock(10, TimeUnit.SECONDS);
        System.out.println("写操作。。。。。。。");
    }

    public void testCountDown() {
        RCountDownLatch latch = this.redissonClient.getCountDownLatch("latch");
        // TODO：业务
        latch.countDown();
    }

    public void testLatch() throws InterruptedException {
        RCountDownLatch latch = this.redissonClient.getCountDownLatch("latch");
        latch.trySetCount(6l);
        latch.await();
        // TODO：业务
    }
}
