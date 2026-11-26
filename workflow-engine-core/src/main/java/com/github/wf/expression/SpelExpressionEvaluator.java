package com.github.wf.expression;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.MethodResolver;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.ReflectiveMethodResolver;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import java.lang.reflect.Method;
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

    /**
     * Workflow conditions need a handful of harmless String/Collection methods
     * (templates use {@code exception.message.contains('timeout')}), but must
     * never reach {@code T(...)} type references, constructors or reflection.
     * {@link SimpleEvaluationContext} blocks type refs/constructors by design;
     * this resolver further limits instance methods to the allow-list below.
     */
    private static final Set<String> ALLOWED_METHODS = Set.of(
            "contains", "startsWith", "endsWith", "equals", "equalsIgnoreCase",
            "matches", "toLowerCase", "toUpperCase", "trim", "toString",
            "size", "isEmpty", "containsKey", "containsValue", "keySet", "values",
            "get", "intValue", "longValue", "doubleValue", "compareTo"
    );

    /** Reject obviously dangerous syntax before parsing, even inside literals. */
    private static final Pattern BLOCKED_PATTERN = Pattern.compile(
            "(^|[^\\w])(T\\s*\\(|new\\s+[A-Z]|Class\\.forName|getClass\\s*\\(|getClassLoader\\s*\\()");

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ConcurrentHashMap<String, Expression> expressionCache = new ConcurrentHashMap<>();
    private final MethodResolver safeMethodResolver = new SafeMethodResolver();

    @Override
    public Object evaluate(String expression, Map<String, Object> variables) {
        String spelExpr = toSpel(expression, variables.keySet());
        if (BLOCKED_PATTERN.matcher(spelExpr).find()) {
            throw new IllegalArgumentException("Unsafe SpEL expression rejected: " + expression);
        }
        EvaluationContext context = buildContext(variables);
        // parseExpression is relatively expensive — cache the parsed form,
        // expressions are evaluated once per node entry on hot paths.
        Expression expr = expressionCache.computeIfAbsent(spelExpr, parser::parseExpression);
        if (expressionCache.size() > MAX_CACHED_EXPRESSIONS) expressionCache.clear();
        return expr.getValue(context);
    }

    private EvaluationContext buildContext(Map<String, Object> variables) {
        SimpleEvaluationContext context = SimpleEvaluationContext
                .forPropertyAccessors(
                        org.springframework.expression.spel.support.DataBindingPropertyAccessor.forReadOnlyAccess(),
                        new ReadOnlyMapAccessor())
                .withMethodResolvers(safeMethodResolver)
                .withAssignmentDisabled()
                .build();
        Map<String, Object> normalized = normalizeVariables(variables);
        for (Map.Entry<String, Object> entry : normalized.entrySet()) {
            context.setVariable(entry.getKey(), entry.getValue());
        }
        return context;
    }

    /**
     * Read-only accessor for map-valued variables (exception, probe_result, …)
     * so conditions can use {@code exception.message} / {@code probe_result['x']}.
     * Spring 6.2 removed its built-in MapAccessor, so supply a minimal one that
     * never writes and never resolves JavaBean properties.
     */
    private static final class ReadOnlyMapAccessor implements org.springframework.expression.PropertyAccessor {
        @Override
        public boolean canRead(EvaluationContext context, Object target, String name) {
            return target instanceof Map<?, ?> map && map.containsKey(name);
        }

        @Override
        public org.springframework.expression.TypedValue read(EvaluationContext context, Object target, String name) {
            Object value = target instanceof Map<?, ?> map ? map.get(name) : null;
            return new org.springframework.expression.TypedValue(value);
        }

        @Override
        public boolean canWrite(EvaluationContext context, Object target, String name) { return false; }

        @Override
        public void write(EvaluationContext context, Object target, String name, Object value) {
            throw new UnsupportedOperationException("Assignment is disabled in workflow conditions");
        }

        @Override
        public Class<?>[] getSpecificTargetClasses() { return new Class<?>[] { Map.class }; }
    }

    /**
     * Only allow methods on the whitelist. Everything else (getClass, wait,
     * Runtime.exec via reflection, etc.) is invisible to expression resolution.
     */
    private static final class SafeMethodResolver implements MethodResolver {
        private final ReflectiveMethodResolver delegate = new ReflectiveMethodResolver();

        @Override
        public org.springframework.expression.MethodExecutor resolve(
                EvaluationContext context, Object target, String name,
                java.util.List<org.springframework.core.convert.TypeDescriptor> argumentTypes) {
            if (!ALLOWED_METHODS.contains(name)) return null;
            try {
                org.springframework.expression.MethodExecutor executor =
                        delegate.resolve(context, target, name, argumentTypes);
                if (executor == null) return null;
                // ReflectiveMethodExecutor exposes the resolved Method; verify it.
                if (executor instanceof org.springframework.expression.spel.support.ReflectiveMethodExecutor rme) {
                    Method m = rme.getMethod();
                    if (!ALLOWED_METHODS.contains(m.getName()) || !isSafeDeclaringClass(m.getDeclaringClass())) {
                        return null;
                    }
                }
                return executor;
            } catch (Exception e) {
                return null;
            }
        }

        private boolean isSafeDeclaringClass(Class<?> c) {
            if (c == null) return false;
            // Block reflective/Core Java escape hatches even if a whitelisted
            // name happens to exist there (e.g. Class.getMethods has "get"?).
            String n = c.getName();
            return !n.startsWith("java.lang.reflect.")
                    && !n.startsWith("java.lang.invoke.")
                    && c != Class.class
                    && c != Runtime.class
                    && c != System.class
                    && c != Process.class
                    && c != ProcessBuilder.class;
        }
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
