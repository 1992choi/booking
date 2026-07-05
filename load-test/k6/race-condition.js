import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter } from 'k6/metrics';

const API_BASE_URL = __ENV.API_BASE_URL || 'http://localhost:8080';
const RESERVATION_BASE_URL = __ENV.RESERVATION_BASE_URL || 'http://localhost:8081';
const EMAIL = __ENV.EMAIL || 'test@bookit.com';
const PASSWORD = __ENV.PASSWORD || '12341234';
const MERCHANT_NAME = '레이스테스트 시설';
const RESOURCE_NAME = '레이스 컨디션 테스트 룸';

const reservationSuccess = new Counter('reservation_success');
const reservationConflict = new Counter('reservation_conflict');
const reservationUnexpected = new Counter('reservation_unexpected');

export const options = {
    scenarios: {
        race: {
            executor: 'shared-iterations',
            vus: Number(__ENV.VUS) || 200,
            iterations: Number(__ENV.ITERATIONS) || 50000,
            maxDuration: __ENV.MAX_DURATION || '5m',
        },
    },
};

export function setup() {
    const loginRes = http.post(
        `${API_BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ email: EMAIL, password: PASSWORD }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    if (loginRes.status !== 200) {
        fail(`로그인 실패: status=${loginRes.status} body=${loginRes.body}`);
    }
    const accessToken = loginRes.json('accessToken');

    const merchants = http.get(`${RESERVATION_BASE_URL}/api/v1/merchants`).json();
    const merchant = merchants.find((m) => m.name === MERCHANT_NAME);

    if (!merchant) {
        fail(`가맹점을 찾을 수 없습니다: ${MERCHANT_NAME}`);
    }

    const detail = http.get(`${RESERVATION_BASE_URL}/api/v1/merchants/${merchant.id}`).json();
    const resource = detail.resources.find((r) => r.name === RESOURCE_NAME);

    if (!resource) {
        fail(`리소스를 찾을 수 없습니다: ${RESOURCE_NAME}`);
    }

    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const date = tomorrow.toISOString().slice(0, 10);

    const slots = http
        .get(`${RESERVATION_BASE_URL}/api/v1/resources/${resource.id}/available-times?date=${date}`)
        .json();

    if (!slots || slots.length === 0) {
        fail(`예약 가능 시간을 찾을 수 없습니다: resourceId=${resource.id}, date=${date}`);
    }

    return {
        accessToken,
        resourceId: resource.id,
        availableTimeId: slots[0].id,
        maxCapacity: resource.maxCapacity,
    };
}

export default function (data) {
    const res = http.post(
        `${RESERVATION_BASE_URL}/api/v1/reservations`,
        JSON.stringify({
            resourceId: data.resourceId,
            availableTimeIds: [data.availableTimeId],
            headCount: 1,
        }),
        {
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${data.accessToken}`,
            },
        }
    );

    if (res.status === 201) {
        reservationSuccess.add(1);
    } else if (res.status === 409) {
        reservationConflict.add(1);
    } else {
        reservationUnexpected.add(1);
    }

    check(res, {
        '201(성공) 또는 409(정원초과)만 발생': (r) => r.status === 201 || r.status === 409,
    });
}

export function teardown(data) {
    console.log(`리소스 최대 수용 인원(maxCapacity): ${data.maxCapacity} — reservation_success 카운터와 비교해서 확인할 것`);
}
