package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mapper.PointsRecordMapper;
import com.tianji.learning.mq.msg.SignInMessage;
import com.tianji.learning.service.IPointsRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务实现类
 * </p>
 *
 * @author author
 * @since 2025-10-12
 */
@Service
@RequiredArgsConstructor
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord> implements IPointsRecordService {
    final StringRedisTemplate redisTemplate;
    /**
     *  为指定业务添加积分  这是每一次的添加结果，并不是每次添加结果的总和
     * @param message 不同业务的每次添加积分大小 以及当前登录用户
     * @param pointsRecordType 不同业务（包括签到，问答，笔记等）
     */
    @Override
    public void addPointsRecord(SignInMessage message, PointsRecordType pointsRecordType) {

        // 1.判断每次添加的是否为空
        if (message.getPoints() == null || message.getUserId() == null) {
            return; // 不断重试
        }
        int realPoint = message.getPoints();
        // 2.判断是否是有上限     像签到业务就没有添加上限
        // 大于0则表示有上限
        if (pointsRecordType.getMaxPoints() > 0) {
            // 3.查询各自业务下在当天时间内的积分数
            // 查询条件：当前用户对应下的业务类型 在当天时间内 的积分总数数
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime dayStartTime = DateUtils.getDayStartTime(now);
            LocalDateTime dayEndTime = DateUtils.getDayEndTime(now);
            QueryWrapper<PointsRecord> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("user_Id", message.getUserId());
            queryWrapper.eq("type", pointsRecordType);
            queryWrapper.between("create_time", dayStartTime, dayEndTime);
            queryWrapper.select("sum(points) as totalPoints");
            Map<String, Object> map = this.getMap(queryWrapper);
            int currentPointsTotal = 0;
            if (map != null) {
                currentPointsTotal = Integer.parseInt(map.get("totalPoints").toString());
            }
            // 计算出这一次需要添加的积分数
            if (message.getPoints() + currentPointsTotal > pointsRecordType.getMaxPoints()) {
                realPoint = pointsRecordType.getMaxPoints() - message.getPoints();
            }
                // 4.判断这次添加是否超过上限的最大积分数
            if (message.getPoints() >= pointsRecordType.getMaxPoints()) {
                    return;
            }
        }
        // 5.封装每次添加积分结果保存到数据库中
        PointsRecord pointsRecord = new PointsRecord();
        pointsRecord.setUserId(message.getUserId());
        pointsRecord.setType(pointsRecordType);
        pointsRecord.setPoints(realPoint);
        this.save(pointsRecord);
        // 6.累加到redis的ZSet结构中
        // 拼接key
        LocalDate localDate = LocalDate.now();
        String formatted = localDate.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String pointsTotalKey = RedisConstants.POINTS_BOARD_KEY_PREFIX + formatted;
        redisTemplate.opsForZSet().incrementScore(pointsTotalKey, String.valueOf(message.getUserId()), realPoint);
    }

    /**
     * 查询今天我的积分情况
     * @return 返回个业务今天获取积分情况
     */
    @Override
    public List<PointsStatisticsVO> getPointsSituationToday() {
        // 1.获取当前登录用户id
        Long userId = UserContext.getUser();
        // 2.查询points_record 表获取当前登录用户对应业务的积分数 条件：用户id；当天时间内；获取总数
        // select type, sum(points) as userId from points_record
        // where userId = ?, type = ?, create_time between ? and ? Group by type
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayStartTime = DateUtils.getDayStartTime(now);
        LocalDateTime dayEndTime = DateUtils.getDayEndTime(now);
        QueryWrapper<PointsRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.between("create_time", dayStartTime, dayEndTime);
        queryWrapper.select("type", "sum(points) as userId"); // 由于总数在数据库并没有对应字段，随机用一个字段来借用
        queryWrapper.groupBy("type");
        List<PointsRecord> pointsRecordList = this.list(queryWrapper);
        if (CollUtils.isEmpty(pointsRecordList)) {
            return CollUtils.emptyList();
        }

        // 3.封装VO返回
        List<PointsStatisticsVO> pointsStatisticsVOList = new ArrayList<>();
        for (PointsRecord pointsRecord : pointsRecordList) {
            PointsStatisticsVO pointsStatisticsVO = new PointsStatisticsVO();
            pointsStatisticsVO.setMaxPoints(pointsRecord.getType().getMaxPoints());
            pointsStatisticsVO.setType(pointsRecord.getType().getDesc());
            pointsStatisticsVO.setPoints(pointsRecord.getUserId().intValue());
            pointsStatisticsVOList.add(pointsStatisticsVO);
        }
        return pointsStatisticsVOList;
    }
}
