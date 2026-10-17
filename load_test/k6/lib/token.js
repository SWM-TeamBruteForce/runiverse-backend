// 서버(JwtTokenAdapter)와 같은 형식의 액세스 토큰을 직접 서명한다.
// 비밀값은 base64 디코딩 없이 UTF-8 바이트 그대로 쓴다(JwtSecretKeyFactory와 같다).
// 서버는 핸드셰이크에서 DB를 보지 않으므로 시드한 user_id만 있으면 된다.

import crypto from 'k6/crypto';
import encoding from 'k6/encoding';

const ISSUER = __ENV.JWT_ISSUER || 'runiverse';
const AUDIENCE = __ENV.JWT_AUDIENCE || 'runiverse-api';
// 핸드셰이크 때만 검사하므로 재연결까지 버티면 된다
const TTL_SECONDS = Number(__ENV.JWT_TTL_SECONDS || 6 * 3600);

function b64url(text) {
  return encoding.b64encode(text, 'rawurl');
}

export function mintAccessToken(secret, userId) {
  const now = Math.floor(Date.now() / 1000);
  const header = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = b64url(JSON.stringify({
    iss: ISSUER,
    aud: [AUDIENCE],
    sub: userId,
    iat: now,
    exp: now + TTL_SECONDS,
    jti: crypto.hexEncode(crypto.randomBytes(16)),
    typ: 'access',
  }));
  const signature = crypto.hmac('sha256', secret, `${header}.${payload}`, 'base64rawurl');
  return `${header}.${payload}.${signature}`;
}
