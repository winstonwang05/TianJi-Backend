package com.tianji.promotion.constants;

public interface PromotionConstants {
    String COUPON_CODE_SERIAL_KEY = "coupon:code:serial"; // 通过redis实现自增的key
    String COUPON_CODE_MAP_KEY = "coupon:code:map"; // 每一个自增id对应BitMap的bit位的key
    String COUPON_CACHE_KEY_PREFIX = "prs:coupon:"; // 不同优惠券id的前缀，Hash存储，field就是 发行时间，截止时间，发行的总数量，每人限量数量；value就是对应值
    String USER_COUPON_CACHE_KEY_PREFIX = "prs:user:coupon:"; // 不同优惠券id的前缀，field就是不同用户id value是当前用户已经领取了的 优惠券数量
    String COUPON_RANGE_KEY = "coupon:code:range"; // 优惠券 的兑换码范围  存储ZSet结构， member是couponId， value就是自增id

    String[] RECEIVE_COUPON_ERROR_MSG = {
            "活动未开始",
            "库存不足",
            "活动已经结束",
            "领取次数过多",
    };
    String[] EXCHANGE_COUPON_ERROR_MSG = {
            "兑换码已兑换",
            "无效兑换码",
            "活动未开始",
            "活动已经结束",
            "领取次数过多",
    };
}