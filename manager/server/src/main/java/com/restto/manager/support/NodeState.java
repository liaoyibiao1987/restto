package com.restto.manager.support;

/**
 * 工作流节点运行状态。
 *
 * <p>WAITING/RUNNING 为非终态（OPEN），SUCCESS/FAILED/SKIPPED 为终态。
 */
public enum NodeState {

    /** 待执行（未满足就绪条件，或在途任务被占用而驻车）。 */
    WAITING,
    /** 已下发、等待 Netty 结果回传。 */
    RUNNING,
    /** 执行成功。 */
    SUCCESS,
    /** 执行失败（含节点离线、任务不存在、超时等）。 */
    FAILED,
    /** 跳过（条件分支未命中，或上游被跳过的级联）。 */
    SKIPPED;

    /**
     * 是否终态。
     *
     * @return 终态返回 true
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == SKIPPED;
    }
}
