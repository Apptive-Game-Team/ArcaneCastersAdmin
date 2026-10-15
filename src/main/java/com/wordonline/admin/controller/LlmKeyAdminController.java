package com.wordonline.admin.controller;

import com.wordonline.admin.dto.llm.LlmProvider;
import com.wordonline.admin.service.BotDualDatabaseService.Target;
import com.wordonline.admin.service.LlmKeyDualDatabaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/llm-keys")
@PreAuthorize("hasAuthority('WORDONLINE_ADMIN')")
@RequiredArgsConstructor
public class LlmKeyAdminController {

    private final LlmKeyDualDatabaseService keyService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("keys", keyService.comparisons());
        model.addAttribute("secondaryDatabaseEnabled", keyService.hasSecondary());
        model.addAttribute("cipherConfigured", keyService.cipherConfigured());
        return "admin-llm-keys";
    }

    // 오류 메시지에 key 를 싣지 않는다. 예외 메시지는 서비스가 고정 문구로만 만든다.
    @PostMapping("/{provider}")
    public String save(@PathVariable String provider, @RequestParam String apiKey,
                       @RequestParam(defaultValue = "primary") String db,
                       RedirectAttributes redirectAttributes) {
        try {
            LlmProvider llmProvider = LlmProvider.from(provider);
            keyService.save(llmProvider, apiKey, target(db));
            redirectAttributes.addFlashAttribute("message", llmProvider.name() + " API key saved");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/llm-keys";
    }

    @PostMapping("/{provider}/delete")
    public String delete(@PathVariable String provider,
                         @RequestParam(defaultValue = "primary") String db,
                         RedirectAttributes redirectAttributes) {
        try {
            LlmProvider llmProvider = LlmProvider.from(provider);
            keyService.delete(llmProvider, target(db));
            redirectAttributes.addFlashAttribute("message", llmProvider.name() + " API key deleted");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/llm-keys";
    }

    @PostMapping("/{provider}/available")
    public String clearUnavailable(@PathVariable String provider,
                                   @RequestParam(defaultValue = "primary") String db,
                                   RedirectAttributes redirectAttributes) {
        try {
            LlmProvider llmProvider = LlmProvider.from(provider);
            keyService.clearUnavailable(llmProvider, target(db));
            redirectAttributes.addFlashAttribute("message", llmProvider.name() + " marked available");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/llm-keys";
    }

    private Target target(String db) {
        return switch (db == null ? "primary" : db.toLowerCase()) {
            case "secondary" -> Target.SECONDARY;
            case "both" -> Target.BOTH;
            default -> Target.PRIMARY;
        };
    }
}
