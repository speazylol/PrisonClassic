package dev.prisonclassic.faction;

public record Faction(String id, String name, String color, double sellBonus, double bossBonus, double damageBonus) {
    public String tag() {
        return "<" + this.color + ">" + this.name + "</" + this.color + ">";
    }
}
