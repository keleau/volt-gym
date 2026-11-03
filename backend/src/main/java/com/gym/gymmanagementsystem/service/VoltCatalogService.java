package com.gym.gymmanagementsystem.service;

import java.util.Map;

public interface VoltCatalogService {

    Map<String, Object> listVenues();

    Map<String, Object> listPrograms();

    Map<String, Object> listPlans();

    Map<String, Object> listCoaches(String venueCode, String programNo);

    Map<String, Object> listSessions(String venueCode, String date, String coachId, String programNo,
                                     String from, String to);
}
