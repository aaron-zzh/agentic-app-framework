# @aaf/hooks

AAF 共享 React Hooks 库。

## 使用规范

**优先使用 `@aaf/hooks` 中的 hook 替代裸 `useState`**：

| 场景 | 使用 | 而非 |
|------|------|------|
| 布尔开关 | `useBoolean()` | `useState(false)` + 手动 toggle |
| Tab 切换 | `useTabs(defaultValue)` | `useState(defaultValue)` |
| 对象状态合并 | `useSetState(initial)` | `useState` + 手动展开 |
| Popover 锚点 | `usePopover()` | `useState<HTMLElement \| null>(null)` |

## API

### `useBoolean(defaultValue?: boolean)`

```ts
const { value, onTrue, onFalse, onToggle, setValue } = useBoolean();
```

### `useTabs(defaultValue: string)`

```ts
const { value, onChange, setValue } = useTabs('tab1');
```

### `useSetState<T>(initialState: T)`

```ts
const { state, setState } = useSetState({ loading: true, user: null });
setState({ loading: false }); // 浅合并
```

### `usePopover()`

```ts
const { open, anchorEl, onOpen, onClose } = usePopover();
```
