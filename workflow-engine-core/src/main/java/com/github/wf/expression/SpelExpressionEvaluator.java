package com.github.wf.expression;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class SpelExpressionEvaluator implements ExpressionEvaluator {

    private static final Pattern INTEGER_PATTERN = Pattern.compile("^-?\\d{1,18}$");
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("^-?\\d+\\.\\d+$");
    /** Cap on cached parsed expressions — expressions are few per definition; reset when exceeded. */
    private static final int MAX_CACHED_EXPRESSIONS = 2048;

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ConcurrentHashMap<String, Expression> expressionCache = new ConcurrentHashMap<>();

    @Override
    public Object evaluate(String expression, Map<String, Object> variables) {
        EvaluationContext context = new StandardEvaluationContext();
        Map<String, Object> normalized = normalizeVariables(variables);
        for (Map.Entry<String, Object> entry : normalized.entrySet()) {
            context.setVariable(entry.getKey(), entry.getValue());
        }
        String spelExpr = toSpel(expression, variables.keySet());
        // parseExpression is relatively expensive — cache the parsed form,
        // expressions are evaluated once per node entry on hot paths.
        Expression expr = expressionCache.computeIfAbsent(spelExpr, parser::parseExpression);
        if (expressionCache.size() > MAX_CACHED_EXPRESSIONS) expressionCache.clear();
        return expr.getValue(context);
    }

    /** Convert plain workflow conditions to SpEL: bare variable → #var, variable names outside string literals → #var. */
    String toSpel(String expression, Set<String> varNames) {
        if (expression.contains("#")) return expression;
        if (!expression.contains("'") && looksLikeVariable(expression)) return "#" + expression;
        return replaceVariablesOutsideQuotes(expression, varNames);
    }

    /**
     * Replace variable names with #var references, but only OUTSIDE single-quoted
     * string literals — a variable named "running" must not corrupt the literal
     * in {@code status == 'running'}.
     */
    private String replaceVariablesOutsideQuotes(String expression, Set<String> varNames) {
        if (varNames.isEmpty()) return expression;
        StringBuilder out = new StringBuilder(expression.length());
        StringBuilder token = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '\'') {
                if (!inQuote) appendWithVars(out, token, varNames);
                else out.append(token);
                token.setLength(0);
                inQuote = !inQuote;
                out.append(c);
            } else {
                token.append(c);
            }
        }
        if (inQuote) out.append(token); // unterminated literal — leave untouched
        else appendWithVars(out, token, varNames);
        return out.toString();
    }

    private void appendWithVars(StringBuilder out, StringBuilder token, Set<String> varNames) {
        String s = token.toString();
        for (String varName : varNames) {
            if (varName == null || varName.isEmpty()) continue;
            // Word-boundary lookarounds also prevent partial matches ("day" vs "days")
            s = s.replaceAll("(?<![\\w$])" + Pattern.quote(varName) + "(?![\\w$])",
                    java.util.regex.Matcher.quoteReplacement("#" + varName));
        }
        out.append(s);
    }

    /** Convert string values that represent numbers to actual numeric types,
     *  so SpEL comparisons like "amount &gt; 1000" work even when amount is "5000".
     *  Strings with leading zeros (order/case numbers like "0123") are preserved as strings. */
    static Map<String, Object> normalizeVariables(Map<String, Object> variables) {
        if (variables == null) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>(variables);
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            if (entry.getValue() instanceof String s) {
                if (INTEGER_PATTERN.matcher(s).matches() && !hasLeadingZero(s)) {
                    try { entry.setValue(Long.parseLong(s)); } catch (NumberFormatException ignored) {}
                } else if (DECIMAL_PATTERN.matcher(s).matches()) {
                    try { entry.setValue(new BigDecimal(s)); } catch (NumberFormatException ignored) {}
                }
            }
        }
        return result;
    }

    private static boolean hasLeadingZero(String s) {
        String digits = s.startsWith("-") ? s.substring(1) : s;
        return digits.length() > 1 && digits.charAt(0) == '0';
    }

    private boolean looksLikeVariable(String expr) {
        return expr.matches("^[a-zA-Z_][a-zA-Z0-9_.]*$");
    }
}
