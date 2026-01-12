package com.example.rag.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Tracks how frequently questions are asked to identify candidates for caching.
 */
@Entity
@Table(name = "question_frequency", indexes = {
    @Index(name = "idx_question_hash", columnList = "questionHash"),
    @Index(name = "idx_ask_count", columnList = "askCount DESC")
})
public class QuestionFrequency {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 64)
  private String questionHash;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String question;

  @Column(nullable = false)
  private Integer askCount = 0;

  @Column(name = "first_asked", nullable = false)
  private LocalDateTime firstAsked;

  @Column(name = "last_asked", nullable = false)
  private LocalDateTime lastAsked;

  @Column(name = "is_cached", nullable = false)
  private Boolean isCached = false;

  public QuestionFrequency() {
    this.firstAsked = LocalDateTime.now();
    this.lastAsked = LocalDateTime.now();
  }

  public QuestionFrequency(String questionHash, String question) {
    this();
    this.questionHash = questionHash;
    this.question = question;
    this.askCount = 1;
  }

  public void incrementCount() {
    this.askCount++;
    this.lastAsked = LocalDateTime.now();
  }

  // Getters and Setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getQuestionHash() {
    return questionHash;
  }

  public void setQuestionHash(String questionHash) {
    this.questionHash = questionHash;
  }

  public String getQuestion() {
    return question;
  }

  public void setQuestion(String question) {
    this.question = question;
  }

  public Integer getAskCount() {
    return askCount;
  }

  public void setAskCount(Integer askCount) {
    this.askCount = askCount;
  }

  public LocalDateTime getFirstAsked() {
    return firstAsked;
  }

  public void setFirstAsked(LocalDateTime firstAsked) {
    this.firstAsked = firstAsked;
  }

  public LocalDateTime getLastAsked() {
    return lastAsked;
  }

  public void setLastAsked(LocalDateTime lastAsked) {
    this.lastAsked = lastAsked;
  }

  public Boolean getIsCached() {
    return isCached;
  }

  public void setIsCached(Boolean isCached) {
    this.isCached = isCached;
  }
}
