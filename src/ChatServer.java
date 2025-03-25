import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Servidor de chat que permite la comunicación entre múltiples clientes.
 * Cada mensaje enviado por un cliente es reenviado a todos los demás clientes.
 */
public class ChatServer {
    // Puerto en el que escucha el servidor
    private static final int PORT = 12345;

    // Conjunto para almacenar los handlers de cada cliente conectado
    private static final Set<ClientHandler> clients = new CopyOnWriteArraySet<>();

    // Formato para mostrar la hora en los mensajes del servidor
    private static final SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");

    // Historial de mensajes recientes (últimos 100 mensajes)
    private static final List<String> messageHistory = new ArrayList<>();
    private static final int MAX_HISTORY_SIZE = 100;

    public static void main(String[] args) {
        System.out.println("Iniciando servidor de chat en puerto " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Servidor iniciado correctamente. Esperando conexiones...");

            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Nueva conexión desde " + clientSocket.getInetAddress().getHostAddress());

                    ClientHandler handler = new ClientHandler(clientSocket);
                    handler.start();
                } catch (IOException e) {
                    System.err.println("Error al aceptar conexión: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Error al iniciar servidor: " + e.getMessage());
        }
    }

    /**
     * Distribuye un mensaje a todos los clientes conectados
     */
    private static void broadcast(String message, ClientHandler sender) {
        // Agregar mensaje al historial
        synchronized (messageHistory) {
            messageHistory.add(message);
            if (messageHistory.size() > MAX_HISTORY_SIZE) {
                messageHistory.remove(0);
            }
        }

        // Enviar el mensaje a todos los clientes
        for (ClientHandler client : clients) {
            // Envía el mensaje a todos los clientes (incluido al remitente)
            client.sendMessage(message);
        }
    }

    /**
     * Obtiene una marca de tiempo formateada para los mensajes del servidor
     */
    private static String getTimestamp() {
        return "[" + formatter.format(new Date()) + "]";
    }

    /**
     * Clase que gestiona la comunicación con un cliente específico
     */
    private static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.username = "Anónimo";

            try {
                this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
                this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            } catch (IOException e) {
                System.err.println("Error al crear streams para cliente: " + e.getMessage());
            }
        }

        @Override
        public void run() {
            try {
                String input;

                // Procesar mensajes del cliente
                while ((input = in.readLine()) != null) {
                    // Procesar el mensaje según su tipo
                    if (input.startsWith("USER:")) {
                        // Procesar nuevo usuario
                        String newUsername = extractUsername(input);
                        if (!newUsername.isEmpty()) {
                            String oldUsername = username;
                            username = newUsername;

                            // Agregar a la lista de clientes
                            clients.add(this);

                            // Enviar mensaje de bienvenida e historial
                            sendMessage(getTimestamp() + " Bienvenido, " + username + "!");

                            // Enviar historial de mensajes recientes solo al nuevo usuario
                            synchronized (messageHistory) {
                                for (String historyMessage : messageHistory) {
                                    sendMessage(historyMessage);
                                }
                            }

                            // Notificar a todos los clientes sobre el nuevo usuario
                            broadcast(getTimestamp() + " " + username + " se ha unido al chat.", null);

                            // Enviar lista de usuarios conectados
                            sendUsersList();
                        }
                    } else if (input.startsWith("MSG:")) {
                        // Procesar mensaje regular
                        String content = input.substring(4).trim();
                        if (!content.isEmpty()) {
                            // Comprobamos si es un comando
                            if (content.equals("/help")) {
                                sendHelpInfo();
                            } else if (content.equals("/users")) {
                                sendUsersList();
                            } else {
                                // Mensaje normal para todos
                                broadcast(getTimestamp() + " " + username + ": " + content, this);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Error en la comunicación con el cliente: " + e.getMessage());
            } finally {
                disconnect();
            }
        }

        /**
         * Envía un mensaje directamente a este cliente
         */
        public void sendMessage(String message) {
            if (out != null) {
                out.println(message);
            }
        }

        /**
         * Extrae el nombre de usuario del primer mensaje
         */
        private String extractUsername(String message) {
            String user = message.substring(5).trim();
            return user.length() > 0 && user.length() <= 20 ? user : "Anónimo";
        }

        /**
         * Envía una lista de usuarios conectados actualmente
         */
        private void sendUsersList() {
            StringBuilder userList = new StringBuilder(getTimestamp() + " Usuarios conectados: ");

            for (ClientHandler client : clients) {
                userList.append(client.username).append(", ");
            }

            // Eliminar la última coma y espacio
            String finalList = userList.toString();
            if (clients.size() > 0) {
                finalList = finalList.substring(0, finalList.length() - 2);
            }

            sendMessage(finalList);
        }

        /**
         * Envía información de ayuda sobre comandos disponibles
         */
        private void sendHelpInfo() {
            sendMessage(getTimestamp() + " Comandos disponibles:");
            sendMessage(getTimestamp() + " /users - Muestra la lista de usuarios conectados");
            sendMessage(getTimestamp() + " /help - Muestra esta ayuda");
        }

        /**
         * Gestiona la desconexión del cliente
         */
        private void disconnect() {
            try {
                if (clients.remove(this) && username != null) {
                    broadcast(getTimestamp() + " " + username + " ha abandonado el chat.", null);
                }

                if (out != null) out.close();
                if (in != null) in.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException e) {
                System.err.println("Error al desconectar cliente: " + e.getMessage());
            }
        }
    }
}