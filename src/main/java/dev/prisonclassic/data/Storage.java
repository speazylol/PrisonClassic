package dev.prisonclassic.data;

import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Хранилище PlayerData. Реализации: YAML-файлы (по умолчанию) и MySQL (storage.type: mysql).
 * Все методы, кроме close(), можно вызывать из асинхронного потока.
 */
public interface Storage {
    /** Загрузить сырые данные игрока, null — игрок новый. */
    YamlConfiguration load(UUID uuid);

    /** Сохранить снимок данных игрока (снимок делается в главном потоке). */
    void save(UUID uuid, YamlConfiguration snapshot, PlayerData indexed);

    /** Все сохранённые игроки (для /top). */
    List<PlayerData> loadAll();

    /** Записать время прохождения подземелья (тайм-аттак). */
    void saveDungeonRecord(String party, long millis, long when);

    /** Лучшие прохождения подземелья. Каждая строка: "игроки|мс|время". */
    List<String> topDungeon(int limit);

    String name();

    void close();
}
