package com.example.chat.service;

import com.example.chat.entity.Conversation;
import com.example.chat.entity.Message;
import com.example.chat.repository.ConversationRepository;
import com.example.chat.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ConversationService {

  private final ConversationRepository conversationRepository;
  private final MessageRepository messageRepository;

  public ConversationService(ConversationRepository conversationRepository,
                             MessageRepository messageRepository) {
    this.conversationRepository = conversationRepository;
    this.messageRepository = messageRepository;
  }

  /**
   * Create a new conversation
   */
  @Transactional
  public Conversation createConversation(String title) {
    Conversation conversation = new Conversation(title);
    return conversationRepository.save(conversation);
  }

  /**
   * Get all conversations ordered by most recent first
   */
  public List<Conversation> getAllConversations() {
    return conversationRepository.findAllByOrderByUpdatedAtDesc();
  }

  /**
   * Get a conversation by ID
   */
  public Optional<Conversation> getConversation(Long id) {
    return conversationRepository.findById(id);
  }

  /**
   * Delete a conversation
   */
  @Transactional
  public void deleteConversation(Long id) {
    conversationRepository.deleteById(id);
  }

  /**
   * Add a message to a conversation
   */
  @Transactional
  public Message addMessage(Long conversationId, String role, String content) {
    Conversation conversation = conversationRepository.findById(conversationId)
        .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));

    Message message = new Message(role, content);
    conversation.addMessage(message);
    conversation = conversationRepository.save(conversation);

    // Return the persisted message with generated ID
    return conversation.getMessages().stream()
        .filter(m -> m.getContent().equals(content) && m.getRole().equals(role))
        .reduce((first, second) -> second) // Get the last matching message
        .orElse(message);
  }

  /**
   * Get all messages for a conversation
   */
  public List<Message> getMessages(Long conversationId) {
    return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
  }

  /**
   * Update conversation title
   */
  @Transactional
  public Conversation updateTitle(Long conversationId, String newTitle) {
    Conversation conversation = conversationRepository.findById(conversationId)
        .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));

    conversation.setTitle(newTitle);
    return conversationRepository.save(conversation);
  }
}
