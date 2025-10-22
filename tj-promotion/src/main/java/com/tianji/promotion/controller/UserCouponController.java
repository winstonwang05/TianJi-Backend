package com.tianji.promotion.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.CouponDiscountDTO;
import com.tianji.promotion.domain.dto.OrderCourseDTO;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.service.IUserCouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 前端控制器
 * </p>
 *
 * @author author
 * @since 2025-10-16
 */
@Api(tags = "用户的优惠券相关接口")
@RestController
@RequestMapping("/user-coupons")
@RequiredArgsConstructor
public class UserCouponController {

    final IUserCouponService userCouponService;

    @ApiOperation("用户领取优惠券")
    @PostMapping("{id}/receive")
    public void receiveCoupon(@PathVariable Long id) {
        userCouponService.receiveCoupon(id);
    }

    @ApiOperation("兑换码兑换优惠券")
    @PostMapping("{code}/exchange")
    public void couponByExchangeCode(@PathVariable String code) {
        userCouponService.couponByExchangeCode(code);
    }

    @ApiOperation("分页查询我的优惠券")
    @GetMapping("page")
    public PageDTO<CouponVO> queryUserCouponsPage(UserCouponQuery query) {
        return userCouponService.queryUserCouponsPage(query);
    }

    @ApiOperation("查询可用的优惠券组合方案") // 方便tj-trade服务调用
    @PostMapping("available")
    public List<CouponDiscountDTO> queryUserCouponsAvailable(@RequestBody List<OrderCourseDTO> orderCourseDTOS) {
        return userCouponService.queryUserCouponsAvailable(orderCourseDTOS);
    }

}
