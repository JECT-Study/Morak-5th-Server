package com.morak.common.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(RequestResponseLoggingFilter.class);
	private static final int MAX_LOG_BODY_LENGTH = 4_096;
	private static final Pattern SENSITIVE_FIELD =
			Pattern.compile("password|passwd|token|authorization|secret|credential", Pattern.CASE_INSENSITIVE);

	private final ObjectMapper objectMapper;

	public RequestResponseLoggingFilter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
		ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
		long startedAt = System.nanoTime();

		try {
			filterChain.doFilter(wrappedRequest, wrappedResponse);
		} finally {
			long durationMillis = (System.nanoTime() - startedAt) / 1_000_000;
			log.info(
					"HTTP {} {} status={} durationMs={} request={} response={}",
					request.getMethod(),
					request.getRequestURI(),
					wrappedResponse.getStatus(),
					durationMillis,
					formatBody(
							wrappedRequest.getContentAsByteArray(),
							wrappedRequest.getContentType(),
							wrappedRequest.getCharacterEncoding()),
					formatBody(
							wrappedResponse.getContentAsByteArray(),
							wrappedResponse.getContentType(),
							wrappedResponse.getCharacterEncoding()));
			wrappedResponse.copyBodyToResponse();
		}
	}

	private String formatBody(byte[] content, String contentType, String encoding) {
		if (content.length == 0) {
			return "<empty>";
		}
		if (content.length > MAX_LOG_BODY_LENGTH) {
			return "<omitted: body exceeds log limit>";
		}
		if (contentType == null || !contentType.toLowerCase().contains("application/json")) {
			return "<omitted: non-JSON body>";
		}

		try {
			Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
			JsonNode body = objectMapper.readTree(new String(content, charset));
			return objectMapper.writeValueAsString(maskSensitiveFields(body));
		} catch (Exception exception) {
			return "<omitted: unreadable JSON body>";
		}
	}

	private JsonNode maskSensitiveFields(JsonNode node) {
		if (node instanceof ObjectNode objectNode) {
			Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
			while (fields.hasNext()) {
				Map.Entry<String, JsonNode> field = fields.next();
				if (SENSITIVE_FIELD.matcher(field.getKey()).find()) {
					objectNode.put(field.getKey(), "[MASKED]");
				} else {
					maskSensitiveFields(field.getValue());
				}
			}
		} else if (node instanceof ArrayNode arrayNode) {
			for (JsonNode item : arrayNode) {
				maskSensitiveFields(item);
			}
		}
		return node;
	}
}
