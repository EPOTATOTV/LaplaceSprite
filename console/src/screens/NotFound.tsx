import { Link } from 'react-router-dom'
import { Compass } from 'lucide-react'

import { Glass } from '../components/glass/Glass'
import styles from './NotFound.module.css'

export default function NotFoundScreen() {
  return (
    <Glass level="panel" className={styles.wrap}>
      <Compass size={20} strokeWidth={1.6} aria-hidden="true" className={styles.icon} />
      <h2 className={styles.title}>这条路没有铺进去</h2>
      <p className={styles.text}>当前地址不在控制台的路由表里，回到面板继续。</p>
      <Link to="/" className={styles.link}>
        返回平台管理后台
      </Link>
    </Glass>
  )
}
