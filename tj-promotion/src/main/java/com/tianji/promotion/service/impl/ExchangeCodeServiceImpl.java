package com.tianji.promotion.service.impl;

import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.mapper.ExchangeCodeMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.CodeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 兑换码 服务实现类
 * </p>
 *
 * @author author
 * @since 2025-10-15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeCodeServiceImpl extends ServiceImpl<ExchangeCodeMapper, ExchangeCode> implements IExchangeCodeService {

    final StringRedisTemplate stringRedisTemplate;

    /**
     * 异步生成兑换码保存到数据库种，对应exchange_code表中
     * @param coupon 需要生成兑换码的优惠券信息
     */
    @Override
    @Transactional
    @Async("generateExchangeCodeExecutor") // 使用自定义的线程池异步去进行
    public void asyncGenerateExchangeCode(Coupon coupon) {
        log.debug("当前执行的线程名称  {}", Thread.currentThread().getName());
        // 1.生成兑换码
        Integer totalNum = coupon.getTotalNum();// 需要生成的兑换码总数量
        // 通过redis实现自增id , increment表示自增的id从原来的数量上继续累加totalNum；所以说是原来的+totalNum
        Long increment = stringRedisTemplate.opsForValue().increment(PromotionConstants.COUPON_CODE_SERIAL_KEY, totalNum);
        if (increment == null) {
            return;
        }
        int maxIncrement = increment.intValue(); // 需要新增id的最大值
        int begin = maxIncrement - totalNum + 1; // 下一次自增要从这里开始自增
        List<ExchangeCode> exchangeCodeList = new ArrayList<>();
        for (int serNum = begin; serNum <= maxIncrement; serNum++) {
            ExchangeCode exchangeCode = new ExchangeCode();
            String code = CodeUtil.generateCode(serNum, coupon.getId());
            exchangeCode.setCode(code);
            exchangeCode.setId(serNum);
            exchangeCode.setExpiredTime(coupon.getIssueEndTime());
            exchangeCode.setExchangeTargetId(coupon.getId()); // 兑换码指定的优惠券id
            exchangeCodeList.add(exchangeCode);

        }
        // 2.封装PO保存到exchange_code表中
        this.saveBatch(exchangeCodeList);
        // 3.将生成的兑换存入redis缓存中， score就是最大自增id
        String couponRangeKey = PromotionConstants.COUPON_RANGE_KEY;
        stringRedisTemplate.opsForZSet().add(couponRangeKey, String.valueOf(coupon.getId()), maxIncrement);

    }

    /**
     * 从缓存的ZSet结构中获取member 也就是自增id对应的couponId
     * @param incrementId 自增id
     * @return couponId
     */
    @Override
    public Long getExchangeTargetIdFromCathe(Long incrementId) {

        // 1.获取key
        String couponRangeKey = PromotionConstants.COUPON_RANGE_KEY;
        // 2.获取member（couponId）
        // 闭区间，从从第一个开始取，取第一个
        Set<String> couponIdOfMember = stringRedisTemplate
                .opsForZSet()
                .rangeByScore(couponRangeKey, incrementId, Double.MAX_VALUE, 0, 1L);
        if (CollUtils.isEmpty(couponIdOfMember)) {
            return null;
        }
        String couponId = couponIdOfMember.iterator().next();
        return Long.valueOf(couponId);
    }

    /**
     * 查询当前自增id在BitMap上是 0还是1   0-未兑换 1-已兑换
     * @param incrementId 自增id
     * @param b 需要将传入的自增id 对应的BitMap的bit为更新为  true - 1   false - 0
     * @return 返回 原来 bit位存的结果
     */
    @Override
    public boolean updateExchangeCodeMark(long incrementId, boolean b) {
        Boolean setBit = stringRedisTemplate.opsForValue().setBit(PromotionConstants.COUPON_CODE_MAP_KEY, incrementId, b);
        return setBit != null && setBit;
    }
}
