package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardItemVO;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.mapper.PointsBoardMapper;
import com.tianji.learning.service.IPointsBoardService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.utils.TableInfoContext;
import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;


import javax.validation.constraints.Min;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tianji.learning.constants.LearningConstants.POINTS_BOARD_TABLE_PREFIX;

/**
 * <p>
 * 学霸天梯榜 服务实现类
 * </p>
 *
 * @author author
 * @since 2025-10-12
 */
@Service
@RequiredArgsConstructor
public class PointsBoardServiceImpl extends ServiceImpl<PointsBoardMapper, PointsBoard> implements IPointsBoardService {
    final StringRedisTemplate stringRedisTemplate;
    final UserClient userClient;
    /**
     * 查询学霸积分排行榜
     * 实时赛季的数据是存储在redis中的，历史赛季的数据信息是持久化存储在MySQL中的
     * 每当新的赛季初的时候，会将赛季初的上一个赛季数据（存储在redis中）会持久化到mysql，接着会清除redis中数据，用来存储当前新赛季数据
     * @param query 分页参数及判断
     * @return 返回积分排行榜信息
     */
    @Override
    public PointsBoardVO queryBoardsRank(PointsBoardQuery query) {
        // 1.判断是否是实时赛季或者历史赛季，通过前端前端传来的查询条件信息
        boolean isCurrentSeason = (query.getSeason() == 0) || (query.getSeason() == null);
        // 拼接key
        LocalDate date = LocalDate.now();
        String format = date.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String boardKey = RedisConstants.POINTS_BOARD_KEY_PREFIX + format;
        // 2.查询我的积分排行榜信息，还需要判断是实时赛季还是历史赛季
        PointsBoard pointsBoard = isCurrentSeason ? queryMyCurrentSeason(boardKey) : queryMyHistoricalSeason(query.getSeason());
        if (pointsBoard == null) {
            return null;
        }
        // 3.分页查询积分排行榜信息，需要判断是实时赛季还是历史赛季
        List<PointsBoard> pointsBoardList = isCurrentSeason ?
                queryCurrentSeasonPage(boardKey, query.getPageNo(), query.getPageSize()) :
                queryHistoricalSeasonPage(query);
        if (CollUtils.isEmpty(pointsBoardList)) {
            return  null; //如果没有排行信息返回空
        }
        // 4.远程调用用户服务
        List<Long> userIds = pointsBoardList.stream().map(PointsBoard::getUserId).collect(Collectors.toList());
        List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
        if (CollUtils.isEmpty(userDTOS)) {
            throw new BizIllegalException("用户不存在");
        }
        Map<Long, String> userMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, UserDTO::getName));
        // 5.遍历封装VO返回
        PointsBoardVO pointsBoardVO = new PointsBoardVO();
        pointsBoardVO.setRank(pointsBoard.getRank());
        pointsBoardVO.setPoints(pointsBoard.getPoints());
        List<PointsBoardItemVO> pointsBoardItemVOS = new ArrayList<>();
        for (PointsBoard point : pointsBoardList) {
            PointsBoardItemVO pointsBoardItemVO = new PointsBoardItemVO();
            pointsBoardItemVO.setName(userMap.get(point.getUserId()));
            pointsBoardItemVO.setRank(point.getRank());
            pointsBoardItemVO.setPoints(point.getPoints());
            pointsBoardItemVOS.add(pointsBoardItemVO);
        }
        pointsBoardVO.setBoardList(pointsBoardItemVOS);

        return pointsBoardVO;
    }

    /**
     * 分页查询历史赛季排行榜信息
     * @param query 分页条件
     * @return 返回分页结果
     */
    private List<PointsBoard> queryHistoricalSeasonPage(PointsBoardQuery query) {
        // 1.将表名存入本地线程
        String tableName = POINTS_BOARD_TABLE_PREFIX + query.getSeason();
        TableInfoContext.setInfo(tableName);
        // 2.分页查询数据库
        Page<PointsBoard> page = this.lambdaQuery()
                .eq(PointsBoard::getSeason, query.getSeason())
                .page(query.toMpPage(new OrderItem("id", false)));
        List<PointsBoard> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            throw new BadRequestException("历史赛季积分排行榜不存在");
        }
        // 3.封装返回
        for (PointsBoard record : records) {
            record.setRank(record.getId().intValue());
        }
        return records;
    }


    /**
     * 分页查询实时赛季积分排行榜
     * @param boardKey key
     * @param pageNo 页码
     * @param pageSize 条数
     * @return 返回分页结果
     */
    public List<PointsBoard> queryCurrentSeasonPage(String boardKey,
                                                     @Min(value = 1, message = "页码不能小于1") Integer pageNo,
                                                     @Min(value = 1, message = "每页查询数量不能小于1") Integer pageSize) {
        // 1.分页参数设置
        int start = (pageNo - 1) * pageSize;
        int end = pageSize + start - 1;
        // 2.查询redis
        Set<ZSetOperations.TypedTuple<String>> reverseRangeByScore = stringRedisTemplate.opsForZSet().reverseRangeWithScores(boardKey, start, end);
        if (CollUtils.isEmpty(reverseRangeByScore)) {
            return CollUtils.emptyList();
        }
        // 3.遍历封装结果
        int rank = 1 + start;
        List<PointsBoard> pointsBoardList = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : reverseRangeByScore) {
            PointsBoard pointsBoard = new PointsBoard();
            String userId = tuple.getValue(); // 用户id
            Double score = tuple.getScore();// 不同用户总积分积分
            if (StringUtils.isBlank(userId) || score == null) {
                continue; // 不存在就遍历下一个用户
            }
            pointsBoard.setPoints(score.intValue());
            pointsBoard.setUserId(Long.valueOf(userId));
            pointsBoard.setRank(rank++);
            pointsBoardList.add(pointsBoard);
        }
        return pointsBoardList;

    }

    /**
     * 查询我的历史积分排行（一条并非分页）
     * 从数据库中查询
     * @param seasonId 赛季id
     * @return 返回我的历史积分排行结果
     */
    private PointsBoard queryMyHistoricalSeason(Long seasonId) {
        // 1.获取登录用户id
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new BadRequestException("用户未登录");
        }
        String tableName = POINTS_BOARD_TABLE_PREFIX + seasonId;

        TableInfoContext.setInfo(tableName);

        // 2.查询数据库
        PointsBoard pointsBoard = this.lambdaQuery()
                .eq(PointsBoard::getUserId, userId)
                .eq(PointsBoard::getSeason, seasonId).one();
        // 3.将表名存入本地线程
        // 4.封装返回
        pointsBoard.setRank(pointsBoard.getId().intValue());
        return pointsBoard;
    }

    /**
     * 查询我的实时积分排行（就一条并非分页）
     * @param boardKey 查询redis 的 key
     * @return 返回我的实时积分排行榜
     */
    private PointsBoard queryMyCurrentSeason(String boardKey) {
        // 1.获取当前登录用户id
        Long userId = UserContext.getUser();
        // 2.通过key查询存储在ZSet结构中总积分和排行
        Double score = stringRedisTemplate.opsForZSet().score(boardKey, userId.toString());// 总积分
        Long rank = stringRedisTemplate.opsForZSet().reverseRank(boardKey, userId.toString()); // 排行信息
        // 3.封装返回
        PointsBoard pointsBoard = new PointsBoard();
        pointsBoard.setPoints(score == null ? 0 : score.intValue());
        pointsBoard.setRank(rank == null ? 0 : rank.intValue() + 1); // redis得到的排行是按索引，需要 + 1；
        return pointsBoard;
    }
}
