package com.wordonline.admin.controller;

import com.wordonline.admin.dto.bot.BotDeckForm;
import com.wordonline.admin.dto.bot.BotForm;
import com.wordonline.admin.service.BotDualDatabaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/bot")
@PreAuthorize("hasAuthority('WORDONLINE_ADMIN')")
@RequiredArgsConstructor
public class BotAdminController {

    private final BotDualDatabaseService botService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("bots", botService.comparisons());
        model.addAttribute("secondaryDatabaseEnabled", botService.hasSecondary());
        model.addAttribute("botForm", new BotForm());
        return "admin-bot";
    }

    @GetMapping("/{userId}")
    public String detail(@PathVariable long userId, Model model) {
        model.addAttribute("bot", botService.comparison(userId));
        model.addAttribute("primaryMagics", botService.primaryMagics());
        model.addAttribute("secondaryMagics", botService.secondaryMagics());
        model.addAttribute("secondaryDatabaseEnabled", botService.hasSecondary());
        model.addAttribute("botForm", new BotForm());
        model.addAttribute("deckForm", new BotDeckForm());
        return "admin-bot-detail";
    }

    @PostMapping
    public String create(@ModelAttribute BotForm form,
                         @RequestParam(defaultValue = "primary") String db,
                         RedirectAttributes redirectAttributes) {
        long userId = botService.create(form, botService.target(db));
        redirectAttributes.addFlashAttribute("message", "Bot created");
        return "redirect:/admin/bot/" + userId;
    }

    @PostMapping("/{userId}")
    public String update(@PathVariable long userId, @ModelAttribute BotForm form,
                         @RequestParam(defaultValue = "primary") String db,
                         RedirectAttributes redirectAttributes) {
        botService.update(userId, form, botService.target(db));
        redirectAttributes.addFlashAttribute("message", "Bot updated");
        return "redirect:/admin/bot/" + userId;
    }

    @PostMapping("/{userId}/deck")
    public String updateDeck(@PathVariable long userId, @ModelAttribute BotDeckForm form,
                             @RequestParam(defaultValue = "primary") String db,
                             RedirectAttributes redirectAttributes) {
        botService.replaceDeck(userId, form, botService.target(db));
        redirectAttributes.addFlashAttribute("message", "Deck updated");
        return "redirect:/admin/bot/" + userId;
    }

    @PostMapping("/{userId}/enabled/{enabled}")
    public String setEnabled(@PathVariable long userId, @PathVariable boolean enabled,
                             @RequestParam(defaultValue = "primary") String db) {
        botService.setEnabled(userId, enabled, botService.target(db));
        return "redirect:/admin/bot/" + userId;
    }

    @PostMapping("/{userId}/delete")
    public String delete(@PathVariable long userId,
                         @RequestParam(defaultValue = "primary") String db) {
        botService.delete(userId, botService.target(db));
        return "redirect:/admin/bot";
    }

    @PostMapping("/sync-to-secondary")
    public ResponseEntity<String> syncToSecondary() {
        return ResponseEntity.ok(botService.syncToSecondary().message("Bots: Deploy -> Dev"));
    }

    @PostMapping("/sync-to-primary")
    public ResponseEntity<String> syncToPrimary() {
        return ResponseEntity.ok(botService.syncToPrimary().message("Bots: Dev -> Deploy"));
    }
}
