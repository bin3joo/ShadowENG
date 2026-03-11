"""StyleEcho API 테스트 스크립트."""

import argparse
import json
import re
import sys
import time
from pathlib import Path

import requests

BASE_URL = "http://localhost:8000"
TEST_DIR = Path(__file__).resolve().parent
RESULT_DIR = TEST_DIR / "result"
RESULT_DIR.mkdir(parents=True, exist_ok=True)


def get_reference_result_dir(video_id: str) -> Path:
    """비디오별 테스트 결과 루트 디렉터리를 반환합니다."""
    return RESULT_DIR / video_id


def get_reference_meta_dir(video_id: str) -> Path:
    """비디오별 메타데이터 저장 디렉터리를 반환합니다."""
    return get_reference_result_dir(video_id) / "meta"


def get_reference_audio_dir(video_id: str) -> Path:
    """비디오별 레퍼런스 오디오 저장 디렉터리를 반환합니다."""
    return get_reference_result_dir(video_id) / "ref_audio"


def get_reference_json_path(video_id: str) -> Path:
    """비디오별 레퍼런스 JSON 경로를 반환합니다."""
    return get_reference_meta_dir(video_id) / "reference.json"


def get_reference_script_path(video_id: str) -> Path:
    """비디오별 검증용 스크립트 JSON 경로를 반환합니다."""
    return get_reference_meta_dir(video_id) / "script.json"


def resolve_reference_json_path(reference_input: str) -> Path | None:
    """현재 저장 구조 기준으로 평가용 reference.json 경로를 해석합니다."""
    stripped = reference_input.strip()
    if not stripped:
        return find_latest_reference_json()

    candidate = Path(stripped)

    if candidate.is_dir():
        if candidate.name == "meta":
            return candidate / "reference.json"
        nested_meta_reference = candidate / "meta" / "reference.json"
        if nested_meta_reference.exists():
            return nested_meta_reference
        return candidate / "reference.json"

    if candidate.name == "script.json":
        sibling_reference = candidate.with_name("reference.json")
        if sibling_reference.exists():
            return sibling_reference

    return candidate


def get_evaluate_result_path(reference_path: Path | None = None) -> Path:
    """평가 결과 JSON 저장 경로를 반환합니다."""
    if reference_path is not None and reference_path.parent.name == "meta":
        return reference_path.parent / "evaluate_result.json"
    latest_reference = find_latest_reference_json()
    if latest_reference is not None:
        return latest_reference.parent / "evaluate_result.json"
    return RESULT_DIR / "evaluate_result.json"


def find_latest_reference_json() -> Path | None:
    """가장 최근에 생성된 레퍼런스 JSON 파일을 찾습니다."""
    candidates = list(RESULT_DIR.glob("*/meta/reference.json"))
    if not candidates:
        return None
    return max(candidates, key=lambda path: path.stat().st_mtime)


def extract_video_id(value: str) -> str:
    """YouTube URL 또는 raw video_id 에서 video_id 를 추출합니다."""
    stripped = value.strip()
    if re.fullmatch(r"[A-Za-z0-9_-]{11}", stripped):
        return stripped

    patterns = [
        r"(?:v=)([A-Za-z0-9_-]{11})",
        r"youtu\.be/([A-Za-z0-9_-]{11})",
        r"youtube\.com/embed/([A-Za-z0-9_-]{11})",
        r"youtube\.com/shorts/([A-Za-z0-9_-]{11})",
    ]
    for pattern in patterns:
        match = re.search(pattern, stripped)
        if match:
            return match.group(1)

    raise ValueError(
        "video_id 또는 YouTube URL에서 유효한 11자리 video_id를 찾을 수 없습니다."
    )


def build_script_summary(data: dict) -> dict:
    """검증용 텍스트 중심 스크립트 요약을 생성합니다."""
    parts = data.get("parts", [])
    learning_expressions = data.get("learning_expressions", [])

    return {
        "status": data.get("status"),
        "video_id": data.get("video_id"),
        "stt_method": data.get("stt_method"),
        "translation_status": data.get("translation_status"),
        "translation_retry_count": data.get("translation_retry_count"),
        "translation_provider": data.get("translation_provider"),
        "final_script": data.get("final_script", ""),
        "final_script_ko": data.get("final_script_ko", ""),
        "parts": [
            {
                "part_index": index,
                "start_sec": part.get("start_sec"),
                "end_sec": part.get("end_sec"),
                "part_source": part.get("part_source"),
                "sentence": part.get("sentence", ""),
                "sentence_ko": part.get("sentence_ko", ""),
                "vocabulary": [
                    {
                        "word": vocabulary.get("word", ""),
                        "meaning_ko": vocabulary.get("meaning_ko", ""),
                        "phonetic_en": vocabulary.get("phonetic_en", ""),
                        "phonetic_ko": vocabulary.get("phonetic_ko", ""),
                        "example_en": vocabulary.get("example_en", ""),
                        "example_ko": vocabulary.get("example_ko", ""),
                    }
                    for vocabulary in part.get("vocabulary", [])
                ],
            }
            for index, part in enumerate(parts, start=1)
        ],
        "learning_expressions": [
            {
                "expression": expression.get("expression", ""),
                "meaning": expression.get("meaning", ""),
                "pronunciation_en": expression.get(
                    "pronunciation_en",
                    "",
                ),
                "pronunciation_ko": expression.get(
                    "pronunciation_ko",
                    "",
                ),
                "nuance_in_sentence": expression.get(
                    "nuance_in_sentence",
                    "",
                ),
                "example_en": expression.get("example_en", ""),
                "example_ko": expression.get("example_ko", ""),
            }
            for expression in learning_expressions
        ],
    }


def test_generate_reference(
    video_id_or_url: str,
    start_sec: float,
    end_sec: float,
) -> str | None:
    """generate-reference 호출 후 결과를 test/result 에 저장합니다."""
    url = f"{BASE_URL}/api/v1/generate-reference"
    try:
        video_id = extract_video_id(video_id_or_url)
    except ValueError as exc:
        print(f"❌ {exc}")
        return None

    reference_result_dir = get_reference_result_dir(video_id)
    reference_meta_dir = get_reference_meta_dir(video_id)
    reference_audio_dir = get_reference_audio_dir(video_id)
    reference_meta_dir.mkdir(parents=True, exist_ok=True)
    reference_result_path = get_reference_json_path(video_id)
    reference_script_path = get_reference_script_path(video_id)

    payload = {
        "video_id": video_id,
        "start_sec": start_sec,
        "end_sec": end_sec,
        "save_dir": str(reference_audio_dir),
    }

    print("=" * 60)
    print("📡 POST /api/v1/generate-reference")
    print(f"   video_id  : {video_id}")
    print(f"   구간      : {start_sec}s ~ {end_sec}s")
    print("=" * 60)

    try:
        resp = requests.post(url, json=payload, timeout=180)
    except requests.ConnectionError:
        print("❌ 서버 연결 실패. uvicorn이 실행 중인지 확인하세요.")
        print("   uvicorn main:app --host 0.0.0.0 --port 8000")
        sys.exit(1)

    print(f"\n📥 HTTP {resp.status_code}")

    if resp.status_code != 200:
        print(f"❌ 실패: {resp.text[:500]}")
        return None

    data = resp.json()
    reference_result_dir.mkdir(parents=True, exist_ok=True)

    print(f"\n✅ status      : {data.get('status')}")
    print(f"   video_id    : {data.get('video_id', 'N/A')}")
    print(f"   stt_method  : {data.get('stt_method', 'N/A')}")
    print(f"   final_script: {data.get('final_script', '')[:100]}...")
    print(f"   trimmed     : {data.get('trimmed_word_count', 0)}개 제거됨")
    print(f"   pause_count : {data.get('pause_count', 0)}")
    print(f"   word_count  : {data.get('word_count', 0)}")
    print(f"   quality     : {data.get('reference_quality', 'N/A')}")
    print(f"   reasons     : {data.get('quality_reasons', [])}")
    print(f"   warnings    : {data.get('warnings', [])}")
    print(f"   denoise     : {data.get('denoise_mode', 'N/A')}")
    print(f"   speaker     : {data.get('speaker_mode', 'N/A')}")
    print(f"   dialog      : {data.get('dialog_mode', 'N/A')}")
    print(f"   turn_count  : {data.get('turn_count', 0)}")
    print(f"   caption     : {data.get('caption_source', 'N/A')}")
    print(f"   align_med   : {data.get('alignment_median_score', 0.0)}")
    print(f"   align_low   : {data.get('low_alignment_ratio', 0.0)}")
    print(f"   overlap     : {data.get('overlap_risk_ratio', 0.0)}")
    print(f"   shift       : {data.get('speaker_shift_ratio', 0.0)}")
    print(f"   noise_level : {data.get('noise_level', 'N/A')}")
    print(f"   snr_db      : {data.get('estimated_snr_db', 0.0)}")
    print(f"   speech_ratio: {data.get('speech_ratio', 0.0)}")
    print(f"   diarization : {data.get('diarization_used', False)}")
    print(f"   speakers    : {data.get('detected_speaker_count', 0)}")
    print(f"   full_audio  : {data.get('full_audio_path', 'N/A')}")
    print(f"   trans_stat  : {data.get('translation_status', 'N/A')}")
    print(f"   trans_retry : {data.get('translation_retry_count', 0)}")
    print(f"   trans_prov  : {data.get('translation_provider', 'N/A')}")
    print(f"   final_ko    : {str(data.get('final_script_ko', ''))[:100]}...")

    parts = data.get("parts", [])
    total_words = sum(len(part.get("word_timestamps", [])) for part in parts)
    expressions = data.get("learning_expressions", [])
    print(f"   총 단어 수  : {total_words}")
    print(f"   파트 수     : {len(parts)}")
    print(f"   표현 수     : {len(expressions)}")
    for index, part in enumerate(parts, start=1):
        feat = part.get("features", {})
        f0_len = len(feat.get("f0_array", []))
        rms_len = len(feat.get("rms_array", []))
        wt_len = len(part.get("word_timestamps", []))
        print(
            f"     [Part {index}] {part.get('difficulty', '?'):6s} | "
            f"{part.get('start_sec', 0):.1f}s~{part.get('end_sec', 0):.1f}s | "
            f"WPM={part.get('wpm', 0):.0f} | "
            f"F0={f0_len} RMS={rms_len} Words={wt_len} | "
            f"Src={part.get('part_source', 'sentence')} "
            f"Pause={part.get('pause_count', 0)} "
            f"SpkRisk={part.get('speaker_risk', 'low')} "
            f"Dom={part.get('dominant_speaker', '-') or '-'} "
            f"Cnt={part.get('speaker_count', 0)} | "
            f"{part.get('sentence', '')[:50]}"
        )
        if part.get("sentence_ko"):
            print(f"               KO={part.get('sentence_ko', '')[:70]}")
        vocabulary_items = part.get("vocabulary", [])
        if vocabulary_items:
            print(f"               VOCAB={len(vocabulary_items)}개")
            for vocabulary in vocabulary_items:
                print(
                    "               "
                    f"- {vocabulary.get('word', '')} "
                    f"| {vocabulary.get('meaning_ko', '')} "
                    f"| {vocabulary.get('phonetic_ko', '')}"
                )

    if expressions:
        print("\n📚 학습 표현:")
        for expression in expressions:
            print(
                "   "
                f"- {expression.get('expression', '')}: "
                f"{expression.get('meaning', '')}"
            )
            pronunciation_ko = expression.get("pronunciation_ko", "")
            if pronunciation_ko:
                print(f"     발음  : {pronunciation_ko}")
            nuance = expression.get("nuance_in_sentence", "")
            if nuance:
                print(f"     뉘앙스: {nuance}")

    with open(reference_result_path, "w", encoding="utf-8") as file_obj:
        json.dump(data, file_obj, ensure_ascii=False, indent=2)
    print(f"\n💾 레퍼런스 JSON 저장 완료: {reference_result_path}")

    script_summary = build_script_summary(data)
    with open(reference_script_path, "w", encoding="utf-8") as file_obj:
        json.dump(script_summary, file_obj, ensure_ascii=False, indent=2)
    print(f"💾 검증용 스크립트 JSON 저장 완료: {reference_script_path}")

    return str(reference_result_path)


def test_evaluate_audio(
    reference_json_path: str,
    user_audio_path: str,
    part_index: int | None = None,
) -> None:
    """evaluate-audio 호출 후 결과를 test/result 에 저장합니다."""
    url = f"{BASE_URL}/api/v1/evaluate-audio"
    reference_path = resolve_reference_json_path(reference_json_path)
    user_audio = Path(user_audio_path)

    if reference_path is None or not reference_path.exists():
        print(f"❌ 레퍼런스 JSON 파일이 없습니다: {reference_path}")
        latest_reference = find_latest_reference_json()
        if latest_reference is not None:
            print(f"   가장 최근 레퍼런스: {latest_reference}")
        print("   먼저 'python -m test.test_api generate ...' 를 실행하세요.")
        sys.exit(1)

    if not user_audio.exists():
        print(f"❌ 유저 오디오 파일이 없습니다: {user_audio}")
        sys.exit(1)

    with open(reference_path, "r", encoding="utf-8") as file_obj:
        ref_data = json.load(file_obj)

    if reference_path.name == "script.json":
        print("❌ eval 입력으로 script.json은 사용할 수 없습니다.")
        print(
            "   feature / word_timestamps가 포함된 reference.json을 사용하세요."
        )
        sys.exit(1)

    if not ref_data.get("parts"):
        print("❌ 평가용 reference 데이터에 parts가 없습니다.")
        print(f"   reference.json 경로를 확인하세요: {reference_path}")
        sys.exit(1)

    if part_index is not None:
        parts = ref_data.get("parts", [])
        if part_index < 1 or part_index > len(parts):
            print(
                f"❌ Part {part_index} 없음. "
                f"전체 {len(parts)}개 파트 중 1~{len(parts)} 선택 가능"
            )
            sys.exit(1)
        part = parts[part_index - 1]
        ref_data = {
            "final_script": part["sentence"],
            "features": part.get("features", {}),
            "word_timestamps": part.get("word_timestamps", []),
        }
        print(f"🎯 Part {part_index} 선택: {part['sentence'][:60]}...")
        print(f"   구간: {part['start_sec']:.1f}s ~ {part['end_sec']:.1f}s")
    else:
        parts = ref_data.get("parts", [])
        all_words: list[dict] = []
        all_f0: list[float] = []
        all_rms: list[float] = []
        for part in parts:
            all_words.extend(part.get("word_timestamps", []))
            all_f0.extend(part.get("features", {}).get("f0_array", []))
            all_rms.extend(part.get("features", {}).get("rms_array", []))
        ref_data = {
            "final_script": ref_data.get("final_script", ""),
            "features": {"f0_array": all_f0, "rms_array": all_rms},
            "word_timestamps": all_words,
        }

    print("=" * 60)
    print("📡 POST /api/v1/evaluate-audio")
    print(f"   reference : {reference_path}")
    print(f"   user_audio: {user_audio}")
    if part_index:
        print(f"   part      : {part_index}")
    print("=" * 60)

    try:
        import base64

        with open(user_audio, "rb") as audio_file:
            audio_b64 = base64.b64encode(audio_file.read()).decode("ascii")

        audio_ext = user_audio.suffix.lstrip(".")
        payload = {
            "user_audio": audio_b64,
            "user_audio_format": audio_ext or "wav",
            **ref_data,
        }
        resp = requests.post(url, json=payload, timeout=180)
    except requests.ConnectionError:
        print("❌ 서버 연결 실패.")
        sys.exit(1)

    print(f"\n📥 HTTP {resp.status_code}")

    if resp.status_code != 200:
        print(f"❌ 실패: {resp.text[:500]}")
        return

    data = resp.json()

    print(f"\n✅ status          : {data.get('status')}")

    if data.get("status") == "FAIL":
        print(f"   message         : {data.get('message', '')}")
        return

    print(f"   pass_fail       : {data.get('pass_fail', 'N/A')}")
    print(f"   pass_threshold  : {data.get('pass_threshold', 'N/A')}")
    print(
        f"   user_transcript : {data.get('user_transcription', '')[:100]}..."
    )

    scores = data.get("scores", {})
    print("\n📊 채점 결과:")
    print(f"   🏆 총점           : {scores.get('total_score', 0):.1f}")
    print(f"   📝 단어 정확도    : {scores.get('word_accuracy', 0):.1f}")
    print(f"   🎵 억양+강세      : {scores.get('prosody_and_stress', 0):.1f}")
    print(f"   🥁 단어 리듬      : {scores.get('word_rhythm_score', 0):.1f}")
    print(f"   📈 종결 억양      : {scores.get('boundary_tone_score', 0):.1f}")
    print(
        f"   📊 역동성         : {scores.get('dynamic_stress_score', 0):.1f}"
    )
    print(f"   ⏱️  속도 유사도    : {scores.get('speed_similarity', 0):.1f}")
    print(f"   ⏸️  멈춤 유사도    : {scores.get('pause_similarity', 0):.1f}")

    details = data.get("details", {})
    word_fb = details.get("word_level_feedback", [])
    if word_fb:
        rushed = sum(1 for word in word_fb if word.get("status") == "rushed")
        dragged = sum(1 for word in word_fb if word.get("status") == "dragged")
        missed = sum(1 for word in word_fb if word.get("status") == "missed")
        good = sum(1 for word in word_fb if word.get("status") == "good")
        print(
            f"\n📋 단어 피드백: good={good}, rushed={rushed}, dragged={dragged}, missed={missed}"
        )

    evaluate_result_path = get_evaluate_result_path(reference_path)
    evaluate_result_path.parent.mkdir(parents=True, exist_ok=True)
    with open(evaluate_result_path, "w", encoding="utf-8") as file_obj:
        json.dump(data, file_obj, ensure_ascii=False, indent=2)
    print(f"\n💾 평가 결과 JSON 저장: {evaluate_result_path}")


def main() -> None:
    """CLI 진입점을 실행합니다."""
    start = time.time()
    default_reference_path = find_latest_reference_json()
    parser = argparse.ArgumentParser(
        description="StyleEcho API 테스트 (localhost:8000)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""사용 예시:
  python -m test.test_api generate YOUR_VIDEO_ID 30.0 45.0
  python -m test.test_api generate "YOUR_YOUTUBE_URL" 30.0 45.0

  python -m test.test_api evaluate "./my_recording.wav"
  python -m test.test_api evaluate "./my_recording.wav" --ref "./test/result/VIDEO_ID/meta/reference.json"
  python -m test.test_api evaluate "./my_recording.wav" --ref "./test/result/VIDEO_ID/meta"
  python -m test.test_api evaluate "./my_recording.wav" --ref "./test/result/VIDEO_ID"
""",
    )

    subparsers = parser.add_subparsers(dest="command", required=True)

    gen_parser = subparsers.add_parser(
        "generate", help="generate-reference 테스트"
    )
    gen_parser.add_argument(
        "video_id",
        help="YouTube video_id 또는 YouTube URL",
    )
    gen_parser.add_argument("start_sec", type=float, help="시작 시간 (초)")
    gen_parser.add_argument("end_sec", type=float, help="종료 시간 (초)")

    eval_parser = subparsers.add_parser(
        "evaluate", help="evaluate-audio 테스트"
    )
    eval_parser.add_argument(
        "user_audio", help="유저 녹음 파일 경로 (.wav/.m4a)"
    )
    eval_parser.add_argument(
        "--ref",
        default=str(default_reference_path) if default_reference_path else "",
        help=(
            "레퍼런스 기준 경로(reference.json, meta 디렉터리, video_id 결과 디렉터리) "
            f"(기본: {default_reference_path.as_posix() if default_reference_path else '최근 생성 레퍼런스 없음'})"
        ),
    )
    eval_parser.add_argument(
        "--part",
        type=int,
        default=None,
        help="평가할 파트 번호 (1-based). 생략 시 전체 섹션 평가",
    )

    args = parser.parse_args()

    if args.command == "generate":
        test_generate_reference(args.video_id, args.start_sec, args.end_sec)
    elif args.command == "evaluate":
        test_evaluate_audio(args.ref, args.user_audio, args.part)

    end_time = time.time()
    print(f"\n⏱️  총 실행 시간: {end_time - start:.2f}초")


if __name__ == "__main__":
    main()
