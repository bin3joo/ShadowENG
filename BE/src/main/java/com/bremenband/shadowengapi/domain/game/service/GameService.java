package com.bremenband.shadowengapi.domain.game.service;

import com.bremenband.shadowengapi.domain.game.dto.redis.GameSessionData;
import com.bremenband.shadowengapi.domain.game.dto.res.*;
import com.bremenband.shadowengapi.domain.game.entity.*;
import com.bremenband.shadowengapi.domain.game.repository.*;
import com.bremenband.shadowengapi.domain.study.client.PythonApiClient;
import com.bremenband.shadowengapi.domain.study.dto.python.PythonEvaluateAudioRequest;
import com.bremenband.shadowengapi.domain.study.dto.python.PythonEvaluateAudioResponse;
import com.bremenband.shadowengapi.global.exception.CustomException;
import com.bremenband.shadowengapi.global.exception.ErrorCode;
import com.bremenband.shadowengapi.global.s3.S3Uploader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameService {

    private static final int MAX_LEVEL = 3;
    private static final double HEART_THRESHOLD = 70.0;
    private static final double WEIGHT_FACTOR = 0.1;

    private final UserGameProfileRepository userGameProfileRepository;
    private final DailyGameSentenceRepository dailyGameSentenceRepository;
    private final DailyBestRecordRepository dailyBestRecordRepository;
    private final PythonApiClient pythonApiClient;
    private final S3Uploader s3Uploader;
    private final GameSessionStore gameSessionStore;
    private final GameWriter gameWriter;
    private final ObjectMapper objectMapper;

    // ────────────────────────────────────────────────────────────────
    // GET /game/today
    // ────────────────────────────────────────────────────────────────

    public GameTodayResponse getToday(Long userId) {
        LocalDate today = LocalDate.now();
        List<GameTodayResponse.LevelStatus> levels = new ArrayList<>();

        for (int level = 1; level <= MAX_LEVEL; level++) {
            boolean unlocked = isLevelUnlocked(userId, level, today);

            DailyBestRecord best = dailyBestRecordRepository
                    .findByUserIdAndLevelAndRecordDate(userId, level, today)
                    .orElse(null);

            GameTodayResponse.DailyBest dailyBest = best == null ? null
                    : new GameTodayResponse.DailyBest(
                            best.getGameRecord().getHearts(),
                            best.getBestFinalScore().doubleValue());

            levels.add(new GameTodayResponse.LevelStatus(level, unlocked, dailyBest));
        }

        return new GameTodayResponse(levels);
    }

    // ────────────────────────────────────────────────────────────────
    // GET /game/levels/{level}/rounds/{round}
    // ────────────────────────────────────────────────────────────────

    public GameRoundResponse getRoundSentence(Long userId, int level, int round) {
        LocalDate today = LocalDate.now();

        if (!isLevelUnlocked(userId, level, today)) {
            throw new CustomException(ErrorCode.GAME_LEVEL_LOCKED);
        }

        DailyGameSentence daily = getDailyOrThrow(level, today);
        GameSentence gs = daily.getGameSentence();

        String sentence = switch (round) {
            case 1 -> gs.getContent();
            case 2, 3 -> null;
            default -> throw new CustomException(ErrorCode.GAME_ROUND_INVALID);
        };

        return new GameRoundResponse(level, round, sentence, gs.getMaskedContent(), gs.getReferenceAudioUrl());
    }

    // ────────────────────────────────────────────────────────────────
    // POST /game/levels/{level}/rounds/{round}/evaluate
    // ────────────────────────────────────────────────────────────────

    // Python API 호출(최대 35초)이 있으므로 @Transactional 없이 실행.
    // DB 저장은 GameWriter(@Transactional)에 위임.
    public GameEvaluationResponse evaluate(Long userId, int level, int round, MultipartFile audioFile) {
        LocalDate today = LocalDate.now();
        log.info("[Game.evaluate] start: userId={}, level={}, round={}", userId, level, round);

        // 1. 레벨 해금 확인
        if (!isLevelUnlocked(userId, level, today)) {
            throw new CustomException(ErrorCode.GAME_LEVEL_LOCKED);
        }

        // 2. 오늘의 문장 조회
        DailyGameSentence daily = getDailyOrThrow(level, today);
        GameSentence gs = daily.getGameSentence();

        // 3. 라운드 순서 검증 (round 2, 3은 이전 round 결과가 Redis에 있어야 함)
        GameSessionData session;
        if (round == 1) {
            session = new GameSessionData(); // 새 게임 또는 재도전 — 세션 초기화
        } else {
            session = gameSessionStore.find(userId, level, today)
                    .filter(s -> !s.isGameOver())
                    .filter(s -> s.getRound(round - 1).isPresent())
                    .orElseThrow(() -> new CustomException(ErrorCode.GAME_ROUND_INVALID));
        }

        // 4. S3 업로드
        String s3Key = s3Uploader.upload(audioFile);
        String audioFormat = getExtension(audioFile.getOriginalFilename());
        log.info("[Game.evaluate] uploaded: s3Key={}, format={}", s3Key, audioFormat);

        // 5. Python API 평가 호출
        JsonNode features = parseJson(gs.getFeatures());
        JsonNode wordTimestamps = parseJson(gs.getWordTimestamps());
        PythonEvaluateAudioRequest request =
                new PythonEvaluateAudioRequest(s3Key, audioFormat, gs.getContent(), features, wordTimestamps, null);

        PythonEvaluateAudioResponse python = pythonApiClient.evaluateAudio(request);
        log.info("[Game.evaluate] python status={}", python.status());

        if ("FAIL".equals(python.status())) {
            log.warn("[Game.evaluate] FAIL: userId={}, level={}, round={}, s3Key={}", userId, level, round, s3Key);
            throw new CustomException(ErrorCode.VOICE_RECOGNITION_FAILED);
        }

        // 6. S3 삭제
        s3Uploader.delete(s3Key);

        // 7. 결과 계산
        double totalScore = python.scores().totalScore();
        boolean passed = totalScore >= HEART_THRESHOLD;
        boolean gameOver = !passed || round == MAX_LEVEL;

        // 8. 세션 업데이트 (Redis)
        GameSessionData.RoundResult roundResult = buildRoundResult(round, python);
        session.addRound(roundResult);
        if (passed) session.setHearts(session.getHearts() + 1);
        session.setGameOver(gameOver);
        gameSessionStore.save(userId, level, today, session);

        // 9. 게임 종료 시 DB 저장
        GameEvaluationResponse.GameResult gameResult = null;
        if (gameOver) {
            gameResult = saveAndBuildResult(userId, level, today, session);
            gameSessionStore.delete(userId, level, today);
        }

        log.info("[Game.evaluate] done: userId={}, level={}, round={}, hearts={}, gameOver={}",
                userId, level, round, session.getHearts(), gameOver);

        return buildResponse(python, round, session.getHearts(), gameOver, gameResult);
    }

    // ────────────────────────────────────────────────────────────────
    // GET /game/leaderboard
    // ────────────────────────────────────────────────────────────────

    public GameLeaderboardResponse getLeaderboard(Long userId) {
        UserGameProfile myProfile = userGameProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.GAME_PROFILE_NOT_FOUND));

        if (myProfile.isFrozen()) {
            throw new CustomException(ErrorCode.USER_IS_FROZEN);
        }

        Tier myTier = myProfile.getTier();
        List<UserGameProfile> ranked = userGameProfileRepository
                .findByTierAndNotFrozenOrderByWeeklyScoreDesc(myTier);

        int total = ranked.size();
        int promotionCount = myTier.isHighest() ? 0 : Math.max(1, (int) Math.ceil(total * 0.1));

        // 내 순위
        int myRank = -1;
        for (int i = 0; i < ranked.size(); i++) {
            if (ranked.get(i).getUserId().equals(userId)) {
                myRank = i + 1;
                break;
            }
        }

        // Top 3
        List<GameLeaderboardResponse.RankerInfo> topRankers = new ArrayList<>();
        for (int i = 0; i < Math.min(3, ranked.size()); i++) {
            UserGameProfile p = ranked.get(i);
            topRankers.add(new GameLeaderboardResponse.RankerInfo(
                    i + 1, p.getUser().getNickname(),
                    p.getWeeklyScore().doubleValue(), p.getUserId().equals(userId)));
        }

        // 나 ± 1
        List<GameLeaderboardResponse.RankerInfo> nearbyRankers = new ArrayList<>();
        for (int rank : new int[]{myRank - 1, myRank, myRank + 1}) {
            if (rank >= 1 && rank <= ranked.size()) {
                UserGameProfile p = ranked.get(rank - 1);
                nearbyRankers.add(new GameLeaderboardResponse.RankerInfo(
                        rank, p.getUser().getNickname(),
                        p.getWeeklyScore().doubleValue(), p.getUserId().equals(userId)));
            }
        }

        return new GameLeaderboardResponse(
                myTier.name(), promotionCount, calcTimeUntilReset(), topRankers, nearbyRankers);
    }

    // ────────────────────────────────────────────────────────────────
    // GET /game/profile
    // ────────────────────────────────────────────────────────────────

    public GameProfileResponse getProfile(Long userId) {
        UserGameProfile profile = userGameProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.GAME_PROFILE_NOT_FOUND));
        return new GameProfileResponse(
                profile.getTier().name(),
                profile.getWeeklyScore().doubleValue(),
                profile.isFrozen());
    }

    // ────────────────────────────────────────────────────────────────
    // Private helpers
    // ────────────────────────────────────────────────────────────────

    private boolean isLevelUnlocked(Long userId, int level, LocalDate today) {
        if (level == 1) return true;
        // 이전 레벨 오늘 최고 기록의 hearts >= 1
        return dailyBestRecordRepository
                .findByUserIdAndLevelAndRecordDate(userId, level - 1, today)
                .map(r -> r.getGameRecord().getHearts() >= 1)
                .orElse(false);
    }

    private DailyGameSentence getDailyOrThrow(int level, LocalDate today) {
        return dailyGameSentenceRepository.findByLevelAndAssignedDate(level, today)
                .orElseThrow(() -> new CustomException(ErrorCode.GAME_DAILY_SENTENCE_NOT_ASSIGNED));
    }

    private GameSessionData.RoundResult buildRoundResult(int round, PythonEvaluateAudioResponse python) {
        String wlf;
        try {
            wlf = objectMapper.writeValueAsString(python.details().wordLevelFeedback());
        } catch (Exception e) {
            log.warn("[Game] wordLevelFeedback 직렬화 실패");
            wlf = "[]";
        }
        return new GameSessionData.RoundResult(
                round, wlf,
                python.scores().totalScore(),
                python.scores().speedSimilarity(),
                python.scores().dynamicStressScore(),
                python.scores().boundaryToneScore());
    }

    private GameEvaluationResponse.GameResult saveAndBuildResult(
            Long userId, int level, LocalDate today, GameSessionData session) {

        Collection<GameSessionData.RoundResult> rounds = session.getRounds().values();
        double avgTotal   = rounds.stream().mapToDouble(GameSessionData.RoundResult::getTotalScore).average().orElse(0);
        double avgSpeed   = rounds.stream().mapToDouble(GameSessionData.RoundResult::getSpeedSimilarity).average().orElse(0);
        double avgDynamic = rounds.stream().mapToDouble(GameSessionData.RoundResult::getDynamicStressScore).average().orElse(0);
        double avgBound   = rounds.stream().mapToDouble(GameSessionData.RoundResult::getBoundaryToneScore).average().orElse(0);
        double cumulative = rounds.stream().mapToDouble(GameSessionData.RoundResult::getTotalScore).sum();
        int hearts = session.getHearts();
        double finalScore = avgTotal * (1 + hearts * WEIGHT_FACTOR);

        gameWriter.saveGameResult(userId, level, today, hearts,
                avgTotal, avgSpeed, avgDynamic, avgBound, finalScore, cumulative);

        return new GameEvaluationResponse.GameResult(
                round(avgTotal), round(avgSpeed), round(avgDynamic), round(avgBound),
                round(finalScore), round(cumulative), hearts);
    }

    private GameEvaluationResponse buildResponse(PythonEvaluateAudioResponse python, int round,
                                                  int hearts, boolean gameOver,
                                                  GameEvaluationResponse.GameResult result) {
        PythonEvaluateAudioResponse.Details d = python.details();
        PythonEvaluateAudioResponse.Scores s = python.scores();

        List<GameEvaluationResponse.Details.WordLevelFeedback> wlf = d.wordLevelFeedback().stream()
                .map(w -> new GameEvaluationResponse.Details.WordLevelFeedback(w.word(), w.status()))
                .toList();

        GameEvaluationResponse.Details details = new GameEvaluationResponse.Details(
                wlf,
                new GameEvaluationResponse.Details.BoundaryToneFeedback(d.boundaryToneFeedback().status()),
                new GameEvaluationResponse.Details.DynamicStressFeedback(d.dynamicStressFeedback().status()));

        GameEvaluationResponse.Scores scores = new GameEvaluationResponse.Scores(
                s.totalScore(), s.wordAccuracy(), s.prosodyAndStress(),
                s.wordRhythmScore(), s.boundaryToneScore(), s.dynamicStressScore(),
                s.speedSimilarity(), s.pauseSimilarity());

        return new GameEvaluationResponse(python.userTranscription(), details, scores,
                round, hearts, gameOver, result);
    }

    private GameLeaderboardResponse.TimeUntilReset calcTimeUntilReset() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextMonday = now.toLocalDate()
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .atStartOfDay();
        long totalMinutes = Duration.between(now, nextMonday).toMinutes();
        int days    = (int) (totalMinutes / (60 * 24));
        int hours   = (int) ((totalMinutes % (60 * 24)) / 60);
        int minutes = (int) (totalMinutes % 60);
        return new GameLeaderboardResponse.TimeUntilReset(days, hours, minutes);
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.DATA_CONVERSION_ERROR);
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "webm";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
