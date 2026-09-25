# Kotlin-EraAndroid

**Emuera 1.824 기반 안드로이드 네이티브 era 런처**

PC 용 era 게임(ERB/CSV)을 안드로이드에서 그대로 실행합니다.
게임 엔진은 Emuera 1.824 의 C# 소스를 코틀린으로 옮긴 것이고,
화면(UI)과 안드로이드용 처리는 이 프로젝트에서 따로 만들었습니다.

## 특징

- 코틀린으로 만든 안드로이드 네이티브 앱 (유니티 기반인 uEmuera 의 포크나 개선판이 아닙니다)
- 원본 Emuera 와 같은 방식으로 ERB/CSV 를 읽고 실행
- 텍스트·색·버튼, 숫자/문자 입력, 저장/불러오기 (PC 판과 같은 세이브 형식)
- 이미지 지원: resources 폴더의 이미지, `PRINT_IMG`, `HTML_PRINT` 의 `<img>`, 그래픽 명령, 스프라이트
- 휴대폰 화면에 맞춘 표시: 폰트 크기 조절, 화면 폭에 맞춘 PRINTC 메뉴 열 배치
- 게임이 멈추지 않도록 한 안드로이드판 전용 처리
  (배열 범위 밖 접근·0 나누기는 계속 진행, 끝나지 않는 반복은 30초 뒤 중단)

## 구동 확인된 게임

- eraTHYMKR (2019-10-28 판, v3.21) — 새 게임과 기존 세이브에서 자동 플레이로 엔딩까지 확인

## 사용법

1. [Releases](https://github.com/Railtey/Kotlin-EraAndroid/releases) 에서 최신 APK 를 받아 설치
2. 앱의 메뉴 → "게임 폴더 지정" 에서 `CSV`, `ERB` 폴더가 들어 있는 게임 폴더를 선택
3. 처음 한 번 "모든 파일 접근" 권한을 켜야 합니다 (게임 폴더를 직접 읽고 세이브를 쓰기 때문)

## 알려진 제한

- 소리, 디버그 창 등 Windows 전용 기능은 동작하지 않습니다
- 배경 이미지(CBG)와 도형(PRINT_RECT 등)은 아직 화면에 표시되지 않습니다

## 개발

- `app/` : 안드로이드 앱 (UI)
- `engine/` : 게임 엔진 (순수 코틀린). PC 에서 `./gradlew -p engine test` 로 테스트할 수 있습니다

## 저작권 / 라이선스

- 게임 엔진은 Emuera (Copyright (C) 2008- MinorShift, 妊）|дﾟ)の中の人) 를 바탕으로 합니다.
  이 앱은 원본 Emuera 가 아니며, Emuera 의 원작자와 관계가 없습니다.
- 원본에서 바꾼 점과 라이선스 전문은 [NOTICE](NOTICE) 와 [engine/LICENSE-Emuera.txt](engine/LICENSE-Emuera.txt) 를 봐주세요.
