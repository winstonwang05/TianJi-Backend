package com.tianji.promotion.service;

import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 兑换码 服务类
 * </p>
 *
 * @author author
 * @since 2025-10-15
 */
public interface IExchangeCodeService extends IService<ExchangeCode> {

    void asyncGenerateExchangeCode(Coupon coupon);

    boolean updateExchangeCodeMark(long incrementId, boolean b);

    Long getExchangeTargetIdFromCathe(Long incrementId);
}
