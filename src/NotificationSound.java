import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

public class NotificationSound {
    private String soundFilePath;

    public NotificationSound(String soundFilePath) {
        this.soundFilePath = soundFilePath;
    }

    public void makeNoise() {
        try {
            // Crea un archivo a partir de la ruta
            File soundFile = new File(soundFilePath);

            // Verifica si el archivo existe
            if (!soundFile.exists()) {
                System.err.println("Archivo de sonido no encontrado: " + soundFilePath);
                return;
            }

            // Obtener un AudioInputStream
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);

            // Obtener un clip y abrirlo con el AudioInputStream
            Clip clip = AudioSystem.getClip();
            clip.open(audioInputStream);

            // Reproducir el sonido
            clip.start();

            // Esperar a que termine la reproducción (opcional)
            // clip.drain();

        } catch (UnsupportedAudioFileException e) {
            System.err.println("Formato de audio no soportado: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Error de I/O: " + e.getMessage());
            e.printStackTrace();
        } catch (LineUnavailableException e) {
            System.err.println("Línea de audio no disponible: " + e.getMessage());
        }
    }
}