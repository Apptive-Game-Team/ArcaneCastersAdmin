# 밸런스 스프레드시트

`/admin/spread-sheets/game-objects`는 Deploy와 Dev의 객체, parameter 값, 태그,
마법 분류와 객체 별칭을 각각 읽습니다. 필터 기준 DB와 표시 DB는 별도로 선택합니다.
`Spawn` 또는 `unit`/`mob` 태그로 유닛을 찾으며 태그는 완전 일치와 AND 조건을 사용합니다.

## 계산과 출처

- 개체 DPS = 공격력 / 공격 주기
- 총 DPS = 개체 DPS × 소환 수
- DPS/마나 = 총 DPS / 소환 마나
- 총 HP = 개체 HP × 소환 수
- HP/마나 = 총 HP / 소환 마나

총계는 유닛의 양의 정수 수량에만 적용합니다. 건물과 효과의 `quantity`는 효과 생성
횟수일 수 있으므로 소환 수로 가정하지 않습니다. 누락 수량을 1로 채우지 않습니다.
0 피해는 유효한 값이며 0 이하의 분모, 누락 값, 무한대 결과는 N/A로 표시합니다.
기본 공격의 단일 대상 비교 수치이며 광역, 연쇄, 지속 피해나 공격 불가 시간은 포함하지 않습니다.

`magic_game_object_aliases`를 따라 군집 소환의 HP·공격력 등은 실제 유닛 객체에서,
수량·마나는 소환 마법 이름의 객체에서 읽습니다. 원본 수량과 소환 수를 따로 보여줍니다.
참조 셀은 읽기 전용이며 원본 링크에서 수정합니다. 계산 근거에는 원본 parameter 이름이 표시됩니다.

## 공격 범위와 참조 등록

광역 피해 반경은 attack_area_radius 열로 표시합니다. 원형 공격은 π × 반경²으로 면적을
표시하며, 이 면적은 대상 수나 DPS 배율이 아닙니다. 사거리, 충돌 반지름, 효과 반경을
자동으로 광역 피해 반경으로 간주하지 않습니다. 폭·길이와 빔 폭은 별도 범위 항목입니다.

화면의 parameter 참조 등록·관리에서 DB, 표시 객체·parameter, 원본 객체·parameter,
배율을 선택해 저장합니다. Fire Spirit의 광역 피해 반경은 attack_range × 0.5처럼
표현할 수 있습니다. 값 자체는 원본 링크로 이동해 수정합니다.

참조는 balance_parameter_references에 Deploy/Dev별로 저장합니다. 새로고침과 다른 관리자
세션에서도 유지되며, 브라우저 보기 설정과 분리됩니다. 참조 등록·수정·삭제는 즉시
저장하고, 숫자 편집은 기존 변경 저장 버튼으로 별도 저장합니다. 참조 삭제 후에는
원본 값이나 마법 객체 별칭을 다시 사용합니다. 참조를 등록해도 전투 코드가 바뀌지는 않습니다.

서버는 존재하지 않는 원본, 계산 지표를 원본으로 쓰는 설정, 0 이하·무한대 배율,
자기 참조·다단계 순환·마법 객체 별칭을 통한 순환을 거부합니다. 다른 참조가 의존하는
가상 항목을 삭제해 원본이 사라지는 경우도 거부합니다. 저장 중 테이블 잠금으로
두 관리자의 동시 변경이 순환을 만드는 것을 막습니다.

Database V050_20261026__add_balance_parameter_references.sql을 먼저 적용해야 합니다.
초기 참조는 기존 교차 객체 참조와 Lightning Cloud, Magma Spirit, Firework Tower,
Crater Carrier, Fire Spirit의 확인된 광역 반경을 포함합니다. 이후 변경은 시트에서 관리합니다.

## 편집과 보기

필터·정렬·선택 비교·열 순서·그룹 접힘 상태는 브라우저에 저장합니다.
저장 전 숫자 변경은 관련 지표와 참조 셀에 즉시 반영됩니다. 셀별 또는 전체 되돌리기를
지원하며 변경 목록에는 현재 숨겨진 변경과 참조 영향도 포함합니다.
모두 N/A인 열은 현재 필터 결과의 양쪽 DB를 기준으로 숨기고, 0은 숨기지 않습니다.

저장은 기존 parameter API를 사용합니다. 필터 밖의 변경도 함께 저장하며 전체 복사 버튼은
필터와 무관하게 DB 전체를 대상으로 합니다. DB 간 저장은 원자적이지 않으므로 실패하면
일부 반영 여부를 확인해야 합니다. 실패 시 화면의 편집값은 유지합니다.

## 검증

Java 21에서 `./gradlew test`를 실행합니다.
계산 테스트는 `node --test src/test/js/balance-model.test.cjs`로 실행합니다.
브라우저 테스트에는 Node에서 접근 가능한 `playwright` 패키지와 설치된 Edge가 필요합니다.
`node --test --test-timeout=90000 src/test/js/balance-sheet.browser.test.cjs`로 실행하며
Chrome을 사용할 경우 `BROWSER_CHANNEL=chrome`을 지정합니다.
브라우저 테스트는 가상 호스트와 fixture를 사용하고 실제 DB를 수정하지 않습니다.

실제 PostgreSQL 저장·재조회·동시 순환 방지 테스트는 임시 DB에만 실행하세요.
BALANCE_TEST_DB_URL, BALANCE_TEST_DB_USER, BALANCE_TEST_MIGRATION(위 V050 파일의 절대 경로)을
설정하고 ./gradlew test를 실행하면 BalanceReferenceJdbcTest가 함께 실행됩니다.
이 테스트는 balance_primary와 balance_secondary 스키마 및 테스트 데이터를 생성하므로
비어 있는 전용 테스트 DB가 필요합니다. 환경변수가 없으면 이 테스트만 건너뜁니다.
