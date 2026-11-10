package com.aicoach.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HalfStepTest {

    private final HalfStep.Validator validator = new HalfStep.Validator();

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.0", "0.5", "1", "2.50", "10", "29.5", "30"})
    void acceptsMultiplesOfHalf(String value) {
        assertThat(validator.isValid(new BigDecimal(value), null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.1", "1.25", "2.3", "7.75"})
    void rejectsOtherFractions(String value) {
        assertThat(validator.isValid(new BigDecimal(value), null)).isFalse();
    }

    @org.junit.jupiter.api.Test
    void nullIsLeftToNotNull() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
