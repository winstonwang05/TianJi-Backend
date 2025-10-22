package com.tianji.learning.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.service.IInteractionReplyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动问题的回答或评论 前端控制器
 * </p>
 *
 * @author author
 * @since 2025-10-07
 */
@Api(tags = "回答或者评论相关接口")
@Slf4j
@RestController
@RequestMapping("/replies")
@RequiredArgsConstructor
public class InteractionReplyController {
    final IInteractionReplyService interactionReplyService;

    @ApiOperation("新增回答或者评论")
    @PostMapping
    public void saveReplies(@RequestBody @Validated ReplyDTO replyDTO) {
        interactionReplyService.saveReplies(replyDTO);
    }
    @ApiOperation("用户端分页查询问题或者评论")
    @GetMapping("page")
    public PageDTO<ReplyVO> selectRepliesPage(ReplyPageQuery pageQuery) {
        return interactionReplyService.selectRepliesPage(pageQuery);
    }
}
