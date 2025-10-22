package com.tianji.learning.controller;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;

import com.tianji.api.dto.leanring.LearningLessonDTO;



import com.tianji.learning.service.ILearningRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 学习记录表 前端控制器
 * </p>
 *
 * @author author
 * @since 2025-09-29
 */
@Api(tags = "学习记录相关接口")
@RestController
@RequestMapping("/learning-records")
@RequiredArgsConstructor
public class LearningRecordController {

    final ILearningRecordService learningRecordService;
    @ApiOperation("查询当前用户指定课程的学习进度")
    @GetMapping("/course/{courseId}")
    public LearningLessonDTO queryLearningRecordByCourseId(@PathVariable("courseId") Long courseId) {
        return learningRecordService.queryLearningRecordByCourseId(courseId);
    }

    @ApiOperation("提交学习记录")
    @PostMapping    // 注解Validated不需要我们堆前端传来的属性判空处理，直接在实体类中指明，通过注解就可以实现这个功能
    public void addLearningRecord(@RequestBody @Validated LearningRecordFormDTO learningRecordFormDTO ) {
        learningRecordService.addLearningRecord(learningRecordFormDTO);
    }
}
