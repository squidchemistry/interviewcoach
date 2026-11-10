package com.aicoach.execution.service;

import com.aicoach.common.error.AppException;
import com.aicoach.common.error.ErrorCode;
import com.aicoach.execution.service.CodeExecutor.RawResult;
import com.aicoach.execution.service.CodeExecutor.RawStatus;
import com.aicoach.question.dto.QuestionDetails.DsaDetail;
import com.aicoach.question.dto.QuestionDetails.TestCase;
import com.aicoach.user.domain.CodeLanguage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Runs a candidate's function against a problem's tests: builds the program (driver + candidate code), executes it in
 * the sandbox, and judges each output with the problem's comparator. Hidden test inputs and outputs are never
 * returned to clients; only their verdicts.
 */
@Service
public class CodeRunner {

    public static final int MAX_SOURCE_CHARS = 64 * 1024;

    private final CodeExecutor executor;
    private final HarnessBuilder harness;
    private final ObjectMapper json;

    public CodeRunner(CodeExecutor executor, HarnessBuilder harness, ObjectMapper json) {
        this.executor = executor;
        this.harness = harness;
        this.json = json;
    }

    public enum Verdict {
        ACCEPTED, WRONG_ANSWER, TIME_LIMIT_EXCEEDED, MEMORY_LIMIT_EXCEEDED, RUNTIME_ERROR, COMPILATION_ERROR,
        INTERNAL_ERROR, NOT_EXECUTED
    }

    public record TestResult(int index, boolean sample, Verdict verdict, JsonNode input, JsonNode expected, String actual,
            String stdout, String stderr, Integer timeMs) {
    }

    public record RunResult(Verdict verdict, int passed, int total, List<TestResult> tests, String compileOutput,
            Integer runtimeMs, String executor, boolean executed) {
    }

    public boolean executesCode() {
        return executor.executesCode();
    }

    public String executorName() {
        return executor.name();
    }

    /** {@code includeHidden=false} runs the sample tests only ("Run"); {@code true} runs everything ("Submit"). */
    public RunResult run(DsaDetail problem, CodeLanguage language, String source, boolean includeHidden) {
        if (source == null || source.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Source code is empty");
        }
        if (source.length() > MAX_SOURCE_CHARS) {
            throw new AppException(ErrorCode.PAYLOAD_TOO_LARGE, "Source code exceeds 64 KB");
        }
        List<TestCase> tests = includeHidden ? problem.testCases() : problem.samples();
        String program = harness.program(problem, language, source);
        List<RawResult> raw = executor.run(language, program,
                tests.stream().map(t -> harness.stdin(problem, t.input())).toList());
        OutputComparator comparator = OutputComparator.forName(problem.comparator());

        List<TestResult> results = new ArrayList<>();
        int passed = 0;
        int maxTime = 0;
        String compileOutput = null;
        for (int i = 0; i < tests.size(); i++) {
            TestCase test = tests.get(i);
            RawResult r = raw.get(i);
            Verdict verdict = switch (r.status()) {
                case OK -> judge(comparator, test.expected(), r.stdout()) ? Verdict.ACCEPTED : Verdict.WRONG_ANSWER;
                case COMPILATION_ERROR -> Verdict.COMPILATION_ERROR;
                case RUNTIME_ERROR -> Verdict.RUNTIME_ERROR;
                case TIME_LIMIT_EXCEEDED -> Verdict.TIME_LIMIT_EXCEEDED;
                case MEMORY_LIMIT_EXCEEDED -> Verdict.MEMORY_LIMIT_EXCEEDED;
                case NOT_EXECUTED -> Verdict.NOT_EXECUTED;
                case INTERNAL_ERROR -> Verdict.INTERNAL_ERROR;
            };
            if (verdict == Verdict.ACCEPTED) {
                passed++;
            }
            if (r.status() == RawStatus.COMPILATION_ERROR && compileOutput == null) {
                compileOutput = truncate(r.compileOutput(), 4_000);
            }
            if (r.timeMs() != null) {
                maxTime = Math.max(maxTime, r.timeMs());
            }
            results.add(new TestResult(i, test.sample(), verdict, test.input(), test.expected(),
                    truncate(lastLine(r.stdout()), 2_000), truncate(r.stdout(), 2_000), truncate(r.stderr(), 2_000),
                    r.timeMs()));
        }
        Verdict overall = results.stream().map(TestResult::verdict).filter(v -> v != Verdict.ACCEPTED).findFirst()
                .orElse(Verdict.ACCEPTED);
        return new RunResult(overall, passed, tests.size(), results, compileOutput, maxTime == 0 ? null : maxTime,
                executor.name(), executor.executesCode());
    }

    private boolean judge(OutputComparator comparator, JsonNode expected, String stdout) {
        String line = lastLine(stdout);
        if (line.isEmpty()) {
            return false;
        }
        try {
            return comparator.matches(expected, json.readTree(line));
        } catch (Exception e) {
            return false;
        }
    }

    /** The driver prints the result last; anything the candidate printed before it is ignored. */
    static String lastLine(String stdout) {
        if (stdout == null) {
            return "";
        }
        String[] lines = stdout.strip().split("\\R");
        return lines.length == 0 ? "" : lines[lines.length - 1].strip();
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() > max ? text.substring(0, max) + "…" : text;
    }
}
