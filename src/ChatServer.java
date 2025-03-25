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

    // Conjunto para almacenar los escritores de cada cliente conectado
    private static final Set<ClientHandler> clients = new CopyOnWriteArraySet<>();

    // Formato para mostrar la hora en los mensajes del servidor
    private static final SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");

    // Historial de mensajes recientes (últimos 100 mensajes)
    private static final List<String> messageHistory = new ArrayList<>();
    private static final int MAX_HISTORY_SIZE = 100;

    public static void main(String[] args) {
        System.out.println(getTimestamp() + " Iniciando servidor de chat en el puerto " + PORT);
        System.out.println(getTimestamp() + " Esperando conexiones...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            // Ciclo principal del servidor
            while (true) {
                // Esperar a que se conecte un cliente
                Socket clientSocket = serverSocket.accept();

                // Crear un manejador para el nuevo cliente en un hilo separado
                ClientHandler handler = new ClientHandler(clientSocket);
                clients.add(handler);
                handler.start();

                // Notificar que se conectó un nuevo cliente
                System.out.println(getTimestamp() + " Nuevo cliente conectado: " +
                        clientSocket.getInetAddress().getHostAddress());
            }
        } catch (IOException e) {
            System.err.println(getTimestamp() + " Error en el servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Distribuye un mensaje a todos los clientes conectados
     */
    private static void broadcast(String message, ClientHandler sender) {
        // Agregar el mensaje al historial
        synchronized (messageHistory) {
            messageHistory.add(message);
            if (messageHistory.size() > MAX_HISTORY_SIZE) {
                messageHistory.remove(0); // Eliminar el mensaje más antiguo
            }
        }

        // Enviar mensaje a todos los clientes
        System.out.println(getTimestamp() + " " + message);

        for (ClientHandler client : clients) {
            // Opcional: si no queremos enviar el mensaje al remitente, podemos añadir una verificación
            // if (client != sender) {
            client.sendMessage(message);
            // }
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
        }

        @Override
        public void run() {
            try {
                // Configurar los flujos de entrada y salida
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Esperar a que el cliente envíe su nombre de usuario
                username = extractUsername(in.readLine());

                // Enviar mensaje de bienvenida
                sendMessage("¡Bienvenido al chat, " + username + "!");

                // Enviar el historial de mensajes recientes
                synchronized (messageHistory) {
                    if (!messageHistory.isEmpty()) {
                        sendMessage("--- Últimos mensajes ---");
                        for (String msg : messageHistory) {
                            sendMessage(msg);
                        }
                        sendMessage("--- Fin del historial ---");
                    }
                }

                // Anunciar que el usuario se ha unido
                broadcast(username + " se ha unido al chat.", this);

                // Ciclo principal para procesar mensajes
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    // Verificar si es un comando especial
                    if (inputLine.equalsIgnoreCase("/quit")) {
                        break;
                    } else if (inputLine.startsWith("/usuarios")) {
                        // Comando para listar usuarios conectados
                        sendUsersList();
                    } else if (inputLine.startsWith("/ayuda")) {
                        // Comando de ayuda
                        sendHelpInfo();
                    } else {
                        // Enviar mensaje a todos los clientes
                        broadcast(inputLine, this);
                    }
                }
            } catch (IOException e) {
                System.err.println(getTimestamp() + " Error con cliente " + username + ": " + e.getMessage());
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
            // Formato esperado: "Nombre: mensaje" o simplemente "Nombre se ha unido al chat!"
            if (message == null || message.isEmpty()) {
                return "Usuario" + socket.getPort();
            }

            if (message.contains(":")) {
                return message.substring(0, message.indexOf(":")).trim();
            } else if (message.contains(" se ha unido al chat")) {
                return message.substring(0, message.indexOf(" se ha unido al chat")).trim();
            } else {
                // Generar un nombre de usuario basado en el puerto si no se puede extraer
                return "Usuario" + socket.getPort();
            }
        }

        /**
         * Envía una lista de usuarios conectados actualmente
         */
        private void sendUsersList() {
            StringBuilder userList = new StringBuilder("Usuarios conectados: ");
            int count = 0;
            for (ClientHandler client : clients) {
                if (count > 0) {
                    userList.append(", ");
                }
                userList.append(client.username);
                count++;
            }
            userList.append(" (").append(count).append(" en total)");
            sendMessage(userList.toString());
        }

        /**
         * Envía información de ayuda sobre comandos disponibles
         */
        private void sendHelpInfo() {
            sendMessage("--- Comandos disponibles ---");
            sendMessage("/usuarios - Muestra la lista de usuarios conectados");
            sendMessage("/quit - Desconecta del chat");
            sendMessage("/ayuda - Muestra esta ayuda");
            sendMessage("--- Fin de la ayuda ---");
        }

        /**
         * Gestiona la desconexión del cliente
         */
        private void disconnect() {
            clients.remove(this);

            if (username != null) {
                broadcast(username + " ha abandonado el chat.", this);
            }

            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                System.err.println(getTimestamp() + " Error al cerrar recursos: " + e.getMessage());
            }

            System.out.println(getTimestamp() + " Cliente desconectado: " + username);
        }
    }
}