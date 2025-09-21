import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    scenarios: {
        constant_request_rate: {
            executor: 'constant-arrival-rate',
            rate: 200,                // 목표 RPS
            timeUnit: '1s',
            duration: '10m',          // 10분 동안 부하
            preAllocatedVUs: 50,
            maxVUs: 200,
        },
    },
};

const GW = __ENV.GW || 'http://localhost:8080';
const HOT = __ENV.HOT;   // HOT 메뉴 UUID
const COLD = __ENV.COLD; // COLD 메뉴 UUID
const MODE = __ENV.MODE || 'mix'; // hot / cold / mix
const HOT_RATIO = parseFloat(__ENV.HOT_RATIO || '0.7');

export default function () {
    let menuId;

    if (MODE === 'hot') {
        menuId = HOT;
    } else if (MODE === 'cold') {
        menuId = COLD;
    } else {
        // mix 모드: HOT_RATIO 확률로 HOT 선택
        menuId = Math.random() < HOT_RATIO ? HOT : COLD;
    }

    const url = `${GW}/api/v1/stores/inventory/reserve`;
    const payload = JSON.stringify({
        orderId: crypto.randomUUID(),
        orderLineId: crypto.randomUUID(),
        menuId: menuId,
        qty: 1,
    });

    const params = { headers: { 'Content-Type': 'application/json' } };

    const res = http.post(url, payload, params);

    check(res, {
        'status is 200 or 201': (r) => r.status === 200 || r.status === 201,
        'status is not 500': (r) => r.status !== 500,
    });

    sleep(0.01); // 약간의 간격
}
