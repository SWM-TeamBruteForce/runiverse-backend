// Open-Meteo 대역 — 러닝 종료가 부르는 /v1/forecast에 고정 날씨를 돌려준다.
// 실제 API를 치면 외부 지연(실패 시 재시도 3회)이 종료 응답에 섞여 상태 저장소 비교가 흐려진다.
// WEATHER_STUB_DELAY_MS로 실제 API 수준의 지연을 일부러 넣을 수 있다(기본 0).

const http = require('http');

const PORT = Number(process.env.PORT || 8090);
const DELAY_MS = Number(process.env.WEATHER_STUB_DELAY_MS || 0);
const BODY = JSON.stringify({ hourly: { weather_code: [0], temperature_2m: [15.0] } });

http.createServer((req, res) => {
  if (!req.url.startsWith('/v1/forecast')) {
    res.writeHead(404).end();
    return;
  }
  setTimeout(() => {
    res.writeHead(200, { 'Content-Type': 'application/json' }).end(BODY);
  }, DELAY_MS);
}).listen(PORT, () => console.log(`weather stub listening on ${PORT} (delay ${DELAY_MS}ms)`));
