package com.allen.redisson.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.support.atomic.RedisAtomicLong;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Created by xuguocai on 2021/3/15 9:43  对外提供的调用窗口  redis 集群 ，可以直接用redisson自带分布式锁
 */
@Repository
@DependsOn
public class RedisRepository {

    /**
     * Logger
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisRepository.class);

    /**
     * 默认编码
     */
    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    /**
     * Spring Redis Template
     */
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    public RedisRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 添加到带有 过期时间的  缓存
     *
     * @param key   redis主键
     * @param value 值
     * @param time  过期时间
     */
    public void setExpire(final byte[] key, final byte[] value, final long time) {
        redisTemplate.execute((RedisCallback<Long>) connection -> {
            connection.set(key, value);
            connection.expire(key, time);
            LOGGER.info("[redisTemplate redis]放入 缓存  url:{} ========缓存时间为{}秒", key, time);
            return 1L;
        });
    }

    /**
     * 添加到带有 过期时间的  缓存
     *
     * @param key   redis主键
     * @param value 值
     * @param time  过期时间
     */
    public void setExpire(final String key, final String value, final long time) {
        redisTemplate.execute((RedisCallback<Long>) connection -> {
            RedisSerializer<String> serializer = getRedisSerializer();
            byte[] keys = serializer.serialize(key);
            byte[] values = serializer.serialize(value);
            connection.set(keys, values);
            connection.expire(keys, time);
            LOGGER.info("[redisTemplate redis]放入 缓存  url:{} ========缓存时间为{}秒", key, time);
            return 1L;
        });
    }

    /**
     *  通过 setNx 实现分布式锁
     * @param key  缓存键
     * @param time  过期时间 如 30 秒
     * @return
     */
    public boolean setNx(final String key,long time){
        // 利用lambda表达式
        return (Boolean) redisTemplate.execute((RedisCallback) connection -> {
            // 对 key 是否设置成功
            long expireAt = System.currentTimeMillis() + time ;
            Boolean acquire = connection.setNX(key.getBytes(), String.valueOf(expireAt).getBytes());
            // 成功设置
            if (acquire) {
                return true;
            } else {
                // 重试
                byte[] value = connection.get(key.getBytes());

                if (Objects.nonNull(value) && value.length > 0) {
                    long expireTime = Long.parseLong(new String(value));
                    // 如果锁已经过期
                    if (expireTime < System.currentTimeMillis()) {
                        // 重新加锁，防止死锁
                        byte[] oldValue = connection.getSet(key.getBytes(), String.valueOf(System.currentTimeMillis() + time ).getBytes());
                        return Long.parseLong(new String(oldValue)) < System.currentTimeMillis();
                    }
                }
            }
            return false;
        });
    }

    /**
     * 通过 setIfAbsent 设置 分布式锁
     * @param key
     * @param time  过期时间 如 30 秒
     * @return
     */
    public boolean setNxex(String key,long time) {
        try {
            // setIfAbsent 原子性
            Boolean result = redisTemplate.opsForValue().setIfAbsent(key, 1, time, TimeUnit.SECONDS);
            if (result != null) {
                return result;
            }
        } catch (Throwable e) {
            LOGGER.error("setNXEX error", e);
        }

        return false;
    }

    /**
     * 一次性添加数组到   过期时间的  缓存，不用多次连接，节省开销
     *
     * @param keys   redis主键数组
     * @param values 值数组
     * @param time   过期时间
     */
    public void setExpire(final String[] keys, final String[] values, final long time) {
        redisTemplate.execute((RedisCallback<Long>) connection -> {
            RedisSerializer<String> serializer = getRedisSerializer();
            for (int i = 0; i < keys.length; i++) {
                byte[] bKeys = serializer.serialize(keys[i]);
                byte[] bValues = serializer.serialize(values[i]);
                connection.set(bKeys, bValues);
                connection.expire(bKeys, time);
                LOGGER.info("[redisTemplate redis]放入 缓存  url:{} ========缓存时间为:{}秒", keys[i], time);
            }
            return 1L;
        });
    }


    /**
     * 一次性添加数组到   过期时间的  缓存，不用多次连接，节省开销
     *
     * @param keys   the keys
     * @param values the values
     */
    public void set(final String[] keys, final String[] values) {
        redisTemplate.execute((RedisCallback<Long>) connection -> {
            RedisSerializer<String> serializer = getRedisSerializer();
            for (int i = 0; i < keys.length; i++) {
                byte[] bKeys = serializer.serialize(keys[i]);
                byte[] bValues = serializer.serialize(values[i]);
                connection.set(bKeys, bValues);
                LOGGER.info("[redisTemplate redis]放入 缓存  url:{}", keys[i]);
            }
            return 1L;
        });
    }


    /**
     * 添加到缓存
     *
     * @param key   the key
     * @param value the value
     */
    public void set(final String key, final String value) {
        redisTemplate.execute((RedisCallback<Long>) connection -> {
            RedisSerializer<String> serializer = getRedisSerializer();
            byte[] keys = serializer.serialize(key);
            byte[] values = serializer.serialize(value);
            connection.set(keys, values);
            LOGGER.info("[redisTemplate redis]放入 缓存  url:{}", key);
            return 1L;
        });
    }

    /**
     * 查询在这个时间段内即将过期的key
     *
     * @param key  the key
     * @param time the time
     * @return the list
     */
    public List<String> willExpire(final String key, final long time) {
        final List<String> keysList = new ArrayList<>();
        redisTemplate.execute((RedisCallback<List<String>>) connection -> {
            Set<String> keys = redisTemplate.keys(key + "*");
            for (String key1 : keys) {
                Long ttl = connection.ttl(key1.getBytes(DEFAULT_CHARSET));
                if (0 <= ttl && ttl <= 2 * time) {
                    keysList.add(key1);
                }
            }
            return keysList;
        });
        return keysList;
    }


    /**
     * 查询在以keyPatten的所有  key
     *
     * @param keyPatten the key patten
     * @return the set
     */
    public Set<String> keys(final String keyPatten) {
        return redisTemplate.execute((RedisCallback<Set<String>>) connection -> redisTemplate.keys(keyPatten + "*"));
    }

    /**
     * 根据key获取对象
     *
     * @param key the key
     * @return the byte [ ]
     */
    public byte[] get(final byte[] key) {
        byte[] result = redisTemplate.execute((RedisCallback<byte[]>) connection -> connection.get(key));
        // LOGGER.info("[redisTemplate redis]取出 缓存  url:{} ", key);
        return result;
    }

    /**
     * 根据key获取对象
     *
     * @param key the key
     * @return the string
     */
    public String get(final String key) {
        String resultStr = redisTemplate.execute((RedisCallback<String>) connection -> {
            RedisSerializer<String> serializer = getRedisSerializer();
            byte[] keys = serializer.serialize(key);
            byte[] values = connection.get(keys);
            return serializer.deserialize(values);
        });
        // LOGGER.info("[redisTemplate redis]取出 缓存  url:{} ", key);
        return resultStr;
    }


    /**
     * 根据key获取对象
     *
     * @param keyPatten the key patten
     * @return the keys values
     */
    public Map<String, String> getKeysValues(final String keyPatten) {
        LOGGER.info("[redisTemplate redis]  getValues()  patten={} ", keyPatten);
        return redisTemplate.execute((RedisCallback<Map<String, String>>) connection -> {
            RedisSerializer<String> serializer = getRedisSerializer();
            Map<String, String> maps = new HashMap<>();
            Set<String> keys = redisTemplate.keys(keyPatten + "*");
            for (String key : keys) {
                byte[] bKeys = serializer.serialize(key);
                byte[] bValues = connection.get(bKeys);
                String value = serializer.deserialize(bValues);
                maps.put(key, value);
            }
            return maps;
        });
    }

    /**
     * Ops for hash hash operations.
     *
     * @return the hash operations
     */
    public HashOperations<String, String, String> opsForHash() {
        return redisTemplate.opsForHash();
    }

    /**
     * 对HashMap操作
     *
     * @param key       the key
     * @param hashKey   the hash key
     * @param hashValue the hash value
     */
    public void putHashValue(String key, String hashKey, String hashValue) {
        LOGGER.info("[redisTemplate redis]  putHashValue()  key={},hashKey={},hashValue={} ", key, hashKey, hashValue);
        opsForHash().put(key, hashKey, hashValue);
    }

    /**
     * 获取单个field对应的值
     *
     * @param key     the key
     * @param hashKey the hash key
     * @return the hash values
     */
    public Object getHashValues(String key, String hashKey) {
        LOGGER.info("[redisTemplate redis]  getHashValues()  key={},hashKey={}", key, hashKey);
        return opsForHash().get(key, hashKey);
    }

    /**
     * 根据key值删除
     *
     * @param key      the key
     * @param hashKeys the hash keys
     */
    public void delHashValues(String key, Object... hashKeys) {
        LOGGER.info("[redisTemplate redis]  delHashValues()  key={}", key);
        opsForHash().delete(key, hashKeys);
    }

    /**
     * key只匹配map
     *
     * @param key the key
     * @return the hash value
     */
    public Map<String, String> getHashValue(String key) {
        LOGGER.info("[redisTemplate redis]  getHashValue()  key={}", key);
        return opsForHash().entries(key);
    }

    /**
     * 批量添加
     *
     * @param key the key
     * @param map the map
     */
    public void putHashValues(String key, Map<String, String> map) {
        opsForHash().putAll(key, map);
    }

    /**
     * 集合数量
     *
     * @return the long
     */
    public long dbSize() {
        return redisTemplate.execute(RedisServerCommands::dbSize);
    }

    /**
     * 清空redis存储的数据
     *
     * @return the string
     */
    public String flushDB() {
        return redisTemplate.execute((RedisCallback<String>) connection -> {
            connection.flushDb();
            return "ok";
        });
    }

    /**
     * 判断某个主键是否存在
     *
     * @param key the key
     * @return the boolean
     */
    public boolean exists(final String key) {
        return redisTemplate.execute((RedisCallback<Boolean>) connection -> connection.exists(key.getBytes(DEFAULT_CHARSET)));
    }


    /**
     * 删除key
     *
     * @param keys the keys
     * @return the long
     */
    public long del(final String... keys) {
        return redisTemplate.execute((RedisCallback<Long>) connection -> {
            long result = 0;
            for (String key : keys) {
                result = connection.del(key.getBytes(DEFAULT_CHARSET));
            }
            return result;
        });
    }

    /**
     * 获取 RedisSerializer
     *
     * @return the redis serializer
     */
    protected RedisSerializer<String> getRedisSerializer() {
        return redisTemplate.getStringSerializer();
    }

    /**
     * 对某个主键对应的值加一,value值必须是全数字的字符串
     * 如果值不是整数，那么返回的一定是错误
     * 如果值是整数，那么返回自增后的结果
     * 如果键不存在，那么就会创建此键，然后按照值为0自增，就是返回1
     * @param key the key
     * @return the long
     */
    public long incr(final String key) {
        return redisTemplate.execute((RedisCallback<Long>) connection -> {
            RedisSerializer<String> redisSerializer = getRedisSerializer();
            return connection.incr(redisSerializer.serialize(key));
        });
    }

    /**
     * 对某个主键对应的值减一，value值必须是全数字的字符串
     * <p>Title: decr</p>
     * <p>Description: </p>
     * @param key
     * @return
     */
    public long decr(final String key) {
        return redisTemplate.execute((RedisCallback<Long>) connection -> {
            RedisSerializer<String> redisSerializer = getRedisSerializer();
            return connection.decr(redisSerializer.serialize(key));
        });
    }

    /**
     * 自增指定的长度
     * @param key
     * @param increment
     * @return
     */
    public long incrBy(final String key, long increment) {
        return redisTemplate.execute((RedisCallback<Long>) connection -> {
            RedisSerializer<String> redisSerializer = getRedisSerializer();
            return connection.incrBy(redisSerializer.serialize(key), increment);
        });
    }

    /**
     * 自减指定的长度
     * @param key
     * @param decrement
     * @return
     */
    public long decrBy(final String key, long decrement) {
        return redisTemplate.execute((RedisCallback<Long>) connection -> {
            RedisSerializer<String> redisSerializer = getRedisSerializer();
            return connection.decrBy(redisSerializer.serialize(key), decrement);
        });
    }

    /**
     * redis List 引擎
     *
     * @return the list operations
     */
    public ListOperations<String, Object> opsForList() {
        return redisTemplate.opsForList();
    }

    /**
     * redis List数据结构 : 将一个或多个值 value 插入到列表 key 的表头
     *
     * @param key   the key
     * @param value the value
     * @return the long
     */
    public Long leftPush(String key, String value) {
        return opsForList().leftPush(key, value);
    }

    /**
     * redis List数据结构 : 移除并返回列表 key 的头元素
     *
     * @param key the key
     * @return the string
     */
    public String leftPop(String key) {
        return (String) opsForList().leftPop(key);
    }

    /**
     * redis List数据结构 :将一个或多个值 value 插入到列表 key 的表尾(最右边)。
     *
     * @param key   the key
     * @param value the value
     * @return the long
     */
    public Long in(String key, String value) {
        return opsForList().rightPush(key, value);
    }

    /**
     * redis List数据结构 : 移除并返回列表 key 的末尾元素
     *
     * @param key the key
     * @return the string
     */
    public String rightPop(String key) {
        return (String) opsForList().rightPop(key);
    }


    /**
     * redis List数据结构 : 返回列表 key 的长度 ; 如果 key 不存在，则 key 被解释为一个空列表，返回 0 ; 如果 key 不是列表类型，返回一个错误。
     *
     * @param key the key
     * @return the long
     */
    public Long length(String key) {
        return opsForList().size(key);
    }


    /**
     * redis List数据结构 : 根据参数 i 的值，移除列表中与参数 value 相等的元素
     *
     * @param key   the key
     * @param i     the
     * @param value the value
     */
    public void remove(String key, long i, String value) {
        opsForList().remove(key, i, value);
    }

    /**
     * redis List数据结构 : 将列表 key 下标为 index 的元素的值设置为 value
     *
     * @param key   the key
     * @param index the index
     * @param value the value
     */
    public void set(String key, long index, String value) {
        opsForList().set(key, index, value);
    }

    /**
     * redis List数据结构 : 返回列表 key 中指定区间内的元素，区间以偏移量 start 和 end 指定。
     *
     * @param key   the key
     * @param start the start
     * @param end   the end
     * @return the list
     */
    public List<String> getList(String key, int start, int end) {
        List<Object> objectList = opsForList().range(key, start, end);
        List<String> result = new ArrayList<>();
        if (!CollectionUtils.isEmpty(objectList)) {
            objectList.forEach(obj -> {
                result.add((String) obj);
            });
        }

        return result;
    }

    /**
     * redis List数据结构 : 批量存储
     *
     * @param key  the key
     * @param list the list
     * @return the long
     */
    public Long leftPushAll(String key, List<String> list) {
        return opsForList().leftPushAll(key, list);
    }

    /**
     * redis List数据结构 : 将值 value 插入到列表 key 当中，位于值 index 之前或之后,默认之后。
     *
     * @param key   the key
     * @param index the index
     * @param value the value
     */
    public void insert(String key, long index, String value) {
        opsForList().set(key, index, value);
    }

    /**
     * 利用redis的单线程原子自增性保证数据自增的唯一性
     *
     * @param key
     * @return
     */
    public RedisAtomicLong getRedisAtomicLong(String key) {
        return new RedisAtomicLong(key, redisConnectionFactory);
    }

    /**
     * ZINCRBY key increment member
     *
     * @param key
     * @param increment
     * @param member
     */
    public void doZincrby(String key, Integer increment, String member) {
        redisTemplate.execute((RedisCallback<Double>) connection -> {
            RedisSerializer<String> redisSerializer = getRedisSerializer();
            return connection.zIncrBy(redisSerializer.serialize(key), increment, redisSerializer.serialize(member));
        });
    }

    /**
     * ZREVRANGE key start stop [WITHSCORES]
     *
     * @return
     */
    public List<String> doZrevrange(String key, Integer start, Integer end) {

        List<String> stringList = new ArrayList<>();
        RedisSerializer<String> redisSerializer = getRedisSerializer();
        Set<byte[]> strBytes = redisTemplate.execute((RedisCallback<Set<byte[]>>) connection -> connection.zRevRange(redisSerializer.serialize(key), start, end));
        Iterator byteIter = strBytes.iterator();
        while (byteIter.hasNext()) {
            stringList.add(redisSerializer.deserialize((byte[]) byteIter.next()));
        }
        return stringList;
    }


    /**
     * scan key*
     * @param pattern
     */
    public Set<String> scan(String pattern) {
        if (StringUtils.isEmpty(pattern)) {
            LOGGER.error("scan key 不能为 * ");
            return new HashSet<>();
        }
        ScanOptions options = ScanOptions.scanOptions()
                //这里指定每次扫描key的数量(很多博客瞎说要指定Integer.MAX_VALUE，这样的话跟        keys有什么区别？)
                .count(100000)
                .match(pattern + "*").build();
        RedisSerializer<String> redisSerializer = (RedisSerializer<String>) redisTemplate.getKeySerializer();
        Cursor cursor = (Cursor) redisTemplate.executeWithStickyConnection(redisConnection -> new ConvertingCursor<>(redisConnection.scan(options), redisSerializer::deserialize));
        Set<String> result = new HashSet<>();
        while(cursor.hasNext()){
            result.add(cursor.next().toString());
        }
        //切记这里一定要关闭，否则会耗尽连接数。报Cannot get Jedis connection; nested exception is redis.clients.jedis.exceptions.JedisException: Could not get a
        cursor.close();

        return result;

    }

    public Set<String> scanV2(String pattern) {
        if (StringUtils.isEmpty(pattern)) {
            LOGGER.error("scan key 不能为 * ");
            return new HashSet<>();
        }
        Set<String> result = new HashSet<>();
        RedisConnection connection = null;
        try {

            connection = redisConnectionFactory.getConnection();
            ScanOptions scanOptions = ScanOptions.scanOptions().count(100000).match(pattern + "*").build();
            Cursor<byte[]> cursors = connection.scan(scanOptions);

            cursors.forEachRemaining(new Consumer<byte[]>() {
                @Override
                public void accept(byte[] bytes) {
                    String key = new String(bytes, StandardCharsets.UTF_8);
                    result.add(key);

                }
            });

        }finally {
            RedisConnectionUtils.releaseConnection(connection, redisConnectionFactory);
        }
        return result;
    }

}
