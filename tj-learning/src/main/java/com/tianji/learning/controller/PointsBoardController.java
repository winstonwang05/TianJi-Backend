package com.tianji.learning.controller;


import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.service.IPointsBoardService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 学霸天梯榜 前端控制器
 * </p>
 *
 * @author author
 * @since 2025-10-12
 */
@Api(tags = "赛季相关接口")
@RestController
@RequestMapping("/boards")
@RequiredArgsConstructor
public class PointsBoardController {
    final IPointsBoardService pointsBoardService;

    @ApiOperation("查询学霸积分榜 - 实时排行榜和历史排行榜一起")
    @GetMapping
    public PointsBoardVO queryBoardsRank(PointsBoardQuery query) {
        return pointsBoardService.queryBoardsRank(query);
    }
}
