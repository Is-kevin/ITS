import java.text.SimpleDateFormat
import scala.collection.mutable.ArrayBuffer
import java.util.Date
import org.apache.spark.mllib.evaluation.MulticlassMetrics
import org.apache.spark.mllib.classification.LogisticRegressionWithLBFGS
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression.LabeledPoint

//特征数据提取以及模型建立


object train {
    def main(args:Array[String])={
      //配置spark
      val sparkConf = new SparkConf().setAppName("myTraffic").setMaster("local[2]")
      val sc = new SparkContext(sparkConf)
      //获取数据
      val jedis = RedisClient.pool.getResource
      jedis.select(1)
      val carmera_ids = List("100001000101", "100001000106")
      val carmera_relations:Map[String,Array[String]] = Map[String,Array[String]]("100001000101"->Array("100001000102","100001000103",
      "100001000104","100001000105","100001000101"),"100001000106"->Array("100001000107","100001000108","100001000109","100001000106","100001000105"))
      val day = new SimpleDateFormat("yyyyMMdd")
      val minute = new SimpleDateFormat("HHmm")
      val tmp = carmera_ids.map({
        carmera_id => //"100001000101", "100001000106"
          val nowtime = System.currentTimeMillis()
          val dayNow = new Date(nowtime)
          val Day = day.format(dayNow)
          val list = carmera_relations(carmera_id)
          val relations = list.map({
            carmera_id => (carmera_id, jedis.hgetAll(Day+"_"+carmera_id))
              //"100001000102",(minute,speed_count)
              //"100001000103",(minute,speed_count)
              //.....
          })
          //逻辑处理
          val hours = 3 //使用3个小时内的数据作为训练数据
          val dataForTrain = ArrayBuffer[LabeledPoint]()
          for(i <- Range(60*hours+3,2,-1)){//-1表示倒序 2表示循环到2停止
            val dataX = ArrayBuffer[Double]()
            val dataY = ArrayBuffer[Double]()
            //滤波 dataY取每四分钟的最后一个值
            for(index <- 0 to 3){
              val tempOne = nowtime-60*1000*(i-index)
              val d = new Date(tempOne)
              val tempMinute = minute.format(d)
              for((k,v) <- relations){
                //k:camera_id v:(minute,speed_count)
                val map = v
                if(k == carmera_id && index == 2){
                  val tempNext = tempOne - 60*1000*(-1)  //下一分钟
                  val dNext = new Date(tempNext)
                  val tempNextMinute = minute.format(dNext)
                  if(map.containsKey(tempNextMinute)){
                    //info (speed,count)
                    val info = map.get(tempNextMinute).split("_")
                    val f = info(0).toFloat/info(1).toFloat
                    dataY += f
                  }
                }
                if(map.containsKey(tempMinute)){
                  val info = map.get(tempMinute).split("_")
                  val f = info(0).toFloat/info(1).toFloat
                  dataX += f
                }
                else {
                  dataX += -1.0
                }
              }
            }
            //构建训练数据
            if (dataY.toArray.length == 1){
              val label = dataY.toArray.head
              val record = LabeledPoint(
                if(label.toInt/10<10)    //1～10 10个级别，值越大越通畅，越小越拥堵
                  label.toInt/10
                else
                  10.0,Vectors.dense(dataX.toArray)
              )
              dataForTrain += record
            }
          }
          dataForTrain.foreach(println)
          println(dataForTrain.length)

          val data = sc.parallelize(dataForTrain)
          val splits = data.randomSplit(Array(0.6,0.4), seed = 11L)
          val training = splits(0)
          val test = splits(1)
          if(!data.isEmpty()){
            //训练model 这里还是直接用data而不是切分出来的training数据，是因为我们的数据太少了
            val model = new LogisticRegressionWithLBFGS().setNumClasses(11).run(data)
            //测试
            val predictionAndLables = test.map{case LabeledPoint(label, features) =>
              val prediction = model.predict(features)
              (prediction,label)}
            //获取评估值
            val metrics = new MulticlassMetrics(predictionAndLables)
            val accuracy = metrics.accuracy
            println("accuracy = " + accuracy)

            //存储
//            if(accuracy > 0.8){
              val path = "hdfs://10.211.55.17:9000/lpd/model"+carmera_id+"_"+nowtime
              model.save(sc,path)
              jedis.hset("model",carmera_id,path)
            }
//          }

      })

      RedisClient.pool.close()

    }
}
