package com.gym.gymmanagementsystem.service.impl;

import com.gym.gymmanagementsystem.common.ApiException;
import com.gym.gymmanagementsystem.common.CommunityContent;
import com.gym.gymmanagementsystem.common.StoredResponse;
import com.gym.gymmanagementsystem.common.VoltContract;
import com.gym.gymmanagementsystem.mapper.VoltCommunityMapper;
import com.gym.gymmanagementsystem.pojo.Challenge;
import com.gym.gymmanagementsystem.pojo.ChallengeJoin;
import com.gym.gymmanagementsystem.pojo.CommunityEvent;
import com.gym.gymmanagementsystem.pojo.EventSignup;
import com.gym.gymmanagementsystem.pojo.VoltMember;
import com.gym.gymmanagementsystem.service.IdempotencyService;
import com.gym.gymmanagementsystem.service.VoltCommunityService;
import com.gym.gymmanagementsystem.service.VoltViewService;
import com.gym.gymmanagementsystem.service.mini.VoltMiniSupport;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class VoltCommunityServiceImpl implements VoltCommunityService {

    private static final String EVENT_ENDPOINT = "POST /events/{id}/signups";
    private static final String CHALLENGE_ENDPOINT = "POST /challenges/current/join";

    private final VoltCommunityMapper communityMapper;
    private final VoltViewService viewService;
    private final IdempotencyService idempotencyService;
    private final VoltMiniSupport support;

    public VoltCommunityServiceImpl(VoltCommunityMapper communityMapper,
                                    VoltViewService viewService,
                                    IdempotencyService idempotencyService,
                                    VoltMiniSupport support) {
        this.communityMapper = communityMapper;
        this.viewService = viewService;
        this.idempotencyService = idempotencyService;
        this.support = support;
    }

    @Override
    public Map<String, Object> community(VoltMember member) {
        String phone = member == null ? null : member.getPhone();

        List<Map<String, Object>> events = new ArrayList<>();
        for (CommunityEvent event : communityMapper.findEvents()) {
            boolean mySigned = phone != null && communityMapper.findSignup(event.getEventId(), phone) != null;
            events.add(viewService.eventView(event, mySigned));
        }

        Challenge challenge = communityMapper.findCurrentChallenge();
        boolean joinedByMe = challenge != null && phone != null
                && communityMapper.findChallengeJoin(challenge.getChallengeId(), phone) != null;

        List<String> signedEventIds = phone == null ? List.of() : communityMapper.findSignedEventIds(phone);

        Map<String, Object> membership = new LinkedHashMap<>();
        membership.put("eventIds", signedEventIds);
        membership.put("challengeJoined", joinedByMe);

        Map<String, Object> challengeView = challenge == null ? null : viewService.challengeView(challenge, joinedByMe);
        if (challengeView != null) {
            challengeView.put("myProgress", member == null ? 0
                    : support.stats(member.getMemberId()).get("weekSessions"));
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("stats", CommunityContent.STATS);
        resp.put("ranking", CommunityContent.RANKING);
        resp.put("stories", CommunityContent.STORIES);
        resp.put("events", events);
        resp.put("challenge", challengeView);
        resp.put("membership", membership);
        return resp;
    }

    @Override
    @Transactional
    public Map<String, Object> joinEvent(String eventId, Map<String, Object> input, String idempotencyKey, VoltMember member) {
        String endpoint = EVENT_ENDPOINT;
        Map<String, Object> payload = input == null ? Map.of() : input;
        CommunityEvent event = communityMapper.findEvent(eventId);
        if (event == null) {
            throw ApiException.notFound("活动不存在或已下线。");
        }

        String name = text(payload.get("name"));
        String phone = text(payload.get("phone"));
        if (member != null) {
            name = member.getNickname();
            phone = member.getPhone();
        }
        Map<String, String> fields = new LinkedHashMap<>();
        if (name == null) {
            fields.put("name", "请填写姓名");
        }
        if (!VoltContract.isPhone(phone)) {
            fields.put("phone", "请输入正确的 11 位手机号");
        }
        if (!fields.isEmpty()) {
            throw ApiException.validation("请检查填写内容。", fields);
        }

        String scope = "event|" + (member != null ? member.getMemberId() : phone) + "|" + eventId;
        StoredResponse replay = idempotencyService.find(scope, idempotencyKey, endpoint);
        if (replay != null && replay.getBody() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) replay.getBody();
            return body;
        }

        if (communityMapper.findSignup(eventId, phone) != null) {
            throw ApiException.conflict("DUPLICATE_SIGNUP", "你已经报名过这个活动了。");
        }
        int cap = event.getCapCount() == null ? 0 : event.getCapCount();
        int signed = event.getSignedCount() == null ? 0 : event.getSignedCount();
        if (signed >= cap) {
            throw ApiException.conflict("EVENT_FULL", "这个活动已经报满了，看看其他活动吧。");
        }

        LocalDateTime now = LocalDateTime.now();
        EventSignup signup = new EventSignup();
        signup.setSignupId(VoltContract.uniqueId("SG"));
        signup.setEventId(eventId);
        signup.setMemberId(member == null ? null : member.getMemberId());
        signup.setMemberName(name);
        signup.setMemberPhone(phone);
        signup.setSignupNo(VoltContract.uniqueId("E"));
        signup.setCreatedAt(now);
        try {
            communityMapper.insertSignup(signup);
        } catch (DuplicateKeyException ex) {
            throw ApiException.conflict("DUPLICATE_SIGNUP", "你已经报名过这个活动了。");
        }

        if (communityMapper.increaseEventSigned(eventId) == 0) {
            throw ApiException.conflict("EVENT_FULL", "这个活动已经报满了，看看其他活动吧。");
        }
        CommunityEvent fresh = communityMapper.findEvent(eventId);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("signupNo", signup.getSignupNo());
        resp.put("joinedAt", VoltContract.isoDateTime(now));
        resp.put("event", viewService.eventView(fresh, true));

        idempotencyService.save(scope, idempotencyKey, endpoint, 200, resp);
        return resp;
    }

    @Override
    @Transactional
    public Map<String, Object> joinChallenge(Map<String, Object> input, String idempotencyKey, VoltMember member) {
        String endpoint = CHALLENGE_ENDPOINT;
        Map<String, Object> payload = input == null ? Map.of() : input;
        Challenge challenge = communityMapper.findCurrentChallenge();
        if (challenge == null) {
            throw ApiException.notFound("当前没有进行中的挑战。");
        }

        String name = text(payload.get("name"));
        String phone = text(payload.get("phone"));
        if (member != null) {
            name = member.getNickname();
            phone = member.getPhone();
        }
        Map<String, String> fields = new LinkedHashMap<>();
        if (!VoltContract.isPhone(phone)) {
            fields.put("phone", "请输入正确的 11 位手机号");
        }
        if (!fields.isEmpty()) {
            throw ApiException.validation("请检查填写内容。", fields);
        }

        String scope = "challenge|" + (member != null ? member.getMemberId() : phone)
                + "|" + challenge.getChallengeId();
        StoredResponse replay = idempotencyService.find(scope, idempotencyKey, endpoint);
        if (replay != null && replay.getBody() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) replay.getBody();
            return body;
        }

        ChallengeJoin existing = communityMapper.findChallengeJoin(challenge.getChallengeId(), phone);
        String memberNo = member != null ? member.getMemberNo() : memberNoOf(phone);
        LocalDateTime now = LocalDateTime.now();

        Map<String, Object> resp = new LinkedHashMap<>();
        if (existing != null) {
            resp.put("joined", true);
            resp.put("already", true);
            resp.put("memberNo", existing.getMemberNo());
            resp.put("joinedAt", VoltContract.isoDateTime(existing.getCreatedAt()));
            idempotencyService.save(scope, idempotencyKey, endpoint, 200, resp);
            return resp;
        }

        ChallengeJoin join = new ChallengeJoin();
        join.setJoinId(VoltContract.uniqueId("CJ"));
        join.setChallengeId(challenge.getChallengeId());
        join.setMemberId(member == null ? null : member.getMemberId());
        join.setMemberPhone(phone);
        join.setMemberNo(memberNo);
        join.setCreatedAt(now);
        try {
            communityMapper.insertChallengeJoin(join);
        } catch (DuplicateKeyException ex) {
            ChallengeJoin current = communityMapper.findChallengeJoin(challenge.getChallengeId(), phone);
            Map<String, Object> already = new LinkedHashMap<>();
            already.put("joined", true);
            already.put("already", true);
            already.put("memberNo", current == null ? memberNo : current.getMemberNo());
            already.put("joinedAt", VoltContract.isoDateTime(current == null ? now : current.getCreatedAt()));
            idempotencyService.save(scope, idempotencyKey, endpoint, 200, already);
            return already;
        }
        communityMapper.increaseChallengeJoined(challenge.getChallengeId());

        resp.put("joined", true);
        resp.put("already", false);
        resp.put("memberNo", memberNo);
        resp.put("joinedAt", VoltContract.isoDateTime(now));

        idempotencyService.save(scope, idempotencyKey, endpoint, 200, resp);
        return resp;
    }

    private String memberNoOf(String phone) {
        return String.format("M%010d", Math.abs((long) phone.hashCode() % 10_000_000_000L));
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String trimmed = String.valueOf(value).trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
