import java.text.SimpleDateFormat
import java.util.Date

import org.apache.spark.mllib.classification.LogisticRegressionModel
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.mllib.linalg.Vectors

import scala.collection.mutable.ArrayBuffer




object predict {
  def main(args:Array[String])={
    //配置spark
    val sparkConf = new SparkConf().setAppName("predict").setMaster("local[4]")
    val sc = new SparkContext(sparkConf)
    //模拟卡口数据，时间和卡口
    val day = new SimpleDateFormat("yyyyMMdd")
    val minute = new SimpleDateFormat("HHmm")
    val sdf = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss")
    val input = "2021-01-13_23:06:26" //每次运行前要先将此时间设置成当前时间
    val date = sdf.parse(input)
    val inputTime = date.getTime
    val inputTempTime = new Date(inputTime)
    val Day = day.format(inputTempTime)
    val carmera_ids = List("100001000101", "100001000106")
    val carmera_relations:Map[String,Array[String]] = Map[String,Array[String]]("100001000101"->Array("100001000102","100001000103",
      "100001000104","100001000105","100001000101"),"100001000106"->Array("100001000107","100001000108","100001000109","100001000106","100001000105"))

    //读数据
    val jedis = RedisClient.pool.getResource
    jedis.select(1)
    val tmp = carmera_ids.map({carmera_id =>
      val list = carmera_relations(carmera_id)
      val relations = list.map({carmera_id=>
        (carmera_id, jedis.hgetAll(Day+"_"+carmera_id))
      })
      //数据准备
      val dataX = ArrayBuffer[Double]()
      for(index <- Range(4,0,-1)){ //用近3分钟的数据预测
        val tmpOne = inputTime-60*1000*index
        val tmpMinute = minute.format(tmpOne)
        for((k, v) <- relations){
          val map = v
          if(map.containsKey(tmpMinute)){
            val info = map.get(tmpMinute)
            val f = info(0).toFloat/info(1).toFloat  //总速度/总车辆数
            dataX += f
          }
          else{
            dataX += -1
          }
        }
      }
      print(Vectors.dense(dataX.toArray))
      //加载模型
      val path = jedis.hget("model",carmera_id)
      val model = LogisticRegressionModel.load(sc,path)
      //预测
      val prediction = model.predict(Vectors.dense(dataX.toArray))
      println(prediction)
      //结果保存
      jedis.hset(input,carmera_id,prediction.toString)
    })
    RedisClient.pool.close()


  }
}
