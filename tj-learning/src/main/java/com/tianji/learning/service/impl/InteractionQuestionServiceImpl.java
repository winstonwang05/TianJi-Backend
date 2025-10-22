package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CategoryClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author author
 * @since 2025-10-07
 */
@Service
@RequiredArgsConstructor
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion>
        implements IInteractionQuestionService {

    final IInteractionReplyService replyService;
    final UserClient userClient;
    final SearchClient searchClient;
    final CourseClient courseClient; // 课程服务
    final CatalogueClient catalogueClient; // 目录id 小节，章节
    final CategoryCache categoryCache;
    /**
     * 新增互动问题接口
     * @param questionFormDTO 前端传来的问题信息
     */
    @Override
    public void saveQuestion(QuestionFormDTO questionFormDTO) {
        // 1.获取当前用户信息
        Long userId = UserContext.getUser();
        // 2.封装po保存到数据库
        InteractionQuestion interactionQuestion = BeanUtils.copyBean(questionFormDTO, InteractionQuestion.class);
        interactionQuestion.setUserId(userId);
        this.save(interactionQuestion);
    }

    /**
     * 更新互动问题
     * @param questionFormDTO 前端传来的修改后属性
     * @param id 该问题id
     */
    @Override
    public void updateQuestion(QuestionFormDTO questionFormDTO, Long id) {
        // 1.健壮性判断
        if (StringUtils.isBlank(questionFormDTO.getTitle()) ||
                StringUtils.isBlank(questionFormDTO.getDescription()) ||
                questionFormDTO.getAnonymity() == null) {
            throw new BadRequestException("非法参数");
        }
        // 2.根据id查询原来的问题信息
        InteractionQuestion question = this.getById(id);
        if (question == null) {
            throw new BadRequestException("非法参数");
        }
        // 获取当前登录用户
        Long userId = UserContext.getUser();
        if (!question.getUserId().equals(userId)) {
            throw new BadRequestException("不能修改别人的互动问题");
        }
        // 3.封装修改后的属性
        question.setTitle(questionFormDTO.getTitle());
        question.setDescription(questionFormDTO.getDescription());
        question.setAnonymity(questionFormDTO.getAnonymity());
        // 4.更新
        this.updateById(question);
    }

    /**
     * 用户端分页查询互动问题
     * @param query 前端传来的分页信息（比如条件查看所以，还是只查询自己的）
     * @return 返回VO
     */
    @Override
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query) {
        // 1.校验课程id
        if (query.getCourseId() == null) {
            throw new BadRequestException("课程id不能为空");
        }
        // 2.获取当前登录用户id
        Long userId = UserContext.getUser();
        // 3.分页查询interaction_question 问题表
        Page<InteractionQuestion> page = this.lambdaQuery()
                // 由于我们查询问题表时候，在前端页面仅显示问题的标题，不会显示问题的详情（显示的话就会显得繁琐）,所以我们需要去掉这个详情字段查询（没必要的字段查询屏蔽）
                // 方式一: .select(InteractionQuestion::getId, InteractionQuestion::getCourseId, InteractionQuestion::getTitle)
                /*           方式二：
                          .select(InteractionQuestion.class, new Predicate<TableFieldInfo>() {
                              @Override
                              public boolean test(TableFieldInfo tableFieldInfo) {
                                  return !tableFieldInfo.getProperty().equals("description"); //指定不查询的字段
                              }
                          })*/
                // 简写
                .select(InteractionQuestion.class, tableFieldInfo -> !tableFieldInfo.getProperty().equals("description"))
                .eq(query.getOnlyMine(), InteractionQuestion::getUserId, userId) // 查询当前用户的问题
                .eq(InteractionQuestion::getCourseId, query.getCourseId()) //根据课程id查询
                .eq(query.getSectionId() != null, InteractionQuestion::getSectionId, query.getSectionId())// 只有小节不为null，才查询
                .eq(InteractionQuestion::getHidden, false) // 只有为不隐藏查询得到
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 这里可以通过stream流放入集合中中，但是由于需要遍历两个字段值，需要分别stream流，所以我们用循环一次；
        Set<Long> latestAnswerIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (InteractionQuestion record : records) {
            if (!record.getAnonymity()) { // 如果用户是匿名提交，不会显示用户名和头像
                userIds.add(record.getUserId());
            }
            Long latestAnswerId = record.getLatestAnswerId();
            if (latestAnswerId != null) {
                // 最新回答的用户可能不存在
                latestAnswerIds.add(latestAnswerId);
            }
        }
        // 4.根据问题表最新用户id查询回答表，得到回答标题内容
        Map<Long, InteractionReply> replyMap = new HashMap<>();
        if (CollUtils.isNotEmpty(latestAnswerIds)) {
            /*List<InteractionReply> interactionReplies = replyService.listByIds(latestAnswerIds);*/
            List<InteractionReply> interactionReplies = replyService.list(Wrappers.<InteractionReply>lambdaQuery()
                    .in(InteractionReply::getId, latestAnswerIds)
                    .eq(InteractionReply::getHidden, false));
            for (InteractionReply reply : interactionReplies) {
                    if (!reply.getAnonymity()) {
                        userIds.add(reply.getUserId());
                    }
                    replyMap.put(reply.getId(), reply);
                }
        }
        // 5.远程调用用户服务，得到用户头像，名字等属性
        List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
        Map<Long, UserDTO> userDTOMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, c -> c));
        // 6.封装vo返回
        List<QuestionVO> questionVOS = new ArrayList<>();
        for (InteractionQuestion record : records) {
            QuestionVO questionVO = BeanUtils.copyBean(record, QuestionVO.class);
            if (!questionVO.getAnonymity()) {
                UserDTO userDTO = userDTOMap.get(record.getUserId());
                if (userDTO != null) {
                    questionVO.setUserName(userDTO.getName());
                    questionVO.setUserIcon(userDTO.getIcon());
                }
            }
            InteractionReply interactionReply = replyMap.get(record.getLatestAnswerId());
            if (interactionReply != null) {
                UserDTO userDTO = userDTOMap.get(interactionReply.getUserId());
                if (userDTO != null) {
                    questionVO.setLatestReplyUser(userDTO.getName());
                }
                questionVO.setLatestReplyContent(interactionReply.getContent());

            }

            questionVOS.add(questionVO);
        }
        return PageDTO.of(page, questionVOS);
    }

    /**
     * 用户查询单个问题详情
     * @param id 问题id
     * @return 返回VO
     */
    @Override
    public QuestionVO queryQuestionById(Long id) {
        // 1.校验
        if (id == null) {
            throw new BadRequestException("问题不存在");
        }
        // 2.根据问题id查询问题表
        InteractionQuestion question = this.getById(id);
        if (question == null) {
            throw new BadRequestException("问题不存在");
        }
        // 3.管理员隐藏的问题，返回空
        if (question.getHidden()) {
            return null;
        }
        QuestionVO questionVO = BeanUtils.copyBean(question, QuestionVO.class);
        // 4.用户是否匿名，如果不匿名，远程调用用户服务得到用户信息
        if (!question.getAnonymity()) {
            UserDTO userDTO = userClient.queryUserById(question.getUserId());
            if (userDTO != null) {
                questionVO.setUserName(userDTO.getName());
                questionVO.setUserIcon(userDTO.getIcon());
            }
        }
        // 5.封装返回
        return questionVO;
    }

    /**
     * 用户删除问题
     * @param id 问题id
     */
    @Override
    public void deleteQuestion(Long id) {
        // 1.获取当前登录用户
        Long userId = UserContext.getUser();
        // 2.根据问题id查询问题表
        InteractionQuestion question = this.getById(id);
        if (question == null) {
            throw new BadRequestException("该问题不存在");
        }
        // 3.校验是否是当前用户（不能删除别人的问题）
        if (!question.getUserId().equals(userId)) {
            throw new BadRequestException("无权限");
        }
        // 4.如果是删除问题
        this.removeById(id);
        // TODO 5.删除该问题下的回答和评论
    }

    /**
     * 管理端隐藏或者显示问题
     * @param id 问题id
     * @param hidden 是否隐藏
     */
    @Override
    public void updateHidden(Long id, boolean hidden) {
        // 1.根据问题id查询问题表
        InteractionQuestion question = this.getById(id);
        if (question == null) {
            throw new BadRequestException("问题不存在");
        }
        // 2.设置字段
        question.setHidden(hidden);
        // 3.更新数据库
        boolean result = updateById(question);
        if (!result) {
            throw new BadRequestException("修改失败");
        }
    }

    /**
     * 管理端分页查询互动问题
     * @param pageQuery 前端传来的条件信息
     * @return 返回分页后的VO
     */
    @Override
    public PageDTO<QuestionAdminVO> queryQuestionAdminVOPage(QuestionAdminPageQuery pageQuery) {
        // 管理端不需要判空处理，管理端都能查看; 前端传来的参数与查询条件有关
        // 1.远程调用ES，通过前端传来的搜索课程名得到课程id
        String courseName = pageQuery.getCourseName();
        List<Long> coursesIdByName = null;
        if (StringUtils.isNotBlank(courseName)) {
          coursesIdByName = searchClient.queryCoursesIdByName(courseName);
            if (CollUtils.isEmpty(coursesIdByName)) {
                return PageDTO.empty(0L, 0L);
            }
        }
        // 2.查询问题表，条件：搜索服务得到的课程ids；管理端状态不为null；起始时间和终止不为null；分页默认按时间倒序
        Page<InteractionQuestion> page = this.lambdaQuery()
                .in(CollUtils.isNotEmpty(coursesIdByName), InteractionQuestion::getCourseId, coursesIdByName)
                .eq(pageQuery.getStatus() != null, InteractionQuestion::getStatus, pageQuery.getStatus())
                .between(pageQuery.getBeginTime() != null && pageQuery.getEndTime() != null,
                        InteractionQuestion::getCreateTime, pageQuery.getBeginTime(), pageQuery.getEndTime())
                .page(pageQuery.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(0L, 0L);
        }
        Set<Long> userIds = new HashSet<>();
        Set<Long> courseIds = new HashSet<>();
        Set<Long> chapterIdAndSectionId = new HashSet<>();
        for (InteractionQuestion record : records) {
            userIds.add(record.getUserId());
            courseIds.add(record.getCourseId());
            chapterIdAndSectionId.add(record.getChapterId());
            chapterIdAndSectionId.add(record.getSectionId());
        }
        // 3.远程调用用户服务得到用户名
        List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
        if (CollUtils.isEmpty(userDTOS)) {
            throw new BadRequestException("用户不存在");
        }
        Map<Long, UserDTO> userDTOMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, c -> c));
        // 4.远程调用课程服务得到 课程 和 章节 信息
        List<CourseSimpleInfoDTO> courseClientSimpleInfoList = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(courseClientSimpleInfoList)) {
            throw new BadRequestException("课程不存在");
        }
        Map<Long, CourseSimpleInfoDTO> courseSimpleInfoDTOMap = courseClientSimpleInfoList.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS = catalogueClient.batchQueryCatalogue(chapterIdAndSectionId);
        if (CollUtils.isEmpty(cataSimpleInfoDTOS)) {
            throw new BadRequestException("该章节或者小节不存在");
        }
        Map<Long, String> chapterAndSectionMap = cataSimpleInfoDTOS.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
        // 5.三级分类处理
        // 6.封装VO返回
        List<QuestionAdminVO> questionAdminVOS = new ArrayList<>();
        for (InteractionQuestion record : records) {
            QuestionAdminVO questionAdminVO = BeanUtils.copyBean(record, QuestionAdminVO.class);
            UserDTO userDTO = userDTOMap.get(record.getUserId());
            if (userDTO != null) {
                questionAdminVO.setUserName(userDTO.getName());
            }
            CourseSimpleInfoDTO courseSimpleInfoDTO = courseSimpleInfoDTOMap.get(record.getCourseId());
            if (courseSimpleInfoDTO != null) {
                questionAdminVO.setCourseName(courseSimpleInfoDTO.getName());
                List<Long> categoryIds = courseSimpleInfoDTO.getCategoryIds();
                questionAdminVO.setCategoryName(categoryCache.getCategoryNames(categoryIds)); // 三级标题名称
            }
           /* questionAdminVO.setChapterName(chapterAndSectionMap.get(record.getChapterId()) == null ? "" : chapterAndSectionMap.get(record.getChapterId())); // 章节名称
            questionAdminVO.setSectionName(chapterAndSectionMap.get(record.getSectionId()) == null ? "" : chapterAndSectionMap.get(record.getSectionId())); // 小节名称*/
            questionAdminVO.setChapterName(chapterAndSectionMap.get(record.getChapterId())); // 章节名字
            questionAdminVO.setSectionName(chapterAndSectionMap.get(record.getSectionId())); // 小节名字
            questionAdminVOS.add(questionAdminVO);
        }
        return PageDTO.of(page, questionAdminVOS);
    }

    /**
     * 管理端根据问题id查询详情
     * @param id 问题id
     * @return 返回问题详情VO
     */
    @Override
    public QuestionAdminVO queryQuestionAdminVOById(Long id) {
        // 1.根据问题id查询问题表
        InteractionQuestion question = this.getById(id);
        if (question == null) {
            throw new BadRequestException("该问题不存在");
        }
        // 封装用户ids，目录ids
        Set<Long> userIds = new HashSet<>();
        Set<Long> chapterIdAndSectionId = new HashSet<>();
        userIds.add(question.getUserId());
        chapterIdAndSectionId.add(question.getChapterId());
        chapterIdAndSectionId.add(question.getSectionId());
        // 2.远程调用课程服务, 需要获取老师信息
        CourseFullInfoDTO courseInfoById = courseClient.getCourseInfoById(question.getCourseId(), false, true);
        if (courseInfoById == null) {
            throw new BadRequestException("该课程下的问题不存在");
        }
        // 3.远程调用目录信息
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS = catalogueClient.batchQueryCatalogue(chapterIdAndSectionId);
        if (CollUtils.isEmpty(cataSimpleInfoDTOS)) {
            throw new BadRequestException("该目录下的问题不存在");
        }
        Map<Long, String> chapterAndSectionMap = cataSimpleInfoDTOS.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
        // 4.远程调用用户信息
        userIds.addAll(courseInfoById.getTeacherIds());
        List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
        if (CollUtils.isEmpty(userDTOS)) {
            throw new BadRequestException("该问题的作者或者老师不存在");
        }
        Map<Long, UserDTO> userDTOMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, c -> c));
        // 5.从caffeine缓存中获取分级分类名字
        String categoryNames = categoryCache.getCategoryNames(courseInfoById.getCategoryIds());
        // 6.封装VO
        QuestionAdminVO questionAdminVO = BeanUtils.copyBean(question, QuestionAdminVO.class);
        if (userDTOMap != null) {
            questionAdminVO.setUserName(userDTOMap.get(question.getUserId()).getName());
            questionAdminVO.setUserIcon(userDTOMap.get(question.getUserId()).getIcon());
            questionAdminVO.setTeacherName(userDTOMap.get(courseInfoById.getTeacherIds().get(0)).getName());
        }
        if (chapterAndSectionMap != null) {
            questionAdminVO.setChapterName(chapterAndSectionMap.get(question.getChapterId()));
            questionAdminVO.setSectionName(chapterAndSectionMap.get(question.getSectionId()));
        }
        questionAdminVO.setCategoryName(categoryNames);
        return questionAdminVO;
    }
}
