package com.aicoach.execution.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Strategy for judging a program's JSON output against the expected value. Comparison happens in the API (not in
 * Judge0), so order-insensitive and floating-point answers are judged correctly.
 */
public interface OutputComparator {

    boolean matches(JsonNode expected, JsonNode actual);

    static OutputComparator forName(String name) {
        return switch (name) {
            case "UNORDERED_LIST" -> new UnorderedList();
            case "FLOAT_TOLERANCE" -> new FloatTolerance(1e-6);
            default -> new Exact();
        };
    }

    /** Structural equality; numbers compare by value (so 2 and 2.0 are equal). */
    final class Exact implements OutputComparator {
        @Override
        public boolean matches(JsonNode expected, JsonNode actual) {
            return equal(expected, actual, 0);
        }
    }

    /** Numbers within an absolute tolerance. */
    final class FloatTolerance implements OutputComparator {
        private final double tolerance;

        public FloatTolerance(double tolerance) {
            this.tolerance = tolerance;
        }

        @Override
        public boolean matches(JsonNode expected, JsonNode actual) {
            return equal(expected, actual, tolerance);
        }
    }

    /** Top-level array compared as a multiset (element order does not matter). */
    final class UnorderedList implements OutputComparator {
        @Override
        public boolean matches(JsonNode expected, JsonNode actual) {
            if (!expected.isArray() || !actual.isArray() || expected.size() != actual.size()) {
                return equal(expected, actual, 0);
            }
            List<JsonNode> remaining = new ArrayList<>();
            actual.forEach(remaining::add);
            for (JsonNode want : expected) {
                Iterator<JsonNode> it = remaining.iterator();
                boolean found = false;
                while (it.hasNext()) {
                    if (equal(want, it.next(), 0)) {
                        it.remove();
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
            return true;
        }
    }

    private static boolean equal(JsonNode a, JsonNode b, double tolerance) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.isNumber() && b.isNumber()) {
            return tolerance == 0 ? a.decimalValue().compareTo(b.decimalValue()) == 0
                    : Math.abs(a.doubleValue() - b.doubleValue()) <= tolerance;
        }
        if (a.isArray() && b.isArray()) {
            if (a.size() != b.size()) {
                return false;
            }
            for (int i = 0; i < a.size(); i++) {
                if (!equal(a.get(i), b.get(i), tolerance)) {
                    return false;
                }
            }
            return true;
        }
        return a.equals(b);
    }
}
