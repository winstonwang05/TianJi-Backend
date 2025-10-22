package com.tianji.promotion.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponVO;

import java.util.List;

/**
 * <p>
 * 优惠券的规则信息 服务类
 * </p>
 *
 * @author author
 * @since 2025-10-15
 */
public interface ICouponService extends IService<Coupon> {

    void saveCoupon(CouponFormDTO couponFormDTO);

    PageDTO<CouponPageVO> getCouponsPage(CouponQuery couponQuery);

    void updateCoupon(Long id, CouponFormDTO couponFormDTO);

    void deleteCoupon(Long id);

    CouponDetailVO getCouponById(Long id);

    void issueCoupon(Long id, CouponIssueFormDTO couponIssueFormDTO);

    List<CouponVO> queryIssuingCouponsList();

    void pauseIssuingCoupon(Long id);
}
