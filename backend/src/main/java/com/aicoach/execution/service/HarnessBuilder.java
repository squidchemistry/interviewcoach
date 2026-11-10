package com.aicoach.execution.service;

import com.aicoach.question.dto.QuestionDetails.DsaDetail;
import com.aicoach.question.dto.QuestionDetails.Param;
import com.aicoach.user.domain.CodeLanguage;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Builder of runnable programs for DSA problems. The candidate writes only the function (LeetCode style); this class
 * adds a language-specific driver that reads the arguments from stdin in a simple line format and prints the return
 * value as compact JSON, which {@link OutputComparator}s then compare with the expected value.
 * <p>
 * stdin format per argument: scalars on one line; 1-D arrays as a count line then the values (numbers/booleans
 * space-separated on one line, strings one per line); 2-D arrays as a row-count line then one line per row
 * ({@code len v1 v2 ...}; for strings {@code len} then {@code len} lines).
 * <p>
 * In JavaScript, {@code long} values are {@code BigInt} so 64-bit results stay exact.
 */
@Component
public class HarnessBuilder {

    public Map<CodeLanguage, String> starterCode(DsaDetail detail) {
        Map<CodeLanguage, String> starters = new EnumMap<>(CodeLanguage.class);
        for (CodeLanguage language : CodeLanguage.values()) {
            starters.put(language, starter(detail, language));
        }
        return starters;
    }

    public String starter(DsaDetail d, CodeLanguage language) {
        String fn = d.functionName();
        return switch (language) {
            case PYTHON -> "def " + fn + "(" + d.params().stream().map(p -> p.name() + ": " + pyType(p.type()))
                    .collect(Collectors.joining(", ")) + ") -> " + pyType(d.returnType()) + ":\n    # Write your solution here\n    "
                    + "return " + pyDefault(d.returnType()) + "\n";
            case JAVASCRIPT -> "/**\n" + d.params().stream().map(p -> " * @param {" + jsType(p.type()) + "} " + p.name())
                    .collect(Collectors.joining("\n")) + "\n * @return {" + jsType(d.returnType()) + "}\n */\nfunction " + fn
                    + "(" + d.params().stream().map(Param::name).collect(Collectors.joining(", "))
                    + ") {\n  // Write your solution here\n  return " + jsDefault(d.returnType()) + ";\n}\n";
            case JAVA -> "class Solution {\n    public " + javaType(d.returnType()) + " " + fn + "("
                    + d.params().stream().map(p -> javaType(p.type()) + " " + p.name()).collect(Collectors.joining(", "))
                    + ") {\n        // Write your solution here\n        return " + javaDefault(d.returnType()) + ";\n    }\n}\n";
            case CPP -> "class Solution {\npublic:\n    " + cppType(d.returnType()) + " " + fn + "("
                    + d.params().stream().map(p -> cppParam(p.type()) + " " + p.name()).collect(Collectors.joining(", "))
                    + ") {\n        // Write your solution here\n        return " + cppDefault(d.returnType()) + ";\n    }\n};\n";
        };
    }

    /** Candidate code plus driver: a complete program for the given language. */
    public String program(DsaDetail d, CodeLanguage language, String candidateSource) {
        return switch (language) {
            case PYTHON -> PY_PRELUDE + "\n" + candidateSource + "\n\nif __name__ == '__main__':\n    _args = ["
                    + d.params().stream().map(p -> pyReader(p.type())).collect(Collectors.joining(", "))
                    + "]\n    print(json.dumps(" + d.functionName() + "(*_args), separators=(',', ':')))\n";
            case JAVASCRIPT -> JS_PRELUDE + "\n" + candidateSource + "\n\n{\n  const _args = ["
                    + d.params().stream().map(p -> jsReader(p.type())).collect(Collectors.joining(", "))
                    + "];\n  process.stdout.write(__json(" + d.functionName() + "(..._args)) + '\\n');\n}\n";
            case JAVA -> "import java.util.*;\nimport java.io.*;\n\n" + candidateSource + "\n\n" + javaMain(d);
            case CPP -> "#include <bits/stdc++.h>\nusing namespace std;\n\n" + candidateSource + "\n\n" + cppMain(d);
        };
    }

    /** stdin for one test case (arguments in parameter order). */
    public String stdin(DsaDetail d, JsonNode input) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < d.params().size(); i++) {
            encode(d.params().get(i).type(), input.get(i), lines);
        }
        return String.join("\n", lines) + "\n";
    }

    private static void encode(String type, JsonNode value, List<String> lines) {
        switch (type) {
            case "int", "long", "double" -> lines.add(value.asText());
            case "boolean" -> lines.add(value.asBoolean() ? "true" : "false");
            case "string" -> lines.add(value.asText());
            case "int[]", "long[]", "boolean[]" -> {
                lines.add(Integer.toString(value.size()));
                List<String> parts = new ArrayList<>();
                value.forEach(v -> parts.add(v.isBoolean() ? (v.asBoolean() ? "true" : "false") : v.asText()));
                lines.add(String.join(" ", parts));
            }
            case "string[]" -> {
                lines.add(Integer.toString(value.size()));
                value.forEach(v -> lines.add(v.asText()));
            }
            case "int[][]" -> {
                lines.add(Integer.toString(value.size()));
                for (JsonNode row : value) {
                    List<String> parts = new ArrayList<>();
                    parts.add(Integer.toString(row.size()));
                    row.forEach(v -> parts.add(v.asText()));
                    lines.add(String.join(" ", parts));
                }
            }
            case "string[][]" -> {
                lines.add(Integer.toString(value.size()));
                for (JsonNode row : value) {
                    lines.add(Integer.toString(row.size()));
                    row.forEach(v -> lines.add(v.asText()));
                }
            }
            default -> throw new IllegalArgumentException("Unsupported type " + type);
        }
    }

    // ------------------------------------------------------------------ Python

    private static final String PY_PRELUDE = """
            import sys, json
            _lines = sys.stdin.read().split('\\n')
            _pos = 0
            def _next():
                global _pos
                line = _lines[_pos] if _pos < len(_lines) else ''
                _pos += 1
                return line
            def _int(): return int(_next().strip())
            def _float(): return float(_next().strip())
            def _bool(): return _next().strip() == 'true'
            def _str(): return _next()
            def _ints():
                n = int(_next().strip()); line = _next()
                return [int(x) for x in line.split()] if n > 0 else []
            def _bools():
                n = int(_next().strip()); line = _next()
                return [x == 'true' for x in line.split()] if n > 0 else []
            def _strs():
                n = int(_next().strip())
                return [_next() for _ in range(n)]
            def _intss():
                r = int(_next().strip())
                return [[int(x) for x in _next().split()[1:]] for _ in range(r)]
            def _strss():
                r = int(_next().strip()); out = []
                for _ in range(r):
                    n = int(_next().strip()); out.append([_next() for _ in range(n)])
                return out
            """;

    private static String pyReader(String type) {
        return switch (type) {
            case "int", "long" -> "_int()";
            case "double" -> "_float()";
            case "boolean" -> "_bool()";
            case "string" -> "_str()";
            case "int[]", "long[]" -> "_ints()";
            case "boolean[]" -> "_bools()";
            case "string[]" -> "_strs()";
            case "int[][]" -> "_intss()";
            case "string[][]" -> "_strss()";
            default -> throw new IllegalArgumentException(type);
        };
    }

    private static String pyType(String type) {
        return switch (type) {
            case "int", "long" -> "int";
            case "double" -> "float";
            case "boolean" -> "bool";
            case "string" -> "str";
            case "int[]", "long[]" -> "list[int]";
            case "boolean[]" -> "list[bool]";
            case "string[]" -> "list[str]";
            case "int[][]" -> "list[list[int]]";
            case "string[][]" -> "list[list[str]]";
            default -> throw new IllegalArgumentException(type);
        };
    }

    private static String pyDefault(String type) {
        return switch (type) {
            case "int", "long" -> "0";
            case "double" -> "0.0";
            case "boolean" -> "False";
            case "string" -> "\"\"";
            default -> "[]";
        };
    }

    // ------------------------------------------------------------------ JavaScript

    private static final String JS_PRELUDE = """
            const __lines = require('fs').readFileSync(0, 'utf8').split('\\n');
            let __pos = 0;
            const __next = () => (__pos < __lines.length ? __lines[__pos++] : (__pos++, ''));
            const __int = () => parseInt(__next().trim(), 10);
            const __long = () => BigInt(__next().trim());
            const __float = () => parseFloat(__next().trim());
            const __bool = () => __next().trim() === 'true';
            const __str = () => __next();
            const __nums = (conv) => { const n = parseInt(__next().trim(), 10); const line = __next(); return n > 0 ? line.trim().split(/\\s+/).map(conv) : []; };
            const __strs = () => { const n = parseInt(__next().trim(), 10); const out = []; for (let i = 0; i < n; i++) out.push(__next()); return out; };
            const __intss = () => { const r = parseInt(__next().trim(), 10); const out = []; for (let i = 0; i < r; i++) out.push(__next().trim().split(/\\s+/).slice(1).filter((x) => x !== '').map((x) => parseInt(x, 10))); return out; };
            const __strss = () => { const r = parseInt(__next().trim(), 10); const out = []; for (let i = 0; i < r; i++) out.push(__strs()); return out; };
            const __json = (v) => JSON.stringify(v, (k, x) => (typeof x === 'bigint' ? `__BIG__${x}__BIG__` : x)).replace(/"__BIG__(-?\\d+)__BIG__"/g, '$1');
            """;

    private static String jsReader(String type) {
        return switch (type) {
            case "int" -> "__int()";
            case "long" -> "__long()";
            case "double" -> "__float()";
            case "boolean" -> "__bool()";
            case "string" -> "__str()";
            case "int[]" -> "__nums((x) => parseInt(x, 10))";
            case "long[]" -> "__nums((x) => BigInt(x))";
            case "boolean[]" -> "__nums((x) => x === 'true')";
            case "string[]" -> "__strs()";
            case "int[][]" -> "__intss()";
            case "string[][]" -> "__strss()";
            default -> throw new IllegalArgumentException(type);
        };
    }

    private static String jsType(String type) {
        return switch (type) {
            case "int", "double" -> "number";
            case "long" -> "bigint";
            case "boolean" -> "boolean";
            case "string" -> "string";
            case "int[]" -> "number[]";
            case "long[]" -> "bigint[]";
            case "boolean[]" -> "boolean[]";
            case "string[]" -> "string[]";
            case "int[][]" -> "number[][]";
            case "string[][]" -> "string[][]";
            default -> throw new IllegalArgumentException(type);
        };
    }

    private static String jsDefault(String type) {
        return switch (type) {
            case "int", "double" -> "0";
            case "long" -> "0n";
            case "boolean" -> "false";
            case "string" -> "''";
            default -> "[]";
        };
    }

    // ------------------------------------------------------------------ Java

    private static String javaType(String type) {
        return switch (type) {
            case "int" -> "int";
            case "long" -> "long";
            case "double" -> "double";
            case "boolean" -> "boolean";
            case "string" -> "String";
            case "int[]" -> "int[]";
            case "long[]" -> "long[]";
            case "boolean[]" -> "boolean[]";
            case "string[]" -> "String[]";
            case "int[][]" -> "int[][]";
            case "string[][]" -> "String[][]";
            default -> throw new IllegalArgumentException(type);
        };
    }

    private static String javaDefault(String type) {
        return switch (type) {
            case "int" -> "0";
            case "long" -> "0L";
            case "double" -> "0.0";
            case "boolean" -> "false";
            case "string" -> "\"\"";
            case "int[]" -> "new int[0]";
            case "long[]" -> "new long[0]";
            case "boolean[]" -> "new boolean[0]";
            case "string[]" -> "new String[0]";
            case "int[][]" -> "new int[0][]";
            case "string[][]" -> "new String[0][]";
            default -> throw new IllegalArgumentException(type);
        };
    }

    private static String javaReader(String type) {
        return switch (type) {
            case "int" -> "Integer.parseInt(next().trim())";
            case "long" -> "Long.parseLong(next().trim())";
            case "double" -> "Double.parseDouble(next().trim())";
            case "boolean" -> "next().trim().equals(\"true\")";
            case "string" -> "next()";
            case "int[]" -> "ints()";
            case "long[]" -> "longs()";
            case "boolean[]" -> "bools()";
            case "string[]" -> "strs()";
            case "int[][]" -> "intss()";
            case "string[][]" -> "strss()";
            default -> throw new IllegalArgumentException(type);
        };
    }

    private static String javaMain(DsaDetail d) {
        StringBuilder args = new StringBuilder();
        StringBuilder call = new StringBuilder();
        for (int i = 0; i < d.params().size(); i++) {
            Param p = d.params().get(i);
            args.append("        ").append(javaType(p.type())).append(" a").append(i).append(" = ")
                    .append(javaReader(p.type())).append(";\n");
            call.append(i == 0 ? "" : ", ").append("a").append(i);
        }
        return """
                public class Main {
                    static List<String> lines; static int pos = 0;
                    static String next() { return pos < lines.size() ? lines.get(pos++) : ""; }
                    static int[] ints() { int n = Integer.parseInt(next().trim()); String l = next().trim(); int[] r = new int[n]; if (n > 0) { String[] p = l.split("\\\\s+"); for (int i = 0; i < n; i++) r[i] = Integer.parseInt(p[i]); } return r; }
                    static long[] longs() { int n = Integer.parseInt(next().trim()); String l = next().trim(); long[] r = new long[n]; if (n > 0) { String[] p = l.split("\\\\s+"); for (int i = 0; i < n; i++) r[i] = Long.parseLong(p[i]); } return r; }
                    static boolean[] bools() { int n = Integer.parseInt(next().trim()); String l = next().trim(); boolean[] r = new boolean[n]; if (n > 0) { String[] p = l.split("\\\\s+"); for (int i = 0; i < n; i++) r[i] = p[i].equals("true"); } return r; }
                    static String[] strs() { int n = Integer.parseInt(next().trim()); String[] r = new String[n]; for (int i = 0; i < n; i++) r[i] = next(); return r; }
                    static int[][] intss() { int n = Integer.parseInt(next().trim()); int[][] r = new int[n][]; for (int i = 0; i < n; i++) { String[] p = next().trim().split("\\\\s+"); int len = Integer.parseInt(p[0]); r[i] = new int[len]; for (int j = 0; j < len; j++) r[i][j] = Integer.parseInt(p[j + 1]); } return r; }
                    static String[][] strss() { int n = Integer.parseInt(next().trim()); String[][] r = new String[n][]; for (int i = 0; i < n; i++) r[i] = strs(); return r; }
                    static String q(String s) { StringBuilder b = new StringBuilder("\\""); for (char c : s.toCharArray()) { if (c == '"' || c == '\\\\') b.append('\\\\').append(c); else if (c < 0x20) b.append(String.format("\\\\u%%04x", (int) c)); else b.append(c); } return b.append('"').toString(); }
                    static String json(Object o) {
                        if (o == null) return "null";
                        if (o instanceof String s) return q(s);
                        if (o instanceof int[] a) { StringJoiner j = new StringJoiner(",", "[", "]"); for (int x : a) j.add(Integer.toString(x)); return j.toString(); }
                        if (o instanceof long[] a) { StringJoiner j = new StringJoiner(",", "[", "]"); for (long x : a) j.add(Long.toString(x)); return j.toString(); }
                        if (o instanceof boolean[] a) { StringJoiner j = new StringJoiner(",", "[", "]"); for (boolean x : a) j.add(Boolean.toString(x)); return j.toString(); }
                        if (o instanceof Object[] a) { StringJoiner j = new StringJoiner(",", "[", "]"); for (Object x : a) j.add(json(x)); return j.toString(); }
                        return String.valueOf(o);
                    }
                    public static void main(String[] argv) throws IOException {
                        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
                        lines = new ArrayList<>(); String l; while ((l = in.readLine()) != null) lines.add(l);
                %s        System.out.println(json(new Solution().%s(%s)));
                    }
                }
                """.formatted(args, d.functionName(), call);
    }

    // ------------------------------------------------------------------ C++

    private static String cppType(String type) {
        return switch (type) {
            case "int" -> "int";
            case "long" -> "long long";
            case "double" -> "double";
            case "boolean" -> "bool";
            case "string" -> "string";
            case "int[]" -> "vector<int>";
            case "long[]" -> "vector<long long>";
            case "boolean[]" -> "vector<bool>";
            case "string[]" -> "vector<string>";
            case "int[][]" -> "vector<vector<int>>";
            case "string[][]" -> "vector<vector<string>>";
            default -> throw new IllegalArgumentException(type);
        };
    }

    private static String cppParam(String type) {
        String t = cppType(type);
        return t.startsWith("vector") || t.equals("string") ? t + "&" : t;
    }

    private static String cppDefault(String type) {
        return switch (type) {
            case "int", "long", "double" -> "0";
            case "boolean" -> "false";
            case "string" -> "\"\"";
            default -> "{}";
        };
    }

    private static String cppReader(String type) {
        return switch (type) {
            case "int" -> "stoi(nx())";
            case "long" -> "stoll(nx())";
            case "double" -> "stod(nx())";
            case "boolean" -> "trim(nx()) == \"true\"";
            case "string" -> "nx()";
            case "int[]" -> "rdi()";
            case "long[]" -> "rdl()";
            case "boolean[]" -> "rdb()";
            case "string[]" -> "rds()";
            case "int[][]" -> "rdii()";
            case "string[][]" -> "rdss()";
            default -> throw new IllegalArgumentException(type);
        };
    }

    private static String cppMain(DsaDetail d) {
        StringBuilder args = new StringBuilder();
        StringBuilder call = new StringBuilder();
        for (int i = 0; i < d.params().size(); i++) {
            Param p = d.params().get(i);
            args.append("    ").append(cppType(p.type())).append(" a").append(i).append(" = ")
                    .append(cppReader(p.type())).append(";\n");
            call.append(i == 0 ? "" : ", ").append("a").append(i);
        }
        return """
                static vector<string> L; static size_t P = 0;
                static string nx() { return P < L.size() ? L[P++] : (P++, string()); }
                static string trim(const string& s) { size_t a = s.find_first_not_of(" \\t\\r"), b = s.find_last_not_of(" \\t\\r"); return a == string::npos ? "" : s.substr(a, b - a + 1); }
                template <class T> static vector<T> rdv(function<T(const string&)> f) { int n = stoi(nx()); string l = nx(); vector<T> r; if (n > 0) { stringstream ss(l); string t; while (ss >> t) r.push_back(f(t)); } return r; }
                static vector<int> rdi() { return rdv<int>([](const string& t) { return stoi(t); }); }
                static vector<long long> rdl() { return rdv<long long>([](const string& t) { return stoll(t); }); }
                static vector<bool> rdb() { return rdv<bool>([](const string& t) { return t == "true"; }); }
                static vector<string> rds() { int n = stoi(nx()); vector<string> r; for (int i = 0; i < n; i++) r.push_back(nx()); return r; }
                static vector<vector<int>> rdii() { int n = stoi(nx()); vector<vector<int>> r; for (int i = 0; i < n; i++) { stringstream ss(nx()); int len; ss >> len; vector<int> row(len); for (int j = 0; j < len; j++) ss >> row[j]; r.push_back(row); } return r; }
                static vector<vector<string>> rdss() { int n = stoi(nx()); vector<vector<string>> r; for (int i = 0; i < n; i++) r.push_back(rds()); return r; }
                static string js(int v) { return to_string(v); }
                static string js(long long v) { return to_string(v); }
                static string js(double v) { ostringstream o; o << setprecision(15) << v; return o.str(); }
                static string js(bool v) { return v ? "true" : "false"; }
                static string js(const string& s) { string o = "\\""; for (char c : s) { if (c == '"' || c == '\\\\') { o += '\\\\'; o += c; } else if ((unsigned char) c < 0x20) { char b[8]; snprintf(b, sizeof b, "\\\\u%%04x", c); o += b; } else o += c; } return o + "\\""; }
                static string js(vector<bool>::const_reference v) { return v ? "true" : "false"; }
                template <class T> static string js(const vector<T>& v) { string o = "["; for (size_t i = 0; i < v.size(); i++) { if (i) o += ","; o += js(v[i]); } return o + "]"; }
                int main() {
                    ios::sync_with_stdio(false);
                    string line; while (getline(cin, line)) { if (!line.empty() && line.back() == '\\r') line.pop_back(); L.push_back(line); }
                %s    Solution s;
                    cout << js(s.%s(%s)) << endl;
                    return 0;
                }
                """.formatted(args, d.functionName(), call);
    }
}
