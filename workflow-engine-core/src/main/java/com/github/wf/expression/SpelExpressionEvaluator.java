package com.github.wf.expression;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpelExpressionEvaluator implements ExpressionEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();

    @Override
    public Object evaluate(String expression, Map<String, Object> variables) {
        EvaluationContext context = new StandardEvaluationContext();
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            context.setVariable(entry.getKey(), entry.getValue());
        }
        String spelExpr = expression;
        if (!expression.contains("#")) {
            if (!expression.contains("'") && looksLikeVariable(expression)) {
                spelExpr = "#" + expression;
            } else {
                for (String varName : variables.keySet()) {
                    if (varName.contains(".")) {
                        // Dotted names must use bracket notation: #this['svc.result']
                        spelExpr = spelExpr.replaceAll("\\b" + Pattern.quote(varName) + "\\b",
                                Matcher.quoteReplacement("#this['" + varName + "']"));
                    } else {
                        spelExpr = spelExpr.replaceAll("\\b" + Pattern.quote(varName) + "\\b",
                                Matcher.quoteReplacement("#" + varName));
                    }
                }
            }
        }
        return parser.parseExpression(spelExpr).getValue(context);
    }

    private boolean looksLikeVariable(String expr) {
        return expr.matches("^[a-zA-Z_][a-zA-Z0-9_.]*$");
    }
}
