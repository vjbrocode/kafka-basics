package com.kafka.app.standalone.metrics;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Sample Output:
 
$$LAG = {test-topic-2=PartitionOffsets [lag=240962, timestamp=1621841118911, endOffset=10332030, currentOffset=10091068, partion=2, topic=test-topic], test-topic-0=PartitionOffsets [lag=297786, timestamp=1621841118911, endOffset=10329013, currentOffset=10031227, partion=0, topic=test-topic], test-topic-1=PartitionOffsets [lag=241057, timestamp=1621841118911, endOffset=10338957, currentOffset=10097900, partion=1, topic=test-topic]}
================================================================================================
Lag on partition 2 of topic test-topic = 10332030 (endOffset) - 10091068 (currentOffset) = [ 240962 ] at 1621841118911
Lag on partition 0 of topic test-topic = 10329013 (endOffset) - 10031227 (currentOffset) = [ 297786 ] at 1621841118911
Lag on partition 1 of topic test-topic = 10338957 (endOffset) - 10097900 (currentOffset) = [ 241057 ] at 1621841118911

 * @author vijay
 *
 */
public class CosumerGroupLag {

	static String host = "localhost:9092";
	static String topic = "test-topic";
	static String groupId = "test-group";

	public static void main(String... vj) {
		CosumerGroupLag cgl = new CosumerGroupLag();

		while (true) {
			Map<TopicPartition, PartitionOffsets> lag = cgl.getConsumerGroupOffsets(host, topic, groupId);
			System.out.println("$$LAG = " + lag);
			printLagPerPartition(lag);
			waitFor10Seconds();
		}
	}

	private static void printLagPerPartition(Map<TopicPartition, PartitionOffsets> lag) {
		System.out.println("================================================================================================");
		for(Entry<TopicPartition, PartitionOffsets> s : lag.entrySet()) {
			PartitionOffsets po = s.getValue();
			System.out.println("Lag on partition " + po.partion + " of topic " + po.topic + " = " + po.endOffset + " (endOffset) - " + po.currentOffset + " (currentOffset) = [ " + po.lag + " ] at " + po.timestamp);
		}
		System.out.println();
	}

	public Map<TopicPartition, PartitionOffsets> getConsumerGroupOffsets(String host, String topic, String groupId) {
		Map<TopicPartition, Long> logEndOffset = getLogEndOffset(topic, host);

		Set<TopicPartition> topicPartitions = new HashSet<>();
		for (Entry<TopicPartition, Long> s : logEndOffset.entrySet()) {
			topicPartitions.add(s.getKey());
		}
		
		KafkaConsumer<String, Object> consumer = createNewConsumer(groupId, host);
		Map<TopicPartition, OffsetAndMetadata> comittedOffsetMeta = consumer.committed(topicPartitions);

		BinaryOperator<PartitionOffsets> mergeFunction = (a, b) -> {
			throw new IllegalStateException();
		};
		Map<TopicPartition, PartitionOffsets> result = logEndOffset.entrySet().stream()
				.collect(Collectors.toMap(entry -> (entry.getKey()), entry -> {
					OffsetAndMetadata committed = comittedOffsetMeta.get(entry.getKey());
					long currentOffset = 0;
					if(committed != null) { //committed offset will be null for unknown consumer groups
						currentOffset = committed.offset();
					}
					return new PartitionOffsets(entry.getValue(), currentOffset, entry.getKey().partition(), topic);
				}, mergeFunction));

		return result;
	}

	private final String monitoringConsumerGroupID = "monitoring_consumer_" + UUID.randomUUID().toString();
	public Map<TopicPartition, Long> getLogEndOffset(String topic, String host) {
		Map<TopicPartition, Long> endOffsets = new ConcurrentHashMap<>();
		KafkaConsumer<?, ?> consumer = createNewConsumer(monitoringConsumerGroupID, host);
		List<PartitionInfo> partitionInfoList = consumer.partitionsFor(topic);
		List<TopicPartition> topicPartitions = partitionInfoList.stream()
				.map(pi -> new TopicPartition(topic, pi.partition())).collect(Collectors.toList());
		consumer.assign(topicPartitions);
		consumer.seekToEnd(topicPartitions);
		topicPartitions.forEach(topicPartition -> endOffsets.put(topicPartition, consumer.position(topicPartition)));
		consumer.close();
		return endOffsets;
	}

	private static KafkaConsumer<String, Object> createNewConsumer(String groupId, String host) {
		Properties properties = new Properties();
		properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, host);
		properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
		properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
		properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		return new KafkaConsumer<>(properties);
	}

	private static class PartitionOffsets {
		private long lag;
		private long timestamp = System.currentTimeMillis();
		private long endOffset;
		private long currentOffset;
		private int partion;
		private String topic;

		public PartitionOffsets(long endOffset, long currentOffset, int partion, String topic) {
			this.endOffset = endOffset;
			this.currentOffset = currentOffset;
			this.partion = partion;
			this.topic = topic;
			this.lag = endOffset - currentOffset;
		}

		@Override
		public String toString() {
			return "PartitionOffsets [lag=" + lag + ", timestamp=" + timestamp + ", endOffset=" + endOffset
					+ ", currentOffset=" + currentOffset + ", partion=" + partion + ", topic=" + topic + "]";
		}

	}
	
	private static void waitFor10Seconds() {
		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}