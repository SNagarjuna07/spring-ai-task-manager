package com.nagarjuna.toolcalling.config;

import com.nagarjuna.toolcalling.service.TaskToolService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.time.LocalDate;
import java.util.Map;

@Configuration
public class ChatClientConfig {

    @Value("classpath:/prompts/system.st")
    private Resource promptFile;

    @Bean
    public ChatClient chatClient(
            ChatClient.Builder builder,
            ChatMemory chatMemory,
            TaskToolService toolService
    ) {

        PromptTemplate template = new PromptTemplate(promptFile);

        String prompt = template.render(
                Map.of("date", LocalDate.now().toString())
        );

        return builder
                .defaultSystem(prompt)
                .defaultTools(toolService)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor
                                .builder(chatMemory)
                                .order(1)
                                .build(),
                        new SimpleLoggerAdvisor()
                )
                .build();
    }
}
