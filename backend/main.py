"""
CrocodileDetector – URL 분석 백엔드  v3.1
===========================================

파이프라인:
  Layer 1 │ urlscan.io  → 전문 보안 엔진 판정 (주심)
  Layer 2 │ WHOIS       → 신규 도메인 보완 탐지 (부심)
              .kr / .한국 → KISA Open API
              그 외        → python-whois

판정 원칙:
  DANGER  → urlscan malicious=True
  CAUTION → urlscan score ≥ 20
          → urlscan 실패(DNS 오류 등) — 이유 무관하고 CAUTION
          → urlscan 성공했지만 도메인 7일 이내 신생
  SAFE    → 그 외
"""

import asyncio
import logging
import os
import re
from datetime import datetime, timezone
from urllib.parse import urlparse

import httpx
import whois
from dotenv import load_dotenv
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel, field_validator

# ═══════════════════════════════════════════════════════════════════════════════
# 초기화
# ═══════════════════════════════════════════════════════════════════════════════

load_dotenv()

URLSCAN_API_KEY: str = os.getenv("URLSCAN_API_KEY", "")
KISA_API_KEY: str    = os.getenv("KISA_API_KEY", "")

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s – %(message)s",
)
log = logging.getLogger("crocodile")

app = FastAPI(title="CrocodileDetector API", version="3.1.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ═══════════════════════════════════════════════════════════════════════════════
# 상수
# ═══════════════════════════════════════════════════════════════════════════════

URLSCAN_SUBMIT_URL = "https://urlscan.io/api/v1/scan/"
URLSCAN_RESULT_URL = "https://urlscan.io/api/v1/result/{uuid}/"
KISA_WHOIS_URL     = "https://whois.kisa.or.kr/openapi/whois.jsp"

POLL_INTERVAL_SEC  = 5
POLL_MAX_RETRIES   = 12    # 5초 × 12 = 최대 60초

HTTPX_TIMEOUT      = 20.0

URLSCAN_CAUTION_SCORE = 20  # urlscan score 이상이면 CAUTION
WHOIS_DANGER_AGE_DAYS = 7   # 도메인 생성 n일 이내면 CAUTION (urlscan 성공 시)
WHOIS_CAUTION_AGE_DAYS = 60 # urlscan 실패 시 이내면 CAUTION 사유 추가


# ═══════════════════════════════════════════════════════════════════════════════
# 요청 / 응답 스키마
# ═══════════════════════════════════════════════════════════════════════════════

class URLRequest(BaseModel):
    url: str

    @field_validator("url")
    @classmethod
    def ensure_scheme(cls, v: str) -> str:
        v = v.strip()
        if not v.startswith(("http://", "https://")):
            v = "https://" + v
        return v


class AnalysisResponse(BaseModel):
    verdict: str
    verdict_ko: str
    verdict_reason: str

    submitted_url: str
    domain: str
    final_url: str | None
    is_redirected: bool

    urlscan_uuid: str | None
    urlscan_success: bool
    urlscan_malicious: bool | None
    urlscan_score: int | None
    server_country: str | None
    screenshot_url: str | None

    whois_source: str
    creation_date: str | None
    domain_age_days: int | None

    analyzed_at: str


# ═══════════════════════════════════════════════════════════════════════════════
# 공통 유틸
# ═══════════════════════════════════════════════════════════════════════════════

def extract_domain(url: str) -> str:
    host = urlparse(url).netloc or urlparse(url).path
    return host.split(":")[0].lower().strip()


def is_kr_domain(domain: str) -> bool:
    return domain.endswith(".kr") or domain.endswith(".한국")


def now_utc() -> datetime:
    return datetime.now(timezone.utc)


def calc_age_days(creation: datetime) -> int:
    if creation.tzinfo is None:
        creation = creation.replace(tzinfo=timezone.utc)
    return max((now_utc() - creation).days, 0)


# ═══════════════════════════════════════════════════════════════════════════════
# Layer 1 – urlscan.io  (주심)
# ═══════════════════════════════════════════════════════════════════════════════

async def _urlscan_submit(client: httpx.AsyncClient, url: str) -> str | None:
    if not URLSCAN_API_KEY:
        log.warning("URLSCAN_API_KEY 미설정 – urlscan 건너뜀")
        return None
    try:
        r = await client.post(
            URLSCAN_SUBMIT_URL,
            headers={"API-Key": URLSCAN_API_KEY, "Content-Type": "application/json"},
            json={"url": url, "visibility": "unlisted"},  # 사용자 URL 비공개 필수
            timeout=HTTPX_TIMEOUT,
        )
    except httpx.RequestError as e:
        log.error("urlscan 제출 네트워크 오류: %s", e)
        return None

    if r.status_code == 429:
        log.warning("urlscan 요청 한도 초과 (429)")
        return None
    if r.status_code not in (200, 201):
        log.warning("urlscan 제출 실패 HTTP %s: %s", r.status_code, r.text[:200])
        return None

    uuid = r.json().get("uuid")
    log.info("urlscan 제출 완료 UUID=%s", uuid)
    return uuid


async def _urlscan_poll(client: httpx.AsyncClient, uuid: str) -> dict | None:
    result_url = URLSCAN_RESULT_URL.format(uuid=uuid)
    for attempt in range(1, POLL_MAX_RETRIES + 1):
        await asyncio.sleep(POLL_INTERVAL_SEC)
        try:
            r = await client.get(
                result_url,
                headers={"API-Key": URLSCAN_API_KEY},  # 결과 조회 시도 API 키 필수
                timeout=HTTPX_TIMEOUT,
            )
        except httpx.RequestError as e:
            log.error("urlscan 폴링 네트워크 오류: %s", e)
            continue

        if r.status_code == 200:
            log.info("urlscan 결과 수신 (시도 %d/%d)", attempt, POLL_MAX_RETRIES)
            return r.json()
        if r.status_code == 404:
            log.debug("urlscan 결과 대기 중 (시도 %d/%d)", attempt, POLL_MAX_RETRIES)
            continue
        log.warning("urlscan 폴링 HTTP %s", r.status_code)

    log.error("urlscan 폴링 타임아웃 uuid=%s", uuid)
    return None


def _parse_urlscan(data: dict, submitted_url: str) -> dict:
    verdicts  = data.get("verdicts", {}).get("overall", {})
    page      = data.get("page", {})
    task      = data.get("task", {})

    malicious = bool(verdicts.get("malicious", False))
    score     = int(verdicts.get("score") or 0)

    # 리다이렉트: 도메인 자체가 바뀐 경우만 True
    final_url     = page.get("url") or None
    submitted_dom = extract_domain(submitted_url)
    final_dom     = extract_domain(final_url) if final_url else submitted_dom
    is_redirected = (submitted_dom != final_dom) and bool(final_url)

    return {
        "urlscan_malicious": malicious,
        "urlscan_score"    : score,
        "server_country"   : page.get("country"),
        "screenshot_url"   : task.get("screenshotURL"),
        "final_url"        : final_url if is_redirected else None,
        "is_redirected"    : is_redirected,
    }


async def run_urlscan(client: httpx.AsyncClient, url: str) -> dict:
    empty = {
        "urlscan_uuid"     : None,
        "urlscan_success"  : False,   # 성공 여부 플래그 (판정 로직에서 사용)
        "urlscan_malicious": None,
        "urlscan_score"    : None,
        "server_country"   : None,
        "screenshot_url"   : None,
        "final_url"        : None,
        "is_redirected"    : False,
    }

    uuid = await _urlscan_submit(client, url)
    if not uuid:
        return empty

    result = await _urlscan_poll(client, uuid)
    if not result:
        return {**empty, "urlscan_uuid": uuid}

    return {
        **_parse_urlscan(result, url),
        "urlscan_uuid"   : uuid,
        "urlscan_success": True,   # 여기까지 왔으면 성공
    }


# ═══════════════════════════════════════════════════════════════════════════════
# Layer 2 – WHOIS  (부심)
#
# 목적: urlscan이 놓치는 신규 피싱 도메인 탐지
#   - 방금 만든 피싱 도메인은 urlscan DB에 없어 score=0, malicious=False 가능
#   - .kr 도메인은 KISA API로 정확한 생성일 조회 (urlscan이 KISA 레지스트리
#     WHOIS를 못 읽는 경우 보완)
# ═══════════════════════════════════════════════════════════════════════════════

_DATE_PATTERNS = [
    re.compile(r"(\d{4})\.\s*(\d{2})\.\s*(\d{2})\."),  # 2020. 01. 15.
    re.compile(r"(\d{4})-(\d{2})-(\d{2})"),              # 2020-01-15
    re.compile(r"(\d{4})(\d{2})(\d{2})"),                # 20200115
]


def _parse_date(raw: str) -> datetime | None:
    for pat in _DATE_PATTERNS:
        m = pat.search(raw)
        if m:
            try:
                return datetime(
                    int(m.group(1)), int(m.group(2)), int(m.group(3)),
                    tzinfo=timezone.utc,
                )
            except ValueError:
                continue
    return None


def _find_reg_date(d: dict, depth: int = 0) -> str | None:
    """KISA 응답 JSON 재귀 탐색. API 버전마다 구조가 달라 유연하게 처리."""
    if depth > 4:
        return None
    targets = {"regDate", "reg_date", "createdDate", "created_date", "registrationDate"}
    for k, v in d.items():
        if k in targets and v:
            return str(v)
        if isinstance(v, dict):
            found = _find_reg_date(v, depth + 1)
            if found:
                return found
    return None


async def _kisa_whois(client: httpx.AsyncClient, domain: str) -> dict:
    empty = {"creation_date": None, "domain_age_days": None, "whois_source": "unavailable"}

    if not KISA_API_KEY:
        log.warning("KISA_API_KEY 미설정 – KISA WHOIS 건너뜀")
        return empty

    try:
        r = await client.get(
            KISA_WHOIS_URL,
            params={"query": domain, "key": KISA_API_KEY, "answer": "json"},
            timeout=HTTPX_TIMEOUT,
        )
    except httpx.RequestError as e:
        log.error("KISA WHOIS 네트워크 오류: %s", e)
        return empty

    if r.status_code != 200:
        log.warning("KISA WHOIS HTTP %s", r.status_code)
        return empty

    try:
        data = r.json()
    except Exception as e:
        log.error("KISA WHOIS JSON 파싱 오류: %s", e)
        return empty

    raw_date = _find_reg_date(data)
    if not raw_date:
        log.warning("KISA 응답에서 등록일 필드 없음")
        return empty

    creation = _parse_date(raw_date)
    if not creation:
        log.warning("KISA 날짜 파싱 실패: '%s'", raw_date)
        return empty

    return {
        "creation_date"   : creation.strftime("%Y-%m-%d"),
        "domain_age_days" : calc_age_days(creation),
        "whois_source"    : "kisa",
    }


def _python_whois_sync(domain: str) -> dict:
    """동기 함수 – 반드시 run_in_executor로 호출 (이벤트루프 블로킹 방지)."""
    empty = {"creation_date": None, "domain_age_days": None, "whois_source": "unavailable"}
    try:
        w = whois.whois(domain)
        creation = w.creation_date
        if creation is None:
            return empty
        if isinstance(creation, list):
            creation = creation[0]
        if not isinstance(creation, datetime):
            return empty
        return {
            "creation_date"   : creation.strftime("%Y-%m-%d"),
            "domain_age_days" : calc_age_days(creation),
            "whois_source"    : "python-whois",
        }
    except Exception as e:
        log.warning("python-whois 실패 (%s): %s", domain, e)
        return empty


async def run_whois(client: httpx.AsyncClient, domain: str) -> dict:
    loop = asyncio.get_event_loop()
    if is_kr_domain(domain):
        log.info("KISA WHOIS 조회: %s", domain)
        result = await _kisa_whois(client, domain)
        if result["creation_date"] is None:
            log.info("KISA 실패 → python-whois 폴백: %s", domain)
            result = await loop.run_in_executor(None, _python_whois_sync, domain)
    else:
        log.info("python-whois 조회: %s", domain)
        result = await loop.run_in_executor(None, _python_whois_sync, domain)
    return result


# ═══════════════════════════════════════════════════════════════════════════════
# 종합 판정
#
# urlscan 판정을 최대한 존중하고, WHOIS는 urlscan이 놓쳤을 때만 보정.
#
# Step 1: urlscan malicious=True          → DANGER
# Step 2: urlscan score ≥ 20             → CAUTION
# Step 3: urlscan 실패 (DNS 오류 등)      → CAUTION (이유 무관)
# Step 4: urlscan 성공, 도메인 ≤ 7일     → CAUTION (미탐지 보완)
# Step 5: 그 외                           → SAFE
# ═══════════════════════════════════════════════════════════════════════════════

def determine_verdict(
    urlscan_malicious : bool | None,
    urlscan_score     : int | None,
    urlscan_success   : bool,
    age_days          : int | None,
) -> tuple[str, str, str]:

    # Step 1: urlscan 악성 판정 (주심 최종 결정)
    if urlscan_malicious is True:
        return (
            "DANGER", "위험",
            "urlscan.io 보안 엔진이 악성 사이트로 판정했습니다",
        )

    # Step 2: urlscan score 기반 (urlscan 자체 기준 그대로 활용)
    if urlscan_score is not None and urlscan_score >= URLSCAN_CAUTION_SCORE:
        return (
            "CAUTION", "주의",
            f"urlscan.io 위험 점수 {urlscan_score}/100",
        )

    # Step 3: urlscan 실패 → 이유 무관하고 CAUTION
    # DNS 오류, 타임아웃, API 오류 모두 포함
    # 정상 사이트라면 urlscan이 반드시 응답을 줘야 함
    if not urlscan_success:
        if age_days is not None and age_days <= WHOIS_CAUTION_AGE_DAYS:
            return (
                "CAUTION", "주의",
                f"urlscan.io 검사 불가 + 도메인 생성 {age_days}일 이내",
            )
        return (
            "CAUTION", "주의",
            "urlscan.io 검사 불가 (DNS 오류 또는 응답 없음) — 접속 자제 권고",
        )

    # Step 4: urlscan은 안전하다고 했지만 도메인이 너무 신생
    # 방금 만든 피싱 도메인은 urlscan DB에 없어 낮은 점수가 나올 수 있음
    if age_days is not None and age_days <= WHOIS_DANGER_AGE_DAYS:
        return (
            "CAUTION", "주의",
            f"도메인 생성 {age_days}일 이내 — urlscan 미탐지 가능성 있는 신규 도메인",
        )

    # Step 5: 안전
    reasons = []
    if urlscan_malicious is False:
        reasons.append("urlscan.io 악성 판정 없음")
    if urlscan_score is not None:
        reasons.append(f"urlscan 점수 {urlscan_score}/100")
    if age_days is not None:
        reasons.append(f"도메인 생성 {age_days}일")
    else:
        reasons.append("도메인 생성일 확인 불가")

    return (
        "SAFE", "안전",
        " / ".join(reasons) if reasons else "특이사항 없음",
    )


# ═══════════════════════════════════════════════════════════════════════════════
# 예외 핸들러
# ═══════════════════════════════════════════════════════════════════════════════

@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    log.error("처리되지 않은 예외: %s", exc, exc_info=True)
    return JSONResponse(
        status_code=500,
        content={"detail": f"서버 내부 오류: {type(exc).__name__}"},
    )


# ═══════════════════════════════════════════════════════════════════════════════
# 라우터
# ═══════════════════════════════════════════════════════════════════════════════

@app.get("/", include_in_schema=False)
async def serve_index():
    path = os.path.join(os.path.dirname(__file__), "index.html")
    if os.path.isfile(path):
        return FileResponse(path, media_type="text/html")
    return JSONResponse({"message": "CrocodileDetector API v3.1"})


@app.get("/health")
async def health_check():
    return {
        "status"          : "ok",
        "urlscan_key_set" : bool(URLSCAN_API_KEY),
        "kisa_key_set"    : bool(KISA_API_KEY),
    }


@app.post("/analyze/url", response_model=AnalysisResponse)
async def analyze_url(body: URLRequest):
    url    = body.url
    domain = extract_domain(url)

    if not domain:
        return JSONResponse(
            status_code=422,
            content={"detail": "유효한 도메인을 파싱할 수 없습니다."},
        )

    log.info("▶ 분석 시작: %s (도메인: %s)", url, domain)

    async with httpx.AsyncClient(follow_redirects=False) as client:
        urlscan_result, whois_result = await asyncio.gather(
            run_urlscan(client, url),
            run_whois(client, domain),
        )

    verdict, verdict_ko, reason = determine_verdict(
        urlscan_malicious = urlscan_result.get("urlscan_malicious"),
        urlscan_score     = urlscan_result.get("urlscan_score"),
        urlscan_success   = urlscan_result.get("urlscan_success", False),
        age_days          = whois_result.get("domain_age_days"),
    )

    log.info("◀ 판정: [%s] %s | %s", verdict, domain, reason)

    return AnalysisResponse(
        verdict          = verdict,
        verdict_ko       = verdict_ko,
        verdict_reason   = reason,
        submitted_url    = url,
        domain           = domain,
        final_url        = urlscan_result.get("final_url"),
        is_redirected    = urlscan_result.get("is_redirected", False),
        urlscan_uuid     = urlscan_result.get("urlscan_uuid"),
        urlscan_success  = urlscan_result.get("urlscan_success", False),
        urlscan_malicious= urlscan_result.get("urlscan_malicious"),
        urlscan_score    = urlscan_result.get("urlscan_score"),
        server_country   = urlscan_result.get("server_country"),
        screenshot_url   = urlscan_result.get("screenshot_url"),
        whois_source     = whois_result.get("whois_source", "unavailable"),
        creation_date    = whois_result.get("creation_date"),
        domain_age_days  = whois_result.get("domain_age_days"),
        analyzed_at      = now_utc().strftime("%Y-%m-%dT%H:%M:%SZ"),
    )
