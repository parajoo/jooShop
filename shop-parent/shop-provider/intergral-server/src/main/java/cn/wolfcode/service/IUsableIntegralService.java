package cn.wolfcode.service;

import cn.wolfcode.domain.OperateIntergralVo;
import cn.wolfcode.domain.RefundVo;
import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextParameter;
import io.seata.rm.tcc.api.LocalTCC;
import io.seata.rm.tcc.api.TwoPhaseBusinessAction;

@LocalTCC
public interface IUsableIntegralService {
    /**
     * TCC phase1 :Try ,实现用户积分资源预留
     * @param operateIntergralVo 积分对象
     * @param ctx 上下文对象
     */
    @TwoPhaseBusinessAction(
            name = "tryPayment",
            commitMethod = "commitPayment",
            rollbackMethod = "rollbackPayment"
    )
    String tryPayment(@BusinessActionContextParameter(paramName = "vo") OperateIntergralVo operateIntergralVo,
                    BusinessActionContext ctx);

    /**
     * TCC phase2：Confirm 真正扣除积分保留流水日志
     *
     * @param ctx 上下文对象
     */
    void commitPayment(BusinessActionContext ctx);

    /**
     * TCC phase2：Cancel回滚阶段
     * @param ctx
     */
    void rollbackPayment(BusinessActionContext ctx);

    String doPay(OperateIntergralVo vo);

    boolean deRefund(RefundVo refundVo);
}
