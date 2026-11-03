package com.gym.gymmanagementsystem.mapper;

import com.gym.gymmanagementsystem.pojo.Coach;
import com.gym.gymmanagementsystem.pojo.Plan;
import com.gym.gymmanagementsystem.pojo.Program;
import com.gym.gymmanagementsystem.pojo.Venue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Mapper
public interface VoltCatalogMapper {

    List<Venue> findVenues();

    Venue findVenue(@Param("venueCode") String venueCode);

    int countTodaySlots(@Param("venueCode") String venueCode, @Param("date") LocalDate date,
                        @Param("now") LocalTime now);

    List<Program> findPrograms();

    Program findProgram(@Param("programNo") String programNo);

    List<Plan> findPlans();

    Plan findPlan(@Param("planCode") String planCode);

    List<Coach> findCoaches();

    Coach findCoach(@Param("coachId") String coachId);
}
