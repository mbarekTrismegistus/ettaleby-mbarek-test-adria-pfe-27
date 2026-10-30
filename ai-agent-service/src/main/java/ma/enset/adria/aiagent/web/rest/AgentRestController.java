package ma.enset.adria.aiagent.web.rest;

import jakarta.validation.Valid;
import ma.enset.adria.aiagent.dto.ChatRequest;
import ma.enset.adria.aiagent.dto.ChatResponse;
import ma.enset.adria.aiagent.dto.ToolResponse;
import ma.enset.adria.aiagent.service.BankingAgentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agent")
public class AgentRestController {
    private final BankingAgentService agent;

    public AgentRestController(BankingAgentService agent) {
        this.agent = agent;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return agent.chat(request);
    }

    @GetMapping("/tools")
    public List<ToolResponse> tools() {
        return agent.listTools();
    }

    @DeleteMapping("/conversations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@PathVariable UUID id) {
        agent.clearConversation(id);
    }
}
