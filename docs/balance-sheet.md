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

`static/js/balance-model.js`의 `references`는 확인된 교차 객체 참조를 정의합니다.
현재 Bean Shooter 투사체 속도·반지름, Lightning Cloud 공격력·효과 반지름,
Water Slime 사거리, Grass Generator 잎 영역 반지름을 포함합니다.
Rust의 각 PrefabInitializer에서 확인했으며 Bean Shooter 피해는 자체 damage를 사용합니다.
전투 코드에서 참조가 바뀌면 이 정의와 회귀 테스트도 함께 수정해야 합니다.
등록하지 않은 특수 공격의 관계를 값 유무나 이름으로 추측하지 않습니다.

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
