package ma.enset.adria.aiagent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.mcp.McpToolFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.util.Set;

@Configuration
public class AgentConfiguration {

    @Bean
    ChatMemory chatMemory(@Value("${bank.agent.memory.max-messages:20}") int maxMessages) {
        return MessageWindowChatMemory.builder().maxMessages(maxMessages).build();
    }

    @Bean
    ChatClient bankingChatClient(ChatClient.Builder builder, ChatMemory memory,
                                @Value("classpath:prompts/banking-system.txt") Resource systemPrompt) {
        return builder.defaultSystem(systemPrompt)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build();
    }

    @Bean
    McpToolFilter bankingToolFilter() {
        // Balance changes must go through transaction-service and create a ledger record.
        Set<String> excluded = Set.of("debitAccount", "creditAccount");
        return (connection, tool) -> !excluded.contains(tool.name());
    }
}
