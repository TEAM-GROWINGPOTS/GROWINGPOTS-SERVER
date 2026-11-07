# Flyway 마이그레이션 작성 규칙

스키마는 이제 Hibernate가 자동으로 바꾸지 않음. 엔티티를 바꾸면 `src/main/resources/db/migration`에 마이그레이션 파일을 같이 추가해야 함. local, dev, prod 모두 `ddl-auto: validate`라서, 마이그레이션을 빠뜨리면 앱이 안 뜸.

## 기본 규칙

- 파일 이름은 `V{번호}__{설명}.sql`. 번호는 1씩 올리고, 설명은 영어 소문자와 `_`로 씀
  - 예: `V2__add_course_version.sql`, `V3__add_display_order_to_student_course.sql`
- **이미 머지된 마이그레이션 파일은 절대 고치지 않음.** Flyway가 파일 체크섬을 기록해 둬서, 고치면 다음 배포 때 앱이 안 뜸. 잘못 만들었으면 다음 번호로 고치는 마이그레이션을 새로 추가함
- 엔티티 변경과 마이그레이션은 같은 PR에 넣음
- 문법은 MySQL 8.4 기준 (RDS 버전)
- 되돌리는 기능(undo)은 무료 버전에 없음. 되돌릴 때도 새 마이그레이션으로 처리함

## 번호가 겹칠 때

두 명이 동시에 `V2`를 만들면, 나중에 머지하는 쪽이 develop 최신을 받아서 다음 번호로 바꾸고 올림. 아직 머지 안 된 파일이라 이름을 바꿔도 괜찮음.

## 작성할 때 주의할 것

- **NOT NULL 컬럼 추가:** 기존 행이 있어서 기본값이 없으면 실패함. `DEFAULT`를 주거나, 컬럼 추가 → 값 채우기 → NOT NULL 변경 순서로 나눠서 씀
- **컬럼 삭제·이름 변경:** 배포 중에는 이전 버전 앱이 잠깐 같이 돌 수 있음. 지금 코드가 아직 쓰는 컬럼은 지우지 말고, 코드에서 먼저 안 쓰게 바꾼 다음 마이그레이션에서 지움
- **enum 값 추가:** `ALTER TABLE ... MODIFY COLUMN ... enum(...)`으로 전체 값 목록을 다시 적어야 함. 기존 값을 빠뜨리면 그 값을 가진 행이 깨짐
- **이름 붙이기:** 새로 만드는 인덱스·제약은 Hibernate가 붙이는 해시 이름 대신 `idx_<테이블>_<컬럼>`, `uk_<테이블>_<컬럼>`, `fk_<테이블>_<컬럼>`처럼 알아볼 수 있게 붙임. V1에 있는 기존 이름은 dev DB와 맞춰야 해서 그대로 둠

## 환경별 동작

| 환경 | 처음 실행할 때 | 이후 |
|---|---|---|
| local | 빈 DB면 V1부터 실행 | 새 마이그레이션만 실행 |
| dev | 이미 테이블이 있어서 V1을 실행하지 않고 "적용됨"으로만 기록(baseline) | 새 마이그레이션만 실행 |
| prod | 빈 DB여야 함. 테이블이 있는데 Flyway 기록이 없으면 실행을 거부하고 앱이 안 뜸 | 새 마이그레이션만 실행 |
| test | Flyway 안 씀 (H2에 엔티티로 스키마 생성) | |

## 로컬 DB 준비

Flyway 도입 전에 `ddl-auto: update`로 만든 로컬 DB는 Flyway 기록이 없어서 앱이 안 뜸. 로컬 DB는 새로 만들면 됨.

```sql
DROP DATABASE growingpots;
CREATE DATABASE growingpots;
```

앱을 띄우면 V1부터 자동으로 적용됨.

## 마이그레이션 검증

평소 테스트는 H2라서 마이그레이션 파일이 실제로 도는지는 확인하지 못함. 그래서 `MigrationVerificationTest`가 실제 MySQL의 빈 DB에 마이그레이션을 전부 적용하고, 엔티티와 맞는지 Hibernate validate로 확인함. PR을 올리면 CI의 `migration` 잡이 자동으로 돌림.

로컬에서 돌리려면 Docker로 MySQL을 띄우고 환경 변수를 넣어서 실행하면 됨.

```bash
docker run -d --name growingpots-migration -e MYSQL_ROOT_PASSWORD=pw -e MYSQL_DATABASE=growingpots -p 127.0.0.1:3307:3306 mysql:8.4

MIGRATION_TEST_DB_URL='jdbc:mysql://127.0.0.1:3307/growingpots' MIGRATION_TEST_DB_PASSWORD=pw \
  ./gradlew test --tests '*MigrationVerificationTest'

docker rm -f growingpots-migration
```

환경 변수가 없으면 이 테스트는 건너뜀. 다시 돌릴 때는 빈 DB로 해야 하니 컨테이너를 새로 띄우면 됨.

## prod 첫 배포 때 할 것

prod DB(`growingpots_prod`)에는 Flyway 도입 전 예전 구조의 테이블이 남아 있음. prod는 아직 연 적이 없어서 데이터를 보존하지 않고 새로 만들기로 함.

1. 혹시 모르니 prod DB 백업
2. `growingpots_prod`의 테이블을 전부 지움 (DB는 그대로 둠)
3. 배포하면 Flyway가 V1부터 적용함
