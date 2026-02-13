package edu.uci.ics.texera.sqlservice;

import org.apache.calcite.sql.*;
import org.apache.calcite.sql.util.SqlShuttle;

import java.util.HashMap;
import java.util.Map;

/**
 * Safely expands SELECT list aliases for Calcite 1.38.
 * Supports rewriting SELECT list, WHERE, HAVING, GROUP BY, ORDER BY.
 * Avoids in-place mutation of immutable SqlCall nodes.
 */
public class AliasExpander {

    public static SqlNode expand(SqlNode node) {
        if (node instanceof SqlSelect) {
            expandSelect((SqlSelect) node);
        } else {
            node.accept(new SqlShuttle() {
                @Override
                public SqlNode visit(SqlCall call) {
                    SqlNode[] newOperands = call.getOperandList().stream()
                            .map(op -> op == null ? null : expand(op))
                            .toArray(SqlNode[]::new);
                    return new SqlBasicCall(call.getOperator(), newOperands, call.getParserPosition());
                }
            });
        }
        return node;
    }

    private static void expandSelect(SqlSelect select) {
        // 1. Collect SELECT list aliases
        Map<String, SqlNode> aliasMap = new HashMap<>();
        if (select.getSelectList() != null) {
            for (SqlNode item : select.getSelectList()) {
                if (item instanceof SqlBasicCall) {
                    SqlBasicCall call = (SqlBasicCall) item;
                    if (call.getOperator().getKind() == SqlKind.AS) {
                        SqlNode expr = call.operand(0);
                        SqlNode aliasNode = call.operand(1);
                        if (aliasNode instanceof SqlIdentifier) {
                            String alias = ((SqlIdentifier) aliasNode).getSimple().toUpperCase();
                            aliasMap.put(alias, expr);
                        }
                    }
                }
            }
        }

        // 2. Visitor to replace alias references safely
        SqlShuttle rewriter = new SqlShuttle() {
            @Override
            public SqlNode visit(SqlIdentifier id) {
                String name = id.getSimple().toUpperCase();
                if (aliasMap.containsKey(name)) {
                    return aliasMap.get(name).clone(id.getParserPosition());
                }
                return id;
            }

            @Override
            public SqlNode visit(SqlCall call) {
                if (call instanceof SqlSelect) {
                    expandSelect((SqlSelect) call);
                    return call;
                }

                // Keep AS alias intact
                if (call.getOperator().getKind() == SqlKind.AS) {
                    SqlNode newLeft = call.operand(0).accept(this);
                    return new SqlBasicCall(call.getOperator(), new SqlNode[]{newLeft, call.operand(1)}, call.getParserPosition());
                }

                // Rebuild safely instead of in-place mutation
                SqlNode[] newOperands = call.getOperandList().stream()
                        .map(op -> op == null ? null : op.accept(this))
                        .toArray(SqlNode[]::new);
                return new SqlBasicCall(call.getOperator(), newOperands, call.getParserPosition());
            }
        };

        // 3. Rewrite SELECT list
        if (select.getSelectList() != null) {
            SqlNodeList newSelectList = new SqlNodeList(select.getSelectList().getParserPosition());
            for (SqlNode item : select.getSelectList()) {
                newSelectList.add(item.accept(rewriter));
            }
            select.setSelectList(newSelectList);
        }

        // 4. Rewrite WHERE
        if (select.getWhere() != null)
            select.setWhere(select.getWhere().accept(rewriter));

        // 5. Rewrite HAVING
        if (select.getHaving() != null)
            select.setHaving(select.getHaving().accept(rewriter));

        // 6. Rewrite GROUP BY
        if (select.getGroup() != null) {
            SqlNodeList newGroup = new SqlNodeList(select.getGroup().getParserPosition());
            for (SqlNode groupExpr : select.getGroup()) {
                newGroup.add(groupExpr.accept(rewriter));
            }
            select.setGroupBy(newGroup);
        }

        // 7. Rewrite ORDER BY
        if (select.getOrderList() != null) {
            SqlNodeList newOrder = new SqlNodeList(select.getOrderList().getParserPosition());
            for (SqlNode orderExpr : select.getOrderList()) {
                newOrder.add(orderExpr.accept(rewriter));
            }
            select.setOrderBy(newOrder);
        }

        // 8. Recurse into FROM if it's a subquery
        if (select.getFrom() instanceof SqlSelect) {
            expandSelect((SqlSelect) select.getFrom());
        }
    }
}
