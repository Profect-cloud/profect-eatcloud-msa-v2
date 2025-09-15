package com.eatcloud;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.configuration.Configuration;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.util.Properties;

public class EatCloudStreamProcessor {
    
    private static final String DOCDB_ENDPOINT = "eatcloud-recommendations.cluster-cnikgsy08gto.ap-northeast-2.docdb.amazonaws.com";
    private static final String LOKI_ENDPOINT = "http://10.20.11.117:3100";
    
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // Kinesis 설정
        Properties kinesisConfig = new Properties();
        kinesisConfig.setProperty("aws.region", "ap-northeast-2");
        kinesisConfig.setProperty("aws.credentials.provider", "AUTO");
        
        // 추천 이벤트 스트림
        FlinkKinesisConsumer<String> recommendationSource = new FlinkKinesisConsumer<>(
            "eatcloud-recommendation-events",
            new SimpleStringSchema(),
            kinesisConfig
        );
        
        // 상태 유지 로그 스트림
        FlinkKinesisConsumer<String> statefulLogSource = new FlinkKinesisConsumer<>(
            "eatcloud-stateful-logs",
            new SimpleStringSchema(),
            kinesisConfig
        );
        
        DataStream<String> recommendationStream = env.addSource(recommendationSource);
        DataStream<String> statefulLogStream = env.addSource(statefulLogSource);
        
        // 추천 이벤트 → DocumentDB
        recommendationStream.addSink(new DocumentDBSink());
        
        // 상태 유지 로그 → Loki
        statefulLogStream.addSink(new LokiSink());
        
        env.execute("EatCloud Dual Stream Processor");
    }
    
    // DocumentDB Sink 클래스
    public static class DocumentDBSink extends RichSinkFunction<String> {
        private MongoClient mongoClient;
        private MongoCollection<Document> collection;
        
        @Override
        public void open(Configuration parameters) throws Exception {
            String connectionString = "mongodb://dbadmin:devpassword123@" + DOCDB_ENDPOINT + ":27017/?ssl=true";
            mongoClient = MongoClients.create(connectionString);
            MongoDatabase database = mongoClient.getDatabase("eatcloud");
            collection = database.getCollection("recommendations");
        }
        
        @Override
        public void invoke(String value, Context context) throws Exception {
            Document doc = Document.parse(value);
            doc.append("processedAt", System.currentTimeMillis());
            collection.insertOne(doc);
        }
        
        @Override
        public void close() throws Exception {
            if (mongoClient != null) {
                mongoClient.close();
            }
        }
    }
    
    // Loki Sink 클래스
    public static class LokiSink extends RichSinkFunction<String> {
        private CloseableHttpClient httpClient;
        
        @Override
        public void open(Configuration parameters) throws Exception {
            httpClient = HttpClients.createDefault();
        }
        
        @Override
        public void invoke(String value, Context context) throws Exception {
            HttpPost post = new HttpPost(LOKI_ENDPOINT + "/loki/api/v1/push");
            post.setHeader("Content-Type", "application/json");
            post.setHeader("X-Scope-OrgID", "1");
            
            String lokiPayload = String.format(
                "{\"streams\":[{\"stream\":{\"job\":\"kinesis-stateful-logs\"},\"values\":[[\"" + 
                System.currentTimeMillis() * 1000000 + "\",\"%s\"]]}]}", 
                value.replace("\"", "\\\"")
            );
            
            post.setEntity(new StringEntity(lokiPayload));
            httpClient.execute(post);
        }
        
        @Override
        public void close() throws Exception {
            if (httpClient != null) {
                httpClient.close();
            }
        }
    }
}
