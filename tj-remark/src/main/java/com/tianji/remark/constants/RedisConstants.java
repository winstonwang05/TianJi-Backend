package com.tianji.remark.constants;

public interface RedisConstants {
    /* 业务下用户点赞集合的key ，下面是前缀，后缀就是业务id*/
    String LIKE_BIZ_KEY_PREFIX = "likes:set:biz:";
    /* 统计业务id下的点赞的key，下面是前缀，后缀是业务类型（问答或者笔记）*/
    String LIKE_COUNT_KEY_PREFIX = "likes:times:type:";
}
