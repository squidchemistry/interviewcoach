package com.aicoach.ai.service.prompt;

import com.aicoach.ai.AiTypes.AiPurpose;
import com.aicoach.ai.AiTypes.ModelTier;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Loads the versioned prompt templates from {@code classpath:prompts/*.v<N>.md} at startup and fails fast if any is
 * malformed. Prompts are files, never inline strings, so every change is reviewable and every AI call logs the exact
 * prompt id and version it used. The highest version of each id is active.
 */
@Component
public class PromptRegistry {

    private static final String SYSTEM_MARKER = "=== SYSTEM ===";
    private static final String USER_MARKER = "=== USER ===";

    /** A parsed template: metadata from the front-matter plus compiled system/user sections. */
    public record PromptTemplate(String id, int version, AiPurpose purpose, ModelTier tier, double temperature,
            int maxTokens, boolean json, Set<String> untrusted, Template system, Template user) {

        public String versionLabel() {
            return "v" + version;
        }
    }

    /** A rendered template, ready to send. */
    public record RenderedPrompt(PromptTemplate template, String system, String user) {
    }

    private final Map<String, PromptTemplate> templates = new HashMap<>();
    private final Mustache.Compiler compiler;

    public PromptRegistry() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Map<String, String> partials = new HashMap<>();
        for (Resource partial : resolver.getResources("classpath:prompts/partials/*.md")) {
            partials.put(partial.getFilename().replace(".md", ""), partial.getContentAsString(StandardCharsets.UTF_8));
        }
        this.compiler = Mustache.compiler()
                .escapeHTML(false)
                .emptyStringIsFalse(true)
                .zeroIsFalse(false)
                .defaultValue("")
                .withLoader(name -> {
                    String partial = partials.get(name);
                    if (partial == null) {
                        throw new IllegalStateException("Unknown prompt partial: " + name);
                    }
                    return new StringReader(partial);
                });
        for (Resource resource : resolver.getResources("classpath:prompts/*.md")) {
            PromptTemplate template = parse(resource.getFilename(), resource.getContentAsString(StandardCharsets.UTF_8));
            templates.merge(template.id(), template, (a, b) -> a.version() >= b.version() ? a : b);
        }
        if (templates.isEmpty()) {
            throw new IllegalStateException("No prompt templates found under classpath:prompts/");
        }
    }

    public PromptTemplate get(String id) {
        PromptTemplate template = templates.get(id);
        if (template == null) {
            throw new IllegalArgumentException("Unknown prompt: " + id);
        }
        return template;
    }

    public Set<String> ids() {
        return Set.copyOf(templates.keySet());
    }

    /** Renders a prompt; values for keys declared {@code untrusted} are framed before substitution. */
    public RenderedPrompt render(String id, Map<String, Object> variables) {
        PromptTemplate template = get(id);
        Map<String, Object> context = new LinkedHashMap<>(variables);
        for (String key : template.untrusted()) {
            Object value = context.get(key);
            if (value instanceof String text) {
                context.put(key, UntrustedContent.frame(key, text, 24_000));
            }
        }
        return new RenderedPrompt(template, template.system().execute(context).trim(),
                template.user().execute(context).trim());
    }

    PromptTemplate parse(String filename, String content) {
        String normalized = content.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n")) {
            throw new IllegalStateException(filename + ": missing front-matter");
        }
        int end = normalized.indexOf("\n---\n", 4);
        if (end < 0) {
            throw new IllegalStateException(filename + ": unterminated front-matter");
        }
        Map<String, String> meta = new HashMap<>();
        for (String line : normalized.substring(4, end).split("\n")) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                meta.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
            }
        }
        String body = normalized.substring(end + 5);
        int system = body.indexOf(SYSTEM_MARKER);
        int user = body.indexOf(USER_MARKER);
        if (system < 0 || user < system) {
            throw new IllegalStateException(filename + ": needs '" + SYSTEM_MARKER + "' then '" + USER_MARKER + "' sections");
        }
        for (String key : List.of("id", "version", "purpose", "tier", "temperature", "maxTokens")) {
            if (!meta.containsKey(key)) {
                throw new IllegalStateException(filename + ": front-matter is missing '" + key + "'");
            }
        }
        Set<String> untrusted = meta.getOrDefault("untrusted", "").isBlank() ? Set.of()
                : Arrays.stream(meta.get("untrusted").split(",")).map(String::trim).collect(Collectors.toUnmodifiableSet());
        try {
            return new PromptTemplate(meta.get("id"), Integer.parseInt(meta.get("version")),
                    AiPurpose.valueOf(meta.get("purpose")), ModelTier.valueOf(meta.get("tier")),
                    Double.parseDouble(meta.get("temperature")), Integer.parseInt(meta.get("maxTokens")),
                    Boolean.parseBoolean(meta.getOrDefault("json", "false")), untrusted,
                    compiler.compile(body.substring(system + SYSTEM_MARKER.length(), user)),
                    compiler.compile(body.substring(user + USER_MARKER.length())));
        } catch (RuntimeException e) {
            throw new IllegalStateException(filename + ": invalid prompt template: " + e.getMessage(), e);
        }
    }
}
