package com.tianji.remark.controller;


import com.tianji.remark.domain.dto.LikeRecordFormDTO;

import com.tianji.remark.service.ILikedRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * <p>
 * 点赞记录表 前端控制器
 * </p>
 *
 * @author author
 * @since 2025-10-10
 */
@Api(tags = "点赞相关接口")
@RestController
@RequestMapping("/likes")
@RequiredArgsConstructor
public class LikedRecordController {
    private final ILikedRecordService likedRecordService;
    @ApiOperation("点赞或者取消点赞")
    @PostMapping
    public void addLikeRecord(@RequestBody @Validated LikeRecordFormDTO likeRecordFormDTO) {
        likedRecordService.addLikeRecord(likeRecordFormDTO);
    }
    @ApiOperation("根据指定业务id查询点赞状态")
    @GetMapping("list")
    public Set<Long> getLikesStatusByBizIds(@RequestParam("bizIds") List<Long> bizIds) {
        return likedRecordService.getLikesStatusByBizIds(bizIds);
    }
}
