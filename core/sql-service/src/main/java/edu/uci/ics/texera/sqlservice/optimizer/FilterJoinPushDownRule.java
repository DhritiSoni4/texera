package edu.uci.ics.texera.sqlservice.optimizer;

import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Filter;
import org.apache.calcite.rel.core.Join;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexShuttle;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Filter pushdown rule across joins for modern Calcite versions.
 */
public class FilterJoinPushDownRule extends RelOptRule {

    public static final FilterJoinPushDownRule INSTANCE = new FilterJoinPushDownRule();

    private FilterJoinPushDownRule() {
        super(
                operand(Filter.class,
                        operand(Join.class, any())),
                "FilterJoinPushDownRule"
        );
    }

    @Override
    public void onMatch(RelOptRuleCall call) {
        Filter filter = call.rel(0);
        Join join = call.rel(1);

        RexBuilder rexBuilder = filter.getCluster().getRexBuilder();

        // Flatten AND predicates manually
        List<RexNode> conjuncts = flattenAnd(filter.getCondition());

        List<RexNode> leftFilters = new ArrayList<>();
        List<RexNode> rightFilters = new ArrayList<>();
        List<RexNode> remaining = new ArrayList<>();

        int leftFieldCount = join.getLeft().getRowType().getFieldCount();
        int rightFieldCount = join.getRight().getRowType().getFieldCount();

        for (RexNode c : conjuncts) {
            Set<Integer> refs = new HashSet<>();
            c.accept(new org.apache.calcite.rex.RexVisitorImpl<Void>(true) {
                @Override
                public Void visitInputRef(RexInputRef ref) {
                    refs.add(ref.getIndex());
                    return null;
                }
            });

            boolean usesLeft = refs.stream().anyMatch(idx -> idx < leftFieldCount);
            boolean usesRight = refs.stream().anyMatch(idx -> idx >= leftFieldCount);

            if (usesLeft && !usesRight) {
                leftFilters.add(c);
            } else if (usesRight && !usesLeft) {
                // shift indices for right table
                rightFilters.add(shiftRightRefs(c, -leftFieldCount));
            } else {
                remaining.add(c);
            }
        }

        RelBuilder builder = call.builder();

        // Push filters into left and right
        RelNode left = join.getLeft();
        if (!leftFilters.isEmpty()) {
            left = builder.push(left).filter(composeAnd(rexBuilder, leftFilters)).build();
        }

        RelNode right = join.getRight();
        if (!rightFilters.isEmpty()) {
            right = builder.push(right).filter(composeAnd(rexBuilder, rightFilters)).build();
        }

        // Rebuild join (condition first!)
        Join newJoin = join.copy(
                join.getTraitSet(),
                join.getCondition(), // condition goes first
                left,
                right,
                join.getJoinType(),
                join.isSemiJoinDone()
        );

        if (!remaining.isEmpty()) {
            // Apply remaining filter above join
            RexNode newCond = composeAnd(rexBuilder, remaining);
            call.transformTo(builder.push(newJoin).filter(newCond).build());
        } else {
            call.transformTo(newJoin);
        }
    }

    /** Recursively flatten AND into a list of RexNodes */
    private List<RexNode> flattenAnd(RexNode condition) {
        List<RexNode> list = new ArrayList<>();
        if (condition == null) return list;

        if (condition instanceof RexCall && ((RexCall) condition).getOperator() == SqlStdOperatorTable.AND) {
            for (RexNode op : ((RexCall) condition).getOperands()) {
                list.addAll(flattenAnd(op));
            }
        } else {
            list.add(condition);
        }
        return list;
    }

    /** Compose a list of RexNodes into a single AND */
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
