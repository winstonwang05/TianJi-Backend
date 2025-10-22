package com.tianji.learning.service.impl;

import com.tianji.common.utils.BeanUtils;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.domain.vo.PointsBoardSeasonVO;
import com.tianji.learning.mapper.PointsBoardSeasonMapper;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.tianji.learning.constants.LearningConstants.POINTS_BOARD_TABLE_PREFIX;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author author
 * @since 2025-10-12
 */
@Service
public class PointsBoardSeasonServiceImpl extends ServiceImpl<PointsBoardSeasonMapper, PointsBoardSeason>
        implements IPointsBoardSeasonService {

    /**
     * 创建不同赛季信息的表
     * @param id 赛季id
     */
    @Override
    public void createPointsBoardLatestTable(Integer id) {
        getBaseMapper().createPointsBoardTable(POINTS_BOARD_TABLE_PREFIX + id);
    }

    /**
     * 查询赛季列表
     * @return 返回赛季列表信息
     */
    @Override
    public List<PointsBoardSeasonVO> getPointsBoardSeasons() {
        List<PointsBoardSeason> list = this.list();
        List<PointsBoardSeasonVO> pointsBoardSeasonVOList = new ArrayList<>();
        for (PointsBoardSeason pointsBoardSeason : list) {
            PointsBoardSeasonVO pointsBoardSeasonVO = BeanUtils.copyBean(pointsBoardSeason, PointsBoardSeasonVO.class);
            pointsBoardSeasonVOList.add(pointsBoardSeasonVO);
        }

        return pointsBoardSeasonVOList;
    }
}
