/**
 * Field 命名空间——统一导出所有表单控件
 * @author AaronZZH & Kiro
 *
 * @example
 * ```tsx
 * <Form methods={methods} onSubmit={onSubmit}>
 *   <Field.Text name="name" label="姓名" />
 *   <Field.Select name="role" label="角色" options={options} />
 *   <Field.Number name="age" label="年龄" min={0} max={150} />
 *   <Field.Checkbox name="agree" label="同意协议" />
 *   <Field.Date name="birthday" label="生日" />
 *   <Field.Textarea name="bio" label="简介" rows={4} />
 * </Form>
 * ```
 */

import { FieldCheckbox } from "./field-checkbox"
import { FieldDate } from "./field-date"
import { FieldNumber } from "./field-number"
import { FieldSelect } from "./field-select"
import { FieldText } from "./field-text"
import { FieldTextarea } from "./field-textarea"

export const Field = {
  Text: FieldText,
  Textarea: FieldTextarea,
  Number: FieldNumber,
  Select: FieldSelect,
  Checkbox: FieldCheckbox,
  Date: FieldDate
}
