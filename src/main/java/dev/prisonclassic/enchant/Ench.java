package dev.prisonclassic.enchant;

import org.bukkit.Material;

public enum Ench {
    BOOM("Бум", 3, Ench.Kind.PICK, "Сферический подрыв блоков вокруг (радиус 1/2/3): блоки сразу в статистику уровня, монеты сразу на баланс"),
    HASTE("Спешка", 2, Ench.Kind.PICK, "Шанс получить Спешку: 1 — 14 сек, 2 — 23 сек"),
    COIN("Бустер монет", 3, Ench.Kind.PICK, "Шанс бонуса к монетам за блок: +25% / +45% / +100%"),
    BLOCKS("Бустер блоков", 3, Ench.Kind.PICK, "Шанс бонуса к счётчику блоков: +25% / +45% / +100%"),
    KEYMASTER("Ключник", 3, Ench.Kind.SHOVEL, "Шанс дропа ключей: 1 — обычный, 2 — ДК, 3 — легендарный"),
    GRAVE("Расхититель могил", 1, Ench.Kind.AXE, "Шанс получить добычу блока x5 от уровня"),
    HUNTER("Охотник", 3, Ench.Kind.SWORD, "Больше дропа с мобов"),
    VAMPIRE("Вампа", 3, Ench.Kind.SWORD, "Вампиризм: возвращает часть нанесённого урона"),
    BLIND("Слепота", 3, Ench.Kind.SWORD, "1 — слепота, 2 — +слабость I, 3 — +слабость II"),
    PETRIFY("Окаменение", 1, Ench.Kind.SWORD, "Шанс оглушить цель (полный стан + тьма)"),
    PRECISION("Точность", 1, Ench.Kind.SWORD, "Удар проходит сквозь уклонение"),
    BOMBER("Подрывник", 1, Ench.Kind.BOW, "Из места попадания спавнится динамит (0.3 сек)"),
    POISON_ARROW("Пронзающая стрела", 3, Ench.Kind.BOW, "Шанс отравить цель"),
    NINJA("Ниндзя", 3, Ench.Kind.ARMOR, "1 — скорость I; 2 — +регенерация I; 3 — скорость II + регенерация III"),
    VOODOO("Вуду", 2, Ench.Kind.ARMOR, "Шанс наложить на атакующего слабость и замедление"),
    CLEANSE("Очищение", 1, Ench.Kind.ARMOR, "Снимает негативные эффекты"),
    NESOKRA("Несокрушимый", 5, Ench.Kind.ARMOR, "Снижает входящий урон на 4% за уровень"),
    HEAVY("Тяжёлые доспехи", 1, Ench.Kind.ARMOR, "Замедление II, но входящий урон -16%"),
    GLUTTON("Обжора", 1, Ench.Kind.ARMOR, "Убийство игрока/моба восстанавливает 2 HP"),
    ENLIGHT("Просвещение", 3, Ench.Kind.ARMOR, "Лечит часть полученного урона. Суммируется с брони; после 6 — убывающая отдача"),
    EVASION("Уклонение", 1, Ench.Kind.ARMOR, "Шанс 17% полностью увернуться от удара (сквозь него бьёт «Точность»)"),
    JUMP("Прыгунчик", 2, Ench.Kind.BOOTS, "Прыжок выше"),
    SPEED("Скорость", 2, Ench.Kind.BOOTS, "Скорость бега"),
    TOTEM("Тотем бессмертия", 2, Ench.Kind.ARMOR, "Воскрешение на месте смерти: золотые сердца и регенерация. Только из Древнего ключа, 1 книга на сет"),
    WAVE("Волна воды", 3, Ench.Kind.ARMOR, "Шанс при получении урона выпустить АОЕ-волну воды по врагам вокруг"),
    DEVOURER("Пожиратель", 3, Ench.Kind.SWORD, "Крадёт здоровье противника (вампиризм) и даёт короткую регенерацию"),
    LEAP("Скачок", 2, Ench.Kind.BOW, "Шанс телепортироваться за спину цели при попадании стрелы"),
    TRIPLE("Триствольник", 2, Ench.Kind.BOW, "Шанс выстрелить веером из трёх стрел"),
    CACTUS("Кактус", 3, Ench.Kind.ARMOR, "Повышенный шанс вернуть атакующему часть урона");

    public final String title;
    public final int max;
    public final Ench.Kind kind;
    public final String desc;

    private Ench(String var3, int var4, Ench.Kind var5, String var6) {
        this.title = var3;
        this.max = var4;
        this.kind = var5;
        this.desc = var6;
    }

    public boolean fits(Material var1) {
        return this.kind.matches(var1);
    }

    public static Ench parse(String var0) {
        try {
            return valueOf(var0.toUpperCase());
        } catch (IllegalArgumentException var2) {
            return null;
        }
    }

    public static enum Kind {
        PICK,
        SHOVEL,
        AXE,
        SWORD,
        BOW,
        ARMOR,
        BOOTS;

        public boolean matches(Material var1) {
            String var2 = var1.name();

            return switch (this) {
                case PICK -> var2.endsWith("_PICKAXE");
                case SHOVEL -> var2.endsWith("_SHOVEL");
                case AXE -> var2.endsWith("_AXE");
                case SWORD -> var2.endsWith("_SWORD");
                case BOW -> var1 == Material.BOW || var1 == Material.CROSSBOW;
                case ARMOR -> var2.endsWith("_HELMET") || var2.endsWith("_CHESTPLATE") || var2.endsWith("_LEGGINGS") || var2.endsWith("_BOOTS");
                case BOOTS -> var2.endsWith("_BOOTS");
            };
        }
    }
}
