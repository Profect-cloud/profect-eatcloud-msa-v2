import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

// ===== Env configuration =====
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const MENU_ID  = __ENV.MENU_ID  || 'REPLACE-ME-MENU-UUID';
const QTY      = parseInt(__ENV.QTY || '1', 10);
const TOKEN    = __ENV.AUTH_TOKEN || ''; // optional JWT
const VUS1     = parseInt(__ENV.VUS || '30', 10);
const VUS2     = parseInt(__ENV.VUS2 || '10', 10);
const DURATION = __ENV.DURATION || '10s';
const SCEN2_START = __ENV.SCEN2_START || '12s';

// ===== Scenarios & thresholds =====
// 409/503은 테스트 특성상 "예상 가능한 응답"일 수 있으니 실패율 판단은 우리가 정의한 unexpected_fail로만 본다.
export const options = {
    scenarios: {
        hot_key_reserve: {
            exec: 'reserveStorm',
            executor: 'constant-vus',
            vus: VUS1,
            duration: DURATION,
        },
        idempotency_dupe: {
            exec: 'idempotencyDoubleHit',
            startTime: SCEN2_START,
            executor: 'per-vu-iterations',
            vus: VUS2,
            iterations: 1,
            maxDuration: '30s',
        },
    },
    thresholds: {
        // "예상치 못한 실패"(401, 기타 4xx, 503 이외 5xx, 네트워크 실패 등)만 5% 미만으로 관리
        unexpected_fail: ['rate<0.05'],
    },
};

// ===== Metrics =====
export const m_ok         = new Counter('inv_reserve_success');        // 200/201
export const m_409        = new Counter('inv_reserve_conflict');       // 409 (멱등/잔량부족 등)
export const m_503        = new Counter('inv_reserve_503');            // 503 (락 타임아웃 등)
export const m_401        = new Counter('inv_reserve_401');            // 401 (토큰 문제)
export const m_4xx_other  = new Counter('inv_reserve_4xx_other');      // 기타 4xx
export const m_5xx_other  = new Counter('inv_reserve_5xx_other');      // 503 제외 5xx
export const m_other      = new Counter('inv_reserve_other');          // status==0 등
export const unexpected_fail = new Rate('unexpected_fail');            // 임계치용

function uuidv4() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0, v = c === 'x' ? r : (r & 0x3) | 0x8;
        return v.toString(16);
    });
}

function authHeaders() {
    const h = { 'Content-Type': 'application/json' };
    if (TOKEN) h['Authorization'] = `Bearer ${TOKEN}`;
    return h;
}

function countByStatus(res) {
    // k6에서 네트워크 실패 등은 status == 0 인 경우가 있음
    if (!res || res.status === 0) {
        m_other.add(1);
        unexpected_fail.add(1);
        return;
    }
    const s = res.status;
    if (s === 200 || s === 201) { m_ok.add(1); return; }
    if (s === 409) { m_409.add(1); return; }
    if (s === 503) { m_503.add(1); return; }               // 예상 가능한 상황 (락 등)
    if (s === 401) { m_401.add(1); unexpected_fail.add(1); return; } // 인증 실패는 실패로 집계
    if (s >= 400 && s < 500) { m_4xx_other.add(1); unexpected_fail.add(1); return; }
    if (s >= 500) { m_5xx_other.add(1); unexpected_fail.add(1); return; } // 503 제외 5xx는 실패
    // 그 외 예외 케이스
    m_other.add(1);
    unexpected_fail.add(1);
}

// ===== Scenario 1: Hot-key contention (동일 메뉴에 동시 예약) =====
export function reserveStorm() {
    const url = `${BASE_URL}/api/v1/stores/inventory/reserve`;
    const body = JSON.stringify({
        menuId: MENU_ID,
        orderId: uuidv4(),
        orderLineId: uuidv4(),
        qty: QTY,
    });

    const res = http.post(url, body, { headers: authHeaders() });
    countByStatus(res);
    sleep(0.1);
}

// ===== Scenario 2: Idempotency (동일 orderLineId를 동시에 2회) =====
export function idempotencyDoubleHit() {
    const url = `${BASE_URL}/api/v1/stores/inventory/reserve`;
    const commonId = uuidv4();
    const orderId = uuidv4();

    const mk = () => ({
        method: 'POST',
        url,
        body: JSON.stringify({
            menuId: MENU_ID,
            orderId,
            orderLineId: commonId,
            qty: QTY,
        }),
        params: { headers: authHeaders() },
    });

    const [r1, r2] = http.batch([mk(), mk()]);
    // 결과 카운트(지표) 업데이트
    countByStatus(r1);
    countByStatus(r2);

    // 기대: 200/201 한 건 + 409 한 건
    let succ = 0, conf = 0;
    [r1, r2].forEach(r => {
        if (r.status === 200 || r.status === 201) succ++;
        else if (r.status === 409) conf++;
    });
    check(null, {
        'idempotency one success': () => succ === 1,
        'idempotency one conflict': () => conf === 1,
    });
}

// ===== Summary =====
function getCount(data, name) {
    const m = data.metrics?.[name];
    if (!m) return 0;
    // k6 버전에 따라 values.count 또는 count로 나타나는 경우가 있어 안전하게 처리
    if (m.values && typeof m.values.count !== 'undefined') return m.values.count;
    if (typeof m.count !== 'undefined') return m.count;
    return 0;
}

export function handleSummary(data) {
    const lines = [
        '--- Inventory Concurrency Summary ---',
        `BASE_URL: ${BASE_URL}`,
        `MENU_ID:  ${MENU_ID}`,
        '',
        `Reserve success:   ${getCount(data, 'inv_reserve_success')}`,
        `Conflict(409):     ${getCount(data, 'inv_reserve_conflict')}`,
        `Svc Unavail(503):  ${getCount(data, 'inv_reserve_503')}`,
        `401 Unauthorized:  ${getCount(data, 'inv_reserve_401')}`,
        `Other 4xx:         ${getCount(data, 'inv_reserve_4xx_other')}`,
        `Other 5xx:         ${getCount(data, 'inv_reserve_5xx_other')}`,
        `Other errors:      ${getCount(data, 'inv_reserve_other')}`,
        '-------------------------------------',
    ];
    const txt = lines.join('\n');
    return {
        stdout: txt,
        'inventory_summary.txt': txt, // 파일도 남김(도커 컨테이너 내 /work 기준)
    };
}
