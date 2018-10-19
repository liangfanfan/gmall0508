package com.atguigu.gmall.service;

import com.atguigu.gmall.bean.PaymentInfo;

import java.util.Map;

public interface PaymentService {
    void savePayment(PaymentInfo paymentInfo);

    PaymentInfo updatePayment(PaymentInfo paymentInfo);

    void sendPaymentSuccessQueue(String out_trade_no, String alipayTradeNo);

    void sendPaymentCheckQueue(String outTradeNo,int count);

    Map<String,String> checkAlipayPayment(String outTradeNo);

    void updatePaymentSuccess(PaymentInfo paymentInfo);

    boolean checkStatus(String outTradeNo);
}
