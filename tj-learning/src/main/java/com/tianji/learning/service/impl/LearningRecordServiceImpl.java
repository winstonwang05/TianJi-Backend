package com.tianji.learning.service.impl;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.utils.LearningRecordDelayTaskHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author author
 * @since 2025-09-29
 */
@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    final ILearningLessonService learningLessonService;
    final CourseClient courseClient;
    final LearningRecordDelayTaskHandler taskHandler;
    /** 课程所有小节记录信息
     * 根据课程id查询课程进度（学习记录）
     * @param courseId 课程id
     * @return 返回课程进度信息
     */
    @Override
    public LearningLessonDTO queryLearningRecordByCourseId(Long courseId) {
        // 1.获取当前用户id
        Long userId = UserContext.getUser();
        // 2.查询用户课表 条件 userId 和 courseId
        LearningLesson lesson = learningLessonService.lambdaQuery()
                .eq(LearningLesson::getCourseId, courseId)
                .eq(LearningLesson::getUserId, userId)
                .one();
        // 3.根据课表id查询学习记录LearningRecord
        List<LearningRecord> recordList = this.lambdaQuery()
                .eq(LearningRecord::getLessonId, lesson.getId())
                .eq(LearningRecord::getUserId, userId) //可写可不写，因为lesson中已经包含了userId，他是userId + courseId
                .list();
        // 4.封装返回
        LearningLessonDTO learningLessonDTO = new LearningLessonDTO();
        learningLessonDTO.setId(lesson.getId());
        learningLessonDTO.setLatestSectionId(lesson.getLatestSectionId());
        learningLessonDTO.setRecords(BeanUtils.copyList(recordList, LearningRecordDTO.class));
        return learningLessonDTO;
    }
    // 提交学习记录
    @Override
    public void addLearningRecord(LearningRecordFormDTO learningRecordFormDTO) {
        // 1.获取当前用户
        Long userId = UserContext.getUser();
        // 2.处理学习记录
        boolean isFinished = false;
        if (learningRecordFormDTO.getSectionType().equals(SectionType.EXAM)) {
            // 2.1 如果是考试学习记录
            isFinished = handleExamRecord(userId, learningRecordFormDTO);

        } else {
            // 2.2 如果是视频学习记录
            isFinished = handleVideoRecord(userId, learningRecordFormDTO);

        }
        // 如果不是第一次提交记录，结果就是false
        if (!isFinished) {
            return;
        }
        // 3.处理课表数据，这个isFinished永远就是true了
        handleLessonData(learningRecordFormDTO);

    }
    // 处理课表信息
    private void handleLessonData(LearningRecordFormDTO learningRecordFormDTO) {
        // 1.获取课表信息
        LearningLesson lesson = learningLessonService.getById(learningRecordFormDTO.getLessonId());
        if (lesson == null) {
            throw new BizIllegalException("课程不存在");
        }
        // 2.通过课表得到课程id远程调用得到课程信息，得到该课程的总课时数
        CourseFullInfoDTO courseInfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (courseInfo == null) {
            throw new BizIllegalException("课程不存在");
        }
        // 3.如果是第一次小节完成，也就是isFinished为true
        boolean allFinished = false;

            // 4.需要该课程的总课时数比较
            Integer sectionNum = courseInfo.getSectionNum();
            allFinished = lesson.getLearnedSections() + 1 >= sectionNum;

        // 5.更新数据库信息
        learningLessonService.lambdaUpdate()
                .set(lesson.getLearnedSections() == 0, LearningLesson::getStatus, LessonStatus.LEARNING.getValue())
                .set(LearningLesson::getLatestSectionId, learningRecordFormDTO.getSectionId())
                .set(LearningLesson::getLatestLearnTime, learningRecordFormDTO.getCommitTime())
                .set(allFinished, LearningLesson::getStatus, LessonStatus.FINISHED)
                .setSql("learned_sections = learned_sections + 1")
                .eq(LearningLesson::getId, lesson.getId())
                .update();
    }

    // 处理视频提交信息
    private boolean handleVideoRecord(Long userId, LearningRecordFormDTO learningRecordFormDTO) {
/*        // 获取小节信息 select * from
        LearningRecord record = this.lambdaQuery()
                .eq(LearningRecord::getLessonId, learningRecordFormDTO.getLessonId())
                .eq(LearningRecord::getSectionId, learningRecordFormDTO.getSectionId())
                .one();*/
        // 1.先查询缓存
        LearningRecord record = queryOldRecord(learningRecordFormDTO.getLessonId(), learningRecordFormDTO.getSectionId());
        // 2.判断记录是否存在
        // 2.1不存在记录
        if (record == null) {
            LearningRecord learningRecord = BeanUtils.copyBean(learningRecordFormDTO, LearningRecord.class);
            learningRecord.setUserId(userId);
            boolean result = this.save(learningRecord);
            if (!result) {
                throw new DbException("新增学习记录失败");
            }
            return false;
        }
        // 存在记录 isFinished为true表示第一次保存记录 ，当isFinished表示false说明可能是第二次了
        boolean isFinished = !record.getFinished() && learningRecordFormDTO.getMoment() * 2 >= learningRecordFormDTO.getDuration();
        // 如果并不是第一次完成
        if (!isFinished) {
            LearningRecord learningRecord = new LearningRecord();
            learningRecord.setLessonId(learningRecordFormDTO.getLessonId());
            learningRecord.setSectionId(learningRecordFormDTO.getSectionId());
            learningRecord.setFinished(record.getFinished());
            learningRecord.setMoment(learningRecordFormDTO.getMoment());
            learningRecord.setId(record.getId());
            taskHandler.addLearningRecordTask(learningRecord);
        }
        // 更新记录 update learning_lesson set finished = ?, finish_time = ?, moment = ? where id = ?
        boolean result = this.lambdaUpdate()
                .set(LearningRecord::getMoment, learningRecordFormDTO.getMoment())
                .set(isFinished, LearningRecord::getFinished, true)
                .set(isFinished, LearningRecord::getFinishTime, learningRecordFormDTO.getCommitTime())
                .eq(LearningRecord::getId, record.getId())
                .update();
        if (!result) {
            throw new DbException("更新视频记录失败");
        }
        // 删除缓存
        taskHandler.cleanRecordCache(learningRecordFormDTO.getLessonId(), learningRecordFormDTO.getSectionId());
        return isFinished; // true
    }

    private LearningRecord queryOldRecord(@NotNull(message = "课表id不能为空") Long lessonId, @NotNull(message = "节的id不能为空") Long sectionId) {
        // 1.查询缓存
        LearningRecord recordCache = taskHandler.readRecordCache(lessonId, sectionId);
        // 2.命中缓存
        if (recordCache != null) {
            return recordCache;
        }
        // 3.未命中，查询数据库，写入redis
        LearningRecord record = this.lambdaQuery()
                .eq(LearningRecord::getLessonId, lessonId)
                .eq(LearningRecord::getSectionId, sectionId)
                .one();
        if (record == null) {
            return null;
        }
        taskHandler.writeRecordCache(record);
        // 4.返回缓存信息
        return record;
    }

    // 提交考试记录
    private boolean handleExamRecord(Long userId, LearningRecordFormDTO learningRecordFormDTO) {
        // 将DTO转化为PO
        LearningRecord learningRecord = BeanUtils.copyBean(learningRecordFormDTO, LearningRecord.class);
        // 设置PO属性保存到数据库中，也就是提交考试记录信息
        learningRecord.setUserId(userId);
        learningRecord.setFinished(true); // 默认是false表示该小节未完成，考试就设置为true
        learningRecord.setFinishTime(learningRecordFormDTO.getCommitTime()); // 完成时间
        boolean isFinished = this.save(learningRecord);
        if (!isFinished) {
            throw new DbException("保存考试记录失败");
        }
        return true;
    }
}
