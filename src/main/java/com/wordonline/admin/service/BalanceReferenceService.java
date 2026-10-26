package com.wordonline.admin.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class BalanceReferenceService {
    private final JdbcTemplate primary;
    private final Optional<JdbcTemplate> secondary;
    private final BalanceSheetService sheets;
    private static final Set<String> DERIVED = Set.of("summon_count", "total_dps", "dps_per_mana",
            "total_hp", "hp_per_mana", "individual_dps", "attack_area");

    public BalanceReferenceService(@Qualifier("jdbcTemplate") JdbcTemplate primary,
                                   @Qualifier("secondaryJdbcTemplate") Optional<JdbcTemplate> secondary,
                                   BalanceSheetService sheets) {
        this.primary = primary;
        this.secondary = secondary;
        this.sheets = sheets;
    }

    public record Change(String db, String objectName, String parameterName,
                         String targetObjectName, String targetParameterName, Double multiplier, boolean remove) {}
    private record Node(String objectName, String parameterName) {}

    public Map<String, Map<String, BalanceSheetService.ReferenceTarget>> save(Change change) {
        if (change == null) throw bad("참조 설정을 입력하세요.");
        JdbcTemplate jdbc = switch (Objects.toString(change.db(), "")) {
            case "primary" -> primary;
            case "secondary" -> secondary.orElseThrow(() -> bad("Dev DB가 설정되지 않았습니다."));
            default -> throw bad("저장할 DB를 선택하세요.");
        };
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));
        return transaction.execute(status -> {
            // 두 관리자가 동시에 반대 방향 참조를 저장해 순환을 만드는 것을 막습니다.
            jdbc.execute("LOCK TABLE balance_parameter_references IN SHARE ROW EXCLUSIVE MODE");
            var snapshot = sheets.read(jdbc);
            validateAndApply(snapshot, change);
            if (change.remove()) {
                jdbc.update("""
                        delete from balance_parameter_references where game_object_id =
                        (select id from game_objects where name = ?) and parameter_name = ?
                        """, change.objectName(), change.parameterName());
            } else {
                jdbc.update("""
                        insert into balance_parameter_references
                            (game_object_id, parameter_name, target_game_object_id, target_parameter_name, multiplier)
                        values ((select id from game_objects where name = ?), ?,
                                (select id from game_objects where name = ?), ?, ?)
                        on conflict (game_object_id, parameter_name) do update set
                            target_game_object_id = excluded.target_game_object_id,
                            target_parameter_name = excluded.target_parameter_name, multiplier = excluded.multiplier
                        """, change.objectName(), change.parameterName(), change.targetObjectName(),
                        change.targetParameterName(), change.multiplier());
            }
            return snapshot.references();
        });
    }

    static void validateAndApply(BalanceSheetService.Snapshot snapshot, Change change) {
        if (change.objectName() == null || !snapshot.objects().containsKey(change.objectName())) throw bad("참조를 표시할 객체가 없습니다.");
        validateParameter(change.parameterName());
        if (DERIVED.contains(change.parameterName())) throw bad("계산 지표에는 참조를 등록할 수 없습니다.");
        var refs = snapshot.references().computeIfAbsent(change.objectName(), key -> new TreeMap<>());
        var previous = refs.get(change.parameterName());
        try {
            if (change.remove()) {
                refs.remove(change.parameterName());
            } else {
                if (change.targetObjectName() == null || !snapshot.objects().containsKey(change.targetObjectName())) throw bad("원본 객체가 없습니다.");
                validateParameter(change.targetParameterName());
                if (DERIVED.contains(change.targetParameterName())) throw bad("계산 지표를 원본으로 사용할 수 없습니다.");
                if (change.multiplier() == null || !Double.isFinite(change.multiplier()) || change.multiplier() <= 0) {
                    throw bad("배율은 유한한 양수여야 합니다.");
                }
                refs.put(change.parameterName(), new BalanceSheetService.ReferenceTarget(change.targetObjectName(),
                        change.targetParameterName(), change.multiplier()));
            }
            for (var object : snapshot.references().entrySet()) {
                for (String parameter : object.getValue().keySet()) {
                    resolve(snapshot, new Node(object.getKey(), parameter));
                }
            }
        } catch (RuntimeException error) {
            if (previous == null) refs.remove(change.parameterName()); else refs.put(change.parameterName(), previous);
            throw error;
        }
    }

    private static void resolve(BalanceSheetService.Snapshot snapshot, Node node) {
        Set<Node> visited = new HashSet<>();
        double multiplier = 1;
        while (true) {
            if (!visited.add(node)) throw bad("순환 참조는 저장할 수 없습니다: " + node.objectName() + "." + node.parameterName());
            var target = snapshot.references().getOrDefault(node.objectName(), Map.of()).get(node.parameterName());
            if (target != null) {
                multiplier *= target.multiplier();
                if (!Double.isFinite(multiplier) || multiplier <= 0) throw bad("다단계 참조의 누적 배율이 숫자 범위를 벗어납니다.");
                node = new Node(target.objectName(), target.parameterName());
                continue;
            }
            Node current = node;
            var magic = snapshot.magics().stream().filter(m -> m.name().equals(current.objectName())).findFirst();
            if (magic.isPresent() && !magic.get().objectName().equals(node.objectName())
                    && !Set.of("mana_cost", "quantity", "magic_id").contains(node.parameterName())) {
                node = new Node(magic.get().objectName(), node.parameterName());
                continue;
            }
            var object = snapshot.objects().get(node.objectName());
            if (object == null || !object.values().containsKey(node.parameterName())) {
                throw bad("원본 parameter가 없습니다: " + node.objectName() + "." + node.parameterName());
            }
            return;
        }
    }

    private static void validateParameter(String name) {
        if (name == null || !name.matches("[a-z][a-z0-9_]{0,127}")) throw bad("parameter 이름은 영문 소문자로 시작하는 영문·숫자·밑줄 128자 이내여야 합니다.");
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
