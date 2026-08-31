package api;

import util.JPAUtil;

public class Main {

    public static void main(String[] args) {
        // Inicializa a base de dados
        JPAUtil.inicializar();

        // Lê a porta do ambiente (Railway define a variável PORT automaticamente)
        String portEnv = System.getenv("PORT");
        int port = (portEnv != null && !portEnv.isEmpty()) ? Integer.parseInt(portEnv) : 8080;

        System.out.println("[FlexStock] Iniciando servidor na porta " + port + "...");

        // Inicia o servidor Javalin
        ApiServer server = new ApiServer();
        server.start(port);

        // Fecha JPA ao encerrar
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            JPAUtil.fechar();
            System.out.println("[FlexStock] Servidor encerrado.");
        }));
    }
}
