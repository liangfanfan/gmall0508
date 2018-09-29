package com.atguigu.gmall.cart.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.CartInfo;
import com.atguigu.gmall.bean.UserInfo;
import com.atguigu.gmall.cart.mapper.CartInfoMapper;
import com.atguigu.gmall.service.CartService;
import com.atguigu.gmall.util.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;
import tk.mybatis.mapper.entity.Example;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CartServiceImpl implements CartService {

    @Autowired
    CartInfoMapper cartInfoMapper;

    @Autowired
    RedisUtil redisUtil;

    @Override
    public CartInfo ifCartExits(CartInfo cartInfo) {
        String skuId = cartInfo.getSkuId();
        String userId = cartInfo.getUserId();

        Example example = new Example(CartInfo.class);
        example.createCriteria().andEqualTo("skuId",skuId).andEqualTo("userId",userId);
        CartInfo info = cartInfoMapper.selectOneByExample(example);
        return info;
    }

    @Override
    public void updateCart(CartInfo cartInfoDb) {
        cartInfoMapper.updateByPrimaryKeySelective(cartInfoDb);

        // 同步缓存
        flushCartCacheByUserId(cartInfoDb.getUserId());
    }

    @Override
    public void saveCartInfo(CartInfo cartInfo) {
        cartInfoMapper.insertSelective(cartInfo);
        // 同步缓存
        flushCartCacheByUserId(cartInfo.getUserId());
    }

    @Override
    public void flushCartCacheByUserId(String userId) {
        CartInfo cartInfo = new CartInfo();
        cartInfo.setUserId(userId);
        List<CartInfo> cartInfoList = cartInfoMapper.select(cartInfo);

        if(null!=cartInfoList && cartInfoList.size()>0){
            Map<String,String> map = new HashMap<>();
            for (CartInfo info : cartInfoList) {
                map.put(info.getId(), JSON.toJSONString(info));
            }
            Jedis jedis = redisUtil.getJedis();
            jedis.del("cart:" + userId + ":list");
            jedis.hmset("cart:" + userId + ":list",map);
            jedis.close();
        }else {
            Jedis jedis = redisUtil.getJedis();
            jedis.del("cart:" + userId + ":list");
            jedis.close();
        }


    }

    @Override
    public List<CartInfo> getCartInfosFromCacheByUserId(String userId) {
        List<CartInfo> cartInfoList = new ArrayList<>();

        Jedis jedis = redisUtil.getJedis();
        List<String> hvals = jedis.hvals("cart:" + userId + ":list");
        if(null!=hvals && hvals.size()>0){
            for (String s : hvals) {
                CartInfo cartInfo = JSON.parseObject(s, CartInfo.class);
                cartInfoList.add(cartInfo);
            }
        }
        jedis.close();
        return cartInfoList;
    }

    @Override
    public void updateCartByUserId(CartInfo cartInfo) {
        Example example = new Example(CartInfo.class);
        example.createCriteria().andEqualTo(cartInfo.getSkuId()).andEqualTo(cartInfo.getUserId());
        cartInfoMapper.updateByExampleSelective(cartInfo,example);
        // 同步缓存
        flushCartCacheByUserId(cartInfo.getUserId());
    }

    /**
     * 合并cookie中的购物车数据到数据库
     * @param userId 登录的账号ID
     * @param listCartCookie cookie中的购物车数据
     * 判断是更新还是添加
     */
    @Override
    public void combine(String userId, List<CartInfo> listCartCookie) {
        // 获取数据库中的购物车数据
        CartInfo cartInfo = new CartInfo();
        cartInfo.setUserId(userId);
        List<CartInfo> listCartDb = cartInfoMapper.select(cartInfo);
        // cookie中数据不为空时遍历
        if(null!=listCartCookie && listCartCookie.size()>0){
            for (CartInfo cartCookie : listCartCookie) {
               String skuId = cartCookie.getSkuId();

                boolean b = true;
                // 数据库中数据是否为空
                if(null!=listCartDb && listCartDb.size()>0){
                    // 数据库中数据是否有cookie中相同数据
                    b = if_new_cart(listCartDb,cartCookie);
                }
                if(!b){
                    CartInfo cartDb = new CartInfo();
                    // 更新
                    // 遍历数据库中的购物车数据，
                    for (CartInfo info : listCartDb) {
                        // 如果数据库中的商品和cookie中商品一样,便是更新数据库中此商品信息
                        if(info.getSkuId().equals(skuId)){
                            cartDb = info;
                        }
                    }
                    cartDb.setSkuNum(cartCookie.getSkuNum());
                    cartDb.setIsChecked(cartCookie.getIsChecked());
                    cartDb.setCartPrice(cartDb.getSkuPrice().multiply(new BigDecimal(cartDb.getSkuNum())));
                    cartInfoMapper.updateByPrimaryKeySelective(cartDb);
                }else {
                    // 添加
                    cartCookie.setUserId(userId);
                    cartInfoMapper.insertSelective(cartCookie);
                }

            }
        }

        // 同步刷新缓存
        flushCartCacheByUserId(userId);
    }

    @Override
    public void deleteCart(String join, String userId) {

        cartInfoMapper.deleteCartsById(join);

        // 同步刷新缓存
        flushCartCacheByUserId(userId);
    }


    /***
     * 判断购物车数据更新还是新增
     * @param listCartDb
     * @param cartInfo
     * @return
     */
    private boolean if_new_cart(List<CartInfo> listCartDb, CartInfo cartInfo) {

        boolean b = true;

        for (CartInfo info : listCartDb) {
            if (info.getSkuId().equals(cartInfo.getSkuId())) {
                // 有相同的数据 为更新
                b = false;
            }
        }

        return b;
    }
}
