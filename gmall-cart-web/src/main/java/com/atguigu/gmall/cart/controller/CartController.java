package com.atguigu.gmall.cart.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.annotation.LoginRequire;
import com.atguigu.gmall.bean.CartInfo;
import com.atguigu.gmall.bean.SkuInfo;
import com.atguigu.gmall.service.CartService;
import com.atguigu.gmall.service.SkuService;
import com.atguigu.gmall.util.CookieUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class CartController {

    @Reference
    SkuService skuService;

    @Reference
    CartService cartService;


    /**
     *  是购物车多选框更新选中状态方法
     * @param cartInfo
     * @param request
     * @param response
     * @param map
     * @return
     */
    @LoginRequire(needSuccess = false)
    @RequestMapping(value = "checkCart",method = RequestMethod.POST)
    public String checkCart(CartInfo cartInfo,HttpServletRequest request,HttpServletResponse response, ModelMap map){
        List<CartInfo> cartInfoList = new ArrayList<>();

        String userId =(String) request.getAttribute("userId");
        String skuId = cartInfo.getSkuId();

        // 是否登录
        if(StringUtils.isNotBlank(userId)){
            cartInfo.setUserId(userId);
            cartService.updateCartByUserId(cartInfo);
            cartInfoList = cartService.getCartInfosFromCacheByUserId(userId);

        }else {
            // 更新cookie的数据
            String listCartCookie = CookieUtil.getCookieValue(request, "listCartCookie", true);
            cartInfoList = JSON.parseArray(listCartCookie,CartInfo.class);
            for (CartInfo info : cartInfoList) {
                String skuId1 = info.getSkuId();
                if(skuId1.equals(skuId)){
                    info.setIsChecked(cartInfo.getIsChecked());
                }
            }
            CookieUtil.setCookie(request,response,"listCartCookie",JSON.toJSONString(cartInfoList),1000*60*60*24,true);

        }

        map.put("cartList",cartInfoList);
        BigDecimal totalPrice = getTotalPrice(cartInfoList);
        map.put("totalPrice",totalPrice);
        return "cartListInner";
    }


    @LoginRequire(needSuccess = false)
    @RequestMapping("cartList")
    public String cartList(HttpServletRequest request, ModelMap map){
        List<CartInfo> cartInfoList = new ArrayList<>();
        String userId = (String) request.getAttribute("userId");
        // 取出购物车数据
        if(StringUtils.isBlank(userId)){
            // 从cookie取
            String cookieValue = CookieUtil.getCookieValue(request, "listCartCookie", true);
            if(StringUtils.isNotBlank(cookieValue)){
                cartInfoList = JSON.parseArray(cookieValue,CartInfo.class);
            }
        }else {
            // 从Redis取
            cartInfoList = cartService.getCartInfosFromCacheByUserId(userId);
        }

        map.put("cartList",cartInfoList);
        BigDecimal totalPrice = getTotalPrice(cartInfoList);
        map.put("totalPrice",totalPrice);
        return "cartList";
    }

    private BigDecimal getTotalPrice(List<CartInfo> cartInfoList) {
        BigDecimal totalPrice = new BigDecimal("0");
        for (CartInfo cartInfo : cartInfoList) {
            String isChecked = cartInfo.getIsChecked();
            if(isChecked.equals("1")){
                totalPrice = totalPrice.add(cartInfo.getCartPrice());
            }
        }

        return totalPrice;
    }


    @LoginRequire(needSuccess = false)
    @RequestMapping(value = "addToCart",method = RequestMethod.POST)
    public String addToCart(HttpServletRequest request, HttpServletResponse response, @RequestParam Map<String, String> map){

        //购物车集合
        List<CartInfo> cartInfoList = new ArrayList<>();
        // 接收数据
        String skuId = map.get("skuId");
        String skuNum = map.get("num");
        // 是否登录
        String userId = (String) request.getAttribute("userId");
        // 查询当前数据的所有信息
        SkuInfo skuInfo = skuService.getSkuById(skuId);
        // 用来封装的购物车对象
        CartInfo cartInfo = new CartInfo();
        // 封装新传来的数据，
        cartInfo.setSkuId(skuId);
        cartInfo.setSkuName(skuInfo.getSkuName());
        cartInfo.setImgUrl(skuInfo.getSkuDefaultImg());
        cartInfo.setIsChecked("1");
        cartInfo.setSkuNum(Integer.parseInt(skuNum));
        cartInfo.setSkuPrice(skuInfo.getPrice());
        cartInfo.setCartPrice(cartInfo.getSkuPrice().multiply(new BigDecimal(cartInfo.getSkuNum())));

        // 判断是否登录
        if(StringUtils.isBlank(userId)){
            cartInfo.setUserId(userId);
            // 添加到cookie
            String cartCookie = CookieUtil.getCookieValue(request, "listCartCookie", true);
            // 判断cookie中是否空车
            if(StringUtils.isBlank(cartCookie)){
                // 新车，直接加数据
                cartInfoList.add(cartInfo);
            }else{
                cartInfoList = JSON.parseArray(cartCookie, CartInfo.class);
                // 判断是更新数据还是新增数据
                boolean b = if_new_cart(cartInfoList, cartInfo);

                if(b){
                    // 新增
                    cartInfoList.add(cartInfo);
                }else {
                    // 更新
                    for (CartInfo info : cartInfoList) {
                        info.setSkuNum(info.getSkuNum() + cartInfo.getSkuNum());
                        info.setCartPrice(info.getSkuPrice().multiply(new BigDecimal(info.getSkuNum())));

                    }
                }
            }
            // 将购物车数据放入cookie
            CookieUtil.setCookie(request,response,"listCartCookie",JSON.toJSONString(cartInfoList),1000*60*60*24,true);

        }else{
            // 添加已经登录的账户ID
            cartInfo.setUserId(userId);
            // 判断数据库中是否有数据
            CartInfo cartInfoDb =  cartService.ifCartExits(cartInfo);

            if(null!=cartInfoDb){
                // 有数据更新
                cartInfoDb.setSkuNum(cartInfoDb.getSkuNum() + cartInfo.getSkuNum());
                cartInfoDb.setCartPrice(cartInfoDb.getSkuPrice().multiply(new BigDecimal(cartInfoDb.getSkuNum())));
                cartService.updateCart(cartInfoDb);
            }else{
                // 没有数据就添加
                cartService.saveCartInfo(cartInfo);
            }

            // 全部添加到缓存中
            cartService.flushCartCacheByUserId(userId);
        }

        
        return "redirect:/cartSuccess";
    }

    private boolean if_new_cart(List<CartInfo> cartInfoList, CartInfo cartInfo) {
        boolean b = true;

        for (CartInfo info : cartInfoList) {
            if(info.getSkuId().equals(cartInfo.getSkuId())){
                b = false;
            }
        }
        return b;
    }

    @LoginRequire(needSuccess = false)
    @RequestMapping("cartSuccess")
    public String cartSuccess(){

        return "success";
    }



}
