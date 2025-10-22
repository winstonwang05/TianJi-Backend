
package com.tianji.remark.service.impl;
/*
import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.dto.LikedTimesDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

*/
/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author author
 * @since 2025-10-10
 *//*

@Slf4j
@Service
@RequiredArgsConstructor
public class LikedRecordServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper rabbitMqHelper;
    */
/**
     * 实现点赞或者取消点赞
     * @param likeRecordFormDTO 前端传来的参数，包括点赞的id，点赞的类型
     *//*

    @Override
    public void addLikeRecord(LikeRecordFormDTO likeRecordFormDTO) {
        // 1.获取当前登录用户id
        Long userId = UserContext.getUser();
*/
/*        // 2.如果是点赞逻辑
        boolean flag = false;
        if (likeRecordFormDTO.getLiked()) {
            flag = like(userId, likeRecordFormDTO);
        } else {
            // 3.如果是取消点赞逻辑
            flag = unlike(userId, likeRecordFormDTO);
        }*//*

        // 优化
        boolean flag = likeRecordFormDTO.getLiked() ? like(userId, likeRecordFormDTO) : unlike(userId, likeRecordFormDTO);
        if (!flag) {
            return;
        }
        // 4.查询点赞表中对应业务的点赞量
        Integer totalLikeNum = this.lambdaQuery().eq(LikedRecord::getBizId, likeRecordFormDTO.getBizId()).count();
*/
/*        LikedTimesDTO likedTimesDTO = new LikedTimesDTO();
        likedTimesDTO.setLikedTimes(totalLikeNum);
        likedTimesDTO.setBizId(likeRecordFormDTO.getBizId());*//*


*/
/*        // 方式二：
        LikedTimesDTO likedTimesDTO = LikedTimesDTO.builder().likedTimes(totalLikeNum).bizId(likeRecordFormDTO.getBizId()).build();*//*

        // 方式三：

        LikedTimesDTO likedTimesDTO = LikedTimesDTO.of(likeRecordFormDTO.getBizId(), totalLikeNum);
        // 5.通过mq发送点赞量到对应的业务（笔记或者回答）
        String constantKey = StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, likeRecordFormDTO.getBizId());
        log.debug("发送点赞消息 {}", likedTimesDTO);
        rabbitMqHelper.send(
                MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
                constantKey,
                likedTimesDTO
        );
    }
    // 实现取消点赞逻辑
    private boolean unlike(Long userId, LikeRecordFormDTO likeRecordFormDTO) {
        LikedRecord record = this.lambdaQuery()
                .eq(LikedRecord::getUserId, userId)
                .eq(LikedRecord::getBizId, likeRecordFormDTO.getBizId())
                .one();
        if (record == null) {
            // 说明用户点过赞了
            return false;
        }
        return this.removeById(record.getId());
    }

    // 实现点赞逻辑
    private boolean like(Long userId, LikeRecordFormDTO likeRecordFormDTO) {
        LikedRecord record = this.lambdaQuery()
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
        return this.save(likeRecord);
    }

    */
/**
     * 根据指定业务id批量查询点赞状态
     * @param bizIds 业务ids
     * @return 返回批量点赞状态结果
     *//*

    @Override
    public Set<Long> getLikesStatusByBizIds(List<Long> bizIds) {
        if (CollUtils.isEmpty(bizIds)) {
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
        return likeBizIds;
    }
}
*/
