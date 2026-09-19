package com.kkdev.waroracle.config;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.kkdev.waroracle.annotation.LogTag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Spring MVC Interceptor that inspects handler methods for LogTag annotations,
 * setting the logTag and traceId (logTag_timestamp) in SLF4J MDC for logging and response correlation.
 */
@Component
public class LogTagInterceptor implements HandlerInterceptor
{

    public static final String TRACE_ID_KEY = "traceId";
    public static final String LOG_TAG_KEY = "logTag";
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    public static String getCurrentTraceId()
    {
        return MDC.get(TRACE_ID_KEY);
    }

    public static String getCurrentTag()
    {
        return MDC.get(LOG_TAG_KEY);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
    {
        String tag = "REQUEST";

        if (handler instanceof HandlerMethod)
        {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            LogTag logTag = handlerMethod.getMethodAnnotation(LogTag.class);
            if (logTag != null && !logTag.value().trim().isEmpty())
            {
                tag = logTag.value().trim();
            }
            else
            {
                tag = handlerMethod.getMethod().getName();
            }
        }

        String traceId = request.getHeader(HEADER_TRACE_ID);
        if (traceId == null || traceId.trim().isEmpty())
        {
            traceId = tag + "_" + System.currentTimeMillis();
        }

        MDC.put(LOG_TAG_KEY, tag);
        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader(HEADER_TRACE_ID, traceId);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
    {
        MDC.clear();
    }
}
