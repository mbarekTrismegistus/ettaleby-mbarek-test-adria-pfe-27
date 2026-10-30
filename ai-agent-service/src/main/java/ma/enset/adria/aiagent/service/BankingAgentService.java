package ma.enset.adria.aiagent.service;

import ma.enset.adria.aiagent.dto.ChatRequest;
import ma.enset.adria.aiagent.dto.ChatResponse;
import ma.enset.adria.aiagent.dto.ToolResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class BankingAgentService {
    private final ChatClient chatClient;
    private final ChatMemory memory;
    private final ToolCallbackProvider tools;
    private final Object[] conversationLocks = new Object[64];

    public BankingAgentService(ChatClient chatClient, ChatMemory memory, ToolCallbackProvider tools) {
        this.chatClient = chatClient;
        this.memory = memory;
        this.tools = tools;
        Arrays.setAll(conversationLocks, index -> new Object());
    }

    public ChatResponse chat(ChatRequest request) {
        UUID id = request.conversationId() == null ? UUID.randomUUID() : request.conversationId();
        // Serialize turns for a conversation without retaining an unbounded map of locks.
        synchronized (lock(id)) {
            try {
                String answer = chatClient.prompt().user(request.message())
                        .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, id.toString()))
                        .toolCallbacks(availableTools()).call().content();
                if (answer == null || answer.isBlank()) {
                    throw new IllegalStateException("The model returned no answer");
                }
                return new ChatResponse(id, answer);
            } catch (RuntimeException exception) {
                throw new AgentUnavailableException(id, exception);
            }
        }
    }

    public List<ToolResponse> listTools() {
        try {
            return Arrays.stream(availableTools()).map(tool -> tool.getToolDefinition())
                    .map(definition -> new ToolResponse(definition.name(), definition.description())).toList();
        } catch (RuntimeException exception) {
            throw new AgentUnavailableException(null, exception);
        }
    }

    public void clearConversation(UUID id) {
        synchronized (lock(id)) {
            memory.clear(id.toString());
        }
    }

    private ToolCallback[] availableTools() {
        ToolCallback[] callbacks = tools.getToolCallbacks();
        if (callbacks.length == 0) {
            throw new IllegalStateException("No banking MCP tools are available");
        }
        return callbacks;
    }

    private Object lock(UUID id) {
        return conversationLocks[Math.floorMod(id.hashCode(), conversationLocks.length)];
    }
}
