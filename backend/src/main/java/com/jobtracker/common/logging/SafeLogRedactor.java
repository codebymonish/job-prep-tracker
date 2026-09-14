package com.jobtracker.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.regex.Pattern;

public class SafeLogRedactor extends TurboFilter {

    private static final Pattern JWT_PATTERN = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern TOKEN_PARAM_PATTERN = Pattern.compile("([?&])token=[^&\\s]+");
    
    private static final Marker REDACTED_MARKER = MarkerFactory.getMarker("REDACTED");

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
        if (!isStarted()) {
            return FilterReply.NEUTRAL;
        }

        if (marker == REDACTED_MARKER) {
            return FilterReply.NEUTRAL;
        }

        boolean needsRedaction = false;
        String redactedFormat = format;
        if (redactedFormat != null) {
            String newFormat = redact(redactedFormat);
            if (!newFormat.equals(redactedFormat)) {
                redactedFormat = newFormat;
                needsRedaction = true;
            }
        }

        Object[] redactedParams = null;
        if (params != null) {
            redactedParams = new Object[params.length];
            for (int i = 0; i < params.length; i++) {
                if (params[i] instanceof String str) {
                    String redactedStr = redact(str);
                    if (!redactedStr.equals(str)) {
                        needsRedaction = true;
                    }
                    redactedParams[i] = redactedStr;
                } else {
                    redactedParams[i] = params[i];
                }
            }
        }

        if (needsRedaction) {
            switch (level.toInt()) {
                case Level.TRACE_INT -> logger.trace(REDACTED_MARKER, redactedFormat, redactedParams);
                case Level.DEBUG_INT -> logger.debug(REDACTED_MARKER, redactedFormat, redactedParams);
                case Level.INFO_INT -> logger.info(REDACTED_MARKER, redactedFormat, redactedParams);
                case Level.WARN_INT -> logger.warn(REDACTED_MARKER, redactedFormat, redactedParams);
                case Level.ERROR_INT -> logger.error(REDACTED_MARKER, redactedFormat, redactedParams);
            }
            return FilterReply.DENY;
        }

        return FilterReply.NEUTRAL;
    }

    private String redact(String input) {
        if (input == null) return null;
        String output = input;
        output = JWT_PATTERN.matcher(output).replaceAll("[JWT_REDACTED]");
        output = EMAIL_PATTERN.matcher(output).replaceAll("[EMAIL_REDACTED]");
        output = TOKEN_PARAM_PATTERN.matcher(output).replaceAll("$1token=[TOKEN_REDACTED]");
        return output;
    }
}
