package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;



/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author author
 * @since 2025-09-27
 */
@Service
@RequiredArgsConstructor
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson>
        implements ILearningLessonService {
    final CourseClient courseClient; // 课程
    final CatalogueClient catalogueClient; // 目录
    final LearningRecordMapper learningRecordMapper;
    // 添加课程信息，rabbitmq异步执行的消息部分
    @Override
    @Transactional
    public void addUserLesson(Long userId, List<Long> courseIds) {
        // 1.通过feign远程调用课程模块，得到课程信息
        List<CourseSimpleInfoDTO> courseSimpleInfoDTOList = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(courseSimpleInfoDTOList)) {
            log.error("课程信息不存在，无法添加到课表");
            return;
        }
        List<LearningLesson> learningLessonList = new ArrayList<>(courseSimpleInfoDTOList.size());
        // 2.封装po响应给前端
        for (CourseSimpleInfoDTO courseSimpleInfoDTO : courseSimpleInfoDTOList) {
            LearningLesson learningLesson = new LearningLesson();
            learningLesson.setUserId(userId);
            learningLesson.setCourseId(courseSimpleInfoDTO.getId());
            Integer validDuration = courseSimpleInfoDTO.getValidDuration(); // 有效期，单位是月
            if (validDuration != null && validDuration > 0) {
                LocalDateTime localDateTime = LocalDateTime.now();
                learningLesson.setCreateTime(localDateTime);
                learningLesson.setExpireTime(localDateTime.plusMonths(validDuration));
            }
            learningLessonList.add(learningLesson);
        }
        // 3.批量保存到数据库
        this.saveBatch(learningLessonList);

    }
    // 分页查询我的课表
    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery) {
        // 1.获取当前登录用户（从ThreadLocal中获取）
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new BadRequestException("必须登录！");
        }
        // 2.根据用户Id分页查询LearningLesson表
        Page<LearningLesson> lessonPage = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .page(pageQuery.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = lessonPage.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(lessonPage);
        }
        // 3.远程调用课程模块 得到课程的名字，图片等信息封装到po
        // 3.1获取课程Ids
        Set<Long> courseIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        List<CourseSimpleInfoDTO> courseInfo = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(courseInfo)) {
            throw new BizIllegalException("课程不存在");
        }
        // 3.2将课程信息放入map集合，key是课程id，value是课程类
        Map<Long, CourseSimpleInfoDTO> collect = courseInfo.stream()
                .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        // 4.封装po给前端
        List<LearningLessonVO> learningLessonVOList = new ArrayList<>();
        for (LearningLesson learningLesson : records) {
            // 实体类变换
            LearningLessonVO learningLessonVO = BeanUtils.copyBean(learningLesson, LearningLessonVO.class);
            CourseSimpleInfoDTO courseSimpleInfoDTO = collect.get(learningLesson.getCourseId());
            if (courseSimpleInfoDTO != null) {
                learningLessonVO.setCourseName(courseSimpleInfoDTO.getName());
                learningLessonVO.setCourseCoverUrl(courseSimpleInfoDTO.getCoverUrl());
                learningLessonVO.setSections(courseSimpleInfoDTO.getSectionNum());
            }
            learningLessonVOList.add(learningLessonVO);
        }
        return PageDTO.of(lessonPage, learningLessonVOList);
    }
    // 查询最近一次学习的课程（一条）
    @Override
    public LearningLessonVO queryMyCurrentLesson() {
        // 1.获取当前登录用户
        Long userId = UserContext.getUser();
        // 2.去根据用户id去数据库查询最近课程 status 1 -学习中， 然后按最近学习时间倒序，最后取第一条数据
        if (userId == null) {
            throw new BadRequestException("必须登录");
        }
        // select * from learning_lesson where userId = ? and status = ? order by latest_learn_time desc limit 1;
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING)
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1")
                .one();
        if (lesson == null) {
            return null;
        }
        // 3.远程调用课程模块，得到该课程封面，课程名称，总课时数用来封装Vo响应给前端
        // 后两个参数表示是否需要目录信息和教师信息
        CourseFullInfoDTO courseInfoById = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (courseInfoById == null) {
            throw new BizIllegalException("课程不存在");
        }
        // 4.数据库查询该课程的总课时数
        Integer count = this.lambdaQuery().eq(LearningLesson::getUserId, userId).count();
        // 5.远程调用课程模块其他业务方法，得到最近一次学习的小节及其名称
        Long latestSectionId = lesson.getLatestSectionId(); // 最近一次学习小节的id
        List<CataSimpleInfoDTO> SimpleCartInfoDOS = catalogueClient.batchQueryCatalogue(CollUtils.singletonList(latestSectionId));
        if (CollUtils.isEmpty(SimpleCartInfoDOS)) {
            throw new BizIllegalException("小节不存在");
        }
        // 6.封装VO响应前端
        LearningLessonVO learningLessonVO = BeanUtils.copyBean(lesson, LearningLessonVO.class);
        learningLessonVO.setCourseName(courseInfoById.getName());
        learningLessonVO.setCourseCoverUrl(courseInfoById.getCoverUrl());
        learningLessonVO.setSections(courseInfoById.getSectionNum());
        learningLessonVO.setCourseAmount(count); // 当前用户学的课程的总数
        CataSimpleInfoDTO cataSimpleInfoDTO = SimpleCartInfoDOS.get(0);
        learningLessonVO.setLatestSectionIndex(cataSimpleInfoDTO.getCIndex());// 最近一次小节进度
        learningLessonVO.setLatestSectionName(cataSimpleInfoDTO.getName()); // 最近一次小节名称
        return learningLessonVO;
    }
    // 校验课程是否有效
    @Override
    public Long checkCourseValid(Long courseId) {
        // 1.获取当前用户
        Long userId = UserContext.getUser();
        // 2.判断用户课程是否存在该课程
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getCourseId, courseId)
                .eq(LearningLesson::getUserId, userId)
                .one();
        if (lesson == null) {
            return null;
        }
        // 3.判断课程是否过期
        if (lesson.getExpireTime() != null && LocalDateTime.now().isAfter(lesson.getExpireTime())) {
            return null; // 课程过期
        }
        // 4.未过期，返回课表id
        return lesson.getId();
    }
    // 删除课程信息
    @Override
    public void deleteRefundCourseFromLesson(Long userId, Long courseId) {
        Wrapper<LearningLesson> wrapper = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId);
        remove(wrapper);
    }
    // 查询指定课程信息
    @Override
    public LearningLessonVO queryCourseFromLesson(Long courseId) {
        // 获取用户id
        Long userId = UserContext.getUser();
        // 通过用户id和课程id去数据库查询用户表
        if (userId == null) {
            return null;
        }
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        // 判断课程是否存在
        if (lesson == null) {
            return null;
        }

        // 存在，封装Vo响应
        return BeanUtils.copyBean(lesson, LearningLessonVO.class);
    }
    // 查询课程的学习人数（相当于是查询有多少条数据，不同用户对应这一个课程id）
    @Override
    public Integer countCourseFromLesson(Long courseId) {
        return this.lambdaQuery().eq(LearningLesson::getCourseId, courseId).count();
    }

    // 创建学习计划
    @Override
    public void createLearningPlan(LearningPlanDTO learningPlanDTO) {
        // 1.获取当前用户
        Long userId = UserContext.getUser();
        // 2.查询课表信息
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, learningPlanDTO.getCourseId())
                .one();
        if (lesson == null) {
            throw new BizIllegalException("课表不存在");
        }
        // 3.修改课表
        this.lambdaUpdate()
                .set(LearningLesson::getWeekFreq, learningPlanDTO.getFreq())
                .set(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .eq(LearningLesson::getId, lesson.getId())
                .update();
    }
    // 分页查询我的课程计划
    @Override
    public LearningPlanPageVO queryMyPlans(PageQuery pageQuery) {
        Long userId = UserContext.getUser();
        // 1.查询本周计划学习小节数总量
        // select Sum(week_freq) from learning_lesson where userId = ? and plan_status = 1 and status in (0,1);
        QueryWrapper<LearningLesson> wrapper = new QueryWrapper<>();
        wrapper.select("sum(week_freq) as plansTotal");
        wrapper.eq("user_id", userId);
        wrapper.in("status", LessonStatus.NOT_BEGIN, LessonStatus.LEARNING);
        wrapper.eq("plan_status", PlanStatus.PLAN_RUNNING);

        Map<String, Object> map = this.getMap(wrapper);
        Integer plansTotal = 0;
        // key 是上面总数取得别名，值就是总数值
        if (map!= null && map.get("plansTotal") != null) {
            // 若存在更新总计划树，需要将object类型转成Integer
            plansTotal = Integer.valueOf(map.get("plansTotal").toString());
        }
        // TODO 2.查询本周的学习积分
        // 3.查询本周 实际上 已经学习了小节总数
        // select count(*) from learning_record where userId = ? and finished = 1 and finish_time between '一周开始' and '一周结束';
        // 由于在LearningRecord实现类中已经用过该实现类得调用方法，如果这里注入LearningRecord就会出现循环注入，所以这里我们使用mapper来查询
        LocalDate now = LocalDate.now();
        LocalDateTime weekBeginTime = DateUtils.getWeekBeginTime(now);
        LocalDateTime weekEndTime = DateUtils.getWeekEndTime(now);
        Integer weekFinishedPlansNum = learningRecordMapper.selectCount(Wrappers.<LearningRecord>lambdaQuery()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getFinished, true)
                .between(LearningRecord::getFinishTime, weekBeginTime, weekEndTime));
        // 4.分页查询课程计划表，若不存在就直接返回空
        Page<LearningLesson> page = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .in(LearningLesson::getStatus, LessonStatus.NOT_BEGIN, LessonStatus.LEARNING)
                .page(pageQuery.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if (records == null) {
            LearningPlanPageVO pageVO = new LearningPlanPageVO();
            pageVO.setTotal(0L);
            pageVO.setPages(0L);
            pageVO.setList(CollUtils.emptyList());
            return pageVO;
        }
        // 5.远程调用课程服务得到课程信息（courseName及该课程总小节数）
        Set<Long> courseIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        List<CourseSimpleInfoDTO> courseClientSimpleInfoList = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(courseClientSimpleInfoList)) {
            throw new BizIllegalException("课程不存在");
        }
        Map<Long, CourseSimpleInfoDTO> courseInfo = courseClientSimpleInfoList.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        // 6.查询得到本周已经学习的小节数量
        // select lesson_id, count(*) from learning_record
        // where user_id = ? and finished = 1 and finish_time between '一周开始' and '一周结束' group by lesson_id;
        QueryWrapper<LearningRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("lesson_id as lessonId", "count(*) as userId");
        queryWrapper.eq("user_id", userId);
        queryWrapper.eq("finished", true);
        queryWrapper.between("finish_time", weekBeginTime, weekEndTime);
        queryWrapper.groupBy("lesson_id");
        List<LearningRecord> learningRecords = learningRecordMapper.selectList(queryWrapper);
        Map<Long, Long> longMap = learningRecords.stream().collect(Collectors.toMap(LearningRecord::getLessonId, LearningRecord::getUserId));
        // 7.封装VO返回
        LearningPlanPageVO learningPlanPageVO = new LearningPlanPageVO();
        learningPlanPageVO.setWeekTotalPlan(plansTotal);
        learningPlanPageVO.setWeekFinished(weekFinishedPlansNum);
        List<LearningPlanVO> planVOS = new ArrayList<>();
        for (LearningLesson record : records) {
            LearningPlanVO planVO = BeanUtils.copyBean(record, LearningPlanVO.class);
            CourseSimpleInfoDTO courseSimpleInfoDTO = courseInfo.get(record.getCourseId());
            if (courseSimpleInfoDTO != null) {
                planVO.setCourseName(courseSimpleInfoDTO.getName());
                planVO.setSections(courseSimpleInfoDTO.getSectionNum());
            }
           /* 方式一：Long aLong = longMap.get(record.getId());
            if (aLong != null) {
                planVO.setWeekLearnedSections(aLong.intValue());
            } else {
                planVO.setWeekLearnedSections(0);
            }*/
            // 方式二：
            planVO.setWeekLearnedSections(longMap.getOrDefault(record.getId(),0L).intValue());
            planVOS.add(planVO);

        }
/*       方式一 learningPlanPageVO.setList(planVOS);
        learningPlanPageVO.setPages(page.getPages());
        learningPlanPageVO.setTotal(page.getTotal());*/
        // 方式二返回
        return learningPlanPageVO.pageInfo(page.getTotal(), page.getPages(), planVOS);
    }
}

