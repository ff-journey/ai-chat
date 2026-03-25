package ff.pro.aichatali.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class QueryRewriter {

    @Autowired
    @Qualifier("generalChatClient")
    ChatClient chatClient;


    // Step-back：把具体问题抽象化，检索更宽泛的背景知识
    public String stepBack(String query) {
        return chatClient.prompt()
                .system("将用户的具体问题改写为更抽象、更通用的背景问题，用于检索相关背景知识。只输出改写后的问题，不要解释。")
                .user(query)
                .call()
                .content();
    }

    // HyDE：生成假设性答案，用答案的向量去检索
    public String hyde(String query) {
        return chatClient.prompt()
                .system("根据问题写一段可能的答案，用于辅助检索。直接写答案内容，不要说'根据'、'可能'等前缀。")
                .user(query)
                .call()
                .content();
    }
}