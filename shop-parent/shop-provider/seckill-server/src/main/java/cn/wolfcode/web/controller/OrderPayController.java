package cn.wolfcode.web.controller;


import cn.wolfcode.common.domain.UserInfo;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.common.web.anno.RequireLogin;
import cn.wolfcode.common.web.resolver.RequestUser;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.PayResult;
import cn.wolfcode.service.IOrderInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import java.util.Map;


@RestController
@RequestMapping("/orderPay")
@RefreshScope
public class OrderPayController {
    private final IOrderInfoService orderInfoService;

    public OrderPayController(IOrderInfoService orderInfoService) {
        this.orderInfoService = orderInfoService;
    }

    @GetMapping("/refund")
    public Result<String> refund(String orderNo){
        orderInfoService.refund(orderNo);
        return Result.success();
    }
    @RequireLogin
    @GetMapping("/pay")
    public Result<String> doPay(String orderNo, Integer type, @RequestUser UserInfo userInfo) {
        //判断类型 调用不同api
        if(type== OrderInfo.PAY_TYPE_ONLINE){
            return Result.success(orderInfoService.onlinePay(orderNo));
        }
        //积分支付
        orderInfoService.integralPay(orderNo,userInfo.getPhone());
        return Result.success();
    }
    @PostMapping("/success")
    public Result<?> alipaySuccess(@RequestBody PayResult result) {
        orderInfoService.alipaySuccess(result);
        return Result.success();
    }
}
