import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';

/**
 * 공개 데모의 데이터를 처음 상태로 되돌리고 시연용 요청을 다시 심는다.
 *
 * 데모 계정 비밀번호를 README 에 적어 두었으므로 누구나 로그인해 데이터를 바꾼다.
 * 며칠이면 검사실 큐가 장난 데이터로 찬다. 매일 새벽에 한 번 되돌린다.
 *
 * 지울 것과 남길 것을 목록으로 관리하지 않는다. 데이터를 전부 비우고
 * Flyway 시드 파일(V3, V5)을 그대로 다시 실행한다. 이유가 두 가지다.
 *   - 방문자가 admin01 의 역할을 간호사로 바꿔 두면 "남길 목록" 방식으로는
 *     그 상태가 영구히 남는다. 아무도 관리자 화면에 못 들어간다.
 *   - 시드에 행을 추가할 때마다 목록을 같이 고쳐야 하는데, 잊으면 조용히 어긋난다.
 *
 * 요청 데이터는 SQL 로 꽂지 않고 실제 API 로 만든다. 직접 꽂으면
 * transfer_event 와 audit_log 가 비어서 통계·이력 화면이 텅 빈 채로 나온다.
 *
 * 환경변수
 *   API_BASE         기본 http://localhost:8080
 *   MIGRATION_DIR    V3/V5 시드 파일이 있는 경로
 *   PGHOST PGPORT PGUSER PGPASSWORD PGDATABASE
 *   PG_VIA_DOCKER    값이 있으면 psql 대신 그 이름의 컨테이너에 docker exec 한다 (로컬용)
 *   RESET_AT         "04:00" 형식. 지정하면 매일 그 시각에 반복한다. 없으면 한 번만.
 */

const API = `${process.env.API_BASE ?? 'http://localhost:8080'}/api/v1`;
const MIGRATION_DIR = process.env.MIGRATION_DIR ?? '/app/sql';
const VIA_DOCKER = process.env.PG_VIA_DOCKER;

const tokens = {};

// ── psql 실행 ───────────────────────────────────────────────

/**
 * 로컬에서는 psql 이 깔려 있지 않은 경우가 많아 컨테이너에 docker exec 한다.
 * 배포에서는 이미지 안에 psql 이 있고 도커 소켓을 줄 이유가 없으므로 직접 부른다.
 */
function runSql(sql) {
  const env = { ...process.env };
  const args = [
    '-v', 'ON_ERROR_STOP=1',
    '-U', env.PGUSER ?? 'nursecollab',
    '-d', env.PGDATABASE ?? 'nursecollab',
  ];

  const [command, argv] = VIA_DOCKER
    ? ['docker', ['exec', '-i', VIA_DOCKER, 'psql', ...args]]
    : ['psql', ['-h', env.PGHOST ?? 'localhost', '-p', env.PGPORT ?? '5432', ...args]];

  execFileSync(command, argv, { input: sql, env, stdio: ['pipe', 'pipe', 'inherit'] });
}

/**
 * -f 를 쓰지 않고 내용을 읽어 stdin 으로 넘긴다.
 * docker exec 로 부를 때 -f 의 경로는 컨테이너 안을 가리키기 때문이다.
 */
const runFile = (path) => runSql(readFileSync(path, 'utf8'));

// ── 1. 비우고 시드 다시 심기 ────────────────────────────────

/**
 * flyway_schema_history 는 건드리지 않는다. 지우면 다음 기동 때
 * Flyway 가 마이그레이션을 처음부터 다시 돌리려다 실패한다.
 */
function wipe() {
  runSql(`
    truncate table
      audit_log, nursing_note, vital_sign, notification, request_message,
      transfer_event, transfer_request, request_no_sequence,
      patient_alert, encounter, exam_type, staff, patient, department
    restart identity cascade;
  `);
  runFile(`${MIGRATION_DIR}/V3__seed_master_data.sql`);
  runFile(`${MIGRATION_DIR}/V5__seed_demo_patients.sql`);
  console.log('  비우고 시드 재적재 완료');
}

// ── 2. 시연용 요청 만들기 ───────────────────────────────────

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

/** 상태를 한 칸씩 민다. 전이마다 누를 수 있는 쪽이 정해져 있어 행위자를 함께 받는다. */
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

// 오늘 아침의 이송 요청들.
// [요청한 간호사, 병실, 병상, 검사, 우선순위, 어디까지 진행됐나]
const PLAN = [
  ['ward01', '302', '1', 'MRI_BRAIN', 'EMERGENCY', 'COMPLETED'],
  ['ward02', '501', '1', 'MRI_LSPINE', 'ROUTINE', 'COMPLETED'],
  ['ward01', '305', '1', 'CT_ABDOMEN', 'URGENT', 'COMPLETED'],
  ['ward02', '503', '2', 'CT_CHEST', 'ROUTINE', 'COMPLETED'],
  ['ward01', '302', '2', 'CT_CHEST', 'ROUTINE', 'RETURNED'],
  ['ward02', '501', '1', 'MRI_BRAIN', 'URGENT', 'IN_PROGRESS'],
  ['ward01', '302', '1', 'MRI_LSPINE', 'ROUTINE', 'IN_TRANSIT'],
  ['ward02', '503', '2', 'CT_ABDOMEN', 'ROUTINE', 'READY'],
  ['ward01', '305', '1', 'MRI_BRAIN', 'URGENT', 'ACCEPTED'],
  ['ward02', '501', '1', 'CT_CHEST', 'ROUTINE', 'REQUESTED'],
  ['ward01', '302', '2', 'MRI_LSPINE', 'ROUTINE', 'REQUESTED'],
];

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

async function seed() {
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

  for (const [requester, room, bed, examCode, priority, target] of PLAN) {
    const created = await call(requester, 'POST', '/transfer-requests', {
      encounterId: encOf(requester, room, bed),
      examTypeId: examOf(examCode),
      priority,
    });
    await advance(created.id, pathTo(target, requester, performerOf(examCode)));
  }
  console.log(`  요청 ${PLAN.length}건 생성 완료`);
}

// ── 3. 시각 되돌리기 ────────────────────────────────────────

/**
 * 방금 만든 요청은 대기시간이 몇 초라 "평균 대기 0분" 이 나온다.
 * 지난 4시간에 걸쳐 들어온 것으로 민다.
 *
 * 고정 시각(예: 07시)에 맞추면 그 시각 전에 돌렸을 때 요청이 미래로 가고,
 * 대기시간 계산이 음수가 돼 화면에 전부 0분으로 찍힌다. now() 기준이어야 한다.
 */
function backdate() {
  runSql(`
    with ordered as (
      select id, row_number() over (order by id) - 1 as n from transfer_request
    )
    update transfer_request r
       set requested_at = now() - interval '4 hour' + (o.n * interval '20 minute')
      from ordered o
     where o.id = r.id;

    -- 각 요청의 이력을 요청 시각 뒤로 순서대로 배치한다.
    -- 첫 전이(접수)는 6~24분 뒤, 그 뒤는 한 칸당 7~13분 뒤.
    with ordered as (
      select e.id, e.request_id,
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

    update transfer_request r
       set completed_at = last_event.at
      from (select request_id, max(occurred_at) as at
              from transfer_event where to_status = 'COMPLETED' group by request_id) last_event
     where last_event.request_id = r.id;

    update transfer_request r
       set scheduled_at = accepted.at + interval '30 minute'
      from (select request_id, min(occurred_at) as at
              from transfer_event where to_status = 'ACCEPTED' group by request_id) accepted
     where accepted.request_id = r.id;
  `);
  console.log('  시각 보정 완료');
}

// ── 실행 ────────────────────────────────────────────────────

async function runOnce() {
  const at = new Date().toLocaleString('ko-KR');
  console.log(`[${at}] 데모 초기화 시작`);
  wipe();
  await seed();
  backdate();
  console.log(`[${new Date().toLocaleString('ko-KR')}] 완료\n`);
}

/** 다음 지정 시각까지 남은 밀리초. 이미 지났으면 내일 같은 시각. */
function msUntil(hhmm) {
  const [h, m] = hhmm.split(':').map(Number);
  const next = new Date();
  next.setHours(h, m, 0, 0);
  if (next <= new Date()) next.setDate(next.getDate() + 1);
  return next - new Date();
}

async function main() {
  const at = process.env.RESET_AT;

  if (!at) {
    await runOnce();
    return;
  }

  console.log(`매일 ${at} 에 초기화한다`);
  // 기동하자마자 한 번 맞춰 둔다. 컨테이너를 새로 올린 직후 데이터가 없으면 화면이 비어 있다.
  await runOnce();

  for (;;) {
    const wait = msUntil(at);
    console.log(`다음 초기화까지 ${Math.round(wait / 60000)}분`);
    await new Promise((r) => setTimeout(r, wait));
    try {
      await runOnce();
    } catch (e) {
      // 한 번 실패했다고 컨테이너가 죽으면 그날 이후로 영영 초기화되지 않는다
      console.error('초기화 실패, 다음 시각에 다시 시도한다:', e.message);
    }
  }
}

main().catch((e) => {
  console.error('실패:', e.message);
  process.exit(1);
});
