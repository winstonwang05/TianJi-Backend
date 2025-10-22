package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 学生课程表 前端控制器
 * </p>
 *
 * @author author
 * @since 2025-09-27
 */
@Api(tags = "我的课程相关接口")
@RestController
@RequestMapping("/lessons")
@RequiredArgsConstructor
public class LearningLessonController {
    final ILearningLessonService iLearningLessonService;
    @ApiOperation("分页查询我的课表")
    @GetMapping("page")
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery) {
        return iLearningLessonService.queryMyLessons(pageQuery);
    }
    @ApiOperation("查询正在学习的课程")
    @GetMapping("now")
    public LearningLessonVO queryMyCurrentLesson() {
        return iLearningLessonService.queryMyCurrentLesson();
    }
    @ApiOperation("检查当前课程是否有效")
    @GetMapping("{courseId}/valid")
    public Long checkCourseValid(@PathVariable("courseId") Long courseId) {
        return iLearningLessonService.checkCourseValid(courseId);
    }
    @ApiOperation("用户手动删除当前课程")
    @DeleteMapping("{courseId}")
    public void deleteCourseFromLesson(@PathVariable("courseId") Long courseId) {
        Long userId = UserContext.getUser();
        iLearningLessonService.deleteRefundCourseFromLesson(userId, courseId);
    }
    @ApiOperation("用户查询课表中指定课程状态")
    @GetMapping("{courseId}")
    public LearningLessonVO queryCourseFromLesson(
            @ApiParam(value = "课程id", example = "1") @PathVariable("courseId") Long courseId) {
        return iLearningLessonService.queryCourseFromLesson(courseId);
    }
    @ApiOperation("统计课程的学习人数")
    @GetMapping("{courseId}/count")
    public Integer countCourseFromLesson(@PathVariable Long courseId) {
        return iLearningLessonService.countCourseFromLesson(courseId);
    }
    @ApiOperation("创建学习计划")
    @PostMapping("plans")
    public void createLearningPlan(@RequestBody @Validated LearningPlanDTO learningPlanDTO) {
         iLearningLessonService.createLearningPlan(learningPlanDTO);
    }
    @ApiOperation("分页查询我的课程计划")
    @GetMapping("plans")
    public LearningPlanPageVO queryMyPlans(PageQuery pageQuery) {
        return iLearningLessonService.queryMyPlans(pageQuery);
    }
}
