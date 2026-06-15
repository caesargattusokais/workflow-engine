package com.github.wf.expression;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class SpelExpressionEvaluatorTest {

    private ExpressionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new SpelExpressionEvaluator();
    }

    @Test
    void evaluatesSimpleComparison() {
        boolean result = evaluator.evaluateToBoolean("days > 3", Map.of("days", 5));
        assertThat(result).isTrue();
        boolean result2 = evaluator.evaluateToBoolean("days > 3", Map.of("days", 1));
        assertThat(result2).isFalse();
    }

    @Test
    void evaluatesEquality() {
        boolean result = evaluator.evaluateToBoolean("status == 'approved'",
                Map.of("status", "approved"));
        assertThat(result).isTrue();
    }

    @Test
    void evaluatesVariableReference() {
        Object result = evaluator.evaluate("applicant", Map.of("applicant", "张三"));
        assertThat(result).isEqualTo("张三");
    }

    @Test
    void evaluatesCompoundExpression() {
        boolean result = evaluator.evaluateToBoolean(
                "amount >= 5000 and type == 'emergency'",
                Map.of("amount", 6000, "type", "emergency"));
        assertThat(result).isTrue();
    }

    @Test
    void handlesNullVariable() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("approver", null);
        boolean result = evaluator.evaluateToBoolean("approver == null", vars);
        assertThat(result).isTrue();
    }
}
