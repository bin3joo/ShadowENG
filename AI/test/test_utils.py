"""테스트 헬퍼 유틸리티."""

from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path
from typing import Callable

TEST_DIR = Path(__file__).resolve().parent
RESULT_DIR = TEST_DIR / "result"
RESULT_DIR.mkdir(parents=True, exist_ok=True)


def run_and_save_output(output_name: str, runner: Callable[[], None]) -> Path:
    """테스트 함수를 실행하고, 출력을 표시한 후 result/ 하위에 저장합니다."""
    buffer = StringIO()
    with redirect_stdout(buffer):
        runner()
    content = buffer.getvalue()
    print(content, end="")
    output_path = RESULT_DIR / output_name
    output_path.write_text(content, encoding="utf-8")
    return output_path
