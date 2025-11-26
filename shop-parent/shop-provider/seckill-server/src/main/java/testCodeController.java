import cn.wolfcode.common.domain.UserInfo;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.common.web.anno.RequireLogin;
import cn.wolfcode.common.web.resolver.RequestUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/*
@RestController//springMVC的路由映射注解，用来指定控制器或方法对应的话访问url路径
@RequestMapping("/order")

public class testCodeController {



    @RequireLogin
    @GetMapping("/doSeckill")
   public Result<?> doSeckill(Long seckillId, Integer time
                               @RequestUser UserInfo userInfo,
                               @RequestHeader("token") String token){
    //用户重复下单标识校验
        //1.商品是否在秒杀时间范围内？-查询商品对象？并判断是否在时间内

        //2.拼接key，redis查询用户下单该商品的次数

    //redis原子预减库存
        //1.mq异步下单
        //2.异常处理
    }

    private boolean isInTimeRange(){

    }


}
*/
