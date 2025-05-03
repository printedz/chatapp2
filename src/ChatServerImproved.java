import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Servidor de chat mejorado que permite la comunicación entre múltiples clientes.
 * Utiliza ExecutorService para gestionar hilos y java.util.logging para los logs.
 */
public class ChatServerImproved {
    // Puerto en el que escucha el servidor
    private static final int PORT = 12345;

    // Logger para el servidor
    private static final Logger serverLogger = Logger.getLogger(ChatServerImproved.class.getName());

    // Conjunto thread-safe para almacenar los handlers de cada cliente conectado
    // CopyOnWriteArraySet es eficiente para lecturas frecuentes (broadcast) y escrituras infrecuentes (conexión/desconexión)
    private static final Set<ClientHandler> clients = new CopyOnWriteArraySet<>();

    // Historial de mensajes recientes (últimos 100 mensajes) - Sincronizado externamente
    private static final List<String> messageHistory = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_HISTORY_SIZE = 100;

    // Formato para mostrar la hora en los mensajes
    private static final SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");

    // Prefijos para comandos del cliente
    private static final String USER_PREFIX = "USER:";
    private static final String MSG_PREFIX = "MSG:";
    private static final String CMD_HELP = "/help";
    private static final String CMD_USERS = "/users";


    public static void main(String[] args) {
        setupLogger(); // Configurar el logger
        serverLogger.info("Iniciando servidor de chat en puerto " + PORT);

        // Usar un ExecutorService para gestionar los hilos de los clientes de forma eficiente
        ExecutorService clientExecutor = Executors.newCachedThreadPool();

        // Usar try-with-resources para asegurar que el ServerSocket se cierre
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            serverLogger.info("Servidor iniciado correctamente. Esperando conexiones...");

            while (!serverSocket.isClosed()) { // Bucle principal mientras el servidor esté activo
                try {
                    Socket clientSocket = serverSocket.accept(); // Espera una conexión de cliente
                    serverLogger.info("Nueva conexión desde " + clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort());

                    // Crear y ejecutar el handler para el nuevo cliente usando el ExecutorService
                    ClientHandler handler = new ClientHandler(clientSocket);
                    clientExecutor.execute(handler); // Ejecuta el Runnable en un hilo del pool

                } catch (IOException e) {
                    // Si el serverSocket se cierra mientras está en accept(), saltará una excepción
                    if (serverSocket.isClosed()) {
                        serverLogger.info("El servidor ha sido detenido.");
                        break; // Salir del bucle si el servidor se detuvo
                    }
                    serverLogger.log(Level.SEVERE, "Error al aceptar conexión cliente", e);
                }
            }
        } catch (IOException e) {
            serverLogger.log(Level.SEVERE, "No se pudo iniciar el servidor en el puerto " + PORT, e);
        } finally {
            // Intentar cerrar el pool de hilos de forma ordenada cuando el servidor termine
            serverLogger.info("Deteniendo el servidor...");
            clientExecutor.shutdown(); // Deshabilita nuevas tareas
            try {
                // Espera un tiempo para que las tareas existentes terminen
                if (!clientExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    clientExecutor.shutdownNow(); // Cancela tareas en ejecución
                }
            } catch (InterruptedException ie) {
                // (Re-)Cancel if current thread also interrupted
                clientExecutor.shutdownNow();
                // Preserve interrupt status
                Thread.currentThread().interrupt();
            }
            serverLogger.info("Servidor detenido.");
        }
    }

    /**
     * Configura el logger principal del servidor para mostrar mensajes en la consola.
     */
    private static void setupLogger() {
        serverLogger.setLevel(Level.INFO); // Nivel mínimo de log a mostrar (INFO, WARNING, SEVERE)
        ConsoleHandler handler = new ConsoleHandler(); // Handler para la consola
        handler.setFormatter(new SimpleFormatter()); // Formato simple: Nivel: Mensaje
        handler.setLevel(Level.ALL); // El handler procesa todos los niveles, el logger filtra
        serverLogger.addHandler(handler);
        serverLogger.setUseParentHandlers(false); // Evitar duplicar logs si hay un logger raíz configurado
    }

    /**
     * Obtiene una marca de tiempo formateada para los mensajes.
     * @return String con la hora actual en formato [HH:mm:ss]
     */
    private static String getTimestamp() {
        // SimpleDateFormat no es thread-safe, pero aquí se usa desde hilos potencialmente diferentes.
        // Para alta concurrencia, considera ThreadLocal<SimpleDateFormat> o java.time.format.DateTimeFormatter
        return "[" + formatter.format(new Date()) + "]";
    }

    /**
     * Añade un mensaje al historial, asegurando que no exceda el tamaño máximo.
     * Este método debe ser llamado desde un bloque sincronizado si se modifica messageHistory.
     * @param message Mensaje a añadir.
     */
    private static void addToHistory(String message) {
        // messageHistory es una lista sincronizada, las operaciones individuales son atómicas,
        // pero la combinación de add y remove si se supera el tamaño necesita sincronización adicional.
        synchronized (messageHistory) {
            messageHistory.add(message);
            // Si el historial excede el tamaño, elimina el mensaje más antiguo
            while (messageHistory.size() > MAX_HISTORY_SIZE) {
                messageHistory.remove(0); // Elimina el primer elemento (el más antiguo)
            }
        }
    }

    /**
     * Distribuye un mensaje a todos los clientes conectados, excepto al remitente original.
     * Añade el mensaje al historial.
     *
     * @param message El mensaje a enviar (ya debe estar formateado con timestamp y remitente).
     * @param sender  El ClientHandler que originó el mensaje (para no enviárselo de vuelta).
     */
    private static void broadcast(String message, ClientHandler sender) {
        addToHistory(message); // Añade al historial
        serverLogger.fine("Broadcasting a clientes: " + message); // Log de nivel fino (debug)
        // Itera sobre la copia segura de clientes
        for (ClientHandler client : clients) {
            if (client != sender) { // No enviar al remitente
                client.sendMessage(message);
            }
        }
    }

    /**
     * Envía un mensaje generado por el sistema (como unirse/irse del chat) a TODOS los clientes conectados.
     * Añade el mensaje al historial y actualiza la lista de usuarios para todos.
     *
     * @param message El mensaje del sistema a enviar (ya formateado con timestamp).
     */
    private static void broadcastSystemMessage(String message) {
        addToHistory(message); // Añade al historial
        serverLogger.info("System Broadcast: " + message); // Log de nivel informativo
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
        // Después de un mensaje de sistema (unirse/irse), actualiza la lista de usuarios para todos
        sendUserListToAll();
    }

    /**
     * Envía la lista actualizada de usuarios a todos los clientes conectados actualmente.
     */
    private static void sendUserListToAll() {
        String userListMessage = generateUserListMessage(); // Genera el mensaje una vez
        serverLogger.fine("Enviando lista de usuarios actualizada a todos.");
        for (ClientHandler client : clients) {
            client.sendMessage(userListMessage);
        }
    }

    /**
     * Genera el string formateado con la lista de usuarios conectados actualmente.
     * @return String con la lista de usuarios (ej: "[HH:mm:ss] Usuarios conectados (2): Alice, Bob").
     */
    private static String generateUserListMessage() {
        StringBuilder userList = new StringBuilder(getTimestamp() + " Usuarios conectados (" + clients.size() + "): ");
        if (!clients.isEmpty()) {
            // Recolectar nombres de usuario
            List<String> usernames = new ArrayList<>();
            for (ClientHandler client : clients) {
                // Asegurarse de no añadir usuarios no completamente inicializados (aunque no debería pasar aquí)
                if (client.getUsername() != null && !client.getUsername().equals("Anónimo")) {
                    usernames.add(client.getUsername());
                }
            }
            Collections.sort(usernames, String.CASE_INSENSITIVE_ORDER); // Ordenar alfabéticamente sin importar mayúsculas
            userList.append(String.join(", ", usernames)); // Unir con comas
        } else {
            userList.append("(ninguno)"); // Mensaje si no hay nadie
        }
        return userList.toString();
    }


    /**
     * Clase interna que gestiona la comunicación con un cliente específico.
     * Implementa Runnable para ser ejecutada por un ExecutorService.
     */
    private static class ClientHandler implements Runnable {
        private final Socket socket; // Socket para este cliente
        private PrintWriter out;     // Stream para enviar mensajes al cliente
        private BufferedReader in;   // Stream para recibir mensajes del cliente
        private String username;     // Nombre de usuario (null hasta que se identifique)
        private final Logger clientLogger; // Logger específico para este cliente

        /**
         * Constructor para ClientHandler.
         * @param socket El socket del cliente conectado.
         */
        public ClientHandler(Socket socket) {
            this.socket = socket;
            // Crear un logger específico para este cliente, usando su dirección remota para identificarlo en los logs
            this.clientLogger = Logger.getLogger("ClientHandler." + socket.getRemoteSocketAddress());
            this.clientLogger.setLevel(Level.INFO); // Nivel de log para este cliente (puede ajustarse)
            // Hereda handlers del logger padre (serverLogger) por defecto, así que saldrá por consola.
        }

        /**
         * Obtiene el nombre de usuario de este cliente.
         * @return El nombre de usuario, o "Anónimo" si aún no se ha establecido.
         */
        public String getUsername() {
            return username != null ? username : "Anónimo"; // Evitar devolver null
        }

        @Override
        public void run() {
            clientLogger.info("Iniciando handler para cliente.");
            // Usar try-with-resources para asegurar que los streams y el socket se cierren al final
            try (
                    Socket clientSocket = this.socket; // Para asegurar que se cierre
                    BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
                    PrintWriter writer = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true) // true para autoFlush
            ) {
                this.in = reader;
                this.out = writer;

                // --- Fase 1: Identificación del Usuario ---
                sendMessage(getTimestamp() + " [Sistema] Bienvenido al servidor de chat. Por favor, identifíquese con " + USER_PREFIX + "suNombre");
                String clientInput;
                while (this.username == null && (clientInput = in.readLine()) != null) {
                    clientLogger.fine("Recibido (pre-auth): " + clientInput);
                    if (clientInput.startsWith(USER_PREFIX)) {
                        processUserCommand(clientInput); // Intenta procesar la identificación
                        // Si processUserCommand tuvo éxito, this.username no será null y el bucle terminará.
                    } else {
                        // Rechazar otros mensajes antes de la identificación
                        sendMessage(getTimestamp() + " [Sistema] Comando inválido. Use " + USER_PREFIX + "suNombre para identificarse.");
                        clientLogger.warning("Mensaje/comando inválido recibido antes de la identificación: " + clientInput);
                    }
                }

                // Si llegamos aquí y username es null, significa que el cliente se desconectó antes de identificarse.
                if (this.username == null) {
                    clientLogger.warning("El cliente se desconectó antes de identificarse.");
                    return; // Termina el método run() para este cliente
                }

                // --- Fase 2: Procesamiento de Mensajes y Comandos ---
                clientLogger.info("Usuario '" + this.username + "' autenticado. Procesando mensajes.");
                while (connected() && (clientInput = in.readLine()) != null) { // Bucle principal mientras esté conectado
                    clientLogger.fine("Recibido de " + this.username + ": " + clientInput);
                    if (clientInput.startsWith(MSG_PREFIX)) {
                        processMessageCommand(clientInput); // Procesa mensajes o comandos /help, /users
                    } else if (clientInput.startsWith(USER_PREFIX)){
                        // Podría implementarse cambio de nombre, pero por ahora se rechaza
                        sendMessage(getTimestamp() + " [Sistema] Ya estás identificado como " + this.username + ". No puedes cambiar el nombre.");
                        clientLogger.warning("Intento de re-identificación ignorado para " + this.username);
                    } else {
                        // Mensaje con formato desconocido
                        sendMessage(getTimestamp() + " [Sistema] Comando desconocido. Usa " + MSG_PREFIX + "mensaje o comandos como " + CMD_HELP);
                        clientLogger.warning("Comando desconocido recibido de " + this.username + ": " + clientInput);
                    }
                }

            } catch (SocketException e) {
                // Común si el cliente cierra la conexión abruptamente (ej. cierra la ventana)
                clientLogger.info("Conexión cerrada inesperadamente por el cliente: " + getUsernameWithAddress() + " (" + e.getMessage() + ")");
            } catch (IOException e) {
                // Otros errores de I/O durante la comunicación
                clientLogger.log(Level.WARNING, "Error de I/O con cliente " + getUsernameWithAddress(), e);
            } catch (Exception e) {
                // Captura general para errores inesperados en el hilo del cliente
                clientLogger.log(Level.SEVERE, "Error inesperado en el handler del cliente " + getUsernameWithAddress(), e);
            } finally {
                // --- Fase 3: Limpieza y Desconexión ---
                // Este bloque se ejecuta siempre, ya sea por fin normal, excepción o desconexión.
                clientLogger.info("Finalizando handler para: " + getUsernameWithAddress());
                disconnect(); // Llama al método de limpieza
            }
        }

        /**
         * Procesa el comando de identificación inicial del usuario (USER:nombre).
         * Establece el nombre de usuario si es válido y no está en uso.
         * @param command El comando completo recibido del cliente.
         */
        private void processUserCommand(String command) {
            String potentialUsername = command.substring(USER_PREFIX.length()).trim();
            clientLogger.fine("Intentando registrar usuario: " + potentialUsername);

            // Validar nombre de usuario
            if (!isValidUsernameFormat(potentialUsername)) {
                sendMessage(getTimestamp() + " [Sistema] Nombre de usuario no válido (vacío, muy largo o caracteres inválidos). Intente con otro.");
                clientLogger.warning("Intento de registro con formato de nombre inválido: '" + potentialUsername + "'");
                return; // No establece this.username, el bucle de identificación continuará
            }

            // Comprobar si el nombre ya está en uso (de forma thread-safe)
            if (isUsernameTaken(potentialUsername)) {
                sendMessage(getTimestamp() + " [Sistema] El nombre de usuario '" + potentialUsername + "' ya está en uso. Intente con otro.");
                clientLogger.warning("Intento de registro con nombre duplicado: " + potentialUsername);
                return; // No establece this.username
            }

            // Nombre válido y no en uso: proceder con el registro
            this.username = potentialUsername;
            clients.add(this); // Añadir a la lista de clientes activos *después* de validar y asignar nombre

            sendMessage(getTimestamp() + " [Sistema] ¡Bienvenido al chat, " + this.username + "!");
            clientLogger.info("Usuario '" + this.username + "' registrado y añadido a la lista de clientes.");

            // Enviar historial reciente al nuevo usuario
            sendHistory();

            // Notificar a todos (incluido el nuevo) que alguien se unió y actualizar listas
            broadcastSystemMessage(getTimestamp() + " [Sistema] " + this.username + " se ha unido al chat.");

        }

        /**
         * Procesa un mensaje normal (MSG:...) o un comando (/...) enviado por un usuario ya identificado.
         * @param command El comando completo recibido del cliente.
         */
        private void processMessageCommand(String command) {
            String content = command.substring(MSG_PREFIX.length()).trim();
            if (!content.isEmpty()) {
                clientLogger.fine("Mensaje/comando recibido de " + this.username + ": " + content);
                // Comprobar si es un comando especial conocido
                switch (content.toLowerCase()) { // Usar toLowerCase para comandos insensibles a mayúsculas
                    case CMD_HELP:
                        sendHelpInfo();
                        break;
                    case CMD_USERS:
                        sendUsersList(); // Enviar la lista solo a este cliente que la pidió
                        break;
                    // Aquí podrían añadirse más comandos como /quit, /whisper <user> <msg>, etc.
                    // case "/quit":
                    //    disconnect();
                    //    break;
                    default:
                        // Si no es un comando conocido, es un mensaje normal para broadcast
                        String formattedMessage = getTimestamp() + " [" + this.username + "]: " + content;
                        broadcast(formattedMessage, this); // Enviar a todos excepto al remitente
                        break;
                }
            } else {
                clientLogger.fine("Mensaje vacío ignorado de " + this.username);
            }
        }

        /**
         * Valida el formato básico del nombre de usuario.
         * @param name Nombre a validar.
         * @return true si el formato es aceptable, false si no.
         */
        private boolean isValidUsernameFormat(String name) {
            if (name == null || name.isEmpty()) return false; // No puede ser nulo o vacío
            if (name.length() < 3 || name.length() > 15) return false; // Longitud entre 3 y 15 caracteres
            // Permitir letras, números, guiones bajos y guiones medios. Prohibir espacios.
            if (!name.matches("^[a-zA-Z0-9_-]+$")) return false;
            // Podrían añadirse más restricciones (no empezar con número, etc.)
            return true;
        }

        /**
         * Comprueba si un nombre de usuario ya está siendo utilizado por otro cliente conectado.
         * Es case-insensitive.
         * @param name Nombre a comprobar.
         * @return true si el nombre está en uso, false si está libre.
         */
        private boolean isUsernameTaken(String name) {
            // Iterar sobre la colección thread-safe de clientes
            for (ClientHandler client : clients) {
                // Asegurarse de que el cliente comparado ya tenga un nombre asignado
                // y comparar ignorando mayúsculas/minúsculas
                if (client.username != null && client.username.equalsIgnoreCase(name)) {
                    return true; // Encontrado (duplicado)
                }
            }
            return false; // No encontrado (disponible)
        }


        /**
         * Envía el historial reciente de mensajes a este cliente.
         */
        private void sendHistory() {
            sendMessage(getTimestamp() + " [Sistema] --- Inicio del Historial Reciente ---");
            // Acceder al historial sincronizado de forma segura
            List<String> currentHistory;
            synchronized (messageHistory) {
                // Crear una copia para evitar mantener el bloqueo durante el envío
                currentHistory = new ArrayList<>(messageHistory);
            }

            if (currentHistory.isEmpty()) {
                sendMessage(getTimestamp() + " [Sistema] No hay mensajes previos en el historial.");
            } else {
                for (String msg : currentHistory) {
                    sendMessage(msg); // Enviar cada mensaje histórico
                }
            }
            sendMessage(getTimestamp() + " [Sistema] --- Fin del Historial Reciente ---");
        }

        /**
         * Envía la lista de usuarios conectados actualmente solo a este cliente.
         */
        private void sendUsersList() {
            sendMessage(generateUserListMessage()); // Reutiliza el método que genera la lista
        }

        /**
         * Envía información de ayuda sobre los comandos disponibles a este cliente.
         */
        private void sendHelpInfo() {
            sendMessage(getTimestamp() + " [Sistema] --- Ayuda ---");
            sendMessage(getTimestamp() + " [Sistema] Envía mensajes usando: " + MSG_PREFIX + "tu mensaje");
            sendMessage(getTimestamp() + " [Sistema] Comandos disponibles:");
            sendMessage(getTimestamp() + " [Sistema]   " + CMD_USERS + " - Muestra la lista de usuarios conectados.");
            sendMessage(getTimestamp() + " [Sistema]   " + CMD_HELP + " - Muestra esta ayuda.");
            // Añadir aquí la descripción de futuros comandos
            sendMessage(getTimestamp() + " [Sistema] --- Fin Ayuda ---");
        }

        /**
         * Envía un mensaje directamente a este cliente.
         * Es synchronized para prevenir escrituras concurrentes si fuera posible (aunque aquí no debería serlo).
         * @param message Mensaje a enviar.
         */
        public synchronized void sendMessage(String message) {
            if (out != null && !out.checkError()) { // Comprobar si el stream está operativo
                out.println(message);
                // No es necesario out.flush() porque el PrintWriter se creó con autoFlush=true
            } else {
                clientLogger.warning("No se pudo enviar mensaje a " + getUsernameWithAddress() + " (stream cerrado o con error)");
                // Considerar intentar cerrar la conexión si el stream falla consistentemente
                // disconnect();
            }
        }

        /**
         * Comprueba si el socket de este cliente sigue conectado.
         * @return true si el socket está conectado, false en caso contrario.
         */
        private boolean connected() {
            return socket != null && !socket.isClosed() && socket.isConnected();
        }


        /**
         * Devuelve una representación String del cliente (nombre y dirección).
         * Útil para logging.
         */
        private String getUsernameWithAddress() {
            String user = (username != null) ? username : "NoIdentificado";
            // Obtener la dirección remota de forma segura
            SocketAddress remoteAddress = null;
            try {
                remoteAddress = socket.getRemoteSocketAddress();
            } catch (Exception e) { /* Ignorar si el socket ya está cerrado */ }
            return user + "@" + (remoteAddress != null ? remoteAddress : "dirección desconocida");
        }

        /**
         * Gestiona la desconexión limpia del cliente:
         * 1. Lo elimina de la lista de clientes activos.
         * 2. Notifica a los demás clientes que se ha ido.
         * 3. Cierra los recursos (streams, socket) asociados a este cliente.
         */
        private void disconnect() {
            // Solo notificar y eliminar si el usuario llegó a identificarse y añadirse a 'clients'
            boolean removed = false;
            if (this.username != null) {
                // Intentar eliminar de la lista thread-safe
                removed = clients.remove(this);
            }

            // Si se eliminó correctamente (estaba en la lista)
            if (removed) {
                String departureMessage = getTimestamp() + " [Sistema] " + this.username + " ha abandonado el chat.";
                clientLogger.info(this.username + " se ha desconectado. Notificando a los demás.");
                // Notifica a los restantes y actualiza sus listas de usuarios
                broadcastSystemMessage(departureMessage);
            } else {
                // Si username es null o no se pudo eliminar (quizás ya se había eliminado)
                clientLogger.info("Cliente " + getUsernameWithAddress() + " desconectado (posiblemente antes de registrarse o ya eliminado).");
            }

            // Cerrar los recursos (streams y socket)
            // El try-with-resources de run() ya intenta cerrarlos, pero esto asegura el cierre
            // incluso si disconnect() se llama desde otro lugar o si hubo un error antes.
            try {
                if (socket != null && !socket.isClosed()) {
                    clientLogger.fine("Cerrando socket para " + getUsernameWithAddress());
                    socket.close(); // Esto debería cerrar también los streams asociados
                }
            } catch (IOException e) {
                clientLogger.log(Level.WARNING, "Error al cerrar el socket para " + getUsernameWithAddress(), e);
            }
            // No es estrictamente necesario cerrar 'in' y 'out' explícitamente si se cerró el socket,
            // pero no hace daño intentarlo por si acaso.
            if (out != null) {
                out.close();
            }
            // 'in' (BufferedReader) también debería cerrarse al cerrar el socket.

            serverLogger.fine("Recursos liberados para la conexión: " + getUsernameWithAddress());
        }
    }
}
