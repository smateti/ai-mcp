package com.naagi.llm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class ConversationTurn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    private int turnNumber;
    private String role;

    @Column(columnDefinition = "CLOB")
    private String content;

    private LocalDateTime createdAt;

    public ConversationTurn(Conversation conversation, int turnNumber, String role, String content) {
        this.conversation = conversation;
        this.turnNumber = turnNumber;
        this.role = role;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }
}
