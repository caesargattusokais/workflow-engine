package com.github.wf.expression;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpelExpressionEvaluator implements ExpressionEvaluator {

    private static final Pattern INTEGER_PATTERN = Pattern.compile("^-?\\d{1,18}$");
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("^-?\\d+\\.\\d+$");

    private final ExpressionParser parser = new SpelExpressionParser();

    @Override
    public Object evaluate(String expression, Map<String, Object> variables) {
        EvaluationContext context = new StandardEvaluationContext();
        Map<String, Object> normalized = normalizeVariables(variables);
        for (Map.Entry<String, Object> entry : normalized.entrySet()) {
            context.setVariable(entry.getKey(), entry.getValue());
        }
        String spelExpr = expression;
        if (!expression.contains("#")) {
            if (!expression.contains("'") && looksLikeVariable(expression)) {
                spelExpr = "#" + expression;
            } else {
                for (String varName : variables.keySet()) {
                    spelExpr = spelExpr.replaceAll("\\b" + Pattern.quote(varName) + "\\b",
                            Matcher.quoteReplacement("#" + varName));
                }
            }
        }
        return parser.parseExpression(spelExpr).getValue(context);
    }

    /** Convert string values that represent numbers to actual numeric types,
     *  so SpEL comparisons like "amount &gt; 1000" work even when amount is "5000". */
    static Map<String, Object> normalizeVariables(Map<String, Object> variables) {
        if (variables == null) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>(variables);
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            if (entry.getValue() instanceof String s) {
                if (INTEGER_PATTERN.matcher(s).matches()) {
                    try { entry.setValue(Long.parseLong(s)); } catch (NumberFormatException ignored) {}
                } else if (DECIMAL_PATTERN.matcher(s).matches()) {
                    try { entry.setValue(new BigDecimal(s)); } catch (NumberFormatException ignored) {}
                }
            }
        }
        return result;
    }

    private boolean looksLikeVariable(String expr) {
        return expr.matches("^[a-zA-Z_][a-zA-Z0-9_.]*$");
    }
}
