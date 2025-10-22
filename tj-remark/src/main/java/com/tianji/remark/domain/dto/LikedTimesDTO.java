package com.tianji.remark.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
// 指定业务比如笔记或者回答业务，得到其点赞量，通过mq异步去发送消息
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor (staticName = "of")
public class LikedTimesDTO {
    /**
     * 点赞的业务id
     */
    private Long bizId;
    /**
     * 总的点赞次数
     */
    private Integer likedTimes;
}
