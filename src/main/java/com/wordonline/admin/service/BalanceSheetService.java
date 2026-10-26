package com.wordonline.admin.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BalanceSheetService {
    private final JdbcTemplate primary;
    private final Optional<JdbcTemplate> secondary;

    public BalanceSheetService(@Qualifier("jdbcTemplate") JdbcTemplate primary,
                               @Qualifier("secondaryJdbcTemplate") Optional<JdbcTemplate> secondary) {
        this.primary = primary;
        this.secondary = secondary;
    }

    public record ObjectData(Map<String, Double> values, List<String> tags) {}
    public record MagicData(String name, String kind, String objectName) {}
    public record ReferenceTarget(String objectName, String parameterName, double multiplier) {}
    public record Snapshot(Map<String, ObjectData> objects, List<MagicData> magics,
                           Map<String, Map<String, ReferenceTarget>> references) {
        public Snapshot(Map<String, ObjectData> objects, List<MagicData> magics) {
            this(objects, magics, new TreeMap<>());
        }
    }
    public record Sheet(Snapshot primary, Snapshot secondary, boolean secondaryEnabled) {}

    public Sheet load() {
        return new Sheet(read(primary), secondary.map(this::read).orElse(new Snapshot(Map.of(), List.of())),
                secondary.isPresent());
    }

    Snapshot read(JdbcTemplate jdbc) {
        Map<String, ObjectData> objects = new TreeMap<>();
        jdbc.query("select name from game_objects order by name", rs -> {
            objects.put(rs.getString("name"), new ObjectData(new TreeMap<>(), new ArrayList<>()));
        });
        jdbc.query("""
                select g.name, p.name as parameter_name, pv.value
                from parameter_values pv join game_objects g on g.id = pv.game_object_id
                join parameters p on p.id = pv.parameter_id
                """, rs -> {
            objects.get(rs.getString("name")).values().put(rs.getString("parameter_name"),
                    rs.getObject("value", Double.class));
        });
        jdbc.query("""
                select g.name, t.name as tag_name from game_object_tags gt
                join game_objects g on g.id = gt.game_object_id join tags t on t.id = gt.tag_id
                """, rs -> {
            objects.get(rs.getString("name")).tags().add(rs.getString("tag_name"));
        });
        List<MagicData> magics = jdbc.query("""
                select m.name, m.cast_kind, coalesce(a.game_object_name, m.name) as object_name
                from magics m left join magic_game_object_aliases a on a.magic_name = m.name
                order by m.name
                """, (rs, index) -> new MagicData(rs.getString("name"), rs.getString("cast_kind"),
                rs.getString("object_name")));
        Map<String, Map<String, ReferenceTarget>> references = new TreeMap<>();
        jdbc.query("""
                select g.name, r.parameter_name, t.name as target_name, r.target_parameter_name, r.multiplier
                from balance_parameter_references r join game_objects g on g.id = r.game_object_id
                join game_objects t on t.id = r.target_game_object_id
                """, rs -> {
            references.computeIfAbsent(rs.getString("name"), key -> new TreeMap<>())
                    .put(rs.getString("parameter_name"), new ReferenceTarget(rs.getString("target_name"),
                            rs.getString("target_parameter_name"), rs.getDouble("multiplier")));
        });
        return new Snapshot(objects, magics, references);
    }
}
