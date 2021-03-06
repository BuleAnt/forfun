package graphx

import org.apache.log4j.{Level, Logger}
import org.apache.spark.graphx.{Edge, Graph, VertexId, VertexRDD}
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}

/**
  * Created by Administrator on 2018/3/19.
  */
class GraphX_Test {

}

object GraphX_Test{
    def main(args: Array[String]): Unit = {
        Logger.getLogger("org.apache.spark").setLevel(Level.WARN)
        Logger.getLogger("org.eclilpse.jetty.server").setLevel(Level.WARN)

        //运行环境
        val conf = new SparkConf().setAppName("simpleGraphX").setMaster("local[2]")
        val sc = new SparkContext(conf)

        //设置点和边，都用元组定义的Array,数据类型是(String,Int)
        val vertexArray = Array(
            (1L,("Alice",28)),
            (2L,("bob",27)),
            (3l,("Charlie",65)),
            (4L,("David",42)),
            (5L,("Ed",55)),
            (6L,("Fran",50))
        )
        //边
        val edgeArray = Array(
            Edge(2L,1L,7), //顶点，另一顶点，关系
            Edge(2L,4L,2),
            Edge(3L,2L,4),
            Edge(3L,6L,3),
            Edge(4L,1L,1),
            Edge(5L,2L,2),
            Edge(5L,3L,8),
            Edge(5L,6L,3)
        )
        //构造vertexRDD和edgeRDD
        val vertexRDD:RDD[(Long,(String,Int))] = sc.parallelize(vertexArray)
        val edgeRDD:RDD[Edge[Int]] = sc.parallelize(edgeArray)
        //构造图
        val graph:Graph[(String,Int),Int] = Graph(vertexRDD,edgeRDD)

        println("属性演示!")
        println("找出图中年龄大于30的顶点:")
        graph.vertices.filter{
            case(id,(name,age)) => age > 30
        }.collect().foreach{
            case(id,(name,age)) => println(s"$name is $age")
        }
        //边操作
        println("找出图中属性大于5的边:")
        graph.edges.filter(
            e => e.attr>5
        ).collect().foreach(
            e => println(s"${e.srcId} to ${e.dstId} attr ${e.attr}")
        )
        //triplets操作 ((srcId,srcAttr),(dstId,dstAttr),attr)
        println("列出边属性>5的triplets:")
        for(triplet <- graph.triplets.filter(t => t.attr>5).collect){
            println(s"${triplet.srcAttr._1} likes ${triplet.dstAttr._1}")
        }
        //Degrees操作
        println("找出图中的最大出度、入度、度数:")
        var liebiao =  List[(VertexId,Int)]()
        def max(a:(VertexId,Int),b:(VertexId,Int)):(VertexId,Int)= {
            if(a._2 >= b._2) {
                a
            }
            else {
                b
            }
        }
        println(" max of outDegrees:" + graph.outDegrees.reduce(max) +
         " max of inDegrees:" + graph.inDegrees.reduce(max) +
         " max of Degrees:" + graph.degrees.reduce(max) )
        //转换操作
        println("/n转换操作")
        println("顶点的转换操作，顶点age + 10")
        graph.mapVertices{
            case (id,(name,age)) => (id,(name,age + 10))
        }.vertices.collect.foreach(v => println(s"${v._2._1} is ${v._2._2}"))
        println("转换边的操作,边的属性 * 2")
        graph.mapEdges{e => e.attr * 2}.edges.collect().foreach(e => println(s"${e.srcId} to ${e.dstId} att ${e.attr}"))
        println()
        //结构操作
        println("顶点年纪 > 30的子图:")
        val subGraph = graph.subgraph(vpred = (id,vd) => vd._2 >= 30)
        println("子图所有顶点:")
        subGraph.vertices.collect.foreach(v => println(s"${v._2._1} is ${v._2._2}"))
        println("子图所有边:")
        subGraph.edges.collect.foreach(e => println(s"${e.srcId} to ${e.dstId} att ${e.attr}"))
        println()

        //连接操作
        println("连接操作:")
        val inDgrees:VertexRDD[Int] = graph.inDegrees
        case class User(name:String,age:Int,inDeg:Int,outDeg:Int)
        //创建一个新图，顶点VD的数据类型为User，并从graph做类型转换
        val initialUserGraph:Graph[User,Int] = graph.mapVertices{
            case (id,(name,age)) => User(name,age,0,0)
        }
        //initialUserGraph与inDegrees、outDegrees进行连接，并修改initialGraph中inDeg值、outDeg值
        //
        val userGraph = initialUserGraph.outerJoinVertices(initialUserGraph.inDegrees){
            case(id,u,inDegOpt) => User(u.name,u.age,inDegOpt.getOrElse(0),u.outDeg)
        }.outerJoinVertices(initialUserGraph.outDegrees){
            case(id,u,outDegOpt) => User(u.name,u.age,u.inDeg,outDegOpt.getOrElse(0))
        }
        println("出度和入度相同的人员")
        userGraph.vertices.filter{
            case(id,u) => u.inDeg == u.outDeg
        }.collect.foreach{case (id,u) => println(u.name)}











        sc.stop()
    }
}
