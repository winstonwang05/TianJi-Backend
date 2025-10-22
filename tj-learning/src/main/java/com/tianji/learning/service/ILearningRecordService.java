package com.tianji.learning.service;

import com.tianji.api.dto.leanring.LearningLessonDTO;

import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningRecord;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 学习记录表 服务类
 * </p>
 *
 * @author author
 * @since 2025-09-29
 */
public interface ILearningRecordService extends IService<LearningRecord> {

    LearningLessonDTO queryLearningRecordByCourseId(Long courseId);

    void addLearningRecord(LearningRecordFormDTO learningRecordFormDTO);
}
