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

def visualize_aligned():
    # 1. 데이터 로드
    ref_json_path = r"c:\Users\SSAFY\pjt\eng\S14P21A306\AI\test\result\NrO20Jb-hy0\meta\reference.json"
    with open(ref_json_path, 'r', encoding='utf-8') as f:
        ref_data = json.load(f)
    
    part3 = ref_data['parts'][2]
    ref_f0 = np.array(part3['features']['f0_array'])
    ref_rms = np.array(part3['features']['rms_array'])
    
    user_audio_path = r"c:\Users\SSAFY\pjt\eng\S14P21A306\AI\시연.m4a"
    y, sr = librosa.load(user_audio_path, sr=config.TARGET_SR)
    user_f0, user_rms, user_feats = extract_prosody_features(y, sr)
    
    # 2. 레퍼런스 특징(Normalized) 재구성 (JSON에서 직접 가져오거나 추출 로직 사용)
    # JSON에는 원시값이 있으므로, 시각화를 위해 원시값을 DTW로 정렬함.
    # 하지만 DTW 계산 자체는 정규화된 특징으로 수행해야 더 정확함.
    # 레퍼런스 정규화 (feature_extraction.py의 _normalize_f0 사용)
    valid_f0 = ref_f0[ref_f0 > 0]
    ref_f0_norm = np.where(ref_f0 > 0, (ref_f0 - np.mean(valid_f0)) / (np.std(valid_f0) + 1e-8), 0) if len(valid_f0) > 0 else ref_f0
    ref_rms_norm = (ref_rms - np.mean(ref_rms)) / (np.std(ref_rms) + 1e-8)
    ref_feats = np.stack([ref_f0_norm, ref_rms_norm], axis=-1)

    # 3. DTW 시간 매칭 실행
    distance, path = fastdtw(ref_feats, user_feats, dist=euclidean, radius=config.PROSODY_DTW_RADIUS)
    
    # 정렬된 인덱스 추출
    ref_indices = [p[0] for p in path]
    user_indices = [p[1] for p in path]
    
    # 정렬된 배열 생성
    aligned_ref_f0 = ref_f0[ref_indices]
    aligned_user_f0 = user_f0[user_indices]
    aligned_ref_rms = ref_rms[ref_indices]
    aligned_user_rms = user_rms[user_indices]
    
    # 4. 시각화 (서부플롯 4개: 상단은 원본 시계열, 하단은 DTW 정렬 시계열)
    fig = make_subplots(
        rows=2, cols=2, 
        subplot_titles=(
            "Original F0 (Not Aligned)", "DTW Aligned F0",
            "Original RMS (Not Aligned)", "DTW Aligned RMS"
        ),
        vertical_spacing=0.12,
        horizontal_spacing=0.1
    )

    # 시간축 및 스텝축
    steps = np.arange(len(path))

    # Row 1-1: Original F0
    fig.add_trace(go.Scatter(y=ref_f0, name="Ref F0 (Raw)", line=dict(color='#3A86FF')), row=1, col=1)
    fig.add_trace(go.Scatter(y=user_f0, name="User F0 (Raw)", line=dict(color='#FF006E', dash='dot')), row=1, col=1)

    # Row 1-2: Aligned F0
    fig.add_trace(go.Scatter(x=steps, y=aligned_ref_f0, name="Ref F0 (Aligned)", line=dict(color='#3A86FF'), showlegend=False), row=1, col=2)
    fig.add_trace(go.Scatter(x=steps, y=aligned_user_f0, name="User F0 (Aligned)", line=dict(color='#FF006E', dash='dot'), showlegend=False), row=1, col=2)

    # Row 2-1: Original RMS
    fig.add_trace(go.Scatter(y=ref_rms, name="Ref RMS (Raw)", line=dict(color='#38B000')), row=2, col=1)
    fig.add_trace(go.Scatter(y=user_rms, name="User RMS (Raw)", line=dict(color='#FFBE0B', dash='dot')), row=2, col=1)

    # Row 2-2: Aligned RMS
    fig.add_trace(go.Scatter(x=steps, y=aligned_ref_rms, name="Ref RMS (Aligned)", line=dict(color='#38B000'), showlegend=False), row=2, col=2)
    fig.add_trace(go.Scatter(x=steps, y=aligned_user_rms, name="User RMS (Aligned)", line=dict(color='#FFBE0B', dash='dot'), showlegend=False), row=2, col=2)

    fig.update_layout(
        height=1000, width=1500, 
        title_text="Time Matching Analysis (Dynamic Time Warping)",
        template="plotly_white",
        legend=dict(orientation="h", yanchor="bottom", y=1.02, xanchor="right", x=1)
    )
    
    # 저장
    output_png = r"c:\Users\SSAFY\pjt\eng\S14P21A306\AI\test\comparison_aligned.png"
    fig.write_image(output_png)
    print(f"Aligned visualization saved successfully: {output_png}")

if __name__ == "__main__":
    visualize_aligned()
