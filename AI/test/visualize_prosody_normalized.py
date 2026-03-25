import json
import librosa
import numpy as np
import plotly.graph_objects as go
from plotly.subplots import make_subplots
import os
import sys
from fastdtw import fastdtw
from scipy.spatial.distance import euclidean

# 프로젝트 루트를 sys.path에 추가하여 config 및 domain 모듈을 가져올 수 있도록 함
project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.append(project_root)

import config
from domain.prosody.feature_extraction import extract_prosody_features

def _normalize_f0_manual(f0: np.ndarray) -> np.ndarray:
    """유성음 구간 기준으로 F0 값을 정규화합니다. (aggregator.py 로직과 동일)"""
    valid_f0 = f0[f0 > 0]
    if len(valid_f0) > 0:
        return np.where(
            f0 > 0,
            (f0 - np.mean(valid_f0)) / (np.std(valid_f0) + 1e-8),
            0,
        )
    return f0

def _normalize_rms_manual(rms: np.ndarray) -> np.ndarray:
    """RMS 값을 Z-score 정규화합니다."""
    return (rms - np.mean(rms)) / (np.std(rms) + 1e-8)

def visualize_normalized_aligned():
    # 1. 데이터 로드
    ref_json_path = r"c:\Users\SSAFY\pjt\eng\S14P21A306\AI\test\result\NrO20Jb-hy0\meta\reference.json"
    with open(ref_json_path, 'r', encoding='utf-8') as f:
        ref_data = json.load(f)
    
    part3 = ref_data['parts'][2]
    ref_f0_raw = np.array(part3['features']['f0_array'])
    ref_rms_raw = np.array(part3['features']['rms_array'])
    
    user_audio_path = r"c:\Users\SSAFY\pjt\eng\S14P21A306\AI\시연.m4a"
    y, sr = librosa.load(user_audio_path, sr=config.TARGET_SR)
    user_f0_raw, user_rms_raw, user_feats_norm = extract_prosody_features(y, sr)
    
    # 2. 레퍼런스 데이터 정규화 (채점 로직과 동일하게 적용)
    ref_f0_norm = _normalize_f0_manual(ref_f0_raw)
    ref_rms_norm = _normalize_rms_manual(ref_rms_raw)
    ref_feats_norm = np.stack([ref_f0_norm, ref_rms_norm], axis=-1)

    # 유저 데이터 정규화 (추출 시 이미 되어 있지만 명시적으로 확인)
    user_f0_norm = _normalize_f0_manual(user_f0_raw)
    user_rms_norm = _normalize_rms_manual(user_rms_raw)

    # 3. DTW 시간 매칭 실행 (정규화된 특징 기준)
    distance, path = fastdtw(ref_feats_norm, user_feats_norm, dist=euclidean, radius=config.PROSODY_DTW_RADIUS)
    
    # 정렬된 인덱스 및 배열 생성
    ref_indices = [p[0] for p in path]
    user_indices = [p[1] for p in path]
    
    aligned_ref_f0 = ref_f0_norm[ref_indices]
    aligned_user_f0 = user_f0_norm[user_indices]
    aligned_ref_rms = ref_rms_norm[ref_indices]
    aligned_user_rms = user_rms_norm[user_indices]
    
    # 4. 시각화 (정규화된 데이터 기준)
    fig = make_subplots(
        rows=2, cols=2, 
        subplot_titles=(
            "Normalized F0 (Time Scaled)", "Aligned Normalized F0 (DTW)",
            "Normalized RMS (Time Scaled)", "Aligned Normalized RMS (DTW)"
        ),
        vertical_spacing=0.12,
        horizontal_spacing=0.1
    )

    steps = np.arange(len(path))

    # Row 1-1: Normalized F0
    fig.add_trace(go.Scatter(y=ref_f0_norm, name="Ref F0 (Norm)", line=dict(color='#3A86FF')), row=1, col=1)
    fig.add_trace(go.Scatter(y=user_f0_norm, name="User F0 (Norm)", line=dict(color='#FF006E', dash='dot')), row=1, col=1)

    # Row 1-2: Aligned Normalized F0
    fig.add_trace(go.Scatter(x=steps, y=aligned_ref_f0, name="Ref F0 (Aligned)", line=dict(color='#3A86FF'), showlegend=False), row=1, col=2)
    fig.add_trace(go.Scatter(x=steps, y=aligned_user_f0, name="User F0 (Aligned)", line=dict(color='#FF006E', dash='dot'), showlegend=False), row=1, col=2)

    # Row 2-1: Normalized RMS
    fig.add_trace(go.Scatter(y=ref_rms_norm, name="Ref RMS (Norm)", line=dict(color='#38B000')), row=2, col=1)
    fig.add_trace(go.Scatter(y=user_rms_norm, name="User RMS (Norm)", line=dict(color='#FFBE0B', dash='dot')), row=2, col=1)

    # Row 2-2: Aligned Normalized RMS
    fig.add_trace(go.Scatter(x=steps, y=aligned_ref_rms, name="Ref RMS (Aligned)", line=dict(color='#38B000'), showlegend=False), row=2, col=2)
    fig.add_trace(go.Scatter(x=steps, y=aligned_user_rms, name="User RMS (Aligned)", line=dict(color='#FFBE0B', dash='dot'), showlegend=False), row=2, col=2)

    fig.update_layout(
        height=1000, width=1500, 
        title_text="Normalized Prosody Analysis (Internal Scoring Scale)",
        template="plotly_white",
        legend=dict(orientation="h", yanchor="bottom", y=1.02, xanchor="right", x=1)
    )
    
    # Y축 범위 조정 (Z-score이므로 보통 -3 ~ 3 사이)
    fig.update_yaxes(title_text="Z-Score", row=1, col=1)
    fig.update_yaxes(title_text="Z-Score", row=2, col=1)

    # 저장
    output_png = r"c:\Users\SSAFY\pjt\eng\S14P21A306\AI\test\comparison_normalized.png"
    fig.write_image(output_png)
    print(f"Normalized visualization saved successfully: {output_png}")

if __name__ == "__main__":
    visualize_normalized_aligned()
