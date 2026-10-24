package dev.projectskins.server;

import dev.projectskins.ProjectSkins;
import dev.projectskins.skin.PngValidator;
import dev.projectskins.skin.SkinValidator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Каталог PNG, встроенных в JAR мода. Gradle создаёт projectskins-skins.txt;
 * перечисление не зависит от того, находятся ресурсы в папке IDE или в архиве.
 * Проверка выполняется один раз при создании каталога: сервер не отправляет
 * заведомо несуществующее имя или PNG неверного размера.
 * Здесь нет клиентского менеджера ресурсов, поэтому класс работает на dedicated server.
 */
public final class LocalSkinCatalog {
    private final Set<String> ids;
    private final Map<String, String> invalid;

    public LocalSkinCatalog() {
        TreeSet<String> validIds = new TreeSet<>();
        TreeMap<String, String> invalidIds = new TreeMap<>();
        try (InputStream index = LocalSkinCatalog.class.getResourceAsStream("/projectskins-skins.txt")) {
            if (index == null) {
                throw new IOException("В сборке отсутствует projectskins-skins.txt");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(index, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String id = line.strip();
                    if (id.isEmpty() || id.startsWith("#")) {
                        continue;
                    }
                    try {
                        SkinValidator.localId(id);
                        validateResource(id);
                        validIds.add(id);
                    } catch (IOException | IllegalArgumentException exception) {
                        invalidIds.put(id, exception.getMessage());
                        ProjectSkins.LOGGER.warn("Встроенный скин {} недоступен: {}", id, exception.getMessage());
                    }
                }
            }
        } catch (IOException exception) {
            // Ошибка каталога не должна запрещать использование корректных URL.
            ProjectSkins.LOGGER.error("Не удалось прочитать каталог локальных скинов", exception);
        }
        ids = Collections.unmodifiableSet(validIds);
        invalid = Collections.unmodifiableMap(invalidIds);
    }

    public Set<String> ids() {
        return ids;
    }

    /** Ошибка попадает в ответ команды до изменения серверного назначения. */
    public void require(String id) {
        if (!ids.contains(id)) {
            String reason = invalid.get(id);
            if (reason != null) {
                throw new IllegalArgumentException("Некорректный локальный скин " + id + ": " + reason);
            }
            throw new IllegalArgumentException("Локальный скин '" + id + "' отсутствует в сборке сервера");
        }
    }

    private static void validateResource(String id) throws IOException {
        String path = "/assets/projectskins/textures/skins/" + id + ".png";
        try (InputStream image = LocalSkinCatalog.class.getResourceAsStream(path)) {
            if (image == null) {
                throw new IOException("Ресурс " + path + " не найден");
            }
            // На один байт больше лимита, чтобы обнаружить превышение без чтения всего файла.
            byte[] bytes = image.readNBytes(PngValidator.MAX_BYTES + 1);
            PngValidator.validate(bytes);
        }
    }
}
