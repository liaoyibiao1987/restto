package com.restto.manager.support;

import com.restto.manager.common.BusinessException;
import com.restto.manager.support.WorkflowGraph.Decision;
import com.restto.manager.support.WorkflowGraph.Edge;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工作流 DAG 引擎纯逻辑测试（无 Spring / 无 DB）。
 */
class WorkflowGraphTest {

    /**
     * @param keys 节点键
     * @return 节点集合
     */
    private static Set<String> nodes(String... keys) {
        return new LinkedHashSet<>(Arrays.asList(keys));
    }

    /**
     * @param src 源
     * @param tgt 目标
     * @return always 边
     */
    private static Edge e(String src, String tgt) {
        return new Edge(src, tgt, WorkflowGraph.COND_ALWAYS);
    }

    /**
     * @param src 源
     * @param tgt 目标
     * @param c   条件
     * @return 指定条件边
     */
    private static Edge e(String src, String tgt, String c) {
        return new Edge(src, tgt, c);
    }

    // ===== validateAcyclic / entryKeys =====

    @Test
    void linearChainAcyclicSingleEntry() {
        WorkflowGraph g = WorkflowGraph.build(nodes("a", "b", "c"),
                Arrays.asList(e("a", "b", "on_success"), e("b", "c", "on_success")));
        g.validateAcyclic();
        assertEquals(Collections.singleton("a"), g.entryKeys());
    }

    @Test
    void diamondAcyclicSingleEntry() {
        WorkflowGraph g = WorkflowGraph.build(nodes("a", "b", "c", "d"),
                Arrays.asList(e("a", "b"), e("a", "c"), e("b", "d"), e("c", "d")));
        g.validateAcyclic();
        assertEquals(Collections.singleton("a"), g.entryKeys());
    }

    @Test
    void twoParallelEntries() {
        WorkflowGraph g = WorkflowGraph.build(nodes("a", "b"),
                Collections.<Edge>emptyList());
        Set<String> entries = g.entryKeys();
        assertTrue(entries.contains("a"));
        assertTrue(entries.contains("b"));
    }

    @Test
    void cycleRejected() {
        WorkflowGraph g = WorkflowGraph.build(nodes("a", "b"),
                Arrays.asList(e("a", "b"), e("b", "a")));
        assertThrows(BusinessException.class, g::validateAcyclic);
    }

    @Test
    void selfLoopRejected() {
        WorkflowGraph g = WorkflowGraph.build(nodes("a"), Collections.singletonList(e("a", "a")));
        assertThrows(BusinessException.class, g::validateAcyclic);
    }

    @Test
    void singleNodeNoEdgesAcyclic() {
        WorkflowGraph g = WorkflowGraph.build(nodes("a"), Collections.<Edge>emptyList());
        g.validateAcyclic();
        assertEquals(Collections.singleton("a"), g.entryKeys());
    }

    @Test
    void emptyGraphRejectedByWellFormed() {
        WorkflowGraph g = WorkflowGraph.build(Collections.<String>emptySet(), Collections.<Edge>emptyList());
        assertThrows(BusinessException.class, g::validateWellFormed);
    }

    @Test
    void edgeToUnknownNodeRejected() {
        WorkflowGraph g = WorkflowGraph.build(nodes("a"), Collections.singletonList(e("a", "ghost")));
        assertThrows(BusinessException.class, g::validateWellFormed);
    }

    @Test
    void duplicateEdgesDeduped() {
        // 两条完全相同的边应合并，不影响拓扑（inDegree 计 1）
        WorkflowGraph g = WorkflowGraph.build(nodes("a", "b"),
                Arrays.asList(e("a", "b", "on_success"), e("a", "b", "on_success")));
        g.validateAcyclic();
        assertEquals(1, g.incomingOf("b").size());
    }

    // ===== isSatisfied 表驱动 =====

    @Test
    void isSatisfiedMatrix() {
        assertTrue(WorkflowGraph.isSatisfied("on_success", NodeState.SUCCESS));
        assertFalse(WorkflowGraph.isSatisfied("on_success", NodeState.FAILED));
        assertTrue(WorkflowGraph.isSatisfied("on_failed", NodeState.FAILED));
        assertFalse(WorkflowGraph.isSatisfied("on_failed", NodeState.SUCCESS));
        assertTrue(WorkflowGraph.isSatisfied("always", NodeState.SUCCESS));
        assertTrue(WorkflowGraph.isSatisfied("always", NodeState.FAILED));
        assertFalse(WorkflowGraph.isSatisfied("on_success", NodeState.SKIPPED));
        assertFalse(WorkflowGraph.isSatisfied("always", NodeState.SKIPPED));
        // null 条件按 always
        assertTrue(WorkflowGraph.isSatisfied(null, NodeState.SUCCESS));
        assertFalse(WorkflowGraph.isSatisfied(null, NodeState.SKIPPED));
    }

    // ===== decide / nextReady =====

    @Test
    void entryNodeReadyByDefault() {
        WorkflowGraph g = WorkflowGraph.build(nodes("a"), Collections.<Edge>emptyList());
        assertEquals(Decision.READY, g.decide("a", g.initialStates()));
    }

    @Test
    void waitingSourceKeepsNodeWaiting() {
        WorkflowGraph g = WorkflowGraph.build(nodes("a", "b"), Collections.singletonList(e("a", "b")));
        // a 未终态 → b 的边 OPEN
        assertEquals(Decision.WAIT, g.decide("b", g.initialStates()));
    }

    @Test
    void sourceSuccessMakesTargetReady() {
        WorkflowGraph g = WorkflowGraph.build(nodes("a", "b"),
                Collections.singletonList(e("a", "b", "on_success")));
        Map<String, NodeState> st = g.initialStates();
        st.put("a", NodeState.SUCCESS);
        assertEquals(Decision.READY, g.decide("b", st));
    }

    @Test
    void sourceFailedOnSuccessMakesTargetSkip() {
        WorkflowGraph g = WorkflowGraph.build(nodes("a", "b"),
                Collections.singletonList(e("a", "b", "on_success")));
        Map<String, NodeState> st = g.initialStates();
        st.put("a", NodeState.FAILED);
        assertEquals(Decision.SKIP, g.decide("b", st));
        assertTrue(g.deadNodes(st).contains("b"));
    }

    @Test
    void diamondBothSuccessTargetReady() {
        WorkflowGraph g = WorkflowGraph.build(nodes("a", "b", "c", "d"),
                Arrays.asList(e("a", "b"), e("a", "c"), e("b", "d"), e("c", "d")));
        Map<String, NodeState> st = g.initialStates();
        st.put("a", NodeState.SUCCESS);
        st.put("b", NodeState.SUCCESS);
        st.put("c", NodeState.SUCCESS);
        assertEquals(Decision.READY, g.decide("d", st));
    }

    @Test
    void diamondOneBranchFailedAnyPathStillReady() {
        // b 失败、c 成功（均 always）→ d 仍就绪（任一路径成功）
        WorkflowGraph g = WorkflowGraph.build(nodes("a", "b", "c", "d"),
                Arrays.asList(e("a", "b"), e("a", "c"), e("b", "d"), e("c", "d")));
        Map<String, NodeState> st = g.initialStates();
        st.put("a", NodeState.SUCCESS);
        st.put("b", NodeState.FAILED);
        st.put("c", NodeState.SUCCESS);
        assertEquals(Decision.READY, g.decide("d", st));
    }

    @Test
    void diamondOneBranchOpenKeepsWaiting() {
        WorkflowGraph g = WorkflowGraph.build(nodes("a", "b", "c", "d"),
                Arrays.asList(e("a", "b"), e("a", "c"), e("b", "d"), e("c", "d")));
        Map<String, NodeState> st = g.initialStates();
        st.put("a", NodeState.SUCCESS);
        st.put("b", NodeState.SUCCESS);
        // c 仍 RUNNING → 一条边 OPEN
        st.put("c", NodeState.RUNNING);
        assertEquals(Decision.WAIT, g.decide("d", st));
    }

    @Test
    void conditionalSplitRoutesBranch() {
        // a→b on_success, a→c on_failed, a 成功 → b 就绪，c 跳过
        WorkflowGraph g = WorkflowGraph.build(nodes("a", "b", "c"),
                Arrays.asList(e("a", "b", "on_success"), e("a", "c", "on_failed")));
        Map<String, NodeState> st = g.initialStates();
        st.put("a", NodeState.SUCCESS);
        Set<String> ready = g.nextReady(st);
        assertTrue(ready.contains("b"));
        assertFalse(ready.contains("c"));
        assertTrue(g.deadNodes(st).contains("c"));
    }

    // ===== deadNodes 级联 =====

    @Test
    void loneUnsatisfiableEdgeSkips() {
        WorkflowGraph g = WorkflowGraph.build(nodes("a", "b"),
                Collections.singletonList(e("a", "b", "on_success")));
        Map<String, NodeState> st = g.initialStates();
        st.put("a", NodeState.FAILED);
        assertTrue(g.deadNodes(st).contains("b"));
    }

    @Test
    void passedAndMissedBothTerminalNotDead() {
        // d 有两条入边：b 成功(PASSED)、c 失败 on_success(MISSED)，均终态 → 不跳过，就绪
        WorkflowGraph g = WorkflowGraph.build(nodes("a", "b", "c", "d"),
                Arrays.asList(e("b", "d", "on_success"), e("c", "d", "on_success")));
        Map<String, NodeState> st = g.initialStates();
        st.put("b", NodeState.SUCCESS);
        st.put("c", NodeState.FAILED);
        assertFalse(g.deadNodes(st).contains("d"));
        assertTrue(g.nextReady(st).contains("d"));
    }

    @Test
    void skipPropagatesTransitively() {
        // a→b on_success, b→c on_success, a 失败 → b 跳过，c 也应跳过
        WorkflowGraph g = WorkflowGraph.build(nodes("a", "b", "c"),
                Arrays.asList(e("a", "b", "on_success"), e("b", "c", "on_success")));
        Map<String, NodeState> st = g.initialStates();
        st.put("a", NodeState.FAILED);
        st.put("b", NodeState.SKIPPED);
        assertTrue(g.deadNodes(st).contains("c"));
    }

    @Test
    void openEdgeNotDead() {
        WorkflowGraph g = WorkflowGraph.build(nodes("a", "b"),
                Collections.singletonList(e("a", "b")));
        // a 仍 WAITING（OPEN）→ b 既不就绪也不跳过
        Map<String, NodeState> st = g.initialStates();
        assertFalse(g.deadNodes(st).contains("b"));
        assertFalse(g.nextReady(st).contains("b"));
    }

    // ===== 推进语义（nextReady 快照） =====

    @Test
    void serialChainAdvancesOneStep() {
        WorkflowGraph g = WorkflowGraph.build(nodes("a", "b", "c"),
                Arrays.asList(e("a", "b", "on_success"), e("b", "c", "on_success")));
        Map<String, NodeState> st = g.initialStates();
        st.put("a", NodeState.SUCCESS);
        Set<String> ready = g.nextReady(st);
        assertTrue(ready.contains("b"));
        assertFalse(ready.contains("c"));
    }

    @Test
    void alwaysChainFiresOnFailure() {
        WorkflowGraph g = WorkflowGraph.build(nodes("a", "b"),
                Collections.singletonList(e("a", "b", "always")));
        Map<String, NodeState> st = g.initialStates();
        st.put("a", NodeState.FAILED);
        assertTrue(g.nextReady(st).contains("b"));
    }

    @Test
    void conditionalMergeAfterSuccessBranch() {
        // a→b(s), a→c(f), b→d, c→d, a 成功 → b 跑、c 跳过；b 成功后 d 就绪
        WorkflowGraph g = WorkflowGraph.build(nodes("a", "b", "c", "d"),
                Arrays.asList(e("a", "b", "on_success"), e("a", "c", "on_failed"),
                        e("b", "d"), e("c", "d")));
        Map<String, NodeState> st = g.initialStates();
        st.put("a", NodeState.SUCCESS);
        st.put("b", NodeState.SUCCESS);
        st.put("c", NodeState.SKIPPED);
        assertTrue(g.nextReady(st).contains("d"));
    }

    @Test
    void conditionalMergeAfterFailureBranch() {
        WorkflowGraph g = WorkflowGraph.build(nodes("a", "b", "c", "d"),
                Arrays.asList(e("a", "b", "on_success"), e("a", "c", "on_failed"),
                        e("b", "d"), e("c", "d")));
        Map<String, NodeState> st = g.initialStates();
        st.put("a", NodeState.FAILED);
        st.put("b", NodeState.SKIPPED);
        st.put("c", NodeState.SUCCESS);
        assertTrue(g.nextReady(st).contains("d"));
    }

    // ===== finalStatus =====

    @Test
    void finalStatusAllSuccess() {
        Map<String, NodeState> st = new HashMap<>();
        st.put("a", NodeState.SUCCESS);
        st.put("b", NodeState.SUCCESS);
        assertEquals(NodeState.SUCCESS, WorkflowGraph.finalStatus(st));
    }

    @Test
    void finalStatusAnyFailed() {
        Map<String, NodeState> st = new HashMap<>();
        st.put("a", NodeState.SUCCESS);
        st.put("b", NodeState.FAILED);
        assertEquals(NodeState.FAILED, WorkflowGraph.finalStatus(st));
    }

    @Test
    void finalStatusSkippedOnlyIsSuccess() {
        Map<String, NodeState> st = new HashMap<>();
        st.put("a", NodeState.SUCCESS);
        st.put("b", NodeState.SKIPPED);
        assertEquals(NodeState.SUCCESS, WorkflowGraph.finalStatus(st));
    }

    @Test
    void finalStatusFailedDominatesSkipped() {
        Map<String, NodeState> st = new HashMap<>();
        st.put("a", NodeState.FAILED);
        st.put("b", NodeState.SKIPPED);
        assertEquals(NodeState.FAILED, WorkflowGraph.finalStatus(st));
    }

    @Test
    void firstFailureSummaryNullWhenNoFailure() {
        Map<String, NodeState> st = new HashMap<>();
        st.put("a", NodeState.SUCCESS);
        assertNull(WorkflowGraph.firstFailureSummary(st, null, null));
    }

    @Test
    void firstFailureSummaryDescribesLabelAndError() {
        Map<String, NodeState> st = new HashMap<>();
        st.put("a", NodeState.SUCCESS);
        st.put("b", NodeState.FAILED);
        Map<String, String> labels = Collections.singletonMap("b", "备份DB");
        Map<String, String> errs = Collections.singletonMap("b", "node offline");
        String summary = WorkflowGraph.firstFailureSummary(st, labels, errs);
        assertTrue(summary.contains("备份DB"));
        assertTrue(summary.contains("node offline"));
    }

    @Test
    void hasRunningAndAllTerminal() {
        Map<String, NodeState> st = new HashMap<>();
        st.put("a", NodeState.RUNNING);
        st.put("b", NodeState.WAITING);
        assertTrue(WorkflowGraph.hasRunning(st));
        assertFalse(WorkflowGraph.allTerminal(st));
        st.put("a", NodeState.SUCCESS);
        st.put("b", NodeState.SKIPPED);
        assertFalse(WorkflowGraph.hasRunning(st));
        assertTrue(WorkflowGraph.allTerminal(st));
    }

    @Test
    void normalizeNullAndBlank() {
        assertEquals("always", WorkflowGraph.normalize(null));
        assertEquals("always", WorkflowGraph.normalize("  "));
        assertEquals("on_success", WorkflowGraph.normalize(" on_success "));
    }

    @Test
    void conflictingConditionsActAsAlways() {
        // a→b 同时 on_success 与 on_failed 等价 always：a 任一终态均就绪
        WorkflowGraph g = WorkflowGraph.build(nodes("a", "b"),
                Arrays.asList(e("a", "b", "on_success"), e("a", "b", "on_failed")));
        Map<String, NodeState> st1 = g.initialStates();
        st1.put("a", NodeState.SUCCESS);
        assertEquals(Decision.READY, g.decide("b", st1));
        Map<String, NodeState> st2 = g.initialStates();
        st2.put("a", NodeState.FAILED);
        assertEquals(Decision.READY, g.decide("b", st2));
    }

    @Test
    void disconnectedComponentsAdvanceIndependently() {
        // 两个独立组件 a→b、c→d；a、c 均成功 → b、d 同时就绪（互不干扰），
        // 且 d 不会因 a 所在组件而等待。
        WorkflowGraph g = WorkflowGraph.build(nodes("a", "b", "c", "d"),
                Arrays.asList(e("a", "b"), e("c", "d")));
        Map<String, NodeState> st = g.initialStates();
        st.put("a", NodeState.SUCCESS);
        st.put("c", NodeState.SUCCESS);
        Set<String> ready = g.nextReady(st);
        assertEquals(2, ready.size());
        assertTrue(ready.contains("b"));
        assertTrue(ready.contains("d"));
    }

    @Test
    void entryNodeStaysReadyUntilDispatched() {
        // 入口节点 c 在 WAITING 时即就绪（与兄弟组件无关）
        WorkflowGraph g = WorkflowGraph.build(nodes("a", "b", "c", "d"),
                Arrays.asList(e("a", "b"), e("c", "d")));
        Map<String, NodeState> st = g.initialStates();
        st.put("a", NodeState.SUCCESS);
        // c 仍 WAITING → c 自身就绪；b 因 a 成功就绪；d 因 c 未终态不就绪
        Set<String> ready = g.nextReady(st);
        assertTrue(ready.contains("b"));
        assertTrue(ready.contains("c"));
        assertFalse(ready.contains("d"));
    }
}
