package com.atguigu.gmall.cart.service;

import com.atguigu.gmall.cart.feign.GmallPmsClient;
import com.atguigu.gmall.cart.feign.GmallSmsClient;
import com.atguigu.gmall.cart.feign.GmallWmsClient;
import com.atguigu.gmall.cart.pojo.Cart;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CartService {

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private GmallWmsClient wmsClient;

    public void addCart(Cart cart) {
        // 1.组装key

        // 2.获取该用户的购物车

        // 3.判断该用户是否已有该购物车记录

        // 有，则跟新数量

        // 无，则新增新的记录

    }

    public Cart queryCartBySkuId(Long skuId) {

        return null;
    }
}
