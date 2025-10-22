/*
package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.CodeUtil;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

*/
/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author author
 * @since 2025-10-16
 *//*

@Service
@RequiredArgsConstructor
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {
    final CouponMapper couponMapper;
    final IExchangeCodeService exchangeCodeService;
    final RedissonClient redissonClient;
    */
/***
     * 领取优惠券
     * @param id 需要领取的优惠券id
     *//*

    @Override
    @Transactional
    public void receiveCoupon(Long id) {
        // 1.校验id是否存在
        if (id == null) {
            throw new RuntimeException("id is null");
        }
        // 2.根据优惠券id查询coupon表，得到coupon对象
        Coupon coupon = couponMapper.selectById(id);
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在");
        }
        // 3.判断是否发行
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getIssueBeginTime()) || now.isAfter(coupon.getIssueEndTime())) {
            throw new BadRequestException("优惠券未发行");
        }
        if (!coupon.getStatus().equals(CouponStatus.ISSUING)) {
            throw new BadRequestException("优惠券未发行");
        }
        // 4.判断库存是否充足
        if (coupon.getTotalNum() <= 0 || coupon.getIssueNum() >= coupon.getTotalNum()) {
            throw new BadRequestException("优惠券库存不足");
        }
*/
/*        // 5.判断是否超出了每人限领数
        Long userId = UserContext.getUser();
        Integer receiveCount = this.lambdaQuery()
                .eq(UserCoupon::getCouponId, id)
                .eq(UserCoupon::getUserId, userId)
                .count();
        if (receiveCount >= coupon.getUserLimit()) {
            throw new BadRequestException("已达到领取上限");
        }
        // 6.更新优惠券表发行数量+ 1；并新增优惠券信息到user_coupon表
        couponMapper.incrIssueNum(id);
        saveUserCoupon(userId, coupon);*//*

*/
/*        // 通过悲观锁
        Long userId = UserContext.getUser();
        synchronized (userId.toString().intern()) {
            // 通过APO上下文代理对象获取当前类的代理对象
            IUserCouponService userCouponService = (IUserCouponService)AopContext.currentProxy();
            // 用当前类的代理对象来调用事务方法
            userCouponService.checkAndCreateUserCoupon(coupon, userId, null);
        }*//*


        // 通过Redisson分布式锁
        Long userId = UserContext.getUser();
        String lockKey = "lock:coupon:uid" + userId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            // 尝试获取锁
            boolean isLock = lock.tryLock(); // 尝试获取锁，不设置指定的过期时间，就会触发看门狗机制
            if (!isLock) {
                throw new BizIllegalException("操作频繁");
            }
            // 通过APO上下文代理对象获取当前类的代理对象
            log.debug("获取到分布式锁");
            IUserCouponService userCouponService = (IUserCouponService)AopContext.currentProxy();
            // 用当前类的代理对象来调用事务方法
            userCouponService.checkAndCreateUserCoupon(coupon, userId, null);
        } finally {
            // 无论如何都需要释放锁
            lock.unlock();
        }

    }

    */
/**
     * 保存优惠券信息到user_coupon表
     * @param userId 用户id
     * @param coupon 优惠券对象
     *//*

    private void saveUserCoupon(Long userId, Coupon coupon) {
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(coupon.getId());
        LocalDateTime termBeginTime = coupon.getTermBeginTime();
        LocalDateTime termEndTime = coupon.getTermEndTime();
        // 如果前端未传来时间，手动设置
        if (termBeginTime == null && termEndTime == null) {
            termBeginTime = LocalDateTime.now();
            termEndTime = termBeginTime.plusDays(coupon.getTermDays());
        }
        userCoupon.setTermBeginTime(termBeginTime);
        userCoupon.setTermEndTime(termEndTime);

        this.save(userCoupon);
    }

    */
/**
     * 兑换码兑换优惠券
     * @param code 需要兑换的兑换码
     *//*

    @Override
    public void couponByExchangeCode(String code) {
        // 1.校验code是否为空
        if (StringUtils.isEmpty(code)) {
            throw new BadRequestException("非法参数");
        }
        // 2.解析code得到 所自增的id
        Long incrementId = CodeUtil.parseCode(code);
        // 3.通过redis的BitMap判断是否已兑换
        boolean result = exchangeCodeService.updateExchangeCodeMark(incrementId, true);
        if (result) {
            // 说明原来为true - 1
            throw new BadRequestException("该兑换码已经兑换过了");
        }
        // 后面步骤如果出现异常，不能兑换兑换码，所以需要抛出异常将兑换码重置
        try {
            // 4.根据 自增id查询exchange_code表得到对应实体类对象，判断是否存在
            ExchangeCode exchangeCode = exchangeCodeService.getById(incrementId);
            if (exchangeCode == null) {
                throw new BadRequestException("兑换码不存在");
            }
            // 5.判断兑换码是否过期
            if (LocalDateTime.now().isAfter(exchangeCode.getExpiredTime())) {
                throw new BadRequestException("兑换码已过期");
            }
            // 6.判断当前用户优惠券是否超过上限
            // 7.更新优惠券发放数量+ 1
            // 8.生成优惠券
            // 9.更新兑换码的为已使用
            Long couponId = exchangeCode.getExchangeTargetId();
            Coupon coupon = couponMapper.selectById(couponId);
            Long userId = UserContext.getUser();

            */
/*checkAndCreateUserCoupon(coupon, userId, incrementId);*//*

*/
/*            synchronized (userId.toString().intern()) {
                checkAndCreateUserCoupon(coupon, userId, incrementId);
            }*//*


            checkAndCreateUserCoupon(coupon, userId, incrementId);

        } catch (Exception e) {
            exchangeCodeService.updateExchangeCodeMark(incrementId, false);
            throw new BadRequestException(e.getMessage());
        }

    }

    */
/**
     * 校验是否超过购买上限以及生成优惠券信息
     * @param coupon 优惠券对象
     * @param userId 当前登录用户id
     * @param incrementId 自增id，用来更新兑换码状态
     *//*

    @Transactional
    public void checkAndCreateUserCoupon(Coupon coupon, Long userId, Long incrementId) {
            // 更新优惠券表发行数量+ 1；并新增优惠券信息到user_coupon表
            // 3.生成优惠券保存到user_coupon表中
            Integer receiveCount = this.lambdaQuery()
                    .eq(UserCoupon::getCouponId, coupon.getId())
                    .eq(UserCoupon::getUserId, userId)
                    .count();
            if (receiveCount >= coupon.getUserLimit()) {
                throw new BadRequestException("已达到领取上限");
            }
            couponMapper.incrIssueNum(coupon.getId());
            saveUserCoupon(userId, coupon);
            // 4.更新兑换码状态为 已使用
            if (incrementId != null) {
                exchangeCodeService.lambdaUpdate()
                        .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                        .set(ExchangeCode::getUserId, userId)
                        .eq(ExchangeCode::getId, incrementId)
                        .update();

            }


    }

    */
/**
     * 分页查询我的优惠券
     * @param query 查询条件 包含分页参数以及 需要查询状态一个属性  已过期 未使用  已使用
     * @return 返回分页后的优惠券信息
     *//*

    @Override
    public PageDTO<CouponVO> queryUserCouponsPage(UserCouponQuery query) {
        // 1.获取当前登录用户id
        Long userId = UserContext.getUser();
        // 2.分页查询user_coupon表 条件 ：当前登录用户id 需要查询的状态
        Page<UserCoupon> userCouponPage = this.lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getStatus, query.getStatus())
                .page(query.toMpPage());
        List<UserCoupon> userCouponPageRecords = userCouponPage.getRecords();
        if (CollUtils.isEmpty(userCouponPageRecords)) {
            return PageDTO.empty(0L, 0L);
        }
        // 3.获取优惠券ids
        Set<Long> couponIds = userCouponPageRecords.stream().map(UserCoupon::getCouponId).collect(Collectors.toSet());
        // 4.查询coupon表封装VO返回
        List<Coupon> couponList = couponMapper.selectBatchIds(couponIds);
        List<CouponVO> couponVOList = new ArrayList<>();
        for (Coupon coupon : couponList) {
            CouponVO couponVO = BeanUtils.copyBean(coupon, CouponVO.class);
            couponVOList.add(couponVO);
        }
        return PageDTO.of(userCouponPage, couponVOList);
    }
}
*/
