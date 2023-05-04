package com.group_1.master.service;

import com.group_1.master.dto.QueryFilesInAlbumResponse;
import com.group_1.sharedDynamoDB.exception.NoSuchElementFoundException;
import com.group_1.sharedDynamoDB.model.*;
import com.group_1.sharedDynamoDB.repository.*;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * com.group_1.master.service
 * Created by NhatLinh - 19127652
 * Date 5/2/2023 - 2:14 PM
 * Description: ...
 */
@Service
@AllArgsConstructor
public class AlbumServiceImpl implements AlbumService {

    private final UserAlbumRepository userAlbumRepository;
    private final UserFileRepository userFileRepository;
    private final UserFilesInAlbumRepository userFilesInAlbumRepository;
    private final UserRepository userRepository;
    @Override
    public UserAlbum createAlbum(String userId, String albumName) {
        UserAlbum album = userAlbumRepository.getRecordByKey(DynamoDbRepository.getKey(userId, albumName));
        if (album != null)
            return album;
        userRepository.updateRecord(DynamoDbRepository.getKey(userId), s -> s.setAlbumCount(s.getAlbumCount() + 1));
        return userAlbumRepository.saveRecord(UserAlbum.builder()
                .userId(userId)
                .name(albumName)
                .createdDate(LocalDateTime.now().toString())
                .imgCount(0L)
                .build());
    }

    @Override
    public UserAlbum deleteAlbum(String userId, String albumName) {
        Key albumKey = DynamoDbRepository.getKey(userId, albumName);
        UserAlbum album = userAlbumRepository.getRecordByKey(albumKey, true);
        if (album == null)
            throw new NoSuchElementFoundException(albumName, userId);
        QueryResponse<UserFileInAlbum> albumQueryResponse = userFilesInAlbumRepository
                .query(
                        DynamoDbRepository.getQueryConditional(DynamoDbRepository.getKey(userId, albumName)),
                        null,
                        UserFileInAlbum.Indexes.ALBUM_NAME,
                        (int)album.getImgCount().longValue(),
                        null,
                        false,
                        UserFileInAlbum.Fields.updated);
        for (UserFileInAlbum userFileInAlbum : albumQueryResponse.getResponse())
            userFilesInAlbumRepository.deleteRecordByKey(DynamoDbRepository.getKey(userId, userFileInAlbum.getUpdated()));
        userRepository.updateRecord(DynamoDbRepository.getKey(userId), s -> s.setAlbumCount(s.getAlbumCount() - 1));
        return userAlbumRepository.deleteRecordByKey(albumKey);
    }


    @Override
    public List<UserFileInAlbum> addImagesToAlbum(String userId, String albumName, String... fileNames) {
        Key albumKey = DynamoDbRepository.getKey(userId, albumName);
        UserAlbum album = userAlbumRepository.getRecordByKey(albumKey, true);
        //If the album does not exist -> try to create a new one
        if (album == null)
            createAlbum(userId, albumName);

        List<UserFileInAlbum> resultSet = new ArrayList<>();
        if (fileNames == null || fileNames.length == 0)
            return resultSet;
        final Expression userFilesInAlbumWithAlbumNameEqual = Expression.builder()
                .expression("#a = :name")
                .putExpressionName("#a", UserFileInAlbum.Fields.albumName)
                .putExpressionValue(":name", AttributeValue.fromS(albumName))
                .build();
        LocalDateTime now = LocalDateTime.now();
        for (String fileName : fileNames)
        {
            UserFile userFile = userFileRepository.getRecordByKey(DynamoDbRepository.getKey(userId, fileName));
            if (userFile == null)
                continue;
            UserFileInAlbum userFileInAlbum = userFilesInAlbumRepository.queryOne(
                    DynamoDbRepository.getQueryConditional(DynamoDbRepository.getKey(userId, fileName)),
                    userFilesInAlbumWithAlbumNameEqual,
                    UserFileInAlbum.Indexes.FILE_NAME_INDEX,
                    UserFileInAlbum.Fields.updated, UserFileInAlbum.Fields.albumName);
            //If the image is already inside the album -> ignore it
            if (userFileInAlbum != null)
                continue;
            UserFileInAlbum created = userFilesInAlbumRepository.saveRecord(UserFileInAlbum.builder()
                            .userId(userId)
                            .albumName(albumName)
                            .fileName(fileName)
                            .updated(now.toString())
                    .build());
            resultSet.add(created);
            now = now.plusNanos(1);
        }
        if (!resultSet.isEmpty())
            userAlbumRepository.updateRecord(DynamoDbRepository.getKey(userId, albumName),
                    u -> u.setImgCount(u.getImgCount() + resultSet.size()));
        return resultSet;
    }

    @Override
    public List<UserFileInAlbum> deleteImagesFromAlbum(String userId, String albumName, String... fileNames) {
        List<UserFileInAlbum> resultSet = new ArrayList<>();
        if (fileNames == null || fileNames.length == 0)
            return resultSet;
        final Expression userFilesInAlbumWithAlbumNameEqual = Expression.builder()
                .expression("#a = :name")
                .putExpressionName("#a", UserFileInAlbum.Fields.albumName)
                .putExpressionValue(":name", AttributeValue.fromS(albumName))
                .build();
        for (String fileName : fileNames)
        {
            UserFileInAlbum userFileInAlbum = userFilesInAlbumRepository.queryOne(
                    DynamoDbRepository.getQueryConditional(DynamoDbRepository.getKey(userId, fileName)),
                    userFilesInAlbumWithAlbumNameEqual,
                    UserFileInAlbum.Indexes.FILE_NAME_INDEX,
                    UserFileInAlbum.Fields.updated, UserFileInAlbum.Fields.albumName);
            //Image not on the album
            if (userFileInAlbum == null)
                continue;
            UserFileInAlbum deleted = userFilesInAlbumRepository.deleteRecordByKey(
                    DynamoDbRepository.getKey(userId, userFileInAlbum.getUpdated()));
            resultSet.add(deleted);
        }
        if (!resultSet.isEmpty())
            userAlbumRepository.updateRecord(DynamoDbRepository.getKey(userId, albumName),
                    u -> u.setImgCount(u.getImgCount() - resultSet.size()));
        return resultSet;
    }

    @Override
    public List<UserFileInAlbum> moveImages(String userId, String fromAlbum, String toAlbum, String... fileNames) {
        deleteImagesFromAlbum(userId, fromAlbum, fileNames);
        return addImagesToAlbum(userId, toAlbum, fileNames);
    }

    @Override
    public QueryResponse<UserAlbum> queryAlbums(String userId, int limit, Map<String, AttributeValue> lastEvaluatedKey) {
        return userAlbumRepository.query(
                DynamoDbRepository.getQueryConditional(DynamoDbRepository.getKey(userId)),
                null,
                limit,
                lastEvaluatedKey,
                false
        );
    }

    @Override
    public QueryFilesInAlbumResponse queryImages(String userId, String albumName, int limit, Map<String, AttributeValue> startKey) {
        QueryResponse<UserFileInAlbum> albumQueryResponse = userFilesInAlbumRepository
                .query(DynamoDbRepository.getQueryConditional(DynamoDbRepository.getKey(userId, albumName)),
                        null,
                        UserFileInAlbum.Indexes.ALBUM_NAME,
                        limit,
                        startKey,
                        false);
        List<UserFile> resultSet = new ArrayList<>();
        for (UserFileInAlbum userFileInAlbum : albumQueryResponse.getResponse())
        {
            UserFile userFile = userFileRepository.getRecordByKey(DynamoDbRepository.getKey(userId, userFileInAlbum.getFileName()));
            if (userFile == null)
                continue;
            resultSet.add(userFile);
        }
        return QueryFilesInAlbumResponse.builder()
                .albumName(albumName)
                .prevEvaluatedKey(null)
                .nextEvaluatedKey(albumQueryResponse.getNextEvaluatedKey())
                .response(resultSet).build();
    }
}
