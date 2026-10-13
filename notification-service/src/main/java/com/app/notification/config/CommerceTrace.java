package com.app.notification.config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Only W3C trace identity is propagated; no baggage, credentials or business payload. */
public final class CommerceTrace {

    private static final Logger AUDIT = LoggerFactory.getLogger(CommerceTrace.class);

    private CommerceTrace() {
    }

    public static String capture() {
        var context = Span.current().getSpanContext();
        return context.isValid() ? "00-" + context.getTraceId() + "-" + context.getSpanId()
                + "-" + context.getTraceFlags().asHex() : null;
    }

    public static Span start(
            String traceParent,
            String operation
    ) {
        Context parent = Context.root();
        if (traceParent != null && traceParent.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")) {
            var identity = traceParent.split("-");
            var remote = SpanContext.createFromRemoteParent(identity[1], identity[2],
                    TraceFlags.fromHex(identity[3], 0), TraceState.getDefault());
            if (remote.isValid()) {
                parent = parent.with(Span.wrap(remote));
            }
        }
        return GlobalOpenTelemetry.getTracer("commerce.notification")
                .spanBuilder(operation).setParent(parent).setSpanKind(SpanKind.PRODUCER).startSpan();
    }

    public static void audit(
            String action,
            String outcome
    ) {
        if (!action.matches("[a-z_]{1,40}") || !outcome.matches("[a-z_]{1,40}")) {
            throw new IllegalArgumentException("Bounded audit fields required");
        }
        var context = Span.current().getSpanContext();
        AUDIT.info("COMMERCE_AUDIT action={} outcome={} trace_id={} span_id={}",
                action, outcome, context.getTraceId(), context.getSpanId());
    }
}
