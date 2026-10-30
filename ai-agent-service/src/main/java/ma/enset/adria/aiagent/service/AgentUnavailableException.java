package ma.enset.adria.aiagent.service;

import java.util.UUID;

public class AgentUnavailableException extends RuntimeException {
    private final UUID conversationId;

    public AgentUnavailableException(UUID conversationId, RuntimeException cause) {
        super("The banking assistant could not complete the request", cause);
        this.conversationId = conversationId;
    }

    public UUID getConversationId() {
        return conversationId;
    }
}
