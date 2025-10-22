package com.tianji.learning.service.impl;

import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.mq.msg.SignInMessage;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignRecordService implements ISignRecordService {
    final StringRedisTemplate redisTemplate;
    final RabbitMqHelper rabbitMqHelper;

    /**
     * 实现签到功能
     * @return 返回连续签到天数和总积分
     */
    @Override
    public SignResultVO addSignRecords() {
        // 1.获取当前登录用户id
        Long userId = UserContext.getUser();
        // 2.拼接key，并设置redis中当天在BitMap中映射位置
        LocalDate now = LocalDate.now();
        String format = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String signRecordKey = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId.toString() + format;
        int offset = now.getDayOfMonth() - 1;
        Boolean setBit = redisTemplate.opsForValue().setBit(signRecordKey, offset, true);
        if (Boolean.TRUE.equals(setBit)) {
            throw new BizIllegalException("不能重复签到");
        }
        // 3.统计连续签到次数，用来实现积分增加
        int count = countContinuingSignDays(signRecordKey, now.getDayOfMonth());
        int rewardPoints = 0; // 积分
        switch (count) {
            case 7:
                rewardPoints = 10;
                break;
            case 14:
                rewardPoints = 20;
                break;
            case 28:
                rewardPoints = 40;
                break;
        }
        // 4.保存到积分表
        rabbitMqHelper.send(MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.SIGN_IN,
                SignInMessage.of(userId, rewardPoints + 1));
        // 5.封装VO返回
        SignResultVO signResultVO = new SignResultVO();
        signResultVO.setSignDays(count);
        signResultVO.setRewardPoints(rewardPoints);

        return signResultVO;
    }

    /**
     * 统计连续签到的天数
     * @param signRecordKey key
     * @param dayOfMonth 截至到当前天
     * @return 返回统计连续签到次数
     */
    private int countContinuingSignDays(String signRecordKey, int dayOfMonth) {
        // 从BitMap中获取截止到当前时间的连续签到次数
        // 截至到今天的签到表，偏移量表示从第一天开始统计到今天
        List<Long> bitField = redisTemplate.opsForValue().bitField(signRecordKey,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if (CollUtils.isEmpty(bitField)) {
            return 0;
        }
        // 得到的是集合，但是实际上就一个值，也就是二进制转为的十进制,总的签到记录
        Long signRecord = bitField.get(0);
        log.debug("countContinuingSignDays: {}", signRecord);
        int count = 0;
        // 让签到与 1 做 与运算 并右移 一位
        while (((signRecord) & 1) == 1) {
            count++;
            signRecord = signRecord>>>1;
        }
        return count;
    }

    /**
     * 查询签到记录这个月内
     * @return 返回这个月截止到今天的签到记录
     */
    @Override
    public Byte[] querySignRecords() {
        // 1.获取当前登录用户id
        Long userId = UserContext.getUser();
        // 2.拼接key 查询redis获取签到记录 - 截止到今天的所有记录
        LocalDate now = LocalDate.now();
        String format = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String signRecordKey = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId.toString() + format;
        LocalDateTime localDateTime = LocalDateTime.now();
        int dayOfMonth = localDateTime.getDayOfMonth();
        List<Long> bitField = redisTemplate.opsForValue().bitField(signRecordKey,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if (CollUtils.isEmpty(bitField)) {
            return new Byte[0];
        }
        Long signRecord = bitField.get(0);
        log.debug("querySignRecords: {}", signRecord);
        // 3.封装集合返回前端
        Byte[] bytes = new Byte[dayOfMonth];
        int offset = now.getDayOfMonth() - 1;
        while (offset >= 0) {
            bytes[offset] = (byte) (signRecord & 1);
            offset--;
            signRecord = signRecord>>>1;
        }
        return bytes;
    }
}
