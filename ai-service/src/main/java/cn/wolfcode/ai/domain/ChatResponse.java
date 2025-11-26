package cn.wolfcode.ai.domain;

public class ChatResponse {
    private String answer;
    private String model;
    private boolean mock;

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public boolean isMock() {
        return mock;
    }

    public void setMock(boolean mock) {
        this.mock = mock;
    }
}

