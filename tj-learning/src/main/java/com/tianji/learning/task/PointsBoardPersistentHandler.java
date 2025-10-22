package com.tianji.learning.task;

import com.tianji.common.utils.CollUtils;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.tianji.learning.constants.LearningConstants.PAGE_NUMBER;
import static com.tianji.learning.constants.LearningConstants.POINTS_BOARD_TABLE_PREFIX;
import static com.tianji.learning.constants.RedisConstants.POINTS_BOARD_KEY_PREFIX;
import static com.tianji.learning.constants.RedisConstants.SIGN_RECORD_KEY_PREFIX;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointsBoardPersistentHandler {
    final IPointsBoardSeasonService pointsBoardSeasonService;
    final IPointsBoardService pointsBoardService;
    final StringRedisTemplate redisTemplate;
/*
    @Scheduled(cron = "0 0 3 1 * ?") // 每个月第一天凌晨三点执行
*/  // 定时任务-动态表名的生成
    @XxlJob("createTableJob")
    public void createPointsBoardOfLastSeason() {
        // 1.在每一个新的月初获取这个点在上个月的时间点
        LocalDate today = LocalDate.now();
        LocalDate localDate = today.minusMonths(1); // 获取上个月的时间点
        // 2.查询对应的赛季id
        PointsBoardSeason one = pointsBoardSeasonService
                .lambdaQuery()
                //  每个赛季的开始时间  <= 当前时间减去一个月 <= 每个赛季结束时间
                .le(PointsBoardSeason::getBeginTime, localDate)
                .ge(PointsBoardSeason::getEndTime, localDate)
                .one();
        log.debug("上个赛季信息 {}", one);
        if (one == null) {
            return;
        }
        // 3.根据赛季id创建不同表
        pointsBoardSeasonService.createPointsBoardLatestTable(one.getId());
    }

    // 定时任务 - 将redis中的存储的赛季信息持久化    到上个赛季的DB中
    @XxlJob("savePointsBoard2DB")
    public void savePointsBoard2DB() {
        // 1.查询数据库获取上个月的赛季信息
        LocalDate today = LocalDate.now();
        LocalDate localDate = today.minusMonths(1);// 当前时间的上个月时间
        // select * from points_board_season where begin_time <= ? and  ? >= end_time;
        PointsBoardSeason one = pointsBoardSeasonService
                .lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, localDate)
                .ge(PointsBoardSeason::getEndTime, localDate)
                .one();
        log.debug("上个赛季信息 {}", one);
        if (one == null) {
            return;
        }
        // 2.将上个赛季的表名存入本地线程中
        String tableName = POINTS_BOARD_TABLE_PREFIX + one.getId();
        log.debug("动态表名 {}", tableName);
        TableInfoContext.setInfo(tableName);
        // 3.拼接key 分页查询redis中的积分榜数据
        String format = today.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key =  POINTS_BOARD_KEY_PREFIX + format;
        int shardIndex = XxlJobHelper.getShardIndex(); // 获取分片索引
        int shardTotal = XxlJobHelper.getShardTotal(); // 获取分片总数
        int pageNo = shardIndex + 1; // 分片索引从0开始，所以要+1,表示从第一页开始查询
        int pageSize = PAGE_NUMBER;
        // 4.将查询到的redis积分榜数据持久化到mysql中
        while (true) {
            List<PointsBoard> pointsBoardList = pointsBoardService.queryCurrentSeasonPage(key, pageNo, pageSize);
            if (CollUtils.isEmpty(pointsBoardList)) {
                break; // 无分页结果跳出循环并不是返回
            }
            pageNo += shardTotal; // 每一次分页查询都要增加分片总数，获取下一页数据，所有的页码都会被覆盖

            for (PointsBoard pointsBoard : pointsBoardList) {
                // 其他积分总数和用户id已经赋值了，由于新表的字段将rank作为id，但是我们不需要rank字段
                pointsBoard.setId(pointsBoard.getRank().longValue());
                pointsBoard.setRank(null);
            }
            pointsBoardService.saveBatch(pointsBoardList);
        }
        // 5.移除线程中的内容
        TableInfoContext.remove();

    }
    // 定时任务 - 清除redis中的数据
    @XxlJob("clearPointsBoardFromRedis")
    public void clearPointsBoardFromRedis(){
        // 1.获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 2.计算key
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateTimeFormatter.ofPattern("yyyyMM"));
        // 3.删除
        redisTemplate.unlink(key);
    }


}
