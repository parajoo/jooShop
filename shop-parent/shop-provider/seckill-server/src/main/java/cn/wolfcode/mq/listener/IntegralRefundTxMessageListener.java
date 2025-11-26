package cn.wolfcode.mq.listener;

import cn.wolfcode.domain.RefundLog;
import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.service.IOrderInfoService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQTransactionListener(txProducerGroup = MQConstant.INTEGRAL_REFUND_TX_GROUP)
public class IntegralRefundTxMessageListener implements RocketMQLocalTransactionListener {
    private final IOrderInfoService orderInfoService;

    public IntegralRefundTxMessageListener(IOrderInfoService orderInfoService) {
        this.orderInfoService = orderInfoService;
    }

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message message, Object arg) {
        try {
            log.info("[事务消息监听器]准备进行本地事务 进行积分退款： {}",arg);
            //执行更新订单状态/回补库存，通知到消费者
            orderInfoService.integralRefundRollback((String)arg);
            //如果执行回滚消息成功 就像将消息提交 通知到消费者
            return RocketMQLocalTransactionState.UNKNOWN;
        } catch (Exception e) {
            log.warn("[事务消息监听器]执行本地事务出现异常",e);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message message) {
        //查询当前订单是否已经变成退款状态
        try {
            String orderNo = (String) message.getHeaders().get("orderNo");
            log.info("[事务消息监听器]本地事务执行完成未获得结果 准备检查订单状态是否已退款： {}",orderNo);
            //基于orderNo查询退款记录 如果已经有退款记录 说明退款成功 否则退款失败
            RefundLog refundLog =  orderInfoService.selectRefundLogByOrderNo(orderNo);
            if(refundLog!=null){
                return RocketMQLocalTransactionState.COMMIT;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return RocketMQLocalTransactionState.UNKNOWN;
    }
}
