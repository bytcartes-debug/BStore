package api;

import dao.UsuarioDAO;
import model.Usuario;
import util.JPAUtil;

public class Main {

    public static void main(String[] args) {
        // Inicializa a base de dados
        JPAUtil.inicializar();

        // Garante que existe pelo menos um superuser no sistema
        semearSuperuser();

        // Lê a porta do ambiente (Render define PORT automaticamente)
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

    private static void semearSuperuser() {
        try {
            UsuarioDAO dao = new UsuarioDAO();
            if (dao.contarTodos() == 0) {
                // Lê credenciais de variáveis de ambiente ou usa padrão
                String email = System.getenv("ADMIN_EMAIL") != null
                    ? System.getenv("ADMIN_EMAIL") : "admin@flexstock.com";
                String senha = System.getenv("ADMIN_PASSWORD") != null
                    ? System.getenv("ADMIN_PASSWORD") : "admin123";
                String nome  = System.getenv("ADMIN_NOME") != null
                    ? System.getenv("ADMIN_NOME") : "Administrador";

                Usuario admin = new Usuario(nome, email, senha, "superuser");
                dao.salvar(admin);
                System.out.println("[FlexStock] Superuser criado: " + email + " / " + senha);
            }
        } catch (Exception e) {
            System.err.println("[FlexStock] Aviso: não foi possível semear superuser: " + e.getMessage());
        }
    }
}
