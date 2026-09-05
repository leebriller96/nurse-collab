import { execFileSync } from 'node:child_process';

const API = 'http://localhost:8080/api/v1';

/**
 * 시연·스크린샷용 데모 데이터를 만든다.
 *
 * 전부 실제 API 로 넣는다. SQL 로 직접 꽂으면 transfer_event 와 audit_log 가 비어서
 * 통계·이력 화면이 텅 빈 채로 나온다. 시스템이 만든 기록이어야 화면에 나온다.
 *
 * 시각만 마지막에 SQL 로 되돌린다. 방금 만든 요청은 대기시간이 전부 0분이라
 * 통계 화면에 볼 것이 없기 때문이다. 아래 backdate() 주석 참고.
 *
 *   docker compose down -v && docker compose up -d   # DB 를 비우고
 *   ./gradlew bootRun                                # Flyway 가 다시 심은 뒤
 *   node seed-demo.mjs
 */

const tokens = {};

async function login(loginId) {
  const res = await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ loginId, password: 'nurse1234!' }),
  });
  if (!res.ok) throw new Error(`${loginId} 로그인 실패: ${res.status}`);
  tokens[loginId] = (await res.json()).accessToken;
}

async function call(who, method, path, body) {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${tokens[who]}`,
      'Content-Type': 'application/json',
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${path} → ${res.status} ${text}`);
  return text ? JSON.parse(text) : null;
}

/** 상태를 한 칸씩 밀어 준다. 각 전이의 주체가 다르므로 누가 누르는지 함께 받는다. */
async function advance(requestId, steps) {
  for (const [who, toStatus] of steps) {
    const current = await call(who, 'GET', `/transfer-requests/${requestId}`);
    const payload = { toStatus, version: current.version };
    if (toStatus === 'ACCEPTED') {
      // 접수에는 예정 시각이 반드시 있어야 한다
      payload.scheduledAt = new Date(Date.now() + 40 * 60000).toISOString();
    }
    await call(who, 'POST', `/transfer-requests/${requestId}/transitions`, payload);
  }
}

/**
 * 방금 만든 요청들의 시각을 오늘 아침으로 되돌린다.
 *
 * 통계는 requested_at 과 transfer_event.occurred_at 의 차이로 계산한다.
 * 씨앗을 심은 직후에는 그 차이가 몇 초라 "평균 대기 0분" 이 나온다.
 * 요청은 07시부터 차례로 들어온 것으로, 각 전이는 앞 단계보다 몇 분씩 뒤에 일어난 것으로 민다.
 */
function backdate() {
  const sql = `
    -- 지난 4시간에 걸쳐 20분 간격으로 흩는다.
    -- 고정 시각(예: 07시)에 맞추면 그 시각 전에 돌렸을 때 요청이 미래로 가고,
    -- 대기시간이 음수가 돼 화면에 전부 "0분" 으로 찍힌다.
    with ordered as (
      select id, row_number() over (order by id) - 1 as n from transfer_request
    )
    update transfer_request r
       set requested_at = now() - interval '4 hour' + (o.n * interval '20 minute')
      from ordered o
     where o.id = r.id;

    -- 각 요청의 이력을 요청 시각 뒤로 순서대로 배치한다.
    -- 첫 전이(접수)는 6~24분 뒤, 그 뒤 전이는 한 칸당 7~13분 뒤로 둔다.
    with ordered as (
      select e.id,
             e.request_id,
             row_number() over (partition by e.request_id order by e.id) as k
        from transfer_event e
    )
    update transfer_event e
       set occurred_at = r.requested_at
                       + interval '1 minute' * (6 + (r.id * 7) % 19)
                       + interval '1 minute' * ((o.k - 1) * (7 + (r.id * 3) % 7))
      from ordered o
      join transfer_request r on r.id = o.request_id
     where o.id = e.id;

    -- 완료 시각은 마지막 이력과 같아야 한다
    update transfer_request r
       set completed_at = last_event.at
      from (
        select request_id, max(occurred_at) as at
          from transfer_event where to_status = 'COMPLETED' group by request_id
      ) last_event
     where last_event.request_id = r.id;

    -- 예정 시각은 접수 시각 30분 뒤로 맞춘다
    update transfer_request r
       set scheduled_at = accepted.at + interval '30 minute'
      from (
        select request_id, min(occurred_at) as at
          from transfer_event where to_status = 'ACCEPTED' group by request_id
      ) accepted
     where accepted.request_id = r.id;
  `;

  execFileSync(
    'docker',
    ['exec', '-i', 'nurse-collab-postgres', 'psql', '-U', 'nursecollab', '-d', 'nursecollab', '-v', 'ON_ERROR_STOP=1'],
    { input: sql, stdio: ['pipe', 'pipe', 'inherit'] },
  );
  console.log('  시각 보정 완료');
}

async function main() {
  for (const id of ['ward01', 'ward02', 'mri01', 'ct01']) {
    await login(id);
  }

  const encounters = {
    ward01: await call('ward01', 'GET', '/encounters'),
    ward02: await call('ward02', 'GET', '/encounters'),
  };
  const exams = await call('ward01', 'GET', '/exam-types');

  const encOf = (who, roomNo, bedNo) => {
    const list = encounters[who].content ?? encounters[who];
    const found = list.find((e) => e.roomNo === roomNo && e.bedNo === bedNo);
    if (!found) throw new Error(`재원 없음: ${roomNo}-${bedNo}`);
    return found.encounterId ?? found.id;
  };
  const examOf = (code) => {
    const found = exams.find((e) => e.code === code);
    if (!found) throw new Error(`검사 종류 없음: ${code}`);
    return found.id;
  };

  // 오늘 아침의 이송 요청들.
  // [요청한 간호사, 병실, 병상, 검사, 우선순위, 어디까지 진행됐나]
  const plan = [
    ['ward01', '302', '1', 'MRI_BRAIN',  'EMERGENCY', 'COMPLETED'],
    ['ward02', '501', '1', 'MRI_LSPINE', 'ROUTINE',   'COMPLETED'],
    ['ward01', '305', '1', 'CT_ABDOMEN', 'URGENT',    'COMPLETED'],
    ['ward02', '503', '2', 'CT_CHEST',   'ROUTINE',   'COMPLETED'],
    ['ward01', '302', '2', 'CT_CHEST',   'ROUTINE',   'RETURNED'],
    ['ward02', '501', '1', 'MRI_BRAIN',  'URGENT',    'IN_PROGRESS'],
    ['ward01', '302', '1', 'MRI_LSPINE', 'ROUTINE',   'IN_TRANSIT'],
    ['ward02', '503', '2', 'CT_ABDOMEN', 'ROUTINE',   'READY'],
    ['ward01', '305', '1', 'MRI_BRAIN',  'URGENT',    'ACCEPTED'],
    ['ward02', '501', '1', 'CT_CHEST',   'ROUTINE',   'REQUESTED'],
    ['ward01', '302', '2', 'MRI_LSPINE', 'ROUTINE',   'REQUESTED'],
  ];

  // 상태별로 어디까지 밀어야 하는지. 전이마다 누를 수 있는 쪽이 정해져 있다.
  const performerOf = (examCode) => (examCode.startsWith('MRI') ? 'mri01' : 'ct01');
  const pathTo = (target, requester, performer) => {
    const all = [
      [performer, 'ACCEPTED'],
      [performer, 'READY'],
      [requester, 'IN_TRANSIT'],
      [performer, 'IN_PROGRESS'],
      [performer, 'RETURNED'],
      [requester, 'COMPLETED'],
    ];
    const idx = all.findIndex(([, s]) => s === target);
    return idx === -1 ? [] : all.slice(0, idx + 1);
  };

  for (const [requester, room, bed, examCode, priority, target] of plan) {
    const created = await call(requester, 'POST', '/transfer-requests', {
      encounterId: encOf(requester, room, bed),
      examTypeId: examOf(examCode),
      priority,
    });
    const performer = performerOf(examCode);
    await advance(created.id, pathTo(target, requester, performer));
    console.log(`  ${created.requestNo} ${room}-${bed} ${examCode} → ${target}`);
  }

  backdate();
  console.log('\n데모 데이터 준비 완료');
}

main().catch((e) => {
  console.error('실패:', e.message);
  process.exit(1);
});
