package com.tianji.promotion.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.service.ICouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 优惠券的规则信息 前端控制器
 * </p>
 *
 * @author author
 * @since 2025-10-15
 */
@Api(tags = "优惠券相关接口")
@RestController
@RequestMapping("/coupons")
@RequiredArgsConstructor
public class CouponController {
    final ICouponService couponService;
    @ApiOperation("新增优惠券")
    @PostMapping
    public void saveCoupon(@RequestBody @Validated CouponFormDTO couponFormDTO) {
        couponService.saveCoupon(couponFormDTO);
    }

    @ApiOperation("分页查询优惠券列表-管理端")
    @GetMapping("page")
    public PageDTO<CouponPageVO> getCouponsPage(CouponQuery couponQuery) {
        return couponService.getCouponsPage(couponQuery);
    }

    @ApiOperation("修改优惠券信息-管理端")
    @PutMapping("{id}")
    public void updateCoupon(@PathVariable Long id, @RequestBody @Validated CouponFormDTO couponFormDTO) {
        couponService.updateCoupon(id, couponFormDTO);
    }

    @ApiOperation("删除优惠券-管理端")
    @DeleteMapping("{id}")
    public void deleteCoupon(@PathVariable Long id) {
        couponService.deleteCoupon(id);
    }

    @ApiOperation("查询具体优惠券信息-管理端")
    @GetMapping("{id}")
    public CouponDetailVO getCouponById(@PathVariable Long id) {
        return couponService.getCouponById(id);
    }
    @ApiOperation("发放优惠券-管理端")
    @PutMapping("{id}/issue")
    public void issueCoupon(@PathVariable Long id,
                            @RequestBody @Validated CouponIssueFormDTO couponIssueFormDTO) {
        couponService.issueCoupon(id, couponIssueFormDTO);
    }
    @ApiOperation("查询发放中的优惠券列表-用户端")
    @GetMapping("list")
    public List<CouponVO> queryIssuingCouponsList() {
        return couponService.queryIssuingCouponsList();
    }

    @ApiOperation("暂停发放优惠券-管理端")
    @PutMapping("{id}/pause")
    public void pauseIssuingCoupon(@PathVariable Long id) {
        couponService.pauseIssuingCoupon(id);
    }

}
