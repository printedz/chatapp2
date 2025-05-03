import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.function.Consumer;
// No se necesita SwingUtilities aquí, se usa en Main para actualizar la GUI desde los handlers

/**
 * Clase que encapsula la lógica de red para el cliente de chat.
 * Maneja la conexión, envío y recepción de mensajes, y notifica cambios de estado.
 */
public class Networking {
    // Información del servidor
    private final String serverAddress;
    private final int serverPort;

    // Recursos de red
    private Socket socket;
    private PrintWriter output; // Para enviar mensajes
    private BufferedReader input; // Para recibir mensajes

    // Gestión de hilos
    private final ExecutorService networkExecutor; // Para tareas de red (escucha)
    private volatile boolean connected; // Indica si está conectado (volatile para visibilidad entre hilos)

    // Callbacks para comunicar con la GUI (o lógica principal)
    private Consumer<String> messageHandler; // Se llama cuando llega un mensaje
    private Consumer<ConnectionStatus> statusHandler; // Se llama cuando cambia el estado de conexión

    /**
     * Enumera los posibles estados de la conexión para informar a la GUI.
     */
    public enum ConnectionStatus {
        CONNECTED,      // Conexión establecida con éxito
        DISCONNECTED,   // Desconectado (por el usuario, el servidor o error)
        CONNECTION_FAILED, // No se pudo establecer la conexión inicial
        RECONNECTING    // Estado intermedio si se implementa reconexión automática
    }

    /**
     * Constructor para la clase Networking.
     *
     * @param serverAddress La dirección IP o nombre de host del servidor.
     * @param serverPort    El puerto en el que escucha el servidor.
     */
    public Networking(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        // Usar un Executor de un solo hilo para manejar la escucha de mensajes
        // Esto asegura que los mensajes se procesen en orden y simplifica la sincronización
        this.networkExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ChatClient-NetworkListener"); // Dar nombre al hilo
            t.setDaemon(true); // Marcar como daemon para que no impida salir a la JVM
            return t;
        });
        this.connected = false; // Inicialmente no conectado
    }

    // --- Getters para información ---

    public String getServerAddress() {
        return serverAddress;
    }

    public int getServerPort() {
        return serverPort;
    }

    // --- Setters para los Handlers (Callbacks) ---

    /**
     * Establece el manejador (callback) que se invocará cuando se reciba un mensaje del servidor.
     *
     * @param handler Una función (lambda o referencia a método) que acepta un String (el mensaje).
     */
    public void setMessageHandler(Consumer<String> handler) {
        this.messageHandler = handler;
    }

    /**
     * Establece el manejador (callback) que se invocará cuando cambie el estado de la conexión.
     *
     * @param handler Una función que acepta un ConnectionStatus.
     */
    public void setStatusHandler(Consumer<ConnectionStatus> handler) {
        this.statusHandler = handler;
    }

    // --- Métodos principales de conexión/desconexión ---

    /**
     * Intenta establecer una conexión con el servidor de chat.
     * Esta operación es bloqueante y debería llamarse desde un hilo separado de la GUI.
     */
    public void connect() {
        // Si ya está conectado, no hacer nada
        if (isConnected()) {
            System.err.println("Ya está conectado.");
            return;
        }

        try {
            // Crear el socket y los streams de E/S usando UTF-8
            socket = new Socket();
            // Establecer un timeout de conexión (ej. 10 segundos)
            socket.connect(new InetSocketAddress(serverAddress, serverPort), 10000);

            // Usar UTF-8 explícitamente para E/S
            output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true); // true para autoFlush
            input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            connected = true; // Marcar como conectado *después* de establecer todo

            // Notificar a la GUI/lógica principal que la conexión fue exitosa
            updateStatus(ConnectionStatus.CONNECTED);

            // Iniciar la tarea de escucha de mensajes en el Executor
            listenForMessages();

        } catch (UnknownHostException e) {
            System.err.println("Error: Host desconocido - " + serverAddress);
            handleConnectionFailure(e);
        } catch (SocketTimeoutException e) {
            System.err.println("Error: Timeout al conectar a " + serverAddress + ":" + serverPort);
            handleConnectionFailure(e);
        } catch (IOException e) {
            System.err.println("Error de E/S al conectar: " + e.getMessage());
            handleConnectionFailure(e);
        }
    }

    /**
     * Cierra la conexión con el servidor y libera los recursos asociados.
     * Puede ser llamado desde cualquier hilo.
     */
    public void disconnect() {
        // Solo actuar si realmente está conectado
        if (connected) {
            connected = false; // Marcar como desconectado primero para detener bucles
            System.out.println("Desconectando...");
            // Cerrar los recursos de red de forma segura
            disconnectQuietly();
            // Notificar a la GUI/lógica principal
            updateStatus(ConnectionStatus.DISCONNECTED);
            System.out.println("Desconectado.");
        }
    }

    /**
     * Método auxiliar para manejar fallos durante el intento de conexión.
     * Limpia recursos y notifica el fallo.
     * @param e La excepción que causó el fallo.
     */
    private void handleConnectionFailure(Exception e) {
        connected = false;
        disconnectQuietly(); // Asegurarse de cerrar cualquier recurso parcialmente abierto
        updateStatus(ConnectionStatus.CONNECTION_FAILED); // Notificar fallo
    }


    // --- Lógica de Comunicación ---

    /**
     * Inicia un hilo/tarea para escuchar continuamente los mensajes entrantes del servidor.
     */
    private void listenForMessages() {
        networkExecutor.submit(() -> {
            System.out.println("Hilo listener iniciado.");
            try {
                String messageFromServer;
                // Leer líneas mientras esté conectado y el stream esté abierto
                while (connected && (messageFromServer = input.readLine()) != null) {
                    // Si hay un manejador de mensajes registrado, pasárselo
                    if (messageHandler != null) {
                        // Pasar una copia final del mensaje al handler
                        final String finalMessage = messageFromServer;
                        // Es responsabilidad del messageHandler (en Main) usar SwingUtilities.invokeLater si necesita actualizar la GUI
                        messageHandler.accept(finalMessage);
                    }
                }
            } catch (SocketException e) {
                // SocketException suele ocurrir si la conexión se cierra (local o remotamente)
                if (connected) { // Solo informar si esperábamos estar conectados
                    System.err.println("Conexión perdida (SocketException): " + e.getMessage());
                    disconnect(); // Desconectar formalmente y notificar
                }
            } catch (IOException e) {
                // Otros errores de lectura
                if (connected) {
                    System.err.println("Error de E/S al leer del servidor: " + e.getMessage());
                    disconnect(); // Desconectar formalmente y notificar
                }
            } finally {
                // Asegurarse de que si el bucle termina (incluso por error no capturado antes), se desconecte.
                if (connected) {
                    System.err.println("El hilo listener terminó inesperadamente. Desconectando...");
                    disconnect();
                }
                System.out.println("Hilo listener terminado.");
            }
        });
    }

    /**
     * Envía un mensaje de texto al servidor.
     *
     * @param message El mensaje a enviar.
     * @return true si el mensaje se envió sin errores aparentes, false en caso contrario.
     */
    public boolean sendMessage(String message) {
        // No enviar si no está conectado o el stream de salida no es válido
        if (!isConnected() || output == null || output.checkError()) {
            System.err.println("No se puede enviar mensaje: No conectado o stream con error.");
            return false;
        }

        try {
            output.println(message);
            // checkError() comprueba si ha ocurrido algún error en el stream desde la última operación.
            // No garantiza entrega, pero es una comprobación útil.
            return !output.checkError();
        } catch (Exception e) {
            // Captura genérica por si println lanza algo inesperado (aunque no debería)
            System.err.println("Excepción inesperada al enviar mensaje: " + e.getMessage());
            // Considerar desconectar si el envío falla repetidamente
            // disconnect();
            return false;
        }
    }

    // --- Métodos Auxiliares y de Estado ---

    /**
     * Cierra los recursos de red (socket, streams) de forma segura, ignorando excepciones.
     * Se usa durante la desconexión normal o en caso de error.
     */
    private void disconnectQuietly() {
        // Cerrar en orden inverso a la creación: output, input, socket
        try {
            if (output != null) {
                output.close();
            }
        } catch (Exception e) { /* Ignorar */ }
        try {
            if (input != null) {
                input.close();
            }
        } catch (Exception e) { /* Ignorar */ }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) { /* Ignorar */ }

        // Poner referencias a null para ayudar al GC y evitar uso accidental
        output = null;
        input = null;
        socket = null;
    }

    /**
     * Verifica si el cliente está actualmente conectado al servidor.
     *
     * @return true si `connected` es true y el socket parece válido, false en caso contrario.
     */
    public boolean isConnected() {
        // Comprobación más robusta: flag 'connected' y estado del socket
        return connected && socket != null && socket.isConnected() && !socket.isClosed();
    }

    /**
     * Notifica al manejador de estado (si existe) sobre un cambio en el estado de la conexión.
     * @param newStatus El nuevo estado de la conexión.
     */
    private void updateStatus(ConnectionStatus newStatus) {
        if (statusHandler != null) {
            // Es responsabilidad del statusHandler (en Main) usar SwingUtilities.invokeLater
            statusHandler.accept(newStatus);
        } else {
            System.out.println("Estado de conexión cambiado a: " + newStatus + " (No hay handler registrado)");
        }
    }

    /**
     * Cierra la conexión (si está activa) y detiene el ExecutorService.
     * Debería llamarse cuando la aplicación cliente se cierra.
     */
    public void shutdown() {
        System.out.println("Iniciando apagado de Networking...");
        disconnect(); // Asegurarse de que está desconectado

        // Apagar el ExecutorService de forma ordenada
        networkExecutor.shutdown(); // Deshabilita nuevas tareas
        try {
            // Esperar un poco a que la tarea de escucha termine
            if (!networkExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                networkExecutor.shutdownNow(); // Forzar cancelación si no termina
            }
        } catch (InterruptedException e) {
            networkExecutor.shutdownNow();
            Thread.currentThread().interrupt(); // Restaurar estado de interrupción
        }
        System.out.println("Networking apagado.");
    }
}
