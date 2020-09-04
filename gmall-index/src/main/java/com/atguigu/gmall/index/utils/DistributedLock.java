package com.atguigu.gmall.index.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Component
public class DistributedLock {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Thread thread;

    public Boolean tryLock(String lockName, String uuid, Long expire){
        String script = "if (redis.call('exists', KEYS[1]) == 0 or redis.call('hexists', KEYS[1], ARGV[1])==1) then redis.call('hincrby', KEYS[1], ARGV[1], 1) redis.call('expire', KEYS[1], ARGV[2]) return 1 else return 0 end";
        Boolean flag = this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList(lockName), uuid, expire.toString());
        if (!flag){
            // 如果获取锁失败，则重试
            try {
                Thread.sleep(50);
                tryLock(lockName, uuid, expire);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        this.renewTime(lockName, expire);
        return true;
    }

    public void unlock(String lockName, String uuid){
        String script = "if redis.call('hexists', KEYS[1], ARGV[1]) == 0 then return nil end if (redis.call('hincrby', KEYS[1], ARGV[1], -1) > 0) then return 0 else redis.call('del', KEYS[1]) return 1 end";
        Boolean flag = this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList(lockName), uuid);
        if (flag == null){
            throw new RuntimeException("您在尝试解除别人的锁：" + lockName + ", uuid: " + uuid);
        }
        thread.interrupt();
    }

    public void renewTime(String lockName, Long expire){
        String script = "if redis.call('exists', KEYS[1]) == 1 then return redis.call('expire', KEYS[1], ARGV[1]) else return 0 end";
        thread = new Thread(() -> {
            while (this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList(lockName), expire.toString())) {
                try {
                    Thread.sleep(expire * 1000 * 2 / 3);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }
}
