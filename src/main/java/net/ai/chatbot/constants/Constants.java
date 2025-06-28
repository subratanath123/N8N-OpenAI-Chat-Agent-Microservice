package net.ai.chatbot.constants;

public class Constants {
    public static final String TRAIN_WEBSITE_EVENT_STREAM = "train-website-event";

    //Have to make this dybamic so that multiple application get a unique stream consumer group
    public static final String REDIS_STREAM_SERVER_GROUP = "redis-stream-server-group-1";

}
