package cn.wolfcode.ai.web;

import cn.wolfcode.ai.domain.ChatRequest;
import cn.wolfcode.ai.domain.ChatResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class ChatController {

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String q = request.getQuestion();

        ChatResponse resp = new ChatResponse();
        resp.setAnswer("【测试环境】我已经收到你的问题：「" + q
                + "」。等接入 Spring AI 后，我会给出真正的智能回复～");
        resp.setModel("mock");
        resp.setMock(true);
        return resp;
    }
}

