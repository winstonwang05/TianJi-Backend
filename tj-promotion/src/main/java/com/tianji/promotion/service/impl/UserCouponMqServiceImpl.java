package com.tianji.promotion.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.extension.plugins.pagination.DialectFactory;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.discount.Discount;
import com.tianji.promotion.discount.DiscountStrategy;
import com.tianji.promotion.domain.dto.CouponDiscountDTO;
import com.tianji.promotion.domain.dto.OrderCourseDTO;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.tianji.promotion.utils.CodeUtil;
import com.tianji.promotion.utils.PermuteUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jcajce.provider.asymmetric.X509;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.module.Configuration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author author
 * @since 2025-10-16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserCouponMqServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {
    final CouponMapper couponMapper;
    final IExchangeCodeService exchangeCodeService;
    final RedissonClient redissonClient;
    final StringRedisTemplate stringRedisTemplate;
    final RabbitMqHelper rabbitMqHelper;
    final ICouponScopeService couponScopeService;
    /***
     * 领取优惠券
     * @param id 需要领取的优惠券id
     */
    @Override
    @Transactional
    public void receiveCoupon(Long id) {
        // I.获取锁对象
        String lockKey = "lock:coupon:uid" + id;
        RLock lock = redissonClient.getLock(lockKey);
        // II.尝试获取锁
        try {
            boolean tryLock = lock.tryLock();
            if (tryLock) {
                // 1.校验id是否存在
                if (id == null) {
                    throw new RuntimeException("id is null");
                }
                // 2.根据优惠券id查询coupon表，得到coupon对象
                /*Coupon coupon = couponMapper.selectById(id);*/
                // 从缓存中取数据
                Coupon coupon = queryCouponByCathe(id);
                if (coupon == null) {
                    throw new BadRequestException("优惠券不存在");
                }
                // 3.判断是否发行
                LocalDateTime now = LocalDateTime.now();
                if (now.isBefore(coupon.getIssueBeginTime()) || now.isAfter(coupon.getIssueEndTime())) {
                    throw new BadRequestException("优惠券未发行");
                }
                // 存入缓存中已经检验
/*                if (!coupon.getStatus().equals(CouponStatus.ISSUING)) {
                    throw new BadRequestException("优惠券未发行");
                }*/
                // 4.判断库存是否充足
                if (coupon.getTotalNum() <= 0) {
                    throw new BadRequestException("优惠券库存不足");
                }

                // 5.判断是否达到每人上限
                String fieldKey = PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX + id;
                String userId = String.valueOf(UserContext.getUser());
                // 在原来的已经领取的数量上  + 1
                Long increment = stringRedisTemplate.opsForHash().increment(fieldKey, userId, 1);
                if (increment > coupon.getUserLimit()) {
                    throw new BadRequestException("超出每人限领数量");
                }

                // 需要修改总发行优惠券数量 - 1
                String couponCatheKey = PromotionConstants.COUPON_CACHE_KEY_PREFIX + id;
                stringRedisTemplate.opsForHash().increment(couponCatheKey, "totalNum", -1);
                UserCouponDTO message = new UserCouponDTO();
                message.setCouponId(id);
                message.setUserId(Long.valueOf(userId));
                rabbitMqHelper.send(
                        MqConstants.Exchange.PROMOTION_EXCHANGE,
                        MqConstants.Key.COUPON_RECEIVE,
                        message
                );
            }
        } finally {
            // III.释放锁
            lock.unlock();
        }


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
        saveUserCoupon(userId, coupon);*/
/*        // 通过悲观锁
        Long userId = UserContext.getUser();
        synchronized (userId.toString().intern()) {
            // 通过APO上下文代理对象获取当前类的代理对象
            IUserCouponService userCouponService = (IUserCouponService)AopContext.currentProxy();
            // 用当前类的代理对象来调用事务方法
            userCouponService.checkAndCreateUserCoupon(coupon, userId, null);
        }*/

/*        // 通过Redisson分布式锁
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
        }*/

    }

    /**
     * 从redis HashSet中获取coupon对象
     * @param id 优惠券id 用来拼接key
     * @return 返回coupon对象
     */
    private Coupon queryCouponByCathe(Long id) {
        String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + id;
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        return BeanUtils.mapToBean(entries, Coupon.class, false, CopyOptions.create());// 不需要开启驼峰
    }

    /**
     * 保存优惠券信息到user_coupon表
     * @param userId 用户id
     * @param coupon 优惠券对象
     */
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

    /**
     * 兑换码兑换优惠券
     * @param code 需要兑换的兑换码
     */
    @Override
    @Transactional
    public void couponByExchangeCode(String code) {
        Long userId = UserContext.getUser();
        String lockKey = "lock:coupon:uid" + userId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean tryLock = lock.tryLock();
            if (tryLock) {
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
/*            // 4.根据 自增id查询exchange_code表得到对应实体类对象，判断是否存在
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

            *//*checkAndCreateUserCoupon(coupon, userId, incrementId);*//*
                     *//*            synchronized (userId.toString().intern()) {
                checkAndCreateUserCoupon(coupon, userId, incrementId);
            }*//*

            checkAndCreateUserCoupon(coupon, userId, incrementId);*/

                    // 从缓存中取出couponId
                    Long couponId = exchangeCodeService.getExchangeTargetIdFromCathe(incrementId);
                    // 校验是否存在
                    String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId;
                    Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
                    Coupon coupon = BeanUtils.mapToBean(entries, Coupon.class, false, CopyOptions.create());
                    if (coupon == null) {
                        throw new BadRequestException("优惠券不存在");
                    }
                    // 校验优惠券是否过期
                    LocalDateTime now = LocalDateTime.now();
                    if (now.isBefore(coupon.getIssueBeginTime()) || now.isAfter(coupon.getIssueEndTime())) {
                        throw new BadRequestException("未发行或者过期");
                    }
                    // 校验限领数量
                    String userReceiveKey = PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX + couponId;
                    Long increment = stringRedisTemplate.opsForHash().increment(userReceiveKey, String.valueOf(userId), 1);
                    if (increment > coupon.getUserLimit()) {
                        throw new BadRequestException("超过限领数量");
                    }

                    // 兑换码无需检查库存
                    // 发送消息MQ消费 ，更新发放数量，生成优惠券 ，并修改兑换码状态
                    UserCouponDTO message = new UserCouponDTO();
                    message.setCouponId(coupon.getId());
                    message.setUserId(userId);
                    message.setIncrementId(incrementId);
                    rabbitMqHelper.send(
                            MqConstants.Exchange.PROMOTION_EXCHANGE,
                            MqConstants.Key.COUPON_EXCHANGE,
                            message
                    );


                } catch (Exception e) {
                    exchangeCodeService.updateExchangeCodeMark(incrementId, false);
                    throw new BadRequestException(e.getMessage());
                }
            }

        } finally {
            lock.unlock();
        }

    }

    /**
     * 监听者 消费消息，也就是生成优惠券 ，领取优惠券
     * @param userCouponDTO 消息内容，包括couponId，userId
     */
    @Override
    @Transactional
    public void createUserCoupon(UserCouponDTO userCouponDTO) {
        // 1.根据couponId查询coupon表
        Long couponId = userCouponDTO.getCouponId();
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            return; // 消费者继续重试
        }
        // 2.更新发行数量
        int issueNum = couponMapper.incrIssueNum(coupon.getId());
        if (issueNum == 0) {
            return;
        }
        // 3.生成优惠券
        saveUserCoupon(userCouponDTO.getUserId(), coupon);
    }

    /**
     * 校验是否超过购买上限以及生成优惠券信息
     * @param coupon 优惠券对象
     * @param userId 当前登录用户id
     * @param incrementId 自增id，用来更新兑换码状态
     */
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
        int issueNum = couponMapper.incrIssueNum(coupon.getId());
            if (issueNum == 0) {
                // 更新语句成功返回1，失败返回0
                throw new BadRequestException("优惠券不足");
            }
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

    /**
     * 通过兑换码兑换优惠券 生成优惠券 并更新优惠券状态为已兑换
     * @param userCouponDTO 消息内容
     */
    @Override
    @Transactional
    public void createUserCouponByExchangeCode(UserCouponDTO userCouponDTO) {
        // 更新优惠券发放数量 + 1
        int issueNum = couponMapper.incrIssueNum(userCouponDTO.getCouponId());
        if (issueNum == 0) {
            return; // 重试
        }
        Coupon coupon = couponMapper.selectById(userCouponDTO.getCouponId());
        // 生成优惠券
        saveUserCoupon(userCouponDTO.getUserId(), coupon);
        // 更新兑换码状态
        exchangeCodeService.lambdaUpdate()
                .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                .set(ExchangeCode::getUserId, userCouponDTO.getUserId())
                .eq(ExchangeCode::getId, userCouponDTO.getCouponId())
                .update();
    }

    /**
     * 分页查询我的优惠券
     * @param query 查询条件 包含分页参数以及 需要查询状态一个属性  已过期 未使用  已使用
     * @return 返回分页后的优惠券信息
     */
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

    /**
     * 查询出可以利用的优惠券组合方案 所以是一个集合封装
     * @param orderCourseDTOS 前端传来的请求参数 包括每一个订单的id 三级分类id 价格
     * @return 返回优惠券的组合方案 返回的是最优组合
     */
    @Override
    public List<CouponDiscountDTO> queryUserCouponsAvailable(List<OrderCourseDTO> orderCourseDTOS) {
        // 1.查询 user_coupon表 和 coupon表 条件 ：userId， 状态为 未使用 需要查询的字段，优惠券id，用户券id（用来更新优惠券状态）， 优惠券规则
        List<Coupon> couponList = getBaseMapper().queryMyCoupons(UserContext.getUser());
        if (CollUtils.isEmpty(couponList)) {
            return CollUtils.emptyList();
        }
        log.debug("用户拥有的优惠券且未使用的个数 {}", couponList.size());
        // 2.初筛 也就是将当前用户拥有且未使用的优惠券如果门槛超过 总价格，需要过滤掉
        // 2.1 累加各课程价格得到总价格
        int totalPriceNumber = orderCourseDTOS.stream().mapToInt(OrderCourseDTO::getPrice).sum();
        log.debug("用户购买的总价格 {}", totalPriceNumber);
/*        List<Coupon> coupons = new ArrayList<>();
        // 2.2 方式一：过滤得到满足条件的优惠券
        for (Coupon coupon : couponList) {
            // 是否可用
            boolean canUse = DiscountStrategy.getDiscount(coupon.getDiscountType()).canUse(totalPriceNumber, coupon);
            if (canUse) {
                coupons.add(coupon);
            }
        }*/
        // 2.2方式二：过滤得到满足条件的优惠券集合
/*        List<Coupon> coupons = couponList.stream().filter(new Predicate<Coupon>() {
            @Override
            public boolean test(Coupon coupon) {
                return DiscountStrategy.getDiscount(coupon.getDiscountType()).canUse(totalPriceNumber, coupon);
            }
        }).collect(Collectors.toList());*/
        List<Coupon> coupons = couponList.stream()
                .filter(coupon -> DiscountStrategy.getDiscount(coupon.getDiscountType()).canUse(totalPriceNumber, coupon))
                .collect(Collectors.toList());
        if (CollUtils.isEmpty(coupons)) {
            return CollUtils.emptyList();
        }
        log.debug("初筛之后满足条件的优惠券个数 {}", coupons.size());
        // 3.细筛 有些优惠券是存在使用对象的限定范围的 key 是优惠券对象 value 是能使用的优惠券的课程集合
        Map<Coupon, List<OrderCourseDTO>> availableCoupons = queryAvailableCoupons(coupons, orderCourseDTOS);
        if (CollUtils.isEmpty(availableCoupons)) {
            return CollUtils.emptyList();
        }
        log.debug("细筛之后满足条件的优惠券个数 {}", availableCoupons.size());

        // 4.将符合条件的所有优惠券排列组合
        List<Coupon> couponsList = new ArrayList<>(availableCoupons.keySet());
        List<List<Coupon>> permutations = PermuteUtil.permute(couponsList);
        log.debug("优惠券排列组合方案数 {}", permutations.size());

        // 5.利用多线程异步执行算出每种排列组合的结果
        // 使用并行流来异步计算每种方案的优惠金额
        List<CouponDiscountDTO> solutions = permutations.parallelStream()
                .map(couponCombination -> calculateSolutionDiscount(couponCombination, availableCoupons, orderCourseDTOS))
                .filter(solution -> solution.getDiscountAmount() > 0)
                .collect(Collectors.toList());

        // 6.筛选出最优的优惠券组合方案
        if (CollUtils.isEmpty(solutions)) {
            return CollUtils.emptyList();
        }

        // 按优惠金额降序排序，取最优方案
        solutions.sort((s1, s2) -> Integer.compare(s2.getDiscountAmount(), s1.getDiscountAmount()));
        log.debug("找到 {} 个有效方案，最优方案优惠金额 {}", solutions.size(), solutions.get(0).getDiscountAmount());

        // 返回优惠金额最大的方案
        return CollUtils.singletonList(solutions.get(0));
    }

    /**
     * 细筛方法：查询该优惠券的限定范围的课程，如果存在，将该优惠券对象作为key value就是能使用该优惠券对应的课程范围
     * @param coupons 初筛之后后 优惠券对象集合
     * @param orderCourseDTOS 前端传来的购买课程信息集合
     * @return 返回优惠券能够使用的限定范围  - 课程范围
     */
    private Map<Coupon, List<OrderCourseDTO>> queryAvailableCoupons(List<Coupon> coupons,
                                                                    List<OrderCourseDTO> orderCourseDTOS) {
        Map<Coupon, List<OrderCourseDTO>> availableCoupons = new HashMap<>(coupons.size());
        // 1.遍历初筛的优惠券对象集合
        for (Coupon coupon : coupons) {
            // 判断是否有限定范围
            List<OrderCourseDTO> couponOfAvailableCourses = orderCourseDTOS; // 如果没有限定范围就是前端传来的课程集合
            if (coupon.getSpecific()) {
                // 2.得到每个优惠券对象指定范围的课程范围
                // 2.1 先查询每个优惠券总的范围 查询条件 优惠券id  获取指定范围课程集合
                List<CouponScope> couponScopes = couponScopeService.lambdaQuery().eq(CouponScope::getCouponId, coupon.getId()).list();
                // 但是我们仅需要分类id集合
                List<Long> couponOfBizIds = couponScopes.stream().map(CouponScope::getBizId).collect(Collectors.toList());
                // 2.2返回前端传来 的购买课程范围 存在于 指定范围的课程范围
                couponOfAvailableCourses = orderCourseDTOS.stream()
                         .filter(orderCourseDTO -> couponOfBizIds.contains(orderCourseDTO.getCateId()))
                         .collect(Collectors.toList());
            }
            if (CollUtils.isEmpty(couponOfAvailableCourses)) {
                continue;
            }
            // 3.计算前端传来的课程总价格数
            int totalPriceAmount = orderCourseDTOS.stream().mapToInt(OrderCourseDTO::getPrice).sum();
            // 4.判断是否可用，如果可用，放入Map集合中 key 就是优惠券对象， value就是该优惠券对象 能够在前端传来的参数中符合指定范围的 课程集合
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if (discount.canUse(totalPriceAmount, coupon)) {
                availableCoupons.put(coupon, couponOfAvailableCourses);
            }
        }
        return availableCoupons;

    }

    /**
     * 计算某个优惠券组合方案的优惠金额
     * @param couponCombination 优惠券组合方案
     * @param availableCoupons 可用优惠券及其适用课程的映射
     * @param orderCourseDTOS 订单课程列表
     * @return 优惠券折扣结果DTO
     */
    private CouponDiscountDTO calculateSolutionDiscount(
            List<Coupon> couponCombination,
            Map<Coupon, List<OrderCourseDTO>> availableCoupons,
            List<OrderCourseDTO> orderCourseDTOS) {

        CouponDiscountDTO result = new CouponDiscountDTO();
        // 记录已经使用过优惠券的课程，避免重复使用
        Set<Long> usedCourseIds = new HashSet<>();
        // 总优惠金额
        int totalDiscount = 0;

        // 遍历优惠券组合中的每一张优惠券
        for (Coupon coupon : couponCombination) {
            // 获取该优惠券适用的课程列表
            List<OrderCourseDTO> applicableCourses = availableCoupons.get(coupon);
            if (CollUtils.isEmpty(applicableCourses)) {
                continue;
            }

            // 过滤出还未使用过优惠券的课程
            List<OrderCourseDTO> unusedCourses = applicableCourses.stream()
                    .filter(course -> !usedCourseIds.contains(course.getId()))
                    .collect(Collectors.toList());

            if (CollUtils.isEmpty(unusedCourses)) {
                // 没有可用的课程，跳过这张优惠券
                continue;
            }

            // 计算这些课程的总价
            int coursesTotalPrice = unusedCourses.stream()
                    .mapToInt(OrderCourseDTO::getPrice)
                    .sum();

            // 判断是否满足优惠券使用条件
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if (!discount.canUse(coursesTotalPrice, coupon)) {
                // 不满足使用条件，跳过这张优惠券
                continue;
            }

            // 计算优惠金额
            int discountAmount = discount.calculateDiscount(coursesTotalPrice, coupon);
            if (discountAmount > 0) {
                // 累加优惠金额
                totalDiscount += discountAmount;
                // 添加用户券ID（使用creater字段存储的用户券ID）
                result.getIds().add(coupon.getCreater());
                // 添加优惠券规则描述
                result.getRules().add(discount.getRule(coupon));
                // 标记这些课程已使用优惠券
                unusedCourses.forEach(course -> usedCourseIds.add(course.getId()));
            }
        }

        result.setDiscountAmount(totalDiscount);
        return result;
    }
}
