# AI Figure Paint Gift Worker 설정

이 Worker는 AI API 키를 APK 밖에 보관하고, 선물용 라이선스의 활성 기기를 최대 3대로 제한하며, 세 기기의 월 AI 예상 사용액을 합쳐 3,000원 이내로 차단합니다. Cloudflare 무료 한도를 초과하면 자동 유료 전환하지 말고 Cloudflare 대시보드에서 사용량을 확인하세요.

## 1. 준비

- Cloudflare 계정
- Node.js 20 이상
- AI API 키
- 이 저장소의 `cloudflare-worker` 폴더

API 키와 관리자 비밀값을 GitHub나 채팅에 올리지 마세요.

## 2. 로그인과 D1 생성

```bash
cd cloudflare-worker
npm install
npx wrangler login
npx wrangler d1 create ai-figure-paint-gift
```

출력된 `database_id`를 `wrangler.toml`의 `REPLACE_WITH_D1_DATABASE_ID`와 교체합니다.

스키마를 적용합니다.

```bash
npm run db:remote
```

## 3. Secret 등록

각 명령은 값을 화면에 출력하지 않고 Cloudflare Secret으로 저장합니다.

```bash
npx wrangler secret put AI_API_KEY
npx wrangler secret put ADMIN_SECRET
npx wrangler secret put LICENSE_HASH_SALT
```

`ADMIN_SECRET`과 `LICENSE_HASH_SALT`는 길고 임의적인 서로 다른 문자열을 사용합니다.

## 4. 배포

```bash
npm run check
npm run deploy
```

배포 후 표시되는 `https://...workers.dev` 주소를 기록합니다. Android 빌드 시 공개 Worker 주소만 사용하며 AI API 키나 관리자 Secret은 넣지 않습니다.

## 5. 활성화 코드 한 개 만들기

아래 요청은 활성화 코드를 한 번만 보여줍니다. `<WORKER_URL>`과 `<ADMIN_SECRET>`은 본인의 값으로 교체합니다.

```bash
curl -X POST "<WORKER_URL>/v1/admin/licenses" \
  -H "Authorization: Bearer <ADMIN_SECRET>" \
  -H "Content-Type: application/json" \
  -d '{}'
```

응답의 `activation_code`만 선물받는 사람에게 전달합니다. AI API 키는 전달하지 않습니다.

## 6. Android Worker 주소 설정

빌드할 때 다음 Gradle 속성을 사용합니다.

```bash
./gradlew assembleDebug -PGIFT_WORKER_URL="https://본인-worker.workers.dev"
```

GitHub Actions에서는 `GIFT_WORKER_URL` 저장소 Secret에 Worker 주소를 저장한 뒤 빌드 명령에 전달합니다. Worker URL은 비밀정보가 아니지만 빌드 설정을 일정하게 유지하기 위해 Secret을 사용할 수 있습니다.

## 7. 기기 등록 해제

Cloudflare D1의 `devices` 테이블에서 대상 기기 해시를 확인한 다음 관리자 요청을 사용합니다.

```bash
curl -X POST "<WORKER_URL>/v1/admin/revoke-device" \
  -H "Authorization: Bearer <ADMIN_SECRET>" \
  -H "Content-Type: application/json" \
  -d '{"license_id":"라이선스 ID","device_hash":"기기 해시"}'
```

해제된 기기는 다시 사용할 수 없습니다. 새 기기 한 대를 등록할 빈자리가 생깁니다.

## 8. 사용량 확인

앱의 AI 설정 화면에서 다음 항목을 확인할 수 있습니다.

- 이번 달 예상 사용액
- 남은 금액
- 활성화 기기 수
- 다음 초기화 날짜

월 사용량은 한국 시간 기준으로 구분됩니다. 환율과 모델 요금 변동을 고려해 Worker는 월 3,000원보다 조금 일찍 신규 요청을 중단할 수 있습니다.

## 9. Worker 일시 중지

긴급 중지는 Cloudflare 대시보드에서 Worker route를 비활성화하거나, D1의 `licenses.enabled`를 `0`으로 변경합니다. 앱에 관리자 비밀값을 넣지 마세요.
