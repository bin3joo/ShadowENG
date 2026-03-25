import json
import librosa
import numpy as np
import plotly.graph_objects as go
from plotly.subplots import make_subplots
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
from domain.prosody.feature_extraction import extract_prosody_features
from domain.processing.audio_processing import separate_vocals

def _normalize_f0_manual(f0: np.ndarray) -> np.ndarray:
    valid_f0 = f0[f0 > 0]
    if len(valid_f0) > 0:
        return np.where(f0 > 0, (f0 - np.mean(valid_f0)) / (np.std(valid_f0) + 1e-8), 0)
    return f0

def _normalize_rms_manual(rms: np.ndarray) -> np.ndarray:
    return (rms - np.mean(rms)) / (np.std(rms) + 1e-8)

def visualize_vr_comparison():
    # 1. 레퍼런스 데이터 로드
    ref_json_path = r"c:\Users\SSAFY\pjt\eng\S14P21A306\AI\test\result\NrO20Jb-hy0\meta\reference.json"
    with open(ref_json_path, 'r', encoding='utf-8') as f:
        ref_data = json.load(f)
    
    part3 = ref_data['parts'][2]
    ref_f0_raw = np.array(part3['features']['f0_array'])
    ref_rms_raw = np.array(part3['features']['rms_array'])
    
    # 2. 사용자 음성 준비 및 WAV 변환
    user_audio_pure_path = r"c:\Users\SSAFY\pjt\eng\S14P21A306\AI\시연.m4a"
    y_user, sr = librosa.load(user_audio_pure_path, sr=config.TARGET_SR)
    
    # 임시 WAV 파일 생성 (VR 입력을 위해)
    temp_dir = os.path.join(project_root, "temp")
    os.makedirs(temp_dir, exist_ok=True)
    temp_wav_path = os.path.join(temp_dir, "user_demo_temp.wav")
    wavfile.write(temp_wav_path, config.TARGET_SR, np.asarray(y_user, dtype=np.float32))
    
    # 3. 사용자 음성 VR 작업 수행
    vr_output_dir = os.path.join(temp_dir, "user_vr_test")
    os.makedirs(vr_output_dir, exist_ok=True)
    
    print(f"Starting VR separation (WAV input): {temp_wav_path}")
    vocal_only_path = separate_vocals(temp_wav_path, vr_output_dir)
    print(f"VR separation completed: {vocal_only_path}")
    
    # VR 처리된 결과물 피처 추출
    y_vr, _ = librosa.load(vocal_only_path, sr=config.TARGET_SR)
    user_f0_raw, user_rms_raw, _ = extract_prosody_features(y_vr, config.TARGET_SR, denoise=False)
    
    # 임시 WAV 정리 (선택사항)
    if os.path.exists(temp_wav_path):
        os.remove(temp_wav_path)
    
    # 4. 정규화
    ref_f0_norm = _normalize_f0_manual(ref_f0_raw)
    ref_rms_norm = _normalize_rms_manual(ref_rms_raw)
    ref_feats_norm = np.stack([ref_f0_norm, ref_rms_norm], axis=-1)

    user_f0_norm = _normalize_f0_manual(user_f0_raw)
    user_rms_norm = _normalize_rms_manual(user_rms_raw)
    user_feats_norm = np.stack([user_f0_norm, user_rms_norm], axis=-1)

    # 5. DTW 시간 매칭
    distance, path = fastdtw(ref_feats_norm, user_feats_norm, dist=euclidean, radius=config.PROSODY_DTW_RADIUS)
    
    ref_indices = [p[0] for p in path]
    user_indices = [p[1] for p in path]
    
    aligned_ref_f0 = ref_f0_norm[ref_indices]
    aligned_user_f0 = user_f0_norm[user_indices]
    aligned_ref_rms = ref_rms_norm[ref_indices]
    aligned_user_rms = user_rms_norm[user_indices]
    
    # 6. 시각화
    fig = make_subplots(
        rows=2, cols=2, 
        subplot_titles=(
            "Normalized F0 (VR Mode)", "Aligned F0 (VR + DTW)",
            "Normalized RMS (VR Mode)", "Aligned RMS (VR + DTW)"
        ),
        vertical_spacing=0.12,
        horizontal_spacing=0.1
    )

    steps = np.arange(len(path))

    fig.add_trace(go.Scatter(y=ref_f0_norm, name="Ref F0", line=dict(color='#3A86FF')), row=1, col=1)
    fig.add_trace(go.Scatter(y=user_f0_norm, name="User F0 (VR)", line=dict(color='#8338EC', dash='dot')), row=1, col=1)

    fig.add_trace(go.Scatter(x=steps, y=aligned_ref_f0, name="Ref F0 (Algn)", line=dict(color='#3A86FF'), showlegend=False), row=1, col=2)
    fig.add_trace(go.Scatter(x=steps, y=aligned_user_f0, name="User F0 (Algn)", line=dict(color='#8338EC', dash='dot'), showlegend=False), row=1, col=2)

    fig.add_trace(go.Scatter(y=ref_rms_norm, name="Ref RMS", line=dict(color='#38B000')), row=2, col=1)
    fig.add_trace(go.Scatter(y=user_rms_norm, name="User RMS (VR)", line=dict(color='#FB5607', dash='dot')), row=2, col=1)

    fig.add_trace(go.Scatter(x=steps, y=aligned_ref_rms, name="Ref RMS (Algn)", line=dict(color='#38B000'), showlegend=False), row=2, col=2)
    fig.add_trace(go.Scatter(x=steps, y=aligned_user_rms, name="User RMS (Algn)", line=dict(color='#FB5607', dash='dot'), showlegend=False), row=2, col=2)

    fig.update_layout(
        height=1000, width=1500, 
        title_text="User Audio VR (Vocal Remover) Aligned Comparison",
        template="plotly_white"
    )
    
    output_png = r"c:\Users\SSAFY\pjt\eng\S14P21A306\AI\test\comparison_vr_mode_aligned.png"
    fig.write_image(output_png)
    print(f"Visualization Saved (VR Aligned): {output_png}")

if __name__ == "__main__":
    visualize_vr_comparison()
