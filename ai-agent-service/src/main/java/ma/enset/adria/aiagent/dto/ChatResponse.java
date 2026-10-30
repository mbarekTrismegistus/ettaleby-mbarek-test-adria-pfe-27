package ma.enset.adria.aiagent.dto;

import java.util.UUID;

public record ChatResponse(UUID conversationId, String answer) {
}
