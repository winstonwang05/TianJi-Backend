package com.tianji.learning;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest

public class Test1 {
    @Autowired
    private ILearningLessonService learningLessonService;
    @Test
    public void test() {
/*        // 传统分页查询
        Page<LearningLesson> page = new Page<>(1, 2);
        LambdaQueryWrapper<LearningLesson> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(LearningLesson::getUserId, 2);
        queryWrapper.orderByAsc(LearningLesson::getLatestLearnTime);
        Page<LearningLesson> page1 = learningLessonService.page(page, queryWrapper);
        page1.getRecords().forEach(System.out::println);*/
/*        // 另外一种排序方法
        Page<LearningLesson> page = new Page<>(1, 2);
        List<OrderItem> orderItems = new ArrayList<>();
        OrderItem orderItem = new OrderItem();
        orderItem.setColumn("latest_learn_time");
        orderItem.setAsc(false);
        orderItems.add(orderItem);
        page.setOrders(orderItems);
        LambdaQueryWrapper<LearningLesson> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(LearningLesson::getUserId, 2);
        learningLessonService.page(page, queryWrapper);
        page.getRecords().forEach(System.out::println);*/
        // 项目中使用的方式
        PageQuery pageQuery = new PageQuery();
        // 设置pageQuery实体类的属性信息
        pageQuery.setPageSize(2);
        pageQuery.setPageNo(1);
        pageQuery.setSortBy("latest_learn_time");
        pageQuery.setIsAsc(false);
        Page<LearningLesson> latestLearnTime = learningLessonService.lambdaQuery()
                .eq(LearningLesson::getUserId, 2)
                .page(pageQuery.toMpPage("latest_learn_time", false));// toMapPage源码就是通过第二种方式实现的
        for (LearningLesson learningLesson : latestLearnTime.getRecords()) {
            System.out.println(learningLesson);
        }

    }

}
