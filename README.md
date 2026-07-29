# WildPressure

Paper 26.2 build 87에서 자연 야생 스폰을 차단하고, 실제 몹 개체군을 생태 구역 단위로 직접 배치·감소·보충하는 플러그인입니다.

## 현재 구현 범위 (0.1.0)

- `NATURAL`, `CHUNK_GEN` 자연 스폰 선택 차단
- 플레이어 영향 범위의 합집합으로 생태 구역 계산
- 플레이어가 겹칠 때 개체군 예산 중복 방지
- 기본 목표: 플레이어 단독 영향권당 실제 관리 몹 약 5,000마리
- 청크 이벤트와 분산 갱신 기반 관리 몹 인덱스
- 지역별 부족분 자체 생성
- 짧은 시간 대량 사망 시 지역 고갈 및 즉시 재생성 차단
- 영향권 밖 개체의 유예 후 점진 정리
- MSPT 임계치 이상 신규 생성 정지
- 관리자 상태·지역 조사·프로파일러 명령

이 버전은 **개체군 기반을 먼저 완성한 MVP**입니다. 정찰대, 소음, 군집 이동, 공성 역할 AI는 다음 단계입니다.

## 요구 사항

- Paper `26.2 build 87`
- Java 25

## 설치

1. `target/WildPressure-0.1.0.jar`를 서버 `plugins/`에 넣습니다.
2. 서버를 시작해 `plugins/WildPressure/config.yml`을 생성합니다.
3. Paper 자연 스폰 설정을 별도로 중복 변경하지 않아도 플러그인이 지정된 `SpawnReason`을 차단합니다.
4. `/wild status`로 관리 개체 수와 목표를 확인합니다.

## 명령어

- `/wild status` — 전체 개체군 상태
- `/wild inspect` — 현재 생태 구역의 종별 개체 수
- `/wild profiler` — 최근 재조정 시간과 생성·정리량
- `/wild reload` — 설정 재적용

권한: `wildpressure.admin` (기본 OP)

## 빌드

```bash
mvn clean verify
```

결과물: `target/WildPressure-0.1.0.jar`

## 운영 주의

실제 엔티티 5,000마리는 서버와 클라이언트 모두에 큰 부하를 줄 수 있습니다. 먼저 `target-per-player`, `max-spawns-per-pass`, `pause-spawning-above-mspt`를 낮게 두고 프로파일러로 확인한 뒤 올리십시오. 플러그인은 몹 수를 가상화하지 않습니다.

`adopt-existing-on-enable: true`이면 활성 월드에 이미 로드된 설정 대상 몹도 관리 개체로 편입하고 바닐라 원거리 디스폰을 막습니다.
