package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.service.IInteractionQuestionService;
import com.tianji.learning.service.impl.InteractionQuestionServiceImpl;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author author
 * @since 2025-10-07
 */
@Api(tags = "互动问题相关接口-用户端")
@RestController
@RequestMapping("/questions")
@RequiredArgsConstructor
public class InteractionQuestionController {
    private final IInteractionQuestionService interactionQuestionService;
    @ApiOperation("新增互动问题")
    @PostMapping
    public void saveQuestion(@Validated @RequestBody QuestionFormDTO questionFormDTO) {
        interactionQuestionService.saveQuestion(questionFormDTO);
    }
    @ApiOperation("修改互动问题")
    @PutMapping("{id}")
    public void updateQuestion(@RequestBody QuestionFormDTO questionFormDTO,
                               @PathVariable Long id) {
        interactionQuestionService.updateQuestion(questionFormDTO, id);
    }
    @ApiOperation("用户端分页查询互动问题")
    @GetMapping("page")
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query) {
        return interactionQuestionService.queryQuestionPage(query);
    }
    @ApiOperation("用户端查询问题详情")
    @GetMapping("{id}")
    public QuestionVO queryQuestionById(@PathVariable Long id) {
        return interactionQuestionService.queryQuestionById(id);
    }
    @ApiOperation("用户删除问题")
    @DeleteMapping("{id}")
    public void deleteQuestion(@PathVariable Long id) {
        interactionQuestionService.deleteQuestion(id);
    }
}
