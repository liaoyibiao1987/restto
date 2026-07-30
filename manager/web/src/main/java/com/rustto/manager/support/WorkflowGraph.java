package com.rustto.manager.support;

import com.rustto.manager.common.BusinessException;
import com.rustto.manager.common.ResultCode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工作流 DAG 纯引擎（无 Spring / 无 IO），由节点键集合 + 边集合构造。
 *
 * <p>负责：图合法性校验、无环校验（Kahn 拓扑）、入口节点、边条件满足判定、
 * 节点就绪/跳过判定（any-path-success 语义）、终态汇总。全部为纯函数式计算，便于单测覆盖。
 *
 * <h3>就绪 / 跳过判定（any-path-success）</h3>
 * 对节点 N 的入边集 {(src_i, cond_i)}：
 * <ul>
 *   <li>边 OPEN：src 非终态（WAITING/RUNNING）</li>
 *   <li>边 PASSED：src 终态且 {@link #isSatisfied} 命中</li>
 *   <li>边 MISSED：src 终态且未命中</li>
 * </ul>
 * <ul>
 *   <li>READY ⇔ openCount==0 且 passedCount≥1（入口节点 0 入边 ⇒ READY）</li>
 *   <li>SKIP ⇔ 入边&gt;0 且 openCount==0 且 passedCount==0</li>
 *   <li>否则 WAIT（仍需等待）</li>
 * </ul>
 * <p>该语义正确支持串行链、并行、条件分流与菱形汇合（任一上游成功即跑汇合点），
 * 且对孤悬不可满足边不会死锁（直接 SKIP）。
 */
public final class WorkflowGraph {

    /** 默认边条件。 */
    public static final String COND_ALWAYS = "always";
    /** 成功才继续。 */
    public static final String COND_ON_SUCCESS = "on_success";
    /** 失败才继续。 */
    public static final String COND_ON_FAILED = "on_failed";

    /** 一个 WAITING 节点在当前状态下的去向（判定结果，非持久状态）。 */
    public enum Decision {
        /** 就绪，可下发。 */
        READY,
        /** 应跳过。 */
        SKIP,
        /** 仍需等待上游。 */
        WAIT
    }

    /** 不可变边。 */
    public static final class Edge {
        /** 源节点键。 */
        public final String sourceKey;
        /** 目标节点键。 */
        public final String targetKey;
        /** 条件：on_success / on_failed / always。 */
        public final String condition;

        /**
         * @param sourceKey 源
         * @param targetKey 目标
         * @param condition 条件（null/空 归一为 always）
         */
        public Edge(String sourceKey, String targetKey, String condition) {
            this.sourceKey = sourceKey;
            this.targetKey = targetKey;
            this.condition = normalize(condition);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Edge)) {
                return false;
            }
            Edge e = (Edge) o;
            return sourceKey.equals(e.sourceKey)
                    && targetKey.equals(e.targetKey)
                    && condition.equals(e.condition);
        }

        @Override
        public int hashCode() {
            return sourceKey.hashCode() * 31 * 31 + targetKey.hashCode() * 31 + condition.hashCode();
        }
    }

    private final Set<String> nodeKeys;
    /** targetKey → 入边列表。 */
    private final Map<String, List<Edge>> incoming;
    /** sourceKey → 出边列表。 */
    private final Map<String, List<Edge>> outgoing;

    private WorkflowGraph(Set<String> nodeKeys, Map<String, List<Edge>> incoming,
                          Map<String, List<Edge>> outgoing) {
        this.nodeKeys = nodeKeys;
        this.incoming = incoming;
        this.outgoing = outgoing;
    }

    /**
     * 由节点键集合 + 边集合构造（自动去重重复边、归一化条件）。
     *
     * @param nodeKeys 节点键集合
     * @param edges    边集合
     * @return 工作流图
     */
    public static WorkflowGraph build(Set<String> nodeKeys, List<Edge> edges) {
        Set<String> keys = new LinkedHashSet<>(nodeKeys == null ? Collections.emptySet() : nodeKeys);
        Set<Edge> dedup = new LinkedHashSet<>(edges == null ? Collections.emptyList() : edges);
        Map<String, List<Edge>> incoming = new HashMap<>();
        Map<String, List<Edge>> outgoing = new HashMap<>();
        for (String k : keys) {
            incoming.put(k, new ArrayList<>());
            outgoing.put(k, new ArrayList<>());
        }
        for (Edge e : dedup) {
            incoming.computeIfAbsent(e.targetKey, k -> new ArrayList<>()).add(e);
            outgoing.computeIfAbsent(e.sourceKey, k -> new ArrayList<>()).add(e);
        }
        return new WorkflowGraph(Collections.unmodifiableSet(keys), incoming, outgoing);
    }

    /**
     * 归一化边条件：null/空 → always。
     *
     * @param condition 原始条件
     * @return 归一化后条件
     */
    public static String normalize(String condition) {
        if (condition == null || condition.trim().isEmpty()) {
            return COND_ALWAYS;
        }
        return condition.trim();
    }

    /**
     * 校验图结构良好：至少一个节点；所有边引用已知节点键。
     *
     * @throws BusinessException 节点为空 / 边引用未知节点
     */
    public void validateWellFormed() {
        if (nodeKeys.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "工作流至少需要一个节点");
        }
        for (List<Edge> es : incoming.values()) {
            for (Edge e : es) {
                if (!nodeKeys.contains(e.sourceKey) || !nodeKeys.contains(e.targetKey)) {
                    throw new BusinessException(ResultCode.PARAM_INVALID,
                            "边引用了不存在的节点: " + e.sourceKey + "→" + e.targetKey);
                }
            }
        }
    }

    /**
     * Kahn 拓扑排序校验无环（含自环）。
     *
     * @throws BusinessException 存在环
     */
    public void validateAcyclic() {
        Map<String, Integer> inDegree = new HashMap<>();
        for (String k : nodeKeys) {
            inDegree.put(k, incoming.get(k).size());
        }
        Deque<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> en : inDegree.entrySet()) {
            if (en.getValue() == 0) {
                queue.addLast(en.getKey());
            }
        }
        int processed = 0;
        while (!queue.isEmpty()) {
            String cur = queue.pollFirst();
            processed++;
            for (Edge e : outgoing.getOrDefault(cur, Collections.emptyList())) {
                int d = inDegree.get(e.targetKey) - 1;
                inDegree.put(e.targetKey, d);
                if (d == 0) {
                    queue.addLast(e.targetKey);
                }
            }
        }
        if (processed != nodeKeys.size()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "工作流存在环路，无法执行");
        }
    }

    /**
     * @return 入口节点键集合（无入边）
     */
    public Set<String> entryKeys() {
        Set<String> entries = new LinkedHashSet<>();
        for (String k : nodeKeys) {
            if (incoming.get(k).isEmpty()) {
                entries.add(k);
            }
        }
        return entries;
    }

    /**
     * 边条件是否对给定**终态**源状态满足（非终态不应调用）。
     *
     * @param condition 条件
     * @param srcStatus 源状态（仅 SUCCESS/FAILED/SKIPPED 有意义）
     * @return 命中返回 true
     */
    public static boolean isSatisfied(String condition, NodeState srcStatus) {
        String c = normalize(condition);
        switch (srcStatus) {
            case SUCCESS:
                return COND_ON_SUCCESS.equals(c) || COND_ALWAYS.equals(c);
            case FAILED:
                return COND_ON_FAILED.equals(c) || COND_ALWAYS.equals(c);
            case SKIPPED:
                // 被跳过的源不应触发下游；下游应经由真正命中的兄弟边触发，或一并跳过。
                return false;
            default:
                return false;
        }
    }

    /**
     * 判定某节点在当前状态下的去向。
     *
     * <p>对非 WAITING 节点，返回 {@link Decision#WAIT}（调用方不应再处理已决节点）。
     *
     * @param nodeKey 节点键
     * @param states  全节点状态
     * @return 去向
     */
    public Decision decide(String nodeKey, Map<String, NodeState> states) {
        NodeState cur = states.get(nodeKey);
        if (cur == null) {
            cur = NodeState.WAITING;
        }
        if (cur != NodeState.WAITING) {
            return Decision.WAIT;
        }
        List<Edge> ins = incoming.getOrDefault(nodeKey, Collections.emptyList());
        if (ins.isEmpty()) {
            return Decision.READY;
        }
        int open = 0;
        int passed = 0;
        for (Edge e : ins) {
            NodeState src = states.get(e.sourceKey);
            if (src == null || !src.isTerminal()) {
                open++;
            } else if (isSatisfied(e.condition, src)) {
                passed++;
            }
        }
        if (open == 0 && passed >= 1) {
            return Decision.READY;
        }
        if (open == 0) {
            return Decision.SKIP;
        }
        return Decision.WAIT;
    }

    /**
     * 计算当前状态下「就绪」（WAITING 且 decide==READY）的节点集合。
     *
     * @param states 全节点状态
     * @return 就绪节点键集合
     */
    public Set<String> nextReady(Map<String, NodeState> states) {
        Set<String> ready = new LinkedHashSet<>();
        for (String k : nodeKeys) {
            if (states.get(k) == NodeState.WAITING && decide(k, states) == Decision.READY) {
                ready.add(k);
            }
        }
        return ready;
    }

    /**
     * 计算当前状态下应「跳过」（WAITING 且 decide==SKIP）的节点集合。
     *
     * @param states 全节点状态
     * @return 待跳过节点键集合
     */
    public Set<String> deadNodes(Map<String, NodeState> states) {
        Set<String> dead = new LinkedHashSet<>();
        for (String k : nodeKeys) {
            if (states.get(k) == NodeState.WAITING && decide(k, states) == Decision.SKIP) {
                dead.add(k);
            }
        }
        return dead;
    }

    /**
     * 汇总工作流终态：任一节点 FAILED → FAILED；否则 SUCCESS。
     *
     * <p>应在所有节点终态后调用。SKIPPED 不计失败。
     *
     * @param states 全节点状态
     * @return SUCCESS / FAILED
     */
    public static NodeState finalStatus(Map<String, NodeState> states) {
        for (NodeState s : states.values()) {
            if (s == NodeState.FAILED) {
                return NodeState.FAILED;
            }
        }
        return NodeState.SUCCESS;
    }

    /**
     * @return 全部节点键（不可变）
     */
    public Set<String> nodeKeys() {
        return nodeKeys;
    }

    /**
     * @param nodeKey 节点键
     * @return 该节点的入边（不可变视图）
     */
    public List<Edge> incomingOf(String nodeKey) {
        return Collections.unmodifiableList(incoming.getOrDefault(nodeKey, Collections.emptyList()));
    }

    /**
     * 构造全节点初始化为 WAITING 的状态表（保持插入顺序）。
     *
     * @return 初始状态表
     */
    public Map<String, NodeState> initialStates() {
        Map<String, NodeState> states = new LinkedHashMap<>();
        for (String k : nodeKeys) {
            states.put(k, NodeState.WAITING);
        }
        return states;
    }

    /**
     * 是否全部节点终态。
     *
     * @param states 全节点状态
     * @return 全终态返回 true
     */
    public static boolean allTerminal(Map<String, NodeState> states) {
        for (NodeState s : states.values()) {
            if (!s.isTerminal()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 是否存在仍在途（RUNNING）的节点（finalize 判定：无 RUNNING 才可结算）。
     *
     * @param states 全节点状态
     * @return 有 RUNNING 返回 true
     */
    public static boolean hasRunning(Map<String, NodeState> states) {
        for (NodeState s : states.values()) {
            if (s == NodeState.RUNNING) {
                return true;
            }
        }
        return false;
    }

    /**
     * 收集首个失败节点的简短原因摘要。
     *
     * @param states         全节点状态
     * @param nodeKeyToLabel 节点键→标签
     * @param errors         节点键→错误信息
     * @return 失败摘要；无失败返回 null
     */
    public static String firstFailureSummary(Map<String, NodeState> states,
                                              Map<String, String> nodeKeyToLabel,
                                              Map<String, String> errors) {
        for (Map.Entry<String, NodeState> en : states.entrySet()) {
            if (en.getValue() == NodeState.FAILED) {
                String label = nodeKeyToLabel == null ? en.getKey() : nodeKeyToLabel.get(en.getKey());
                String err = errors == null ? null : errors.get(en.getKey());
                String head = (label == null || label.isEmpty()) ? en.getKey() : label;
                return err == null || err.isEmpty() ? ("节点 " + head + " 失败") : ("节点 " + head + " 失败: " + err);
            }
        }
        return null;
    }
}
