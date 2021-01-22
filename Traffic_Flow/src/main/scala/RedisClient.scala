import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import redis.clients.jedis.JedisPool
//工具类
object RedisClient {
    // 配置
  val host = "10.211.55.17"
  val port = 6379
  val timeout = 30000

    // 定义pool
  lazy val pool = new JedisPool(new GenericObjectPoolConfig(), host, port, timeout)

    // 关闭连接池
  lazy val hook = new Thread{
    override def run = {
      pool.destroy()
    }
  }
  sys.addShutdownHook(hook.run)
}
