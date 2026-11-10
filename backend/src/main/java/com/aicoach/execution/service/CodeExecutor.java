package com.aicoach.execution.service;

import com.aicoach.user.domain.CodeLanguage;
import java.util.List;

/**
 * Strategy: runs a complete program against several stdin inputs in a sandbox. User code NEVER runs on the API host
 * (enforced by an ArchUnit rule); implementations call an isolated sandbox service.
 */
public interface CodeExecutor {

    String name();

    /** False for executors that cannot really run code (the mock), so the UI and evaluator can say so. */
    boolean executesCode();

    List<RawResult> run(CodeLanguage language, String program, List<String> stdins);

    enum RawStatus { OK, COMPILATION_ERROR, RUNTIME_ERROR, TIME_LIMIT_EXCEEDED, MEMORY_LIMIT_EXCEEDED, INTERNAL_ERROR, NOT_EXECUTED }

    record RawResult(RawStatus status, String stdout, String stderr, String compileOutput, Integer timeMs, Integer memoryKb) {
    }
}
