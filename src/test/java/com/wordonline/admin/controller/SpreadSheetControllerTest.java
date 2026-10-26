package com.wordonline.admin.controller;

import com.wordonline.admin.service.BalanceSheetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SpreadSheetController.class)
@AutoConfigureMockMvc(addFilters = false)
class SpreadSheetControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean BalanceSheetService service;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void rendersBothDatabaseSnapshotsAndKeepsScriptsInsideLayout() throws Exception {
        var primary = new BalanceSheetService.Snapshot(Map.of(), List.of());
        var secondary = new BalanceSheetService.Snapshot(Map.of("dev_only",
                new BalanceSheetService.ObjectData(Map.of("quantity", 3.0), List.of("unit"))),
                List.of(new BalanceSheetService.MagicData("dev_only", "Spawn", "dev_only")));
        when(service.load()).thenReturn(new BalanceSheetService.Sheet(primary, secondary, true));
        mvc.perform(get("/admin/spread-sheets/game-objects?tags=unit"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("dev_only")))
                .andExpect(content().string(containsString("/js/balance-model.js")))
                .andExpect(content().string(containsString("/js/balance-sheet.js")))
                .andExpect(content().string(containsString("/css/balance-sheet.css")))
                .andExpect(content().string(containsString("window.balanceSheetData")));
    }
}
