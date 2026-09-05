# 배포 가이드 — 폰에서 열리는 데모 만들기

이 문서를 따라가면 `https://내도메인` 으로 아무나 접속해 볼 수 있는 데모가 만들어진다.
서버 관리 경험이 없어도 되도록 명령 하나하나를 적었다.

작업 시간은 **30~40분**이고, 대부분 도메인 DNS 전파를 기다리는 시간이다.

---

## 0. 무엇을 고를 것인가

### 서버

| | 가격 | 한국에서 응답 속도 | 확실성 |
|---|---|---|---|
| **Oracle Cloud 무료 티어** (일본 리전) | **무료** | 빠름 (~30ms) | **낮음** — 아래 설명 |
| **Vultr** (서울 리전) | $10~20/월 | 매우 빠름 (~5ms) | 높음 |
| **Hetzner** (독일) | €5.49/월 | 느림 (~250ms) | 높음 |

**Oracle 이 공짜인데 왜 "확실성 낮음" 인가.**
ARM 인스턴스 수요가 공급을 넘어서 `Out of host capacity` 오류가 상시로 난다.
며칠씩 재시도하는 사람도 있고, 재시도 스크립트를 돌리는 사람도 있다.
2026년 6월에는 예고 없이 무료 한도를 4 OCPU/24GB 에서 **2 OCPU/12GB 로 반토막** 냈다.
(그래도 이 프로젝트에는 충분하다.)

**권하는 순서**

1. **Oracle 을 먼저 시도한다.** 30분 안에 인스턴스가 만들어지면 그대로 쓴다. 계속 공짜다.
2. **용량 오류가 두세 번 반복되면 미련 없이 Vultr 로 간다.** 포트폴리오는 되는 게 중요하다.

느린 응답이 왜 문제가 되냐면, 이 프로젝트에서 제일 보여줄 것이
**"폰에서 등록하면 검사실 화면이 즉시 바뀐다"** 이기 때문이다.
독일 서버는 그 "즉시" 가 반 박자 늦다. 못 쓸 정도는 아니지만 인상이 깎인다.

> **이 문서의 3장부터는 어느 서버를 골랐든 똑같다.** 다른 건 1장뿐이다.

### 서버 사양

- **램 2GB 이상.** JVM·PostgreSQL·Redis·웹이 한 대에 같이 돈다
- **Ubuntu 24.04 LTS**
- 디스크 20GB 이상

### 도메인

**Cloudflare Registrar** 를 권한다. 원가로 판다(.com 연 $10.44, 2026년 11월부터 $11.15).
가비아는 .com 첫 해 19,800원이다. 국내 카드 결제와 한국어 지원이 필요하면 가비아도 괜찮다.

**도메인이 반드시 필요하다.** IP 주소만으로는 인증서를 받을 수 없고,
인증서가 없으면 폰 브라우저가 실시간 연결(`wss://`)을 막아 **실시간 갱신이 조용히 죽는다.**

---

## 1. 서버 만들기

### 1-A. Oracle Cloud (무료)

1. [oracle.com/cloud/free](https://www.oracle.com/cloud/free/) 에서 가입한다.
   신용카드를 요구하지만 Always Free 자원만 쓰면 청구되지 않는다.
2. **홈 리전을 `Japan Central (Osaka)` 또는 `Japan East (Tokyo)` 로 고른다.**
   한국(춘천) 리전은 ARM 인스턴스를 만들 수 없다. **홈 리전은 나중에 못 바꾼다.**
3. 좌측 메뉴 → **Compute → Instances → Create instance**
4. 설정
   - Image: **Ubuntu 24.04**
   - Shape: **Ampere → VM.Standard.A1.Flex**, OCPU **2**, 메모리 **12GB**
   - SSH 키: **Save private key** 를 눌러 내려받는다. 다시 못 받는다
5. **Create** 를 누른다.
   - `Out of host capacity` 가 뜨면 다른 가용 도메인(AD-1/2/3)으로 바꿔 재시도한다
   - 그래도 안 되면 1-B 로 간다

**Oracle 만의 함정 두 가지.** 이것 때문에 대부분 막힌다.

먼저 방화벽 규칙을 연다.
Instance 상세 → **Virtual cloud network** 클릭 → **Security Lists** → 기본 목록 →
**Add Ingress Rules** 로 두 줄을 넣는다.

| Source CIDR | IP Protocol | Destination Port |
|---|---|---|
| `0.0.0.0/0` | TCP | `80` |
| `0.0.0.0/0` | TCP | `443` |

그리고 **Ubuntu 이미지 안에도 별도의 iptables 규칙이 있다.** 위 설정만으로는 안 열린다.
3장에서 서버에 접속한 뒤 처리한다.

### 1-B. Vultr (서울, 유료)

1. [vultr.com](https://www.vultr.com/) 가입
2. **Deploy → Cloud Compute → Shared CPU**
3. 설정
   - Location: **Seoul**
   - Image: **Ubuntu 24.04 LTS**
   - Plan: 램 **2GB 이상** (4GB 를 권한다)
   - SSH Key: 등록해 두면 비밀번호 없이 접속한다
4. **Deploy Now**

방화벽은 기본으로 열려 있다. 추가 설정이 필요 없다.

### 1-C. Hetzner (독일, 가장 저렴)

1. [hetzner.com/cloud](https://www.hetzner.com/cloud/) 가입 (신분 확인을 요구할 수 있다)
2. 프로젝트 생성 → **Add Server**
3. 설정
   - Location: **Falkenstein** 또는 **Helsinki**
   - Image: **Ubuntu 24.04**
   - Type: **CX23** (2 vCPU / 4GB / €5.49)
   - SSH Key 등록
4. **Create & Buy now**

---

## 2. 도메인 연결하기

### 2-1. 도메인 구입

[dash.cloudflare.com](https://dash.cloudflare.com) → **Domain Registration → Register Domain**

이름은 아무거나 좋다. 예: `nurse-collab.com`, `이름-portfolio.com`

### 2-2. A 레코드 만들기

Cloudflare 대시보드 → 도메인 선택 → **DNS → Records → Add record**

| Type | Name | IPv4 address | Proxy status |
|---|---|---|---|
| A | `@` | 서버의 공인 IP | **DNS only (회색 구름)** |

**회색 구름이어야 한다.** 주황색 구름(프록시 켜짐)이면
Cloudflare 가 요청을 가로채서 **서버가 인증서를 받지 못한다.**
클릭해서 회색으로 바꾼다.

### 2-3. 전파 확인

집 컴퓨터에서:

```bash
nslookup 내도메인.com
```

서버 IP 가 나오면 된다. 보통 1~5분, 늦으면 30분 걸린다.
**이게 확인되기 전에는 다음으로 넘어가지 않는다.**
DNS 가 안 붙은 상태로 배포하면 인증서 발급에 실패하는데,
Let's Encrypt 는 실패 횟수도 세기 때문에 반복하면 한 시간쯤 묶인다.

---

## 3. 서버 준비하기

여기서부터는 **어느 서버를 골랐든 동일하다.**

### 3-1. 접속

```bash
ssh -i 내려받은키.key ubuntu@서버IP
```

- Oracle: 사용자 이름 `ubuntu`
- Vultr / Hetzner: 사용자 이름 `root` (SSH 키를 안 넣었으면 비밀번호로 접속)

키 권한 오류가 나면 (`UNPROTECTED PRIVATE KEY FILE`):

```bash
chmod 600 내려받은키.key
```

### 3-2. 최신화

```bash
sudo apt update && sudo apt upgrade -y
```

### 3-3. Docker 설치

```bash
curl -fsSL https://get.docker.com | sudo sh
```

`root` 가 아닌 사용자로 접속했다면 `sudo` 없이 쓰도록 등록한다.

```bash
sudo usermod -aG docker $USER
```

**적용하려면 한 번 나갔다 다시 들어와야 한다.**

```bash
exit
```

다시 접속한 뒤 확인한다.

```bash
docker run --rm hello-world
```

### 3-4. Oracle 을 골랐다면 — iptables 열기

**Oracle 이 아니면 이 단계를 건너뛴다.**

Oracle 의 Ubuntu 이미지는 이미지 안에서도 포트를 막는다.
1장에서 Security List 를 열었어도 이것까지 해야 열린다.

```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
```

```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
```

재부팅해도 남도록 저장한다.

```bash
sudo netfilter-persistent save
```

`netfilter-persistent` 가 없다는 오류가 나면 먼저 설치한다.

```bash
sudo apt install -y iptables-persistent
```

### 3-5. 스왑 만들기 (램 2GB 이하면 필수)

램이 모자랄 때 서버가 통째로 죽는 것을 막는다.

```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
```

재부팅해도 남도록 등록한다.

```bash
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

---

## 4. 배포하기

### 4-1. 코드 받기

```bash
git clone https://github.com/leebriller96/nurse-collab.git && cd nurse-collab
```

### 4-2. 설정 파일 만들기

```bash
cp .env.example .env
```

비밀값 두 개를 만든다. 출력된 문자열을 복사해 둔다.

```bash
openssl rand -base64 24 && openssl rand -base64 48
```

첫 번째가 DB 비밀번호, 두 번째가 토큰 서명 키다.

```bash
nano .env
```

이렇게 채운다.

```
POSTGRES_PASSWORD=(첫 번째 문자열)
JWT_SECRET=(두 번째 문자열)

SITE_ADDRESS=내도메인.com
ACME_EMAIL=내이메일@example.com

WEB_PORT=80
WEB_TLS_PORT=443

JVM_HEAP_PERCENT=50
DEMO_RESET_AT=04:00
```

- 램이 2GB 면 `JVM_HEAP_PERCENT=40` 으로 낮춘다
- `SITE_ADDRESS` 에 `https://` 를 **붙이지 않는다.** 도메인만 쓴다

`Ctrl+O` → `Enter` → `Ctrl+X` 로 저장하고 나온다.

### 4-3. 올리기

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

**처음에는 5~15분 걸린다.** Gradle 이 의존성을 받고 이미지를 만든다.
ARM 서버(Oracle)면 조금 더 걸린다.

### 4-4. 확인

```bash
docker compose -f docker-compose.prod.yml ps
```

다섯 개가 전부 `Up` 이어야 한다. `postgres` `redis` `backend` `web` `demo-reset`

인증서가 잘 나왔는지 본다.

```bash
docker compose -f docker-compose.prod.yml logs web | grep -i certificate
```

`certificate obtained successfully` 가 보이면 성공이다.

이제 브라우저에서 **`https://내도메인.com`** 을 연다.
자물쇠 표시가 보이고 로그인 화면이 나오면 끝이다.

---

## 5. 폰에서 확인하기

폰 브라우저로 같은 주소를 연다. 로그인 화면의 **3병동 김간호** 버튼을 누른다.

제대로 됐는지 보는 방법: **화면 오른쪽 아래 구석의 아주 작은 점**이
초록색이면 실시간 연결이 살아 있는 것이다. 회색이면 연결이 안 된 것이고,
십중팔구 인증서 문제다. 6장을 본다.

폰과 노트북을 나란히 놓고 시연해 본다.

1. 폰에서 **김간호(ward01)** 로 로그인 → 환자 → 김OO → 이송 요청 → 뇌 MRI 선택
2. 노트북에서 **박간호(mri01)** 로 로그인 → 들어온 요청
3. 폰에서 요청을 등록하면 **노트북 목록에 즉시 나타난다**

---

## 6. 문제가 생겼을 때

### 사이트가 안 열린다

```bash
docker compose -f docker-compose.prod.yml logs web --tail 50
```

- `no such host` / DNS 오류 → 2장의 A 레코드를 다시 확인한다
- 아무 로그도 없고 브라우저가 계속 기다린다 → **방화벽이다.** Oracle 이면 3-4 를 안 한 것이다

포트가 실제로 열렸는지는 집 컴퓨터에서 확인한다.

```bash
curl -I http://내도메인.com
```

### 자물쇠가 안 뜬다 / 실시간 점이 회색이다

인증서를 못 받은 것이다. 원인은 대개 셋 중 하나다.

1. **Cloudflare 프록시가 주황색 구름이다.** 회색으로 바꾼다
2. **80 포트가 막혀 있다.** 인증서 발급 요청이 80으로 들어온다. 443만 열면 안 된다
3. **DNS 가 아직 서버를 안 가리킨다**

고친 뒤 다시 시도한다.

```bash
docker compose -f docker-compose.prod.yml restart web
```

### 서버가 자꾸 멈춘다

램 부족이다.

```bash
free -h
```

`.env` 의 `JVM_HEAP_PERCENT` 를 낮추고(2GB 서버면 `35`) 3-5 의 스왑을 만든 뒤 다시 올린다.

```bash
docker compose -f docker-compose.prod.yml up -d
```

### 백엔드가 안 뜬다

```bash
docker compose -f docker-compose.prod.yml logs backend --tail 50
```

`JWT_SECRET` 이 비었거나 너무 짧으면 기동하지 않는다. 32바이트 이상이어야 한다.

---

## 7. 운영

### 코드를 고친 뒤 반영하기

```bash
cd ~/nurse-collab && git pull && docker compose -f docker-compose.prod.yml up -d --build
```

### 데모 데이터

**매일 새벽 4시에 처음 상태로 돌아간다.** 데모 계정 비밀번호가 공개돼 있어
누구나 로그인해 데이터를 바꿀 수 있기 때문이다.

지금 당장 되돌리려면:

```bash
docker compose -f docker-compose.prod.yml restart demo-reset
```

시각을 바꾸려면 `.env` 의 `DEMO_RESET_AT` 를 고치고 위 명령을 실행한다.

### 인증서

**아무것도 안 해도 된다.** 만료 30일 전에 알아서 갱신한다.
nginx + certbot 대신 Caddy 를 쓴 이유가 이것이다 —
두 달 뒤 갱신이 조용히 실패해서 면접 직전에 사이트가 죽는 일이 없다.

### 서버 재부팅

컨테이너가 `restart: unless-stopped` 라 자동으로 다시 뜬다. 할 일이 없다.

### 로그 보기

```bash
docker compose -f docker-compose.prod.yml logs -f --tail 50
```

`Ctrl+C` 로 빠져나온다.

### 다 내리기

```bash
docker compose -f docker-compose.prod.yml down
```

데이터까지 지우려면 `-v` 를 붙인다. **되돌릴 수 없다.**

---

## 8. 알아둘 것

**이건 가상 데이터로 도는 데모다.** 실제 병원에서 쓰려면 전혀 다른 이야기가 된다 —
개인정보 영향평가, 접근권한 심사, EMR 연동, 망분리 요건이 붙는다.
개발 기간의 문제가 아니라 조직과 규제 절차라 몇 달 단위다.

**데모 계정 비밀번호는 README 에 공개돼 있다.** 의도한 것이다.
누구나 눌러 볼 수 있어야 포트폴리오로 의미가 있고, 대신 매일 초기화한다.
실제 데이터를 넣을 생각이면 그 전에 계정부터 바꿔야 한다.
