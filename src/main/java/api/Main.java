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
        UsuarioDAO dao = new UsuarioDAO();
        if (dao.contarTodos() != 0) {
            return;
        }
        String email = System.getenv("ADMIN_EMAIL");
        String senha = System.getenv("ADMIN_PASSWORD");
        String nome = System.getenv("ADMIN_NOME");
        if (email == null || email.isBlank() || senha == null || senha.isBlank()) {
            throw new IllegalStateException(
                "A primeira execução requer ADMIN_EMAIL e ADMIN_PASSWORD para criar o superuser.");
        }
        Usuario admin = new Usuario(
            nome == null || nome.isBlank() ? "Administrador" : nome,
            email,
            senha,
            "superuser"
        );
        dao.salvar(admin);
        System.out.println("[FlexStock] Superuser inicial criado.");
    }
}
