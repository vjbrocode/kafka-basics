package com.kafka.app.standalone;

import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;

public class ProducerStandalone {
	
	static String host = "localhost:9092";
	static String topic = "test-topic";
	
	private final static String TOPIC_NAME = topic;
	
	public static void main(String[] args){
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, host);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");

        KafkaProducer<String, Object> kafkaProducer = new KafkaProducer<>(properties);
        try{
            for(int i = 0; i < 1000000; i++){
                System.out.println(i);
                kafkaProducer.send(new ProducerRecord<String, Object>(TOPIC_NAME, Integer.toString(i), "test message - " + i ));
            }
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            kafkaProducer.close();
        }
    }

}
