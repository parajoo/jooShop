package cn.wolfcode.service.impl;

import cn.wolfcode.common.domain.UserInfo;
import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.*;
import cn.wolfcode.feign.PaymentFeignApi;
import cn.wolfcode.feign.fallback.IntegralFeignApi;
import cn.wolfcode.mapper.OrderInfoMapper;
import cn.wolfcode.mapper.PayLogMapper;
import cn.wolfcode.mapper.RefundLogMapper;
import cn.wolfcode.mq.DefaultSendCallback;
import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.mq.OrderMessage;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.util.AssertUtils;
import cn.wolfcode.util.IdGenerateUtil;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import com.alibaba.fastjson.JSON;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * Created by wolfcode
 */
@Slf4j
@Service
@CacheConfig(cacheNames = "orders")
public class OrderInfoServiceImpl implements IOrderInfoService {
    private final ISeckillProductService seckillProductService;
    private final OrderInfoMapper orderInfoMapper;
    private final StringRedisTemplate redisTemplate;
    private final PayLogMapper payLogMapper;
    private final RefundLogMapper refundLogMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final PaymentFeignApi paymentFeignApi;
    private final IntegralFeignApi integralFeignApi;

    public OrderInfoServiceImpl(ISeckillProductService seckillProductService, OrderInfoMapper orderInfoMapper, StringRedisTemplate redisTemplate, PayLogMapper payLogMapper, RefundLogMapper refundLogMapper, RocketMQTemplate rocketMQTemplate, PaymentFeignApi paymentFeignApi, IntegralFeignApi integralFeignApi) {
        this.seckillProductService = seckillProductService;
        this.orderInfoMapper = orderInfoMapper;
        this.redisTemplate = redisTemplate;
        this.payLogMapper = payLogMapper;
        this.refundLogMapper = refundLogMapper;
        this.rocketMQTemplate = rocketMQTemplate;
        this.paymentFeignApi = paymentFeignApi;
        this.integralFeignApi = integralFeignApi;
    }

    @Override
    public OrderInfo selectByUserIdAndSeckillId(Long userId, Long seckillId, Integer time) {
        return orderInfoMapper.selectByUserIdAndSeckillId(userId, seckillId, time);
    }
    @Transactional(rollbackFor = Exception.class)
    @Override
    public OrderInfo doSeckill(UserInfo userInfo, SeckillProductVo vo) {
        // 从 UserInfo 里拿到手机号，调用另一个方法
        return doSeckill(userInfo.getPhone(), vo);
    }
    @Transactional(rollbackFor = Exception.class)
    @Override
    public OrderInfo doSeckill(Long phone, SeckillProductVo vo) {
        // 1. 扣除秒杀商品库存
        seckillProductService.decrStockCount(vo.getId());//,vo.getTime()
        // 2. 创建秒杀订单并保存
        OrderInfo orderInfo = this.buildOrderInfo(phone, vo);

        // 3. 返回订单编号
        orderInfoMapper.insert(orderInfo);
        return orderInfo;
    }
    @Cacheable(key = "'detail:'+#orderNo")
    @Override
    public OrderInfo selectByOrderNo(String orderNo){
        return orderInfoMapper.selectById(orderNo);
    }

    @CachePut(key = "'detail:'+ #result.orderNo")
    @Transactional(rollbackFor = Exception.class)
    @Override
    public OrderInfo doSeckill(Long phone, Long seckillId,Integer time) {
        SeckillProductVo sp = seckillProductService.selectByIdAndTime(seckillId, time);
        return this.doSeckill(phone,sp);
    }



    @Override
    public void failedRollback(OrderMessage message) {
        //1.rollback:数据库不需要回补，只有redis需要--这里DB的回滚由事务操作
        this.RollBackRedisStock(message.getSeckillId(),message.getTime());
        //2.delete user sign
        String userOrderFlag = SeckillRedisKey.SECKILL_ORDER_HASH.join(message.getSeckillId() + "");
        redisTemplate.opsForHash().delete(userOrderFlag,message.getUserPhone()+"");
        //3.delete local sign ==> 通过mq发送广播消息 让每一个服务
        rocketMQTemplate.asyncSend(MQConstant.CANCEL_SECKILL_OVER_SIGE_TOPIC,message.getSeckillId(),new DefaultSendCallback("取消本地标识"));
    }

    private void RollBackRedisStock(Long seckillId,Integer time) {
        Long stockCount = seckillProductService.selectStockCountId(seckillId);
        String hashKey = SeckillRedisKey.SECKILL_STOCK_COUNT_HASH.join(time + "");
        redisTemplate.opsForHash().put(hashKey, seckillId+"",stockCount+"");
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void checkPayTimeout(OrderMessage orderMessage) {
        //1.基于订单编号 查询订单对象
        //2.未支付，则取消订单
        int row = orderInfoMapper.changePayStatus(orderMessage.getOrderNo(), OrderInfo.STATUS_CANCEL, OrderInfo.PAY_TYPE_ONLINE);
        if (row>0){
            //3.mysql该秒杀商品库存加1
            seckillProductService.incrStockCount(orderMessage.getSeckillId());
            //4.失败订单信息回滚：redis库存删除用户下单标识 删除本地标识
            this.failedRollback(orderMessage);
        }
    }

    @Override
    public String onlinePay(String orderNo) {
        //1.基于订单号 查询订单对象
        OrderInfo orderInfo = this.selectByOrderNo(orderNo);
        //2.判断订单状态是否为未支付状态，未支付才能发起支付请求
        AssertUtils.isTrue(OrderInfo.STATUS_ARREARAGE.equals(orderInfo.getStatus()),"订单状态异常，无法发起支付");
        //封装支付参数
        PayVo vo = new PayVo();
        vo.setBody("秒杀："+orderInfo.getProductName());
        vo.setSubject(orderInfo.getProductName());
        vo.setOutTradeNo(orderNo);
        vo.setTotalAmount(orderInfo.getSeckillPrice().toString());//big decimal->string
        //远程调用支付服务支付
        Result<String> result = paymentFeignApi.prepay(vo);
        return result.checkAndGet();
    }

    @Override
    public void alipaySuccess(PayResult result) {
        //1.获取订单信息对象
        OrderInfo orderInfo = this.selectByOrderNo(result.getOutTradeNo());
        AssertUtils.notNull(orderInfo,"订单信息有误");
        //2.判断订单信息是否正确
        AssertUtils.isTrue(orderInfo.getSeckillPrice().toString().equals(result.getTotalAmount()),"支付金额有误");
        //3.更新订单状态  保证幂等性
        int row = orderInfoMapper.changePayStatus(result.getTradeNo(), OrderInfo.STATUS_ACCOUNT_PAID, OrderInfo.PAY_TYPE_ONLINE);
        AssertUtils.isTrue(row>0,"订单状态修改失败");
        //4.记录支付日志
        PayLog payLog = new PayLog();
        payLog.setPayType(OrderInfo.PAY_TYPE_ONLINE);
        payLog.setTotalAmount(result.getTotalAmount());
        payLog.setOutTradeNo(result.getOutTradeNo());
        payLog.setTradeNo(result.getTradeNo());
        payLog.setNotifyTime(System.currentTimeMillis()+"");
        payLogMapper.insert(payLog);
    }

    @Override
    public void alipayRefund(OrderInfo orderInfo) {

    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void refund(String orderNo) {
        //1.query order object based on orderNo
        OrderInfo orderInfo = orderInfoMapper.selectById(orderNo);
        //2.判断订单状态=已支付
        AssertUtils.isTrue(OrderInfo.STATUS_ACCOUNT_PAID.equals(orderInfo.getStatus()),"订单状态错误");
        //3.判断支付类型 不同类型调用不同退款接口
        //封装退款Vo对象
        RefundVo refundVo = new RefundVo(orderNo,orderInfo.getSeckillPrice().toString(),"不想要了");
        Result<Boolean> result = null;
        if(orderInfo.getPayType() == orderInfo.PAY_TYPE_ONLINE){
            //调用支付宝退款接口
            result = paymentFeignApi.refund(refundVo);
        }
        else{
            //调用integral退款接口
            //result = integralFeignApi.refund(refundVo);
            log.info("[integral refund]准备发送积分退款事务消息: {} ", JSON.toJSONString(refundVo));
            //积分退款：发送RocketMQ事务消息
            Message<RefundVo> message = MessageBuilder.withPayload(refundVo).setHeader("orderNo",orderNo).build();
            TransactionSendResult sendResult = rocketMQTemplate.sendMessageInTransaction(MQConstant.INTEGRAL_REFUND_TX_GROUP,
                    MQConstant.INTEGRAL_REFUND_TX_TOPIC, message, orderNo);
            //判断sendResult是否未为COMMIT=>判断本地事务是否执行成功
            if(
                    sendResult.getLocalTransactionState().equals(LocalTransactionState.COMMIT_MESSAGE) ||
                sendResult.getLocalTransactionState().equals(LocalTransactionState.UNKNOW)){
                log.info("[integral refund]积分退款本地事务执行成功，等待远程服务执行状态: {} ", sendResult.getLocalTransactionState());
                return;
            }
        }
        //判断是否退款成功，成功才进行以下操作
        if(result == null || result.hasError() || !result.getData()){
            throw new BusinessException(SeckillCodeMsg.REFUND_ERROR);
        }
        this.refundRollback(orderInfo);
    }

    private void refundRollback(OrderInfo orderInfo) {
        //4.更新订单状态为已退款
        int row = orderInfoMapper.changeRefundStatus(orderInfo.getOrderNo(), OrderInfo.STATUS_REFUND);
        AssertUtils.isTrue(row>0,"退款失败，更新状态异常");
        //5.库存回补(MySQL+Redis)
        seckillProductService.incrStockCount(orderInfo.getSeckillId());
        this.RollBackRedisStock(orderInfo.getSeckillId(), orderInfo.getSeckillTime());
        //6.删除本地下单标识
        rocketMQTemplate.asyncSend(MQConstant.CANCEL_SECKILL_OVER_SIGE_TOPIC, orderInfo.getSeckillId(),new DefaultSendCallback("取消本地标识"));
        //7.创建退款日志 保存
        RefundLog refundLog = new RefundLog();
        refundLog.setRefundReason("用户申请退款"+ orderInfo.getProductName());
        refundLog.setRefundAmount(orderInfo.getSeckillPrice().toString());
        refundLog.setRefundTime(new Date());
        refundLog.setRefundType(orderInfo.getPayType());
        refundLog.setOutTradeNo(orderInfo.getOrderNo());
        refundLogMapper.insert(refundLog);
    }
    /*
    开启全局事务 ，此时进入这个方法的代理对象就是TM
    TM：Transaction Manager 事务管理器，用于开启、提交或者回滚全局事务。
     */
    @GlobalTransactional
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void integralPay(String orderNo, Long phone) {
        //1.基于订单号 查询订单对象
        OrderInfo orderInfo = this.selectByOrderNo(orderNo);
        //2.判断订单状态是否为未支付状态，未支付才能发起支付请求
        AssertUtils.isTrue(OrderInfo.STATUS_ARREARAGE.equals(orderInfo.getStatus()),"订单状态异常，无法发起支付");
        //判断当前用户是否是创建订单的用户
        AssertUtils.isTrue(orderInfo.getUserId().equals(phone),"illegal operations!");
        //封装支付参数
        OperateIntergralVo vo = new OperateIntergralVo();
        vo.setInfo("积分秒杀:"+orderInfo.getProductName());
        vo.setValue(orderInfo.getIntergral());
        vo.setUserId(phone);
        vo.setOutTradeNo(orderNo);
        //远程调用支付服务支付
        Result<String> result = integralFeignApi.prepay(vo);//远程完成
        String tradeNo = result.checkAndGet();
        //发起支付
        int row = orderInfoMapper.changePayStatus(orderNo, OrderInfo.STATUS_ACCOUNT_PAID, OrderInfo.PAY_TYPE_INTERGRAL);
        AssertUtils.isTrue(row>0,"订单状态修改失败");
        PayLog payLog = new PayLog();
        payLog.setPayType(OrderInfo.PAY_TYPE_INTERGRAL);
        payLog.setTotalAmount(vo.getValue()+"");
        payLog.setOutTradeNo(orderNo);
        //流水变动交易号
        payLog.setTradeNo(tradeNo);
        payLog.setNotifyTime(System.currentTimeMillis()+"");
        payLogMapper.insert(payLog);
    }

    @Override
    public void integralRefundRollback(String orderNo) {
        OrderInfo orderInfo = orderInfoMapper.selectById(orderNo);
        this.refundRollback(orderInfo);
    }

    @Override
    public RefundLog selectRefundLogByOrderNo(String orderNo) {
        return refundLogMapper.selectByOrderNo(orderNo);
    }

    //private OrderInfo buildOrderInfo(UserInfo userInfo, SeckillProductVo vo) {
    private OrderInfo buildOrderInfo(Long phone, SeckillProductVo vo) {
        Date now = new Date();
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setCreateDate(now);
        orderInfo.setDeliveryAddrId(1L);
        orderInfo.setIntergral(vo.getIntergral());
        orderInfo.setOrderNo(IdGenerateUtil.get().nextId() + "");//id generator=>雪花算法==>保证唯一性（如果按1，2，3排序 分表之后数据库自增 id就不唯一）
        orderInfo.setPayType(OrderInfo.PAY_TYPE_ONLINE);
        orderInfo.setProductCount(1);
        orderInfo.setProductId(vo.getProductId());
        orderInfo.setProductImg(vo.getProductImg());
        orderInfo.setProductName(vo.getProductName());
        orderInfo.setProductPrice(vo.getProductPrice());
        orderInfo.setSeckillDate(now);
        orderInfo.setSeckillId(vo.getId());
        orderInfo.setSeckillPrice(vo.getSeckillPrice());
        orderInfo.setSeckillTime(vo.getTime());
        orderInfo.setStatus(OrderInfo.STATUS_ARREARAGE);
        orderInfo.setUserId(phone);
        return orderInfo;
    }
}
