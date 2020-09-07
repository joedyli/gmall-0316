package com.atguigu.gmall.item.service;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.ItemException;
import com.atguigu.gmall.item.feign.GmallPmsClient;
import com.atguigu.gmall.item.feign.GmallSmsClient;
import com.atguigu.gmall.item.feign.GmallWmsClient;
import com.atguigu.gmall.item.vo.ItemVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.vo.GroupVo;
import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Time;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class ItemService {

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    public ItemVo loadData(Long skuId) {

        ItemVo itemVo = new ItemVo();

        CompletableFuture<SkuEntity> skuCompletableFuture = CompletableFuture.supplyAsync(() -> {
            // 1.根据skuId查询sku信息 Y
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(skuId);
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity == null) {
                throw new ItemException("该skuId对应的商品不存在！");
            }
            itemVo.setSkuId(skuId);
            itemVo.setTitle(skuEntity.getTitle());
            itemVo.setSubTitle(skuEntity.getSubtitle());
            itemVo.setPrice(skuEntity.getPrice());
            itemVo.setWeight(new BigDecimal(skuEntity.getWeight()));
            itemVo.setDefaultImage(skuEntity.getDefaultImage());
            return skuEntity;
        }, threadPoolExecutor);

        CompletableFuture<Void> cateCompletableFuture = skuCompletableFuture.thenAcceptAsync(skuEntity -> {
            // 2.根据cid3查询一二三级分类集合 Y
            ResponseVo<List<CategoryEntity>> catesResponseVo = this.pmsClient.queryAllCategoriesByCid3(skuEntity.getCatagoryId());
            List<CategoryEntity> categoryEntities = catesResponseVo.getData();
            itemVo.setCategories(categoryEntities);
        }, threadPoolExecutor);

        CompletableFuture<Void> brandCompletableFuture = skuCompletableFuture.thenAcceptAsync(skuEntity -> {
            // 3.根据品牌id查询品牌信息 Y
            ResponseVo<BrandEntity> brandEntityResponseVo = this.pmsClient.queryBrandById(skuEntity.getBrandId());
            BrandEntity brandEntity = brandEntityResponseVo.getData();
            if (brandEntity != null) {
                itemVo.setBrandId(brandEntity.getId());
                itemVo.setBrandName(brandEntity.getName());
            }
        }, threadPoolExecutor);

        CompletableFuture<Void> spuCompletableFuture = skuCompletableFuture.thenAcceptAsync(skuEntity -> {
            // 4.根据spuId查询spu信息 Y
            ResponseVo<SpuEntity> spuEntityResponseVo = this.pmsClient.querySpuById(skuEntity.getSpuId());
            SpuEntity spuEntity = spuEntityResponseVo.getData();
            if (spuEntity != null) {
                itemVo.setSpuId(spuEntity.getId());
                itemVo.setSpuName(spuEntity.getName());
            }
        }, threadPoolExecutor);

        CompletableFuture<Void> salesCompletableFuture = CompletableFuture.runAsync(() -> {
            // 5.根据skuId查询优惠信息（sms） Y
            ResponseVo<List<ItemSaleVo>> salesResponseVo = this.smsClient.querySaleVoBySkuId(skuId);
            List<ItemSaleVo> itemSaleVos = salesResponseVo.getData();
            itemVo.setSales(itemSaleVos);
        }, threadPoolExecutor);

        CompletableFuture<Void> wareCompletableFuture = CompletableFuture.runAsync(() -> {
            // 6.根据skuId查询库存信息 Y
            ResponseVo<List<WareSkuEntity>> wareResponseVo = this.wmsClient.queryWareSkusBySkuId(skuId);
            List<WareSkuEntity> wareSkuEntities = wareResponseVo.getData();
            if (!CollectionUtils.isEmpty(wareSkuEntities)) {
                itemVo.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
            }
        }, threadPoolExecutor);

        CompletableFuture<Void> imageCompletableFuture = CompletableFuture.runAsync(() -> {
            // 7.根据skuId查询sku的图片列表 Y
            ResponseVo<List<SkuImagesEntity>> imagesResponseVo = this.pmsClient.queryImagesBySkuId(skuId);
            List<SkuImagesEntity> skuImagesEntities = imagesResponseVo.getData();
            itemVo.setImages(skuImagesEntities);
        }, threadPoolExecutor);

        CompletableFuture<Void> saleAttrsCompletableFuture = skuCompletableFuture.thenAcceptAsync(skuEntity -> {
            // 8.根据spuId查询spu下所有sku的销售属性组合 Y
            ResponseVo<List<SaleAttrValueVo>> saleAttrsResponseVo = this.pmsClient.querySaleAttrValueVoBySpuId(skuEntity.getSpuId());
            List<SaleAttrValueVo> saleAttrValueVos = saleAttrsResponseVo.getData();
            itemVo.setSaleAttrs(saleAttrValueVos);
        }, threadPoolExecutor);

        CompletableFuture<Void> saleAttrCompletableFuture = CompletableFuture.runAsync(() -> {
            // 9.根据skuId查询当前sku的销售属性  Y
            ResponseVo<List<SkuAttrValueEntity>> saleAttrValueResponseVo = this.pmsClient.querySaleAttrValuesBySkuId(skuId);
            List<SkuAttrValueEntity> skuAttrValueEntities = saleAttrValueResponseVo.getData();
            if (!CollectionUtils.isEmpty(skuAttrValueEntities)) {
                // [{attrId: 3, attrName: 机身颜色, attrValue: 白色}, {}, {}] ====> {3: 白色, 4: 8G, 5: 512G}
                Map<Long, String> saleAttr = skuAttrValueEntities.stream().collect(Collectors.toMap(SkuAttrValueEntity::getAttrId, SkuAttrValueEntity::getAttrValue));
                itemVo.setSaleAttr(saleAttr);
            }
        }, threadPoolExecutor);

        CompletableFuture<Void> mappingCompletableFuture = skuCompletableFuture.thenAcceptAsync(skuEntity -> {
            // 10.根据spuId查询spu下所有销售属性组合和skuId的映射关系 Y
            ResponseVo<String> mappingResponseVo = this.pmsClient.querySaleAttrValuesMappingSkuIdBySpuId(skuEntity.getSpuId());
            String json = mappingResponseVo.getData();
            itemVo.setSkuJsons(json);
        }, threadPoolExecutor);

        CompletableFuture<Void> descCompletableFuture = skuCompletableFuture.thenAcceptAsync(skuEntity -> {
            // 11.根据spuId查询spu的海报信息列表 Y
            ResponseVo<SpuDescEntity> spuDescEntityResponseVo = this.pmsClient.querySpuDescById(skuEntity.getSpuId());
            SpuDescEntity spuDescEntity = spuDescEntityResponseVo.getData();
            if (spuDescEntity != null && StringUtils.isNotBlank(spuDescEntity.getDecript())) {
                String[] urls = StringUtils.split(spuDescEntity.getDecript(), ",");
                itemVo.setSpuImages(Arrays.asList(urls));
            }
        }, threadPoolExecutor);

        CompletableFuture<Void> groupCompletableFuture = skuCompletableFuture.thenAcceptAsync(skuEntity -> {
            // 12.根据cid3、spuId、skuId查询分组及组下的规格参数以及值
            ResponseVo<List<GroupVo>> groupResponseVo = this.pmsClient.queryGroupVoByCidAndSpuIdAndSkuId(skuEntity.getCatagoryId(), skuEntity.getSpuId(), skuId);
            List<GroupVo> groupVos = groupResponseVo.getData();
            itemVo.setGroups(groupVos);
        }, threadPoolExecutor);

        CompletableFuture.allOf(cateCompletableFuture, brandCompletableFuture, spuCompletableFuture,
                salesCompletableFuture, wareCompletableFuture, imageCompletableFuture, saleAttrsCompletableFuture,
                saleAttrCompletableFuture, mappingCompletableFuture, descCompletableFuture, groupCompletableFuture
        ).join();

        return itemVo;
    }

    public static void main(String[] args) throws IOException {

        CompletableFuture.runAsync(() -> {
            System.out.println("初始化了一个没有返回结果集的子任务");
        });

        CompletableFuture<String> afuture = CompletableFuture.supplyAsync(() -> {
            System.out.println("初始化了一个有返回结果集的子任务A");
//            int i = 1/0;
            return "hello CompletableFuture supplyAsync";
        });
        CompletableFuture<String> bfuture = afuture.thenApplyAsync(t -> {
            System.out.println("=================thenApplyAsync B==================");
            try {
                TimeUnit.SECONDS.sleep(3);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("上一个任务的返回结果：" + t);
            return "hello thenApplyAsync B";
        });
        CompletableFuture<String> cfuture = afuture.thenApplyAsync(t -> {
            System.out.println("=================thenApplyAsync C==================");
            try {
                TimeUnit.SECONDS.sleep(4);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("上一个任务的返回结果：" + t);
            return "hello thenApplyAsync C";
        });
        CompletableFuture<Void> dfuture = afuture.thenAcceptAsync(t -> {
            System.out.println("=================thenApplyAsync D==================");
            try {
                TimeUnit.SECONDS.sleep(3);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("上一个任务的返回结果：" + t);
        });
        CompletableFuture<Void> efuture = afuture.thenRunAsync(() -> {
            System.out.println("=================thenApplyAsync E==================");
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("上一个任务的返回结果：");
        });

        CompletableFuture.anyOf(bfuture, cfuture, dfuture, efuture).join();

        System.out.println("xxxxxxxxxxxxxxxxxxxxxxx");

//                .whenCompleteAsync((t, u) -> { // 上一个任务执行完就会执行该方法，不管任务有没有异常
//            System.out.println("=================whenComplete==================");
//            try {
//                TimeUnit.SECONDS.sleep(2);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//            System.out.println("t: " + t); // t是上一个任务的返回结果集
//            System.out.println("u: " + u); // u是上一个任务的异常信息
//        }).exceptionally(t -> { // 上一个任务出现异常时，才会执行
//            System.out.println("=================exceptionally==================");
//            System.out.println("t: " + t);
//            return "hello exceptionally!";
//        });

//        System.in.read();

       // new ThreadPoolExecutor(3, 5, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10));
//         ExecutorService executorService = Executors.newSingleThreadExecutor();
//        ExecutorService executorService = Executors.newFixedThreadPool(3);
//        ExecutorService executorService = Executors.newCachedThreadPool();
        //ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(2);
//        scheduledExecutorService.scheduleAtFixedRate(() -> {
//            System.out.println("这是一个定时任务：" + System.currentTimeMillis());
//        }, 5, 10, TimeUnit.SECONDS);
//        for (int i = 0; i < 5; i++) {
//            executorService.submit(() -> {
//                System.out.println(Thread.currentThread().getName() + "执行了一个子任务。。。");
//            });
//        }
//        FutureTask<String> futureTask = new FutureTask<>(new MyCallable());
//        new Thread(futureTask).start();
//        try {
//            // 阻塞方式获取
//            System.out.println(futureTask.get());
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        } catch (ExecutionException e) {
//            e.printStackTrace();
//        }
        //new MyThread().start();
        //new Thread(new MyRunnable()).start();
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                System.out.println("这是runnable的匿名内部类方式实现多线程。。。。。");
//            }
//        }).start();
//        new Thread(() -> {
//            System.out.println();
//        }, "").start();
    }
}

class MyThread extends Thread{
    @Override
    public void run() {
        System.out.println("这是thread方式实现多线程。。。。。");
    }
}

class MyRunnable implements Runnable{

    @Override
    public void run() {
        System.out.println("这是runnable方式实现多线程。。。。。");
    }
}

class MyCallable implements Callable<String> {

    @Override
    public String call() throws Exception {
        System.out.println("这是Callable方式实现多线程。。。。。");
        return "1111";
    }
}
