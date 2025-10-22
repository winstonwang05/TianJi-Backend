package com.tianji.promotion.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.CouponDiscountDTO;
import com.tianji.promotion.domain.dto.OrderCourseDTO;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.UserCoupon;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.tianji.promotion.domain.vo.CouponVO;

import java.util.List;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务类
 * </p>
 *
 * @author author
 * @since 2025-10-16
 */
public interface IUserCouponService extends IService<UserCoupon> {

    void receiveCoupon(Long id);

    void couponByExchangeCode(String code);

    PageDTO<CouponVO> queryUserCouponsPage(UserCouponQuery query);

    // 当前接口类代理的事务方法 ，需要在接口中写出抽象方法
     void checkAndCreateUserCoupon(Coupon coupon, Long userId, Long incrementId);

    void createUserCoupon(UserCouponDTO userCouponDTO);

    void createUserCouponByExchangeCode(UserCouponDTO userCouponDTO);

    List<CouponDiscountDTO> queryUserCouponsAvailable(List<OrderCourseDTO> orderCourseDTOS);
}
