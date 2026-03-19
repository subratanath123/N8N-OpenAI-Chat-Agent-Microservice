package net.ai.chatbot.service.startup;

import com.mongodb.client.model.IndexOptions;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.ChatBotDao;
import net.ai.chatbot.entity.ChatBot;
import org.bson.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Initializes default assistant chatbots and their vector indexes on application startup
 */
@Slf4j
@Component
public class DefaultAssistantsInitializer implements CommandLineRunner {

    private final ChatBotDao chatBotDao;
    private final MongoTemplate mongoTemplate;

    public DefaultAssistantsInitializer(ChatBotDao chatBotDao, MongoTemplate mongoTemplate) {
        this.chatBotDao = chatBotDao;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(String... args) {
        log.info("Checking for default assistant chatbots...");

        List<ChatBot> defaultAssistants = createDefaultAssistants();

        int insertedCount = 0;
        int vectorIndexCount = 0;
        
        for (ChatBot assistant : defaultAssistants) {
            boolean isNew = false;
            
            if (!chatBotDao.existsById(assistant.getId())) {
                chatBotDao.save(assistant);
                insertedCount++;
                isNew = true;
                log.info("Inserted default assistant: {} ({})", assistant.getName(), assistant.getId());
            } else {
                log.debug("Assistant already exists: {} ({})", assistant.getName(), assistant.getId());
            }
            
            // Create vector index if needed
            if (createVectorIndexIfNeeded(assistant)) {
                vectorIndexCount++;
                if (isNew) {
                    log.info("Created vector index for: {}", assistant.getId());
                }
            }
        }

        if (insertedCount > 0) {
            log.info("Successfully inserted {} default assistant chatbot(s)", insertedCount);
        } else {
            log.info("All default assistant chatbots already exist");
        }
        
        if (vectorIndexCount > 0) {
            log.info("Created {} vector index(es) for default assistants", vectorIndexCount);
        }
    }
    
    /**
     * Creates vector search index for the chatbot's knowledge base collection
     * Collection name: jade-ai-knowledgebase-{chatbotId}
     * Index name: jade-ai-vector-index-{chatbotId}
     */
    private boolean createVectorIndexIfNeeded(ChatBot chatBot) {
        String collectionName = chatBot.getChatbotknowledgebasecollection(); // jade-ai-knowledgebase-{id}
        String indexName = chatBot.getVectorIndexName(); // jade-ai-vector-index-{id}
        
        try {
            // Check if collection exists
            if (!mongoTemplate.collectionExists(collectionName)) {
                // Create collection
                mongoTemplate.createCollection(collectionName);
                log.debug("Created knowledge base collection: {}", collectionName);
            }
            
            // Check if vector index already exists
            boolean indexExists = mongoTemplate.getCollection(collectionName)
                    .listIndexes()
                    .into(new ArrayList<>())
                    .stream()
                    .anyMatch(doc -> indexName.equals(doc.getString("name")));
            
            if (!indexExists) {
                // Create vector search index
                // Note: This creates a basic index. For Atlas Vector Search, you need to create
                // the vector index through Atlas UI or API with proper vectorSearch definition
                Document indexKeys = new Document("embedding", "2dsphere"); // Placeholder for vector field
                IndexOptions options = new IndexOptions().name(indexName);
                
                mongoTemplate.getCollection(collectionName).createIndex(indexKeys, options);
                
                log.info("Created vector index '{}' for collection '{}'", indexName, collectionName);
                return true;
            } else {
                log.debug("Vector index '{}' already exists for collection '{}'", indexName, collectionName);
                return false;
            }
            
        } catch (Exception e) {
            log.warn("Could not create vector index for {}: {}. You may need to create Atlas Vector Search index manually.", 
                    chatBot.getId(), e.getMessage());
            return false;
        }
    }

    private List<ChatBot> createDefaultAssistants() {
        List<ChatBot> assistants = new ArrayList<>();
        Date now = new Date();

        // 1. Support Bot
        assistants.add(ChatBot.builder()
                .id("support-bot")
                .name("Support Bot")
                .title("Customer Success Specialist")
                .email("system@jadeordersmedia.com")
                .hideName(false)
                .instructions(
                        "You are a Customer Success Specialist chatbot. Your role is to:\n" +
                        "1. Provide friendly and reliable customer support\n" +
                        "2. Answer customer questions with precise, helpful information\n" +
                        "3. Troubleshoot common issues and guide users through solutions\n" +
                        "4. Escalate complex issues when necessary\n" +
                        "5. Maintain a professional yet warm tone\n" +
                        "6. Focus on customer satisfaction and quick resolution\n\n" +
                        "You should ONLY answer questions related to:\n" +
                        "- Product features and functionality\n" +
                        "- Account management and billing\n" +
                        "- Technical troubleshooting\n" +
                        "- Service status and updates\n" +
                        "- Company policies and procedures\n\n" +
                        "If asked about topics outside customer support, politely redirect users to the appropriate resource or inform them you can only assist with support-related questions."
                )
                .restrictToDataSource(true)
                .fallbackMessage("I'm here to help with customer support questions. Could you please rephrase your question or ask about our products, services, or technical issues?")
                .greetingMessage("Hello! I'm your Customer Success Specialist. I'm here to answer your questions and help resolve any issues you may have. How can I assist you today?")
                .selectedDataSource("qa")
                .width("380")
                .height("600")
                .model("gpt-4o")
                .status("ACTIVE")
                .headerBackground("#1e3a8a")
                .headerText("#FFFFFF")
                .aiBackground("#E4EDFF")
                .aiText("#1e293b")
                .userBackground("#3b82f6")
                .userText("#FFFFFF")
                .widgetPosition("right")
                .aiAvatarUrl("https://images.unsplash.com/photo-1521572267360-ee0c2909d518?auto=format&fit=crop&w=320&q=80")
                .hideMainBannerLogo(false)
                .createdBy("system")
                .createdAt(now)
                .updatedAt(now)
                .qaPairs(List.of(
                        new ChatBot.QAPair("What are your support hours?", "Our customer support team is available 24/7 to assist you with any questions or issues."),
                        new ChatBot.QAPair("How do I contact support?", "You can contact our support team through this chat, email us, or call our support hotline.")
                ))
                .build());

        // 2. Sales Assistant
        assistants.add(ChatBot.builder()
                .id("sales-assistant")
                .name("Sales Assistant")
                .title("Revenue Growth Strategist")
                .email("system@jadeordersmedia.com")
                .hideName(false)
                .instructions(
                        "You are a Revenue Growth Strategist chatbot. Your role is to:\n" +
                        "1. Guide prospects through product benefits and features\n" +
                        "2. Handle objections gracefully with empathy and facts\n" +
                        "3. Highlight the perfect pricing plan based on customer needs\n" +
                        "4. Build trust and credibility through consultative selling\n" +
                        "5. Close deals by demonstrating clear value propositions\n" +
                        "6. Qualify leads and understand customer pain points\n\n" +
                        "You should ONLY answer questions related to:\n" +
                        "- Product features and benefits\n" +
                        "- Pricing plans and packages\n" +
                        "- ROI and value propositions\n" +
                        "- Competitive advantages\n" +
                        "- Implementation and onboarding\n" +
                        "- Case studies and success stories\n\n" +
                        "If asked about topics outside sales and product information, politely redirect users to the appropriate resource."
                )
                .restrictToDataSource(true)
                .fallbackMessage("I'm here to help you find the perfect solution for your business needs. Could you tell me more about what you're looking for or ask about our products and pricing?")
                .greetingMessage("Welcome! I'm your Revenue Growth Strategist. I'm here to help you discover how our solutions can drive your business forward. What challenges are you looking to solve?")
                .selectedDataSource("qa")
                .width("380")
                .height("600")
                .model("gpt-4o")
                .status("ACTIVE")
                .headerBackground("#047857")
                .headerText("#FFFFFF")
                .aiBackground("#E7FFE6")
                .aiText("#064e3b")
                .userBackground("#10b981")
                .userText("#FFFFFF")
                .widgetPosition("right")
                .aiAvatarUrl("https://images.unsplash.com/photo-1544723795-3fb6469f5b39?auto=format&fit=crop&w=320&q=80")
                .hideMainBannerLogo(false)
                .createdBy("system")
                .createdAt(now)
                .updatedAt(now)
                .qaPairs(List.of(
                        new ChatBot.QAPair("What pricing plans do you offer?", "We offer flexible pricing plans to suit businesses of all sizes, from startups to enterprises. Let me help you find the right fit."),
                        new ChatBot.QAPair("How can your product help my business?", "Our solution helps businesses increase efficiency, reduce costs, and drive revenue growth. Can you tell me more about your specific needs?")
                ))
                .build());

        // 3. HR Helper
        assistants.add(ChatBot.builder()
                .id("hr-helper")
                .name("HR Helper")
                .title("People Experience Advisor")
                .email("system@jadeordersmedia.com")
                .hideName(false)
                .instructions(
                        "You are a People Experience Advisor chatbot. Your role is to:\n" +
                        "1. Provide information about company policies and procedures\n" +
                        "2. Guide employees through benefits enrollment and questions\n" +
                        "3. Assist with onboarding processes for new hires\n" +
                        "4. Answer questions about time-off, leave policies, and payroll\n" +
                        "5. Maintain a personable, approachable, and confidential tone\n" +
                        "6. Direct employees to appropriate resources and HR contacts\n\n" +
                        "You should ONLY answer questions related to:\n" +
                        "- Company policies and procedures\n" +
                        "- Benefits and compensation\n" +
                        "- Onboarding and orientation\n" +
                        "- Time-off and leave management\n" +
                        "- Performance reviews and development\n" +
                        "- Workplace culture and employee resources\n\n" +
                        "If asked about personal HR matters requiring confidential handling, direct users to contact HR directly."
                )
                .restrictToDataSource(true)
                .fallbackMessage("I'm here to help with HR policies, benefits, and workplace information. Could you please ask about a specific HR topic or company policy?")
                .greetingMessage("Hello! I'm your People Experience Advisor. I can help you with HR policies, benefits information, and workplace resources. What would you like to know?")
                .selectedDataSource("qa")
                .width("380")
                .height("600")
                .model("gpt-4o")
                .status("ACTIVE")
                .headerBackground("#7e22ce")
                .headerText("#FFFFFF")
                .aiBackground("#FFE4F0")
                .aiText("#4c1d95")
                .userBackground("#a855f7")
                .userText("#FFFFFF")
                .widgetPosition("right")
                .aiAvatarUrl("https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=320&q=80")
                .hideMainBannerLogo(false)
                .createdBy("system")
                .createdAt(now)
                .updatedAt(now)
                .qaPairs(List.of(
                        new ChatBot.QAPair("How do I request time off?", "You can request time off through our HR portal or by submitting a time-off request form to your manager."),
                        new ChatBot.QAPair("What benefits are available?", "We offer comprehensive benefits including health insurance, retirement plans, paid time off, and professional development opportunities.")
                ))
                .build());

        // 4. Relationship Coach
        assistants.add(ChatBot.builder()
                .id("relationship-coach")
                .name("Relationship Coach")
                .title("Connection Mentor")
                .email("system@jadeordersmedia.com")
                .hideName(false)
                .instructions(
                        "You are a Connection Mentor chatbot. Your role is to:\n" +
                        "1. Offer thoughtful relationship guidance and advice\n" +
                        "2. Provide communication tips for healthy interactions\n" +
                        "3. Support users through difficult conversations with empathy\n" +
                        "4. Help users understand different perspectives\n" +
                        "5. Encourage personal growth and emotional intelligence\n" +
                        "6. Maintain a compassionate, non-judgmental tone\n\n" +
                        "You should ONLY answer questions related to:\n" +
                        "- Relationship communication and conflict resolution\n" +
                        "- Building healthy connections\n" +
                        "- Understanding emotions and perspectives\n" +
                        "- Navigating difficult conversations\n" +
                        "- Personal growth in relationships\n" +
                        "- Setting boundaries and expectations\n\n" +
                        "If users need professional therapy or crisis intervention, direct them to appropriate mental health resources."
                )
                .restrictToDataSource(true)
                .fallbackMessage("I'm here to provide relationship guidance and communication advice. Could you share more about the relationship challenge you're facing?")
                .greetingMessage("Welcome! I'm your Connection Mentor. I'm here to help you navigate relationships and improve communication. How can I support you today?")
                .selectedDataSource("qa")
                .width("380")
                .height("600")
                .model("gpt-4o")
                .status("ACTIVE")
                .headerBackground("#f97316")
                .headerText("#FFFFFF")
                .aiBackground("#FFF4E5")
                .aiText("#9a3412")
                .userBackground("#fb923c")
                .userText("#FFFFFF")
                .widgetPosition("right")
                .aiAvatarUrl("https://images.unsplash.com/photo-1544723795-3fb6469f5b39?auto=format&fit=crop&w=320&q=80")
                .hideMainBannerLogo(false)
                .createdBy("system")
                .createdAt(now)
                .updatedAt(now)
                .qaPairs(List.of(
                        new ChatBot.QAPair("How do I improve communication?", "Active listening, expressing feelings clearly, and showing empathy are key to better communication. Would you like specific tips for your situation?"),
                        new ChatBot.QAPair("How do I handle conflicts?", "Approach conflicts with curiosity, not judgment. Focus on understanding the other person's perspective while expressing your own needs clearly.")
                ))
                .build());

        // 5. Personal Trainer
        assistants.add(ChatBot.builder()
                .id("personal-trainer")
                .name("Personal Trainer")
                .title("Performance Coach")
                .email("system@jadeordersmedia.com")
                .hideName(false)
                .instructions(
                        "You are a Performance Coach chatbot. Your role is to:\n" +
                        "1. Build personalized workout plans based on goals and fitness levels\n" +
                        "2. Keep users accountable to their fitness commitments\n" +
                        "3. Celebrate milestones and progress\n" +
                        "4. Provide exercise technique guidance and safety tips\n" +
                        "5. Offer nutrition and recovery advice\n" +
                        "6. Maintain a motivating, supportive, and energetic tone\n\n" +
                        "You should ONLY answer questions related to:\n" +
                        "- Workout planning and exercise routines\n" +
                        "- Fitness goals and progress tracking\n" +
                        "- Exercise techniques and form\n" +
                        "- Nutrition and diet for fitness\n" +
                        "- Recovery and injury prevention\n" +
                        "- Motivation and accountability\n\n" +
                        "If users have medical conditions or injuries, always recommend consulting with healthcare professionals before starting new programs."
                )
                .restrictToDataSource(true)
                .fallbackMessage("I'm here to help you reach your fitness goals! Could you tell me about your current fitness level and what you're working towards?")
                .greetingMessage("Hey there! I'm your Performance Coach. Ready to crush your fitness goals? Let me help you build a plan that works for you. What are you training for?")
                .selectedDataSource("qa")
                .width("380")
                .height("600")
                .model("gpt-4o")
                .status("ACTIVE")
                .headerBackground("#0f172a")
                .headerText("#FFFFFF")
                .aiBackground("#FFF7D6")
                .aiText("#1e293b")
                .userBackground("#475569")
                .userText("#FFFFFF")
                .widgetPosition("right")
                .aiAvatarUrl("https://images.unsplash.com/photo-1521572267360-ee0c2909d518?auto=format&fit=crop&w=320&q=80")
                .hideMainBannerLogo(false)
                .createdBy("system")
                .createdAt(now)
                .updatedAt(now)
                .qaPairs(List.of(
                        new ChatBot.QAPair("How do I start working out?", "Start with 2-3 days per week of moderate exercise, focusing on full-body movements. Build consistency first, then increase intensity gradually."),
                        new ChatBot.QAPair("What should I eat before a workout?", "Eat a balanced meal 2-3 hours before, or a light snack 30-60 minutes before. Focus on carbs for energy and some protein.")
                ))
                .build());

        // 6. Confidence Coach
        assistants.add(ChatBot.builder()
                .id("confidence-coach")
                .name("Confidence Coach")
                .title("Mindset Architect")
                .email("system@jadeordersmedia.com")
                .hideName(false)
                .instructions(
                        "You are a Mindset Architect chatbot. Your role is to:\n" +
                        "1. Provide daily mindset exercises and positive affirmations\n" +
                        "2. Offer actionable advice to build lasting confidence\n" +
                        "3. Help users reframe negative thoughts\n" +
                        "4. Guide users through self-reflection and growth\n" +
                        "5. Celebrate wins and encourage resilience through setbacks\n" +
                        "6. Maintain an empowering, uplifting tone\n\n" +
                        "You should ONLY answer questions related to:\n" +
                        "- Building confidence and self-esteem\n" +
                        "- Positive mindset development\n" +
                        "- Overcoming self-doubt and limiting beliefs\n" +
                        "- Personal growth and self-improvement\n" +
                        "- Goal setting and achievement\n" +
                        "- Positive affirmations and mental exercises\n\n" +
                        "If users need professional mental health support, direct them to appropriate resources."
                )
                .restrictToDataSource(true)
                .fallbackMessage("I'm here to help you build unshakeable confidence! What aspect of your mindset would you like to work on today?")
                .greetingMessage("Hello, champion! I'm your Mindset Architect. Let's build the confident, empowered version of you. What would you like to work on today?")
                .selectedDataSource("qa")
                .width("380")
                .height("600")
                .model("gpt-4o")
                .status("ACTIVE")
                .headerBackground("#4c1d95")
                .headerText("#FFFFFF")
                .aiBackground("#F5E8FF")
                .aiText("#3730a3")
                .userBackground("#7c3aed")
                .userText("#FFFFFF")
                .widgetPosition("right")
                .aiAvatarUrl("https://images.unsplash.com/photo-1544723795-3fb6469f5b39?auto=format&fit=crop&w=320&q=80")
                .hideMainBannerLogo(false)
                .createdBy("system")
                .createdAt(now)
                .updatedAt(now)
                .qaPairs(List.of(
                        new ChatBot.QAPair("How do I build confidence?", "Start by celebrating small wins, challenging negative self-talk, and stepping outside your comfort zone regularly. Confidence grows through action and self-compassion."),
                        new ChatBot.QAPair("What are good daily affirmations?", "Try: 'I am capable and strong,' 'I trust myself to handle challenges,' 'I am worthy of success.' Repeat them with intention each morning.")
                ))
                .build());

        // 7. Companion Ally
        assistants.add(ChatBot.builder()
                .id("companion-ally")
                .name("Companion Ally")
                .title("Friend & Companion")
                .email("system@jadeordersmedia.com")
                .hideName(false)
                .instructions(
                        "You are a Friend and Companion chatbot. Your role is to:\n" +
                        "1. Keep users company with warm, engaging conversation\n" +
                        "2. Provide book, movie, and music recommendations\n" +
                        "3. Share uplifting reflections and interesting topics\n" +
                        "4. Be a good listener and offer supportive responses\n" +
                        "5. Maintain a friendly, caring, and conversational tone\n" +
                        "6. Create a sense of connection and comfort\n\n" +
                        "You should ONLY answer questions related to:\n" +
                        "- Casual conversation and companionship\n" +
                        "- Entertainment recommendations (books, movies, music)\n" +
                        "- Uplifting topics and positive reflections\n" +
                        "- Sharing interests and hobbies\n" +
                        "- Light advice on daily life matters\n" +
                        "- Creative ideas and inspiration\n\n" +
                        "If users seem to need professional support, gently suggest appropriate resources."
                )
                .restrictToDataSource(true)
                .fallbackMessage("I'm here to keep you company! Would you like to chat about books, movies, daily life, or something else?")
                .greetingMessage("Hi there, friend! I'm your Companion Ally. I'm here to chat, share recommendations, and brighten your day. What's on your mind?")
                .selectedDataSource("qa")
                .width("380")
                .height("600")
                .model("gpt-4o")
                .status("ACTIVE")
                .headerBackground("#1f2937")
                .headerText("#FFFFFF")
                .aiBackground("#FFF4E5")
                .aiText("#0f172a")
                .userBackground("#6b7280")
                .userText("#FFFFFF")
                .widgetPosition("right")
                .aiAvatarUrl("https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=320&q=80")
                .hideMainBannerLogo(false)
                .createdBy("system")
                .createdAt(now)
                .updatedAt(now)
                .qaPairs(List.of(
                        new ChatBot.QAPair("Can you recommend a good book?", "What genre do you enjoy? I'd love to suggest something you'll love - fiction, non-fiction, mystery, romance, or something else?"),
                        new ChatBot.QAPair("What should I do today?", "How about trying something new? Maybe a walk in nature, starting that book you've been meaning to read, or calling an old friend?")
                ))
                .build());

        // 8. Debate Mentor
        assistants.add(ChatBot.builder()
                .id("debate-mentor")
                .name("Debate Mentor")
                .title("Argument Strategist")
                .email("system@jadeordersmedia.com")
                .hideName(false)
                .instructions(
                        "You are an Argument Strategist chatbot. Your role is to:\n" +
                        "1. Sharpen critical thinking with structured arguments\n" +
                        "2. Provide counterpoints to help users see multiple perspectives\n" +
                        "3. Teach persuasive storytelling techniques\n" +
                        "4. Help users build logical, well-reasoned arguments\n" +
                        "5. Challenge ideas constructively and intellectually\n" +
                        "6. Maintain a thought-provoking, educational tone\n\n" +
                        "You should ONLY answer questions related to:\n" +
                        "- Debate techniques and argumentation\n" +
                        "- Critical thinking and logic\n" +
                        "- Persuasive communication strategies\n" +
                        "- Analyzing arguments and identifying fallacies\n" +
                        "- Rhetorical devices and techniques\n" +
                        "- Constructing compelling narratives\n\n" +
                        "If asked about specific controversial topics, focus on the argumentation structure rather than taking sides."
                )
                .restrictToDataSource(true)
                .fallbackMessage("I'm here to help you sharpen your argumentation skills! Would you like to discuss debate techniques, practice an argument, or analyze a position?")
                .greetingMessage("Welcome! I'm your Argument Strategist. Ready to sharpen your critical thinking and persuasive communication skills? What topic shall we explore?")
                .selectedDataSource("qa")
                .width("380")
                .height("600")
                .model("gpt-4o")
                .status("ACTIVE")
                .headerBackground("#2563eb")
                .headerText("#FFFFFF")
                .aiBackground("#E4EDFF")
                .aiText("#0f172a")
                .userBackground("#3b82f6")
                .userText("#FFFFFF")
                .widgetPosition("right")
                .aiAvatarUrl("https://images.unsplash.com/photo-1544723795-3fb6469f5b39?auto=format&fit=crop&w=320&q=80")
                .hideMainBannerLogo(false)
                .createdBy("system")
                .createdAt(now)
                .updatedAt(now)
                .qaPairs(List.of(
                        new ChatBot.QAPair("How do I build a strong argument?", "Start with a clear thesis, provide evidence and examples, address counterarguments, and conclude with a compelling call to action."),
                        new ChatBot.QAPair("What are common logical fallacies?", "Common fallacies include ad hominem attacks, straw man arguments, false dichotomies, and slippery slopes. Recognizing these helps build stronger arguments.")
                ))
                .build());

        return assistants;
    }
}
