# Kotlin-EraAndroid
Kotlin Native Era Android

현재 구동이 확인된 게임 : eraTHYMKR v3.21

현재 구동이 확인되지 게임 : 위 게임 빼고 전부


이 앱은 uEmuera(일명 짱개앱)의 포크나 개선판이 아닙니다

짱깨앱과는 완전히 다른방식으로 동작합니다

짱깨앱은 유니티기반으로 돌아가면
이 프로그램은 코틀린으로 안드로이드 네이티브로 동작합니다

(최적화가 안되서 게임로드가 느릴수 있습니다만 양해바랍니다)



## 엔진

0.3 버전부터 게임 엔진을 **Emuera 1.824** 소스 코드를 코틀린으로 옮긴 것으로 교체했습니다.
(`engine/` 폴더. PC 에서 `./gradlew -p engine test` 로 테스트할 수 있습니다.)

- 텍스트, 버튼, 색, HTML_PRINT, 그래픽 명령(GCREATE 등), 스프라이트, 이미지(resources 폴더) 지원
- 게임 폴더(CSV, ERB 폴더가 있는 폴더)를 선택해서 실행

## 저작권 / 라이선스

- 게임 엔진은 Emuera (Copyright (C) 2008- MinorShift, 妊）|дﾟ)の中の人) 를 바탕으로 합니다.
  원본에서 바꾼 점과 라이선스는 [NOTICE](NOTICE) 와 [engine/LICENSE-Emuera.txt](engine/LICENSE-Emuera.txt) 를 봐주세요.
