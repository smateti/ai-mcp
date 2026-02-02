package com.naagi.llm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Conversation {

    @Id
    private String id;

    private String systemPrompt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int turnCount;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("turnNumber ASC")
    private List<ConversationTurn> turns = new ArrayList<>();

    public Conversation(String systemPrompt) {
        this.id = UUID.randomUUID().toString();
        this.systemPrompt = systemPrompt;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.turnCount = 0;
    }

    public void addTurn(String role, String content) {
        turnCount++;
        ConversationTurn turn = new ConversationTurn(this, turnCount, role, content);
        turns.add(turn);
        updatedAt = LocalDateTime.now();
    }
}
