# MarketBoard 기술적 지표 기준

버전: 1.0 · 작성: 2026-10-02 · 상태: 계산 기반 구현 완료, 화면·스크리너 연결 예정

관련 기능: [FUNCTION F03·F13](FUNCTION.md), [SPEC 공통 계약](SPEC.md)

## 1. 목적과 범위

종목 상세 차트와 종목 스크리너가 동일한 가격 자료와 계산 공식을 사용하도록 지표의 종류, 기본 파라미터, 입력 자료, 결측 처리 규칙을 정의한다.

이 문서는 계산 기준이며 아래에서 `현재 구현`으로 표시하지 않은 항목은 구현 완료를 의미하지 않는다.

## 2. 1차 선정 지표

| 분류 | 지표 | 기본 파라미터 | 차트 표현 | 스크리너 조건 |
|---|---|---:|---|---|
| 추세 | SMA | 20, 50, 200 | 가격 차트 선 | 종가 상·하단, 이격도 |
| 추세 | EMA | 20, 60 | 가격 차트 선 | 종가 상·하단, 이격도 |
| 모멘텀 | RSI | 14, Wilder 평활 | 보조 페인 | 최솟값·최댓값 |
| 모멘텀 | MACD | 12, 26, 9 | 선·시그널·히스토그램 | 시그널 상·하단, 교차, 히스토그램 부호 |
| 변동성 | 볼린저밴드 | 20, 표준편차 2배 | 가격 차트 밴드 | %B, 상·하단 이탈 |
| 변동성 | ATR | 14, Wilder 평활 | 보조 페인 | ATR% 범위 |
| 거래량 | 상대 거래량 | 20 | 거래량 페인 | 평균 대비 배수 |

스토캐스틱, CCI, ADX, Ichimoku, Williams %R는 1차 범위에서 제외한다. 선정 지표와 역할이 겹치거나 화면과 검색 조건을 먼저 복잡하게 만들기 때문이다.

## 3. 공통 입력 규칙

- 시간 순서는 오래된 봉에서 최신 봉 순서다.
- 기본 가격 소스는 수정주가 기준 OHLCV다. `yfinance` 호출에는 기본값 변화에 의존하지 않도록 `auto_adjust=True`를 명시한다.
- 일봉 스크리너는 완료된 거래일만 사용한다. 장중 미완성 일봉은 포함하지 않는다.
- 결측 가격을 0이나 직전 값으로 임의 대체하지 않는다. 필요한 관측 수가 부족하면 값과 상태를 `null / INSUFFICIENT_HISTORY`로 반환한다.
- 계산 중간값은 반올림하지 않는다. 저장 또는 응답에서도 충분한 정밀도를 유지하고 화면 표시 단계에서만 반올림한다.
- EMA, RSI, MACD, ATR처럼 이전 값에 의존하는 지표는 표시 구간 이전의 워밍업 자료를 함께 조회한다.
- 결과에는 `type`, `timeframe`, `parameters`, `value`, `asOf`, `isPartial`, `calculationVersion`을 포함한다.

예시:

```json
{
  "type": "RSI",
  "timeframe": "1d",
  "parameters": { "period": 14, "smoothing": "WILDER" },
  "value": 58.2417,
  "asOf": "2026-10-01",
  "isPartial": false,
  "calculationVersion": "technical-v1"
}
```

## 4. 계산 공식

### 4.1 SMA

최근 N개 종가의 산술평균이다.

```text
SMA(t, N) = sum(Close[t-i], i=0..N-1) / N
```

- 최소 관측 수: N개 종가
- 이격도: `(Close / SMA - 1) × 100`

### 4.2 EMA

```text
alpha = 2 / (N + 1)
EMA(t) = alpha × Close(t) + (1 - alpha) × EMA(t-1)
```

- 초기 EMA는 최초 N개 종가의 SMA로 고정한다.
- 최소 관측 수는 N개이며 안정적인 값에는 3N개 이상의 워밍업을 권장한다.

### 4.3 RSI

Wilder 평활 방식을 기준으로 한다.

```text
Gain(t) = max(Close(t) - Close(t-1), 0)
Loss(t) = max(Close(t-1) - Close(t), 0)

AvgGain(t) = (AvgGain(t-1) × (N-1) + Gain(t)) / N
AvgLoss(t) = (AvgLoss(t-1) × (N-1) + Loss(t)) / N
RS = AvgGain / AvgLoss
RSI = 100 - 100 / (1 + RS)
```

- 최초 평균 상승·하락은 첫 N개 변화량의 산술평균이다.
- 최소 관측 수: N+1개 종가. 안정화를 위해 3N~5N개 조회를 권장한다.
- 평균 상승과 평균 손실이 모두 0이면 RSI 50이다.
- 평균 손실만 0이면 RSI 100, 평균 상승만 0이면 RSI 0이다.

### 4.4 MACD

```text
MACD = EMA(Close, fast) - EMA(Close, slow)
Signal = EMA(MACD, signal)
Histogram = MACD - Signal
```

기본값은 fast 12, slow 26, signal 9다. 교차 조건은 직전 봉과 현재 봉을 함께 비교한다.

```text
상향 교차 = previous MACD <= previous Signal AND current MACD > current Signal
하향 교차 = previous MACD >= previous Signal AND current MACD < current Signal
```

### 4.5 볼린저밴드

```text
Middle = SMA(Close, N)
Upper = Middle + K × sampleStdDev(Close, N)
Lower = Middle - K × sampleStdDev(Close, N)
%B = (Close - Lower) / (Upper - Lower)
```

기본값은 N=20, K=2이며 표준편차는 표본 표준편차(`ddof=1`)로 통일한다. 밴드 폭이 0이면 `%B`는 null이다.

### 4.6 ATR

```text
TR(t) = max(
  High(t) - Low(t),
  abs(High(t) - Close(t-1)),
  abs(Low(t) - Close(t-1))
)

ATR(t) = (ATR(t-1) × (N-1) + TR(t)) / N
ATR% = ATR / Close × 100
```

최초 ATR은 첫 N개 TR의 산술평균이다. 종목 간 검색·정렬에는 절대 ATR이 아니라 ATR%를 사용한다.

### 4.7 상대 거래량

```text
VolumeMA(t, N) = mean(Volume(t-1) ... Volume(t-N))
RelativeVolume(t) = Volume(t) / VolumeMA(t, N)
```

- 비교 평균에는 현재 봉을 포함하지 않는다.
- 완료된 일봉 기준 스크리너에서 먼저 제공한다.
- 장중 지원 시에는 같은 경과 시간의 과거 누적 거래량과 비교하는 별도 규격이 필요하다.

## 5. 기존 지표 공식 확인

| 지표 | 현재 위치 | 확인 결과 | 조치 |
|---|---|---|---|
| SMA20·SMA50 | Java `TechnicalIndicators`, 프론트 `CandleChart` | 최근 N개 종가 산술평균으로 일치 | 파라미터형 SMA로 확장 |
| RSI14 | Java `TechnicalIndicators`, Python `screener.py` | 두 구현 모두 최근 N개 변화량의 단순평균 방식 | Wilder 방식으로 변경하고 동일 벡터 검증 |
| 모멘텀 | Python `screening_snapshot.py` | `(현재가 / N거래일 전 가격 - 1) × 100` | 63·126·252 거래일 유지 |
| 20일 변동성 | Python `screening_snapshot.py` | 일수익률 표본 표준편차 × √252 × 100 | UI 명칭을 `20일 연환산 변동성`으로 표시 |
| 장기 추세 | 스크리너 스냅샷 | `Close > SMA200` | SMA 이격도 추가 가능 |

현재 `IndicatorType`은 `SMA20`, `SMA50`, `RSI14`처럼 기간이 이름에 포함되어 있다. 가변 기간 지원 시 지표 종류와 파라미터를 분리한다.

## 6. 계산 일관성 검증 기준

같은 고정 OHLCV 벡터에 대해 차트 계산, 단일 종목 최신값, 스크리너 스냅샷이 아래 조건을 만족해야 한다.

1. SMA·EMA·RSI·MACD·볼린저·ATR·상대 거래량 값이 허용 오차 안에서 일치한다.
2. 최소 관측 수보다 한 개 부족할 때 null, 경계 수량에서 첫 값이 생성된다.
3. 상승만 있는 RSI는 100, 하락만 있으면 0, 변화가 없으면 50이다.
4. 볼린저밴드 폭이 0일 때 `%B`는 null이다.
5. 이전 종가를 포함한 ATR 갭 계산을 검증한다.
6. 분할 전후 수정주가 자료에서 인위적인 급등락 신호가 생기지 않는다.
7. 미완성 일봉 포함 여부와 기준 시각이 결과 메타데이터에 반영된다.

## 7. 구현 순서

1. 수정주가·완료 봉·결측·워밍업 기준 고정
2. 공통 테스트 벡터 작성
3. SMA 파라미터화
4. RSI를 Wilder 방식으로 통일
5. EMA와 MACD 추가
6. 볼린저밴드와 ATR 추가
7. 상대 거래량 추가
8. 차트와 스크리너의 동일 벡터 회귀 검사
