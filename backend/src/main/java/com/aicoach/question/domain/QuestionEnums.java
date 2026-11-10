package com.aicoach.question.domain;

/** Enumerations of the question bank. */
public final class QuestionEnums {

    private QuestionEnums() {
    }

    public enum QuestionType { DSA, LLD, BEHAVIOURAL }

    public enum QuestionStatus { DRAFT, PUBLISHED, ARCHIVED }

    public enum Difficulty {
        EASY, MEDIUM, HARD;

        public Difficulty harder() {
            return this == EASY ? MEDIUM : HARD;
        }

        public Difficulty easier() {
            return this == HARD ? MEDIUM : EASY;
        }
    }
}
