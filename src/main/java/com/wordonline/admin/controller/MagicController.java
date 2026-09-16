package com.wordonline.admin.controller;

import com.wordonline.admin.service.MagicService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('WORDONLINE_ADMIN')")
public class MagicController {

    private final MagicService magicService;

    @GetMapping("/admin/magic")
    public String adminMagic(
            Model model
    ) {
        model.addAttribute("magics", magicService.getMagicComparisons());
        model.addAttribute("secondaryDatabaseEnabled", magicService.hasSecondaryDatabase());
        return "admin-magic";
    }

    @PostMapping("/admin/magic/by-name/create")
    public String createMagicByName(
            @RequestParam String name,
            @RequestParam String element,
            @RequestParam String accessType,
            @RequestParam String db
    ) {
        magicService.createMagic(name, element, accessType, isSecondary(db));
        return redirect();
    }

    @PostMapping("/admin/magic/by-name/update")
    public String updateMagicByName(
            @RequestParam String currentName,
            @RequestParam String name,
            @RequestParam String element,
            @RequestParam String accessType,
            @RequestParam String db
    ) {
        magicService.updateMagicByName(currentName, name, element, accessType, isSecondary(db));
        return redirect();
    }

    @PostMapping("/admin/magic/by-name/delete")
    public String deleteMagicByName(
            @RequestParam String name,
            @RequestParam String db
    ) {
        magicService.removeMagic(name, isSecondary(db));
        return redirect();
    }

    @PostMapping("/admin/magic/by-name/bulk-delete")
    public String bulkDeleteByName(
            @RequestParam(required = false) List<String> magicNames,
            @RequestParam String db
    ) {
        if (magicNames != null) {
            for (String magicName : magicNames) {
                magicService.removeMagic(magicName, isSecondary(db));
            }
        }
        return redirect();
    }

    @PostMapping("/admin/magic/create")
    public String createMagic(@RequestParam String name, @RequestParam(value = "db", defaultValue = "primary") String db) {
        magicService.createMagic(name, isSecondary(db));
        return redirect(db);
    }

    @PostMapping("/admin/magic/update")
    public String updateMagic(@RequestParam long id, @RequestParam String name, @RequestParam(value = "db", defaultValue = "primary") String db) {
        magicService.updateMagicName(id, name, isSecondary(db));
        return redirect(db);
    }

    @PostMapping("/admin/magic/delete")
    public String deleteMagic(@RequestParam long id, @RequestParam(value = "db", defaultValue = "primary") String db) {
        magicService.removeMagic(id, isSecondary(db));
        return redirect(db);
    }

    @PostMapping("/admin/magic/bulk-delete")
    public String bulkDelete(
            @RequestParam(required = false) List<Long> selectedMagicIds,
            @RequestParam(value = "db", defaultValue = "primary") String db
    ) {
        if (selectedMagicIds != null) {
            for (Long magicId : selectedMagicIds) {
                magicService.removeMagic(magicId, isSecondary(db));
            }
        }
        return redirect(db);
    }

    @PostMapping("/admin/magic/sync-to-secondary")
    public org.springframework.http.ResponseEntity<String> syncToSecondary() {
        return org.springframework.http.ResponseEntity.ok(magicService.syncToSecondary().toMessage("Magic: Deploy -> Dev"));
    }

    @PostMapping("/admin/magic/sync-to-primary")
    public org.springframework.http.ResponseEntity<String> syncToPrimary() {
        return org.springframework.http.ResponseEntity.ok(magicService.syncToPrimary().toMessage("Magic: Dev -> Deploy"));
    }

    private boolean isSecondary(String db) {
        return "secondary".equalsIgnoreCase(db);
    }

    private String redirect(String db) {
        return "redirect:/admin/magic?db=" + (isSecondary(db) ? "secondary" : "primary");
    }

    private String redirect() {
        return "redirect:/admin/magic";
    }
}
