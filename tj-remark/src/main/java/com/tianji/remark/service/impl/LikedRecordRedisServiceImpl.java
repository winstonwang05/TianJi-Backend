package com.tianji.remark.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.SPELUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.constants.RedisConstants;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.dto.LikedTimesDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author author
 * @since 2025-10-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LikedRecordRedisServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper rabbitMqHelper;
    private final StringRedisTemplate stringRedisTemplate;
    /**
     * 实现点赞或者取消点赞
     * @param likeRecordFormDTO 前端传来的参数，包括点赞的id，点赞的类型
     */
    @Override
    public void addLikeRecord(LikeRecordFormDTO likeRecordFormDTO) {
        // 1.获取当前登录用户id
        Long userId = UserContext.getUser();
/*        // 2.如果是点赞逻辑
        boolean flag = false;
        if (likeRecordFormDTO.getLiked()) {
            flag = like(userId, likeRecordFormDTO);
        } else {
            // 3.如果是取消点赞逻辑
            flag = unlike(userId, likeRecordFormDTO);
        }*/
        // 优化
        boolean flag = likeRecordFormDTO.getLiked() ? like(userId, likeRecordFormDTO) : unlike(userId, likeRecordFormDTO);
        if (!flag) {
            return;
        }
        // 4.查询点赞表中对应业务的点赞量
        /*Integer totalLikeNum = this.lambdaQuery().eq(LikedRecord::getBizId, likeRecordFormDTO.getBizId()).count();*/
/*        LikedTimesDTO likedTimesDTO = new LikedTimesDTO();
        likedTimesDTO.setLikedTimes(totalLikeNum);
        likedTimesDTO.setBizId(likeRecordFormDTO.getBizId());*/

/*        // 方式二：
        LikedTimesDTO likedTimesDTO = LikedTimesDTO.builder().likedTimes(totalLikeNum).bizId(likeRecordFormDTO.getBizId()).build();*/
        // 方式三：

/*        LikedTimesDTO likedTimesDTO = LikedTimesDTO.of(likeRecordFormDTO.getBizId(), totalLikeNum);
        // 5.通过mq发送点赞量到对应的业务（笔记或者回答）
        String constantKey = StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, likeRecordFormDTO.getBizId());
        log.debug("发送点赞消息 {}", likedTimesDTO);
        rabbitMqHelper.send(
                MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
                constantKey,
                likedTimesDTO
        );*/
        // 通过redis来存储点赞总数
        String userLikeRecordKey = RedisConstants.LIKE_BIZ_KEY_PREFIX + likeRecordFormDTO.getBizId();
        String totalLikesOfBizType = RedisConstants.LIKE_COUNT_KEY_PREFIX + likeRecordFormDTO.getBizType();
        // 获取点赞总数
        Long totalLikes = stringRedisTemplate.opsForSet().size(userLikeRecordKey);
        if (totalLikes == null) {
            return;
        }
        // 存储在ZSet中，key为业务类型，这里是问答，value就是该业务下点赞数量总数
        stringRedisTemplate.opsForZSet().add(totalLikesOfBizType, likeRecordFormDTO.getBizId().toString(), totalLikes);
    }
    // 实现取消点赞逻辑
    private boolean unlike(Long userId, LikeRecordFormDTO likeRecordFormDTO) {
/*        LikedRecord record = this.lambdaQuery()
                .eq(LikedRecord::getUserId, userId)
                .eq(LikedRecord::getBizId, likeRecordFormDTO.getBizId())
                .one();
        if (record == null) {
            // 说明用户点过赞了
            return false;
        }
        return this.removeById(record.getId());*/
        String userLikeRecordKey = RedisConstants.LIKE_BIZ_KEY_PREFIX + likeRecordFormDTO.getBizId();
        Long result = stringRedisTemplate.opsForSet().remove(userLikeRecordKey, userId.toString());
        return result != null && result > 0;
    }

    // 实现点赞逻辑
    private boolean like(Long userId, LikeRecordFormDTO likeRecordFormDTO) {
/*        LikedRecord record = this.lambdaQuery()
                .eq(LikedRecord::getUserId, userId)
                .eq(LikedRecord::getBizId, likeRecordFormDTO.getBizId())
                .one();
        if (record != null) {
            // 说明用户点过赞了，返回false
            return false;
        }
        // 保存到数据库
        LikedRecord likeRecord = new LikedRecord();
        likeRecord.setBizId(likeRecordFormDTO.getBizId());
        likeRecord.setUserId(userId);
        likeRecord.setBizType(likeRecordFormDTO.getBizType());
        return this.save(likeRecord);*/
        // 利用redis的Set集合存储，key就是对应的业务id，value是用户集合
        String userLikeRecordKey = RedisConstants.LIKE_BIZ_KEY_PREFIX + likeRecordFormDTO.getBizId();
        Long result = stringRedisTemplate.opsForSet().add(userLikeRecordKey, userId.toString());
        return result != null && result > 0;
    }

    /**
     * 根据指定业务批量查询点赞状态
     * @param bizIds 业务ids
     * @return 返回批量点赞状态结果
     */
    @Override
    public Set<Long> getLikesStatusByBizIds(List<Long> bizIds) {
        // 通过redis管道来实现，如果是短时间内需要大量数据的批量操作
        // 1.获取登录用户id
        Long userId = UserContext.getUser();
        // 2.查询点赞状态
        List<Object> objects = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection src = (StringRedisConnection) connection;
            for (Long bizId : bizIds) {
                String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + bizId;
                src.sIsMember(key, userId.toString());
            }
            return null;
        });
        // 3.返回结果
        return IntStream.range(0, objects.size()) // 创建从0到集合size的流
                .filter(i -> (boolean) objects.get(i)) // 遍历每个元素，保留结果为true的角标i
                .mapToObj(bizIds::get)// 用角标i取bizIds中的对应数据，就是点赞过的id
                .collect(Collectors.toSet());// 收集
        // 3.返回
/*        // 1.获取当前用户id
        Long userId = UserContext.getUser();
        // 2.通过业务id组合的key去redis查询对应value，value就是对该业务id点过赞的用户id集合
        Set<Long> userIdsFormBizIds = new HashSet<>();
        if (CollUtils.isEmpty(bizIds)) {
            return CollUtils.emptySet();
        }
        for (Long bizId : bizIds) {
            String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + bizId;
            Boolean member = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
            if (Boolean.TRUE.equals(member)) {
                userIdsFormBizIds.add(bizId);
            }

        }
        return userIdsFormBizIds;*/
        // 3.判断当前用户id是否在这些业务ids中是否存在，将存在的返回
/*        if (CollUtils.isEmpty(bizIds)) {
            return CollUtils.emptySet();
        }
        // 1.获取当前登录用户id
        Long userId = UserContext.getUser();
        // 2.数据库查询点赞表
        List<LikedRecord> likedRecordList = this.lambdaQuery()
                .in(LikedRecord::getBizId, bizIds)
                .eq(LikedRecord::getUserId, userId)
                .list();
        // 3.封装
        Set<Long> likeBizIds = likedRecordList.stream().map(LikedRecord::getBizId).collect(Collectors.toSet());
        return likeBizIds;*/
    }

    /**
     * 定时任务内容 - 通过获取redis中对应业务类型中点赞总数信息赋值后MQ发送消息
     * @param bizType 不同业务类型 QA-问答 NOTE - 笔记
     * @param maxBizSize 最大从redis中取的数量
     */
    @Override
    public void readLikedTimesAndSendMes(String bizType, int maxBizSize) {
        // 1.拼接key，从redis 的ZSet中获取对应bizType的点赞总量信息
        String likesTotalKey = RedisConstants.LIKE_COUNT_KEY_PREFIX + bizType;
        // 最多从中取出指定score最小数量，然后这个方法最后会将redis数据删除，也就是取出并删除
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().popMin(likesTotalKey, maxBizSize);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return;
        }
        // 2.封装LikedTimesDTO
        List<LikedTimesDTO> likedTimesDTOS = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            Double likedTotal = typedTuple.getScore();// 对应业务id下的点赞总数
            String bizId = typedTuple.getValue();// 对应业务id
            if (StringUtils.isBlank(bizId) || likedTotal == null) {
                continue; // 说明该业务下没有点赞信息，继续遍历下一个业务id
            }
            LikedTimesDTO likedTimesDTO = LikedTimesDTO.of(Long.valueOf(bizId), likedTotal.intValue());
            likedTimesDTOS.add(likedTimesDTO);
        }
        String rountingKey = StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, bizType);
        // 3.封装后的结果作为消息MQ发送
        rabbitMqHelper.send(
                MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
                rountingKey,
                likedTimesDTOS
        );
    }
}
