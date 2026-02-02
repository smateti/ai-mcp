package com.naagi.llm.service;

import com.naagi.llm.entity.Conversation;
import com.naagi.llm.model.*;
import com.naagi.llm.repository.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private LlamaCppProxy llamaCppProxy;

    private ConversationService service;

    @BeforeEach
    void setUp() {
        service = new ConversationService(conversationRepository, llamaCppProxy);
    }

    @Test
    void chat_newConversation_createsAndSaves() {
        ConversationRequest request = new ConversationRequest(
                null, "Hello", null, 0.7, 512, false);

        when(conversationRepository.save(any(Conversation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(llamaCppProxy.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.text("Hi there!"));

        ConversationResponse response = service.chat(request);

        assertNotNull(response.conversationId());
        assertEquals("Hi there!", response.message());
        assertEquals(1, response.turnNumber());

        // Verify save was called (once for create, once for final save)
        verify(conversationRepository, atLeast(1)).save(any(Conversation.class));
    }

    @Test
    void chat_existingConversation_appendsTurns() {
        Conversation existing = new Conversation("You are helpful");
        existing.addTurn("user", "First message");
        existing.addTurn("assistant", "First reply");

        ConversationRequest request = new ConversationRequest(
                existing.getId(), "Second message", null, 0.5, 256, false);

        when(conversationRepository.findById(existing.getId()))
                .thenReturn(Optional.of(existing));
        when(conversationRepository.save(any(Conversation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(llamaCppProxy.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.text("Second reply"));

        ConversationResponse response = service.chat(request);

        assertEquals(existing.getId(), response.conversationId());
        assertEquals("Second reply", response.message());
        assertEquals(2, response.turnNumber()); // 4 turns / 2
    }

    @Test
    void chat_sendsFullHistoryToLlm() {
        Conversation existing = new Conversation("Custom prompt");
        existing.addTurn("user", "msg1");
        existing.addTurn("assistant", "reply1");

        ConversationRequest request = new ConversationRequest(
                existing.getId(), "msg2", null, 0.7, 512, false);

        when(conversationRepository.findById(existing.getId()))
                .thenReturn(Optional.of(existing));
        when(conversationRepository.save(any(Conversation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(llamaCppProxy.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.text("reply2"));

        service.chat(request);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llamaCppProxy).chat(captor.capture());

        List<ChatMessage> messages = captor.getValue().messages();
        assertEquals(4, messages.size()); // system + user1 + assistant1 + user2
        assertEquals("system", messages.get(0).role());
        assertEquals("Custom prompt", messages.get(0).content());
        assertEquals("user", messages.get(1).role());
        assertEquals("msg1", messages.get(1).content());
        assertEquals("assistant", messages.get(2).role());
        assertEquals("reply1", messages.get(2).content());
        assertEquals("user", messages.get(3).role());
        assertEquals("msg2", messages.get(3).content());
    }

    @Test
    void chat_notFound_throws() {
        ConversationRequest request = new ConversationRequest(
                "bad-id", "Hello", null, 0.7, 512, false);

        when(conversationRepository.findById("bad-id")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.chat(request));
    }

    @Test
    void chat_usesDefaultSystemPrompt() {
        ConversationRequest request = new ConversationRequest(
                null, "Hi", null, 0.7, 512, false);

        when(conversationRepository.save(any(Conversation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(llamaCppProxy.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.text("Hello"));

        service.chat(request);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llamaCppProxy).chat(captor.capture());

        assertEquals("You are a helpful assistant.", captor.getValue().messages().get(0).content());
    }

    @Test
    void chat_usesCustomSystemPrompt() {
        ConversationRequest request = new ConversationRequest(
                null, "Hi", "Be concise", 0.7, 512, false);

        when(conversationRepository.save(any(Conversation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(llamaCppProxy.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.text("Ok"));

        service.chat(request);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llamaCppProxy).chat(captor.capture());

        assertEquals("Be concise", captor.getValue().messages().get(0).content());
    }

    @Test
    void buildMessages_includesSystemAndTurns() {
        Conversation conversation = new Conversation("System msg");
        conversation.addTurn("user", "Hello");
        conversation.addTurn("assistant", "Hi");

        List<ChatMessage> messages = service.buildMessages(conversation);

        assertEquals(3, messages.size());
        assertEquals("system", messages.get(0).role());
        assertEquals("System msg", messages.get(0).content());
        assertEquals("user", messages.get(1).role());
        assertEquals("assistant", messages.get(2).role());
    }

    @Test
    void saveAssistantTurn_savesToConversation() {
        Conversation conversation = new Conversation("sys");
        conversation.addTurn("user", "Hi");

        when(conversationRepository.findById(conversation.getId()))
                .thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any(Conversation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.saveAssistantTurn(conversation.getId(), "Response");

        assertEquals(2, conversation.getTurnCount());
        verify(conversationRepository).save(conversation);
    }
}
