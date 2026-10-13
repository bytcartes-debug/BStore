package service.ia;

public class AiRequisicao {

    private final String prompt;
    private final String systemInstruction;
    private final Double temperatura;
    private final Integer maxTokens;

    public AiRequisicao(String prompt) {
        this(prompt, null, 0.2, 1024);
    }

    public AiRequisicao(String prompt, String systemInstruction) {
        this(prompt, systemInstruction, 0.2, 1024);
    }

    public AiRequisicao(String prompt, String systemInstruction, Double temperatura, Integer maxTokens) {
        this.prompt = prompt != null ? prompt : "";
        this.systemInstruction = systemInstruction;
        this.temperatura = temperatura != null ? temperatura : 0.2;
        this.maxTokens = maxTokens != null ? maxTokens : 1024;
    }

    public String getPrompt() { return prompt; }
    public String getSystemInstruction() { return systemInstruction; }
    public Double getTemperatura() { return temperatura; }
    public Integer getMaxTokens() { return maxTokens; }
}
