package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsRecord;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mq.msg.SignInMessage;

import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务类
 * </p>
 *
 * @author author
 * @since 2025-10-12
 */
public interface IPointsRecordService extends IService<PointsRecord> {

    void addPointsRecord(SignInMessage message, PointsRecordType pointsRecordType);

    List<PointsStatisticsVO> getPointsSituationToday();
}
