package ff.pro.aichatali.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GradingService {

    //      private final ChatClient chatClient;
    @Qualifier("generalChatClient")
    @Autowired
    ChatClient chatClient;

    record GradeResult(String score) {
    } // "yes" or "no"

    public List<Document> filterRelevant(String query, List<Document> docs) {
        return  docs.parallelStream()
                .filter(doc -> isRelevant(query, doc))
                .toList();
    }

    private boolean isRelevant(String query, Document doc) {
        GradeResult result = chatClient.prompt()
                .system("""
                        判断文档是否与问题相关。
                        只输出JSON: {"score": "yes"} 或 {"score": "no"}
                        相关 = 文档包含能回答问题的信息。
                        """)
                .user("问题：" + query + "\n\n文档：" + doc.getText())
                .call()
                .entity(GradeResult.class);

        return result != null && "yes".equals(result.score());
    }
}