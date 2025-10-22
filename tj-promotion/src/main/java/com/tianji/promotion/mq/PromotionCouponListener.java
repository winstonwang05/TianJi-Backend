package com.tianji.promotion.mq;


import com.tianji.common.constants.MqConstants;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.service.IUserCouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
@Slf4j
@Component
@RequiredArgsConstructor
public class PromotionCouponListener {
    final IUserCouponService userCouponService;
    /**
     * 领取优惠券的消息 的监听者
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "coupon.receive.queue", durable = "true"),
            exchange = @Exchange(value = MqConstants.Exchange.PROMOTION_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.COUPON_RECEIVE))
    public void onMessage(UserCouponDTO userCouponDTO) {
        log.debug("监听者接收消息， 消息内容 {}", userCouponDTO);
        userCouponService.createUserCoupon(userCouponDTO);
    }


    /**
     * 兑换码兑换优惠券 消息的监听者
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "coupon.exchange.queue", durable = "true"),
            exchange = @Exchange(value = MqConstants.Exchange.PROMOTION_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.COUPON_EXCHANGE))
    public void mess(UserCouponDTO userCouponDTO) {
        log.debug("监听者接收消息， 消息内容 {}", userCouponDTO);
        userCouponService.createUserCouponByExchangeCode(userCouponDTO);

    }
}