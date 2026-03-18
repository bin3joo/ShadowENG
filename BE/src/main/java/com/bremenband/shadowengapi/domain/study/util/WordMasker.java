package com.bremenband.shadowengapi.domain.study.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 영어 문장에서 학습에 의미 있는 단어를 선택하여 마스킹하는 유틸리티.
 *
 * 마스킹 후보 제외 기준:
 *  1. 알파벳 2자 이하 단어 (a, is, to 등 자연 탈락)
 *  2. EXCLUSION_SET에 포함된 기능어 (with, from, that...)
 *
 * 가중치:
 *  - 3글자 단어 → weight 1 (낮은 확률)
 *  - 4글자 이상  → weight 3 (높은 확률)
 */
public class WordMasker {

    private static final String MASK = "_____";
    private static final Random RANDOM = new Random();

    private static final Set<String> EXCLUSION_SET = Set.of(
            // 전치사
            "with", "from", "into", "over", "under", "about", "after",
            "before", "between", "through", "without", "upon", "onto",
            // 접속사
            "that", "than", "then", "when", "where", "while", "though",
            "although", "because", "since", "unless", "until", "whether",
            "however", "therefore",
            // 대명사
            "this", "these", "those", "they", "them", "their", "theirs",
            "your", "yours", "mine", "ours", "its", "his", "her", "hers",
            "what", "which", "who", "whom", "whose",
            // 조동사
            "have", "has", "been", "were", "will", "would", "could",
            "should", "shall", "may", "might", "must", "also", "does", "did",
            // 부사/형용사(기능어 성격)
            "just", "very", "some", "more", "most", "even", "both",
            "each", "only", "same", "such", "here", "there", "still",
            "again", "away", "back", "down", "once", "ever", "never",
            // 기타
            "like", "make", "know", "want", "said"
    );

    private WordMasker() {}

    /**
     * 문장에서 의미 있는 단어 하나를 선택하여 _____ 로 치환한다.
     * 마스킹 후보가 없으면 원문을 그대로 반환한다.
     */
    public static String mask(String sentence) {
        String[] words = sentence.split(" ");
        List<int[]> candidates = new ArrayList<>(); // [단어 인덱스, 가중치]

        for (int i = 0; i < words.length; i++) {
            String clean = words[i].replaceAll("[^a-zA-Z']", "").toLowerCase();
            if (clean.length() <= 2) continue;
            if (EXCLUSION_SET.contains(clean)) continue;

            int weight = clean.length() == 3 ? 1 : 3;
            candidates.add(new int[]{i, weight});
        }

        if (candidates.isEmpty()) {
            return sentence;
        }

        int selectedIndex = weightedRandom(candidates);
        String[] result = words.clone();
        String original = result[selectedIndex];

        // 단어 뒤 구두점 보존 (예: "job," → "_____,")
        String trailing = original.replaceAll("^[a-zA-Z']+", "");
        result[selectedIndex] = MASK + trailing;

        return String.join(" ", result);
    }

    /**
     * word_level_feedback에서 good이 아닌 단어 목록을 받아 해당 단어를 _____ 로 치환한다.
     * 대소문자 및 구두점을 무시하고 매칭한다.
     * 마스킹 대상이 없으면 원문을 그대로 반환한다.
     */
    public static String mask(String sentence, Set<String> wordsToMask) {
        if (wordsToMask.isEmpty()) return sentence;

        String[] words = sentence.split(" ");
        boolean anyMasked = false;

        for (int i = 0; i < words.length; i++) {
            String clean = words[i].replaceAll("[^a-zA-Z']", "").toLowerCase();
            if (wordsToMask.contains(clean)) {
                String trailing = words[i].replaceAll("^[a-zA-Z']+", "");
                words[i] = MASK + trailing;
                anyMasked = true;
            }
        }

        return anyMasked ? String.join(" ", words) : sentence;
    }

    private static int weightedRandom(List<int[]> candidates) {
        int total = candidates.stream().mapToInt(c -> c[1]).sum();
        int r = RANDOM.nextInt(total);
        int cumulative = 0;
        for (int[] candidate : candidates) {
            cumulative += candidate[1];
            if (r < cumulative) {
                return candidate[0];
            }
        }
        return candidates.get(candidates.size() - 1)[0];
    }
}
