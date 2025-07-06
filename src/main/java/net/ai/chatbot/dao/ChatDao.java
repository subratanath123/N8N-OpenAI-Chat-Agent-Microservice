package net.ai.chatbot.dao;

import com.mongodb.client.result.UpdateResult;
import net.ai.chatbot.dto.ChatMessage;
import net.ai.chatbot.dto.User;
import net.ai.chatbot.entity.ChatHistory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.*;

import static java.util.Arrays.asList;

@Repository
public class ChatDao {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private UserDao userDao;

    public String getChatHistoryId(String userOneEmail, String userTwoEmail) {
        return getChatHistory(userOneEmail, userTwoEmail).getId();
    }

    public Page<ChatMessage> getLastChatMessages(
            String userOneEmail,
            String userTwoEmail,
            int page,
            int size
    ) {
        // 1. Fetch or create the ChatHistory between the two users
        ChatHistory chatHistory = getChatHistoryWithMessages(userOneEmail, userTwoEmail);

        if (chatHistory == null) {
            return null;
        }

        // 2. Safely get the embedded messages list
        List<ChatMessage> allMessages = Optional.ofNullable(chatHistory.getMessages())
                .orElseGet(ArrayList::new);

        // 3. Sort in-place newest-first
        allMessages.sort(Comparator.comparing(ChatMessage::getCreated).reversed());

        // 4. Compute paging indexes
        int total = allMessages.size();
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, total);

        // 5. Slice out the page (empty if out-of-range)
        List<ChatMessage> pageList = (fromIndex >= total)
                ? Collections.emptyList()
                : allMessages.subList(fromIndex, toIndex);

        // 6. Wrap and return
        return new PageImpl<>(
                pageList,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "created")),
                total
        );
    }

    public ChatHistory getChatHistoryWithMessages(String userOneEmail, String userTwoEmail) {
        String userOneId = userDao.fetchUserId(userOneEmail);
        String userTwoId = userDao.fetchUserId(userTwoEmail);

        Query query = new Query().addCriteria(
                new Criteria().andOperator(
                        Criteria.where("users._id").in(userOneId),
                        Criteria.where("users._id").in(userTwoId)
                )
        );

        // Project only the _id and the entire messages array
        query.fields()
                .include("_id")       // include the document ID
                .include("messages"); // include the full messages array

        return mongoTemplate.findOne(query, ChatHistory.class);
    }

    public ChatHistory getChatHistory(String userOneEmail, String userTwoEmail) {
        String userOneId = userDao.fetchUserId(userOneEmail);
        String userTwoId = userDao.fetchUserId(userTwoEmail);

        Query query = new Query()
                .addCriteria(
                        new Criteria()
                                .andOperator(
                                        Criteria.where("users._id").in(userOneId),  // userOneId exists anywhere in users._id
                                        Criteria.where("users._id").in(userTwoId)   // userTwoId exists anywhere in users._id
                                )
                );

        query.fields().include("id");

        return Optional.ofNullable(mongoTemplate.findOne(query, ChatHistory.class))
                .orElseGet(() -> {
                    ChatHistory chatHistory = ChatHistory.builder()
                            .users(asList(User.builder().id(userOneId).build(), User.builder().id(userTwoId).build()))
                            .build();

                    return mongoTemplate.save(chatHistory);
                });
    }

    public ChatHistory getChatHistoryWithMessages(String userOneEmail, String userTwoEmail, String projectId) {
        String userOneId = userDao.fetchUserId(userOneEmail);
        String userTwoId = userDao.fetchUserId(userTwoEmail);

        Query query = new Query().addCriteria(
                new Criteria().andOperator(
                        Criteria.where("users._id").in(userOneId),
                        Criteria.where("users._id").in(userTwoId),
                        Criteria.where("projectId").is(projectId)
                )
        );
        query.fields()
                .include("_id")
                .include("messages")
                .include("projectId");
        return mongoTemplate.findOne(query, ChatHistory.class);
    }

    public ChatHistory getChatHistory(String userOneEmail, String userTwoEmail, String projectId) {
        String userOneId = userDao.fetchUserId(userOneEmail);
        String userTwoId = userDao.fetchUserId(userTwoEmail);

        Query query = new Query()
                .addCriteria(
                        new Criteria()
                                .andOperator(
                                        Criteria.where("users._id").in(userOneId),
                                        Criteria.where("users._id").in(userTwoId),
                                        Criteria.where("projectId").is(projectId)
                                )
                );
        query.fields().include("id").include("projectId");
        return Optional.ofNullable(mongoTemplate.findOne(query, ChatHistory.class))
                .orElseGet(() -> {
                    ChatHistory chatHistory = ChatHistory.builder()
                            .users(asList(User.builder().id(userOneId).build(), User.builder().id(userTwoId).build()))
                            .projectId(projectId)
                            .build();
                    return mongoTemplate.save(chatHistory);
                });
    }

    public List<ChatHistory> getChatHistoriesByProjectId(String projectId) {
        Query query = new Query(Criteria.where("projectId").is(projectId));
        return mongoTemplate.find(query, ChatHistory.class);
    }

    public ChatHistory getChatHistory(String id) {
        return mongoTemplate.findById(id, ChatHistory.class);
    }

    public UpdateResult saveChatHistory(String chatHistoryId, ChatMessage chatMessage) {
        Query query = new Query(
                Criteria.where("id")
                        .is(chatHistoryId)
        );

        Update update = new Update().addToSet("messages", chatMessage);

        return mongoTemplate.updateFirst(query, update, ChatHistory.class);
    }
}
