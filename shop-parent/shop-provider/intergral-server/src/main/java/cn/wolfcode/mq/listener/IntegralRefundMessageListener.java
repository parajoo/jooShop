package cn.wolfcode.mq.listener;


import cn.wolfcode.domain.RefundVo;
import cn.wolfcode.service.IUsableIntegralService;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j

@Component
@RocketMQMessageListener(
        consumerGroup = "INTEGRAL_REFUND_TX_CONSUMER_GROUP",
        topic = "INTEGRAL_REFUND_TX_GROUP"
)
public class IntegralRefundMessageListener implements RocketMQListener<RefundVo> {

    private final IUsableIntegralService usableIntegralService;

    public IntegralRefundMessageListener(IUsableIntegralService usableIntegralService) {
        this.usableIntegralService = usableIntegralService;
    }

    @Override
    public void onMessage(RefundVo refundVo) {
        boolean ret = usableIntegralService.deRefund(refundVo);
        log.info("[积分退款] 收到积分退款事务消息  退款结果={},参数={}",ret, JSON.toJSONString(refundVo));
    }

}
