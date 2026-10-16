import { useState } from 'react'
import { CornerDownLeft, Terminal } from 'lucide-react'

import { cx } from '../../lib/cx'
import { formatClock } from '../../lib/format'
import type { OrchestratorCommand } from '../../types'
import styles from './CommandPanel.module.css'

export interface CommandReceipt {
  id: string
  command: string
  text: string
  at: string
}

interface CommandPanelProps {
  commands: OrchestratorCommand[]
  receipts: CommandReceipt[]
  onSubmit: (raw: string) => void
}

export function CommandPanel({ commands, receipts, onSubmit }: CommandPanelProps) {
  const [value, setValue] = useState('')

  const submit = () => {
    if (value.trim().length === 0) return
    onSubmit(value.trim())
    setValue('')
  }

  return (
    <div className={styles.wrap}>
      <div className={styles.subs} role="group" aria-label="可用子命令">
        {commands.map((command) => (
          <button
            key={command.name}
            type="button"
            className={styles.sub}
            title={command.receipt}
            onClick={() => setValue(`${command.name} `)}
          >
            <span className={styles.subName}>/ls {command.name}</span>
            <span className={styles.subArgs}>{command.args.join(' ') || '—'}</span>
          </button>
        ))}
      </div>

      <div className={styles.right}>
        <form
          className={styles.form}
          onSubmit={(event) => {
            event.preventDefault()
            submit()
          }}
        >
          <span className={styles.prompt} aria-hidden="true">
            /ls
          </span>
          <input
            className={styles.input}
            value={value}
            onChange={(event) => setValue(event.target.value)}
            placeholder="inspect <SimID>"
            aria-label="输入 /ls 子命令并按回车执行"
            spellCheck={false}
            autoComplete="off"
          />
          <button type="submit" className={styles.send} disabled={value.trim().length === 0}>
            <CornerDownLeft size={13} strokeWidth={2.2} aria-hidden="true" />
            执行
          </button>
        </form>

        <div className={styles.log} aria-live="polite" aria-label="命令回执">
          <ol className={styles.logList}>
            {receipts.map((receipt, index) => (
              <li
                key={receipt.id}
                className={styles.receipt}
                style={{ animationDelay: `${Math.min(index, 12) * 24}ms` }}
              >
                <span className={cx(styles.receiptTime, 'tabular')}>
                  {formatClock(receipt.at)}
                </span>
                <span className={styles.receiptCmd}>/ls {receipt.command}</span>
                <span className={styles.receiptText}>{receipt.text}</span>
              </li>
            ))}
            {receipts.length === 0 && (
              <li className={styles.empty}>
                <Terminal size={12} strokeWidth={2} aria-hidden="true" />
                会话内暂无命令回执
              </li>
            )}
          </ol>
        </div>
      </div>
    </div>
  )
}
