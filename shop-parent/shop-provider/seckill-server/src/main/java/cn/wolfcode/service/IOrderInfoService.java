package cn.wolfcode.service;


import cn.wolfcode.common.domain.UserInfo;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.PayResult;
import cn.wolfcode.domain.RefundLog;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.mq.OrderMessage;
import org.junit.jupiter.api.Order;
import org.springframework.cache.annotation.CachePut;

/**
 * Created by wolfcode
 */
public interface IOrderInfoService {

    OrderInfo selectByUserIdAndSeckillId(Long phone, Long seckillId, Integer time);

    OrderInfo doSeckill(UserInfo userInfo, SeckillProductVo vo);

    OrderInfo doSeckill(Long phone, SeckillProductVo vo);

    OrderInfo doSeckill(Long phone, Long seckillId, Integer time);

    OrderInfo selectByOrderNo(String orderNo);

    void failedRollback(OrderMessage message);

    void checkPayTimeout(OrderMessage orderMessage);

    String onlinePay(String orderNo);

    void alipaySuccess(PayResult result);

    void alipayRefund(OrderInfo orderInfo);

    void refund(String orderNo);

    void integralPay(String orderNo, Long phone);

    void integralRefundRollback(String orderNo);

    RefundLog selectRefundLogByOrderNo(String orderNo);
    //@Transactional(rollbackFor = Exception.class)
    //String doSeckill(Long phone, SeckillProductVo vo);
}
