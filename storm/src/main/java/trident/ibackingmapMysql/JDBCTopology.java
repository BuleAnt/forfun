package trident.ibackingmapMysql;

import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.kafka.StringScheme;
import org.apache.storm.kafka.ZkHosts;
import org.apache.storm.kafka.trident.OpaqueTridentKafkaSpout;
import org.apache.storm.kafka.trident.TridentKafkaConfig;
import org.apache.storm.spout.SchemeAsMultiScheme;
import org.apache.storm.trident.TridentTopology;
import org.apache.storm.trident.operation.BaseFunction;
import org.apache.storm.trident.operation.CombinerAggregator;
import org.apache.storm.trident.operation.TridentCollector;
import org.apache.storm.trident.state.StateType;
import org.apache.storm.trident.testing.FixedBatchSpout;
import org.apache.storm.trident.tuple.TridentTuple;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.activation.FileDataSource;
import javax.swing.tree.FixedHeightLayoutCache;

/**
 *  测试可保证精准消费
 *  JDBCState<T> implements IBackingMap<T>
 *  Factory implements StateFactory
 *  kafkaspout str字段
 */
public class JDBCTopology {
    public static void main(String[] args) {
        // zookeeper管理状态需要修改配置文件 transactional.zookeeper.servers
        TridentKafkaConfig tconfig = new TridentKafkaConfig(new ZkHosts("hadoop-5:2181,hadoop-6:2181"),"stormtx","stormtx1");
        tconfig.scheme = new SchemeAsMultiScheme(new StringScheme());
        OpaqueTridentKafkaSpout spout = new OpaqueTridentKafkaSpout(tconfig);

        // state持久化配置
        JDBCStateConfig config = new JDBCStateConfig();
        config.setDriver("org.postgresql.Driver");
        config.setUrl("jdbc:postgresql://10.167.222.124:5432/test2");
        config.setUsername("txbi");
        config.setPassword("txbipg");
        config.setBatchSize(10);
        config.setCacheSize(10);
//        config.setType(StateType.TRANSACTIONAL);
        config.setCols("tel");
        config.setTable("stormtx");

        TridentTopology topology = new TridentTopology();
        topology.newStream("OpaqueTest",spout)
                .each(new Fields("str"),new KeyValueFun(),new Fields("tel","times"))
                .groupBy(new Fields("tel"))
                .persistentAggregate(JDBCState.getFactory(config), new Fields("times"),
                        new SumCombineAgg(),new Fields("sum"));

        LocalCluster cluster = new LocalCluster();
        Config conf = new Config();
//        conf.put("transactional.zookeeper.servers","hadoop-5");
//        conf.put("transactional.zookeeper.port","2181");
        cluster.submitTopology("test",conf,topology.build());
    }
}
class KeyValueFun extends BaseFunction{
    @Override
    public void execute(TridentTuple tuple, TridentCollector collector) {
        String record = tuple.getString(0);
        System.out.println(record);
        for(String s: record.split(" "))
            collector.emit(new Values(s,1));
    }
}
// combinerAggregator 先在各自分区聚合，然后合并到一个分区再聚合
// reducerAggregator 先合并同一批次各分区到一个分区，然后聚合
// 所以使用aggregate时，combinerAggregator会有优势

class SumCombineAgg implements CombinerAggregator<Long>{
    private Logger logger = LoggerFactory.getLogger(getClass());
    @Override
    public Long init(TridentTuple tuple) {
        return Long.valueOf(tuple.getInteger(0));
    }

    @Override
    public Long combine(Long val1, Long val2) {
        long val = val1 + val2;
        return val;
    }

    @Override
    public Long zero() {
        return 0L;
    }
}
