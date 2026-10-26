package com.wordonline.admin.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BalanceSheetServiceTest {
    private JdbcTemplate database(String name, String tag, double quantity) throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("balance_parameter_references")) return null;
            RowCallbackHandler callback = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("name")).thenReturn(name);
            if (sql.contains("parameter_values")) {
                when(rs.getString("parameter_name")).thenReturn("quantity");
                when(rs.getObject("value", Double.class)).thenReturn(quantity);
            }
            if (sql.contains("game_object_tags")) when(rs.getString("tag_name")).thenReturn(tag);
            callback.processRow(rs);
            return null;
        }).when(jdbc).query(anyString(), any(RowCallbackHandler.class));
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(
                List.of(new BalanceSheetService.MagicData(name, "Spawn", "body")));
        return jdbc;
    }

    @Test
    void keepsDevOnlyObjectsTagsAndQuantitiesIndependent() throws Exception {
        var service = new BalanceSheetService(database("deploy_unit", "deploy", 1),
                Optional.of(database("dev_unit", "dev", 3)));
        var sheet = service.load();
        assertTrue(sheet.secondaryEnabled());
        assertFalse(sheet.primary().objects().containsKey("dev_unit"));
        assertEquals(List.of("dev"), sheet.secondary().objects().get("dev_unit").tags());
        assertEquals(3.0, sheet.secondary().objects().get("dev_unit").values().get("quantity"));
        assertEquals("body", sheet.secondary().magics().getFirst().objectName());
    }

    @Test
    void returnsEmptySecondaryWhenNotConfigured() throws Exception {
        var sheet = new BalanceSheetService(database("unit", "unit", 1), Optional.empty()).load();
        assertFalse(sheet.secondaryEnabled());
        assertTrue(sheet.secondary().objects().isEmpty());
        assertTrue(sheet.secondary().magics().isEmpty());
    }
}
