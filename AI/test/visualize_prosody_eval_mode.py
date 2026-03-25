import json
import librosa
import numpy as np
import plotly.graph_objects as go
import os
import sys
import tempfile
from scipy.io import wavfile
from fastdtw import fastdtw
from scipy.spatial.distance import euclidean

# 프로젝트 루트 영역 추가
project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.append(project_root)

import config
from domain.prosody.feature_extraction import (
    extract_prosody_features,
    _apply_voiced_mask,
    _apply_f0_median_filter,
    _normalize_f0,
    _normalize_rms
)
from domain.processing.audio_processing import separate_vocals

# =================================================================
# [시각화 스타일 핵심 설정 블록 - Soft Pastel Version]
# =================================================================
VIZ_CONFIG = {
    # ── 1. 기본 규격 & 폰트 ──
    "WIDTH_PX": 1200,              
    "HEIGHT_PX": 600,              
    "DPI_SCALE": 1.5,              
    "FONT_FAMILY": "Outfit, Pretendard, sans-serif",  # 둥근 느낌의 Outfit 폰트 선호
    "TITLE_SIZE": 22,              
    "AXIS_LABEL_SIZE": 14,         
    "TICK_SIZE": 12,               

    # ── 2. 선(Line) 및 채우기(Fill) 스타일 ──
    "LINE_WIDTH": 3.0,             
    "REF_LINE_STYLE": "solid",     
    "USER_LINE_STYLE": "dash",     
    "REF_FILL_ALPHA": 0.12,        # 🌟 파스텔 톤에 어울리는 아주 연한 채우기
    "USER_FILL_ALPHA": 0.08,       

    # ── 3. 컬러 팔레트 (Soft Pastel) ──
    "BG_COLOR": "#FDFCFB",         # 🎨 부드러운 오프화이트 베이지
    "TEXT_COLOR": "#5A5A5A",       # 🎨 부드러운 다크 그레이
    "REF_COLOR": "#7AA2E3",        # 💎 파스텔 스카이 블루 (Ref)
    "USER_F0_COLOR": "#F2A7AD",    # 💖 파스텔 스트로베리 핑크 (User F0)
    "REF_RMS_COLOR": "#97BE5A",    # 🔋 파스텔 애플 그린 (Ref RMS)
    "USER_RMS_COLOR": "#FFCF96",   # 🟡 파스텔 살구 오렌지 (User RMS)

    # ── 4. 격자(Grid) 및 범례(Legend) ──
    "GRID_COLOR": "#F1F1F1",       # 🎨 눈에 거의 띄지 않는 아주 연한 격자
    "GRID_ALPHA": 0.4,             
    "LEGEND_LOC": dict(yanchor="top", y=0.98, xanchor="right", x=0.98, font=dict(color="#5A5A5A", size=14), bgcolor="rgba(255,255,255,0.6)")
}
# =================================================================

def _hex_to_rgba(hex_color, alpha):
    hex_color = hex_color.lstrip('#')
    lv = len(hex_color)
    rgb = tuple(int(hex_color[i:i + lv // 3], 16) for i in range(0, lv, lv // 3))
    return f"rgba({rgb[0]}, {rgb[1]}, {rgb[2]}, {alpha})"

def save_premium_single_plot(fig, title, filename, output_dir):
    """Pastel Mode 디자인이 적용된 단일 플롯 저장"""
    fig.update_layout(
        title=dict(
            text=f"<b>{title}</b>", 
            font=dict(size=VIZ_CONFIG["TITLE_SIZE"], family=VIZ_CONFIG["FONT_FAMILY"], color=VIZ_CONFIG["TEXT_COLOR"]),
            x=0.05,
            y=0.95
        ),
        template="plotly_white",      # 🌟 밝은 템플릿
        width=VIZ_CONFIG["WIDTH_PX"],
        height=VIZ_CONFIG["HEIGHT_PX"],
        paper_bgcolor=VIZ_CONFIG["BG_COLOR"],
        plot_bgcolor=VIZ_CONFIG["BG_COLOR"],
        font=dict(family=VIZ_CONFIG["FONT_FAMILY"], color=VIZ_CONFIG["TEXT_COLOR"]),
        margin=dict(l=60, r=40, t=80, b=60),
        legend=VIZ_CONFIG["LEGEND_LOC"]
    )
    
    fig.update_xaxes(
        showgrid=True, 
        gridwidth=1, 
        gridcolor=VIZ_CONFIG["GRID_COLOR"],
        tickfont=dict(size=VIZ_CONFIG["TICK_SIZE"], color=VIZ_CONFIG["TEXT_COLOR"]),
        zeroline=False
    )
    fig.update_yaxes(
        showgrid=True, 
        gridwidth=1, 
        gridcolor=VIZ_CONFIG["GRID_COLOR"],
        tickfont=dict(size=VIZ_CONFIG["TICK_SIZE"], color=VIZ_CONFIG["TEXT_COLOR"]),
        zeroline=False
    )
    
    path = os.path.join(output_dir, filename)
    fig.write_image(path, scale=VIZ_CONFIG["DPI_SCALE"])
    print(f"🧁 Saved (Pastel): {filename}")

def visualize_eval_comparison():
    # 0. 폴더 준비
    output_dir = os.path.join(project_root, "test", "plots_individual")
    os.makedirs(output_dir, exist_ok=True)

    # 1. 데이터 로드
    ref_dir = os.path.join(project_root, "test", "result", "NrO20Jb-hy0", "meta")
    ref_json_path = os.path.join(ref_dir, "reference.json")
    eval_json_path = os.path.join(ref_dir, "evaluate_result.json")

    if not os.path.exists(ref_json_path) or not os.path.exists(eval_json_path):
        print(f"Error: Required JSON files not found.")
        return

    with open(ref_json_path, 'r', encoding='utf-8') as f:
        ref_data = json.load(f)
    with open(eval_json_path, 'r', encoding='utf-8') as f:
        eval_result = json.load(f)
    
    part3 = ref_data['parts'][2]
    ref_f0_raw = np.array(part3['features']['f0_array'])
    ref_rms_raw = np.array(part3['features']['rms_array'])
    ref_words = part3.get('words', [])
    
    target_sr = config.TARGET_SR
    hop_length = config.HOP_LENGTH
    
    # 2. [Cropping - Reference]
    ref_f0_t, ref_rms_t = ref_f0_raw, ref_rms_raw
    if ref_words:
        part_audio_start = part3['start']
        ridx_s = int((ref_words[0]["start"] - part_audio_start) * target_sr / hop_length)
        ridx_e = int((ref_words[-1]["end"] - part_audio_start) * target_sr / hop_length)
        ref_f0_t = ref_f0_raw[ridx_s:ridx_e]
        ref_rms_t = ref_rms_raw[ridx_s:ridx_e]

    # 3. [Cropping - User]
    word_feedback = eval_result.get("details", {}).get("word_level_feedback", [])
    user_start_time = next((w["user_start_time"] for w in word_feedback if w.get("user_start_time") is not None), None)
    user_end_time = next((w["user_end_time"] for w in reversed(word_feedback) if w.get("user_end_time") is not None), None)
    
    user_audio_path = os.path.join(project_root, "시연.m4a")
    user_y_raw, _ = librosa.load(user_audio_path, sr=target_sr)

    if user_start_time is not None and user_end_time is not None:
        u_idx_s = int(user_start_time * target_sr)
        u_idx_e = int(user_end_time * target_sr)
        user_y_cropped = user_y_raw[u_idx_s:u_idx_e]
        print(f"✅ Using robust user crop: {user_start_time}s ~ {user_end_time}s")
    else:
        user_y_cropped = user_y_raw
        print("⚠️ No matched words found. Using full audio for visualization.")

    # 4. 피처 추출
    user_f0_raw, user_rms_raw, _ = extract_prosody_features(user_y_cropped, target_sr, denoise=True)
    
    # 5. 정규화
    ref_f0_processed = _apply_f0_median_filter(_apply_voiced_mask(ref_f0_t, None), None)
    ref_f0_norm = _normalize_f0(ref_f0_processed)
    ref_rms_norm = _normalize_rms(ref_rms_t)
    user_f0_norm = _normalize_f0(user_f0_raw)
    user_rms_norm = _normalize_rms(user_rms_raw)

    # 6. DTW 정렬
    ref_feats = np.stack([ref_f0_norm, ref_rms_norm], axis=-1)
    user_feats = np.stack([user_f0_norm, user_rms_norm], axis=-1)
    _, path = fastdtw(ref_feats, user_feats, dist=euclidean, radius=config.PROSODY_DTW_RADIUS)
    ridx, uidx = [p[0] for p in path], [p[1] for p in path]
    a_ref_f0, a_user_f0 = ref_f0_norm[ridx], user_f0_norm[uidx]
    a_ref_rms, a_user_rms = ref_rms_norm[ridx], user_rms_norm[uidx]

    # 7. 개별 파일 저장 (Pastel Version)
    fig_ref_f0 = go.Figure(go.Scatter(y=ref_f0_norm, name="Ref Pitch", fill='tozeroy', fillcolor=_hex_to_rgba(VIZ_CONFIG["REF_COLOR"], VIZ_CONFIG["REF_FILL_ALPHA"]), line=dict(color=VIZ_CONFIG["REF_COLOR"], width=VIZ_CONFIG["LINE_WIDTH"])))
    save_premium_single_plot(fig_ref_f0, "Native Pitch Contour", "ref_f0_raw.png", output_dir)

    fig_user_f0 = go.Figure(go.Scatter(y=user_f0_norm, name="User Pitch", fill='tozeroy', fillcolor=_hex_to_rgba(VIZ_CONFIG["USER_F0_COLOR"], VIZ_CONFIG["USER_FILL_ALPHA"]), line=dict(color=VIZ_CONFIG["USER_F0_COLOR"], width=VIZ_CONFIG["LINE_WIDTH"], dash=VIZ_CONFIG["USER_LINE_STYLE"])))
    save_premium_single_plot(fig_user_f0, "Learner Pitch Contour", "user_f0_raw.png", output_dir)

    fig_ref_rms = go.Figure(go.Scatter(y=ref_rms_norm, name="Ref Intensity", fill='tozeroy', fillcolor=_hex_to_rgba(VIZ_CONFIG["REF_RMS_COLOR"], VIZ_CONFIG["REF_FILL_ALPHA"]), line=dict(color=VIZ_CONFIG["REF_RMS_COLOR"], width=VIZ_CONFIG["LINE_WIDTH"])))
    save_premium_single_plot(fig_ref_rms, "Native Speaker Intensity", "ref_rms_raw.png", output_dir)

    fig_user_rms = go.Figure(go.Scatter(y=user_rms_norm, name="User Intensity", fill='tozeroy', fillcolor=_hex_to_rgba(VIZ_CONFIG["USER_RMS_COLOR"], VIZ_CONFIG["USER_FILL_ALPHA"]), line=dict(color=VIZ_CONFIG["USER_RMS_COLOR"], width=VIZ_CONFIG["LINE_WIDTH"], dash=VIZ_CONFIG["USER_LINE_STYLE"])))
    save_premium_single_plot(fig_user_rms, "Learner Intensity Trace", "user_rms_raw.png", output_dir)

    fig_f0_algn = go.Figure()
    fig_f0_algn.add_trace(go.Scatter(y=a_ref_f0, name="Ref", fill='tozeroy', fillcolor=_hex_to_rgba(VIZ_CONFIG["REF_COLOR"], VIZ_CONFIG["REF_FILL_ALPHA"]), line=dict(color=VIZ_CONFIG["REF_COLOR"], width=VIZ_CONFIG["LINE_WIDTH"])))
    fig_f0_algn.add_trace(go.Scatter(y=a_user_f0, name="User", fill='tozeroy', fillcolor=_hex_to_rgba(VIZ_CONFIG["USER_F0_COLOR"], VIZ_CONFIG["USER_FILL_ALPHA"]), line=dict(color=VIZ_CONFIG["USER_F0_COLOR"], width=VIZ_CONFIG["LINE_WIDTH"], dash=VIZ_CONFIG["USER_LINE_STYLE"])))
    save_premium_single_plot(fig_f0_algn, "Pitch Synchronization Analysis", "f0_alignment_comparison.png", output_dir)

    fig_rms_algn = go.Figure()
    fig_rms_algn.add_trace(go.Scatter(y=a_ref_rms, name="Ref", fill='tozeroy', fillcolor=_hex_to_rgba(VIZ_CONFIG["REF_RMS_COLOR"], VIZ_CONFIG["REF_FILL_ALPHA"]), line=dict(color=VIZ_CONFIG["REF_RMS_COLOR"], width=VIZ_CONFIG["LINE_WIDTH"])))
    fig_rms_algn.add_trace(go.Scatter(y=a_user_rms, name="User", fill='tozeroy', fillcolor=_hex_to_rgba(VIZ_CONFIG["USER_RMS_COLOR"], VIZ_CONFIG["USER_FILL_ALPHA"]), line=dict(color=VIZ_CONFIG["USER_RMS_COLOR"], width=VIZ_CONFIG["LINE_WIDTH"], dash=VIZ_CONFIG["USER_LINE_STYLE"])))
    save_premium_single_plot(fig_rms_algn, "Stress Alignment Comparison", "rms_alignment_comparison.png", output_dir)

    print(f"\n🚀 Soft Pastel premium plots generated at: {output_dir}")

if __name__ == "__main__":
    visualize_eval_comparison()
