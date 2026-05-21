package com.example.ragbilibili.vectorstore.dashvector;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.converter.AbstractFilterExpressionConverter;

import java.util.List;

public class DashVectorFilterExpressionConverter extends AbstractFilterExpressionConverter {

    @Override
    protected void doExpression(Expression expression, StringBuilder context) {
        if (expression.type() == Filter.ExpressionType.IN) {
            handleIn(expression, context);
        }
        else if (expression.type() == Filter.ExpressionType.NIN) {
            handleNotIn(expression, context);
        }
        else {
            this.convertOperand(expression.left(), context);
            context.append(getOperationSymbol(expression));
            this.convertOperand(expression.right(), context);
        }
    }

    private void handleIn(Expression expression, StringBuilder context) {
        context.append("(");
        convertToConditions(expression, context);
        context.append(")");
    }

    @SuppressWarnings("unchecked")
    private void convertToConditions(Expression expression, StringBuilder context) {
        Filter.Value right = (Filter.Value) expression.right();
        Object value = right.value();
        if (!(value instanceof List)) {
            throw new IllegalArgumentException("Expected a List, but got: " + value.getClass().getSimpleName());
        }
        List<Object> values = (List<Object>) value;
        for (int index = 0; index < values.size(); index++) {
            this.convertOperand(expression.left(), context);
            context.append(" = ");
            this.doSingleValue(values.get(index), context);
            if (index < values.size() - 1) {
                context.append(" OR ");
            }
        }
    }

    private void handleNotIn(Expression expression, StringBuilder context) {
        context.append("NOT (");
        convertToConditions(expression, context);
        context.append(")");
    }

    private String getOperationSymbol(Expression expression) {
        return switch (expression.type()) {
            case AND -> " AND ";
            case OR -> " OR ";
            case EQ -> " = ";
            case NE -> " != ";
            case LT -> " < ";
            case LTE -> " <= ";
            case GT -> " > ";
            case GTE -> " >= ";
            default -> throw new RuntimeException("Not supported expression type: " + expression.type());
        };
    }

    @Override
    protected void doKey(Key key, StringBuilder context) {
        context.append(key.key());
    }

    @Override
    protected void doSingleValue(Object value, StringBuilder context) {
        if (value == null) {
            context.append("NULL");
        }
        else if (value instanceof String) {
            String escaped = ((String) value).replace("'", "''");
            context.append("'").append(escaped).append("'");
        }
        else if (value instanceof Number || value instanceof Boolean) {
            context.append(value);
        }
        else {
            String escaped = value.toString().replace("'", "''");
            context.append("'").append(escaped).append("'");
        }
    }

    @Override
    protected void doStartGroup(Group group, StringBuilder context) {
        context.append("(");
    }

    @Override
    protected void doEndGroup(Group group, StringBuilder context) {
        context.append(")");
    }
}
