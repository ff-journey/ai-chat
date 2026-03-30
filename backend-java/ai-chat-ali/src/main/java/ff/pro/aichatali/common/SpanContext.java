package ff.pro.aichatali.common;

import java.util.UUID;

public record SpanContext(
        String spanId,
        String parentSpanId,
        SpanType spanType,
        String name,
        long startTime
) {
    public enum SpanType { SUPERVISOR, AGENT, TOOL, LLM }

    public static SpanContext create(SpanType type, String name, String parentSpanId) {
        return new SpanContext(shortId(), parentSpanId, type, name, System.currentTimeMillis());
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
