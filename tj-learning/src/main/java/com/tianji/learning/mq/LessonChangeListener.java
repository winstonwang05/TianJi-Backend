package com.tianji.learning.mq;

import com.alibaba.nacos.client.naming.utils.CollectionUtils;
import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
@Slf4j
@Component
@RequiredArgsConstructor
public class LessonChangeListener {
    final ILearningLessonService lessonService;
    /**
     *  添加课程信息交换机队列绑定
     * @param dto 订单
     */
    // 监听者信息
    // 设置队列名并与交换机绑定
    @RabbitListener(bindings = @QueueBinding(value = @Queue(value = "learning.lesson.pay.queue", durable = "true"),
        exchange = @Exchange(value = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
        key = MqConstants.Key.ORDER_PAY_KEY))
    public void onMessage(OrderBasicDTO dto) {
        log.debug("LessonChangeListener 接受到了消息 用户{}，添加课程{} ", dto.getUserId(), dto.getCourseIds());
        // 校验
        if (dto.getUserId() == null || dto.getOrderId() == null || CollectionUtils.isEmpty(dto.getCourseIds())) {
            log.error("接收到MQ消息有误，订单数据为空");
            return; // 不能抛出异常，这样会导致mq会不断重试循环一直失败
        }
        // 消费消息，通过业务层批量保存到数据库
        lessonService.addUserLesson(dto.getUserId(), dto.getCourseIds());// userId和courseId是联合索引

    }

    /**
     * 删除课程的监听类
     * @param dto 订单
     */
    @RabbitListener(bindings = @QueueBinding(value = @Queue(value = "trade.refund.result.queue", durable = "true"),
        exchange = @Exchange(value = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
    key = MqConstants.Key.ORDER_REFUND_KEY))
    public void listenCourseRefund(OrderBasicDTO dto) {
        log.info("LessonChangeListener 接受到了消息 用户{}，删除课程{} ", dto.getUserId(), dto.getCourseIds());
        // 1.健壮性判断
        if (dto.getUserId() == null || dto.getOrderId() == null || CollectionUtils.isEmpty(dto.getCourseIds())) {
            log.error("接收到MQ消息有误，订单数据为空");
            return;
        }
        // 2.调用业务删除课程
        lessonService.deleteRefundCourseFromLesson(dto.getUserId(), dto.getCourseIds().get(0));
    }

}
