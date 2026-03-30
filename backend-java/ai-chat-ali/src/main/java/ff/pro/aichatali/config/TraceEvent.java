package ff.pro.aichatali.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import ff.pro.aichatali.common.SpanContext;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TraceEvent(
        String type,
        String spanId,
        String parentSpanId,
        String spanType,
        String name,
        String status,
        String output,
        String text,
        String error
) {
    public static TraceEvent spanStart(SpanContext span) {
        return new TraceEvent("span_start", span.spanId(), span.parentSpanId(),
                span.spanType().name().toLowerCase(), span.name(),
                null, null, null, null);
    }

    public static TraceEvent spanEnd(SpanContext span, String status, String output) {
        return new TraceEvent("span_end", span.spanId(), null,
                null, null, status, output, null, null);
    }

    public static TraceEvent delta(String text) {
        return new TraceEvent("delta", null, null,
                null, null, null, null, text, null);
    }

    public static TraceEvent token(String text, String parentSpanId) {
        return new TraceEvent("token", null, parentSpanId,
                null, null, null, null, text, null);
    }

    public static TraceEvent error(String spanId, String message) {
        return new TraceEvent("error", spanId, null,
                null, null, null, null, null, message);
    }
}
