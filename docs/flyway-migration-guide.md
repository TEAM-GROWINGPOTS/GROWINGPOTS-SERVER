# Flyway 마이그레이션 작성 규칙

스키마는 이제 Hibernate가 자동으로 바꾸지 않음. 엔티티를 바꾸면 `src/main/resources/db/migration`에 마이그레이션 파일을 같이 추가해야 함. local, dev, prod 모두 `ddl-auto: validate`라서, 테이블이나 컬럼을 마이그레이션에서 빠뜨리면 앱이 안 뜸.

단 `validate`는 테이블·컬럼이 있는지와 타입만 확인함. 인덱스, 유니크, 외래 키, NOT NULL, enum 값 목록은 마이그레이션에서 빠뜨려도 앱이 그냥 뜸. 이런 걸 바꿨으면 마이그레이션에 들어갔는지 PR에서 직접 확인해야 함.

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
- **enum 값 추가·변경:** `@Enumerated(EnumType.STRING)` 필드는 DB에 MySQL `enum` 타입으로 들어가 있어서, Java enum에 값을 추가하거나 이름을 바꾸면 마이그레이션도 같이 써야 함. `validate`는 enum 값 목록을 비교하지 않아서 빠뜨려도 앱은 그냥 뜨고, 그 값을 저장하는 순간 `Data truncated for column` 에러가 남. 마이그레이션은 `ALTER TABLE ... MODIFY COLUMN ... enum(...)`으로 기존 값까지 전체 목록을 다시 적어야 함. 기존 값을 빠뜨리면 그 값을 가진 행이 깨짐
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

이 테스트도 같은 `validate`를 써서 인덱스·유니크·외래 키·NOT NULL·enum 값 누락은 못 잡음 (V1에서 일부러 빼고 돌려 봤는데 통과함).

## 마이그레이션이 실패했을 때

MySQL은 테이블 생성·변경을 되돌리지 못함. 그래서 마이그레이션이 중간에 실패하면 일부만 적용된 채로 남고, `flyway_schema_history`에 실패 기록(`success = 0`)이 남음. 이 상태로는 다음 배포 때 Flyway가 실행을 거부함.

- **빈 DB에 V1을 처음 적용하다 실패한 경우** (local, prod 첫 배포): 데이터가 없으니 `flyway_schema_history`까지 테이블을 전부 지우고, 원인을 고친 뒤 다시 배포
- **데이터가 있는 DB에서 V2 이후가 실패한 경우**: 테이블을 지우면 안 됨. 실패한 마이그레이션에서 이미 적용된 부분을 직접 되돌리고, `flyway_schema_history`에서 실패 기록 행을 지운 뒤, 마이그레이션을 고쳐서 다시 배포

두 번째 경우 때문에 마이그레이션 파일 하나에는 관련된 변경만 작게 넣는 게 좋음. 어디까지 적용됐는지 찾기 쉬움.

## prod 첫 배포 때 할 것

prod DB(`growingpots_prod`)에는 Flyway 도입 전 예전 구조의 테이블이 남아 있음. prod는 아직 연 적이 없어서 데이터를 보존하지 않고 새로 만들기로 함.

1. 혹시 모르니 prod DB 백업
2. `growingpots_prod`의 테이블을 전부 지움 (DB는 그대로 둠)
3. 배포하면 Flyway가 V1부터 적용함
4. V1이 중간에 실패하면 위의 "마이그레이션이 실패했을 때"의 첫 번째 경우대로 정리하고 다시 배포
