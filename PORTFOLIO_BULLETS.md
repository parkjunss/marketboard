# MarketBoard — 이력서/포트폴리오용 bullet

> 작성: 2026-07-20
> 출처: 세션에서 나눈 프로젝트 정리 대화 요약 (`PROGRESS.md` 근거). 지원 직군/양식에 맞게 문구는 조정해서 쓸 것.

## MarketBoard — 실시간 주식 시세·포트폴리오 관리 서비스 (개인 프로젝트, 기획~개발~배포~운영 전담)

- Spring Boot(Java) + Python(FastAPI) + Next.js(React) 3-tier 구조로, Finnhub WebSocket 실시간 시세를 Redis Pub/Sub → STOMP 경로로 브라우저까지 전달하는 실시간 데이터 파이프라인 설계·구현
- JWT 인증, 브루트포스 방어 레이트리밋(Bucket4j), FK로 얽힌 6개 테이블을 애플리케이션 레벨에서 직접 정리하는 관리자 삭제 API 등 보안·데이터 정합성을 고려해 구현, 백엔드 테스트 38건 작성
- 자체 서버에 self-hosted GitHub Actions 러너를 구성해 push 시 빌드→배포 자동화, nginx + Let's Encrypt + DuckDNS로 HTTPS 공개 서비스 운영, Prometheus/Grafana로 커스텀 메트릭·장애 알림 구축
- 배치 작업 지연 원인을 "종목마다 DB 커넥션을 새로 여는 것"으로 실측 기반 진단해 503종목 처리 시간을 91초로 단축하는 등, 추측이 아닌 실측으로 성능 문제를 해결한 경험 다수
- 배포 전 로컬 환경에서 매번 실제 빌드·컨테이너 기동·E2E 검증을 거치는 습관으로, 프로덕션 장애를 사전에 여러 차례 예방
