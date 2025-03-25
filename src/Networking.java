import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;


/**
 * Clase que maneja la comunicación de red para la aplicación de chat
 */
public class Networking {
    private Socket socket;
    private PrintWriter output;
    private BufferedReader input;
    private final ExecutorService executor;
    private final String serverAddress;
    private final int serverPort;
    private boolean connected;
    private Consumer<String> messageHandler;
    private Consumer<ConnectionStatus> statusHandler;

    /**
     * Enumera los posibles estados de conexión
     */
    public enum ConnectionStatus {
        CONNECTED, DISCONNECTED, CONNECTION_FAILED, RECONNECTING
    }

    /**
     * Constructor para la clase Networking
     *
     * @param serverAddress dirección del servidor (por defecto "localhost")
     * @param serverPort puerto del servidor
     */
    public Networking(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.executor = Executors.newSingleThreadExecutor();
        this.connected = false;
    }

    /**
     * Establece el manejador de mensajes entrantes
     *
     * @param handler función que procesará los mensajes recibidos
     */
    public void setMessageHandler(Consumer<String> handler) {
        this.messageHandler = handler;
    }

    /**
     * Establece el manejador de cambios de estado de la conexión
     *
     * @param handler función que procesará los cambios de estado
     */
    public void setStatusHandler(Consumer<ConnectionStatus> handler) {
        this.statusHandler = handler;
    }

    /**
     * Conecta al servidor de chat
     */
    public void connect() {
        executor.submit(() -> {
            try {
                // Crear el socket y los streams
                socket = new Socket(serverAddress, serverPort);
                output = new PrintWriter(new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream())), true);
                input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                connected = true;

                // Notificar cambio de estado a conectado
                if (statusHandler != null) {
                    statusHandler.accept(ConnectionStatus.CONNECTED);
                }

                // Comenzar a escuchar mensajes entrantes
                listenForMessages();
            } catch (IOException e) {
                connected = false;
                if (statusHandler != null) {
                    statusHandler.accept(ConnectionStatus.CONNECTION_FAILED);
                }
                System.err.println("Error al conectar: " + e.getMessage());
            }
        });
    }

    /**
     * Escucha continuamente los mensajes entrantes
     */
    private void listenForMessages() {
        try {
            String message;
            while (connected && (message = input.readLine()) != null) {
                // Procesar el mensaje recibido
                if (messageHandler != null) {
                    final String receivedMessage = message;
                    // Ejecutamos el handler en el hilo principal para actualizar la UI
                    SwingUtilities.invokeLater(() -> messageHandler.accept(receivedMessage));
                }
            }
        } catch (IOException e) {
            if (connected) {
                disconnectQuietly();
                if (statusHandler != null) {
                    statusHandler.accept(ConnectionStatus.DISCONNECTED);
                }
            }
        }
    }

    /**
     * Envía un mensaje al servidor
     *
     * @param message mensaje a enviar
     * @return true si se envió el mensaje, false si falló
     */
    public boolean sendMessage(String message) {
        if (!connected || output == null) {
            return false;
        }

        executor.submit(() -> {
            output.println(message);
            if (output.checkError()) {
                disconnectQuietly();
                if (statusHandler != null) {
                    statusHandler.accept(ConnectionStatus.DISCONNECTED);
                }
            }
        });

        return true;
    }

    /**
     * Desconecta del servidor
     */
    public void disconnect() {
        if (!connected) {
            return;
        }

        connected = false;
        disconnectQuietly();

        if (statusHandler != null) {
            statusHandler.accept(ConnectionStatus.DISCONNECTED);
        }
    }

    /**
     * Cierra los recursos de red sin lanzar excepciones
     */
    private void disconnectQuietly() {
        connected = false;
        try {
            if (output != null) output.close();
            if (input != null) input.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("Error al cerrar la conexión: " + e.getMessage());
        }
    }

    /**
     * Verifica si está conectado al servidor
     *
     * @return true si está conectado, false en caso contrario
     */
    public boolean isConnected() {
        return connected && socket != null && socket.isConnected() && !socket.isClosed();
    }

    /**
     * Cierra los recursos utilizados por esta clase
     */
    public void shutdown() {
        disconnect();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}