package cn.wolfcode.web.controller;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.OperateIntergralVo;
import cn.wolfcode.domain.RefundVo;
import cn.wolfcode.service.IUsableIntegralService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/integral")
public class IntegralController {
    private final IUsableIntegralService usableIntegralService;

    public IntegralController(IUsableIntegralService usableIntegralService) {
        this.usableIntegralService = usableIntegralService;
    }
    @PostMapping("/refund")
    public Result<Boolean> refund(@RequestBody RefundVo refundVo){
        boolean ret =  usableIntegralService.deRefund(refundVo);
        return Result.success(ret);
    }
    @PostMapping("/prepay")
    public Result<String> prepay(@RequestBody OperateIntergralVo vo){

        //积分支付
        String tradeNo = usableIntegralService.tryPayment(vo,null);
        return Result.success(tradeNo);
    }
}
