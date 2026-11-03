package com.gym.gymmanagementsystem.mapper;

import com.gym.gymmanagementsystem.pojo.VoltSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Mapper
public interface VoltSessionMapper {

    List<VoltSession> findByDateVenueProgram(@Param("date") LocalDate date, @Param("venueCode") String venueCode,
                                             @Param("programNo") String programNo);

    List<VoltSession> findByDate(@Param("date") LocalDate date);

    List<VoltSession> findBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 目录查询：按日期区间 / 单日 / 门店 / 教练 / 课程组合过滤。 */
    List<VoltSession> findFiltered(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                   @Param("date") LocalDate date, @Param("venueCode") String venueCode,
                                   @Param("coachId") String coachId, @Param("programNo") String programNo);

    VoltSession findById(@Param("sessionId") String sessionId);

    /** 预约写路径用：锁住场次行，串行化同一场次的重复校验和占座。 */
    VoltSession findByIdForUpdate(@Param("sessionId") String sessionId);

    VoltSession findFirstOpen(@Param("date") LocalDate date, @Param("today") LocalDate today,
                              @Param("venueCode") String venueCode, @Param("programNo") String programNo,
                              @Param("now") LocalTime now);

    VoltSession findNextByCoach(@Param("coachId") String coachId, @Param("from") LocalDate from,
                                 @Param("now") LocalTime now);

    /** 占用一个座位；满员或已关闭时返回 0，用于并发兜底。 */
    int increaseBooked(@Param("sessionId") String sessionId);

    int decreaseBooked(@Param("sessionId") String sessionId);
}
