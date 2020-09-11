package com.atguigu.gmall.scheduled.jdk;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;

public class TimerDemo {

    public static void main(String[] args) {
        System.out.println("任务初始化时间：" + System.currentTimeMillis());

        new DelayTask().scheduleAtFixedRate(() -> {
            System.out.println("执行了定时任务：" + System.currentTimeMillis());
        }, 5, 10, TimeUnit.SECONDS);

//        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(3);
//        scheduledExecutorService.schedule(() -> {
//            System.out.println("执行了定时任务：" + System.currentTimeMillis());
//        }, 5, TimeUnit.SECONDS);
//        scheduledExecutorService.scheduleAtFixedRate(() -> {
//            System.out.println("执行了定时任务：" + System.currentTimeMillis());
//        }, 5, 10, TimeUnit.SECONDS);

//        new Timer().schedule(new TimerTask() {
//            @Override
//            public void run() {
//                System.out.println("执行了定时任务：" + System.currentTimeMillis());
//            }
//        }, 5000, 10000);

    }


}

class DelayTask implements Delayed {

    private long time; // 延时任务的执行时间

    private final DelayQueue<DelayTask> delayQueue = new DelayQueue<>();

    int flag = 1;

    /**
     * 无限循环执行，当该方法的返回值小于等于0时，该元素才会出队
     * @param unit
     * @return
     */
    @Override
    public long getDelay(TimeUnit unit) {
        return this.time - System.currentTimeMillis();
    }

    /**
     * 通过该方法给任务排队
     * @param o
     * @return
     */
    @Override
    public int compareTo(Delayed o) {
        return (int) (this.time - ((DelayTask)o).time);
    }

    public void schedule(Runnable runnable, long delay, TimeUnit timeUnit){
        // 初始化任务执行时间
        this.time = System.currentTimeMillis() + timeUnit.toMillis(delay);

        try {
            delayQueue.put(this);
            delayQueue.take();

            new Thread(runnable).start();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void scheduleAtFixedRate(Runnable runnable, long delay, long period, TimeUnit timeUnit){
        // 初始化任务执行时间
        this.time = System.currentTimeMillis() + timeUnit.toMillis(delay);

        while (flag > 0) {
            if (flag > 1){
                this.time += timeUnit.toMillis(period);
            }
            try {
                delayQueue.put(this);
                delayQueue.take();

                new Thread(runnable).start();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            flag++;
        }
    }
}
