package com.aicoach.ai.service.provider;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aicoach.ai.AiException;
import com.aicoach.ai.AiTypes.AiPurpose;
import com.aicoach.ai.AiTypes.CallContext;
import com.aicoach.ai.AiTypes.ChatMessage;
import com.aicoach.ai.AiTypes.LlmRequest;
import com.aicoach.ai.AiTypes.LlmResult;
import com.aicoach.ai.AiTypes.ModelTier;
import com.aicoach.ai.service.embedding.OpenAiEmbeddingProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The real vendor adapters against WireMock: request shape, parsing, streaming and error classification. */
class LlmProvidersWireMockTest {

    static final WireMockServer SERVER = new WireMockServer(options().dynamicPort());
    static final ObjectMapper JSON = new ObjectMapper();

    @BeforeAll
    static void start() {
        SERVER.start();
    }

    @AfterAll
    static void stop() {
        SERVER.stop();
    }

    @BeforeEach
    void reset() {
        SERVER.resetAll();
    }

    static LlmRequest request() {
        return new LlmRequest("interviewer", "1", ModelTier.FAST, "test-model", "You are Alex.",
                List.of(ChatMessage.user("Hello")), 0.2, 100, false, Map.of(),
                CallContext.system(AiPurpose.INTERVIEWER_TURN));
    }

    String base() {
        return "http://localhost:" + SERVER.port();
    }

    @Test
    void anthropicCompleteSendsHeadersAndParsesTextAndUsage() {
        SERVER.stubFor(post("/v1/messages").willReturn(aResponse().withHeader("Content-Type", "application/json")
                .withBody("{\"model\":\"test-model\",\"content\":[{\"type\":\"text\",\"text\":\"Hi \"},"
                        + "{\"type\":\"text\",\"text\":\"there\"}],\"usage\":{\"input_tokens\":12,\"output_tokens\":3}}")));
        LlmResult result = new AnthropicLlmProvider("key-1", base(), Duration.ofSeconds(5), JSON).complete(request());
        assertThat(result.text()).isEqualTo("Hi there");
        assertThat(result.inputTokens()).isEqualTo(12);
        assertThat(result.outputTokens()).isEqualTo(3);
        SERVER.verify(postRequestedFor(urlEqualTo("/v1/messages")).withHeader("x-api-key", equalTo("key-1"))
                .withRequestBody(equalToJson("{\"model\":\"test-model\",\"system\":\"You are Alex.\",\"stream\":false}",
                        true, true)));
    }

    @Test
    void anthropicStreamsDeltas() {
        String sse = """
                event: message_start
                data: {"type":"message_start","message":{"usage":{"input_tokens":9}}}

                event: content_block_delta
                data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"Tell me "}}

                event: ping
                data: {"type":"ping"}

                event: content_block_delta
                data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"about it."}}

                event: message_delta
                data: {"type":"message_delta","usage":{"output_tokens":4}}

                """;
        SERVER.stubFor(post("/v1/messages").willReturn(aResponse().withHeader("Content-Type", "text/event-stream").withBody(sse)));
        List<String> deltas = new ArrayList<>();
        LlmResult result = new AnthropicLlmProvider("k", base(), Duration.ofSeconds(5), JSON).stream(request(), deltas::add);
        assertThat(deltas).containsExactly("Tell me ", "about it.");
        assertThat(result.text()).isEqualTo("Tell me about it.");
        assertThat(result.inputTokens()).isEqualTo(9);
        assertThat(result.outputTokens()).isEqualTo(4);
    }

    @Test
    void classifiesErrorsAsTransientOrPermanent() {
        AnthropicLlmProvider provider = new AnthropicLlmProvider("k", base(), Duration.ofSeconds(5), JSON);
        SERVER.stubFor(post("/v1/messages").willReturn(aResponse().withStatus(529)));
        assertThatThrownBy(() -> provider.complete(request())).isInstanceOf(AiException.Transient.class);
        SERVER.stubFor(post("/v1/messages").willReturn(aResponse().withStatus(429)));
        assertThatThrownBy(() -> provider.complete(request())).isInstanceOf(AiException.Transient.class);
        SERVER.stubFor(post("/v1/messages").willReturn(aResponse().withStatus(401).withBody("{\"error\":\"bad key\"}")));
        assertThatThrownBy(() -> provider.complete(request())).isInstanceOf(AiException.class)
                .isNotInstanceOf(AiException.Transient.class);
        SERVER.stubFor(post("/v1/messages").willReturn(aResponse().withStatus(200).withBody("not json")));
        assertThatThrownBy(() -> provider.complete(request())).isInstanceOf(AiException.Transient.class);
    }

    @Test
    void openAiCompatibleCompleteAndStream() {
        SERVER.stubFor(post("/v1/chat/completions").withHeader("Authorization", equalTo("Bearer sk-1"))
                .withRequestBody(equalToJson("{\"stream\":false}", true, true))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(
                        "{\"model\":\"m\",\"choices\":[{\"message\":{\"content\":\"Sure.\"}}],"
                                + "\"usage\":{\"prompt_tokens\":7,\"completion_tokens\":2}}")));
        OpenAiCompatibleLlmProvider provider = new OpenAiCompatibleLlmProvider("sk-1", base() + "/v1", Duration.ofSeconds(5), JSON);
        LlmResult result = provider.complete(request());
        assertThat(result.text()).isEqualTo("Sure.");
        assertThat(result.inputTokens()).isEqualTo(7);

        String sse = """
                data: {"choices":[{"delta":{"content":"One "}}]}

                data: {"choices":[{"delta":{"content":"thing"}}]}

                data: {"choices":[],"usage":{"prompt_tokens":5,"completion_tokens":2}}

                data: [DONE]

                """;
        SERVER.stubFor(post("/v1/chat/completions").withRequestBody(equalToJson("{\"stream\":true}", true, true))
                .willReturn(aResponse().withHeader("Content-Type", "text/event-stream").withBody(sse)));
        List<String> deltas = new ArrayList<>();
        LlmResult streamed = provider.stream(request(), deltas::add);
        assertThat(deltas).containsExactly("One ", "thing");
        assertThat(streamed.text()).isEqualTo("One thing");
        assertThat(streamed.outputTokens()).isEqualTo(2);
    }

    @Test
    void openAiEmbeddingsReturnTheVector() {
        StringBuilder vector = new StringBuilder("[");
        for (int i = 0; i < 1024; i++) {
            vector.append(i == 0 ? "" : ",").append(i / 1024.0);
        }
        vector.append(']');
        SERVER.stubFor(post("/v1/embeddings").willReturn(aResponse().withHeader("Content-Type", "application/json")
                .withBody("{\"data\":[{\"embedding\":" + vector + "}]}")));
        float[] embedding = new OpenAiEmbeddingProvider("sk", base() + "/v1", "text-embedding-3-small", JSON).embed("graphs");
        assertThat(embedding).hasSize(1024);
        assertThat(embedding[512]).isEqualTo(0.5f);
    }
}
