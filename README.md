# Attendance Android App

`android-app`은 기존 `mobile`의 출퇴근 체크 기능을 안드로이드 네이티브 앱으로 옮긴 프로젝트입니다.

## 포함 기능

- 로그인
- 로그인 유지
- 회사명/회사 위치/허용 반경 조회
- 오늘 출근/퇴근 상태 조회
- 현재 위치 표시
- 회사 반경 내에서만 출근/퇴근 버튼 활성화
- 위치 정확도 및 측정 시각을 함께 전송
- 단말 1대 등록 정책과 서버 오류 메시지 표시

## 기본 API 주소

기본값은 `https://api.hsft.io.kr/api/` 입니다.

개발용으로 바꾸려면 [app/build.gradle.kts](/Users/hyeonseobkim/workspace/attendance-app/android-app/app/build.gradle.kts)의 `API_BASE_URL` 값을 수정하세요.

## 빌드

Android Studio에서 `android-app` 폴더를 열고 `app` 모듈을 빌드하면 됩니다.

- 디버그 APK: `Build > Build APK(s)`
- 설치 파일 경로: `app/build/outputs/apk/debug/app-debug.apk`

현재 이 작업 환경에는 Android SDK가 없어 실제 APK 빌드는 여기서 검증하지 못했습니다.
