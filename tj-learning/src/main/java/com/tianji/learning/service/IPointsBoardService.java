package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsBoard;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardVO;

import javax.validation.constraints.Min;
import java.util.List;

/**
 * <p>
 * 学霸天梯榜 服务类
 * </p>
 *
 * @author author
 * @since 2025-10-12
 */
public interface IPointsBoardService extends IService<PointsBoard> {

    PointsBoardVO queryBoardsRank(PointsBoardQuery query);
    List<PointsBoard> queryCurrentSeasonPage(String boardKey,
                                                    @Min(value = 1, message = "页码不能小于1") Integer pageNo,
                                                    @Min(value = 1, message = "每页查询数量不能小于1") Integer pageSize);
}
