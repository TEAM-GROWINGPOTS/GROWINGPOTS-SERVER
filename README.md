# 🥔 GROWINGPOTS

**학점과 이수 요건에 치이는 대학생을 위한 비주얼 학사 플래너**

복잡한 학점 계산은 이제 그만!  
졸업까지 남은 이수 영역과 학점을 한 화면에서 확인하고,  
앞으로의 수강 계획까지 미리 시뮬레이션해볼 수 있어요.

### 🥔 무엇을 할 수 있나요?

| 기능                    | 설명                                                     |
| ----------------------- | -------------------------------------------------------- |
| 🎯 이수 현황 대시보드   | 졸업까지 남은 이수 영역·학점 현황을 한 화면에서 확인해요 |
| 🔮 수강 계획 시뮬레이션 | 앞으로 들을 과목을 미리 넣어보며 예상 학점을 계산해요    |
| 📄 PDF 온보딩           | 성적표(PDF) 업로드만으로 이수 현황을 빠르게 불러와요     |

---

## TEAM-GROWINGPOTS-SERVER

<table width="100%">
  <tr>
    <td align="center" width="50%" style="padding:10px">
      <img src="https://github.com/5eoyng.png" width="180"/><br/>
      <a href="https://github.com/5eoyng" target="_blank" rel="noopener noreferrer"><strong>김서영</strong></a>
    </td>
    <td align="center" width="50%" style="padding:10px">
      <img src="https://github.com/turegold.png" width="180"/><br/>
      <a href="https://github.com/turegold" target="_blank" rel="noopener noreferrer"><strong>이용민</strong></a>
    </td>
  </tr>
</table>

---

## 💎 우리가 일하는 방식

백엔드는 화면에 안 보이는 만큼, "일단 동작한다"보다 "왜 그렇게 동작하는지 설명할 수 있다"를 더 중요하게 여겨요.  
서버는 두 명이 같이 짜는 코드고, 데이터는 한 번 잘못 쌓이면 되돌리기 어렵기 때문에 아래 원칙을 지켜요.

<br/>
<table width="100%">
  <tr>
    <td align="center" width="25%">
      <h3>🔬 근본 원인까지</h3>
      <p>동시성 버그, N+1,<br/>CI 플레이키 같은 문제는<br/>재현해서 증명하고<br/>고쳐요. 추측으로 안 덮어요.</p>
    </td>
    <td align="center" width="25%">
      <h3>🔒 데이터 정합성 우선</h3>
      <p>트랜잭션 격리수준,<br/>락, FK 제약까지<br/>고려하고 짜요.<br/>편한 코드보다 안전한 코드.</p>
    </td>
    <td align="center" width="25%">
      <h3>🧑‍🤝‍🧑 신뢰 머지 없음</h3>
      <p>급해도 리뷰 없이<br/>merge하지 않아요.<br/>서로의 PR을 즉시<br/>크로스 리뷰해요.</p>
    </td>
    <td align="center" width="25%">
      <h3>📜 계약으로 말하기</h3>
      <p>API 스펙과 에러 코드로<br/>프론트와 소통해요.<br/>Swagger 문서가<br/>곧 약속이에요.</p>
    </td>
  </tr>
</table>

<div align="center">

원인을 추적하는 끈기 · 데이터를 함부로 다루지 않는 태도 · 문서로 남기는 습관으로 함께해요

</div>

---

## 🛠️ 기술 스택

<table width="100%">
  <tr><th width="20%">카테고리</th><th>기술 스택</th></tr>
  <tr><td><b>Language</b></td><td>

![Java](https://img.shields.io/badge/Java%2021-007396?logo=openjdk&logoColor=white&style=for-the-badge)

</td></tr>
  <tr><td><b>Framework</b></td><td>

![Spring Boot](https://img.shields.io/badge/Spring%20Boot%203.5-6DB33F?logo=springboot&logoColor=white&style=for-the-badge) ![Spring Security](https://img.shields.io/badge/Spring%20Security-6DB33F?logo=springsecurity&logoColor=white&style=for-the-badge)

</td></tr>
  <tr><td><b>ORM</b></td><td>

![Spring Data JPA](https://img.shields.io/badge/Spring%20Data%20JPA-6DB33F?logo=spring&logoColor=white&style=for-the-badge) ![Hibernate](https://img.shields.io/badge/Hibernate-59666C?logo=hibernate&logoColor=white&style=for-the-badge)

</td></tr>
  <tr><td><b>Database</b></td><td>

![MySQL](https://img.shields.io/badge/MySQL-4479A1?logo=mysql&logoColor=white&style=for-the-badge) ![H2](https://img.shields.io/badge/H2%20(Test)-1F62A0?style=for-the-badge)

</td></tr>
  <tr><td><b>Auth</b></td><td>

![OAuth2](https://img.shields.io/badge/Kakao%20OAuth2-FFCD00?logo=kakaotalk&logoColor=black&style=for-the-badge) ![JWT](https://img.shields.io/badge/JWT-000000?logo=jsonwebtokens&logoColor=white&style=for-the-badge)

</td></tr>
  <tr><td><b>API Docs</b></td><td>

![Swagger](https://img.shields.io/badge/Swagger%20(springdoc)-85EA2D?logo=swagger&logoColor=black&style=for-the-badge)

</td></tr>
  <tr><td><b>PDF Parsing</b></td><td>

![Apache PDFBox](https://img.shields.io/badge/Apache%20PDFBox-D22128?style=for-the-badge)

</td></tr>
  <tr><td><b>Monitoring</b></td><td>

![Sentry](https://img.shields.io/badge/Sentry-362D59?logo=sentry&logoColor=white&style=for-the-badge) ![Prometheus](https://img.shields.io/badge/Actuator%20%2F%20Prometheus-E6522C?logo=prometheus&logoColor=white&style=for-the-badge)

</td></tr>
  <tr><td><b>Build Tool</b></td><td>

![Gradle](https://img.shields.io/badge/Gradle-02303A?logo=gradle&logoColor=white&style=for-the-badge)

</td></tr>
  <tr><td><b>Infra / CI·CD</b></td><td>

![AWS EC2](https://img.shields.io/badge/AWS%20EC2-FF9900?logo=amazonec2&logoColor=white&style=for-the-badge) ![GitHub Actions](https://img.shields.io/badge/GitHub%20Actions-2088FF?logo=githubactions&logoColor=white&style=for-the-badge)

</td></tr>
  <tr><td><b>Version Control</b></td><td>

![Git](https://img.shields.io/badge/Git-F05033?logo=git&logoColor=white&style=for-the-badge) ![GitHub](https://img.shields.io/badge/GitHub-121011?logo=github&logoColor=white&style=for-the-badge)

</td></tr>
  <tr><td><b>Cooperation</b></td><td>

![Notion](https://img.shields.io/badge/Notion-000000?logo=notion&logoColor=white&style=for-the-badge) ![Discord](https://img.shields.io/badge/Discord-5865F2?logo=discord&logoColor=white&style=for-the-badge) ![Figma](https://img.shields.io/badge/Figma-F24E1E?logo=figma&logoColor=white&style=for-the-badge)

</td></tr>
</table>

---

## 📁 폴더 구조

도메인 주도(domain-by-feature) 구조를 기반으로 해요. 각 도메인은 `controller / dto / entity / repository / service`로 나뉘고, 도메인에 속하지 않는 공통 설정·인프라 코드는 `global`에 모아요.

```
└── src/main/java/com/growingpots/
    ├── domain/
    │   ├── user/            # 회원, 온보딩, 학적 정보
    │   ├── transcript/      # 성적표(PDF) 파싱, 이수 과목
    │   ├── graduation/      # 졸업 요건 분석
    │   ├── planner/         # 수강 계획 시뮬레이션
    │   └── university/      # 학과·과목 마스터 데이터, 과목 검색
    └── global/
        ├── config/          # Security, CORS, Swagger 등 설정
        ├── security/        # JWT 인증 필터·핸들러
        ├── filter/
        ├── exception/        # GlobalExceptionHandler, BaseException
        ├── response/         # 공통 응답 포맷 (SuccessCode / ErrorCode)
        ├── aop/
        ├── discord/          # 배포·에러 알림 웹훅
        ├── dev/              # 로컬/테스트용 dev 전용 API
        └── entity/           # BaseTimeEntity 등 공통 엔티티
```

### Controller / Api 분리

각 도메인 컨트롤러는 `XxxController`(구현)와 `XxxApi`(Swagger 문서용 인터페이스)로 나눠요. Swagger 어노테이션이 컨트롤러 로직과 섞이지 않도록 분리하는 컨벤션이에요.

```
PlannerController implements PlannerApi
```

### 공통 응답 포맷

모든 API 응답은 `SuccessCode` / `ErrorCode`를 통해 `success`, `code`, `message`, `data` 형태로 통일해요.

---

## 🌿 GitHub 전략

### 브랜치 전략

GitHub Flow 기반으로 운영하되, 배포 안정성을 위해 `develop` 브랜치를 통합 브랜치로 추가해요.

| 브랜치    | 역할                                                 |
| --------- | ---------------------------------------------------- |
| `main`    | 배포 가능한 상태를 유지하는 최종 브랜치              |
| `develop` | 기능 브랜치들이 모이는 통합 브랜치. 모든 PR은 여기로 |

> **Merge 방식:** Squash Merge를 기본으로 해요. 커밋 히스토리가 깔끔하게 유지돼요.

### 브랜치 네이밍

`type/#issue-number-description` 형식으로 관리해요. (snake_case 또는 kebab-case)

```
feat/#41-search_courses
fix/#207-fix_enrollment_status
bug/#268-put_bug
refactor/#263-add_area
chore/#107-divide_DB
docs/#157-swagger-config
```

---

## 📝 PR & 커밋 컨벤션

### PR 작성

PR은 `.github/PULL_REQUEST_TEMPLATE.md` 템플릿 형식을 그대로 따라 작성해요 (체크리스트 / 작업 내용 / 관련 이슈 / 참고 사항).

**제목 형식:** `[type(#이슈번호)] 설명`

```
[feat(#194)] 이수 과목 목록 이수구분별 정렬
[fix(#207)] enrollmentStatus 수정
```

**라벨:** 작업 타입 라벨(`✨feat` / `fix` / `refactor` / `chore` / `docs`) + 작성자 라벨(`🫧용민` / `🐮서영`)을 함께 붙여요.

**리뷰어 / Assignee:** 팀원과 서로 교차 지정해요 (내가 작성 → 팀원이 리뷰어, assignee는 작성자 본인).

### 커밋 메시지

```
feat(#41): 과목 검색 API 추가
fix(#207): enrollmentStatus 수정
test(#41): 과목 검색 API 테스트 추가
chore: Gradle Test Retry 플러그인 추가
```

---

## 🔍 코드 리뷰

그로잉팟은 신뢰 머지를 하지 않는 것을 원칙으로 해요.  
코드를 작성하는 것으로 개발이 끝나는 게 아니라 리뷰를 거쳐 merge되는 순간까지가 개발이라고 생각해요.  
아무리 급해도 리뷰 없이 merge하지 않고, 서로의 PR을 즉시 크로스 리뷰하며 코드에 대한 이해와 책임을 함께 나눠요.

- ✅ 반드시 **PR을 통해** merge (직접 push 금지)
- ✅ 팀원 간 **크로스 리뷰** (내가 작성 → 팀원이 리뷰)

### 리뷰에서 중요하게 보는 것

- 동시성 / 트랜잭션 격리수준 등 데이터 정합성
- 예외 처리 / 에러 핸들링 (`ErrorCode` 일관성)
- N+1, 인덱스 등 쿼리 성능
- API 계약 (Swagger 문서와 실제 응답 일치)
- 테스트 커버리지

---

## 🚀 배포 & 모니터링

| 구분          | 방식                                                                 |
| ------------- | --------------------------------------------------------------------- |
| **CI**        | `develop` PR 시 GitHub Actions로 빌드·테스트 자동 실행                |
| **CD (dev)**  | `develop` 브랜치 push 시 GitHub Actions가 EC2(dev)로 자동 배포        |
| **CD (prod)** | `main` 브랜치 기준 GitHub Actions가 EC2(prod)로 배포                  |
| **에러 트래킹** | Sentry로 예외·스택트레이스 수집                                     |
| **알림**      | 배포 성공/실패를 Discord 웹훅으로 팀 채널에 알림                     |
| **헬스체크/지표** | Spring Actuator + Prometheus (`/actuator/health`, `/actuator/prometheus`) |

---

## 🗓️ 회의 & 이슈 관리

| 구분          | 방식                                                |
| ------------- | ---------------------------------------------------- |
| **회의 & 회고** | Discord 또는 대면 회의로 진행해요                    |
| **빠른 질문** | 바로 해결 가능한 건 카카오톡으로 즉시 물어봐요        |
| **이슈 관리** | GitHub Issues로 관리해요 (PR은 반드시 이슈와 연결)    |
