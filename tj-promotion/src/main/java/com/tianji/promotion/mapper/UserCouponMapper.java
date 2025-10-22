package com.tianji.promotion.mapper;

import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.UserCoupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 Mapper 接口
 * </p>
 *
 * @author author
 * @since 2025-10-16
 */
public interface UserCouponMapper extends BaseMapper<UserCoupon> {
    /***
     * 查询当前登陆用户所拥有的优惠券并过滤
     * @param userId 当前登录用户id
     * @return 返回用户拥有的优惠券且未使用
     */
    @Select("SELECT c.id, c.discount_type, c.discount_value, c.threshold_amount, c.max_discount_amount, c.`specific`, uc.id AS creater\n" +
            "FROM\n" +
            "coupon c\n" +
            "INNER JOIN user_coupon uc ON c.id = uc.coupon_id\n" +
            "WHERE uc.user_id = #{userId} AND uc.`status` = 1;")
    List<Coupon> queryMyCoupons(Long userId);
}
