import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// ========= Env =========
const BASE    = __ENV.BASE || 'http://localhost:8080'; // ← http:// 포함!
const API     = `${BASE}/api/v1/stores/inventory`;
const ADMIN   = `${BASE}/api/v1/stores/admin/hotpath`;
const MENU_ID = __ENV.MENU_ID || '11111111-1111-1111-1111-111111111111';
const STOCK   = parseInt(__ENV.STOCK || '200');
const QTY     = parseInt(__ENV.QTY || '1');
const AUTH    = __ENV.AUTH_TOKEN || ''; // ← 토큰 받기

// ========= Scenarios =========
export const options = {
    scenarios: {
        hot_off: {
            exec: 'runReserveConfirm',
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '15s', target: 20 },
                { duration: '15s', target: 50 },
                { duration: '20s', target: 100 },
                { duration: '10s', target: 0 },
            ],
            gracefulRampDown: '5s',
            tags: { mode: 'hot_off' },
        },
        hot_on: {
            exec: 'runReserveConfirm',
            executor: 'ramping-vus',
            startTime: '1m10s',
            startVUs: 0,
            stages: [
                { duration: '15s', target: 20 },
                { duration: '15s', target: 50 },
                { duration: '20s', target: 100 },
                { duration: '10s', target: 0 },
            ],
            gracefulRampDown: '5s',
            tags: { mode: 'hot_on' },
        },
        mix_zipf: {
            exec: 'runZipfTraffic',
            executor: 'ramping-vus',
            startTime: '2m40s',
            startVUs: 0,
            stages: [
                { duration: '20s', target: 100 },
                { duration: '20s', target: 200 },
                { duration: '20s', target: 0 },
            ],
            gracefulRampDown: '5s',
            tags: { mode: 'zipf' },
            env: { ZIPF_KEYS: __ENV.ZIPF_KEYS || '20' }
        }
    },
    thresholds: {
        'http_req_failed{mode:hot_off}': ['rate<0.05'],
        'http_req_failed{mode:hot_on}':  ['rate<0.05'],
    }
};

// ========= Metrics =========
const m_res_ok   = new Counter('reserve_ok');
const m_res_409  = new Counter('reserve_409');
const m_res_503  = new Counter('reserve_503');
const m_res_4xx  = new Counter('reserve_other4xx');
const m_res_5xx  = new Counter('reserve_other5xx');
const m_conf_ok  = new Counter('confirm_ok');
const m_conf_err = new Counter('confirm_err');

const t_res  = new Trend('latency_reserve');
const t_conf = new Trend('latency_confirm');

// 네트워크 자체 실패(연결 거부 등)
const m_net_fail = new Counter('net_failed');

// ========= Helpers =========
function uuid() {
    const rnd = () => Math.floor((1 + Math.random()) * 0x10000).toString(16).substring(1);
    return `${rnd()}${rnd()}-${rnd()}-${rnd()}-${rnd()}-${rnd()}${rnd()}${rnd()}`;
}

function headers() {
    const h = { 'Content-Type': 'application/json' };
    if (AUTH) h['Authorization'] = `Bearer ${AUTH}`;
    return h;
}

function postJSON(url, body) {
    return http.post(url, JSON.stringify(body), { headers: headers() });
}
function get(url) {
    return http.get(url, { headers: headers() });
}

function adminSeed(menuId, available, reserved) {
    return http.post(`${ADMIN}/seed-direct/${menuId}?available=${available}&reserved=${reserved}`, null, { headers: headers() });
}
function adminToggle(menuId, on) {
    const path = on ? 'on' : 'off';
    return http.post(`${ADMIN}/toggle/${menuId}/${path}`, null, { headers: headers() });
}
function adminPing() {
    return get(`${ADMIN}/diag/ping`);
}

// ========= Setup =========
export function setup() {
    const ping = adminPing();
    check(ping, { 'admin ping 200': (r) => r.status === 200 });

    adminToggle(MENU_ID, false);
    adminSeed(MENU_ID, STOCK, 0);
    return {};
}

// ========= Summary =========
export function handleSummary(data) {
    const lines = [
        '--- HOT KEY BENCHMARK SUMMARY ---',
        `BASE=${BASE}`,
        `MENU_ID=${MENU_ID}`,
        '',
        `reserve_ok:   ${data.metrics.reserve_ok?.count || 0}`,
        `reserve_409:  ${data.metrics.reserve_409?.count || 0}`,
        `reserve_503:  ${data.metrics.reserve_503?.count || 0}`,
        `reserve_4xx:  ${data.metrics.reserve_other4xx?.count || 0}`,
        `reserve_5xx:  ${data.metrics.reserve_other5xx?.count || 0}`,
        `confirm_ok:   ${data.metrics.confirm_ok?.count || 0}`,
        `confirm_err:  ${data.metrics.confirm_err?.count || 0}`,
        '',
        `reserve_p95:  ${percentile(data, 'latency_reserve', 0.95)} ms`,
        `reserve_p99:  ${percentile(data, 'latency_reserve', 0.99)} ms`,
        `confirm_p95:  ${percentile(data, 'latency_confirm', 0.95)} ms`,
        `confirm_p99:  ${percentile(data, 'latency_confirm', 0.99)} ms`,
        '---------------------------------',
    ].join('\n');

    const csv = [
        'metric,value',
        `reserve_ok,${data.metrics.reserve_ok?.count || 0}`,
        `reserve_409,${data.metrics.reserve_409?.count || 0}`,
        `reserve_503,${data.metrics.reserve_503?.count || 0}`,
        `reserve_other4xx,${data.metrics.reserve_other4xx?.count || 0}`,
        `reserve_other5xx,${data.metrics.reserve_other5xx?.count || 0}`,
        `confirm_ok,${data.metrics.confirm_ok?.count || 0}`,
        `confirm_err,${data.metrics.confirm_err?.count || 0}`,
        `reserve_p95,${percentile(data, 'latency_reserve', 0.95)}`,
        `reserve_p99,${percentile(data, 'latency_reserve', 0.99)}`,
        `confirm_p95,${percentile(data, 'latency_confirm', 0.95)}`,
        `confirm_p99,${percentile(data, 'latency_confirm', 0.99)}`,
    ].join('\n');

    return {
        stdout: lines,
        'hotkey_benchmark_summary.txt': lines,
        'hotkey_benchmark_metrics.csv': csv,
    };
}

function percentile(data, metricName, p) {
    const m = data.metrics[metricName];
    if (!m || !m.values) return 0;
    const key = `p(${(p * 100).toFixed(0)})`;
    return m.values[key] ? Number(m.values[key].toFixed(2)) : 0;
}

// ========= Workloads =========
export function runReserveConfirm() {
    const mode = __ENV.K6_SCENARIO_NAME;
    if (mode === 'hot_on') adminToggle(MENU_ID, true);
    if (mode === 'hot_off') adminToggle(MENU_ID, false);

    const orderId = uuid();
    const orderLineId = uuid();

    const t0 = Date.now();
    const res = postJSON(`${API}/reserve`, { orderId, orderLineId, menuId: MENU_ID, qty: QTY });
    t_res.add(Date.now() - t0);

    if (res.status === 0) {
        m_net_fail.add(1);
        console.warn(`reserve net-fail: ${res.error}`);
    }

    if (res.status === 200) m_res_ok.add(1);
    else if (res.status === 409) m_res_409.add(1);
    else if (res.status === 503) m_res_503.add(1);
    else if (res.status >= 400 && res.status < 500) m_res_4xx.add(1);
    else if (res.status >= 500) m_res_5xx.add(1);

    if (res.status === 200) {
        const t1 = Date.now();
        const conf = postJSON(`${API}/confirm`, { orderLineId });
        t_conf.add(Date.now() - t1);

        if (conf.status === 0) {
            m_net_fail.add(1);
            console.warn(`confirm net-fail: ${conf.error}`);
        }
        if (conf.status === 200) m_conf_ok.add(1);
        else m_conf_err.add(1);
    }

    sleep(0.05);
}

export function runZipfTraffic() {
    const n = parseInt(__ENV.ZIPF_KEYS || '20');
    const keys = buildZipfKeys(n);
    const { menuId } = keys[Math.floor(Math.random() * keys.length)];

    const orderId = uuid();
    const orderLineId = uuid();

    const r = postJSON(`${API}/reserve`, { orderId, orderLineId, menuId, qty: 1 });
    if (r.status === 200) postJSON(`${API}/confirm`, { orderLineId });

    sleep(0.02);
}

function buildZipfKeys(n) {
    const arr = [{ menuId: MENU_ID, w: 1 }];
    for (let i = 2; i <= n; i++) arr.push({ menuId: pseudoUUID(i), w: 1 / i });
    const pool = [];
    arr.forEach(({ menuId, w }) => {
        const reps = Math.max(1, Math.floor(w * 100));
        for (let i = 0; i < reps; i++) pool.push({ menuId });
    });
    return pool;
}
function pseudoUUID(i) {
    const h = i.toString(16).padStart(4, '0');
    return `00000000-0000-0000-0000-${h}${h}${h}${h}`;
}
