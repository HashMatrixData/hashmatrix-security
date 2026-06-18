package io.hashmatrix.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 数据安全分系统启动类。
 *
 * <p>工程基座：分层骨架（api/app/domain/infra）+ 多租户上下文路由 + 网关预认证授权 + Flowable
 * 审批接线 + PostgreSQL 连通，公共能力经 libs-java starter 复用。分类分级/标签/审批/审计业务
 * 在本基座之上落地。
 */
@SpringBootApplication
public class SecurityApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecurityApplication.class, args);
    }
}
