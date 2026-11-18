package com.github.wf.engine;

import com.github.wf.model.Condition;
import com.github.wf.model.Transition;

import java.util.List;
import java.util.function.Predicate;

/**
 * Picks the outgoing transition to follow from a node. Single definition of
 * the direct → conditional → default selection that used to be duplicated in
 * the exclusive gateway and in task completion handling.
 *
 * <p>Two orderings exist and both are preserved:</p>
 * <ul>
 *   <li><b>Edge-order</b> (exclusive gateway semantics): edges are inspected
 *   in listed order; the first direct edge or matching conditional edge wins,
 *   default is only a fallback.</li>
 *   <li><b>Direct-preferred</b> (task completion semantics): a direct edge
 *   always wins over conditionals regardless of position.</li>
 * </ul>
 */
public final class TransitionSelector {

    private TransitionSelector() {}

    /**
     * @param conditionMatch evaluator for conditional edges (may be null-safe —
     *                       it is only invoked with non-null conditions)
     * @param preferDirect   true = direct-preferred ordering, false = edge-order
     * @return the selected transition, the default edge as fallback, or
     *         {@code null} when nothing matched (caller decides whether that
     *         is an error)
     */
    public static Transition select(List<Transition> outgoing,
                                    Predicate<Condition> conditionMatch,
                                    boolean preferDirect) {
        if (preferDirect) {
            for (Transition t : outgoing) {
                if (t.isDirect()) return t;
            }
        }
        Transition defaultTrans = null;
        for (Transition t : outgoing) {
            if (t.isConditional()) {
                if (conditionMatch.test(t.getCondition())) return t;
            } else if (t.isDefault()) {
                defaultTrans = t;
            } else if (t.isDirect() && !preferDirect) {
                return t;
            }
        }
        return defaultTrans;
    }
}
