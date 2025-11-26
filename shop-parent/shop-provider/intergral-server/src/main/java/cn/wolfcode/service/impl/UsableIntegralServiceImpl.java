package cn.wolfcode.service.impl;

import cn.wolfcode.domain.AccountTransaction;
import cn.wolfcode.domain.RefundVo;
import cn.wolfcode.util.AssertUtils;
import cn.wolfcode.domain.AccountLog;
import cn.wolfcode.domain.OperateIntergralVo;
import cn.wolfcode.mapper.AccountLogMapper;
import cn.wolfcode.mapper.AccountTransactionMapper;
import cn.wolfcode.mapper.UsableIntegralMapper;
import cn.wolfcode.service.IUsableIntegralService;
import cn.wolfcode.util.IdGenerateUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import io.seata.rm.tcc.api.BusinessActionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;

@Slf4j
@Service
public class UsableIntegralServiceImpl implements IUsableIntegralService {
    private final UsableIntegralMapper usableIntegralMapper;
    private final AccountTransactionMapper accountTransactionMapper;
    private final AccountLogMapper accountLogMapper;
    private final IdGenerateUtil idGenerateUtil;

    public UsableIntegralServiceImpl(UsableIntegralMapper usableIntegralMapper, AccountTransactionMapper accountTransactionMapper, AccountLogMapper accountLogMapper, IdGenerateUtil idGenerateUtil) {
        this.usableIntegralMapper = usableIntegralMapper;
        this.accountTransactionMapper = accountTransactionMapper;
        this.accountLogMapper = accountLogMapper;
        this.idGenerateUtil = idGenerateUtil;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public String tryPayment(OperateIntergralVo operateIntergralVo, BusinessActionContext ctx) {
        log.info(" [TCC-Try]收到请求参数: {}，准备执行tryPayment ", JSON.toJSONString(operateIntergralVo));
        //1.实现防悬挂，直接向MySQL插入事务控制记录
        String tradeNo = idGenerateUtil.nextId() + "";
        this.insertTxLog(operateIntergralVo, ctx,AccountLog.TYPE_DECR,AccountTransaction.STATE_TRY,tradeNo);
        //2.冻结积分 判断是否冻结成功
        int row = usableIntegralMapper.freezeIntergral(operateIntergralVo.getUserId(), operateIntergralVo.getValue());
        AssertUtils.isTrue(row>0,"积分不足");
        //3.直接生成支付流水订单编号
        /*
        String tradeNo = idGenerateUtil.nextId() + "";
        //4.将tradeNo通过上下文对象，传递给commit方法
        Map<String, Object> actionContext = ctx.getActionContext();
        actionContext.put("tradeNo",tradeNo);
         */
        return tradeNo;
    }

    private void insertTxLog(OperateIntergralVo operateIntergralVo, BusinessActionContext ctx,Integer type,Integer state,String tradeNo) {
        AccountTransaction tx = new AccountTransaction();
        tx.setAmount(operateIntergralVo.getValue());
        tx.setType(type);
        tx.setState(state);
        Date now = new Date();
        tx.setGmtCreated(now);
        tx.setTradeNo(tradeNo);
        tx.setGmtModified(now);
        tx.setUserId(operateIntergralVo.getUserId());
        tx.setTxId(ctx.getXid());
        tx.setActionId(ctx.getBranchId());
        //保存事务记录
        accountTransactionMapper.insert(tx);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void commitPayment(BusinessActionContext ctx) {
        Object obj = ctx.getActionContext("vo");
        log.info("[TCC-CONFIRM]收到TCC CONFIRM消息 xid = {},branchId = {},准备执行业务并提交本地事务...",ctx.getXid(),ctx.getBranchId());
        JSONObject vo = (JSONObject) obj;
        //1.基于全局事务id+分支事务id获取唯一的事务记录
        AccountTransaction tx = accountTransactionMapper.get(ctx.getXid(), ctx.getBranchId());
        //2.判断是否为空,如果为空，说明没有执行一阶段，流程异常
        if(tx == null){
            log.info("[TCC-CONFIRM]流程异常，无法查询到一阶段事务记录，终止CONFIRM流程 xid = {},branchId = {}",ctx.getXid(),ctx.getBranchId());
            return;
        }
        //3.判断状态是否已提交，说明一阶段已提交，保证幂等即可
        if(AccountTransaction.STATE_COMMIT == tx.getState()){
            log.info("[TCC-CONFIRM]流程异常，CONFIRM方法被重复调用 xid = {},branchId = {}",ctx.getXid(),ctx.getBranchId());
            return;
        }
        else if(AccountTransaction.STATE_CANCEL == tx.getState()){
            //4.判断状态是否为已回滚，如果是，说明流程异常，方法终止
            log.info("[TCC-CONFIRM]流程异常，已经执行CANCEL 无法执行CONFIRM xid = {},branchId = {}",ctx.getXid(),ctx.getBranchId());
            return;
        }
        //5.真正扣除金额（扣除冻结金额+原始金额）
        usableIntegralMapper.commitChange(vo.getLong("userId"), vo.getLong("value"));
        //6.创建交易流水日志，保存到数据库
        AccountLog accountLog = new AccountLog();
        accountLog.setAmount(vo.getLong("value"));
        accountLog.setInfo(vo.getString("info"));
        accountLog.setGmtTime(new Date());
        accountLog.setOutTradeNo(vo.getString("outTradeNo"));
        //从ctx获取交易流水号
        accountLog.setTradeNo(tx.getTradeNo());
        accountLog.setType(AccountLog.TYPE_DECR);
        accountLog.setUserId(vo.getLong("userId"));
        accountLogMapper.insert(accountLog);
        //7.将事务记录状态更新为已提交
        int row = accountTransactionMapper.updateAccountTransactionState(
                ctx.getXid(),
                ctx.getBranchId(),
                AccountTransaction.STATE_COMMIT,
                AccountTransaction.STATE_TRY
        );
        log.info("[TCC-CONFIRM] 积分支付提交操作完毕：{}",row);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void rollbackPayment(BusinessActionContext ctx) {
        Object obj = ctx.getActionContext("vo");
        log.info("[TCC-CANCEL]收到TCC CANCEL消息 xid = {},branchId = {},准备回滚TRY阶段操作...",ctx.getXid(),ctx.getBranchId());
        JSONObject vo = (JSONObject) obj;
        //1.查询事务记录，是否为空？
        AccountTransaction tx = accountTransactionMapper.get(ctx.getXid(), ctx.getBranchId());
        //2.如果为空，插入一条CANCEL类型的事务记录，并终止方法
        if(tx == null){
            try {
                log.warn("[TCC-CANCEL]流程异常，出现空回滚问题，TRY阶段未执行，直接执行CANCEL xid = {},branchId = {}",ctx.getXid(),ctx.getBranchId());
                this.insertTxLog(vo.toJavaObject(OperateIntergralVo.class),ctx,AccountLog.TYPE_DECR,AccountTransaction.STATE_CANCEL,null);
            } catch (Exception e) {
                log.warn("[TCC-CANCEL]流程异常，出现空回滚并发问题，在插入空回滚记录前TRY先执行了 xid = {},branchId = {}",ctx.getXid(),ctx.getBranchId());
                log.error("[TCC-CANCEL] 打印流程异常信息",e);
            }
            return;
        }
        //3.如果不为空，判断事务状态是否为已回滚，如果是说明重复执行回滚操作，直接结束方法
        if(tx.getState() == AccountTransaction.STATE_CANCEL){
            log.warn("[TCC-CANCEL]流程异常，出现回滚重复执行问题，直接结束方法 xid = {},branchId = {}",ctx.getXid(),ctx.getBranchId());
            return;
        }
        else if(tx.getState() == AccountTransaction.STATE_COMMIT) {
            //4.否则判断状态是否为已提交，如果是，说明流程异常，结束方法
            log.warn("[TCC-CANCEL]流程异常，已经执行过CONFIRM，不允许再执行CANCEL方法 xid = {},branchId = {}",ctx.getXid(),ctx.getBranchId());
            return;
        }
        //5.如果是初始化方法，正常流程，将TRY执行的冻结金额回滚回去
        usableIntegralMapper.unFreezeIntergral(vo.getLong("userId"),vo.getLong("value"));
        //6.修改事务记录为已回滚
        int row = accountTransactionMapper.updateAccountTransactionState(ctx.getXid(), ctx.getBranchId(),
                AccountTransaction.STATE_CANCEL,
                AccountTransaction.STATE_TRY
        );
        log.info("[TCC-CANCEL]更新事务记录为已回滚 {}",row);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public String doPay(OperateIntergralVo vo) {
        //1.先扣减积分，扣减后是否>=0，不满足说明积分不足
        int row = usableIntegralMapper.decrIntegral(vo.getUserId(), vo.getValue());
        AssertUtils.isTrue(row>0,"账户积分不足");
        //2.构建账户流水
        AccountLog log = new AccountLog();
        log.setAmount(vo.getValue());
        log.setInfo(vo.getInfo());
        log.setGmtTime(new Date());
        log.setOutTradeNo(vo.getOutTradeNo());
        log.setUserId(vo.getUserId());
        log.setTradeNo(idGenerateUtil.nextId()+"");
        log.setType(AccountLog.TYPE_DECR);
        accountLogMapper.insert(log);
        return log.getOutTradeNo();
    }
    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean deRefund(RefundVo refundVo) {
        //1.查询订单编号差支付流水，类型必须是扣款类型
        AccountLog decrLog = accountLogMapper.selectByOutTradeNoAndType(refundVo.getOutTradeNo(),AccountLog.TYPE_DECR);
        AssertUtils.notNull(decrLog,"退款失败，该订单未支付");
        //2.通过支付流水中支付的积分 将其退回给用户积分账户
        BigDecimal bigDecimal = new BigDecimal(refundVo.getRefundAmount());
        int compareTo = bigDecimal.compareTo(new BigDecimal(decrLog.getAmount()));
        AssertUtils.isTrue(compareTo<=0,"退款金额不能大于支付金额");
        //同意积分退款 返回积分
        log.info("[同意积分退款 返回积分]: {}", bigDecimal.longValue()*10);
        usableIntegralMapper.addIntergral(decrLog.getUserId(), bigDecimal.longValue()*10);
        log.info("已退回积分");
        //3.记录新的退款流水
        AccountLog incrLog = new AccountLog();
        incrLog.setInfo(refundVo.getRefundReason());
        incrLog.setType(AccountLog.TYPE_INCR);
        incrLog.setAmount(bigDecimal.longValue());
        incrLog.setTradeNo(idGenerateUtil.nextId()+"");
        incrLog.setOutTradeNo(refundVo.getOutTradeNo());
        incrLog.setUserId(decrLog.getUserId());
        incrLog.setGmtTime(new Date());
        accountLogMapper.insert(incrLog);
        return true;
    }
}
