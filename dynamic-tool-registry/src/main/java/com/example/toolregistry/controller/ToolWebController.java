package com.example.toolregistry.controller;

import com.example.toolregistry.dto.ParsedToolInfo;
import com.example.toolregistry.dto.ToolRegistrationRequest;
import com.example.toolregistry.entity.ToolDefinition;
import com.example.toolregistry.service.ToolRegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/tools")
@RequiredArgsConstructor
public class ToolWebController {

    private final ToolRegistrationService toolRegistrationService;

    @GetMapping
    public String listTools(Model model) {
        model.addAttribute("tools", toolRegistrationService.getAllTools());
        return "tools/list";
    }

    @GetMapping("/new")
    public String showRegistrationForm(Model model) {
        model.addAttribute("request", new ToolRegistrationRequest());
        return "tools/register";
    }

    @PostMapping("/preview")
    public String previewTool(@ModelAttribute ToolRegistrationRequest request, Model model) {
        try {
            ParsedToolInfo preview = toolRegistrationService.previewTool(request);
            model.addAttribute("request", request);
            model.addAttribute("preview", preview);
            return "tools/preview";
        } catch (Exception e) {
            model.addAttribute("error", "Failed to parse OpenAPI: " + e.getMessage());
            model.addAttribute("request", request);
            return "tools/register";
        }
    }

    @PostMapping("/register")
    public String registerTool(@ModelAttribute ToolRegistrationRequest request, RedirectAttributes redirectAttributes) {
        try {
            ToolDefinition tool = toolRegistrationService.registerTool(request);
            redirectAttributes.addFlashAttribute("success", "Tool registered successfully!");
            return "redirect:/tools/" + tool.getId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to register tool: " + e.getMessage());
            return "redirect:/tools/new";
        }
    }

    @GetMapping("/{id}")
    public String viewTool(@PathVariable Long id, Model model) {
        try {
            ToolDefinition tool = toolRegistrationService.getToolById(id);
            model.addAttribute("tool", tool);
            return "tools/view";
        } catch (Exception e) {
            model.addAttribute("error", "Tool not found");
            return "redirect:/tools";
        }
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        try {
            ToolDefinition tool = toolRegistrationService.getToolById(id);
            model.addAttribute("tool", tool);
            return "tools/edit";
        } catch (Exception e) {
            return "redirect:/tools";
        }
    }

    @PostMapping("/{id}/update")
    public String updateTool(@PathVariable Long id,
                           @RequestParam(required = false) String description,
                           @RequestParam(required = false) String humanReadableDescription,
                           @RequestParam(required = false) String baseUrl,
                           @RequestParam(required = false) Long[] paramIds,
                           @RequestParam(required = false) String[] paramHumanDescriptions,
                           @RequestParam(required = false) Long[] paramExampleIds,
                           @RequestParam(required = false) String[] paramExamples,
                           @RequestParam(required = false) Long[] responseIds,
                           @RequestParam(required = false) String[] responseHumanDescriptions,
                           RedirectAttributes redirectAttributes) {
        try {
            toolRegistrationService.updateToolDescriptions(id, description, humanReadableDescription, baseUrl);

            if (paramIds != null && paramHumanDescriptions != null) {
                for (int i = 0; i < paramIds.length; i++) {
                    toolRegistrationService.updateParameterHumanDescription(paramIds[i], paramHumanDescriptions[i]);
                }
            }

            if (paramExampleIds != null && paramExamples != null) {
                for (int i = 0; i < paramExampleIds.length; i++) {
                    if (paramExamples[i] != null && !paramExamples[i].trim().isEmpty()) {
                        toolRegistrationService.updateParameterExample(paramExampleIds[i], paramExamples[i]);
                    }
                }
            }

            if (responseIds != null && responseHumanDescriptions != null) {
                for (int i = 0; i < responseIds.length; i++) {
                    toolRegistrationService.updateResponseHumanDescription(responseIds[i], responseHumanDescriptions[i]);
                }
            }

            redirectAttributes.addFlashAttribute("success", "Tool updated successfully!");
            return "redirect:/tools/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update tool: " + e.getMessage());
            return "redirect:/tools/" + id + "/edit";
        }
    }

    @PostMapping("/{id}/delete")
    public String deleteTool(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            toolRegistrationService.deleteTool(id);
            redirectAttributes.addFlashAttribute("success", "Tool deleted successfully!");
            return "redirect:/tools";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete tool: " + e.getMessage());
            return "redirect:/tools";
        }
    }
}
