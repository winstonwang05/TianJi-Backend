package com.tianji.learning.constants;

public interface RedisConstants {
    /* 用户签到对应key前缀  完整 的后缀是用户id：当前年月*/
    String SIGN_RECORD_KEY_PREFIX = "sign:uid:";
    String POINTS_BOARD_KEY_PREFIX = "board:";
}
