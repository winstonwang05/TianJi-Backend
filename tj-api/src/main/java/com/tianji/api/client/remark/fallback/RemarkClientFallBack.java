package com.tianji.api.client.remark.fallback;

import com.tianji.api.client.remark.RemarkClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

import java.util.List;
import java.util.Set;

// RemarkClient 的降级类 ，需要在调用服务接口声明
@Slf4j
public class RemarkClientFallBack implements FallbackFactory<RemarkClient> {
    // 如果RemarkClient服务宕机或者其他服务调用出问题，那么就会走下面的create返回空数据
    @Override
    public RemarkClient create(Throwable cause) {
        log.error("调用Remark服务降级了，原因 ", cause);
        return new RemarkClient() {
            @Override
            public Set<Long> getLikesStatusByBizIds(List<Long> bizIds) {
                return null;
            }
        };
    }
}
