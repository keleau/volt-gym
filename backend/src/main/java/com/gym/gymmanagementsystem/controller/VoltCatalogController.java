package com.gym.gymmanagementsystem.controller;

import com.gym.gymmanagementsystem.service.VoltCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 目录类接口：门店 / 课程 / 次卡方案 / 教练。官网与小程序共用。
 */
@RestController
@RequestMapping("/api/v1/catalog")
public class VoltCatalogController {

    private final VoltCatalogService catalogService;

    public VoltCatalogController(VoltCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/venues")
    public Map<String, Object> venues() {
        return catalogService.listVenues();
    }

    @GetMapping("/programs")
    public Map<String, Object> programs() {
        return catalogService.listPrograms();
    }

    @GetMapping("/plans")
    public Map<String, Object> plans() {
        return catalogService.listPlans();
    }

    @GetMapping("/coaches")
    public Map<String, Object> coaches(
            @RequestParam(value = "venueCode", required = false) String venueCode,
            @RequestParam(value = "programNo", required = false) String programNo) {
        return catalogService.listCoaches(blankToNull(venueCode), blankToNull(programNo));
    }

    @GetMapping("/sessions")
    public Map<String, Object> sessions(
            @RequestParam(value = "venueCode", required = false) String venueCode,
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "coachId", required = false) String coachId,
            @RequestParam(value = "programNo", required = false) String programNo,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        return catalogService.listSessions(blankToNull(venueCode), blankToNull(date), blankToNull(coachId),
                blankToNull(programNo), blankToNull(from), blankToNull(to));
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
