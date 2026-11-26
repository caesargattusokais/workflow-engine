package com.github.wf.expression;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void coercesNumericStringToNumber() {
        // "5" as string → should become Long 5 before SpEL compares
        boolean result = evaluator.evaluateToBoolean("amount > 3", Map.of("amount", "5"));
        assertThat(result).isTrue();
    }

    @Test
    void coercesDecimalString() {
        boolean result = evaluator.evaluateToBoolean("price >= 99.99", Map.of("price", "100.50"));
        assertThat(result).isTrue();
    }

    @Test
    void preservesNonNumericString() {
        boolean result = evaluator.evaluateToBoolean("status == 'approved'", Map.of("status", "approved"));
        assertThat(result).isTrue();
    }

    @Test
    void coercesNegativeIntegerString() {
        boolean result = evaluator.evaluateToBoolean("val < 0", Map.of("val", "-10"));
        assertThat(result).isTrue();
    }

    @Test
    void doesNotCorruptStringLiteralMatchingVariableName() {
        // A variable named "running" must not corrupt the literal in status == 'running'
        boolean result = evaluator.evaluateToBoolean(
                "status == 'running'", Map.of("status", "running", "running", false));
        assertThat(result).isTrue();
    }

    @Test
    void replacesVariableAdjacentToLiteral() {
        boolean result = evaluator.evaluateToBoolean(
                "status == 'x' and running", Map.of("status", "x", "running", true));
        assertThat(result).isTrue();
    }

    @Test
    void noPartialVariableMatch() {
        // Variable "day" must not replace the "days" prefix
        boolean result = evaluator.evaluateToBoolean(
                "days > 3", Map.of("day", 999, "days", 5));
        assertThat(result).isTrue();
    }

    @Test
    void preservesLeadingZeroStrings() {
        // "0123" looks numeric but is an order/case number — must stay a string
        Map<String, Object> normalized = SpelExpressionEvaluator.normalizeVariables(Map.of("code", "0123"));
        assertThat(normalized.get("code")).isEqualTo("0123");
    }

    @Test
    void allowsHarmlessStringMethodCalls() {
        // Real templates use exception.message.contains('timeout')
        assertThat(evaluator.evaluateToBoolean(
                "exception.message.contains('timeout')",
                Map.of("exception", Map.of("message", "read timeout after 3s")))).isTrue();
        assertThat(evaluator.evaluateToBoolean(
                "message.startsWith('HTTP 5')", Map.of("message", "HTTP 503"))).isTrue();
    }

    @Test
    void rejectsTypeReferenceRce() {
        // Classic SpEL RCE payload — must never reach Runtime.exec
        String payload = "T(java.lang.Runtime).getRuntime().exec('calc')";
        assertThatThrownBy(() -> evaluator.evaluate(payload, Map.of()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsConstructorAndReflection() {
        assertThatThrownBy(() -> evaluator.evaluate("new java.lang.ProcessBuilder('calc')", Map.of()))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> evaluator.evaluate(
                "message.getClass().getClassLoader()", Map.of("message", "x")))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsNonWhitelistedMethod() {
        // wait()/getClass() are not in the allow-list
        assertThatThrownBy(() -> evaluator.evaluate("message.wait(1000)", Map.of("message", "x")))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void cachedExpressionsStayConsistent() {
        // Same expression evaluated repeatedly with different variables (cache path)
        assertThat(evaluator.evaluateToBoolean("days > 3", Map.of("days", 5))).isTrue();
        assertThat(evaluator.evaluateToBoolean("days > 3", Map.of("days", 1))).isFalse();
        assertThat(evaluator.evaluateToBoolean("days > 3", Map.of("days", 10))).isTrue();
    }
}
