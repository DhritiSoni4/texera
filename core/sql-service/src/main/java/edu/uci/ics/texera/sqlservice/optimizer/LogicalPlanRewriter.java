package edu.uci.ics.texera.sqlservice.optimizer;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rex.*;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;

import java.util.*;

/**
 * Logical plan rewriter: pushes filters past projects and joins
 * compatible with modern Calcite versions (>= 1.36).
 */
public class LogicalPlanRewriter {

    public RelNode rewrite(RelNode root) {
        return rewriteNode(root);
    }

    private RelNode rewriteNode(RelNode node) {

        // First rewrite THIS node (top-down)
        if (node instanceof LogicalFilter) {
            LogicalFilter filter = (LogicalFilter) node;

            RelNode rewritten = pushFilterPastJoin(filter);
            if (rewritten != filter) {
                return rewriteNode(rewritten);
            }

            // 🚫 DO NOT push past Project (unsafe)
            return filter;
        }

        // Then rewrite children (bottom-up)
        List<RelNode> newInputs = new ArrayList<>();
        for (RelNode input : node.getInputs()) {
            newInputs.add(rewriteNode(input));
        }

        if (!newInputs.isEmpty()) {
            return node.copy(node.getTraitSet(), newInputs);
        }

        return node;
    }


    /**
     * Push filter past project by rewriting input refs
     */


    /**
     * Push filter past join: splits AND predicates into left, right, or join
     */
    private RelNode pushFilterPastJoin(LogicalFilter filter) {
        RelNode input = filter.getInput();
        if (!(input instanceof LogicalJoin)) {
            return filter;
        }

        LogicalJoin join = (LogicalJoin) input;
        RexBuilder rexBuilder = filter.getCluster().getRexBuilder();

        int leftFieldCount = join.getLeft().getRowType().getFieldCount();

        List<RexNode> conjuncts = flattenAnd(filter.getCondition());

        List<RexNode> leftFilters = new ArrayList<>();
        List<RexNode> rightFilters = new ArrayList<>();
        List<RexNode> remaining = new ArrayList<>();

        for (RexNode c : conjuncts) {
            Set<Integer> refs = new HashSet<>();

            c.accept(new RexVisitorImpl<Void>(true) {
                @Override
                public Void visitInputRef(RexInputRef inputRef) {
                    refs.add(inputRef.getIndex());
                    return null;
                }
            });

            boolean usesLeft = refs.stream().anyMatch(i -> i < leftFieldCount);
            boolean usesRight = refs.stream().anyMatch(i -> i >= leftFieldCount);

            // 🚨 SAFETY RULE:
            // Do NOT push predicates that overlap JOIN condition columns
            if (usesLeft && !usesRight && !overlapsJoinCondition(c, join)) {
                leftFilters.add(c);
            } else if (usesRight && !usesLeft && !overlapsJoinCondition(c, join)) {
                rightFilters.add(shiftRightRefs(c, -leftFieldCount));
            } else {
                remaining.add(c);
            }
        }

        RelNode left = join.getLeft();
        if (!leftFilters.isEmpty()) {
            left = LogicalFilter.create(left, composeAnd(rexBuilder, leftFilters));
        }

        RelNode right = join.getRight();
        if (!rightFilters.isEmpty()) {
            right = LogicalFilter.create(right, composeAnd(rexBuilder, rightFilters));
        }

        LogicalJoin newJoin = join.copy(
                join.getTraitSet(),
                join.getCondition(),
                left,
                right,
                join.getJoinType(),
                join.isSemiJoinDone()
        );

        if (remaining.isEmpty()) {
            return newJoin;
        }

        return LogicalFilter.create(newJoin, composeAnd(rexBuilder, remaining));
    }

    private boolean overlapsJoinCondition(RexNode predicate, LogicalJoin join) {
        Set<Integer> joinRefs = new HashSet<>();
        join.getCondition().accept(new RexVisitorImpl<Void>(true) {
            @Override
            public Void visitInputRef(RexInputRef ref) {
                joinRefs.add(ref.getIndex());
                return null;
            }
        });

        Set<Integer> predRefs = new HashSet<>();
        predicate.accept(new RexVisitorImpl<Void>(true) {
            @Override
            public Void visitInputRef(RexInputRef ref) {
                predRefs.add(ref.getIndex());
                return null;
            }
        });

        for (Integer i : predRefs) {
            if (joinRefs.contains(i)) return true;
        }
        return false;
    }


    /** Flatten AND predicates recursively */
    private List<RexNode> flattenAnd(RexNode condition) {
        List<RexNode> result = new ArrayList<>();
        if (condition == null) return result;
        if (condition instanceof RexCall && ((RexCall) condition).getOperator() == SqlStdOperatorTable.AND) {
            for (RexNode op : ((RexCall) condition).getOperands()) {
                result.addAll(flattenAnd(op));
            }
        } else {
            result.add(condition);
        }
        return result;
    }

    /** Compose multiple RexNodes into a single AND */
    private RexNode composeAnd(RexBuilder rexBuilder, List<RexNode> nodes) {
        if (nodes.isEmpty()) return null;
        RexNode result = nodes.get(0);
        for (int i = 1; i < nodes.size(); i++) {
            result = rexBuilder.makeCall(SqlStdOperatorTable.AND, result, nodes.get(i));
        }
        return result;
    }

    /** Shift input refs by an offset (for right table pushdown) */
    private RexNode shiftRightRefs(RexNode node, int offset) {
        return node.accept(new RexShuttle() {
            @Override
            public RexNode visitInputRef(RexInputRef ref) {
                return new RexInputRef(ref.getIndex() + offset, ref.getType());
            }
        });
    }
}
