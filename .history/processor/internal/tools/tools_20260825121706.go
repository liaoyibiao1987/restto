// Package tools 实现工具聚合与注册中心。
//
// 汇集所有内置工具，对外暴露统一的 Registry() / Lookup()，
// 供 cli（tool list / tool run）与 daemon（按 module 名调度）使用。
//
// # 新增工具
//
//  1. 在 internal/<your-tool>/ 新建包，实现 common.Tool 接口；
//  2. 在本包 import 该包，并在 Registry() 里 append 一行；
//  3. 为新包补 README.md（工具功能 / 参数 / 调用方式）。
//
// 其余代码（cli / daemon）无需改动 —— 这正是 agent 友好 CLI 的解耦点。
package tools

import (
	"sync"

	"rustto-client/internal/backupfile"
	"rustto-client/internal/backupmysql"
	"rustto-client/internal/common"
)

var (
	registryOnce sync.Once
	registry     []common.Tool
	byName       map[string]common.Tool
)

// Registry 构造内置工具集合。
//
// 返回共享实例：daemon 可在多任务间复用同一份无状态工具，
// 避免每条任务重建。顺序即 tool list 的展示顺序。
func Registry() []common.Tool {
	registryOnce.Do(func() {
		registry = []common.Tool{
			backupfile.Tool{},
			backupmysql.Tool{},
		}
		byName = make(map[string]common.Tool, len(registry))
		for _, tool := range registry {
			byName[tool.Info().Name] = tool
		}
	})
	out := make([]common.Tool, len(registry))
	copy(out, registry)
	return out
}

// Lookup 按工具名查找，返回共享实例；未注册返回 nil。
func Lookup(name string) common.Tool {
	Registry() // 确保已初始化
	return byName[name]
}
