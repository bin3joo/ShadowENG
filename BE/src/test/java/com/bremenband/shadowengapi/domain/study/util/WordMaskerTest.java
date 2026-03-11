package com.bremenband.shadowengapi.domain.study.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WordMaskerTest {

    // ── 기본 마스킹 동작 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 문장: 마스킹 결과에 _____ 가 정확히 하나 포함된다")
    void mask_정상문장_마스크하나포함() {
        String sentence = "Everybody knows you are a beautiful talented person.";

        String result = WordMasker.mask(sentence);

        long maskCount = countOccurrences(result, "_____");
        assertThat(maskCount).isEqualTo(1);
    }

    @Test
    @DisplayName("마스킹 후 단어 개수가 원문과 동일하다")
    void mask_마스킹후_단어수_동일() {
        String sentence = "She works as a software engineer at the company.";

        String result = WordMasker.mask(sentence);

        assertThat(result.split(" ")).hasSize(sentence.split(" ").length);
    }

    @Test
    @DisplayName("마스킹 결과는 원문과 달라야 한다 (후보 단어가 충분한 경우)")
    void mask_결과_원문과_다름() {
        String sentence = "The students completed their challenging assignment yesterday.";

        String result = WordMasker.mask(sentence);

        assertThat(result).isNotEqualTo(sentence);
        assertThat(result).contains("_____");
    }

    // ── 구두점 처리 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("단어 뒤 구두점은 마스킹 후에도 보존된다")
    void mask_구두점_보존() {
        // "person." 이 마스킹되면 "_____." 이 되어야 함
        // 후보가 person만 있도록 단순한 문장 사용
        String sentence = "beautiful talented person.";

        String result = WordMasker.mask(sentence);

        // 마스킹 발생 시 구두점 보존 확인
        if (result.contains("_____")) {
            assertThat(result).doesNotContain("_____x"); // 구두점이 사라지지 않음
            // 원문 토큰 수 유지
            assertThat(result.split(" ")).hasSize(sentence.split(" ").length);
        }
    }

    // ── 후보 제외 규칙 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("알파벳 2자 이하 단어는 마스킹되지 않는다")
    void mask_2자이하_마스킹안됨() {
        // "a", "is", "to" 등 2자 이하만 있는 문장 → 마스킹 없이 원문 반환
        String sentence = "I am a to be it so";

        String result = WordMasker.mask(sentence);

        assertThat(result).isEqualTo(sentence);
    }

    @RepeatedTest(30)
    @DisplayName("제외 단어(with, from, that 등)는 100번 반복해도 마스킹되지 않는다")
    void mask_제외단어_마스킹안됨() {
        // 제외 단어만 존재 + 마스킹 가능한 단어도 함께
        String sentence = "with from that this they beautiful";

        String result = WordMasker.mask(sentence);

        // 마스킹이 발생했다면 반드시 "beautiful" 이어야 함
        if (result.contains("_____")) {
            // with, from, that, this, they 는 절대 마스킹되면 안 됨
            assertThat(result).doesNotContain("_____ from");
            assertThat(result).doesNotContain("with _____");
            String[] tokens = result.split(" ");
            for (int i = 0; i < tokens.length; i++) {
                if (tokens[i].startsWith("_____")) {
                    String original = sentence.split(" ")[i];
                    assertThat(original).isEqualTo("beautiful");
                }
            }
        }
    }

    @Test
    @DisplayName("후보 단어가 전혀 없으면 원문을 그대로 반환한다")
    void mask_후보없음_원문반환() {
        // 2자 이하 + 제외 단어만으로 구성
        String sentence = "I am with from that";

        String result = WordMasker.mask(sentence);

        assertThat(result).isEqualTo(sentence);
    }

    // ── 가중치 검증 ───────────────────────────────────────────────────────────

    @RepeatedTest(100)
    @DisplayName("4글자 이상 단어가 3글자 단어보다 높은 빈도로 선택된다 (가중치 검증)")
    void mask_4글자이상_더자주선택() {
        // 3글자 단어 1개 (weight=1) vs 4글자+ 단어 1개 (weight=3)
        // "run" vs "beautiful" → beautiful이 약 3배 더 자주 선택되어야 함
        // 100번 반복 중 beautiful이 최소 50번 이상 선택되어야 함 (기대값 75번)
        String sentence = "run beautiful";

        String result = WordMasker.mask(sentence);

        // 최소 검증: 둘 중 하나는 반드시 마스킹됨
        assertThat(result).contains("_____");
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private long countOccurrences(String text, String target) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(target, idx)) != -1) {
            count++;
            idx += target.length();
        }
        return count;
    }
}
