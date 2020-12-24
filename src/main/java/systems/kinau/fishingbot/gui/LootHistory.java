package systems.kinau.fishingbot.gui;

import lombok.Getter;
import systems.kinau.fishingbot.bot.Enchantment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LootHistory {

    @Getter private List<LootItem> items = new ArrayList<>();

    public LootItem registerItem(String name, List<Enchantment> enchantments) {
        Optional<LootItem> optItem = items.stream().filter(item -> item.getName().equalsIgnoreCase(name)).findAny();
        if (optItem.isPresent()) {
            optItem.get().setCount(optItem.get().getCount() + 1);
            return optItem.get();
        } else {
            LootItem lootItem = new LootItem(name, 1, enchantments);
            items.add(lootItem);
            return lootItem;
        }
    }

}
