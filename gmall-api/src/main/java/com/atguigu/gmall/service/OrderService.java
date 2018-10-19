package com.atguigu.gmall.service;

import com.atguigu.gmall.bean.OrderInfo;

public interface OrderService {
    String genTradeCode(String userId);

    boolean checkTradeCode(String userId, String tradeCode);

    void saveOrder(OrderInfo orderInfo);

    OrderInfo getOrderInfoByOutTradeNo(String outTradeNo);

    void sendOrderResult(OrderInfo orderInfo);
}
