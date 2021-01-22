import java.util.Properties
import net.sf.json.JSONObject
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.spark.{SparkConf, SparkContext}

//数据产生模块

object virtualProducer {
    def main(args :Array[String])={
      //配置kafka，kafka初始化
      val brokers = "10.211.55.17:9092,10.211.55.18:9092,10.211.55.19:9092"
      val props = new Properties()
      props.setProperty("bootstrap.servers", brokers)
      props.setProperty("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
      props.setProperty("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")

      val producer: KafkaProducer[String,String] = new KafkaProducer[String,String](props)

      //配置spark，模拟数据量比较大的情况下数据处理
      val sparkConf = new SparkConf().setAppName("myTraffic").setMaster("local[2]")
      val sc = new SparkContext(sparkConf)

      //读文件处理
      val filename = "test2.txt"
      val rec = sc.textFile(filename).filter(!_.startsWith(";")).map(_.split("，")).collect() //注意这里的逗号是圆角还是半角要和test.txt文件匹配
      //数据发送
      for(item <- rec){
        //数据准备
        val event = new JSONObject()
//        event.put("camera_id",item(0))
//        event.put("even_time",item(4))
//        event.put("speed",item(6))
        event.put("camera_id",item(0))
        event.put("even_time",item(1))
        event.put("speed",item(2))

        //发送数据
        val topic = "test_event"
        producer.send(new ProducerRecord[String,String](topic,event.toString()))
        println("message sent" + event)
        Thread.sleep(200)
      }
    }
}
