import java.text.SimpleDateFormat
import java.util.Calendar
import kafka.serializer.StringDecoder
import org.apache.spark.SparkConf
import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.streaming.{Seconds, StreamingContext}
import net.sf.json.JSONObject

//数据实时收集处理

object CarCount {
    def main(args:Array[String]) = {
      //配置spark
      val masterURL = "local[2]"
      val conf = new SparkConf().setMaster(masterURL).setAppName("carcount")
      val ssc = new StreamingContext(conf,Seconds(5)) //五秒生成一个RDD
//      如果设置检查点会产生大量的检查点文件
//      ssc.checkpoint("")

      //配置kafka
      val topic = Set("test_event")
      val brokers = "10.211.55.17:9092,10.211.55.18:9092,10.211.55.19:9092"
      val kafkaParmas = Map[String,String](
        "bootstrap.servers" -> brokers, "key.serializer" -> "org.apache.kafka.common.serialization.StringSerializer", "value.serializer" -> "org.apache.kafka.common.serialization.StringSerializer"
      )

      //创建consumer
      //createDirectStream是自己来维护offset
      val kafkaStream = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](ssc, kafkaParmas, topic)
      //收集数据
      val event = kafkaStream.flatMap(line => {
        val data = JSONObject.fromObject(line._2)
        println(data)
        Some(data)
      })
      //数据预处理
      val carSpeed = event.map(x =>(x.getString("camera_id"), x.getInt("speed"))).mapValues((x:Int) => (x,1.toInt)).reduceByKeyAndWindow(
        (a:Tuple2[Int,Int], b:Tuple2[Int,Int]) => {(a._1 + b._1, a._2 + b._2)}, Seconds(20), Seconds(10))
      //数据格式 (id,(speed,1))

      //存储数据
      val dbindex = 1
      carSpeed.foreachRDD(rdd=>{
        rdd.foreachPartition(partitionRecords=>{
            val jedis = RedisClient.pool.getResource
            partitionRecords.foreach(pair=>{
              val id = pair._1 //元组下标访问是从1开始
              val speed = pair._2._1
              val count = pair._2._2

              val now = Calendar.getInstance().getTime
              val m = new SimpleDateFormat("HHmm")
              val day = new SimpleDateFormat("yyyyMMdd")
              val time = m.format(now)
              val d = day.format(now)
              if(count != 0){
                jedis.select(dbindex)
                //        (set名字，key，value)
                jedis.hset(d+"_"+id,time,speed+"_"+count)
              }
            })
            jedis.close()
          })
      })

      //启动spark streaming
      ssc.start()
      ssc.awaitTermination()
    }
}
