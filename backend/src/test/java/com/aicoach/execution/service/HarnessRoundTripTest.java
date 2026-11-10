package com.aicoach.execution.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.aicoach.execution.service.CodeExecutor.RawResult;
import com.aicoach.execution.service.CodeExecutor.RawStatus;
import com.aicoach.execution.service.CodeRunner.RunResult;
import com.aicoach.execution.service.CodeRunner.Verdict;
import com.aicoach.question.dto.QuestionDetails.DsaDetail;
import com.aicoach.user.domain.CodeLanguage;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Verifies the generated drivers end to end: every seeded DSA problem's Python reference solution, wrapped by
 * {@link HarnessBuilder}, must pass all of its tests; the JavaScript and Java starter code must compile, read the
 * inputs and print a parseable result. Runs local interpreters from TEST code only (production code never executes
 * user code; that's an ArchUnit rule); skipped where an interpreter is missing.
 */
class HarnessRoundTripTest {

    private static final ObjectMapper JSON = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** Runs programs with local interpreters. */
    static final class LocalExecutor implements CodeExecutor {
        @Override
        public String name() {
            return "local-test";
        }

        @Override
        public boolean executesCode() {
            return true;
        }

        @Override
        public List<RawResult> run(CodeLanguage language, String program, List<String> stdins) {
            try {
                Path dir = Files.createTempDirectory("harness");
                try {
                    List<String> command;
                    switch (language) {
                        case PYTHON -> {
                            Files.writeString(dir.resolve("main.py"), program);
                            command = List.of("python", "main.py");
                        }
                        case JAVASCRIPT -> {
                            Files.writeString(dir.resolve("main.js"), program);
                            command = List.of("node", "main.js");
                        }
                        case JAVA -> {
                            Files.writeString(dir.resolve("Main.java"), program);
                            String javaHome = System.getProperty("java.home");
                            javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
                            java.io.ByteArrayOutputStream log = new java.io.ByteArrayOutputStream();
                            int exit = compiler.run(null, log, log, "-encoding", "UTF-8", "-d", dir.toString(),
                                    dir.resolve("Main.java").toString());
                            String out = log.toString(StandardCharsets.UTF_8);
                            if (exit != 0) {
                                return stdins.stream().map(s -> new RawResult(RawStatus.COMPILATION_ERROR, "", "", out, 0, 0))
                                        .toList();
                            }
                            command = List.of(tool(javaHome, "java"), "-cp", ".", "Main");
                        }
                        default -> throw new IllegalArgumentException(language.name());
                    }
                    List<RawResult> results = new ArrayList<>();
                    for (String stdin : stdins) {
                        results.add(runOnce(command, dir.toFile(), stdin));
                    }
                    return results;
                } finally {
                    try (Stream<Path> files = Files.walk(dir)) {
                        files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                    }
                }
            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }

        private static String tool(String javaHome, String name) {
            File exe = new File(javaHome, "bin/" + name + ".exe");
            return exe.exists() ? exe.getAbsolutePath() : new File(javaHome, "bin/" + name).getAbsolutePath();
        }

        private static RawResult runOnce(List<String> command, File dir, String stdin) throws IOException, InterruptedException {
            Process p = new ProcessBuilder(command).directory(dir).start();
            p.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
            p.getOutputStream().close();
            String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(20, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return new RawResult(RawStatus.TIME_LIMIT_EXCEEDED, stdout, stderr, null, 20_000, 0);
            }
            return new RawResult(p.exitValue() == 0 ? RawStatus.OK : RawStatus.RUNTIME_ERROR, stdout, stderr, null, 1, 0);
        }
    }

    static boolean available(String... command) {
        try {
            Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor(20, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    static List<JsonNode> problems() throws IOException {
        try (InputStream in = HarnessRoundTripTest.class.getResourceAsStream("/seed/questions/dsa.json")) {
            List<JsonNode> out = new ArrayList<>();
            JSON.readTree(in).forEach(out::add);
            return out;
        }
    }

    static DsaDetail detail(JsonNode problem) {
        return JSON.convertValue(problem.get("dsa"), DsaDetail.class);
    }

    private final HarnessBuilder harness = new HarnessBuilder();
    private final CodeRunner runner = new CodeRunner(new LocalExecutor(), harness, JSON);

    @TestFactory
    Stream<DynamicTest> pythonReferenceSolutionsPassEveryTest() throws IOException {
        assumeTrue(available("python", "--version"), "python is not installed");
        return problems().stream().map(problem -> DynamicTest.dynamicTest(problem.get("slug").asText(), () -> {
            DsaDetail d = detail(problem);
            RunResult result = runner.run(d, CodeLanguage.PYTHON, d.referenceSolutionPython(), true);
            assertThat(result.verdict()).as(problem.get("slug").asText() + " " + result.tests()).isEqualTo(Verdict.ACCEPTED);
            assertThat(result.passed()).isEqualTo(result.total()).isEqualTo(d.testCases().size());
        }));
    }

    @TestFactory
    Stream<DynamicTest> javascriptStartersRunAndPrintResults() throws IOException {
        assumeTrue(available("node", "--version"), "node is not installed");
        return problems().stream().map(problem -> DynamicTest.dynamicTest(problem.get("slug").asText(), () -> {
            DsaDetail d = detail(problem);
            RunResult result = runner.run(d, CodeLanguage.JAVASCRIPT, harness.starter(d, CodeLanguage.JAVASCRIPT), false);
            assertThat(result.tests()).allSatisfy(t -> assertThat(t.verdict())
                    .as(t.stderr()).isIn(Verdict.ACCEPTED, Verdict.WRONG_ANSWER));
        }));
    }

    @TestFactory
    Stream<DynamicTest> javaStartersCompileAndRun() throws IOException {
        return problems().stream().limit(12).map(problem -> DynamicTest.dynamicTest(problem.get("slug").asText(), () -> {
            DsaDetail d = detail(problem);
            RunResult result = runner.run(d, CodeLanguage.JAVA, harness.starter(d, CodeLanguage.JAVA), false);
            assertThat(result.tests()).allSatisfy(t -> assertThat(t.verdict())
                    .as(result.compileOutput() + " " + t.stderr()).isIn(Verdict.ACCEPTED, Verdict.WRONG_ANSWER));
        }));
    }

    @TestFactory
    Stream<DynamicTest> wrongPythonAnswersAreDetected() throws IOException {
        assumeTrue(available("python", "--version"), "python is not installed");
        return problems().stream().limit(5).map(problem -> DynamicTest.dynamicTest(problem.get("slug").asText(), () -> {
            DsaDetail d = detail(problem);
            String broken = harness.starter(d, CodeLanguage.PYTHON).replace("# Write your solution here",
                    "raise ValueError('boom')");
            RunResult result = runner.run(d, CodeLanguage.PYTHON, broken, false);
            assertThat(result.verdict()).isEqualTo(Verdict.RUNTIME_ERROR);
            assertThat(result.passed()).isZero();
        }));
    }
}
