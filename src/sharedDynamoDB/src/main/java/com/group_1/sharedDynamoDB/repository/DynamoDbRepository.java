package com.group_1.sharedDynamoDB.repository;

import com.group_1.sharedDynamoDB.exception.NoSuchElementFoundException;
import com.group_1.sharedDynamoDB.model.QueryResponse;
import lombok.AllArgsConstructor;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * dynamo.repository
 * Created by NhatLinh - 19127652
 * Date 3/22/2023 - 12:52 PM
 * Description: ...
 */
@AllArgsConstructor
public abstract class DynamoDbRepository<TValue> {
    protected final DynamoDbTable<TValue> table;
    public abstract Key getKeyFromItem(TValue item);
    public Map<String, String> getLastEvaluatedKeyFromItem(TValue item)
    {
        return null;
    }

    public TValue saveRecord(TValue value) {
        PutItemEnhancedResponse<TValue> response = table.putItemWithResponse(b
                -> b.item(value));
        return value;
    }
    public void clearTable()
    {
        for (TValue value : scanAll())
            deleteRecordByKey(getKeyFromItem(value));
    }

    public Iterable<TValue> scanAll()
    {
        return table.scan().items();
    }

    public static QueryConditional getQueryConditional(Key key)
    {
        return QueryConditional.keyEqualTo(key);
    }

    public static Key getKey(String partition, String sort)
    {
        return Key.builder().partitionValue(partition).sortValue(sort).build();
    }
    public static Key getKey(String partition)
    {
        return Key.builder().partitionValue(partition).build();
    }

//    @SneakyThrows
//    public TValue getRecordById(String id) {
//        return getRecordById(id, false);
//    }
//public TValue getRecordByKeyAndAttributes(Key key, boolean consistent)
//{
//    return table
//            .getItem(builder -> builder
//                    .key(key)
//                    .consistentRead(consistent));
//}
//    @SneakyThrows
//    public TValue getRecordById(String id, boolean consistent) {
//        Key key = Key.builder().partitionValue(id).build();
//        return getRecordByKey(key, consistent);
//    }
//    public TValue deleteRecordById(String id) {
//        Key key = Key.builder().partitionValue(id).build();
//        return table.deleteItem(b -> b.key(key));
//    }
//    public TValue updateRecord(String id, Consumer<TValue> updateCallback)
//    {
//        TValue item = getRecordById(id, true);
//        if (item == null)
//            throw new NoSuchElementFoundException(id, table.tableName());
//        updateCallback.accept(item);
//        return updateRecord(item);
//    }

    public TValue getRecordByKey(Key key, boolean consistent)
    {
        return table.getItem(builder -> builder.key(key).consistentRead(consistent));
    }
    public TValue getRecordByKey(Key key)
    {
        return getRecordByKey(key, true);
    }

    public TValue deleteRecordByKey(Key key) {
        return table.deleteItem(b -> b.key(key));
    }

    public TValue updateRecord(Key key, Consumer<TValue> updateCallback)
    {
        TValue item = getRecordByKey(key, true);
        if (item == null)
            throw new NoSuchElementFoundException(key.toString(), table.tableName());
        updateCallback.accept(item);
        return updateRecord(item);
    }


    private TValue updateRecord(TValue item)
    {
        UpdateItemEnhancedResponse<TValue> response =  table.updateItemWithResponse(b -> b.item(item));
        return response.attributes();
    }
    public QueryResponse<TValue> query(QueryConditional queryConditional,
                                       Expression filterExpression,
                                       String index,
                                       int limit,
                                       Map<String, AttributeValue> exclusiveStartKey,
                                       boolean forward,
                                       String... attributes) {
        DynamoDbIndex<TValue> indexTable = table.index(index);
        SdkIterable<Page<TValue>> response = indexTable.query(getQueryBuilder(queryConditional, filterExpression,
                limit, exclusiveStartKey, forward, attributes));
        return proceedResponse(limit, response.iterator());
    }

    public TValue queryOne(QueryConditional queryConditional,
                                       String index, String... attributes) {
        return queryOne(queryConditional, null, index, attributes);
    }

    public TValue queryOne(QueryConditional queryConditional,
                           Expression filterExpression,
                           String index, String... attributes) {
        DynamoDbIndex<TValue> indexTable = table.index(index);
        SdkIterable<Page<TValue>> response = indexTable.query(getQueryBuilder(queryConditional, filterExpression,
                1, null, false, attributes));
        List<TValue> items = response.iterator().next().items();
        if (items != null && !items.isEmpty())
            return items.get(0);
        return null;
    }

    public QueryResponse<TValue> query(QueryConditional queryConditional,
                                       Expression filterExpression,
                                       int limit,
                                       Map<String, AttributeValue> exclusiveStartKey,
                                       boolean forward,
                                       String... attributes) {
        PageIterable<TValue> response = table.query(getQueryBuilder(queryConditional, filterExpression,
                limit, exclusiveStartKey, forward, attributes));
        return proceedResponse(limit, response.iterator());
    }

    private static Consumer<QueryEnhancedRequest.Builder> getQueryBuilder(QueryConditional queryConditional,
                                                                          Expression filterExpression,
                                                                          int limit,
                                                                          Map<String, AttributeValue> exclusiveStartKey,
                                                                          boolean forward,
                                                                          String... attributes) {
        return b -> {
            b
                    .queryConditional(queryConditional)
                    //.filterExpression(filterExpression)
                    .limit(limit)
                    .exclusiveStartKey(exclusiveStartKey);
            if (attributes != null && attributes.length > 0)
                b.attributesToProject(attributes);
            if (filterExpression != null)
                b.filterExpression(filterExpression);
            b.scanIndexForward(forward);
        };
    }

    private QueryResponse<TValue> proceedResponse(int limit, Iterator<Page<TValue>> response)
    {
        Page<TValue> next = response.next();
        List<TValue> items = next.items();
        Map<String, String> lastKey = null;
        if (items != null
                && !items.isEmpty()
                && items.size() >= limit)
        {
            lastKey = getLastEvaluatedKeyFromItem(items.get(limit - 1));
        }
        return QueryResponse.<TValue>builder()
                .response(next.items())
                .nextEvaluatedKey(lastKey)
                .build();
    }
}
