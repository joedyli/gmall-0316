package com.atguigu.gmall.cart.controller;

import com.atguigu.gmall.cart.interceptor.LoginInterceptor;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.CartException;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@Controller
public class CartController {

    @Autowired
    private CartService cartService;

    /**
     * 新增购物车
     * 新增成功之后，重定向到回显页面
     * @return
     */
    @GetMapping
    public String addCart(Cart cart){
        if (cart == null || cart.getSkuId() == null){
            throw new CartException("请选择加入购物车的商品！");
        }
        this.cartService.addCart(cart);
        return "redirect:http://cart.gmall.com/addCart.html?skuId=" + cart.getSkuId();
    }

    @GetMapping("addCart.html")
    public String queryCartBySkuId(@RequestParam("skuId")Long skuId, Model model) throws JsonProcessingException {
        Cart cart = this.cartService.queryCartBySkuId(skuId);
        model.addAttribute("cart", cart);
        return "addCart";
    }

    @GetMapping("cart.html")
    public String queryCartsByUserId(Model model){
        List<Cart> carts = this.cartService.queryCartsByUserId();
        model.addAttribute("carts", carts);
        return "cart";
    }

    @PostMapping("updateNum")
    @ResponseBody
    public ResponseVo<Object> updateNum(@RequestBody Cart cart){
        this.cartService.updateNum(cart);
        return ResponseVo.ok();
    }

    @PostMapping("deleteCart")
    @ResponseBody
    public ResponseVo<Object> deleteCart(@RequestParam("skuId")Long skuId){
        this.cartService.deleteCart(skuId);
        return ResponseVo.ok();
    }

    @GetMapping("user/{userId}")
    @ResponseBody
    public ResponseVo<List<Cart>> queryCheckedCartsByUserId(@PathVariable("userId")Long userId){
        List<Cart> carts = this.cartService.queryCheckedCartsByUserId(userId);
        return ResponseVo.ok(carts);
    }

    @GetMapping("test")
    @ResponseBody
    public String test(HttpServletRequest request){
        long now = System.currentTimeMillis();
        System.out.println("controller方法开始执行========");
        this.cartService.executor1();
        this.cartService.executor2();
//        future1.addCallback(t -> System.out.println("controller方法获取了future1的返回结果集" + t),
//                ex -> System.out.println("controller方法获取了future1的异常信息" + ex.getMessage()));
//        future2.addCallback(t -> System.out.println("controller方法获取了future2的返回结果集" + t),
//                ex -> System.out.println("controller方法获取了future2的异常信息" + ex.getMessage()));
//        try {
//            System.out.println(future1.get());
//            System.out.println("controller手动打印：" + future2.get());
//        } catch (Exception e) {
//            System.out.println("controller捕获异常后的打印：" + e.getMessage());
//        }
        System.out.println("controller方法结束执行++++++++" + (System.currentTimeMillis() - now));
        return "hello test";
    }
}
