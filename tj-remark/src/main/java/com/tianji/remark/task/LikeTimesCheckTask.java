package com.tianji.remark.task;

import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LikeTimesCheckTask {
    private static final List<String> BIZ_TYPES = List.of("QA", "NOTE"); //业务类型
    private static final int MAX_BIZ_SIZE = 30; // 每次最多取多少业务点赞数量信息
    private final ILikedRecordService recordService;

    @Scheduled(fixedDelay = 40000)
    public void likeTimesCheck() {
        for (String bizType : BIZ_TYPES) {
            recordService.readLikedTimesAndSendMes(bizType, MAX_BIZ_SIZE);
        }
    }
}
