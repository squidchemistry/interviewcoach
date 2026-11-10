package com.aicoach.ai.service.prompt;

import com.aicoach.common.security.Hashing;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Prompt-injection defences for text written by candidates or extracted from their documents.
 * <ul>
 *   <li>{@link #frame}: wraps untrusted text in a block delimited by a per-render random nonce, after neutralising any
 *       attempt to close or spoof the block, and caps its length.</li>
 *   <li>{@link #detect}: flags typical injection phrasing. Flags never block; they are shown in the report and the
 *       evaluator is told about them.</li>
 * </ul>
 */
public final class UntrustedContent {

    private static final Pattern TAG = Pattern.compile("(?i)</?\\s*untrusted_data[^>]*>");
    private static final List<Pattern> INJECTION = List.of(
            Pattern.compile("(?i)ignore\\s+(all\\s+|any\\s+)?(previous|prior|above)\\s+(instructions|prompts?|rules)"),
            Pattern.compile("(?i)disregard\\s+(the\\s+)?(system|previous|above)"),
            Pattern.compile("(?i)\\byou\\s+are\\s+now\\b"),
            Pattern.compile("(?i)system\\s+prompt"),
            Pattern.compile("(?i)(rate|score|grade)\\s+(me|this|the\\s+candidate)\\s+(a\\s+)?(10|ten|100|perfect|maximum)"),
            Pattern.compile("(?i)give\\s+(me|this\\s+candidate)\\s+(a\\s+)?(10|full|perfect|maximum)"),
            Pattern.compile("(?im)^\\s*(assistant|system)\\s*:"),
            Pattern.compile("(?i)</?\\s*untrusted_data"));

    private UntrustedContent() {
    }

    public static String frame(String kind, String text, int maxChars) {
        String nonce = Hashing.randomToken(6);
        String body = text == null ? "" : TAG.matcher(text).replaceAll("[removed]");
        if (body.length() > maxChars) {
            body = body.substring(0, maxChars) + "\n[truncated]";
        }
        return "<untrusted_data kind=\"" + kind + "\" id=\"" + nonce + "\">\n" + body + "\n</untrusted_data id=\"" + nonce + "\">";
    }

    public static List<String> detect(String text) {
        List<String> flags = new ArrayList<>();
        if (text == null) {
            return flags;
        }
        for (Pattern pattern : INJECTION) {
            var matcher = pattern.matcher(text);
            if (matcher.find()) {
                flags.add("PROMPT_INJECTION_ATTEMPT: \"" + matcher.group().trim().toLowerCase(Locale.ROOT) + "\"");
            }
        }
        return flags;
    }
}
