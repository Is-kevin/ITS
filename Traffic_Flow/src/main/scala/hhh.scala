import java.io.{File, PrintWriter}

import scala.util.Random

//产生数据集
object hhh {
  def main(args:Array[String]): Unit = {
    val pw = new PrintWriter(new File("test2.txt"))
    val random = new Random()
    var camera_id : String = ""
    var id : Int = 0
    var time : String = ""
    var res : String = ""
    for(x <- 1 to 100000){
      id = random.nextInt(10) + 100
      camera_id = "100001000" + id

      var num1 = random.nextInt(12)+8
      var num2 = random.nextInt(50)+10
      var num3 = random.nextInt(50)+10
      time = "2020-01-14 "+num1+":"+num2+":"+num3

      var v = random.nextInt(60)+20

      res = camera_id + "，" + time + "，" + v
      pw.write(res+"\r\n")
    }
    pw.close()

  }
}
