import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.net.URL; // Necesario si se carga desde resources

/**
 * Clase simple para reproducir un archivo de sonido de notificación.
 * Utiliza la API Java Sound (javax.sound.sampled).
 */
public class NotificationSound {
    private String soundFilePath; // Ruta al archivo de sonido (puede ser relativa o absoluta)
    private Clip clip; // Objeto para cargar y reproducir el sonido

    /**
     * Constructor.
     * @param soundFilePath Ruta al archivo de sonido (ej. "resources/sounds/notif.wav").
     */
    public NotificationSound(String soundFilePath) {
        this.soundFilePath = soundFilePath;
        // Pre-cargar el clip si se desea para evitar retraso en la primera reproducción
        // preloadClip();
    }

    /**
     * Intenta cargar el archivo de sonido en el objeto Clip.
     * Es privado, se puede llamar desde el constructor o antes de makeNoise.
     */
    private boolean preloadClip() {
        if (clip != null && clip.isOpen()) {
            return true; // Ya cargado
        }

        try {
            // Intentar obtener el archivo como recurso del classpath (más robusto para despliegue)
            URL soundURL = getClass().getClassLoader().getResource(soundFilePath);

            File soundFile;
            if (soundURL != null) {
                System.out.println("Cargando sonido desde recurso: " + soundURL);
                // Si se encuentra como recurso, usarlo directamente
                // Nota: AudioSystem.getAudioInputStream puede necesitar un InputStream, no URL directamente a veces.
                // soundFile = new File(soundURL.toURI()); // Puede fallar si está dentro de un JAR
                try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundURL)) {
                    clip = AudioSystem.getClip();
                    clip.open(audioInputStream);
                    System.out.println("Clip de sonido cargado desde recurso.");
                    return true;
                }
            } else {
                // Si no se encuentra como recurso, intentar como archivo del sistema
                System.out.println("Recurso no encontrado, intentando como archivo: " + soundFilePath);
                soundFile = new File(soundFilePath);
                if (!soundFile.exists()) {
                    System.err.println("Archivo de sonido no encontrado: " + soundFile.getAbsolutePath());
                    return false;
                }
                try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile)) {
                    clip = AudioSystem.getClip();
                    clip.open(audioInputStream);
                    System.out.println("Clip de sonido cargado desde archivo.");
                    return true;
                }
            }

        } catch (UnsupportedAudioFileException e) {
            System.err.println("Error: Formato de audio no soportado para '" + soundFilePath + "'. Use WAV, AU, AIFF. " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Error de E/S al leer el archivo de sonido '" + soundFilePath + "': " + e.getMessage());
            // e.printStackTrace(); // Descomentar para más detalles del error
        } catch (LineUnavailableException e) {
            System.err.println("Error: Línea de audio no disponible. ¿Otro programa está usando el audio? " + e.getMessage());
        } catch (Exception e) { // Captura genérica para otros posibles errores (ej. URISyntaxException)
            System.err.println("Error inesperado al cargar el sonido '" + soundFilePath + "': " + e.getMessage());
        }

        clip = null; // Asegurarse de que clip sea null si falla la carga
        return false;
    }


    /**
     * Reproduce el sonido de notificación.
     * Intenta cargar el clip si no está ya cargado.
     */
    public void makeNoise() {
        // Intentar cargar el clip si es necesario
        if (clip == null || !clip.isOpen()) {
            if (!preloadClip()) {
                System.err.println("No se pudo reproducir el sonido: clip no cargado.");
                return; // Salir si no se pudo cargar
            }
        }

        // Asegurarse de que el clip esté al principio antes de reproducir
        if (clip.isRunning()) {
            clip.stop(); // Detener si ya estaba sonando
        }
        clip.setFramePosition(0); // Rebobinar al inicio

        // Reproducir el sonido
        clip.start();
        System.out.println("Reproduciendo sonido...");

        // Nota: clip.start() no bloquea. El sonido se reproduce en segundo plano.
        // Si necesitaras esperar a que termine, podrías usar:
        // clip.addLineListener(event -> {
        //     if (event.getType() == LineEvent.Type.STOP) {
        //         // System.out.println("Reproducción finalizada.");
        //         // Podrías cerrar el clip aquí si solo se usa una vez: event.getLine().close();
        //     }
        // });
        // O de forma bloqueante (no recomendado en GUI): clip.drain();
    }

    /**
     * Libera los recursos de audio asociados con este objeto Clip.
     * Debería llamarse cuando ya no se necesite el sonido (ej. al cerrar la aplicación).
     */
    public void closeClip() {
        if (clip != null && clip.isOpen()) {
            clip.close();
            System.out.println("Clip de sonido cerrado.");
        }
        clip = null;
    }
}
