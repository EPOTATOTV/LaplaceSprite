import type { TargetTypeRow } from '../../types'
import styles from './TargetTypePanel.module.css'

export function TargetTypePanel({ rows }: { rows: TargetTypeRow[] }) {
  return (
    <div className={styles.wrap}>
      <table className={styles.table}>
        <caption className="visually-hidden">
          12 类被测目标的测试重点、框架工具、代表项目与典型风险
        </caption>
        <thead>
          <tr>
            <th scope="col">目标类型</th>
            <th scope="col">测试重点 / 框架工具</th>
            <th scope="col">代表项目</th>
            <th scope="col">典型风险</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr key={row.type} style={{ animationDelay: `${index * 26}ms` }}>
              <th scope="row">
                <span className={styles.typeName}>{row.label}</span>
                <span className={styles.typeCode}>{row.type}</span>
              </th>
              <td>
                <ul className={styles.focus}>
                  {row.testFocus.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
                <p className={styles.tools}>
                  <span>工具</span>
                  {row.frameworks.join(' · ')}
                </p>
              </td>
              <td>
                <div className={styles.projects}>
                  {row.projects.map((project) => (
                    <span key={project} className={styles.project}>
                      {project}
                    </span>
                  ))}
                </div>
              </td>
              <td>
                <ul className={styles.risks}>
                  {row.risks.map((risk) => (
                    <li key={risk}>{risk}</li>
                  ))}
                </ul>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
