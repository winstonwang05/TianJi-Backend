package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.remark.RemarkClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.tianji.learning.service.IInteractionReplyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.tianji.common.constants.Constant.DATA_FIELD_NAME_CREATE_TIME;
import static com.tianji.common.constants.Constant.DATA_FIELD_NAME_LIKED_TIME;

/**
 * <p>
 * 互动问题的回答或评论 服务实现类
 * </p>
 *
 * @author author
 * @since 2025-10-07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply>
        implements IInteractionReplyService {
    final InteractionQuestionMapper interactionQuestionMapper;
    final RemarkClient remarkClient;
    final RabbitMqHelper rabbitMqHelper;
/*
    final IInteractionQuestionService interactionQuestionService;
*/
    final UserClient userClient;
    /**
     * 新增评论或者回答
     * @param replyDTO 前端传来的回答或者评论相关
     */
    @Override
    public void saveReplies(ReplyDTO replyDTO) {
        // 1.获取当前登录用户id
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new BadRequestException("用户未登录");
        }
        
        // 2.新增回答或者评论数据到回答表
        InteractionReply interactionReply = BeanUtils.copyBean(replyDTO, InteractionReply.class);
        interactionReply.setUserId(userId);
        
        // 设置默认值
        if (interactionReply.getReplyTimes() == null) {
            interactionReply.setReplyTimes(0);
        }
        if (interactionReply.getLikedTimes() == null) {
            interactionReply.setLikedTimes(0);
        }
        if (interactionReply.getHidden() == null) {
            interactionReply.setHidden(false);
        }
        if (interactionReply.getAnonymity() == null) {
            interactionReply.setAnonymity(false);
        }
        
        boolean saveResult = this.save(interactionReply);
        if (!saveResult) {
            throw new BadRequestException("保存回复失败");
        }
        
        // 3.查询问题
        InteractionQuestion interactionQuestion = interactionQuestionMapper.selectById(replyDTO.getQuestionId());
        if (interactionQuestion == null) {
            throw new BadRequestException("问题不存在");
        }
        
        // 4.判断是否是回答，如果是回答，需要更新问题表最新回答id，回答数量+1
        if (replyDTO.getAnswerId() == null) {
            // 这是回答
            interactionQuestion.setLatestAnswerId(interactionReply.getId());
            interactionQuestion.setAnswerTimes(interactionQuestion.getAnswerTimes() + 1);
        } else {
            // 5.如果是评论，需要更新回答表评论数+1
            InteractionReply reply = this.getById(replyDTO.getAnswerId());
            if (reply == null) {
                throw new BadRequestException("回复的回答不存在");
            }
            reply.setReplyTimes(reply.getReplyTimes() + 1);
            this.updateById(reply);
        }
        
        // 6.如果是学生，需要更新问题表的状态为未查看，方便管理端查询
        if (replyDTO.getIsStudent() != null && replyDTO.getIsStudent()) {
            interactionQuestion.setStatus(QuestionStatus.UN_CHECK);
            // 发送每次回答获取的积分数到MQ
            rabbitMqHelper.send(
                    MqConstants.Exchange.LEARNING_EXCHANGE,
                    MqConstants.Key.WRITE_REPLY,
                    5
            );
        }
        
        boolean updateResult = interactionQuestionMapper.updateById(interactionQuestion) > 0;
        if (!updateResult) {
            throw new BadRequestException("更新问题失败");
        }
    }

    /**
     * 用户端分页查询回答或者评论
     * @param pageQuery 回答id或者评论id
     *
     * @return 返回VO
     */
    @Override
    public PageDTO<ReplyVO> selectRepliesPage(ReplyPageQuery pageQuery) {
        // 1.查询回答表 条件：回答id；问题id（如果没有就传0）；查询的字段是不隐藏的；分页先按照点赞数然后才是时间
        Long answerId = pageQuery.getAnswerId(); // 回答id
        Long questionId = pageQuery.getQuestionId(); // 问题id
        if (answerId == null && questionId == null) {
            throw new BadRequestException("问题id或者回答id不存在");
        }
        
        // 2.通过分页结果封装userIds（回答的用户和评论的用户），评论ids
        Page<InteractionReply> page = this.lambdaQuery()
                // 修复查询逻辑：当查询回答时，answerId应该为0；当查询评论时，answerId应该为具体值
                .eq(answerId != null, InteractionReply::getAnswerId, answerId)
                .eq(answerId == null, InteractionReply::getAnswerId, 0L) // 查询回答
                .eq(questionId != null, InteractionReply::getQuestionId, questionId)
                .eq(InteractionReply::getHidden, false)
                .page(pageQuery.toMpPage(
                        new OrderItem(DATA_FIELD_NAME_LIKED_TIME, false),
                        new OrderItem(DATA_FIELD_NAME_CREATE_TIME, true)
                ));
        
        List<InteractionReply> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(0L, 0L);
        }
        
        Set<Long> userIds = new HashSet<>();
        Set<Long> replyIds = new HashSet<>();
        for (InteractionReply reply : records) {
            if (!reply.getAnonymity()) {
                userIds.add(reply.getUserId());
                if (reply.getTargetUserId() != null) {
                    userIds.add(reply.getTargetUserId());
                }
            }
            if (reply.getTargetReplyId() != null && reply.getTargetReplyId() > 0) {
                replyIds.add(reply.getTargetReplyId());
            }
        }
        
        // 3.通过评论ids调用回答表得到评论的用户ids
        List<InteractionReply> interactionReplies = CollUtils.isEmpty(replyIds) 
            ? Collections.emptyList() 
            : listByIds(replyIds);
        
        if (!replyIds.isEmpty()) {
            Set<Long> targetUserIds = interactionReplies.stream()
                    .filter(Predicate.not(InteractionReply::getAnonymity))
                    .map(InteractionReply::getUserId)
                    .collect(Collectors.toSet());
            userIds.addAll(targetUserIds);
        }
        // 查询问题下的批量用户的点赞状态 远程调用点赞服务查询在这些评论下用户点赞情况
        Set<Long> likesStatusByBizIds = remarkClient.getLikesStatusByBizIds(new ArrayList<>(replyIds));
        // 4.远程调用用户服务
        List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
        Map<Long, UserDTO> userDTOMap = new HashMap<>();
        if (userDTOS != null) {
            userDTOMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, c -> c));
        }
        
        // 5.封装VO返回
        List<ReplyVO> replies = new ArrayList<>();
        for (InteractionReply reply : records) {
            ReplyVO replyVO = BeanUtils.copyBean(reply, ReplyVO.class);
            if (!reply.getAnonymity()) {
                UserDTO userDTO = userDTOMap.get(reply.getUserId());
                if (userDTO != null) {
                    replyVO.setUserName(userDTO.getName());
                    replyVO.setUserIcon(userDTO.getIcon());
                    replyVO.setUserType(userDTO.getType());
                }
            }
            
            if (reply.getTargetUserId() != null) {
                UserDTO dto = userDTOMap.get(reply.getTargetUserId());
                if (dto != null) {
                    replyVO.setTargetUserName(dto.getName());
                }
            }
            // 设置当前用户点赞过的评论
            replyVO.setLiked(likesStatusByBizIds.contains(reply.getId()));
            replies.add(replyVO);
        }
        return PageDTO.of(page, replies);
    }
}