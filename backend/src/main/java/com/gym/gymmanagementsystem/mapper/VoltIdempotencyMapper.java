package com.gym.gymmanagementsystem.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface VoltIdempotencyMapper {

    String findResponse(@Param("idempotencyKey") String idempotencyKey, @Param("endpoint") String endpoint);

    int insert(@Param("idempotencyKey") String idempotencyKey, @Param("endpoint") String endpoint,
               @Param("responseJson") String responseJson, @Param("createdAt") LocalDateTime createdAt);
}
