package com.tianji.learning.mq;

import com.tianji.api.dto.msg.LikedTimesDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.service.IInteractionQuestionService;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;


@Slf4j
@Component
@RequiredArgsConstructor
// 监听者，接收消息，消息的内容就是需要更新回答表下的点赞数量
public class LikedRecordListener {

    private final IInteractionReplyService replyService;
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "qa.liked.times.queue", durable = "true"),
            exchange = @Exchange(value = MqConstants.Exchange.LIKE_RECORD_EXCHANGE, type = ExchangeTypes.TOPIC),
             key = MqConstants.Key.QA_LIKED_TIMES_KEY))
    /**
     * 问答系统 更新点赞数量
     */
    public void onMsg(List<LikedTimesDTO> likedTimesDTOS) {
        log.debug("消费者LikedRecordListener 收到了消息 {}", likedTimesDTOS);
        List<InteractionReply> replyList = new ArrayList<>();
        for (LikedTimesDTO likedTimesDTO : likedTimesDTOS) {
            InteractionReply reply = new InteractionReply();
            reply.setId(likedTimesDTO.getBizId());
            reply.setLikedTimes(likedTimesDTO.getLikedTimes());
            replyList.add(reply);
        }
        // 批量更新
        if (CollUtils.isNotEmpty(replyList)) {
            replyService.updateBatchById(replyList);
        }
    }
/*    public void onMsg(LikedTimesDTO likedTimesDTO) {
        log.debug("消费者LikedRecordListener 收到了消息 {}", likedTimesDTO);
        InteractionReply reply = replyService.getById(likedTimesDTO.getBizId());
        if (reply == null) {
            return; // 不断重试，不能抛异常
        }
        reply.setLikedTimes(likedTimesDTO.getLikedTimes());
        replyService.updateById(reply);
    }*/
}
