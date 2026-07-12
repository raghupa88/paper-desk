package com.paperdesk.coach;

/** Thin seam over the Anthropic Messages API so {@link TradingCoachService} is testable without real HTTP calls. */
public interface AnthropicClient {
    String complete(String systemPrompt, String userMessage) throws Exception;
}
