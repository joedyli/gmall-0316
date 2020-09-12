package com.atguigu.gmall.wms.vo;

import lombok.Data;

@Data
public class SkuLockVo {

    private Long skuId;
    private Integer count;

    private Boolean lock = false; // 锁定状态
    private Long wareSkuId; // 锁定成功的情况下，记录锁定仓库的id
}
