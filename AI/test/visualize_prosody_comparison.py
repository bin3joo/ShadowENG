import json
import librosa
import numpy as np
import plotly.graph_objects as go
from plotly.subplots import make_subplots
import os
import sys

# 프로젝트 루트를 sys.path에 추가하여 config 및 domain 모듈을 가져올 수 있도록 함
project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.append(project_root)

import config
from domain.prosody.feature_extraction import extract_prosody_features

def visualize():
    # 1. 레퍼런스 데이터 로드 (NrO20Jb-hy0, Part 3)
    ref_json_path = r"c:\Users\SSAFY\pjt\eng\S14P21A306\AI\test\result\NrO20Jb-hy0\meta\reference.json"
    with open(ref_json_path, 'r', encoding='utf-8') as f:
        ref_data = json.load(f)
    
    # "parts"의 3번째 요소 (Part 3)
    part3 = ref_data['parts'][2]
    ref_f0 = np.array(part3['features']['f0_array'])
    ref_rms = np.array(part3['features']['rms_array'])
    
    # 2. 사용자 시연 오디오 추출
    user_audio_path = r"c:\Users\SSAFY\pjt\eng\S14P21A306\AI\시연.m4a"
    y, sr = librosa.load(user_audio_path, sr=config.TARGET_SR)
    user_f0, user_rms, _ = extract_prosody_features(y, sr)
    
    # 3. Plotly 서브플롯 생성
    fig = make_subplots(
        rows=2, cols=1, 
        subplot_titles=("F0 (Pitch) Comparison", "RMS (Energy) Comparison"),
        vertical_spacing=0.1
    )

    # 시간축 계산 (HOP_LENGTH / TARGET_SR 를 인덱스에 곱함)
    ref_time = np.arange(len(ref_f0)) * config.HOP_LENGTH / config.TARGET_SR
    user_time = np.arange(len(user_f0)) * config.HOP_LENGTH / config.TARGET_SR

    # (1) F0 Plot (Pitch)
    fig.add_trace(go.Scatter(
        x=ref_time, y=ref_f0, 
        name="Reference F0 (Part 3)", 
        line=dict(color='#3A86FF', width=2),
        opacity=0.8
    ), row=1, col=1)
    
    fig.add_trace(go.Scatter(
        x=user_time, y=user_f0, 
        name="User F0 (시연.m4a)", 
        line=dict(color='#FF006E', width=2, dash='dot'),
        opacity=0.8
    ), row=1, col=1)

    # (2) RMS Plot (Energy)
    fig.add_trace(go.Scatter(
        x=ref_time, y=ref_rms, 
        name="Reference RMS (Part 3)", 
        line=dict(color='#38B000', width=2),
        fill='tozeroy', fillcolor='rgba(56, 176, 0, 0.1)'
    ), row=2, col=1)
    
    fig.add_trace(go.Scatter(
        x=user_time, y=user_rms, 
        name="User RMS (시연.m4a)", 
        line=dict(color='#FFBE0B', width=2, dash='dot'),
        fill='tozeroy', fillcolor='rgba(255, 190, 11, 0.1)'
    ), row=2, col=1)

    # 레이아웃 설정
    fig.update_layout(
        height=900, width=1200, 
        title_text=f"Prosody Comparison: Reference vs User Demo",
        template="plotly_white",
        legend=dict(orientation="h", yanchor="bottom", y=1.02, xanchor="right", x=1)
    )
    
    fig.update_xaxes(title_text="Time (seconds)", row=2, col=1)
    fig.update_yaxes(title_text="Pitch (Hz)", row=1, col=1, range=[0, 500])
    fig.update_yaxes(title_text="Amplitude (RMS)", row=2, col=1)

    # 4. PNG 저장 (kaleido 필요)
    output_png = r"c:\Users\SSAFY\pjt\eng\S14P21A306\AI\test\comparison_result.png"
    fig.write_image(output_png)
    print(f"Visualization saved successfully: {output_png}")

if __name__ == "__main__":
    visualize()
