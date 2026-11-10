package com.aicoach.execution.service;

import com.aicoach.user.domain.CodeLanguage;
import java.util.List;

/**
 * MOCK MODE ({@code CODE_EXECUTOR=mock}): never executes code. Every test is reported as NOT_EXECUTED so the UI
 * shows "code execution disabled" and the evaluator treats correctness as not measurable. Used in CI and on hosts
 * where Judge0 cannot run (see docs/09-risks.md R6). Refuses to start with the {@code prod} profile.
 */
public class MockCodeExecutor implements CodeExecutor {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public boolean executesCode() {
        return false;
    }

    @Override
    public List<RawResult> run(CodeLanguage language, String program, List<String> stdins) {
        return stdins.stream()
                .map(stdin -> new RawResult(RawStatus.NOT_EXECUTED, "", "", null, null, null))
                .toList();
    }
}
