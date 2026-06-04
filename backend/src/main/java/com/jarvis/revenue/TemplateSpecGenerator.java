package com.jarvis.revenue;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ChatMessage;
import com.jarvis.ai.LanguageModel;
import com.jarvis.ai.ModelResponse;

import lombok.RequiredArgsConstructor;

/**
 * Produces a {@link TemplateSpec} from a product idea using AT MOST ONE AI call (the cost-control rule:
 * one planning call, then deterministic backend building). Known products (e.g. the Hebrew Reserve
 * Soldier Command Center) return a CACHED seed spec — zero AI cost — so the flagship product builds free.
 */
@Service
@RequiredArgsConstructor
public class TemplateSpecGenerator {

    private static final Logger log = LoggerFactory.getLogger(TemplateSpecGenerator.class);

    private final LanguageModel model;
    private final ObjectMapper mapper;

    /** Returns [spec, aiCallsUsed]. aiCallsUsed is 0 for a cached seed, 1 otherwise. */
    public Result generate(String idea, String language, String targetCustomer, String complexity) {
        String lang = language == null || language.isBlank() ? "en" : language;
        if (isReserveSoldier(idea)) {
            return new Result(reserveSoldierSeed(lang), 0);
        }
        try {
            String system = "You are a Notion template architect. Output ONLY a JSON object (no prose, no code fence) "
                    + "with this exact shape: {\"title\":str,\"language\":str,\"databases\":[{\"name\":str,"
                    + "\"properties\":[{\"name\":str,\"type\":\"title|rich_text|number|select|multi_select|date|checkbox\","
                    + "\"options\":[str]}],\"sampleRows\":[{col:val}]}],\"pages\":[{\"title\":str,\"blocks\":[str]}],"
                    + "\"salesCopy\":str}. Each database MUST have exactly one 'title' property. Block lines may use "
                    + "'# H1','## H2','- bullet','- [ ] todo'. salesCopy is a Gumroad/Etsy product description. "
                    + "Write content in the requested language.";
            String user = "Idea: " + idea + "\nLanguage: " + lang + "\nTarget customer: " + targetCustomer
                    + "\nComplexity: " + (complexity == null ? "medium" : complexity)
                    + "\nDesign a complete, sellable template.";
            ModelResponse r = model.generate(List.of(ChatMessage.system(system), ChatMessage.user(user)), List.of());
            String json = extractJson(r == null ? "" : r.text());
            return new Result(TemplateSpec.fromJson(mapper.readTree(json)), 1);
        } catch (Exception e) {
            log.warn("Template spec generation failed: {}", e.getMessage());
            throw new IllegalStateException("Couldn't generate a template spec: " + e.getMessage(), e);
        }
    }

    public record Result(TemplateSpec spec, int aiCallsUsed) {}

    private static boolean isReserveSoldier(String idea) {
        String i = idea == null ? "" : idea.toLowerCase();
        return i.contains("reserve") || i.contains("מילואים") || (i.contains("soldier") && i.contains("command"));
    }

    /** Cached flagship seed — the Hebrew "Reserve Soldier Command Center" (0 AI cost). */
    private TemplateSpec reserveSoldierSeed(String lang) {
        boolean he = "he".equalsIgnoreCase(lang);
        String title = he ? "מרכז שליטה למשרת מילואים" : "Reserve Soldier Command Center";
        var tasks = new TemplateSpec.DbSpec(he ? "משימות" : "Tasks",
                List.of(new TemplateSpec.PropSpec(he ? "משימה" : "Task", "title", List.of()),
                        new TemplateSpec.PropSpec(he ? "סטטוס" : "Status", "select",
                                he ? List.of("לעשות", "בתהליך", "הושלם") : List.of("Todo", "Doing", "Done")),
                        new TemplateSpec.PropSpec(he ? "תאריך יעד" : "Due", "date", List.of())),
                List.of(Map.of(he ? "משימה" : "Task", he ? "להגיש טופס 1010" : "Submit form 1010",
                        he ? "סטטוס" : "Status", he ? "לעשות" : "Todo")));
        var docs = new TemplateSpec.DbSpec(he ? "מסמכים" : "Documents",
                List.of(new TemplateSpec.PropSpec(he ? "שם המסמך" : "Document", "title", List.of()),
                        new TemplateSpec.PropSpec(he ? "סוג" : "Type", "select",
                                he ? List.of("צו", "אישור", "תלוש") : List.of("Order", "Confirmation", "Payslip"))),
                List.of());
        var payments = new TemplateSpec.DbSpec(he ? "תשלומים" : "Payments",
                List.of(new TemplateSpec.PropSpec(he ? "תיאור" : "Description", "title", List.of()),
                        new TemplateSpec.PropSpec(he ? "סכום" : "Amount", "number", List.of()),
                        new TemplateSpec.PropSpec(he ? "התקבל" : "Received", "checkbox", List.of())),
                List.of());
        var employer = new TemplateSpec.DbSpec(he ? "תקשורת מול המעסיק" : "Employer Communication",
                List.of(new TemplateSpec.PropSpec(he ? "נושא" : "Subject", "title", List.of()),
                        new TemplateSpec.PropSpec(he ? "תאריך" : "Date", "date", List.of())),
                List.of());
        var dashboard = new TemplateSpec.PageSpec(he ? "לוח בקרה" : "Dashboard",
                he ? List.of("# מרכז שליטה למשרת מילואים", "ריכוז כל מה שצריך בזמן ואחרי המילואים.",
                        "- [ ] לעדכן את המעסיק", "- [ ] לאסוף אישורים", "- [ ] לבדוק זכאות למענקים")
                   : List.of("# Reserve Soldier Command Center", "Everything you need during and after reserve duty.",
                        "- [ ] Notify employer", "- [ ] Collect confirmations", "- [ ] Check grant eligibility"));
        var benefits = new TemplateSpec.PageSpec(he ? "צ'קליסט זכויות" : "Benefits Checklist",
                he ? List.of("## זכויות ומענקים", "- [ ] מענק מילואים", "- [ ] הגנת שכר", "- [ ] ימי מחלה/חופשה")
                   : List.of("## Rights & grants", "- [ ] Reserve grant", "- [ ] Salary protection", "- [ ] Leave days"));
        var guide = new TemplateSpec.PageSpec(he ? "מדריך שימוש" : "Usage Guide",
                he ? List.of("## איך להשתמש", "מלאו את המשימות, עקבו אחרי מסמכים ותשלומים, וסמנו זכויות.")
                   : List.of("## How to use", "Fill the tasks, track documents & payments, tick off your benefits."));
        String sales = he
                ? "מרכז שליטה למשרת מילואים — תבנית Notion שמרכזת משימות, מסמכים, תשלומים, תקשורת מול המעסיק וזכויות "
                  + "במקום אחד. חוסכת שעות בירוקרטיה. מתאימה לכל משרת/ת מילואים בישראל."
                : "Reserve Soldier Command Center — a Notion template that puts your tasks, documents, payments, "
                  + "employer comms and benefits in one place. Saves hours of bureaucracy.";
        return new TemplateSpec(title, lang, List.of(tasks, docs, payments, employer),
                List.of(dashboard, benefits, guide), sales);
    }

    /** Pull the first {...} JSON object out of a model reply (tolerates stray prose/fences). */
    private static String extractJson(String text) {
        if (text == null) {
            return "{}";
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return (start >= 0 && end > start) ? text.substring(start, end + 1) : "{}";
    }
}
