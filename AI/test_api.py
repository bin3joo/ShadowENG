"""
StyleEcho API 테스트 스크립트
=============================
사전 조건:  서버가 localhost:8000 에서 실행 중이어야 합니다.
   uvicorn main:app --host 0.0.0.0 --port 8000

사용법:
  * python -m test.test_api generate VIDEO_ID 30.0 45.0
  * python -m test.test_api evaluate "./my_recording.wav"
"""

try:
    from .test.test_api import main
except ImportError:
    from test.test_api import main

if __name__ == "__main__":
    main()
