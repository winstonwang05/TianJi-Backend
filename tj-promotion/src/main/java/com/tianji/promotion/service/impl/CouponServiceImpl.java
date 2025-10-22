package com.tianji.promotion.service.impl;

import cn.hutool.core.lang.hash.Hash;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CategoryClient;
import com.tianji.api.dto.course.CategoryBasicDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.*;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponScopeVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ObtainType;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 优惠券的规则信息 服务实现类
 * </p>
 *
 * @author author
 * @since 2025-10-15
 */
@Service
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {
    final ICouponScopeService couponScopeService;
    final CategoryClient categoryClient;
    final IExchangeCodeService exchangeCodeService;
    final IUserCouponService userCouponService;
    final StringRedisTemplate redisTemplate;
    /**
     * 新增优惠券
     * @param couponFormDTO 前端传来的参数，包括优惠券相关信息
     */
    @Override
    @Transactional
    public void saveCoupon(CouponFormDTO couponFormDTO) {
        // 1.将dto属性值映射到po中
        Coupon coupon = BeanUtils.copyBean(couponFormDTO, Coupon.class);
        if (coupon == null) {
            throw new BadRequestException("不存在优惠券信息");
        }
        // 2.保存到 coupon 表中
        this.save(coupon);
        // 3.判断是否限定范围若没有限定范围，直接返回
        if (!couponFormDTO.getSpecific()) {
            return;
        }
        // 4.限定范围，保存到coupon_scope表中
        List<Long> scopes = couponFormDTO.getScopes();
        if (CollUtils.isEmpty(scopes)) {
            throw new BadRequestException("指定的范围不存在");
        }
/*        List<CouponScope> couponScopes = new ArrayList<>();
        for (Long scope : scopes) {
            CouponScope couponScope = new CouponScope();
            couponScope.setCouponId(couponFormDTO.getId());
            couponScope.setBizId(scope);
            couponScope.setType(1); // 暂时设置指定类
            couponScopes.add(couponScope);
        }*/
/*        List<CouponScope> couponScopes = scopes.stream().map(new Function<Long, CouponScope>() {
            @Override
            public CouponScope apply(Long scopeId) {
                return new CouponScope().setCouponId(couponFormDTO.getId()).setBizId(scopeId).setType(1);
            }
        }).collect(Collectors.toList());*/
        List<CouponScope> couponScopes = scopes.stream()
                .map(scopeId -> new CouponScope().setCouponId(couponFormDTO.getId()).setBizId(scopeId).setType(1))
                .collect(Collectors.toList());
        couponScopeService.saveBatch(couponScopes);
    }

    /**
     * 管理端分页查询优惠券列表
     * @param couponQuery 分页参数
     * @return 返回优惠券分页结果
     */
    @Override
    public PageDTO<CouponPageVO> getCouponsPage(CouponQuery couponQuery) {
        // 1.分页查询优惠券
        Page<Coupon> page = this.lambdaQuery()
                .eq(couponQuery.getStatus() != null, Coupon::getStatus, couponQuery.getStatus()) // 前端该字段可能为空，有就传
                .eq(couponQuery.getType() != null, Coupon::getDiscountType, couponQuery.getType())
                .like(StringUtils.isNotBlank(couponQuery.getName()), Coupon::getName, couponQuery.getName())
                .page(couponQuery.toMpPageDefaultSortByCreateTimeDesc());
        List<Coupon> couponList = page.getRecords();
        if (CollUtils.isEmpty(couponList)) {
            return PageDTO.empty(page);
        }
        // 2.封装VO返回
        List<CouponPageVO> couponPageVOS = BeanUtils.copyList(couponList, CouponPageVO.class);
        return PageDTO.of(page, couponPageVOS);
    }

    /**
     * 管理端修改优惠券信息
     * @param id 需要修改的优惠券id
     * @param couponFormDTO 修改的内容
     */
    @Override
    public void updateCoupon(Long id, CouponFormDTO couponFormDTO) {
        // 1.根据id查询优惠券表
        Coupon coupon = this.getById(id);
        if (coupon == null) {
            throw new BadRequestException("该优惠券不存在");
        }
        // 2.判断该优惠券状态，只有是待发放的优惠券才能修改
        if (!coupon.getStatus().equals(CouponStatus.DRAFT)){
            return;
        }
        Coupon updatedCoupon = BeanUtils.copyBean(couponFormDTO, Coupon.class);
        this.updateById(updatedCoupon);
        // 3.如果需要修改的优惠券指定范围，需要删除后插入
        if (coupon.getSpecific()) {
            couponScopeService.removeById(id);
        }
        // 4.新增修改后的内容到coupon_scope表
        List<Long> scopes = couponFormDTO.getScopes();
        if (CollUtils.isNotEmpty(scopes) && coupon.getSpecific()) {
            List<CouponScope> couponScopes = scopes
                    .stream()
                    .map(aLong -> new CouponScope().setCouponId(couponFormDTO.getId()).setBizId(aLong).setType(1))
                    .collect(Collectors.toList());
            couponScopeService.saveBatch(couponScopes);
        }
    }

    /**
     * 管理端删除待发放优惠券
     * @param id 需要删除的优惠券id
     */
    @Override
    @Transactional
    public void deleteCoupon(Long id) {
        // 1.根据id查询数据库
        Coupon coupon = this.lambdaQuery().eq(Coupon::getId, id).one();
        if (coupon == null) {
            throw new BadRequestException("该优惠券不存在");
        }
        // 2.判断是否是待发放
        if (!coupon.getStatus().equals(CouponStatus.DRAFT)){
            throw new BadRequestException("无法删除");
        }
        // 3.只有待发放才能删除
        this.removeById(id);
        // 4.如果优惠券存在指定范围也需要删除coupon_scope表
        if (coupon.getSpecific()) {
            couponScopeService.removeById(id);
        }
    }

    /**
     * 根据优惠券id查询具体信息
     * @param id 需要查询的优惠券id
     * @return 返回查看结果
     */
    @Override
    public CouponDetailVO getCouponById(Long id) {
        // 1.根据id查询coupon表
        Coupon coupon = this.lambdaQuery().eq(Coupon::getId, id).one();
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在");
        }
        CouponDetailVO couponDetailVO = BeanUtils.copyBean(coupon, CouponDetailVO.class);
        // 2.判断优惠券是否有范围，有就封装
        if (!coupon.getSpecific()) {
            return couponDetailVO;
        }
        List<CouponScope> couponScopes = couponScopeService.lambdaQuery().eq(CouponScope::getCouponId, coupon.getId()).list();
        if (CollUtils.isEmpty(couponScopes)) {
            return couponDetailVO;
        }
        // 远程调用分类接口得到分类名称
        Map<Long, String> map = categoryClient.getAllOfOneLevel().stream().collect(Collectors.toMap(CategoryBasicDTO::getId, CategoryBasicDTO::getName));
        // 3.封装返回
        List<CouponScopeVO> couponScopeVOList = new ArrayList<>();
        for (CouponScope couponScope : couponScopes) {
            CouponScopeVO couponScopeVO = BeanUtils.copyBean(couponScope, CouponScopeVO.class);
            couponScopeVO.setId(couponScope.getBizId());
            couponScopeVO.setName(map.get(couponScope.getBizId()));
            couponScopeVOList.add(couponScopeVO);
        }
        couponDetailVO.setScopes(couponScopeVOList);
        return couponDetailVO;
    }

    /**
     * 新增优惠券-管理端
     * @param id 需要新增的优惠券id
     * @param couponIssueFormDTO 需要新增id的详细信息
     */
    @Override
    public void issueCoupon(Long id, CouponIssueFormDTO couponIssueFormDTO) {
        // 1.校验id是否一致
        if (id == null || !id.equals(couponIssueFormDTO.getId())) {
            throw new BadRequestException("非法参数");
        }
        // 2.根据id查询优惠券是否存在
        Coupon coupon = this.getById(id);
        if (coupon == null) {
            throw new BadRequestException("需要重新发放的优惠券不存在");
        }
        // 3.校验是否是优惠券状态是否是暂停和待发放-可以重新发放
        if (!coupon.getStatus().equals(CouponStatus.PAUSE) && !coupon.getStatus().equals(CouponStatus.DRAFT)) {
            throw new BadRequestException("只有待发放或者暂停状态可以重新发放");
        }
        // 4.更新发放信息
        // 判断是否是立刻发放，起始时间为空或者当前时间在起始时间之前
        LocalDateTime now = LocalDateTime.now();
        boolean isCurrentIssue = couponIssueFormDTO.getIssueBeginTime() == null || !couponIssueFormDTO.getIssueBeginTime().isAfter(now);/*
        if (isCurrentIssue) {
            // 如果是当前时间发放，设置状态为发行中
            coupon.setIssueBeginTime(now);
            coupon.setIssueEndTime(couponIssueFormDTO.getIssueEndTime());
            coupon.setStatus(CouponStatus.ISSUING);
            coupon.setTermBeginTime(couponIssueFormDTO.getTermBeginTime());
            coupon.setTermEndTime(couponIssueFormDTO.getTermEndTime());
            coupon.setTermDays(couponIssueFormDTO.getTermDays());
        } else {
            // 如果不是，需要设置状态为未开始
            coupon.setIssueBeginTime(couponIssueFormDTO.getIssueBeginTime());
            coupon.setIssueEndTime(couponIssueFormDTO.getIssueEndTime());
            coupon.setStatus(CouponStatus.UN_ISSUE);
            coupon.setTermBeginTime(couponIssueFormDTO.getTermBeginTime());
            coupon.setTermEndTime(couponIssueFormDTO.getTermEndTime());
            coupon.setTermDays(couponIssueFormDTO.getTermDays());
        }
        this.updateById(coupon);*/
        // 方式二
        Coupon couponDB = BeanUtils.copyBean(couponIssueFormDTO, Coupon.class);
        if (isCurrentIssue) {
            couponDB.setIssueBeginTime(now);
            couponDB.setStatus(CouponStatus.ISSUING);
        } else {
            coupon.setStatus(CouponStatus.UN_ISSUE);

        }
        this.updateById(couponDB);
        // 5.如果是发行状态，将 发行时间，截止时间，发行的总数量，每人限量数量存入redis缓存中，方便领取优惠券接口快速取出数据判断校验
        if (isCurrentIssue) {
            String issuingCouponKey = PromotionConstants.COUPON_CACHE_KEY_PREFIX + id;
/*            // 方式一：操作redis次数多
            redisTemplate.opsForHash().put(issuingCouponKey, "issueBeginTime", String.valueOf(DateUtils.toEpochMilli(now)));
            redisTemplate.opsForHash().put(issuingCouponKey, "issueEndTime", String.valueOf(DateUtils.toEpochMilli(couponIssueFormDTO.getIssueEndTime())));
            redisTemplate.opsForHash().put(issuingCouponKey, "totalNum", String.valueOf(coupon.getTotalNum()));
            redisTemplate.opsForHash().put(issuingCouponKey, "userLimit", String.valueOf(coupon.getUserLimit()));*/
            // 方式二：仅一次操作redis
            Map<String, String> stringMap = new HashMap<>();
            stringMap.put("issueBeginTime", String.valueOf(DateUtils.toEpochMilli(now)));
            stringMap.put("issueEndTime", String.valueOf(DateUtils.toEpochMilli(couponIssueFormDTO.getIssueEndTime())));
            stringMap.put("totalNum", String.valueOf(coupon.getTotalNum()));
            stringMap.put("userLimit", String.valueOf(coupon.getUserLimit()));
            redisTemplate.opsForHash().putAll(issuingCouponKey, stringMap);
        }

        // 6.如果发放的优惠券为指定范围以及最初状态为待发放，需要生成兑换码
        if (coupon.getObtainWay().equals(ObtainType.ISSUE) && coupon.getStatus().equals(CouponStatus.DRAFT)) {
            coupon.setIssueEndTime(couponDB.getIssueEndTime()); // 兑换码兑换的截止时间就是优惠券的领取的截止时间
            exchangeCodeService.asyncGenerateExchangeCode(coupon);
        }

    }

    /**
     * 用户端查询发行中的优惠券列表
     * @return 返回优惠券列表信息
     */
    @Override
    public List<CouponVO> queryIssuingCouponsList() {
        // 1.查询coupon表，获取优惠券列表 条件 发行中； 领取方式为手动领取方式
        List<Coupon> couponList = this.lambdaQuery()
                .eq(Coupon::getStatus, CouponStatus.ISSUING)
                .eq(Coupon::getObtainWay, ObtainType.PUBLIC)
                .list();
        if (CollUtils.isEmpty(couponList)) {
            return Collections.emptyList();
        }
        // 2.根据发行中的优惠券ids查询 user_coupon表，条件：当前用户id和在这些发行中的优惠券ids，得到用户已经在这些优惠券中已领取了的优惠券id
        Set<Long> couponIds = couponList.stream().map(Coupon::getId).collect(Collectors.toSet());
        List<UserCoupon> userCoupons = userCouponService.lambdaQuery()
                .eq(UserCoupon::getUserId, UserContext.getUser())
                .in(UserCoupon::getCouponId, couponIds)
                .list();
        // 3.1获取当前用户的在这些已领取优惠券id中的数量
/*        // 方式一： key就是优惠券id，value就是该优惠券领取的次数
        Map<Long, Long> map= new HashMap<>();
        for (UserCoupon userCoupon : userCoupons) {
            Long num = map.get(userCoupon.getCouponId());
            if (num == null) {
                // 如果没有就添加
                map.put(userCoupon.getCouponId(), 1L);
            } else {
                // 有就在原来的基础上添加一次
                map.put(userCoupon.getCouponId(), num + 1L);
            }
        }*/
        // 方式二：
        Map<Long, Long> couponCountsMap = userCoupons.stream()
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
        // 3.2过滤得到当前用户已领取并且状态为未使用的数量
        Map<Long, Long> unUseCouponCountMap = userCoupons.stream()
                .filter(UserCoupon -> UserCoupon.getStatus().equals(UserCouponStatus.UNUSED))
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
        // 4.封装VO集合返回
        List<CouponVO> couponVOList = new ArrayList<>();
        for (Coupon coupon : couponList) {
            CouponVO couponVO = BeanUtils.copyBean(coupon, CouponVO.class);
            Long orDefault = couponCountsMap.getOrDefault(coupon.getId(), 0L);
            boolean available = coupon.getIssueNum() < coupon.getTotalNum() &&  orDefault < coupon.getUserLimit();
            couponVO.setAvailable(available); // 是否可以获取该优惠券，条件 ：已经发行数量小于总数量并且用户已领取的数量小于该优惠券的每人限领数量
            Long aDefault = unUseCouponCountMap.getOrDefault(coupon.getId(), 0L);
            boolean receive =  aDefault > 0;
            couponVO.setReceived(receive); // 是否使用，条件：如果用户已领取的优惠券并且状态为未使用，会显示去使用
            couponVOList.add(couponVO);
        }
        return couponVOList;
    }

    /**
     * 管理端 暂停发放中的优惠券
     * @param id 需要暂停的优惠券id
     */
    @Override
    public void pauseIssuingCoupon(Long id) {
        // 1.根据id查询优惠券
        Coupon coupon = this.getById(id);
        // 2.判断该优惠券是否存在
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在");
        }
        // 3.更新优惠券的状态为 暂停 条件是：必须是处于发行中的优惠券
        this.lambdaUpdate()
                .eq(Coupon::getStatus, CouponStatus.ISSUING)
                .set(Coupon::getStatus, CouponStatus.PAUSE)
                .update(coupon);
    }
}
