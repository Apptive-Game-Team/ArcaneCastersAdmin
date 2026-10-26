package com.wordonline.admin.controller;

import com.wordonline.admin.service.BalanceSheetService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/spread-sheets")
@PreAuthorize("hasAuthority('WORDONLINE_ADMIN')")
public class SpreadSheetController {
    private final BalanceSheetService balanceSheetService;

    @GetMapping("/game-objects")
    public String getGameObjects(Model model) {
        var sheet = balanceSheetService.load();
        model.addAttribute("balanceSheet", sheet);
        model.addAttribute("secondaryDatabaseEnabled", sheet.secondaryEnabled());
        return "admin-spreadsheet";
    }
}
